package com.xeroxphotopro.image

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.xeroxphotopro.core.*
import kotlinx.coroutines.tasks.await
import kotlin.math.min

object PhotoProcessor {
    fun load(context: Context, uri: Uri): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val scale = min(1.0, 2400.0 / maxOf(info.size.width, info.size.height))
        decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
    }
    suspend fun faceCrop(bitmap: Bitmap, size: PhotoSize): Pair<Crop, String> {
        val detector = FaceDetection.getClient(FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).build())
        try {
            val faces = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
            if (faces.size != 1) return CropEngine.manual(bitmap.width, bitmap.height, size) to
                if (faces.isEmpty()) "No face detected. Retake or review the manual crop." else "Multiple faces detected. Retake with one person."
            val face = faces.single()
            val b = face.boundingBox
            val crop = CropEngine.aroundFace(bitmap.width, bitmap.height, size, FaceBox(b.left.toDouble(), b.top.toDouble(), b.right.toDouble(), b.bottom.toDouble()))
            val issues = buildList {
                if (kotlin.math.abs(face.headEulerAngleZ) > 8 || kotlin.math.abs(face.headEulerAngleY) > 12 || kotlin.math.abs(face.headEulerAngleX) > 12) add("Keep the head straight and look at the camera.")
                if ((face.leftEyeOpenProbability ?: 1f) < .5 || (face.rightEyeOpenProbability ?: 1f) < .5) add("Check that both eyes are open.")
                if (b.left < crop.x || b.right > crop.x + crop.width || b.top < crop.y || b.bottom > crop.y + crop.height) add("Face reaches the crop edge. Retake from farther away.")
            }
            return crop to (if (issues.isEmpty()) "One face detected. Review hair, chin, shoulders, lighting and sharpness." else issues.joinToString(" "))
        } finally { detector.close() }
    }
    suspend fun validate(bitmap: Bitmap, sourceDpi: Int): List<QualityCheck> {
        val detector = FaceDetection.getClient(FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).build())
        val checks = mutableListOf<QualityCheck>()
        try {
            val faces = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
            checks += QualityCheck("Face count", if (faces.size == 1) CheckStatus.PASS else CheckStatus.REVIEW,
                if (faces.size == 1) "Exactly one face detected." else "${faces.size} faces detected. Retake or review the crop.")
            val face = faces.singleOrNull()
            if (face != null) {
                val straight = kotlin.math.abs(face.headEulerAngleZ) <= 8 && kotlin.math.abs(face.headEulerAngleY) <= 12 && kotlin.math.abs(face.headEulerAngleX) <= 12
                checks += QualityCheck("Head alignment", if (straight) CheckStatus.PASS else CheckStatus.REVIEW,
                    if (straight) "Head pose is within guidance limits." else "Keep the head straight and look directly at the camera.")
                val known = face.getLandmark(FaceLandmark.LEFT_EYE) != null && face.getLandmark(FaceLandmark.RIGHT_EYE) != null && face.leftEyeOpenProbability != null && face.rightEyeOpenProbability != null
                val open = known && face.leftEyeOpenProbability!! >= .5f && face.rightEyeOpenProbability!! >= .5f
                checks += QualityCheck("Eyes", if (!known) CheckStatus.UNKNOWN else if (open) CheckStatus.PASS else CheckStatus.REVIEW,
                    if (open) "Both eyes detected and likely open." else "Confirm both eyes are visible and open.")
                val b = face.boundingBox
                val centered = kotlin.math.abs(b.exactCenterX() / bitmap.width - .5f) <= .12f
                val room = b.left > 0 && b.top > 0 && b.right < bitmap.width && b.bottom < bitmap.height
                checks += QualityCheck("Crop & headroom", if (centered && room) CheckStatus.PASS else CheckStatus.REVIEW,
                    if (centered && room) "Face bounds fit. Manually check crown, chin and shoulders." else "Face is off-center or reaches the edge. Adjust crop or retake.")
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { checks += QualityCheck("Face analysis", CheckStatus.UNKNOWN, "Detection unavailable. Check face count, eyes and alignment manually.") }
        finally { detector.close() }
        val scale = min(1.0, 256.0 / maxOf(bitmap.width, bitmap.height))
        val sample = Bitmap.createScaledBitmap(bitmap, maxOf(16, (bitmap.width * scale).toInt()), maxOf(16, (bitmap.height * scale).toInt()), true)
        try {
            val pixels = IntArray(sample.width * sample.height)
            sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
            checks += QualityMetrics.inspect(sample.width, sample.height, pixels)
        } finally { if (sample !== bitmap) sample.recycle() }
        checks += QualityCheck("Resolution", if (sourceDpi >= 300) CheckStatus.PASS else CheckStatus.REVIEW,
            "$sourceDpi source DPI at this crop; 300 DPI target. Upscaling cannot restore detail.")
        return checks
    }
    fun render(source: Bitmap, crop: Crop, size: PhotoSize): Bitmap {
        // Do not allocate a huge upsampled portrait for large custom sizes or low-resolution sources.
        // The sheet raster is still 300 DPI; PDF embeds only the detail actually available.
        val density = min(300.0, min(crop.width / size.width, crop.height / size.height) * 25.4)
        val output = Bitmap.createBitmap(maxOf(1, kotlin.math.ceil(size.width / 25.4 * density).toInt()), maxOf(1, kotlin.math.ceil(size.height / 25.4 * density).toInt()), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val matrix = Matrix().apply {
            setRectToRect(RectF(crop.x.toFloat(), crop.y.toFloat(), (crop.x + crop.width).toFloat(), (crop.y + crop.height).toFloat()), RectF(0f, 0f, output.width.toFloat(), output.height.toFloat()), Matrix.ScaleToFit.FILL)
        }
        canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }
    fun rotate(source: Bitmap): Bitmap = Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(90f) }, true)
}
