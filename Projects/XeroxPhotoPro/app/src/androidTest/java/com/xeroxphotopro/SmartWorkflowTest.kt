package com.xeroxphotopro

import android.Manifest
import android.app.Application
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import com.xeroxphotopro.core.*
import com.xeroxphotopro.smart.*
import com.xeroxphotopro.export.ExportFormat
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class SmartWorkflowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule val cameraPermission=GrantPermissionRule.grant(Manifest.permission.CAMERA)
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var vm: SmartViewModel
    @Before fun setup() {
        compose.onNodeWithText("Smart A4 Photo Layout Maker").performClick()
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[SmartViewModel::class.java]};idle()
        compose.runOnUiThread {vm.clear();vm.dismiss()};idle()
    }
    private fun idle() {compose.waitUntil(60000) {!vm.busy};compose.waitForIdle()}
    private fun source(color: Int,w: Int=600,h: Int=400,format: Bitmap.CompressFormat=Bitmap.CompressFormat.PNG): File {
        val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).apply {eraseColor(color)}
        val f=File.createTempFile("smart-source-",".img",app.cacheDir)
        f.outputStream().use {b.compress(format,100,it)};b.recycle();return f
    }
    private fun add(colors: List<Int>) {
        val files=colors.map {source(it)}
        compose.runOnUiThread {vm.importImages(files.map {Uri.fromFile(it)})};idle();files.forEachIndexed { i,f->assertArrayEquals(f.readBytes(),vm.assets[i].file.readBytes());f.delete()}
        assertEquals(colors.size,vm.assets.size)
    }
    @Test fun singleMaximumAndExactCopiesThroughControls() {
        add(listOf(Color.RED))
        assertEquals(SmartMode.MAXIMUM,vm.settings.mode)
        compose.onNodeWithText("Edit Image 1").performScrollTo().performClick()
        compose.onNodeWithText("40 × 50").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Apply image settings").performScrollTo().performClick()
        assertEquals("Preset must apply before packing",PhotoSize(40.0,50.0),vm.assets.single().request.size)
        compose.onNodeWithText("Optimize layout").performClick();idle()
        assertEquals(20,vm.layout!!.placements.size)
        compose.onNodeWithText("Save JPEG").assertIsDisplayed()
        compose.onNodeWithText("Exact Copies + Best Fit").performScrollTo().performClick()
        compose.onNodeWithText("Copies").performScrollTo().performTextReplacement("10")
        compose.onNodeWithText("Apply image settings").performScrollTo().performClick()
        compose.onNodeWithText("Optimize layout").performClick();idle()
        assertEquals(10,vm.layout!!.placements.size)
        compose.activityRule.scenario.recreate();idle();assertEquals(10,vm.layout!!.placements.size)
        compose.onNodeWithText("Maximum Fit").performScrollTo().performClick()
        compose.onNodeWithText("Optimize layout").performClick();idle()
        assertEquals(20,vm.layout!!.placements.size)
    }
    @Test fun mixedThreeImagesPreviewExportsMetadataAndPassportIsolation() {
        lateinit var passport: PhotoViewModel
        compose.runOnUiThread {passport=ViewModelProvider(compose.activity)[PhotoViewModel::class.java]}
        compose.waitUntil(30000) {!passport.busy};val oldConfig=passport.config
        add(listOf(Color.RED,Color.BLUE,Color.GREEN))
        assertEquals(SmartMode.BEST_FIT,vm.settings.mode)
        val sizes=listOf(PhotoSize(),PhotoSize(40.0,50.0),PhotoSize(25.0,35.0));val counts=listOf(8,6,10)
        compose.runOnUiThread {vm.assets.toList().forEachIndexed {i,a->vm.update(a.request.id,sizes[i],counts[i],ImageFit.CROP,false,false,a.name)};vm.optimize()};idle()
        assertEquals(24,vm.layout!!.placements.size);assertEquals(oldConfig,passport.config)
        val screenshot=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(app.getExternalFilesDir(null),"smart-three-images.png").outputStream().use {screenshot.compress(Bitmap.CompressFormat.PNG,100,it)};screenshot.recycle()
        for(format in ExportFormat.values()) {
            val f=File(app.cacheDir,"smart-test.${format.extension}")
            compose.runOnUiThread {vm.export(Uri.fromFile(f),format)};idle()
            assertTrue(f.length()>1000)
            val image=if(format==ExportFormat.PDF) {
                PdfRenderer(ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY)).use {pdf->
                    assertEquals(1,pdf.pageCount);pdf.openPage(0).use {page->Bitmap.createBitmap(if(vm.layout!!.width==210.0) 2480 else 3508,if(vm.layout!!.height==297.0) 3508 else 2480,Bitmap.Config.ARGB_8888).also {page.render(it,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT)}}
                }
            } else BitmapFactory.decodeFile(f.path)
            val layout=vm.layout!!
            assertEquals((layout.width*300/25.4).roundToInt(),image.width);assertEquals((layout.height*300/25.4).roundToInt(),image.height)
            layout.placements.forEach {p->val index=vm.assets.indexOfFirst {it.request.id==p.id};val expected=listOf(Color.RED,Color.BLUE,Color.GREEN)[index]
                val actual=image.getPixel(((p.rect.x+p.rect.width/2)*300/25.4).roundToInt(),((p.rect.y+p.rect.height/2)*300/25.4).roundToInt())
                assertTrue(kotlin.math.abs(Color.red(actual)-Color.red(expected))<10 && kotlin.math.abs(Color.blue(actual)-Color.blue(expected))<10 && kotlin.math.abs(Color.green(actual)-Color.green(expected))<10)
            };image.recycle()
            val bytes=f.readBytes()
            if(format==ExportFormat.JPEG) {assertEquals(1,bytes[13].toInt());assertEquals(300,ByteBuffer.wrap(bytes,14,2).short.toInt())}
            if(format==ExportFormat.PNG) {val i=bytes.toString(Charsets.ISO_8859_1).indexOf("pHYs");assertTrue(i>0);assertEquals(11811,ByteBuffer.wrap(bytes,i+4,4).int)}
            f.copyTo(File(app.getExternalFilesDir(null),"smart-mixed.${format.extension}"),overwrite=true)
            compose.runOnUiThread {vm.dismiss()};f.delete()
        }
        compose.onNodeWithText("Home · switch workflow").performClick()
        compose.onNodeWithText("Passport Photo Maker").performClick()
        assertEquals(oldConfig,passport.config)
    }
    @Test fun overflowBlocksExportAndUserCanApplyAlternativeOrientation() {
        add(listOf(Color.RED))
        compose.runOnUiThread {
            val a=vm.assets.single();vm.update(a.request.id,PhotoSize(250.0,180.0),1,ImageFit.INSIDE,false,false,a.name)
            vm.configure(vm.settings.copy(mode=SmartMode.BEST_FIT,orientation=Orientation.PORTRAIT));vm.optimize()
        };idle();assertNull(vm.preview);assertNotNull(vm.overflow);assertNotNull(vm.overflow!!.alternative)
        compose.onNodeWithText("Save JPEG").assertDoesNotExist()
        compose.onNodeWithText("Use fitting alternative orientation").performScrollTo().performClick();idle()
        assertEquals(297.0,vm.layout!!.width,0.0)
    }
    @Test fun rotationMatchesPdfAndRasterAndFitPreservesTransparency() {
        val f=source(Color.TRANSPARENT,300,200)
        val b=BitmapFactory.decodeFile(f.path).copy(Bitmap.Config.ARGB_8888,true)
        Canvas(b).apply {drawColor(Color.BLUE);drawRect(0f,0f,300f,100f,Paint().apply {color=Color.RED})}
        f.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
        compose.runOnUiThread {vm.importImages(listOf(Uri.fromFile(f)))};idle();f.delete()
        compose.runOnUiThread {val a=vm.assets.single();vm.update(a.request.id,PhotoSize(250.0,180.0),1,ImageFit.INSIDE,false,true,a.name);vm.configure(vm.settings.copy(mode=SmartMode.BEST_FIT,orientation=Orientation.PORTRAIT));vm.optimize()};idle()
        assertTrue(vm.layout!!.placements.single().rotated)
        val p=vm.layout!!.placements.single().rect
        for(format in listOf(ExportFormat.PNG,ExportFormat.PDF)) {
            val output=File(app.cacheDir,"rotation.${format.extension}")
            output.outputStream().use {SmartRenderer.write(app,it,vm.layout!!,vm.assets,format)}
            val image=if(format==ExportFormat.PNG) BitmapFactory.decodeFile(output.path) else PdfRenderer(ParcelFileDescriptor.open(output,ParcelFileDescriptor.MODE_READ_ONLY)).use {pdf->pdf.openPage(0).use {page->Bitmap.createBitmap(2480,3508,Bitmap.Config.ARGB_8888).also {page.render(it,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT)}}}
            val left=image.getPixel(((p.x+p.width*.25)*300/25.4).toInt(),((p.y+p.height*.5)*300/25.4).toInt())
            val right=image.getPixel(((p.x+p.width*.75)*300/25.4).toInt(),((p.y+p.height*.5)*300/25.4).toInt())
            assertTrue(Color.blue(left)>240 && Color.red(left)<15);assertTrue(Color.red(right)>240 && Color.blue(right)<15);image.recycle();output.delete()
        }
        val transparent=source(Color.TRANSPARENT,100,50)
        compose.runOnUiThread {vm.importImages(listOf(Uri.fromFile(transparent)),vm.assets.single().request.id)};idle();transparent.delete()
        val asset=vm.assets.single();val result=SmartRenderer.renderImage(app,asset,100)
        assertEquals(Color.WHITE,result.getPixel(result.width/2,result.height/2));result.recycle()
        compose.runOnUiThread {vm.rotate(asset.request.id)};assertEquals(1,vm.assets.single().turns);assertEquals(180.0,vm.assets.single().request.size.width,0.0)
    }
    @Test fun orientationMetadataOriginalBytesFitModesAndDraftReload() {
        val f=source(Color.RED,200,100,Bitmap.CompressFormat.JPEG)
        android.media.ExifInterface(f.path).apply {setAttribute(android.media.ExifInterface.TAG_ORIENTATION,android.media.ExifInterface.ORIENTATION_ROTATE_90.toString());saveAttributes()}
        compose.runOnUiThread {vm.importImages(listOf(Uri.fromFile(f)))};idle()
        val a=vm.assets.single()
        assertArrayEquals(f.readBytes(),a.file.readBytes());f.delete()
        assertEquals(100,a.sourceWidth);assertEquals(200,a.sourceHeight)
        val frame=a.copy(request=a.request.copy(size=PhotoSize(50.0,50.0)),fit=ImageFit.INSIDE)
        val inside=SmartRenderer.renderImage(app,frame,100)
        assertEquals(Color.WHITE,inside.getPixel(2,inside.height/2))
        assertTrue(Color.red(inside.getPixel(inside.width/2,inside.height/2))>240);inside.recycle()
        val filled=SmartRenderer.renderImage(app,frame.copy(fit=ImageFit.CROP),100)
        assertTrue(Color.red(filled.getPixel(2,filled.height/2))>240);filled.recycle()
        lateinit var restored: SmartViewModel
        compose.runOnUiThread {restored=SmartViewModel(app)}
        compose.waitUntil(30000) {!restored.busy}
        assertEquals(vm.assets.map {it.request},restored.assets.map {it.request});assertEquals(vm.settings,restored.settings)
        var intent: android.content.Intent?=null
        compose.runOnUiThread {vm.optimize()};idle()
        compose.runOnUiThread {vm.share(ExportFormat.JPEG) {intent=it}};idle()
        @Suppress("DEPRECATION") val uri=intent!!.getParcelableExtra<Uri>(android.content.Intent.EXTRA_STREAM)!!
        val bytes=app.contentResolver.openInputStream(uri)!!.use {it.readBytes()}
        assertEquals(1,bytes[13].toInt());assertEquals(300,ByteBuffer.wrap(bytes,14,2).short.toInt())
    }
    @Test fun largeWebpAndReplacementRemovalAndCameraImport() {
        val f=source(Color.MAGENTA,4000,3000,Bitmap.CompressFormat.WEBP)
        compose.runOnUiThread {vm.importImages(listOf(Uri.fromFile(f)))};idle();f.delete()
        assertEquals(3508,maxOf(vm.assets.single().sourceWidth,vm.assets.single().sourceHeight))
        val id=vm.assets.single().request.id
        compose.runOnUiThread {vm.remove(id)};assertTrue(vm.assets.isEmpty())
        compose.onNodeWithText("Take photo").performScrollTo().performClick()
        compose.waitUntil(30000) {compose.onAllNodes(hasText("Take photo") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Take photo").performClick()
        compose.waitUntil(30000) {vm.assets.size==1 && !vm.busy};idle()
        assertTrue(vm.assets.single().sourceWidth>0)
    }
}
