package com.xeroxphotopro.house

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xeroxphotopro.core.*
import com.xeroxphotopro.export.ExportFormat
import com.xeroxphotopro.image.PhotoProcessor
import com.xeroxphotopro.smart.*
import kotlinx.coroutines.*
import java.io.File

class HouseViewModel(app: Application): AndroidViewModel(app) {
    private val source=File(app.filesDir,"house-scheme-photo.source")
    private val prefs=app.getSharedPreferences("house-scheme",0)
    var copies by mutableIntStateOf(prefs.getInt("copies",1).coerceIn(1,3));private set
    var cut by mutableStateOf(if(prefs.getBoolean("gray",false)) CutStyle.GRAY else CutStyle.BLACK);private set
    var thumbnail by mutableStateOf<Bitmap?>(null);private set
    var preview by mutableStateOf<Bitmap?>(null);private set
    var busy by mutableStateOf(false);private set
    var message by mutableStateOf<String?>(null);private set
    var sourceRatio by mutableDoubleStateOf(1.5);private set
    val layout get()=HouseLayout.calculate(copies,cut)
    init { task { if(source.exists()) {loadThumbnail();render()} } }
    private fun task(block: suspend ()->Unit) {
        if(busy)return
        busy=true
        viewModelScope.launch {
            try {block()} catch(e: CancellationException) {throw e}
            catch(e: OutOfMemoryError) {message="Not enough memory. Close other apps and retry."}
            catch(e: Exception) {message=e.message?:"Unable to process the photograph."}
            finally {busy=false}
        }
    }
    private suspend fun loadThumbnail()=withContext(Dispatchers.IO) {
        val b=PhotoProcessor.load(getApplication(),Uri.fromFile(source),240)
        thumbnail=b;sourceRatio=b.width.toDouble()/b.height
    }
    private fun asset(layout: SmartLayout): SmartAsset {
        val rect=layout.placements.first().rect
        return SmartAsset(SmartRequest("house-photo",PhotoSize(rect.width,rect.height)),"Original photograph",source,
            thumbnail!!.width,thumbnail!!.height,thumbnail!!,ImageFit.INSIDE)
    }
    private suspend fun render() {
        val selected=layout
        preview=withContext(Dispatchers.Default) {SmartRenderer.preview(getApplication(),selected,listOf(asset(selected)))}
    }
    fun importPhoto(uri: Uri)=task {
        val temp=File(source.parentFile,"house-scheme-photo.tmp")
        try {
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input->temp.outputStream().use {input.copyTo(it)} }?:error("Unable to read selected image.")
                // Validate before replacing the previous private original. Never re-encode the source.
                PhotoProcessor.load(getApplication(),Uri.fromFile(temp),240).recycle()
                check(temp.renameTo(source)) {"Unable to store this photograph."}
            }
            preview=null;loadThumbnail();render()
        } finally {temp.delete()}
    }
    fun select(count: Int,border: CutStyle=cut)=task {
        HouseLayout.calculate(count,border)
        preview=null;copies=count;cut=border;prefs.edit().putInt("copies",count).putBoolean("gray",border==CutStyle.GRAY).apply()
        if(thumbnail!=null) render()
    }
    fun clear() {
        if(busy)return
        if(source.exists() && !source.delete()) {message="Unable to delete working photograph.";return}
        thumbnail=null;preview=null
    }
    fun dismiss() {message=null}
    private suspend fun write(uri: Uri,count: Int,border: CutStyle) {
        check(thumbnail!=null) {"Select a photograph first."}
        val page=HouseLayout.calculate(count,border)
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri,"wt")?.buffered()?.use {
                SmartRenderer.write(getApplication(),it,page,listOf(asset(page)),ExportFormat.JPEG)
            }?:error("Unable to write to this destination.")
        }
    }
    fun filename(count: Int=copies,stamp: Long=System.currentTimeMillis())="Telangana-House-${count}-copy-$stamp.jpg"
    fun save(uri: Uri)=task {
        write(uri,copies,cut)
        message="JPG saved: one portrait A4 sheet at 300 DPI. Print Actual Size / 100%."
    }
    fun saveAll(tree: Uri)=task {
        var saved=0
        try {
            check(thumbnail!=null) {"Select a photograph first."}
            val resolver=getApplication<Application>().contentResolver
            val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
            val stamp=System.currentTimeMillis();val border=cut
            for(count in 1..3) {
                val document=withContext(Dispatchers.IO) {DocumentsContract.createDocument(resolver,parent,"image/jpeg",filename(count,stamp))}?:error("Cannot create a JPG in this folder.")
                try {write(document,count,border);saved++}
                catch(t: Throwable) {withContext(NonCancellable+Dispatchers.IO) {runCatching {DocumentsContract.deleteDocument(resolver,document)}};throw t}
            }
            message="Saved all 3 separate A4 JPGs: 1 copy, 2 copies and 3 smaller copies. Print Actual Size / 100%."
        } catch(e: CancellationException) {throw e}
        catch(e: Exception) {message="$saved of 3 files saved. ${e.message ?: "Unable to complete export."} Retry in a writable folder."}
    }
}
