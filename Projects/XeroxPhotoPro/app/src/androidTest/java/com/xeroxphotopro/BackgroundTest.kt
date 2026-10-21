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
        store.savePhoto(bitmap);store.saveBackground(Color.BLUE)
        assertEquals(Color.BLUE, DraftStore(app).background())
        store.savePhoto(bitmap,resetBackground=false);assertEquals(Color.BLUE,store.background())
        store.savePhoto(bitmap);assertNull(store.background())
        store.saveBackground(Color.WHITE);store.delete();assertNull(store.background())
    }
    @Test fun bundledModelRejectsBlankImageWithoutNetwork() = runBlocking {
        val blank=Bitmap.createBitmap(400,600,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try { BackgroundProcessor.segment(blank); fail("Blank image must not become a replacement portrait") }
        catch (expected: IllegalStateException) { assertTrue(expected.message!!.contains("Could not separate")) }
    }
}
