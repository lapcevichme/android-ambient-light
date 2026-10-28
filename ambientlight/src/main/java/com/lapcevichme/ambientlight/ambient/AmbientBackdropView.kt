package com.lapcevichme.ambientlight.ambient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.View.OnLayoutChangeListener
import androidx.annotation.RequiresApi
import com.lapcevichme.ambientlight.player.InterceptingTextureView

/**
 * Custom View rendering the dynamic ambient lighting glow.
 *
 * Designed to sit as the root backdrop across the entire viewing page/screen (like watch_while_layout).
 * Projects ambient light outwards from the video coordinates into the surrounding UI and screen edges.
 */
class AmbientBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val letterboxDetector = LetterboxDetector()
    private val temporalSmoother = TemporalSmoother()
    private var ambientShader: AmbientShaderDelegate? = null
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Fallback drawing structures for legacy Android (API 24-32)
    private val fallbackBufferWidth = 32
    private val fallbackBufferHeight = 32
    private val fallbackBuffer = Bitmap.createBitmap(fallbackBufferWidth, fallbackBufferHeight, Bitmap.Config.ARGB_8888)
    private val fallbackPixels = IntArray(fallbackBufferWidth * fallbackBufferHeight)
    private val fallbackBlurTemp = IntArray(fallbackBufferWidth * fallbackBufferHeight)
    private var fallbackFramePixels = IntArray(0)
    private var fallbackLastFrame: Bitmap? = null
    private var fallbackLastCrop = CropBounds()

    private val fallbackSrcRect = Rect()
    private val fallbackDstRect = RectF()
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fallbackColorMatrix = ColorMatrix()
    private val tempColorMatrix = ColorMatrix()

    private var hasValidFrame = false
    var framesProcessed: Long = 0
        private set

    var isAmbientEnabled: Boolean = true
        set(value) {
            field = value
            postInvalidateOnAnimation()
        }

    /**
     * Spread reach distance: 0.15 = tight aura around video, 0.8+ = deep expansion into UI.
     */
    var spreadLimit: Float = 0.55f
        set(value) {
            field = value
            ambientShader?.updateSpreadLimit(value)
            updateFallbackGeometry()
            postInvalidateOnAnimation()
        }

    /**
     * Discrete elevation step thickness (Wessel Kroos edge size): 0.05..0.30.
     */
    var edgeSize: Float = 0.12f
        set(value) {
            field = value
            ambientShader?.updateEdgeSize(value)
            refreshFallbackBuffer()
            postInvalidateOnAnimation()
        }

    var brightness: Float = 1.0f
        set(value) {
            field = value
            ambientShader?.updateBrightness(value)
            updateFallbackColorMatrix()
            postInvalidateOnAnimation()
        }

    var vibrance: Float = 0.0f
        set(value) {
            field = value
            ambientShader?.updateVibrance(value)
            updateFallbackColorMatrix()
            postInvalidateOnAnimation()
        }

    var contrast: Float = 1.0f
        set(value) {
            field = value
            ambientShader?.updateContrast(value)
            updateFallbackColorMatrix()
            postInvalidateOnAnimation()
        }

    var directionTop: Boolean = true
        set(value) {
            field = value
            syncDirections()
        }

    var directionBottom: Boolean = true
        set(value) {
            field = value
            syncDirections()
        }

    var directionLeft: Boolean = true
        set(value) {
            field = value
            syncDirections()
        }

    var directionRight: Boolean = true
        set(value) {
            field = value
            syncDirections()
        }

    private fun syncDirections() {
        ambientShader?.updateDirections(directionTop, directionRight, directionBottom, directionLeft)
        updateFallbackGeometry()
        postInvalidateOnAnimation()
    }

    var fadeStart: Float = 0.15f
        set(value) {
            field = value
            ambientShader?.updateFadeParams(fadeStart, fadeCurve)
            refreshFallbackBuffer()
            postInvalidateOnAnimation()
        }

    var fadeCurve: Float = 35f
        set(value) {
            field = value
            ambientShader?.updateFadeParams(fadeStart, fadeCurve)
            refreshFallbackBuffer()
            postInvalidateOnAnimation()
        }

    var isOledMode: Boolean = false
        set(value) {
            field = value
            ambientShader?.updateOledMode(value)
            updateFallbackAlpha()
            postInvalidateOnAnimation()
        }

    var isLetterboxDetectionEnabled: Boolean = true

    var blurRadius: Float = 50f
        set(value) {
            field = value
            updateBlurEffect()
            postInvalidateOnAnimation()
        }

    var flickerReduction: Int
        get() = temporalSmoother.flickerReductionPercent
        set(value) {
            temporalSmoother.flickerReductionPercent = value
        }

    var onDominantColorListener: ((Int) -> Unit)? = null

    // Normalized coordinates of the active video stream within this backdrop [0..1]
    private var videoNormLeft = 0f
    private var videoNormTop = 0f
    private var videoNormRight = 1f
    private var videoNormBottom = 0.35f

    private var hasVideoPosition = false
    private var lastPixelLeft = 0f
    private var lastPixelTop = 0f
    private var lastPixelRight = 0f
    private var lastPixelBottom = 0f

    init {
        setWillNotDraw(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            initShader()
        }
        updateBlurEffect()
        updateFallbackColorMatrix()
        updateFallbackAlpha()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun initShader() {
        try {
            val shader = AmbientShader()
            ambientShader = shader
            shader.updateSpreadLimit(spreadLimit)
            shader.updateEdgeSize(edgeSize)
            shader.updateBrightness(brightness)
            shader.updateVibrance(vibrance)
            shader.updateContrast(contrast)
            shader.updateDirections(directionTop, directionRight, directionBottom, directionLeft)
            shader.updateFadeParams(fadeStart, fadeCurve)
            shader.updateOledMode(isOledMode)
            shader.updateVideoRect(videoNormLeft, videoNormTop, videoNormRight, videoNormBottom)
            shaderPaint.shader = shader.shader
        } catch (_: Throwable) {
            // WORKAROUND: Fall back to scaled bitmap path if GPU driver fails AGSL compilation
            ambientShader = null
        }
    }

    private fun updateBlurEffect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (blurRadius > 1f) {
                setRenderEffect(RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP))
            } else {
                setRenderEffect(null)
            }
        }
    }

    /**
     * Updates the video's layout position on screen to align spatial light projection.
     */
    fun updateVideoPosition(pixelLeft: Float, pixelTop: Float, pixelRight: Float, pixelBottom: Float) {
        hasVideoPosition = true
        lastPixelLeft = pixelLeft
        lastPixelTop = pixelTop
        lastPixelRight = pixelRight
        lastPixelBottom = pixelBottom

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        videoNormLeft = (pixelLeft / w).coerceIn(0f, 1f)
        videoNormTop = (pixelTop / h).coerceIn(0f, 1f)
        videoNormRight = (pixelRight / w).coerceIn(0f, 1f)
        videoNormBottom = (pixelBottom / h).coerceIn(0f, 1f)

        ambientShader?.updateVideoRect(videoNormLeft, videoNormTop, videoNormRight, videoNormBottom)
        updateFallbackGeometry()
        postInvalidateOnAnimation()
    }

    private var attachedTextureView: InterceptingTextureView? = null
    private var attachedLayoutListener: OnLayoutChangeListener? = null
    private var attachedFrameBuffer: Bitmap? = null

    /**
     * Connects an [InterceptingTextureView] directly to this backdrop for classic Android Views / XML layouts.
     *
     * Automatically extracts downsampled frames with zero continuous allocations and keeps spatial
     * light coordinates synchronized across layout changes.
     */
    @JvmOverloads
    fun attachTextureView(
        textureView: InterceptingTextureView,
        downsampleWidth: Int = 128,
        downsampleHeight: Int = 72
    ) {
        detachTextureView()
        attachedTextureView = textureView

        val buffer = Bitmap.createBitmap(downsampleWidth, downsampleHeight, Bitmap.Config.ARGB_8888)
        attachedFrameBuffer = buffer

        textureView.onFrameUpdatedListener = { view ->
            view.getBitmap(buffer)
            processFrame(buffer)
        }

        val listener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            syncAttachedVideoCoordinates()
        }
        attachedLayoutListener = listener
        textureView.addOnLayoutChangeListener(listener)
        addOnLayoutChangeListener(listener)

        syncAttachedVideoCoordinates()
    }

    /**
     * Detaches the previously connected [InterceptingTextureView] and releases listeners and buffers.
     */
    fun detachTextureView() {
        val textureView = attachedTextureView ?: return
        attachedLayoutListener?.let { listener ->
            textureView.removeOnLayoutChangeListener(listener)
            removeOnLayoutChangeListener(listener)
        }
        textureView.onFrameUpdatedListener = null
        attachedTextureView = null
        attachedLayoutListener = null
        attachedFrameBuffer = null
    }

    private fun syncAttachedVideoCoordinates() {
        val videoView = attachedTextureView ?: return
        if (videoView.width <= 0 || videoView.height <= 0 || width <= 0 || height <= 0) {
            videoView.post {
                if (attachedTextureView === videoView) {
                    syncAttachedVideoCoordinates()
                }
            }
            return
        }
        val videoLoc = IntArray(2)
        videoView.getLocationOnScreen(videoLoc)

        val backdropLoc = IntArray(2)
        getLocationOnScreen(backdropLoc)

        val relLeft = (videoLoc[0] - backdropLoc[0]).toFloat()
        val relTop = (videoLoc[1] - backdropLoc[1]).toFloat()
        val relRight = relLeft + videoView.width.toFloat()
        val relBottom = relTop + videoView.height.toFloat()

        updateVideoPosition(relLeft, relTop, relRight, relBottom)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            ambientShader?.updateResolution(w.toFloat(), h.toFloat())
            updateBlurEffect()
            if (hasVideoPosition) {
                updateVideoPosition(lastPixelLeft, lastPixelTop, lastPixelRight, lastPixelBottom)
            } else {
                updateFallbackGeometry()
            }
        }
    }

    private fun updateFallbackGeometry() {
        if (!hasVideoPosition || width <= 0 || height <= 0) {
            fallbackDstRect.set(0f, 0f, width.toFloat(), height.toFloat())
            refreshFallbackBuffer()
            return
        }

        // Same physical reach on every side as the AGSL falloff (spreadLimit * 2.2 * half video width)
        val vWidth = (lastPixelRight - lastPixelLeft).coerceAtLeast(1f)
        val reach = vWidth * 1.1f * spreadLimit.coerceAtLeast(0.01f)

        val left = if (directionLeft) lastPixelLeft - reach else lastPixelLeft
        val top = if (directionTop) lastPixelTop - reach else lastPixelTop
        val right = if (directionRight) lastPixelRight + reach else lastPixelRight
        val bottom = if (directionBottom) lastPixelBottom + reach else lastPixelBottom

        fallbackDstRect.set(left, top, right, bottom)
        refreshFallbackBuffer()
    }

    /** Re-projects the last frame after geometry/parameter changes (e.g. while paused). */
    private fun refreshFallbackBuffer() {
        if (ambientShader == null && hasValidFrame) {
            renderFallbackBuffer()
        }
    }

    private fun updateFallbackColorMatrix() {
        fallbackColorMatrix.reset()

        // 1. Contrast (centered at 128)
        val c = contrast
        if (c != 1.0f) {
            val translate = (1f - c) * 128f
            tempColorMatrix.set(floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            fallbackColorMatrix.postConcat(tempColorMatrix)
        }

        // 2. Vibrance / Saturation
        if (vibrance != 0.0f) {
            val sat = (1f + vibrance * 0.7f).coerceAtLeast(0f)
            tempColorMatrix.setSaturation(sat)
            fallbackColorMatrix.postConcat(tempColorMatrix)
        }

        // 3. Brightness
        if (brightness != 1.0f) {
            val b = brightness.coerceAtLeast(0f)
            tempColorMatrix.setScale(b, b, b, 1f)
            fallbackColorMatrix.postConcat(tempColorMatrix)
        }

        fallbackPaint.colorFilter = ColorMatrixColorFilter(fallbackColorMatrix)
    }

    private fun updateFallbackAlpha() {
        if (!isOledMode) {
            fallbackPaint.alpha = 255
            return
        }
        val lum = (0.2126f * lastAvgR + 0.7152f * lastAvgG + 0.0722f * lastAvgB) / 255f
        if (lum < 0.05f) {
            val factor = (lum / 0.05f).coerceIn(0f, 1f)
            fallbackPaint.alpha = (255 * factor).toInt()
        } else {
            fallbackPaint.alpha = 255
        }
    }

    private fun processFallbackFrame(smoothed: Bitmap, crop: CropBounds) {
        fallbackLastFrame = smoothed
        fallbackLastCrop = crop
        renderFallbackBuffer()
    }

    /**
     * CPU port of the AGSL projection: every buffer cell covering [fallbackDstRect] samples the same
     * Kroos-style projector copy (grown in [edgeSize] steps) and uses the same distance falloff.
     */
    private fun renderFallbackBuffer() {
        val frame = fallbackLastFrame ?: return
        val crop = fallbackLastCrop
        val fw = frame.width
        val fh = frame.height
        if (fallbackFramePixels.size != fw * fh) fallbackFramePixels = IntArray(fw * fh)
        frame.getPixels(fallbackFramePixels, 0, fw, 0, 0, fw, fh)

        val srcL = (crop.left * fw).toInt().coerceIn(0, fw - 1)
        val srcT = (crop.top * fh).toInt().coerceIn(0, fh - 1)
        val srcR = ((1f - crop.right) * fw).toInt().coerceIn(srcL + 1, fw)
        val srcB = ((1f - crop.bottom) * fh).toInt().coerceIn(srcT + 1, fh)
        fallbackSrcRect.set(srcL, srcT, srcR, srcB)

        val dst = fallbackDstRect
        if (dst.width() <= 0f || dst.height() <= 0f) return
        val vL: Float
        val vT: Float
        val vR: Float
        val vB: Float
        if (hasVideoPosition) {
            vL = lastPixelLeft; vT = lastPixelTop; vR = lastPixelRight; vB = lastPixelBottom
        } else {
            vL = dst.left; vT = dst.top; vR = dst.right; vB = dst.bottom
        }
        val halfW = ((vR - vL) * 0.5f).coerceAtLeast(1f)
        val halfH = ((vB - vT) * 0.5f).coerceAtLeast(1f)
        val cx = (vL + vR) * 0.5f
        val cy = (vT + vB) * 0.5f
        val maxDistPx = (spreadLimit.coerceAtLeast(0.01f) * 2.2f * halfW).coerceAtLeast(1f)
        val easing = 16f / (fadeCurve.coerceAtLeast(1f) * 0.64f)
        val stepPx = (edgeSize * maxOf(halfW, halfH)).coerceAtLeast(1f)

        val bw = fallbackBufferWidth
        val bh = fallbackBufferHeight
        for (by in 0 until bh) {
            val py = (dst.top + (by + 0.5f) / bh * dst.height() - cy) / halfH
            for (bx in 0 until bw) {
                val px = (dst.left + (bx + 0.5f) / bw * dst.width() - cx) / halfW
                val idx = by * bw + bx

                val outsideX = kotlin.math.abs(px) > 1f
                val outsideY = kotlin.math.abs(py) > 1f
                if (!outsideX && !outsideY) {
                    fallbackPixels[idx] = sampleFallbackFrame(px, py)
                    continue
                }
                val masked = (py < -1f && !directionTop) || (px > 1f && !directionRight) ||
                    (py > 1f && !directionBottom) || (px < -1f && !directionLeft)
                val dx = (kotlin.math.abs(px) - 1f).coerceAtLeast(0f) * halfW
                val dy = (kotlin.math.abs(py) - 1f).coerceAtLeast(0f) * halfH
                val dist = kotlin.math.sqrt(dx * dx + dy * dy) / maxDistPx
                if (masked || dist >= 1f) {
                    fallbackPixels[idx] = 0
                    continue
                }
                val fadeProgress = ((dist - fadeStart) / (1f - fadeStart).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                val alpha = 1f - Math.pow(fadeProgress.toDouble(), easing.toDouble()).toFloat()

                // Kroos projector: sample the smallest copy (grown by equal pixel steps) containing the cell
                val offX = px * halfW
                val offY = py * halfH
                val growPx = maxOf(kotlin.math.abs(offX) - halfW, kotlin.math.abs(offY) - halfH)
                val level = growPx / stepPx + 0.5f
                val c = sampleFallbackFrame(offX / (halfW + level * stepPx), offY / (halfH + level * stepPx))
                fallbackPixels[idx] = (c and 0x00FFFFFF) or ((alpha * 255f).toInt() shl 24)
            }
        }

        // Fast 2-pass box blur to softly blend the coarse cells
        fastBoxBlur(fallbackPixels, fallbackBlurTemp, bw, bh)
        fastBoxBlur(fallbackPixels, fallbackBlurTemp, bw, bh)
        fallbackBuffer.setPixels(fallbackPixels, 0, bw, 0, 0, bw, bh)
    }

    /** Nearest-neighbour read of the cropped frame at video-normalized coordinates in [-1, 1]. */
    private fun sampleFallbackFrame(px: Float, py: Float): Int {
        val fw = fallbackLastFrame?.width ?: return 0
        val src = fallbackSrcRect
        val u = (px.coerceIn(-1f, 1f) * 0.5f + 0.5f)
        val v = (py.coerceIn(-1f, 1f) * 0.5f + 0.5f)
        val x = (src.left + u * src.width()).toInt().coerceIn(src.left, src.right - 1)
        val y = (src.top + v * src.height()).toInt().coerceIn(src.top, src.bottom - 1)
        return fallbackFramePixels[y * fw + x]
    }

    private fun fastBoxBlur(pixels: IntArray, temp: IntArray, w: Int, h: Int) {
        // Horizontal pass
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0; var a = 0; var count = 0
                for (dx in -1..1) {
                    val nx = x + dx
                    if (nx in 0 until w) {
                        val p = pixels[rowOffset + nx]
                        a += (p ushr 24) and 0xFF
                        r += (p ushr 16) and 0xFF
                        g += (p ushr 8) and 0xFF
                        b += p and 0xFF
                        count++
                    }
                }
                temp[rowOffset + x] = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
        // Vertical pass
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0; var a = 0; var count = 0
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny in 0 until h) {
                        val p = temp[ny * w + x]
                        a += (p ushr 24) and 0xFF
                        r += (p ushr 16) and 0xFF
                        g += (p ushr 8) and 0xFF
                        b += p and 0xFF
                        count++
                    }
                }
                pixels[rowOffset + x] = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
    }

    /**
     * Ingests a downsampled video frame (e.g., 32x18) and executes the ambient pipeline.
     */
    fun processFrame(rawFrame: Bitmap) {
        if (!isAmbientEnabled || rawFrame.width <= 0 || rawFrame.height <= 0) return

        val crop = if (isLetterboxDetectionEnabled) letterboxDetector.detect(rawFrame) else CropBounds()
        val smoothed = temporalSmoother.smooth(rawFrame)
        framesProcessed++

        val shader = ambientShader
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shader.updateCrop(crop)
            shader.updateTexture(smoothed)
            hasValidFrame = true
        } else {
            processFallbackFrame(smoothed, crop)
            hasValidFrame = true
        }

        // Extract average ambient color for UI elevation shadow tints
        computeAverageColor(smoothed)

        postInvalidateOnAnimation()
    }

    private var lastAvgR = 40
    private var lastAvgG = 40
    private var lastAvgB = 40

    private fun computeAverageColor(bitmap: Bitmap) {
        val needsListener = onDominantColorListener != null
        if (!needsListener && !isOledMode) return

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        val width = bitmap.width
        val height = bitmap.height
        val total = width * height

        if (total == 0) return
        val pixels = IntArray(total)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (pixel in pixels) {
            sumR += Color.red(pixel)
            sumG += Color.green(pixel)
            sumB += Color.blue(pixel)
        }

        val targetR = (sumR / total).toInt().coerceIn(0, 255)
        val targetG = (sumG / total).toInt().coerceIn(0, 255)
        val targetB = (sumB / total).toInt().coerceIn(0, 255)

        val smoothedR = (lastAvgR + (targetR - lastAvgR) * 0.2f).toInt()
        val smoothedG = (lastAvgG + (targetG - lastAvgG) * 0.2f).toInt()
        val smoothedB = (lastAvgB + (targetB - lastAvgB) * 0.2f).toInt()

        val hasChanged = kotlin.math.abs(smoothedR - lastAvgR) >= 2 ||
            kotlin.math.abs(smoothedG - lastAvgG) >= 2 ||
            kotlin.math.abs(smoothedB - lastAvgB) >= 2

        lastAvgR = smoothedR
        lastAvgG = smoothedG
        lastAvgB = smoothedB

        updateFallbackAlpha()

        if (hasChanged) {
            onDominantColorListener?.invoke(Color.rgb(smoothedR, smoothedG, smoothedB))
        }
    }

    /**
     * Resets smoothing and detector history (e.g. on seek or video change).
     */
    fun reset() {
        letterboxDetector.reset()
        temporalSmoother.reset()
        hasValidFrame = false
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isAmbientEnabled || !hasValidFrame) return

        val shader = ambientShader
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shaderPaint)
        } else {
            if (fallbackDstRect.width() > 0f && fallbackDstRect.height() > 0f) {
                canvas.drawBitmap(fallbackBuffer, null, fallbackDstRect, fallbackPaint)
            }
        }
    }
}
