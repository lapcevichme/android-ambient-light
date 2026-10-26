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
    private val fallbackBufferWidth = 24
    private val fallbackBufferHeight = 14
    private val fallbackMargin = 2
    private val fallbackBuffer = Bitmap.createBitmap(fallbackBufferWidth, fallbackBufferHeight, Bitmap.Config.ARGB_8888)
    private val fallbackCanvas = Canvas(fallbackBuffer)
    private val fallbackInnerDst = Rect(
        fallbackMargin,
        fallbackMargin,
        fallbackBufferWidth - fallbackMargin,
        fallbackBufferHeight - fallbackMargin
    )
    private val fallbackScalePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fallbackPixels = IntArray(fallbackBufferWidth * fallbackBufferHeight)
    private val fallbackBlurTemp = IntArray(fallbackBufferWidth * fallbackBufferHeight)

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
            postInvalidateOnAnimation()
        }

    var fadeCurve: Float = 35f
        set(value) {
            field = value
            ambientShader?.updateFadeParams(fadeStart, fadeCurve)
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

    private var lastPixelLeft = -1f
    private var lastPixelTop = -1f
    private var lastPixelRight = -1f
    private var lastPixelBottom = -1f

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
            if (lastPixelLeft >= 0f) {
                updateVideoPosition(lastPixelLeft, lastPixelTop, lastPixelRight, lastPixelBottom)
            } else {
                updateFallbackGeometry()
            }
        }
    }

    private fun updateFallbackGeometry() {
        if (lastPixelLeft < 0f || width <= 0 || height <= 0) {
            fallbackDstRect.set(0f, 0f, width.toFloat(), height.toFloat())
            return
        }

        val vWidth = (lastPixelRight - lastPixelLeft).coerceAtLeast(1f)
        val vHeight = (lastPixelBottom - lastPixelTop).coerceAtLeast(1f)

        val spreadX = vWidth * spreadLimit
        val spreadY = vHeight * spreadLimit

        val left = if (directionLeft) lastPixelLeft - spreadX else lastPixelLeft
        val top = if (directionTop) lastPixelTop - spreadY else lastPixelTop
        val right = if (directionRight) lastPixelRight + spreadX else lastPixelRight
        val bottom = if (directionBottom) lastPixelBottom + spreadY else lastPixelBottom

        fallbackDstRect.set(left, top, right, bottom)
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
        val srcL = (crop.left * smoothed.width).toInt().coerceIn(0, smoothed.width - 1)
        val srcT = (crop.top * smoothed.height).toInt().coerceIn(0, smoothed.height - 1)
        val srcR = ((1f - crop.right) * smoothed.width).toInt().coerceIn(srcL + 1, smoothed.width)
        val srcB = ((1f - crop.bottom) * smoothed.height).toInt().coerceIn(srcT + 1, smoothed.height)
        fallbackSrcRect.set(srcL, srcT, srcR, srcB)

        // Clear buffer with transparent background
        fallbackBuffer.eraseColor(Color.TRANSPARENT)

        // Scale downsampled cropped video frame into inner rectangle (leaving 2px transparent border)
        fallbackCanvas.drawBitmap(smoothed, fallbackSrcRect, fallbackInnerDst, fallbackScalePaint)

        // Perform fast 2-pass box blur to softly blend pixel blocks and feather outer alpha
        fallbackBuffer.getPixels(fallbackPixels, 0, fallbackBufferWidth, 0, 0, fallbackBufferWidth, fallbackBufferHeight)
        fastBoxBlur(fallbackPixels, fallbackBlurTemp, fallbackBufferWidth, fallbackBufferHeight)
        fastBoxBlur(fallbackPixels, fallbackBlurTemp, fallbackBufferWidth, fallbackBufferHeight)
        fallbackBuffer.setPixels(fallbackPixels, 0, fallbackBufferWidth, 0, 0, fallbackBufferWidth, fallbackBufferHeight)
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
                // Ensure outermost 1px perimeter stays 0 alpha (feathering to transparent)
                if (x == 0 || x == w - 1 || y == 0 || y == h - 1) {
                    pixels[rowOffset + x] = 0
                    continue
                }
                // Directional suppression
                if (!directionTop && y < fallbackMargin + 2) {
                    pixels[rowOffset + x] = 0
                    continue
                }
                if (!directionBottom && y >= h - fallbackMargin - 2) {
                    pixels[rowOffset + x] = 0
                    continue
                }
                if (!directionLeft && x < fallbackMargin + 2) {
                    pixels[rowOffset + x] = 0
                    continue
                }
                if (!directionRight && x >= w - fallbackMargin - 2) {
                    pixels[rowOffset + x] = 0
                    continue
                }

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
