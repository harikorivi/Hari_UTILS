package com.xeroxphotopro

import android.app.Application
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import com.xeroxphotopro.core.*
import com.xeroxphotopro.image.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

/** Optional local customer evidence. Never package these images in APK or source archive. */
class ReferencePhotoTest {
    @Test fun compareExportedReference() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val dir=app.getExternalFilesDir(null)!!
        val file=File(dir,"reference-sheet.jpg")
        assumeTrue("Needs the user's exported reference sheet",file.exists())
        val sheet=BitmapFactory.decodeFile(file.path)
        val source=Bitmap.createBitmap(sheet,71,1772,414,532);sheet.recycle()
        val crop=Crop(0.0,0.0,414.0,532.0)
        val start=System.nanoTime()
        val mask=BackgroundProcessor.segment(source)
        val before=BackgroundProcessor.replace(source,crop,414,532,mask,Color.rgb(217,234,247),decontaminate=false)
        val after=BackgroundProcessor.replace(source,crop,414,532,mask,Color.rgb(217,234,247))
        val elapsed=(System.nanoTime()-start)/1e6
        fun save(name: String, panels: List<Bitmap>, labels: List<String>) {
            val image=Bitmap.createBitmap(414*panels.size,582,Bitmap.Config.ARGB_8888)
            Canvas(image).apply {
                drawColor(Color.WHITE);val text=Paint().apply { color=Color.BLACK;textSize=17f }
                panels.forEachIndexed { i,b -> drawText(labels[i],i*414+8f,30f,text);drawBitmap(b,null,Rect(i*414,50,(i+1)*414,582),null) }
            }
            File(dir,name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        save("reference-edge-comparison.png",listOf(source,before,after),listOf("Exported reference (not original)","Previous edge composition","New conservative edge correction"))
        val detector=FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).build())
        try {
            val face=detector.process(InputImage.fromBitmap(source,0)).await().single()
            val b=face.boundingBox
            val old=CropEngine.aroundFace(414,532,PhotoSize(),FaceBox(b.left.toDouble(),b.top.toDouble(),b.right.toDouble(),b.bottom.toDouble()))
            val current=PhotoProcessor.faceCrop(source,PhotoSize())
            save("reference-framing-comparison.png",listOf(source,PhotoProcessor.render(source,old,PhotoSize()),PhotoProcessor.render(source,current.first,PhotoSize())),listOf("Exported reference (not original)","Previous box framing","New eye/chin framing"))
            var changed=0
            for(y in 0 until 532) for(x in 0 until 414) if(before.getPixel(x,y)!=after.getPixel(x,y)) changed++
            File(dir,"reference-metrics.txt").writeText("Export-derived reference, not an original camera photo.\nRaw/refined mask: ${mask.width} x ${mask.height}\nSegmentation and two composites: $elapsed ms (emulator only)\nChanged output pixels: $changed\nOld crop: $old\nNew crop: ${current.first}\nFraming guidance: ${current.second}\n")
        } finally { detector.close() }
    }
}
