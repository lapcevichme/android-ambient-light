package com.lapcevichme.ambientlight.ambient

import android.graphics.Bitmap
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import androidx.core.graphics.createBitmap

/**
 * Temporal frame blending with cubic luminance difference response curve.
 * Ported from Wessel Kroos' canvas-webgl.js flicker reduction algorithm, with two additions:
 *
 * 1. Scene cut bypass: Kroos' curve gives large changes slightly LESS weight, so a hard cut would
 *    trail behind for several frames. When the whole frame changes a lot, the new frame is taken as is.
 * 2. Time-based weight: the per-frame blend is defined at [REFERENCE_FPS] and rescaled by the real
 *    frame interval, so 24 and 60 fps videos settle in the same time.
 *
 * All buffers are reused between frames (no per-frame allocations).
 */
internal class TemporalSmoother(
    var flickerReductionPercent: Int = 50 // 0 to 100%
) {
    private var previousPixels = IntArray(0)
    private var currentPixels = IntArray(0)
    private var smoothedBitmap: Bitmap? = null
    private var hasPrevious = false
    private var lastFrameTimeMs = 0L

    fun smooth(input: Bitmap, frameTimeMs: Long = SystemClock.uptimeMillis()): Bitmap {
        val width = input.width
        val height = input.height
        val pixelCount = width * height

        var currentSmoothed = smoothedBitmap
        if (currentSmoothed == null || currentSmoothed.width != width || currentSmoothed.height != height) {
            currentSmoothed = createBitmap(width, height)
            smoothedBitmap = currentSmoothed
            previousPixels = IntArray(pixelCount)
            currentPixels = IntArray(pixelCount)
            hasPrevious = false
        }

        val prev = previousPixels
        val curr = currentPixels
        input.getPixels(curr, 0, width, 0, 0, width, height)

        val frameIntervalMs = (frameTimeMs - lastFrameTimeMs).coerceIn(MIN_FRAME_INTERVAL_MS, MAX_FRAME_INTERVAL_MS)
        lastFrameTimeMs = frameTimeMs

        if (!hasPrevious || flickerReductionPercent <= 0 || isSceneCut(curr, prev, pixelCount)) {
            System.arraycopy(curr, 0, prev, 0, pixelCount)
            hasPrevious = true
            currentSmoothed.setPixels(curr, 0, width, 0, 0, width, height)
            return currentSmoothed
        }

        // Kroos' flicker reduction multiplier: (118 - flickerReduction) / 118
        val flickerMultiplier = ((118 - flickerReductionPercent).coerceIn(1, 118)).toFloat() / 118f
        val frameScale = frameIntervalMs * REFERENCE_FPS / 1000f

        for (i in 0 until pixelCount) {
            val c = curr[i]
            val p = prev[i]

            val currR = (c shr 16 and 0xFF) / 255f
            val currG = (c shr 8 and 0xFF) / 255f
            val currB = (c and 0xFF) / 255f

            val pastR = (p shr 16 and 0xFF) / 255f
            val pastG = (p shr 8 and 0xFF) / 255f
            val pastB = (p and 0xFF) / 255f

            // Rec. 709 luminance difference (identical to canvas-webgl.js)
            val diff = abs(luminance(currR, currG, currB) - luminance(pastR, pastG, pastB))

            // Cubic response curve from canvas-webgl.js:
            // percentage = min(1., (1. - (difference^3)) * flickerReductionDifference)
            val percentage = min(1f, (1f - diff * diff * diff) * flickerMultiplier)
            // Same settling time at any frame rate: keep (1 - percentage) per reference frame
            val previousWeight = (1f - percentage).pow(frameScale)
            val currentWeight = 1f - previousWeight

            val outR = currR * currentWeight + pastR * previousWeight
            val outG = currG * currentWeight + pastG * previousWeight
            val outB = currB * currentWeight + pastB * previousWeight

            // Round instead of truncating: truncation drifts static scenes darker by up to 1 level per frame
            val outColor = (0xFF shl 24) or
                (toChannel(outR) shl 16) or
                (toChannel(outG) shl 8) or
                toChannel(outB)

            prev[i] = outColor
            curr[i] = outColor
        }

        currentSmoothed.setPixels(curr, 0, width, 0, 0, width, height)
        return currentSmoothed
    }

    private fun isSceneCut(curr: IntArray, prev: IntArray, pixelCount: Int): Boolean {
        var sum = 0f
        for (i in 0 until pixelCount) {
            val c = curr[i]
            val p = prev[i]
            sum += abs(
                luminance((c shr 16 and 0xFF) / 255f, (c shr 8 and 0xFF) / 255f, (c and 0xFF) / 255f) -
                    luminance((p shr 16 and 0xFF) / 255f, (p shr 8 and 0xFF) / 255f, (p and 0xFF) / 255f)
            )
        }
        return sum / pixelCount > SCENE_CUT_THRESHOLD
    }

    private fun luminance(r: Float, g: Float, b: Float): Float = r * 0.213f + g * 0.715f + b * 0.072f

    private fun toChannel(value: Float): Int = (value * 255f + 0.5f).toInt().coerceIn(0, 255)

    fun reset() {
        hasPrevious = false
    }

    private companion object {
        const val REFERENCE_FPS = 30f
        const val MIN_FRAME_INTERVAL_MS = 4L
        const val MAX_FRAME_INTERVAL_MS = 250L
        /** Mean absolute luminance change of the whole frame that counts as a scene cut. */
        const val SCENE_CUT_THRESHOLD = 0.15f
    }
}
