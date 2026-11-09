package com.xeroxphotopro.image

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.xeroxphotopro.core.Crop
import com.xeroxphotopro.core.MaskStroke
import com.xeroxphotopro.core.MaskRepair
import com.xeroxphotopro.core.EdgeColor
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

enum class EdgeMode(val label: String) { NATURAL("Natural"), CLEAN("Cleaner edge"), SOFT("Softer hair") }

object BackgroundProcessor {
    private val searchRadii=intArrayOf(1,2,4,8,12)
    suspend fun segment(source: Bitmap): PersonMask = refine(source, rawMask(source))

    internal suspend fun rawMask(source: Bitmap): PersonMask {
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

    /** Joint bilateral upsampling: align uncertain mask edges with source RGB boundaries.
     * Work is bounded to a 1024-pixel longest edge, independent of camera resolution.
     * This refines the existing segmentation; it cannot recover a person the model missed.
     */
    fun refine(source: Bitmap, coarse: PersonMask): PersonMask {
        val scale = minOf(1.0, 1024.0 / maxOf(source.width, source.height))
        val w = maxOf(1, (source.width * scale).roundToInt())
        val h = maxOf(1, (source.height * scale).roundToInt())
        val guide = Bitmap.createScaledBitmap(source, w, h, true)
        val pixels = IntArray(w * h); guide.getPixels(pixels, 0, w, 0, 0, w, h)
        if (guide !== source) guide.recycle()
        val centers = IntArray(coarse.width * coarse.height) { i ->
            val x = ((i % coarse.width + .5) * w / coarse.width).toInt().coerceIn(0, w - 1)
            val y = ((i / coarse.width + .5) * h / coarse.height).toInt().coerceIn(0, h - 1)
            pixels[y * w + x]
        }
        val refined = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val u = (x + .5) / w; val v = (y + .5) / h
            val prior = coarse.sample(u, v)
            if (prior <= .005f || prior >= .995f) { refined[y*w+x] = prior; continue }
            val mx = u * coarse.width - .5; val my = v * coarse.height - .5
            val cx = floor(mx).toInt(); val cy = floor(my).toInt()
            val color = pixels[y*w+x]
            var sum = 0.0; var weight = 0.0
            for (j in maxOf(0,cy-2)..minOf(coarse.height-1,cy+2)) for (i in maxOf(0,cx-2)..minOf(coarse.width-1,cx+2)) {
                val other = centers[j*coarse.width+i]
                var difference = 0.0
                for (shift in 16 downTo 0 step 8) {
                    val d = ((color ushr shift) and 255) - ((other ushr shift) and 255)
                    difference += d*d
                }
                val spatial = (mx-i)*(mx-i)+(my-j)*(my-j)
                val k = kotlin.math.exp(-spatial/4.5 - difference/(2*35.0*35.0))
                sum += k*coarse.confidence[j*coarse.width+i]; weight += k
            }
            // Color similarity alone is not evidence that a nearby background object is hair.
            // Bound the correction to avoid snapping to unrelated high-contrast boundaries.
            val aligned = if (weight > 1e-8) (sum/weight).toFloat().coerceIn(0f,1f) else prior
            refined[y*w+x] = (prior + ((aligned-prior)*.4f).coerceIn(-.12f,.12f)).coerceIn(0f,1f)
        }
        return PersonMask(w,h,refined)
    }

    fun edgeAlpha(confidence: Float, mode: EdgeMode): Float {
        val low = when(mode) { EdgeMode.NATURAL -> .02f; EdgeMode.CLEAN -> .12f; EdgeMode.SOFT -> 0f }
        val high = when(mode) { EdgeMode.NATURAL -> .98f; EdgeMode.CLEAN -> 1f; EdgeMode.SOFT -> 1f }
        return ((confidence-low)/(high-low)).coerceIn(0f,1f)
    }

    /** Find local known-background and known-foreground colors, without blurring the mask.
     * Sparse rings bound work per uncertain output pixel; variable backgrounds are rejected.
     */
    private fun cleanEdge(pixels: IntArray, alpha: FloatArray, w: Int, h: Int, x: Int, y: Int, a: Float): Int {
        val original=pixels[y*w+x]
        if(a !in .12f.. .94f) return original
        var foreground: Int?=null
        var count=0;var red=0;var green=0;var blue=0;var square=0.0
        for(radius in searchRadii) {
            for(dy in -1..1) for(dx in -1..1) {
                if(dx==0 && dy==0) continue
                val xx=x+dx*radius;val yy=y+dy*radius
                if(xx !in 0 until w || yy !in 0 until h) continue
                val i=yy*w+xx;val c=pixels[i]
                if(alpha[i]>=.995f && foreground==null) foreground=c
                if(alpha[i]<=.005f) {
                    val r=(c ushr 16) and 255;val g=(c ushr 8) and 255;val b=c and 255
                    red+=r;green+=g;blue+=b;square+=r*r+g*g+b*b;count++
                }
            }
            if(count>=3 && foreground!=null) break
        }
        val f=foreground ?: return original
        if(count<3) return original
        val r=red.toDouble()/count;val g=green.toDouble()/count;val b=blue.toDouble()/count
        if(square/count-r*r-g*g-b*b>300) return original
        val background=-0x1000000 or (r.roundToInt() shl 16) or (g.roundToInt() shl 8) or b.roundToInt()
        return EdgeColor.recover(original,background,f,a)
    }

    /** Composite only the output portrait, sampling the source mask at each cropped pixel center. */
    fun replace(portrait: Bitmap, crop: Crop, sourceWidth: Int, sourceHeight: Int, mask: PersonMask, color: Int, mode: EdgeMode = EdgeMode.NATURAL, repairs: List<MaskStroke> = emptyList(), decontaminate: Boolean = true): Bitmap {
        // Repairs override the finished alpha, including Cleaner edge: a restored ear stays restored.
        val repaired = if (repairs.isEmpty()) null else PersonMask(mask.width,mask.height,
            MaskRepair.apply(FloatArray(mask.confidence.size) { edgeAlpha(mask.confidence[it],mode) },
                mask.width,mask.height,sourceWidth,sourceHeight,repairs))
        val input=IntArray(portrait.width*portrait.height)
        portrait.getPixels(input,0,portrait.width,0,0,portrait.width,portrait.height)
        val alphas=FloatArray(input.size) { i ->
            val u=(crop.x+(i%portrait.width+.5)*crop.width/portrait.width)/sourceWidth
            val v=(crop.y+(i/portrait.width+.5)*crop.height/portrait.height)/sourceHeight
            edgeAlpha(mask.sample(u,v),mode)
        }
        val output = portrait.copy(Bitmap.Config.ARGB_8888, true)
        val row = IntArray(output.width)
        for (y in 0 until output.height) {
            output.getPixels(row, 0, output.width, 0, y, output.width, 1)
            for (x in row.indices) {
                val u=(crop.x + (x + .5)*crop.width/output.width)/sourceWidth
                val v=(crop.y + (y + .5)*crop.height/output.height)/sourceHeight
                val alpha = repaired?.sample(u,v) ?: edgeAlpha(mask.sample(u,v),mode)
                val originalAlpha=alphas[y*output.width+x]
                // Brush decisions have priority; never recolor repaired edges or original interiors.
                val foregroundColor=if(decontaminate && kotlin.math.abs(alpha-originalAlpha)<.0001f)
                    cleanEdge(input,alphas,output.width,output.height,x,y,originalAlpha) else row[x]
                var rgb = -0x1000000
                for (shift in 16 downTo 0 step 8) {
                    val foreground = (foregroundColor ushr shift) and 255
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
