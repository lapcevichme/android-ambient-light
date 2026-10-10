package com.lapcevichme.ambientlight.ambient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresApi

/**
 * Custom View rendering the dynamic ambient lighting glow beneath the player.
 *
 * Utilizes AGSL RuntimeShader for geometry transformation and color tuning,
 * with Skia hardware-accelerated RenderEffect blur for the soft dispersion aura.
 */
class AmbientBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val letterboxDetector = LetterboxDetector()
    private val temporalSmoother = TemporalSmoother()
    private var ambientShader: AmbientShader? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var hasValidFrame = false

    var isAmbientEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var spread: Float = 1.35f
        set(value) {
            field = value
            ambientShader?.updateSpread(value)
            invalidate()
        }

    var vibrance: Float = 0.25f
        set(value) {
            field = value
            ambientShader?.updateVibrance(value)
            invalidate()
        }

    var blurRadius: Float = 80f
        set(value) {
            field = value
            updateBlurEffect()
            invalidate()
        }

    var flickerReduction: Int
        get() = temporalSmoother.flickerReductionPercent
        set(value) {
            temporalSmoother.flickerReductionPercent = value
        }

    var onDominantColorListener: ((Int) -> Unit)? = null

    init {
        // RenderNode hardware acceleration required for RenderEffect and RuntimeShader
        setWillNotDraw(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            initShader()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun initShader() {
        val shader = AmbientShader()
        ambientShader = shader
        shader.updateSpread(spread)
        shader.updateVibrance(vibrance)
        paint.shader = shader.runtimeShader
        updateBlurEffect()
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            ambientShader?.updateResolution(w.toFloat(), h.toFloat())
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

        ambientShader?.let { shader ->
            shader.updateCrop(crop)
            shader.updateTexture(smoothed)
            hasValidFrame = true
            invalidate()
        }

        // Extract average ambient color for UI elevation shadow tints
        computeAverageColor(smoothed)
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
        if (isAmbientEnabled && hasValidFrame && ambientShader != null) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }
}
