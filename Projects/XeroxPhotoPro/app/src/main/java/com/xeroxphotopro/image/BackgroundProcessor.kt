package com.xeroxphotopro.image

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.xeroxphotopro.core.Crop
import kotlinx.coroutines.tasks.await
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.roundToInt

/** Confidence mask in normalized source coordinates, independent of the selected crop. */
class PersonMask(val width: Int, val height: Int, val confidence: FloatArray) {
    init { require(width > 0 && height > 0 && confidence.size == width * height) }
    fun sample(u: Double, v: Double): Float {
        val x = (u * width - .5).coerceIn(0.0, (width - 1).toDouble())
        val y = (v * height - .5).coerceIn(0.0, (height - 1).toDouble())
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
        val x1 = minOf(x0 + 1, width - 1); val y1 = minOf(y0 + 1, height - 1)
        val dx = (x - x0).toFloat(); val dy = (y - y0).toFloat()
        val top = confidence[y0 * width + x0] * (1 - dx) + confidence[y0 * width + x1] * dx
        val bottom = confidence[y1 * width + x0] * (1 - dx) + confidence[y1 * width + x1] * dx
        return (top * (1 - dy) + bottom * dy).coerceIn(0f, 1f)
    }
}

object BackgroundProcessor {
    suspend fun segment(source: Bitmap): PersonMask {
        val segmenter = Segmentation.getClient(SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).enableRawSizeMask().build())
        try {
            val result = segmenter.process(InputImage.fromBitmap(source, 0)).await()
            val data = FloatArray(result.width * result.height)
            val buffer = result.buffer.order(ByteOrder.nativeOrder())
            buffer.rewind()
            buffer.asFloatBuffer().get(data)
            check(data.all { it.isFinite() } && data.count { it > .8f } > data.size / 100 && data.count { it < .2f } > data.size / 100) {
                "Could not separate the person from the background reliably. Try a clearer portrait or keep Original."
            }
            return PersonMask(result.width, result.height, data)
        } finally { segmenter.close() }
    }

    /** Composite only the output portrait, sampling the source mask at each cropped pixel center. */
    fun replace(portrait: Bitmap, crop: Crop, sourceWidth: Int, sourceHeight: Int, mask: PersonMask, color: Int): Bitmap {
        val output = portrait.copy(Bitmap.Config.ARGB_8888, true)
        val row = IntArray(output.width)
        for (y in 0 until output.height) {
            output.getPixels(row, 0, output.width, 0, y, output.width, 1)
            for (x in row.indices) {
                val alpha = mask.sample((crop.x + (x + .5) * crop.width / output.width) / sourceWidth,
                    (crop.y + (y + .5) * crop.height / output.height) / sourceHeight)
                var rgb = -0x1000000
                for (shift in 16 downTo 0 step 8) {
                    val foreground = (row[x] ushr shift) and 255
                    val background = (color ushr shift) and 255
                    rgb = rgb or ((foreground * alpha + background * (1 - alpha)).roundToInt().coerceIn(0, 255) shl shift)
                }
                row[x] = rgb
            }
            output.setPixels(row, 0, output.width, 0, y, output.width, 1)
        }
        return output
    }
}
