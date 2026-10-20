package com.printassist.android

import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.BitmapFactory
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.cert.X509CRL
import org.junit.Assert.*
import org.junit.Test

class VerificationWorkflowTest {
 private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
 private val context get()=instrumentation.targetContext
 private fun bytes(n:String)=instrumentation.context.assets.open("fixtures/$n").use { it.readBytes() }
 private fun trust():TrustMaterial {
  val cf=CertificateFactory.getInstance("X.509")
  val root=cf.generateCertificate(bytes("root.cer").inputStream()) as X509Certificate
  val leaf=cf.generateCertificate(bytes("leaf.cer").inputStream()) as X509Certificate
  val crls=listOf("root","inter").map { cf.generateCRL(bytes("$it.crl").inputStream()) as X509CRL }
  return TrustMaterial(roots=listOf(root),crls=crls,approvedUidai=setOf(fingerprint(leaf.encoded)))
 }
 @Test fun strictParserDetectsSignatureAndOriginalRemainsIdentical(){
  val original=bytes("valid.pdf")
  PdfSession(context).use { s->
   s.open(original.inputStream(),"")
   assertTrue(s.strictParsing)
   val report=s.verify(trust(),true)
   assertEquals(report.text(),Verdict.VERIFIED,report.verdict)
   assertTrue(report.signatures.single().appearance.contains("AP/N=present"))
   val copy=ByteArrayOutputStream();s.writeOriginal(copy);assertArrayEquals(original,copy.toByteArray())
  }
 }
 @Test fun modifiedPdfDoesNotVerify(){PdfSession(context).use { s->s.open(bytes("tampered.pdf").inputStream(),"");assertEquals(Verdict.INVALID,s.verify(trust(),true).verdict) }}
 @Test fun bundledRootsDoNotTrustSyntheticSigner(){PdfSession(context).use { s->s.open(bytes("valid.pdf").inputStream(),"");assertNotEquals(Verdict.VERIFIED,s.verify(TrustLoader.load(context),false).verdict) }}
 @Test fun pngJpgAndPrintPdfContainOriginalAppearance(){
  PdfSession(context).use { s->
   s.open(bytes("valid.pdf").inputStream(),"")
   for(jpg in listOf(false,true)) {
    val out=ByteArrayOutputStream();s.writeImage(out,0,jpg,200)
    val b=BitmapFactory.decodeByteArray(out.toByteArray(),0,out.size());assertNotNull(b)
    assertTrue(b.width.toLong()*b.height<=8_000_000)
    // Original synthetic blue page rectangle must survive conversion.
    val pixel=b.getPixel((100f/595*b.width).toInt(),((842-100f)/842*b.height).toInt())
    assertTrue(android.graphics.Color.blue(pixel)>android.graphics.Color.red(pixel)+80)
    if(!jpg) File(context.filesDir,"synthetic-appearance.png").writeBytes(out.toByteArray())
    b.recycle()
   }
   val out=ByteArrayOutputStream();s.printPdf(out,defaultPrintAttributes(),setOf(0))
   PDDocument.load(out.toByteArray()).use { printed->assertEquals(1,printed.numberOfPages);assertTrue(printed.signatureDictionaries.isEmpty()) }
   // Independent Android platform PDF renderer can open the generated print output.
   val f=File(context.cacheDir,"synthetic-print-test.pdf")
   try {
    f.writeBytes(out.toByteArray())
    ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY).use { fd->
     android.graphics.pdf.PdfRenderer(fd).use { renderer->assertEquals(1,renderer.pageCount) }
    }
   }finally{f.delete()}
   assertFalse(f.exists())
  }
 }
 @Test fun printCancellationProducesNoOutput(){PdfSession(context).use { s->
  s.open(bytes("valid.pdf").inputStream(),"");val out=ByteArrayOutputStream();val cancel=CancellationSignal().apply{cancel()}
  try{s.printPdf(out,defaultPrintAttributes(),setOf(0),cancel);fail("Cancellation ignored")}catch(_:android.os.OperationCanceledException){}
  assertEquals(0,out.size())
 }}
 @Test fun unsignedDocumentNeverReceivesGreen(){
  val out=ByteArrayOutputStream();PDDocument().use{it.addPage(com.tom_roush.pdfbox.pdmodel.PDPage());it.save(out)}
  PdfSession(context).use{s->s.open(out.toByteArray().inputStream(),"");assertEquals(Verdict.UNSIGNED,s.verify(trust(),false).verdict)}
 }
}
