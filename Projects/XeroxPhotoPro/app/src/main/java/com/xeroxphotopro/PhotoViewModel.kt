package com.xeroxphotopro

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xeroxphotopro.core.*
import com.xeroxphotopro.export.*
import com.xeroxphotopro.image.PhotoProcessor
import com.xeroxphotopro.image.BackgroundProcessor
import com.xeroxphotopro.image.PersonMask
import com.xeroxphotopro.storage.DraftStore
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.min

data class OverflowRequest(val config: SheetConfig, val maximum: Int, val explanation: String)

class PhotoViewModel(application: Application) : AndroidViewModel(application) {
    var screen by mutableStateOf("setup")
    private val store = DraftStore(application)
    var personName by mutableStateOf(store.personName()); private set
    fun updatePersonName(value: String) {
        if (busy || photo == null) return
        personName = value.filterNot { it.isISOControl() }.take(80)
        store.savePersonName(personName)
    }
    fun suggestedFileName(format: ExportFormat): String = "${ExportName.prefix(personName)}${System.currentTimeMillis()}.${format.extension}"
    var config by mutableStateOf(store.settings()); private set
    var photo by mutableStateOf<Bitmap?>(null); private set
    var crop by mutableStateOf<Crop?>(null); private set
    var prepared by mutableStateOf<Bitmap?>(null); private set
    var backgroundColor by mutableStateOf<Int?>(store.background()); private set
    private var personMask: PersonMask? = null
    var checks by mutableStateOf<List<QualityCheck>>(emptyList()); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var overflow by mutableStateOf<OverflowRequest?>(null); private set
    var quality by mutableStateOf("Review the photo before printing."); private set
    var zoom by mutableDoubleStateOf(1.0); private set
    var panX by mutableDoubleStateOf(.5); private set
    var panY by mutableDoubleStateOf(.5); private set
    val layout get() = LayoutEngine.calculate(config)
    init {
        task {
            val saved = store.savedCrop()
            withContext(Dispatchers.IO) {
                SheetExporter.cleanOldShares(application)
                // Remove interrupted capture/encoding scratch files from previous sessions.
                application.cacheDir.listFiles()?.filter { it.name.startsWith("capture-") || it.name.startsWith("sheet-encoding-") }?.forEach { it.delete() }
                store.loadPhoto()
            }?.let {
                setPhoto(it)
                saved?.let { (z, x, y) -> adjust(z, x, y) }
                prepareInternal()
            }
        }
    }
    private fun task(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: OutOfMemoryError) { message = "Not enough memory. Close other apps or use a smaller source photo, then retry." }
            catch (e: Exception) { message = e.message ?: "Operation failed. Retry or select another photo." }
            finally { busy = false }
        }
    }
    private suspend fun setPhoto(bitmap: Bitmap) {
        personMask = null
        photo = bitmap; prepared = null; checks = emptyList()
        crop = CropEngine.manual(bitmap.width, bitmap.height, config.photo)
        zoom = 1.0; panX = .5; panY = .5
        autoCropInternal(bitmap)
    }
    private suspend fun autoCropInternal(bitmap: Bitmap) {
        try {
            val result = withContext(Dispatchers.Default) { PhotoProcessor.faceCrop(bitmap, config.photo) }
            crop = result.first; quality = result.second
            val base = CropEngine.manual(bitmap.width, bitmap.height, config.photo)
            zoom = (base.width / result.first.width).coerceIn(1.0, 5.0)
            panX = if (bitmap.width > result.first.width) (result.first.x / (bitmap.width - result.first.width)).coerceIn(0.0, 1.0) else .5
            panY = if (bitmap.height > result.first.height) (result.first.y / (bitmap.height - result.first.height)).coerceIn(0.0, 1.0) else .5
            store.saveCrop(zoom, panX, panY)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { quality = "Face guidance unavailable. Position the crop manually and inspect the photo." }
    }
    fun importPhoto(uri: Uri, temporaryCapture: Boolean = false, onReady: () -> Unit = {}) = task {
        try {
            val bitmap = withContext(Dispatchers.IO) { PhotoProcessor.load(getApplication(), uri) }
            withContext(Dispatchers.IO) { store.savePhoto(bitmap) }
            // Every new customer begins with the maximum; preserve explicitly selected orientation.
            personName = ""
            backgroundColor = null
            config = config.copy(copies = null); store.saveSettings(config)
            setPhoto(bitmap); prepareInternal(); onReady()
        } finally { if (temporaryCapture && uri.scheme == "file") uri.path?.let { File(it).delete() } }
    }
    fun autoCrop() = task { photo?.let { autoCropInternal(it) }; prepared = null; checks = emptyList() }
    fun rotate() = task {
        photo?.let { source ->
            val rotated = withContext(Dispatchers.Default) { PhotoProcessor.rotate(source) }
            withContext(Dispatchers.IO) { store.savePhoto(rotated, resetBackground = false) }
            setPhoto(rotated)
        }
    }
    fun adjust(z: Double = zoom, x: Double = panX, y: Double = panY) {
        val bitmap = photo ?: return
        zoom = z.coerceIn(1.0, 5.0); panX = x.coerceIn(0.0, 1.0); panY = y.coerceIn(0.0, 1.0)
        crop = CropEngine.manual(bitmap.width, bitmap.height, config.photo, zoom, panX, panY)
        prepared = null; checks = emptyList(); store.saveCrop(zoom, panX, panY)
    }
    fun configure(c: SheetConfig): Boolean {
        if (busy) return false
        return try {
            LayoutEngine.calculate(c)
            val changedSize = c.photo != config.photo
            config = c; store.saveSettings(c); overflow = null
            if (changedSize) adjust(1.0, .5, .5)
            true
        } catch (e: CapacityExceeded) { overflow = OverflowRequest(c, e.maximum, e.message!!); false }
        catch (e: IllegalArgumentException) { message = e.message; false }
    }
    fun useMaximum() { overflow?.let { configure(it.config.copy(copies = null)) } }
    fun dismissOverflow() { overflow = null }
    val effectiveDpi: Int get() {
        val c = crop ?: return 0
        return min(c.width / config.photo.width * 25.4, c.height / config.photo.height * 25.4).toInt()
    }
    fun setBackground(color: Int?) = task {
        // Commit selection only after successful processing; a failure keeps the prior preview.
        prepareInternal(color)
        backgroundColor = color
        store.saveBackground(color)
    }
    private suspend fun prepareInternal(color: Int? = backgroundColor) {
        val source = photo ?: error("Capture or select a photo first.")
        val selected = crop ?: error("Choose a crop first.")
        LayoutEngine.calculate(config)
        val image = withContext(Dispatchers.Default) { PhotoProcessor.render(source, selected, config.photo) }
        val reviewed = withContext(Dispatchers.Default) { PhotoProcessor.validate(image, effectiveDpi) }
        val result = if (color == null) image else withContext(Dispatchers.Default) {
            val mask = personMask ?: BackgroundProcessor.segment(source).also { personMask = it }
            BackgroundProcessor.replace(image, selected, source.width, source.height, mask, color)
        }
        prepared = result
        checks = reviewed + if (color == null) emptyList() else listOf(QualityCheck("Background replacement", CheckStatus.REVIEW,
            "Background edited. Inspect hair, ears and shoulders for missing detail or halos. Use Original if the document disallows editing. Source-photo checks above remain unchanged."))
    }
    fun prepare(onReady: () -> Unit) = task { prepareInternal(); onReady() }
    fun export(uri: Uri, format: ExportFormat) = task {
        val image = prepared ?: error("Review the crop and preview the sheet before exporting.")
        val layout = layout
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.buffered()?.use {
                SheetExporter.write(getApplication(), it, image, layout, format)
            } ?: error("Unable to open the destination. Choose a different folder.")
        }
        message = "${format.name} saved: one A4 sheet. Print at Actual size / 100%. Measure the first photo with a ruler."
    }
    fun share(format: ExportFormat, onReady: (Intent) -> Unit) = task {
        val image = prepared ?: error("Review the crop and preview the sheet before sharing.")
        val layout = layout
        val app = getApplication<Application>()
        val file = withContext(Dispatchers.IO) {
            SheetExporter.cleanOldShares(app)
            val dir = File(app.cacheDir, "exports").apply { check(exists() || mkdirs()) }
            val destination = File.createTempFile(ExportName.prefix(personName), ".${format.extension}", dir)
            try {
                destination.outputStream().buffered().use { SheetExporter.write(app, it, image, layout, format) }
                destination
            } catch (e: Throwable) { destination.delete(); throw e }
        }
        onReady(SheetExporter.shareIntent(app, file, format))
    }
    fun clearPhoto() = task {
        withContext(Dispatchers.IO) { store.delete() }
        personName = ""
        backgroundColor = null; personMask = null
        photo = null; crop = null; prepared = null; checks = emptyList()
        message = "Working photo and temporary shared sheets deleted. Saved exports remain where you saved them."
    }
    fun notify(text: String) { message = text }
    fun dismissMessage() { message = null }
}
