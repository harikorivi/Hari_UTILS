package com.xeroxphotopro.smart

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Intent
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xeroxphotopro.core.*
import com.xeroxphotopro.export.*
import com.xeroxphotopro.image.PhotoProcessor
import kotlinx.coroutines.*
import org.json.*
import java.io.File
import java.util.UUID
import kotlin.math.*

data class SmartAsset(val request: SmartRequest,val name: String,val file: File,val sourceWidth: Int,val sourceHeight: Int,
    val thumbnail: Bitmap,val fit: ImageFit=ImageFit.INSIDE,val turns: Int=0,val lockRatio: Boolean=true)
class SmartViewModel(app: Application): AndroidViewModel(app) {
    private val dir=File(app.filesDir,"smart-layout").apply { mkdirs() }
    private val prefs=app.getSharedPreferences("smart-layout",0)
    var assets by mutableStateOf<List<SmartAsset>>(emptyList());private set
    var settings by mutableStateOf(SmartSettings());private set
    var layout by mutableStateOf<SmartLayout?>(null);private set
    var preview by mutableStateOf<Bitmap?>(null);private set
    var busy by mutableStateOf(false);private set
    var message by mutableStateOf<String?>(null);private set
    var overflow by mutableStateOf<SmartOverflow?>(null);private set
    init { task {
        withContext(Dispatchers.IO) {
            val json=JSONObject(prefs.getString("job","{}")!!)
            settings=SmartSettings(SmartMode.valueOf(json.optString("mode","MAXIMUM")),Orientation.valueOf(json.optString("orientation","AUTO")),json.optDouble("margin",5.0),json.optDouble("gx",3.0),json.optDouble("gy",3.0),CutStyle.valueOf(json.optString("cut","CORNERS")))
            val list=json.optJSONArray("assets")?:JSONArray()
            assets=(0 until list.length()).mapNotNull { i ->
                val a=list.getJSONObject(i);val id=a.getString("id")
                require(id.matches(Regex("[a-zA-Z0-9-]+")))
                val file=File(dir,"$id.source");if(!file.exists()) null else {
                    SmartAsset(SmartRequest(id,PhotoSize(a.getDouble("w"),a.getDouble("h")),a.getInt("copies"),a.optBoolean("auto")),a.getString("name"),file,a.getInt("sw"),a.getInt("sh"),thumbnail(file),ImageFit.valueOf(a.getString("fit")),a.getInt("turns"),a.optBoolean("lock",true))
                }
            }
            val active=assets.map {it.file.name}.toSet()
            dir.listFiles()?.filterNot {it.name in active}?.forEach {it.delete()}
        }
    } }
    private fun task(block: suspend ()->Unit) {
        if(busy) return
        busy=true
        viewModelScope.launch {
            try { block() } catch(e: CancellationException) { throw e }
            catch(e: OutOfMemoryError) { message="Not enough memory. Try fewer or smaller source images." }
            catch(e: Exception) { message=e.message?:"Unable to process this image." }
            finally { busy=false }
        }
    }
    private fun thumbnail(file: File): Bitmap = PhotoProcessor.load(getApplication(),Uri.fromFile(file),160)
    private fun invalidate() { layout=null;preview=null;overflow=null }
    private fun save() {
        val json=JSONObject().put("mode",settings.mode.name).put("orientation",settings.orientation.name).put("margin",settings.margin).put("gx",settings.gapX).put("gy",settings.gapY).put("cut",settings.cut.name)
        val list=JSONArray()
        assets.forEach { a->list.put(JSONObject().put("id",a.request.id).put("name",a.name).put("w",a.request.size.width).put("h",a.request.size.height).put("copies",a.request.copies).put("auto",a.request.allowRotation).put("sw",a.sourceWidth).put("sh",a.sourceHeight).put("fit",a.fit.name).put("turns",a.turns).put("lock",a.lockRatio)) }
        json.put("assets",list);prefs.edit().putString("job",json.toString()).apply()
    }
    fun importImages(uris: List<Uri>,replaceId: String?=null,temporary: Boolean=false)=task {
        try {
            require(uris.isNotEmpty());require(uris.size+(if(replaceId==null) assets.size else assets.size-1)<=20) { "A job supports up to 20 different images." }
            val added=mutableListOf<SmartAsset>()
            try {
                for(uri in uris) {
                    val a=withContext(Dispatchers.IO) {
                        val bitmap=PhotoProcessor.load(getApplication(),uri,3508)
                        val id=UUID.randomUUID().toString();val file=File(dir,"$id.source")
                        try {
                            getApplication<Application>().contentResolver.openInputStream(uri)!!.use { input->file.outputStream().use { output->input.copyTo(output) } }
                            val ratio=bitmap.width.toDouble()/bitmap.height
                            val width=(40.0*ratio).coerceIn(10.0,100.0)
                            SmartAsset(SmartRequest(id,PhotoSize(width,(width/ratio).coerceIn(10.0,287.0))),"Image ${assets.size+added.size+1}",file,bitmap.width,bitmap.height,thumbnail(file))
                        } catch(t: Throwable) {file.delete();throw t} finally {bitmap.recycle()}
                    };added+=a
                }
            } catch(t: Throwable) { added.forEach { it.file.delete() };throw t }
            if(replaceId!=null) {
                val old=assets.single { it.request.id==replaceId };val fresh=added.single()
                assets=assets.map { if(it.request.id==replaceId) fresh.copy(name=old.name,request=old.request.copy(id=fresh.request.id),fit=old.fit,lockRatio=old.lockRatio) else it }
                old.file.delete()
            } else assets=assets+added
            if(replaceId==null) settings=settings.copy(mode=if(assets.size==1) SmartMode.MAXIMUM else SmartMode.BEST_FIT)
            invalidate();save()
        } finally { if(temporary) uris.filter { it.scheme=="file" }.forEach { it.path?.let { p->File(p).delete() } } }
    }
    fun update(id: String,size: PhotoSize,copies: Int,fit: ImageFit,lock: Boolean,auto: Boolean,name: String) {
        if(busy)return
        assets=assets.map { if(it.request.id==id) it.copy(request=it.request.copy(size=size,copies=copies,allowRotation=auto),fit=fit,lockRatio=lock,name=name.take(60).ifBlank { it.name }) else it };invalidate();save()
    }
    fun rotate(id: String) { if(busy)return;assets=assets.map { if(it.request.id==id) it.copy(turns=(it.turns+1)%4,request=it.request.copy(size=PhotoSize(it.request.size.height,it.request.size.width))) else it };invalidate();save() }
    fun remove(id: String) { if(busy)return;assets.singleOrNull { it.request.id==id }?.file?.delete();assets=assets.filterNot { it.request.id==id };invalidate();save() }
    fun clear() { if(busy)return;assets.forEach { it.file.delete() };File(getApplication<Application>().cacheDir,"exports").listFiles()?.filter {it.name.startsWith("SmartA4-")}?.forEach {it.delete()};assets=emptyList();settings=SmartSettings();invalidate();save() }
    fun configure(value: SmartSettings) { if(busy)return;settings=value;invalidate();save() }
    fun notify(value: String) { message=value }
    fun dismiss() {message=null}
    fun optimize()=task {
        invalidate()
        try {
            val result=withContext(Dispatchers.Default) { SmartLayoutEngine.calculate(assets.map { it.request },settings) }
            val image=withContext(Dispatchers.Default) { SmartRenderer.preview(getApplication(),result,assets) }
            layout=result;preview=image
        } catch(e: SmartOverflow) { overflow=e }
    }
    fun suggestedName(format: ExportFormat)="${ExportName.prefix("SmartA4")}${System.currentTimeMillis()}.${format.extension}"
    fun export(uri: Uri,format: ExportFormat)=task {
        val selected=layout?:error("Optimize and review the sheet first.")
        withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openOutputStream(uri,"wt")!!.buffered().use { SmartRenderer.write(getApplication(),it,selected,assets,format) } }
        message="${format.name} saved: one A4 page. Print at Actual size / 100%."
    }
    fun share(format: ExportFormat,ready: (Intent)->Unit)=task {
        val selected=layout?:error("Optimize first.")
        val app=getApplication<Application>()
        val file=withContext(Dispatchers.IO) {
            SheetExporter.cleanOldShares(app);val exports=File(app.cacheDir,"exports").apply { mkdirs() }
            val f=File.createTempFile("SmartA4-",".${format.extension}",exports)
            try {f.outputStream().buffered().use { SmartRenderer.write(app,it,selected,assets,format) };f} catch(t: Throwable) {f.delete();throw t}
        };ready(SheetExporter.shareIntent(app,file,format))
    }
}
