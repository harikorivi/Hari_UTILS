package com.printassist.android

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class ScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun generalModeAndVerificationWarning() {
        compose.onNodeWithText("General PDF").performClick()
        compose.onNodeWithText("PDF password (blank if none)").assertExists()
        compose.onNodeWithText("Signature: NOT CHECKED").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Unlock & verify original").assertIsNotEnabled()
    }
    @Test fun homePreviewForDocumentation() {
        compose.onNodeWithText("Full English name").assertExists()
        compose.onNodeWithText("Choose PDF").assertIsDisplayed()
        // Empty home screen only; production always uses FLAG_SECURE.
        compose.activityRule.scenario.onActivity { it.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Allow SurfaceFlinger to apply the test-only capture flag change.
        android.os.SystemClock.sleep(500)
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir, "validation-home.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
