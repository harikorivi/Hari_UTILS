package com.xeroxphotopro.core

import kotlin.math.sqrt

enum class CheckStatus { PASS, REVIEW, UNKNOWN }
data class QualityCheck(val label: String, val status: CheckStatus, val detail: String)
/** Heuristics for operator guidance, not document-authority acceptance or calibrated camera scores. */
object QualityMetrics {
    fun inspect(width: Int, height: Int, pixels: IntArray): List<QualityCheck> {
        require(width >= 3 && height >= 3 && pixels.size == width * height)
        val gray = DoubleArray(pixels.size) { i ->
            val c = pixels[i]; .2126 * ((c shr 16) and 255) + .7152 * ((c shr 8) and 255) + .0722 * (c and 255)
        }
        // Central region approximates the subject; corners are only a background hint.
        val values = ArrayList<Double>(); val laplacian = ArrayList<Double>()
        var left = 0.0; var right = 0.0; var nl = 0; var nr = 0
        for (y in height / 5 until height * 4 / 5) for (x in width / 5 until width * 4 / 5) {
            if (x == 0 || y == 0 || x == width - 1 || y == height - 1) continue
            val i = y * width + x; val value = gray[i]; values.add(value)
            laplacian.add(4 * value - gray[i - 1] - gray[i + 1] - gray[i - width] - gray[i + width])
            if (x < width / 2) { left += value; nl++ } else { right += value; nr++ }
        }
        fun variance(v: List<Double>): Double { val mean = v.average(); return v.sumOf { (it - mean) * (it - mean) } / v.size }
        val mean = values.average()
        val clipped = values.count { it < 20 || it > 245 }.toDouble() / values.size
        val blur = variance(laplacian)
        val uneven = kotlin.math.abs(left / nl - right / nr)
        val corners = buildList {
            for (y in 0 until maxOf(1, height / 6)) for (x in 0 until maxOf(1, width / 6)) {
                add(gray[y * width + x]); add(gray[y * width + width - 1 - x])
            }
        }
        return listOf(
            QualityCheck("Lighting", if (mean in 55.0..210.0 && clipped < .3) CheckStatus.PASS else CheckStatus.REVIEW,
                if (mean < 55) "Photo appears dark. Add even light and retake." else if (mean > 210 || clipped >= .3) "Check bright areas and deep shadows." else "Exposure heuristic is within range; inspect the face."),
            QualityCheck("Sharpness", if (blur >= 65) CheckStatus.PASS else CheckStatus.REVIEW,
                if (blur < 65) "Possible blur or low detail. Inspect eyes and hair at full size; retake if soft." else "Edge detail detected; inspect eyes and hair."),
            QualityCheck("Shadows", if (uneven <= 35) CheckStatus.PASS else CheckStatus.REVIEW,
                if (uneven > 35) "Uneven brightness across the subject. Check for one-sided shadows." else "No strong left/right brightness imbalance detected."),
            QualityCheck("Background", if (sqrt(variance(corners)) < 22) CheckStatus.PASS else CheckStatus.REVIEW,
                if (sqrt(variance(corners)) >= 22) "Background corners vary. Use a plain backdrop and inspect around hair." else "Corners appear uniform. Inspect the whole background manually.")
        )
    }
}
