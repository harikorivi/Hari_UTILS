package com.printassist.android

import android.print.*
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class NativePrintTest {
 @Test fun adapterHonorsPageSelectionAndCustomerName() {
  val i=InstrumentationRegistry.getInstrumentation();val context=i.targetContext
  val executor=Executors.newSingleThreadExecutor();val file=File(context.cacheDir,"adapter-test.pdf")
  val s=PdfSession(context)
  try {
   val original=ByteArrayOutputStream();PDDocument().use { p->repeat(3){p.addPage(PDPage())};p.save(original) }
   s.open(original.toByteArray().inputStream(),"")
   var finished=false
   val adapter=NativePrintAdapter(s,3,executor,"Devarakonda_Keerthi.pdf"){finished=true}
   assertEquals(3,adapter.documentInfo().pageCount)
   assertEquals("Devarakonda_Keerthi.pdf",adapter.documentInfo().name)
   val done=CountDownLatch(1);var failure:String?=null;var written:Array<PageRange>?=null
   ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE).use { fd->
    i.runOnMainSync { adapter.writePages(arrayOf(PageRange(1,1)),fd,CancellationSignal(),
      {pages -> written=pages;done.countDown()},
      {error -> failure=error.toString();done.countDown()},
      {failure="cancelled";done.countDown()}) }
    assertTrue(done.await(20,TimeUnit.SECONDS));assertNull(failure)
    assertEquals(listOf(PageRange(1,1)),written!!.toList())
   }
   PDDocument.load(file).use { assertEquals(1,it.numberOfPages) }
   i.runOnMainSync {adapter.onFinish()};assertTrue(finished)
  }finally{s.close();executor.shutdownNow();file.delete()}
  assertFalse(file.exists())
 }
}
