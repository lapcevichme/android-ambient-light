package com.lapcevichme.ambientlight.ambient

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.min
import androidx.core.graphics.createBitmap

/**
 * Temporal frame blending with cubic luminance difference response curve.
 * Directly ported from Wessel Kroos' canvas-webgl.js flicker reduction algorithm.
 *
 * Provides smooth color transitions on static or slow scenes while reacting instantly
 * without ghosting or delay during sharp scene cuts or flash events.
 */
internal class TemporalSmoother(
    var flickerReductionPercent: Int = 50 // 0 to 100%
) {
    private var previousPixels: IntArray? = null
    private var smoothedBitmap: Bitmap? = null

    fun smooth(input: Bitmap): Bitmap {
        val width = input.width
        val height = input.height
        val pixelCount = width * height

        var currentSmoothed = smoothedBitmap
        if (currentSmoothed == null || currentSmoothed.width != width || currentSmoothed.height != height) {
            currentSmoothed = createBitmap(width, height)
            smoothedBitmap = currentSmoothed
            previousPixels = IntArray(pixelCount)
        }

        val prev = previousPixels ?: IntArray(pixelCount).also { previousPixels = it }
        val currentPixels = IntArray(pixelCount)
        input.getPixels(currentPixels, 0, width, 0, 0, width, height)

        if (prev.all { it == 0 } || flickerReductionPercent <= 0) {
            // First frame initialization or flicker reduction disabled
            System.arraycopy(currentPixels, 0, prev, 0, pixelCount)
            currentSmoothed.setPixels(currentPixels, 0, width, 0, 0, width, height)
            return currentSmoothed
        }

        // Kroos' flicker reduction multiplier: (118 - flickerReduction) / 118
        val flickerMultiplier = ((118 - flickerReductionPercent).coerceIn(1, 118)).toFloat() / 118f

        for (i in 0 until pixelCount) {
            val curr = currentPixels[i]
            val past = prev[i]

            val currR = (curr shr 16 and 0xFF) / 255f
            val currG = (curr shr 8 and 0xFF) / 255f
            val currB = (curr and 0xFF) / 255f

            val pastR = (past shr 16 and 0xFF) / 255f
            val pastG = (past shr 8 and 0xFF) / 255f
            val pastB = (past and 0xFF) / 255f

            // Rec. 709 luminance difference (identical to canvas-webgl.js)
            val currLum = currR * 0.213f + currG * 0.715f + currB * 0.072f
            val pastLum = pastR * 0.213f + pastG * 0.715f + pastB * 0.072f
            val diff = abs(currLum - pastLum)

            // Cubic response curve from canvas-webgl.js:
            // percentage = min(1., (1. - (difference^3)) * flickerReductionDifference)
            // gl_FragColor = currentColor * percentage + previousColor * (1. - percentage)
            val cubicDiff = diff * diff * diff
            val percentage = min(1f, (1f - cubicDiff) * flickerMultiplier)
            val previousWeight = 1f - percentage

            val outR = (currR * percentage + pastR * previousWeight).coerceIn(0f, 1f)
            val outG = (currG * percentage + pastG * previousWeight).coerceIn(0f, 1f)
            val outB = (currB * percentage + pastB * previousWeight).coerceIn(0f, 1f)

            val outColor = Color.argb(
                255,
                (outR * 255f).toInt(),
                (outG * 255f).toInt(),
                (outB * 255f).toInt()
            )

            prev[i] = outColor
            currentPixels[i] = outColor
        }

        currentSmoothed.setPixels(currentPixels, 0, width, 0, 0, width, height)
        return currentSmoothed
    }

    fun reset() {
        previousPixels?.fill(0)
    }
}
