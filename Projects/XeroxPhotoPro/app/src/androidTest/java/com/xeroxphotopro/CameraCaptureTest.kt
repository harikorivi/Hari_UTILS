package com.xeroxphotopro

import android.Manifest
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.isEnabled
import androidx.test.rule.GrantPermissionRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xeroxphotopro.camera.CameraScreen
import com.xeroxphotopro.image.PhotoProcessor
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraCaptureTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val permissions = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @Test fun cameraXCapturesDecodableImage() {
        val captured = AtomicReference<Uri?>(null)
        compose.setContent { MaterialTheme { CameraScreen(onPhoto = { captured.set(it) }, onBack = {}) } }
        compose.waitUntil(30000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Take photo") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Take photo").performClick()
        compose.waitUntil(30000) { captured.get() != null }
        val photo = PhotoProcessor.load(ApplicationProvider.getApplicationContext(), captured.get()!!)
        assertTrue(photo.width > 0 && photo.height > 0)
        photo.recycle()
        File(captured.get()!!.path!!).delete()
    }
}
