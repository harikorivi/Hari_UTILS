package com.xeroxphotopro

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xeroxphotopro.core.*
import com.xeroxphotopro.image.*
import com.xeroxphotopro.export.*
import com.xeroxphotopro.storage.DraftStore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackgroundTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Test fun repairedEarRestoresOriginalPixelsEvenInCleanerMode() {
        val original=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val mask=PersonMask(100,100,FloatArray(10000))
        val stroke=MaskStroke(listOf(BrushPoint(.2,.5),BrushPoint(.3,.5)),.05,true)
        for (mode in EdgeMode.values()) {
            val result=BackgroundProcessor.replace(original,Crop(0.0,0.0,100.0,100.0),100,100,mask,Color.BLUE,mode,listOf(stroke))
            assertEquals(Color.RED,result.getPixel(25,50))
            assertEquals(Color.BLUE,result.getPixel(75,50))
            assertEquals(Color.RED,original.getPixel(75,50))
        }
        val store=DraftStore(app)
        store.savePhoto(original);store.saveRepairs(listOf(stroke))
        assertEquals(listOf(stroke),DraftStore(app).repairs())
        store.savePhoto(original,resetBackground=false);assertEquals(listOf(stroke),store.repairs())
        store.savePhoto(original);assertTrue(store.repairs().isEmpty())
        store.saveRepairs(listOf(stroke));store.delete();assertTrue(store.repairs().isEmpty())
    }
    @Test fun colorGuidanceAlignsUncertainMaskAndRetainsConfidentInterior() {
        val source=Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(source)
        canvas.drawColor(Color.BLUE)
        canvas.drawRect(40f,0f,80f,40f,android.graphics.Paint().apply { color=Color.RED })
        val coarse=PersonMask(4,2,floatArrayOf(0f,.25f,.75f,1f,0f,.25f,.75f,1f))
        val refined=BackgroundProcessor.refine(source,coarse)
        assertTrue(refined.sample(.45,.5)<coarse.sample(.45,.5))
        assertTrue(refined.sample(.55,.5)>coarse.sample(.55,.5))
        assertEquals(0f,refined.sample(.01,.5),.001f)
        assertEquals(1f,refined.sample(.99,.5),.001f)
        assertTrue(refined.confidence.all { it.isFinite() && it in 0f..1f })
        assertTrue(BackgroundProcessor.edgeAlpha(.08f,EdgeMode.CLEAN)<BackgroundProcessor.edgeAlpha(.08f,EdgeMode.SOFT))
        for (i in 0..100) {
            val confidence=i/100f
            assertTrue(BackgroundProcessor.edgeAlpha(confidence,EdgeMode.CLEAN)<=BackgroundProcessor.edgeAlpha(confidence,EdgeMode.NATURAL)+.0001f)
        }
        for(mode in EdgeMode.values()) {
            assertEquals(0f,BackgroundProcessor.edgeAlpha(0f,mode),0f)
            assertEquals(1f,BackgroundProcessor.edgeAlpha(1f,mode),0f)
        }
    }
    @Test fun maskInterpolationCropAlignmentAndOriginalPixels() {
        val source = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val mask = PersonMask(4, 2, floatArrayOf(0f,0f,1f,1f,0f,0f,1f,1f))
        val result = BackgroundProcessor.replace(source,Crop(0.0,0.0,4.0,2.0),4,2,mask,Color.BLUE)
        assertEquals(Color.BLUE,result.getPixel(0,0)); assertEquals(Color.RED,result.getPixel(3,0))
        assertEquals(Color.RED,source.getPixel(0,0))
        assertEquals(.5f,mask.sample(.5,.5),.0001f)
        val crop = Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val shifted = BackgroundProcessor.replace(crop,Crop(2.0,0.0,2.0,2.0),4,2,mask,Color.BLUE)
        assertEquals(Color.RED,shifted.getPixel(0,0))
    }
    @Test fun replacementColorReachesEveryExport() {
        val source = Bitmap.createBitmap(350,450,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val edited = BackgroundProcessor.replace(source,Crop(0.0,0.0,350.0,450.0),350,450,
            PersonMask(2,2,floatArrayOf(0f,1f,0f,1f)),Color.BLUE)
        val layout = LayoutEngine.calculate(SheetConfig(copies=1))
        for (format in ExportFormat.values()) {
            val file=File(app.cacheDir,"background-test.${format.extension}")
            file.outputStream().use { SheetExporter.write(app,it,edited,layout,format) }
            val bitmap = if (format == ExportFormat.PDF) {
                PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                    assertEquals(1,pdf.pageCount)
                    pdf.openPage(0).use { page -> Bitmap.createBitmap(2480,3508,Bitmap.Config.ARGB_8888).also {
                        page.render(it,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    } }
                }
            } else BitmapFactory.decodeFile(file.path)
            // Find a blue background pixel and a preserved red foreground pixel on the sheet.
            var blue=false; var red=false
            for (y in 0 until bitmap.height step 8) for (x in 0 until bitmap.width step 8) {
                val c=bitmap.getPixel(x,y)
                if(Color.blue(c)>230 && Color.red(c)<25) blue=true
                if(Color.red(c)>230 && Color.blue(c)<25) red=true
            }
            assertTrue("${format.name} background",blue); assertTrue("${format.name} foreground",red)
            bitmap.recycle();file.delete()
        }
    }
    @Test fun backgroundPersistenceResetAndRotationSave() {
        val store=DraftStore(app)
        val bitmap=Bitmap.createBitmap(30,40,Bitmap.Config.ARGB_8888)
        store.savePhoto(bitmap);store.saveBackground(Color.BLUE);store.saveEdgeMode(EdgeMode.CLEAN)
        assertEquals(Color.BLUE, DraftStore(app).background());assertEquals(EdgeMode.CLEAN,DraftStore(app).edgeMode())
        store.savePhoto(bitmap,resetBackground=false);assertEquals(Color.BLUE,store.background());assertEquals(EdgeMode.CLEAN,store.edgeMode())
        store.savePhoto(bitmap);assertNull(store.background());assertEquals(EdgeMode.NATURAL,store.edgeMode())
        store.saveBackground(Color.WHITE);store.delete();assertNull(store.background())
    }
    @Test fun bundledModelRejectsBlankImageWithoutNetwork() = runBlocking {
        val blank=Bitmap.createBitmap(400,600,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try { BackgroundProcessor.segment(blank); fail("Blank image must not become a replacement portrait") }
        catch (expected: IllegalStateException) { assertTrue(expected.message!!.contains("Could not separate")) }
    }
}
