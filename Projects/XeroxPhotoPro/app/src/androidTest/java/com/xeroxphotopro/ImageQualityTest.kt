package com.xeroxphotopro

import android.app.Application
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import com.xeroxphotopro.core.*
import com.xeroxphotopro.image.*
import com.xeroxphotopro.storage.DraftStore
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import kotlin.math.*

class ImageQualityTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    @Test fun controlledMatteReducesHaloAndPreservesOpaqueInterior() {
        val w=240;val h=320
        val source=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val alpha=FloatArray(w*h)
        for(y in 0 until h) for(x in 0 until w) {
            val distance=sqrt(((x-120)*(x-120)+(y-160)*(y-160)).toDouble())
            val a=((84-distance)/8).coerceIn(0.0,1.0)
            alpha[y*w+x]=a.toFloat()
            val c=(30*a+255*(1-a)).roundToInt();source.setPixel(x,y,Color.rgb(c,c,c))
        }
        val mask=PersonMask(w,h,alpha);val crop=Crop(0.0,0.0,w.toDouble(),h.toDouble())
        val before=BackgroundProcessor.replace(source,crop,w,h,mask,Color.rgb(160,205,230),EdgeMode.SOFT,decontaminate=false)
        val start=System.nanoTime()
        val after=BackgroundProcessor.replace(source,crop,w,h,mask,Color.rgb(160,205,230),EdgeMode.SOFT)
        var oldError=0.0;var newError=0.0
        for(y in 0 until h) for(x in 0 until w) {
            val a=alpha[y*w+x]
            if(a==1f) assertEquals(source.getPixel(x,y),after.getPixel(x,y))
            if(a in .12f.. .94f) {
                val expected=30*a+160*(1-a)
                oldError+=abs(Color.red(before.getPixel(x,y))-expected)
                newError+=abs(Color.red(after.getPixel(x,y))-expected)
            }
        }
        assertTrue("Expected lower color error: $newError < $oldError",newError<oldError*.85)
        val dir=app.getExternalFilesDir(null)!!
        File(dir,"quality-metrics.txt").writeText("Controlled matte absolute red-channel error: before=$oldError after=$newError\nComposite plus pixel verification elapsed ms=${(System.nanoTime()-start)/1e6}\nSynthetic benchmark, not proof of real hair/ear recovery.\n")
        val comparison=Bitmap.createBitmap(w*3,h+40,Bitmap.Config.ARGB_8888)
        Canvas(comparison).apply {
            drawColor(Color.WHITE)
            val paint=Paint().apply { color=Color.BLACK;textSize=17f }
            listOf(source,before,after).forEachIndexed { index,b ->
                drawText(listOf("Source (synthetic)","Before","After")[index],index*w+8f,26f,paint)
                drawBitmap(b,index*w.toFloat(),40f,null)
            }
        }
        File(dir,"edge-comparison.png").outputStream().use { comparison.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun draftSaveIsLosslessAndTargetsSurviveReload() {
        val bitmap=Bitmap.createBitmap(40,50,Bitmap.Config.ARGB_8888)
        for(y in 0 until 50) for(x in 0 until 40) bitmap.setPixel(x,y,Color.rgb(x*6,y*5,(x+y)*2))
        val store=DraftStore(app,1);store.savePhoto(bitmap)
        store.saveFraming(FramingTarget(.48,.40))
        assertEquals(FramingTarget(.48,.40),DraftStore(app,1).framing())
        val restored=store.loadPhoto()!!
        for(y in 0 until 50) for(x in 0 until 40) assertEquals(bitmap.getPixel(x,y),restored.getPixel(x,y))
        store.delete()
    }
}
