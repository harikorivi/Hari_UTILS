package com.xeroxphotopro.core

import kotlin.math.max
import kotlin.math.min

data class Crop(val x: Double, val y: Double, val width: Double, val height: Double)
data class FaceBox(val left: Double, val top: Double, val right: Double, val bottom: Double)
object CropEngine {
    fun manual(imageWidth: Int, imageHeight: Int, size: PhotoSize, zoom: Double = 1.0, panX: Double = .5, panY: Double = .5): Crop {
        require(imageWidth > 0 && imageHeight > 0)
        require(zoom.isFinite() && zoom in 1.0..5.0 && panX in 0.0..1.0 && panY in 0.0..1.0)
        val ratio = size.width / size.height
        val width = min(imageWidth.toDouble(), imageHeight * ratio) / zoom
        val height = width / ratio
        return Crop(max(0.0, imageWidth - width) * panX, max(0.0, imageHeight - height) * panY, width, height)
    }
    fun aroundFace(w: Int, h: Int, size: PhotoSize, face: FaceBox): Crop {
        val base = manual(w, h, size)
        val desiredHeight = max((face.bottom - face.top) / .58, (face.right - face.left) / .65 / (size.width / size.height))
        val zoom = (base.height / desiredHeight).coerceIn(1.0, 5.0)
        val crop = manual(w, h, size, zoom)
        return crop.copy(x = ((face.left + face.right) / 2 - crop.width / 2).coerceIn(0.0, max(0.0, w - crop.width)),
            y = (face.top - crop.height * .16).coerceIn(0.0, max(0.0, h - crop.height)))
    }
}
