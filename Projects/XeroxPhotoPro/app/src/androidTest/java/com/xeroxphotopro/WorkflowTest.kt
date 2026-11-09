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
import androidx.compose.ui.unit.dp
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
        compose.onNodeWithText("Passport Photo Maker").performClick()
        compose.runOnUiThread { vm = ViewModelProvider(compose.activity)[PhotoViewModel::class.java] }
        idle()
        compose.runOnUiThread { vm.clearPhoto() }; idle()
        compose.runOnUiThread { vm.updateTwoPhotoMode(false); vm.dismissMessage(); vm.dismissOverflow(); vm.configure(SheetConfig()); vm.screen = "setup" }
    }
    @Test fun customFramingTargetsPersistAndGuidesToggle() {
        import()
        compose.runOnUiThread { vm.screen="setup" }
        compose.onNodeWithText("Custom crop").performScrollTo().performClick()
        compose.onNodeWithText("Show framing guides").performScrollTo().performClick()
        compose.onNodeWithText("Custom framing targets").performScrollTo().performClick()
        compose.onNodeWithText("Eye line from top (%) · 32–48").performScrollTo().performTextReplacement("42")
        compose.onNodeWithText("Eye-to-chin height (%) · 24–40").performScrollTo().performTextReplacement("30")
        compose.onNodeWithText("Apply targets & auto frame").performScrollTo().performClick();idle()
        assertEquals(FramingTarget(.42,.30),vm.framingTarget)
        assertEquals(vm.framingTarget,com.xeroxphotopro.storage.DraftStore(app).framing())
        compose.onNodeWithText("Reset framing targets").performScrollTo().performClick();idle()
        assertEquals(FramingTarget(),vm.framingTarget)
    }
    @Test fun twoPhotoSheetKeepsIndependentDraftsAndExportsAllFormats() {
        lateinit var second: PhotoViewModel
        compose.runOnUiThread { second=ViewModelProvider(compose.activity).get("second-photo",PhotoViewModel::class.java) }
        compose.waitUntil(30000) { !second.busy }
        compose.runOnUiThread { second.clearPhoto() }
        compose.waitUntil(30000) { !second.busy }
        compose.runOnUiThread { second.dismissMessage() }
        compose.onNodeWithText("Custom sheet mode").performClick()
        compose.onNodeWithText("Two photos · 15 + 15").performClick()
        assertTrue(vm.twoPhotoMode)
        compose.onNodeWithText("Add and review both photos to finish the sheet.").assertExists()
        compose.onNodeWithText("Save JPEG").assertDoesNotExist()
        listOf(vm,second).forEachIndexed { index,target ->
            val bitmap=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888).apply { eraseColor(if(index==0) Color.RED else Color.BLUE) }
            val file=File(app.cacheDir,"slot-$index.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,100,it) };bitmap.recycle()
            compose.runOnUiThread { target.importPhoto(Uri.fromFile(file)) }
            compose.waitUntil(30000) { !target.busy };file.delete()
            compose.runOnUiThread { target.updatePersonName(if(index==0) "Alice" else "Bob") }
        }
        assertEquals("Alice",com.xeroxphotopro.storage.DraftStore(app).personName())
        assertEquals("Bob",com.xeroxphotopro.storage.DraftStore(app,1).personName())
        compose.onNodeWithText("Bottom photo · 15 copies — Edit").performScrollTo().performClick()
        compose.onNodeWithText("Bob").performScrollTo().assertExists()
        compose.onNodeWithText("Top photo · 15 copies — Edit").performScrollTo().performClick()
        compose.onNodeWithText("Alice").performScrollTo().assertExists()
        val first=vm.prepared
        compose.runOnUiThread { second.adjust(1.2); second.prepare { } }
        compose.waitUntil(30000) { !second.busy }
        assertSame(first,vm.prepared)
        compose.runOnUiThread { vm.screen="preview" };idle()
        compose.onNodeWithText("Save JPEG").assertIsDisplayed()
        assertTrue(vm.suggestedFileName(ExportFormat.JPEG,second.personName).contains("Alice-Bob"))
        compose.onNodeWithText("Close · Custom sheet mode").performScrollTo().performClick()
        compose.onNodeWithText("Top three rows: photo 1. Bottom three rows: photo 2. Safe margins and cutting gaps are retained.").performScrollTo()
        screenshot("two-photo-sheet.png")
        for(format in ExportFormat.values()) {
            val file=File(app.cacheDir,"two-photo.${format.extension}")
            file.outputStream().use { SheetExporter.write(app,it,listOf(vm.prepared!!,second.prepared!!),vm.layout,format) }
            val sheet=if(format==ExportFormat.PDF) {
                PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                    assertEquals(1,pdf.pageCount)
                    pdf.openPage(0).use { page ->
                        Bitmap.createBitmap(2480,3508,Bitmap.Config.ARGB_8888).also { page.render(it,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT) }
                    }
                }
            } else BitmapFactory.decodeFile(file.path)
            assertEquals(2480,sheet.width);assertEquals(3508,sheet.height)
            vm.layout.placements.forEachIndexed { index,p ->
                val color=sheet.getPixel(pixelsFor(p.x+p.width/2),pixelsFor(p.y+p.height/2))
                if(index<15) assertTrue(Color.red(color)>230 && Color.blue(color)<25) else assertTrue(Color.blue(color)>230 && Color.red(color)<25)
            }
            sheet.recycle();file.delete()
        }
        compose.activityRule.scenario.recreate();idle()
        assertEquals("Bob",second.personName)
        compose.runOnUiThread { vm.updateTwoPhotoMode(false);second.updateTwoPhotoMode(false);second.clearPhoto();vm.screen="setup" }
        compose.waitUntil(30000) { !second.busy }
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
        compose.onNodeWithText("Custom background").performScrollTo().performClick()
        compose.onNodeWithText("Light blue").performScrollTo().performClick(); idle()
        assertEquals(Color.rgb(217,234,247), vm.prepared!!.getPixel(0,0))
        compose.runOnUiThread { vm.screen = "setup" }
        compose.onNodeWithText("Compare with original").performScrollTo()
        screenshot("background-controls.png")
        compose.onNodeWithContentDescription("Enlarge portrait").performScrollTo().performClick()
        compose.onNodeWithText("Detail zoom").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Compare with original").performScrollTo().performClick()
        compose.onNodeWithText("Before · original photo").assertIsDisplayed()
        assertEquals(Color.rgb(217,234,247),vm.backgroundColor)
        compose.onNodeWithText("Show edited photo").performScrollTo().performClick()
        compose.onNodeWithText("Refine cutout").performScrollTo().performClick()
        compose.onNodeWithText("Cleaner edge").performScrollTo().performClick(); idle()
        assertEquals(com.xeroxphotopro.image.EdgeMode.CLEAN,vm.edgeMode)
        compose.onNodeWithText("Natural").performScrollTo().performClick(); idle()
        compose.onNodeWithText("Repair ears / edges").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Repair canvas").performTouchInput { swipe(androidx.compose.ui.geometry.Offset(width*.3f,height*.5f),androidx.compose.ui.geometry.Offset(width*.4f,height*.5f),300) }
        compose.onNodeWithText("1/64 strokes · Restore").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(vm.repairs.isEmpty())
        compose.onNodeWithText("Repair ears / edges").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Repair canvas").performTouchInput { swipe(androidx.compose.ui.geometry.Offset(width*.3f,height*.5f),androidx.compose.ui.geometry.Offset(width*.4f,height*.5f),300) }
        compose.onNodeWithText("Undo stroke").performClick()
        compose.onNodeWithText("0/64 strokes · Restore").assertExists()
        compose.onNodeWithContentDescription("Repair canvas").performTouchInput { swipe(androidx.compose.ui.geometry.Offset(width*.3f,height*.5f),androidx.compose.ui.geometry.Offset(width*.4f,height*.5f),300) }
        screenshot("ear-repair.png")
        compose.onNodeWithText("Apply repair").assertIsDisplayed()
        compose.activityRule.scenario.recreate(); idle()
        compose.onNodeWithText("1/64 strokes · Restore").assertExists()
        compose.runOnUiThread { compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE }; idle()
        compose.onNodeWithText("Apply repair").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Repair canvas").assertHeightIsAtLeast(100.dp)
        screenshot("ear-repair-landscape.png")
        compose.runOnUiThread { compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT }; idle()
        compose.onNodeWithText("1/64 strokes · Restore").assertExists()
        compose.waitUntil(30000) { compose.onAllNodes(hasText("Apply repair") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithText("Apply repair").assertIsEnabled().performClick(); idle()
        assertEquals(1,vm.repairs.size)
        assertEquals(vm.repairs,com.xeroxphotopro.storage.DraftStore(app).repairs())
        vm.prepared!!.let { result -> File(app.getExternalFilesDir(null),"refined-portrait.png").outputStream().use { result.compress(Bitmap.CompressFormat.PNG,100,it) } }
        val raw=kotlinx.coroutines.runBlocking { com.xeroxphotopro.image.BackgroundProcessor.rawMask(original) }
        val baseline=com.xeroxphotopro.image.BackgroundProcessor.replace(vm.originalPrepared!!,vm.crop!!,original.width,original.height,raw,Color.rgb(217,234,247),com.xeroxphotopro.image.EdgeMode.SOFT)
        File(app.getExternalFilesDir(null),"baseline-portrait.png").outputStream().use { baseline.compress(Bitmap.CompressFormat.PNG,100,it) }
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
        import();assertNull(vm.backgroundColor);assertTrue(vm.repairs.isEmpty())
    }
    @Test fun optionalPersonNameIsUsedForSaveAndShareAndResetsWithPhoto() {
        import()
        compose.onNodeWithText("Person’s name (optional)").performScrollTo().performTextInput("Hari Korivi")
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
    @Test fun fifteenCopiesAreTopAlignedAndCustomControlsAreOptIn() {
        import()
        compose.onNodeWithText("Copies").performTextReplacement("15")
        compose.onNodeWithText("Apply copies").performClick(); idle()
        assertEquals(15,vm.layout.placements.size)
        assertEquals(3,vm.layout.rows)
        assertEquals(6.0,vm.layout.placements.first().y,0.0)
        compose.onNodeWithText("15 photos · 5 columns × 3 rows").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save JPEG").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText("Width (mm)").assertDoesNotExist()
        compose.onNodeWithText("Preview zoom").assertDoesNotExist()
        screenshot("simple-15-preview.png")
        // Inspect the actual rendered output, including the complete blank lower section.
        for (format in ExportFormat.values()) {
            val file=File(app.getExternalFilesDir(null),"packed-15.${format.extension}")
            compose.runOnUiThread { vm.export(Uri.fromFile(file),format) }; idle()
            val image=if (format==ExportFormat.PDF) {
                PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                    assertEquals(1,pdf.pageCount)
                    pdf.openPage(0).use { page -> Bitmap.createBitmap(2480,3508,Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.WHITE);page.render(it,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    } }
                }
            } else BitmapFactory.decodeFile(file.path)
            val scale=300.0/25.4
            assertNotEquals(Color.WHITE,image.getPixel((8*scale).toInt(),(8*scale).toInt()))
            assertEquals(Color.WHITE,image.getPixel((100*scale).toInt(),(200*scale).toInt()))
            image.recycle()
            compose.runOnUiThread { vm.dismissMessage() }
        }
        compose.onNodeWithText("Custom photo / layout").performScrollTo().performClick()
        compose.onNodeWithText("Custom size").performScrollTo().performClick()
        compose.onNodeWithText("Width (mm)").performScrollTo().performTextReplacement("50")
        compose.onNodeWithText("Height (mm)").performScrollTo().performTextReplacement("50")
        compose.onNodeWithText("Apply size").performScrollTo().performClick(); idle()
        assertEquals(PhotoSize(50.0,50.0),vm.config.photo)
        compose.onNodeWithText("Custom sheet layout").performScrollTo().performClick()
        val before=vm.config.border
        compose.onNodeWithText("Thin gray inside border").performScrollTo().performClick()
        assertEquals(!before,vm.config.border)
        screenshot("custom-controls.png")
        import();assertEquals(PhotoSize(),vm.config.photo)
    }
    @Test fun launchShowsCaptureGalleryAndMaximum() {
        compose.onNodeWithText("Take photo").assertIsDisplayed()
        compose.onNodeWithText("Gallery").assertIsDisplayed()
        compose.onNodeWithText("Photo size: 35 × 45 mm").assertIsDisplayed()
        compose.onNodeWithText("Width (mm)").assertDoesNotExist()
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
                        assertEquals(1,bytes[13].toInt())
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
        for(format in listOf(ExportFormat.JPEG,ExportFormat.PNG)) {
            compose.runOnUiThread { vm.share(format) { intent=it } };idle()
            @Suppress("DEPRECATION") val shared=intent!!.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            val bytes=app.contentResolver.openInputStream(shared)!!.use { it.readBytes() }
            if(format==ExportFormat.JPEG) {
                assertEquals(1,bytes[13].toInt());assertEquals(300,ByteBuffer.wrap(bytes,14,2).short.toInt());assertEquals(300,ByteBuffer.wrap(bytes,16,2).short.toInt())
            } else {
                val offset=bytes.toString(Charsets.ISO_8859_1).indexOf("pHYs")
                assertTrue(offset>0);assertEquals(11811,ByteBuffer.wrap(bytes,offset+4,4).int);assertEquals(1,bytes[offset+12].toInt())
            }
        }
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
