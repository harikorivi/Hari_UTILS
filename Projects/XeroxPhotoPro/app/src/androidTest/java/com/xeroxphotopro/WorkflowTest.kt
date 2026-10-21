package com.xeroxphotopro

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xeroxphotopro.core.*
import com.xeroxphotopro.export.*
import com.xeroxphotopro.image.PhotoProcessor
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class WorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var vm: PhotoViewModel
    @Before fun reset() {
        compose.runOnUiThread { vm = ViewModelProvider(compose.activity)[PhotoViewModel::class.java] }
        idle()
        compose.runOnUiThread { vm.clearPhoto() }; idle()
        compose.runOnUiThread { vm.dismissMessage(); vm.dismissOverflow(); vm.configure(SheetConfig()); vm.screen = "setup" }
    }
    private fun idle() { compose.waitUntil(30000) { !vm.busy }; compose.waitForIdle() }
    private fun fixture(width: Int = 600, height: Int = 900): File {
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        android.graphics.Canvas(source).apply {
            drawColor(Color.rgb(20,100,90))
            val paint = android.graphics.Paint().apply { color = Color.WHITE }
            drawRect(width * .25f, height * .25f, width * .75f, height * .75f, paint)
        }
        val file = File.createTempFile("test-source-", ".jpg", app.cacheDir)
        file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 95, it) }; source.recycle()
        return file
    }
    private fun import(width: Int = 600, height: Int = 900) {
        val file = fixture(width,height)
        compose.runOnUiThread { vm.importPhoto(Uri.fromFile(file), onReady = { vm.screen = "preview" }) }; idle(); file.delete()
        assertNotNull(vm.prepared)
    }
    private fun screenshot(name: String) {
        compose.waitForIdle(); android.os.SystemClock.sleep(250)
        val image = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(app.getExternalFilesDir(null),name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
    }
    /** Optional private portrait smoke test; supply portrait.jpg in the app external files directory. */
    @Test fun realPortraitBackgroundWorkflow() {
        val file = File(app.getExternalFilesDir(null), "portrait-input.jpg")
        Assume.assumeTrue("Supply a consented private portrait for this optional visual test", file.exists())
        compose.runOnUiThread { vm.importPhoto(Uri.fromFile(file)) }; idle()
        val original = vm.photo!!
        compose.runOnUiThread { vm.setBackground(Color.WHITE) }; idle()
        assertEquals(Color.WHITE, vm.backgroundColor); assertSame(original, vm.photo)
        assertTrue(vm.checks.any { it.label == "Background replacement" })
        assertEquals(Color.WHITE, vm.prepared!!.getPixel(0,0))
        compose.runOnUiThread { vm.screen = "setup" }
        compose.onNodeWithText("Light blue").performScrollTo().performClick(); idle()
        assertEquals(Color.rgb(217,234,247), vm.prepared!!.getPixel(0,0))
        compose.runOnUiThread { vm.screen = "setup" }
        compose.onNodeWithText("Sheet portrait · inspect hair and shoulder edges before printing.").performScrollTo()
        screenshot("background-controls.png")
        for (format in ExportFormat.values()) {
            val destination = File(app.getExternalFilesDir(null), "background-real.${format.extension}")
            compose.runOnUiThread { vm.export(Uri.fromFile(destination),format) }; idle()
            assertTrue(destination.length()>0)
            compose.runOnUiThread { vm.dismissMessage() }
        }
        compose.runOnUiThread { vm.setBackground(null) }; idle()
        assertNull(vm.backgroundColor)
        val restored=PhotoProcessor.render(original,vm.crop!!,vm.config.photo)
        assertTrue(restored.sameAs(vm.prepared))
        compose.runOnUiThread { vm.setBackground(Color.WHITE) }; idle()
        compose.runOnUiThread { vm.rotate() }; idle()
        compose.runOnUiThread { vm.prepare {} }; idle()
        assertNotNull(vm.prepared);assertEquals(Color.WHITE,vm.backgroundColor)
        import();assertNull(vm.backgroundColor)
    }
    @Test fun optionalPersonNameIsUsedForSaveAndShareAndResetsWithPhoto() {
        import()
        compose.onNodeWithText("Person’s name (optional)").performTextInput("Hari Korivi")
        assertEquals("Hari Korivi",vm.personName)
        assertEquals("Hari Korivi",com.xeroxphotopro.storage.DraftStore(app).personName())
        for (format in ExportFormat.values()) {
            assertTrue(vm.suggestedFileName(format).startsWith("XeroxPhotoPro-Hari-Korivi-"))
            assertTrue(vm.suggestedFileName(format).endsWith(".${format.extension}"))
            var shared: Intent? = null
            compose.runOnUiThread { vm.share(format) { shared=it } }; idle()
            val uri=shared!!.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            app.contentResolver.query(uri,null,null,null,null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                val name=cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                assertTrue(name.startsWith("XeroxPhotoPro-Hari-Korivi-"))
                assertTrue(name.endsWith(".${format.extension}"))
            }
        }
        compose.activityRule.scenario.recreate(); idle()
        compose.runOnUiThread { vm=ViewModelProvider(compose.activity)[PhotoViewModel::class.java] }
        assertEquals("Hari Korivi",vm.personName)
        compose.runOnUiThread { vm.rotate() }; idle()
        assertEquals("Hari Korivi",vm.personName)
        import();assertEquals("",vm.personName)
        compose.runOnUiThread { vm.updatePersonName("Next Person"); vm.clearPhoto() }; idle()
        assertEquals("",vm.personName)
        assertEquals("",com.xeroxphotopro.storage.DraftStore(app).personName())
    }
    @Test fun legacySettingsMigrateAndFractionalCustomSizePersistsExactly() {
        val prefs=app.getSharedPreferences("shop",0)
        prefs.edit().clear().putFloat("width",50f).putFloat("height",50f).putInt("copies",24).putBoolean("landscape",true).commit()
        val store=com.xeroxphotopro.storage.DraftStore(app)
        val migrated=store.settings()
        assertEquals(Orientation.AUTO,migrated.orientation); assertNull(migrated.copies)
        assertEquals(15,LayoutEngine.calculate(migrated).placements.size)
        val custom=SheetConfig(PhotoSize(32.1,41.7),copies=3)
        store.saveSettings(custom); store.saveCrop(1.5,.25,.75)
        assertEquals(custom,store.settings())
        assertEquals(Triple(1.5,.25,.75),store.savedCrop())
        store.saveSettings(SheetConfig())
    }
    @Test fun launchShowsCaptureGalleryAndMaximum() {
        compose.onNodeWithText("Take photo").assertIsDisplayed()
        compose.onNodeWithText("Gallery").assertIsDisplayed()
        compose.onNodeWithText("Selected copies: 30 • Maximum: 30").performScrollTo().assertIsDisplayed()
        assertNull(vm.config.copies)
    }
    @Test fun importAutomaticallyPreviewsMaximumAndReportsQuality() {
        import()
        compose.onNodeWithText("Your A4 sheet").assertIsDisplayed()
        assertEquals(30,vm.layout.placements.size)
        assertEquals(Orientation.PORTRAIT,vm.layout.grid.orientation)
        assertTrue(vm.checks.any { it.label=="Face count" && it.status==CheckStatus.REVIEW })
        assertTrue(vm.checks.any { it.label=="Resolution" })
        screenshot("maximum-preview.png")
        compose.onNodeWithText("Selected copies: 30 • Maximum: 30").performScrollTo().assertIsDisplayed()
    }
    @Test fun overflowIsRejectedAndUseMaximumAppliesPendingOrientation() {
        import()
        compose.runOnUiThread { assertFalse(vm.configure(vm.config.copy(photo=PhotoSize(50.0,50.0),copies=24,orientation=Orientation.LANDSCAPE))) }
        assertEquals(30,vm.layout.placements.size)
        compose.onNodeWithText("Use maximum (15)").assertIsDisplayed().performClick()
        assertEquals(15,vm.layout.placements.size)
        assertEquals(PhotoSize(50.0,50.0),vm.config.photo)
        compose.runOnUiThread { vm.prepare { vm.screen="preview" } }; idle()
        assertEquals(Orientation.LANDSCAPE,vm.layout.grid.orientation)
    }
    @Test fun exportAllFormatsIsSingleA4WithDensityAndRepeatedExportsSucceed() {
        import()
        for (orientation in listOf(Orientation.PORTRAIT,Orientation.LANDSCAPE)) {
            compose.runOnUiThread { vm.configure(vm.config.copy(orientation=orientation)) }
            for (format in ExportFormat.values()) {
                val file=File(app.cacheDir,"test-sheet.${format.extension}")
                compose.runOnUiThread { vm.export(Uri.fromFile(file),format) }; idle()
                assertTrue("Export failed: ${vm.message}",file.length()>1000)
                if(format==ExportFormat.PDF) {
                    PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                        assertEquals(1,pdf.pageCount)
                        pdf.openPage(0).use { assertTrue(it.width in if(orientation==Orientation.PORTRAIT) 595..596 else 841..842) }
                    }
                } else {
                    val bitmap=BitmapFactory.decodeFile(file.path)
                    assertNotNull(bitmap)
                    assertEquals(if(orientation==Orientation.PORTRAIT) 2480 else 3508,bitmap.width)
                    assertEquals(if(orientation==Orientation.PORTRAIT) 3508 else 2480,bitmap.height)
                    val p=vm.layout.placements.first(); val scale=300.0/25.4
                    assertNotEquals(Color.WHITE,bitmap.getPixel(((p.x+1)*scale).toInt(),((p.y+1)*scale).toInt()))
                    bitmap.recycle()
                    val bytes=file.readBytes()
                    if(format==ExportFormat.JPEG) {
                        assertEquals(300,ByteBuffer.wrap(bytes,14,2).short.toInt())
                        assertEquals(300,ByteBuffer.wrap(bytes,16,2).short.toInt())
                    } else {
                        val position=bytes.toString(Charsets.ISO_8859_1).indexOf("pHYs")
                        assertTrue(position>0); assertEquals(11811,ByteBuffer.wrap(bytes,position+4,4).int)
                    }
                }
                // Keep synthetic output as test evidence, never the supplied customer portrait.
                file.copyTo(File(app.getExternalFilesDir(null),"${orientation.name.lowercase()}.${format.extension}"),overwrite=true)
                file.delete()
                compose.runOnUiThread { vm.dismissMessage() }
            }
        }
        assertFalse(app.cacheDir.listFiles()!!.any { it.name.startsWith("sheet-encoding-") })
    }
    @Test fun shareUsesRestrictedContentUriWithReadGrantAndDecodableFile() {
        import()
        var intent: Intent?=null
        compose.runOnUiThread { vm.share(ExportFormat.PNG) { intent=it } }; idle()
        assertEquals("image/png",intent!!.type)
        assertTrue(intent!!.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION") val uri=intent!!.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content",uri.scheme); assertEquals("com.xeroxphotopro.exports",uri.authority)
        app.contentResolver.openInputStream(uri).use { stream -> val bitmap=BitmapFactory.decodeStream(stream); assertEquals(2480,bitmap.width); bitmap.recycle() }
        val info=app.packageManager.resolveContentProvider(uri.authority!!,0)!!
        assertFalse(info.exported); assertTrue(info.grantUriPermissions)
    }
    @Test fun orientationAndActivityRecreationRetainCropAndReducedCount() {
        import()
        compose.runOnUiThread {
            vm.configure(vm.config.copy(copies=8,orientation=Orientation.LANDSCAPE)); vm.adjust(1.4,.3,.7)
            vm.prepare { vm.screen="preview" }
        }; idle()
        val selected=vm.crop
        compose.activityRule.scenario.recreate(); idle()
        lateinit var retained: PhotoViewModel
        compose.runOnUiThread { retained=ViewModelProvider(compose.activity)[PhotoViewModel::class.java] }
        assertSame(vm,retained); assertEquals(selected,retained.crop)
        assertEquals(8,retained.layout.placements.size); assertEquals(Orientation.LANDSCAPE,retained.config.orientation)
        compose.runOnUiThread { compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        idle(); assertEquals(selected,vm.crop); assertEquals(8,vm.layout.placements.size)
        compose.runOnUiThread { compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
    @Test fun lowResolutionAndLargeSourceImportsAreHandled() {
        import(120,160)
        assertTrue(vm.effectiveDpi<300)
        assertEquals(CheckStatus.REVIEW,vm.checks.first { it.label=="Resolution" }.status)
        import(4000,3000)
        assertEquals(2400,maxOf(vm.photo!!.width,vm.photo!!.height))
        assertEquals(30,vm.layout.placements.size)
    }
    @Test fun customSinglePhotoAndMinMarginsPreserveSize() {
        import()
        compose.runOnUiThread { vm.configure(SheetConfig(PhotoSize(190.0,280.0),margin=2.0)) }
        assertEquals(1,vm.layout.placements.size)
        compose.runOnUiThread { vm.prepare { vm.screen="preview" } }; idle()
        val file=File(app.cacheDir,"custom.pdf")
        compose.runOnUiThread { vm.export(Uri.fromFile(file),ExportFormat.PDF) }; idle()
        PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use { assertEquals(1,it.pageCount) }
        file.delete()
    }
}
