package com.lapcevichme.ambientlight.ambient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresApi

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
    private var ambientShader: AmbientShader? = null
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Fallback drawing structures
    private var lastFrameBitmap: Bitmap? = null
    private val fallbackSrcRect = Rect()
    private val fallbackDstRect = RectF()
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var hasValidFrame = false
    var framesProcessed: Long = 0
        private set

    var isAmbientEnabled: Boolean = true
        set(value) {
            field = value
            postInvalidateOnAnimation()
        }

    /**
     * Spread reach distance: 0.15 = tight aura around video, 1.0+ = illuminates full page/screen.
     */
    var spreadDistance: Float = 0.8f
        set(value) {
            field = value
            ambientShader?.updateSpreadDistance(value)
            postInvalidateOnAnimation()
        }

    var brightness: Float = 1.0f
        set(value) {
            field = value
            ambientShader?.updateBrightness(value)
            postInvalidateOnAnimation()
        }

    var vibrance: Float = 0.35f
        set(value) {
            field = value
            ambientShader?.updateVibrance(value)
            postInvalidateOnAnimation()
        }

    var blurRadius: Float = 100f
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

    init {
        setWillNotDraw(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            initShader()
        }
        updateBlurEffect()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun initShader() {
        try {
            val shader = AmbientShader()
            ambientShader = shader
            shader.updateSpreadDistance(spreadDistance)
            shader.updateBrightness(brightness)
            shader.updateVibrance(vibrance)
            shader.updateVideoRect(videoNormLeft, videoNormTop, videoNormRight, videoNormBottom)
            shaderPaint.shader = shader.runtimeShader
        } catch (e: Throwable) {
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
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)

        videoNormLeft = (pixelLeft / w).coerceIn(0f, 1f)
        videoNormTop = (pixelTop / h).coerceIn(0f, 1f)
        videoNormRight = (pixelRight / w).coerceIn(0f, 1f)
        videoNormBottom = (pixelBottom / h).coerceIn(0f, 1f)

        ambientShader?.updateVideoRect(videoNormLeft, videoNormTop, videoNormRight, videoNormBottom)
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            ambientShader?.updateResolution(w.toFloat(), h.toFloat())
            fallbackDstRect.set(0f, 0f, w.toFloat(), h.toFloat())
            updateBlurEffect()
        }
    }

    /**
     * Ingests a downsampled video frame (e.g., 32x18) and executes the ambient pipeline.
     */
    fun processFrame(rawFrame: Bitmap) {
        if (!isAmbientEnabled || rawFrame.width <= 0 || rawFrame.height <= 0) return

        val crop = letterboxDetector.detect(rawFrame)
        val smoothed = temporalSmoother.smooth(rawFrame)
        framesProcessed++

        val shader = ambientShader
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shader.updateCrop(crop)
            shader.updateTexture(smoothed)
            hasValidFrame = true
        } else {
            // Fallback path: copy bitmap reference for direct canvas scaling
            lastFrameBitmap = smoothed
            fallbackSrcRect.set(0, 0, smoothed.width, smoothed.height)
            hasValidFrame = true
        }

        // Extract average ambient color for UI elevation shadow tints
        computeAverageColor(smoothed)

        postInvalidateOnAnimation()
    }

    private fun computeAverageColor(bitmap: Bitmap) {
        val listener = onDominantColorListener ?: return
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

        val avgR = (sumR / total).toInt().coerceIn(0, 255)
        val avgG = (sumG / total).toInt().coerceIn(0, 255)
        val avgB = (sumB / total).toInt().coerceIn(0, 255)

        listener(Color.rgb(avgR, avgG, avgB))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isAmbientEnabled || !hasValidFrame) return

        val shader = ambientShader
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shaderPaint)
        } else {
            val bitmap = lastFrameBitmap
            if (bitmap != null && !bitmap.isRecycled) {
                fallbackDstRect.set(0f, 0f, width.toFloat(), height.toFloat())
                canvas.drawBitmap(bitmap, fallbackSrcRect, fallbackDstRect, fallbackPaint)
            }
        }
    }
}
