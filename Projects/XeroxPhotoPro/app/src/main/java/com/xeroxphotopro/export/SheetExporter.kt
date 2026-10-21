package com.xeroxphotopro.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.xeroxphotopro.core.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

enum class ExportFormat(val mime: String, val extension: String) {
    PDF("application/pdf", "pdf"), JPEG("image/jpeg", "jpg"), PNG("image/png", "png")
}
object SheetExporter {
    fun write(context: Context, out: OutputStream, image: Bitmap, layout: SheetLayout, format: ExportFormat) {
        // Revalidate at export boundary as well as when editing; no writer supports overflow.
        val checked = LayoutEngine.calculate(layout.config)
        if (format == ExportFormat.PDF) {
            val jpeg = ByteArrayOutputStream().also { check(image.compress(Bitmap.CompressFormat.JPEG, 97, it)) }.toByteArray()
            PhotoPdf.write(out, checked, jpeg, image.width, image.height)
            return
        }
        var sheet: Bitmap? = null
        val temp = File.createTempFile("sheet-encoding-", ".tmp", context.cacheDir)
        try {
            sheet = SheetRenderer.bitmap(image, checked)
            temp.outputStream().buffered().use {
                check(sheet.compress(if (format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 97, it)) { "Image encoding failed." }
            }
            temp.inputStream().buffered().use {
                if (format == ExportFormat.PNG) RasterMetadata.png(it, out) else RasterMetadata.jpeg(it, out)
            }
        } finally { sheet?.recycle(); temp.delete() }
    }
    fun shareIntent(context: Context, file: File, format: ExportFormat): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("A4 photo sheet", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    fun cleanOldShares(context: Context) {
        // Unique filenames keep already-open share targets from seeing a later customer's sheet.
        File(context.cacheDir, "exports").listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
    }
}
