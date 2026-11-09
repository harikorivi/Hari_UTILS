package com.xeroxphotopro

import android.app.Application
import android.graphics.*
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.xeroxphotopro.core.*
import com.xeroxphotopro.house.HouseViewModel
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.nio.ByteBuffer

class HouseWorkflowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var vm: HouseViewModel
    private fun idle() {compose.waitUntil(60000) {!vm.busy};compose.waitForIdle()}
    @Before fun setup() {
        compose.onNodeWithText("Telangana House Scheme Photo Copy Format").performScrollTo().performClick()
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[HouseViewModel::class.java]};idle()
        compose.runOnUiThread {vm.clear();vm.dismiss();vm.select(1,CutStyle.BLACK)};idle()
    }
    private fun import(w: Int=600,h: Int=400): ByteArray {
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {val p=Paint();p.color=Color.RED;drawRect(0f,0f,w/2f,h/2f,p);p.color=Color.GREEN;drawRect(w/2f,0f,w.toFloat(),h/2f,p);p.color=Color.BLUE;drawRect(0f,h/2f,w/2f,h.toFloat(),p);p.color=Color.YELLOW;drawRect(w/2f,h/2f,w.toFloat(),h.toFloat(),p)}
        val f=File(app.cacheDir,"house-source.png");f.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();val bytes=f.readBytes()
        compose.runOnUiThread {vm.importPhoto(Uri.fromFile(f))};idle();f.delete();return bytes
    }
    private fun validate(bytes: ByteArray,count: Int) {
        assertEquals("JFIF",String(bytes,6,4,Charsets.US_ASCII));assertEquals(1,bytes[13].toInt());assertEquals(300,ByteBuffer.wrap(bytes,14,2).short.toInt());assertEquals(300,ByteBuffer.wrap(bytes,16,2).short.toInt())
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        assertEquals(2480,bitmap.width);assertEquals(3508,bitmap.height);assertEquals(Color.WHITE,bitmap.getPixel(0,0))
        val colors=listOf(Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW)
        HouseLayout.calculate(count).placements.forEach {p->
            listOf(.25 to .25,.75 to .25,.25 to .75,.75 to .75).forEachIndexed {i,(x,y)->
                val c=bitmap.getPixel(((p.rect.x+p.rect.width*x)*300/25.4).toInt(),((p.rect.y+p.rect.height*y)*300/25.4).toInt())
                for(shift in listOf(16,8,0)) assertTrue(kotlin.math.abs(((c ushr shift) and 255)-((colors[i] ushr shift) and 255))<15)
            }
        };bitmap.recycle()
    }
    @Test fun copyControlsPreserveSourceAndExportThreeSeparateA4Jpegs() {
        val original=import();assertArrayEquals(original,File(app.filesDir,"house-scheme-photo.source").readBytes())
        assertEquals(CutStyle.BLACK,vm.cut)
        compose.onNodeWithText("Light grey").performScrollTo().performClick();idle();assertEquals(CutStyle.GRAY,vm.cut)
        compose.onNodeWithText("3 copies").performScrollTo().performClick();idle();assertEquals(3,vm.copies)
        compose.onNodeWithText("Save all 3 JPGs to folder").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithContentDescription("House scheme A4 preview").performScrollTo()
        val screenshot=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(app.getExternalFilesDir(null),"house-three-preview.png").outputStream().use {screenshot.compress(Bitmap.CompressFormat.PNG,100,it)};screenshot.recycle()
        val tree=DocumentsContract.buildTreeDocumentUri("com.xeroxphotopro.test.house-documents","root")
        app.startActivity(android.content.Intent().setClassName("com.xeroxphotopro.test","com.xeroxphotopro.HouseTestGrantActivity").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        compose.waitUntil(10000) { app.checkUriPermission(tree,android.os.Process.myPid(),android.os.Process.myUid(),android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==android.content.pm.PackageManager.PERMISSION_GRANTED }
        compose.waitForIdle()
        val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,"root")
        fun docs(): List<String> {
            val ids=mutableListOf<String>();app.contentResolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),null,null,null)!!.use {c->while(c.moveToNext()) ids+=c.getString(0)};return ids
        }
        docs().forEach {DocumentsContract.deleteDocument(app.contentResolver,DocumentsContract.buildDocumentUriUsingTree(tree,it))}
        compose.runOnUiThread {vm.saveAll(tree)};idle();assertTrue(vm.message!!.startsWith("Saved all 3"))
        val files=docs();assertEquals(3,files.size)
        for(n in 1..3) {
            val name=files.single {it.contains("-${n}-copy-")}
            val bytes=app.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree,name))!!.use {it.readBytes()};validate(bytes,n)
            File(app.getExternalFilesDir(null),"house-$n-copy.jpg").writeBytes(bytes)
        }
        compose.runOnUiThread {vm.dismiss()};compose.activityRule.scenario.recreate();idle();assertEquals(3,vm.copies);assertEquals(CutStyle.GRAY,vm.cut)
    }
    @Test fun nonSixByFourImageRemainsUncroppedAndSingleSaveWorks() {
        import(400,400)
        compose.onNodeWithText("This photo is not 6:4. The whole image is preserved with white space inside the frame; nothing is cropped or stretched.").performScrollTo().assertExists()
        val f=File(app.cacheDir,"house-single.jpg")
        compose.runOnUiThread {vm.save(Uri.fromFile(f))};idle();val b=BitmapFactory.decodeFile(f.path)
        val p=vm.layout.placements.first().rect
        val x=((p.x+2)*300/25.4).toInt();val y=((p.y+p.height/2)*300/25.4).toInt()
        assertEquals(Color.WHITE,b.getPixel(x,y));b.recycle();f.delete()
        compose.runOnUiThread {vm.dismiss();vm.clear()};assertNull(vm.preview);assertFalse(File(app.filesDir,"house-scheme-photo.source").exists())
    }
}
