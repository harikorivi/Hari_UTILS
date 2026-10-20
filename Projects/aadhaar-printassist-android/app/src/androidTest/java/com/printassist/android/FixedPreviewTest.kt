package com.printassist.android

import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FixedPreviewTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun scrollingControlsAndSwipingPreviewDoNotMovePreviewContainer(){
  val i=InstrumentationRegistry.getInstrumentation()
  val file=File(i.targetContext.cacheDir,"synthetic-locked-preview.pdf")
  file.writeBytes(i.context.assets.open("fixtures/locked-signed.pdf").use{it.readBytes()})
  lateinit var model:PrintAssistViewModel
  try {
   compose.runOnIdle {
    model=ViewModelProvider(compose.activity)[PrintAssistViewModel::class.java]
    model.open(Uri.fromFile(file),"SURE1990","Synthetic Test",true)
   }
   compose.waitUntil(20_000){model.state.value.bitmap!=null}
   compose.waitForIdle()
   val preview=compose.onNodeWithContentDescription("Print copy page preview")
   val before=preview.fetchSemanticsNode().boundsInRoot
   compose.onNodeWithText("Clear customer session").performScrollTo()
   preview.performTouchInput{swipeUp()}
   compose.waitForIdle()
   assertEquals(before,preview.fetchSemanticsNode().boundsInRoot)
   compose.onNodeWithText("Clear customer session").performClick()
   compose.onNodeWithContentDescription("Print copy page preview").assertDoesNotExist()
  }finally{file.delete()}
 }
}
