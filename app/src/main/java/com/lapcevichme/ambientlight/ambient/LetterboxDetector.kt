package com.lapcevichme.ambientlight.ambient

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/**
 * Bounds representing the detected letterboxing/pillarboxing in normalized [0.0, 1.0] UV coordinates.
 */
data class CropBounds(
    val top: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f,
    val right: Float = 0f
)

/**
 * Detects black bars (letterbox/pillarbox) along video frame borders.
 * Ported and adapted from Wessel Kroos' bar-detection.js algorithm.
 *
 * Samples scanlines near outer edges of low-resolution frame to identify inactive black margins.
 */
class LetterboxDetector(
    private val blackThreshold: Float = 16f / 255f, // Standard video black level threshold (~16 in 8-bit TV range)
    private val smoothingFactor: Float = 0.2f       // Exponential smoothing to avoid jitter during transient dark scenes
) {
    private var currentBounds = CropBounds()

    fun detect(bitmap: Bitmap): CropBounds {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return currentBounds

        var detectedTop = 0f
        var detectedBottom = 0f
        var detectedLeft = 0f
        var detectedRight = 0f

        val halfHeight = height / 2
        val halfWidth = width / 2

        // Top edge scan
        for (y in 0 until halfHeight) {
            if (isRowBlack(bitmap, y, width)) {
                detectedTop = (y + 1).toFloat() / height
            } else {
                break
            }
        }

        // Bottom edge scan
        for (y in (height - 1) downTo halfHeight) {
            if (isRowBlack(bitmap, y, width)) {
                detectedBottom = (height - y).toFloat() / height
            } else {
                break
            }
        }

        // Left edge scan
        for (x in 0 until halfWidth) {
            if (isColumnBlack(bitmap, x, height)) {
                detectedLeft = (x + 1).toFloat() / width
            } else {
                break
            }
        }

        // Right edge scan
        for (x in (width - 1) downTo halfWidth) {
            if (isColumnBlack(bitmap, x, height)) {
                detectedRight = (width - x).toFloat() / width
            } else {
                break
            }
        }

        // Apply temporal smoothing to prevent abrupt crop snapping on dark movie frames
        val smoothed = CropBounds(
            top = currentBounds.top + (detectedTop - currentBounds.top) * smoothingFactor,
            bottom = currentBounds.bottom + (detectedBottom - currentBounds.bottom) * smoothingFactor,
            left = currentBounds.left + (detectedLeft - currentBounds.left) * smoothingFactor,
            right = currentBounds.right + (detectedRight - currentBounds.right) * smoothingFactor
        )

        currentBounds = smoothed
        return smoothed
    }

    private fun isRowBlack(bitmap: Bitmap, y: Int, width: Int): Boolean {
        // Step across row for performance (5 samples across the line)
        val step = maxOf(1, width / 5)
        for (x in 0 until width step step) {
            val pixel = bitmap.getPixel(x, y)
            if (luminance(pixel) > blackThreshold) return false
        }
        return true
    }

    private fun isColumnBlack(bitmap: Bitmap, x: Int, height: Int): Boolean {
        val step = maxOf(1, height / 5)
        for (y in 0 until height step step) {
            val pixel = bitmap.getPixel(x, y)
            if (luminance(pixel) > blackThreshold) return false
        }
        return true
    }

    /**
     * Rec. 709 perceptual luminance formula.
     */
    private fun luminance(pixel: Int): Float {
        val r = Color.red(pixel) / 255f
        val g = Color.green(pixel) / 255f
        val b = Color.blue(pixel) / 255f
        return r * 0.2126f + g * 0.7152f + b * 0.0722f
    }
}
