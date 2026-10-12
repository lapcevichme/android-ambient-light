package com.lapcevichme.ambientlight.ambient

import android.graphics.Bitmap
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
 * Ported from Wessel Kroos' bar-detection.js with full anti-flicker guards:
 *
 * 1. Dark Scene Guard: Prevents dark frames/flashes from triggering false 50% crops.
 * 2. Symmetry Validation: Rejects asymmetrical scene elements (sky, hair, vignettes).
 * 3. Max Depth Ceiling: Real aspect ratios never exceed 35% border depth.
 * 4. Sliding Window Majority Filter: Requires persistent occurrence before shifting target bounds.
 * 5. Deadband: Snaps bars < 3% to full-frame 0.0 to eliminate subpixel fluttering.
 */
class LetterboxDetector(
    private val blackThreshold: Float = 20f / 255f, // ~20 in 8-bit TV/video range
    private val historySize: Int = 16,               // Sliding window size (Kroos' averageHistorySize)
    private val smoothingSpeed: Float = 0.15f        // Smooth reframing factor
) {
    private var currentBounds = CropBounds()
    private var targetBounds = CropBounds()
    private val history = ArrayDeque<CropBounds>(historySize)

    fun detect(bitmap: Bitmap): CropBounds {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return currentBounds

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. WESSEL'S DARK SCENE GUARD:
        // A black bar can ONLY exist if the active content inside is brighter than the bar.
        // If the center region is dark, this is a dark scene or cut transition — NOT a letterbox.
        var centerLumSum = 0f
        var centerSamples = 0
        val cxStart = width / 4
        val cxEnd = (width * 3) / 4
        val cyStart = height / 4
        val cyEnd = (height * 3) / 4

        for (y in cyStart until cyEnd) {
            val rowOffset = y * width
            for (x in cxStart until cxEnd) {
                centerLumSum += luminance(pixels[rowOffset + x])
                centerSamples++
            }
        }

        val avgCenterLum = if (centerSamples > 0) centerLumSum / centerSamples else 0f
        if (avgCenterLum <= blackThreshold * 1.5f) {
            // Scene is dark/black: freeze current stable bounds, avoid rubber-banding during cuts
            return currentBounds
        }

        // 2. Scan edges up to 35% depth (widest cinematic format 2.76:1 is ~18%)
        val maxVerticalBarRows = (height * 0.35f).toInt()
        val maxHorizontalBarCols = (width * 0.35f).toInt()

        // Top bar scan
        var rawTopRows = 0
        for (y in 0 until maxVerticalBarRows) {
            if (isRowBlack(pixels, y, width)) {
                rawTopRows = y + 1
            } else {
                break
            }
        }

        // Bottom bar scan
        var rawBottomRows = 0
        for (y in (height - 1) downTo (height - maxVerticalBarRows)) {
            if (isRowBlack(pixels, y, width)) {
                rawBottomRows = height - y
            } else {
                break
            }
        }

        // Left bar scan (pillarbox)
        var rawLeftCols = 0
        for (x in 0 until maxHorizontalBarCols) {
            if (isColBlack(pixels, x, width, height)) {
                rawLeftCols = x + 1
            } else {
                break
            }
        }

        // Right bar scan (pillarbox)
        var rawRightCols = 0
        for (x in (width - 1) downTo (width - maxHorizontalBarCols)) {
            if (isColBlack(pixels, x, width, height)) {
                rawRightCols = width - x
            } else {
                break
            }
        }

        // 3. WESSEL'S SYMMETRY VALIDATION:
        // Real letterbox/pillarbox bars are symmetric.
        // If one side has dark rows and the other has none, it's scene content (sky/vignette), not a bar.
        var topNorm = rawTopRows.toFloat() / height
        var bottomNorm = rawBottomRows.toFloat() / height
        var leftNorm = rawLeftCols.toFloat() / width
        var rightNorm = rawRightCols.toFloat() / width

        val verticalDiff = abs(topNorm - bottomNorm)
        if (verticalDiff > 0.08f) {
            // Asymmetric vertical bars: invalidate
            topNorm = 0f
            bottomNorm = 0f
        } else {
            val avg = (topNorm + bottomNorm) / 2f
            topNorm = avg
            bottomNorm = avg
        }

        val horizontalDiff = abs(leftNorm - rightNorm)
        if (horizontalDiff > 0.08f) {
            // Asymmetric horizontal bars: invalidate
            leftNorm = 0f
            rightNorm = 0f
        } else {
            val avg = (leftNorm + rightNorm) / 2f
            leftNorm = avg
            rightNorm = avg
        }

        // 4. DEADBAND: Bars < 3% of the screen are noise/subpixel border artifacts -> snap to 0
        if (topNorm < 0.03f) { topNorm = 0f; bottomNorm = 0f }
        if (leftNorm < 0.03f) { leftNorm = 0f; rightNorm = 0f }

        val candidate = CropBounds(topNorm, bottomNorm, leftNorm, rightNorm)

        // 5. WESSEL'S HISTORY BUFFER & MAJORITY FILTER (occurrences check):
        // Prevents any transient 1-2 frame flash or cut from twitching the aspect ratio.
        if (history.size >= historySize) {
            history.removeFirst()
        }
        history.addLast(candidate)

        // Count similar occurrences in history
        val dominantCandidate = history.maxByOrNull { item ->
            history.count { other ->
                abs(item.top - other.top) < 0.03f && abs(item.left - other.left) < 0.03f
            }
        } ?: candidate

        val matchCount = history.count {
            abs(it.top - dominantCandidate.top) < 0.03f && abs(it.left - dominantCandidate.left) < 0.03f
        }

        if (matchCount >= historySize / 2) {
            // Dominant aspect ratio confirmed by majority of frames
            targetBounds = dominantCandidate
        }

        // 6. Smooth reframing towards targetBounds
        currentBounds = CropBounds(
            top = lerp(currentBounds.top, targetBounds.top, smoothingSpeed),
            bottom = lerp(currentBounds.bottom, targetBounds.bottom, smoothingSpeed),
            left = lerp(currentBounds.left, targetBounds.left, smoothingSpeed),
            right = lerp(currentBounds.right, targetBounds.right, smoothingSpeed)
        )

        return currentBounds
    }

    private fun isRowBlack(pixels: IntArray, y: Int, width: Int): Boolean {
        val rowOffset = y * width
        for (x in 0 until width) {
            if (luminance(pixels[rowOffset + x]) > blackThreshold) return false
        }
        return true
    }

    private fun isColBlack(pixels: IntArray, x: Int, width: Int, height: Int): Boolean {
        for (y in 0 until height) {
            if (luminance(pixels[y * width + x]) > blackThreshold) return false
        }
        return true
    }

    private fun luminance(pixel: Int): Float {
        val r = (pixel shr 16 and 0xFF) / 255f
        val g = (pixel shr 8 and 0xFF) / 255f
        val b = (pixel and 0xFF) / 255f
        return r * 0.2126f + g * 0.7152f + b * 0.0722f
    }

    private fun lerp(start: Float, stop: Float, amount: Float): Float {
        val diff = stop - start
        if (abs(diff) < 0.002f) return stop
        return start + diff * amount
    }

    fun reset() {
        history.clear()
        currentBounds = CropBounds()
        targetBounds = CropBounds()
    }
}
