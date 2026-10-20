package com.printassist.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.ByteArrayOutputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.cert.X509CRL
import org.junit.Assert.*
import org.junit.Test

class SignatureAppearanceTest {
 private val i get()=InstrumentationRegistry.getInstrumentation()
 private fun bytes(n:String)=i.context.assets.open("fixtures/$n").use{it.readBytes()}
 private fun trust():TrustMaterial {
  val cf=CertificateFactory.getInstance("X.509")
  return TrustMaterial(roots=listOf(cf.generateCertificate(bytes("root.cer").inputStream()) as X509Certificate),
   crls=listOf("root","inter").map{cf.generateCRL(bytes("$it.crl").inputStream()) as X509CRL})
 }
 // Interior of the synthetic signature widget; no underlying page artwork there.
 private fun ink(b:Bitmap):Int {
  var count=0
  for(y in (580*b.height/842) until (655*b.height/842))
   for(x in (390*b.width/595) until (580*b.width/595)) {
    val c=b.getPixel(x,y)
    if(Color.red(c)<240 || Color.green(c)<240 || Color.blue(c)<240) count++
   }
  return count
 }
 @Test fun failedAndIncompleteVerificationOmitWidgetInPreviewAndEveryCopyFormat(){
  for(n in listOf("valid","tampered")) PdfSession(i.targetContext).use{s->
   val original=bytes("$n.pdf");s.open(original.inputStream(),"")
   val r=s.verify(if(n=="valid")TrustMaterial() else trust(),false)
   assertNotEquals(Verdict.VERIFIED,r.verdict)
   val preview=s.render(0);assertEquals(0,ink(preview));preview.recycle()
   for(jpg in listOf(false,true)) {
    val out=ByteArrayOutputStream();s.writeImage(out,0,jpg,150)
    val image=BitmapFactory.decodeByteArray(out.toByteArray(),0,out.size())
    assertEquals("Widget leaked into image",0,ink(image));image.recycle()
   }
   val out=ByteArrayOutputStream();s.printPdf(out,defaultPrintAttributes(),setOf(0))
   PDDocument.load(out.toByteArray()).use{copy->
    val image=PDFRenderer(copy).renderImage(0,1.5f)
    assertEquals("Widget leaked into print PDF",0,ink(image));image.recycle()
   }
   val saved=ByteArrayOutputStream();s.writeOriginal(saved);assertArrayEquals(original,saved.toByteArray())
  }
 }
 @Test fun verifiedAppearanceIsKeptAndPolicyResetsForNextCustomer(){
  PdfSession(i.targetContext).use{s->
   s.open(bytes("valid.pdf").inputStream(),"")
   assertEquals(Verdict.VERIFIED,s.verify(trust(),false).verdict)
   val preview=s.render(0);assertTrue(ink(preview)>100);preview.recycle()
   val out=ByteArrayOutputStream();s.writeImage(out,0,false,150)
   val image=BitmapFactory.decodeByteArray(out.toByteArray(),0,out.size());assertTrue(ink(image)>100);image.recycle()
   s.open(bytes("valid.pdf").inputStream(),"")
   val next=s.render(0);assertEquals(0,ink(next));next.recycle()
  }
 }
 @Test fun lockedSignedPdfOmitsUnverifiedWidgetAfterAuthorizedUnlock(){
  PdfSession(i.targetContext).use{s->
   val original=bytes("locked-signed.pdf")
   s.open(original.inputStream(),"SURE1990")
   val report=s.verify(TrustMaterial(),true)
   assertTrue(report.signatures.single().integrity)
   assertNotEquals(Verdict.VERIFIED,report.verdict)
   val preview=s.render(0);assertEquals(0,ink(preview));preview.recycle()
   val saved=ByteArrayOutputStream();s.writeOriginal(saved);assertArrayEquals(original,saved.toByteArray())
  }
 }
}
