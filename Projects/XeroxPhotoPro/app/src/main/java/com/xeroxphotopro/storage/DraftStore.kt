package com.xeroxphotopro.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.xeroxphotopro.core.*
import com.xeroxphotopro.image.PhotoProcessor
import java.io.File

class DraftStore(private val context: Context) {
    private val draft = File(context.filesDir, "current-photo.jpg")
    private val prefs = context.getSharedPreferences("shop", Context.MODE_PRIVATE)
    fun loadPhoto(): Bitmap? = if (draft.exists()) PhotoProcessor.load(context, Uri.fromFile(draft)) else null
    fun savePhoto(photo: Bitmap, resetBackground: Boolean = true) {
        val temp = File(context.filesDir, "current-photo.tmp")
        try {
            temp.outputStream().use { check(photo.compress(Bitmap.CompressFormat.JPEG, 98, it)) }
            check(temp.renameTo(draft)) { "Unable to save draft. Check free storage." }
            if (resetBackground) { saveBackground(null); savePersonName("") }
            prefs.edit().remove("zoom").remove("panX").remove("panY").apply()
        } finally { temp.delete() }
    }
    fun personName(): String = prefs.getString("personName", "").orEmpty()
    fun savePersonName(name: String) { prefs.edit().putString("personName", name).apply() }
    fun background(): Int? = if (prefs.contains("background")) prefs.getInt("background", -1) else null
    fun saveBackground(color: Int?) {
        val edit = prefs.edit()
        if (color == null) edit.remove("background") else edit.putInt("background", color)
        edit.apply()
    }
    fun delete() {
        saveBackground(null)
        check(!draft.exists() || draft.delete()) { "Unable to remove the current draft." }
        savePersonName("")
        prefs.edit().remove("zoom").remove("panX").remove("panY").apply()
        File(context.cacheDir, "exports").listFiles()?.forEach { it.delete() }
    }
    fun settings(): SheetConfig = runCatching {
        // Upgrade from v0.1: do not retain its old fixed counts or orientation flag.
        val current = prefs.getInt("schema", 0) == 2
        val c = SheetConfig(photo = PhotoSize(prefs.getString("widthExact", null)?.toDoubleOrNull() ?: prefs.getFloat("width", 35f).toDouble(), prefs.getString("heightExact", null)?.toDoubleOrNull() ?: prefs.getFloat("height", 45f).toDouble()),
            copies = if (current) prefs.getInt("copies", 0).takeIf { it > 0 } else null,
            orientation = if (current) Orientation.valueOf(prefs.getString("orientation", "AUTO")!!) else Orientation.AUTO,
            margin = prefs.getFloat("margin", 5f).toDouble(), gap = prefs.getFloat("gap", 3f).toDouble(),
            guides = prefs.getBoolean("guides", true), border = prefs.getBoolean("border", false))
        runCatching { LayoutEngine.calculate(c); c }.getOrElse { c.copy(copies = null).also { LayoutEngine.calculate(it) } }
    }.getOrDefault(SheetConfig())
    fun saveSettings(c: SheetConfig) {
        prefs.edit().putString("widthExact", c.photo.width.toString()).putString("heightExact", c.photo.height.toString()).putInt("schema", 2).putFloat("width", c.photo.width.toFloat()).putFloat("height", c.photo.height.toFloat())
            .putInt("copies", c.copies ?: 0).putString("orientation", c.orientation.name).putFloat("margin", c.margin.toFloat())
            .putFloat("gap", c.gap.toFloat()).putBoolean("guides", c.guides).putBoolean("border", c.border).apply()
    }
    fun saveCrop(zoom: Double, x: Double, y: Double) {
        prefs.edit().putFloat("zoom", zoom.toFloat()).putFloat("panX", x.toFloat()).putFloat("panY", y.toFloat()).apply()
    }
    fun savedCrop(): Triple<Double, Double, Double>? = if (prefs.contains("zoom")) Triple(
        prefs.getFloat("zoom", 1f).toDouble().coerceIn(1.0, 5.0), prefs.getFloat("panX", .5f).toDouble().coerceIn(0.0, 1.0), prefs.getFloat("panY", .5f).toDouble().coerceIn(0.0, 1.0)) else null
}
