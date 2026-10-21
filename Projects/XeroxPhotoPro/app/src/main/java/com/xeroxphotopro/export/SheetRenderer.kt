package com.xeroxphotopro.export

import android.graphics.*
import com.xeroxphotopro.core.*
import kotlin.math.roundToInt

/** Shared by Compose preview and image export. All drawing coordinates are millimeters. */
object SheetRenderer {
    fun draw(canvas: Canvas, image: Bitmap, layout: SheetLayout, pixelsPerMm: Float) {
        val saved = canvas.save()
        try {
            canvas.scale(pixelsPerMm, pixelsPerMm)
            val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(140, 140, 140); style = Paint.Style.STROKE
                strokeWidth = (.2 / POINTS_PER_MM).toFloat()
            }
            layout.placements.forEach { p ->
                val rect = RectF(p.x.toFloat(), p.y.toFloat(), (p.x + p.width).toFloat(), (p.y + p.height).toFloat())
                canvas.drawBitmap(image, null, rect, imagePaint)
                if (layout.config.border) {
                    val inner = RectF(rect).apply { inset(linePaint.strokeWidth / 2, linePaint.strokeWidth / 2) }
                    canvas.drawRect(inner, linePaint)
                }
                if (layout.config.guides) LayoutEngine.marks(p).forEach { s ->
                    canvas.drawLine(s.x1.toFloat(), s.y1.toFloat(), s.x2.toFloat(), s.y2.toFloat(), linePaint)
                }
            }
        } finally { canvas.restoreToCount(saved) }
    }
    fun bitmap(image: Bitmap, layout: SheetLayout): Bitmap {
        val scale = 300.0 / 25.4
        val sheet = Bitmap.createBitmap((layout.width * scale).roundToInt(), (layout.height * scale).roundToInt(), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(sheet); canvas.drawColor(Color.WHITE)
            draw(canvas, image, layout, scale.toFloat())
            return sheet
        } catch (e: Throwable) { sheet.recycle(); throw e }
    }
}
