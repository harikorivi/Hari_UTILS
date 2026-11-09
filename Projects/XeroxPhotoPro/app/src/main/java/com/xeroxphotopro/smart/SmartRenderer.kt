package com.xeroxphotopro.smart

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.xeroxphotopro.image.PhotoProcessor
import com.xeroxphotopro.core.*
import com.xeroxphotopro.export.ExportFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import kotlin.math.*

enum class ImageFit { INSIDE, CROP }
object SmartRenderer {
    fun renderImage(context: Context, asset: SmartAsset, dpi: Int): Bitmap {
        val source=PhotoProcessor.load(context,Uri.fromFile(asset.file),3508)
        val rotated=if(asset.turns==0) source else Bitmap.createBitmap(source,0,0,source.width,source.height,Matrix().apply { postRotate(asset.turns*90f) },true)
        if(rotated!==source) source.recycle()
        try {
            val w=pixelsFor(asset.request.size.width,dpi);val h=pixelsFor(asset.request.size.height,dpi)
            val result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            Canvas(result).apply {
                drawColor(Color.WHITE)
                val factor=if(asset.fit==ImageFit.INSIDE) min(w.toFloat()/rotated.width,h.toFloat()/rotated.height) else max(w.toFloat()/rotated.width,h.toFloat()/rotated.height)
                val rw=rotated.width*factor;val rh=rotated.height*factor
                drawBitmap(rotated,null,RectF((w-rw)/2,(h-rh)/2,(w+rw)/2,(h+rh)/2),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            return result
        } finally { rotated.recycle() }
    }
    fun draw(canvas: Canvas,layout: SmartLayout,images: Map<String,Bitmap>,scale: Float) {
        val saved=canvas.save();canvas.scale(scale,scale)
        try {
            val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val line=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style=Paint.Style.STROKE;strokeWidth=(.2/POINTS_PER_MM).toFloat()
                color=if(layout.settings.cut==CutStyle.GRAY || layout.settings.cut==CutStyle.CORNERS) Color.rgb(140,140,140) else Color.BLACK
                if(layout.settings.cut==CutStyle.DASHED) pathEffect=DashPathEffect(floatArrayOf((2/POINTS_PER_MM).toFloat(),(2/POINTS_PER_MM).toFloat()),0f)
            }
            for(item in layout.placements) {
                val p=item.rect;val image=images.getValue(item.id)
                val mark=canvas.save()
                if(item.rotated) {
                    canvas.translate((p.x+p.width).toFloat(),p.y.toFloat());canvas.rotate(90f)
                    canvas.drawBitmap(image,null,RectF(0f,0f,p.height.toFloat(),p.width.toFloat()),paint)
                } else canvas.drawBitmap(image,null,RectF(p.x.toFloat(),p.y.toFloat(),(p.x+p.width).toFloat(),(p.y+p.height).toFloat()),paint)
                canvas.restoreToCount(mark)
                if(layout.settings.cut==CutStyle.CORNERS) LayoutEngine.marks(p).forEach { s->canvas.drawLine(s.x1.toFloat(),s.y1.toFloat(),s.x2.toFloat(),s.y2.toFloat(),line) }
                else if(layout.settings.cut!=CutStyle.NONE) {
                    val rect=RectF(p.x.toFloat(),p.y.toFloat(),(p.x+p.width).toFloat(),(p.y+p.height).toFloat());rect.inset(line.strokeWidth/2,line.strokeWidth/2);canvas.drawRect(rect,line)
                }
            }
        } finally { canvas.restoreToCount(saved) }
    }
    fun bitmap(layout: SmartLayout,images: Map<String,Bitmap>,dpi: Int): Bitmap {
        val scale=dpi/25.4
        val result=Bitmap.createBitmap((layout.width*scale).roundToInt(),(layout.height*scale).roundToInt(),Bitmap.Config.ARGB_8888)
        try { Canvas(result).apply { drawColor(Color.WHITE);draw(this,layout,images,scale.toFloat()) };return result }
        catch(t: Throwable) { result.recycle();throw t }
    }
    fun preview(context: Context,layout: SmartLayout,assets: List<SmartAsset>): Bitmap {
        val images=prepare(context,layout,assets,100)
        try { return bitmap(layout,images,100) } finally { images.values.forEach { it.recycle() } }
    }
    private fun prepare(context: Context,layout: SmartLayout,assets: List<SmartAsset>,dpi: Int): Map<String,Bitmap> {
        val images=mutableMapOf<String,Bitmap>()
        try {
            layout.placements.map { it.id }.distinct().forEach { id-> images[id]=renderImage(context,assets.single { it.request.id==id },dpi) }
            return images
        } catch(t: Throwable) { images.values.forEach { it.recycle() };throw t }
    }
    fun write(context: Context,out: OutputStream,layout: SmartLayout,assets: List<SmartAsset>,format: ExportFormat) {
        val images=prepare(context,layout,assets,300)
        try {
            if(format==ExportFormat.PDF) {
                val photos=images.mapValues { (_,image)->PdfPhoto(ByteArrayOutputStream().also { check(image.compress(Bitmap.CompressFormat.JPEG,97,it)) }.toByteArray(),image.width,image.height) }
                SmartPdf.write(out,layout,photos)
            } else {
                val sheet=bitmap(layout,images,300);val temp=File.createTempFile("smart-encoding-",".tmp",context.cacheDir)
                try {
                    temp.outputStream().buffered().use { check(sheet.compress(if(format==ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,97,it)) }
                    temp.inputStream().buffered().use { if(format==ExportFormat.PNG) RasterMetadata.png(it,out) else RasterMetadata.jpeg(it,out) }
                } finally { sheet.recycle();temp.delete() }
            }
        } finally { images.values.forEach { it.recycle() } }
    }
}
