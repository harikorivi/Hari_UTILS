package com.xeroxphotopro.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScreen(onPhoto: (Uri) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var permissionRequested by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it; permissionRequested = true }
    var front by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var bound by remember { mutableStateOf(false) }
    var frameAspect by remember { mutableFloatStateOf(.75f) }
    var flashAvailable by remember { mutableStateOf(false) }
    val alive = remember { AtomicBoolean(true) }
    DisposableEffect(Unit) { onDispose { alive.set(false) } }
    BackHandler(capturing) {}
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    var ready by remember { mutableStateOf(false) }
    var guidance by remember { mutableStateOf("Center the face inside the guide") }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val capture = remember(front) { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val currentPhoto by rememberUpdatedState(onPhoto)
    @Composable fun controls() {
        Text("Capture a clear photo", style = MaterialTheme.typography.titleLarge)
        Text("Plain background • Even light • Full head and shoulders", style = MaterialTheme.typography.bodySmall)
        if (!permission) {
            Text(if (permissionRequested) "Camera permission is off. Enable it in Android Settings or use Gallery." else "Allow camera access to take a photo.")
            Button(onClick = { request.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            if (permissionRequested) TextButton(onClick = {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }) { Text("Open app settings") }
        } else {
            Text(cameraError ?: guidance, color = if (cameraError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text("Guidance only • review the final crop", style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { front = !front }, enabled = !capturing) { Text(if (front) "Rear" else "Front") }
                OutlinedButton(onClick = { flash = !flash }, enabled = !capturing && flashAvailable) { Text(if (flash) "Flash on" else "Flash off") }
            }
            Button(modifier = Modifier.fillMaxWidth(), enabled = !capturing && bound && cameraError == null, onClick = {
                var file: File? = null
                try {
                    file = File.createTempFile("capture-", ".jpg", context.cacheDir)
                    val capturedFile = file
                    capturing = true
                    capture.flashMode = if (flash && flashAvailable) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                    capture.targetRotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                    capture.takePicture(ImageCapture.OutputFileOptions.Builder(capturedFile).build(), mainExecutor, object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                            capturing = false
                            if (alive.get()) currentPhoto(Uri.fromFile(capturedFile)) else capturedFile.delete()
                        }
                        override fun onError(exception: ImageCaptureException) { capturedFile.delete(); capturing = false; guidance = "Capture failed. Check storage and try again." }
                    })
                } catch (_: Exception) { file?.delete(); capturing = false; guidance = "Unable to save photo. Check free storage and try again." }
            }) { Text(if (capturing) "Capturing…" else "Take photo") }
        }
        TextButton(onClick = onBack, enabled = !capturing) { Text("Back to photo setup") }
    }
    @Composable fun preview(modifier: Modifier) {
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            if (permission) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    // Match PreviewView FIT_CENTER: guides follow the image, not black letterboxing.
                    val width = minOf(size.width, size.height * frameAspect)
                    val height = width / frameAspect
                    val left = (size.width - width) / 2
                    val top = (size.height - height) / 2
                    val color = if (ready) Color(0xFF66E2A8) else if (guidance.startsWith("No face")) Color(0xFFFF7777) else Color(0xFFFFD166)
                    drawOval(color, Offset(left + width * .25f, top + height * .18f), Size(width * .5f, height * .48f), style = Stroke(2.dp.toPx()))
                    drawLine(color, Offset(left + width * .2f, top + height * .38f), Offset(left + width * .8f, top + height * .38f), 1.dp.toPx())
                    drawLine(color, Offset(left + width * .15f, top + height * .78f), Offset(left + width * .85f, top + height * .78f), 1.dp.toPx())
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
        val controlsHeight = maxHeight * .55f
        if (maxWidth > maxHeight) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                preview(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { controls() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                preview(Modifier.weight(1f).fillMaxWidth())
                Column(Modifier.heightIn(max = controlsHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { controls() }
            }
        }
    }
    if (permission) DisposableEffect(front, lifecycle) {
        val disposed = AtomicBoolean(false)
        val analyzing = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val detector = FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).build())
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        cameraError = null; ready = false; bound = false
        analysis.setAnalyzer(executor) { frame ->
            val media = frame.image
            if (disposed.get() || media == null || !analyzing.compareAndSet(false, true)) frame.close()
            else {
                val rotation = frame.imageInfo.rotationDegrees
                val w = if (rotation % 180 == 0) frame.width else frame.height
                val h = if (rotation % 180 == 0) frame.height else frame.width
                val plane = frame.planes[0]
                val buffer = plane.buffer.duplicate()
                var light = 0L; var samples = 0
                for (y in 0 until frame.height step 16) for (x in 0 until frame.width step 16) {
                    val position = y * plane.rowStride + x * plane.pixelStride
                    if (position < buffer.limit()) { light += buffer.get(position).toInt() and 255; samples++ }
                }
                val brightness = if (samples > 0) light.toDouble() / samples else 128.0
                try { detector.process(InputImage.fromMediaImage(media, rotation))
                    .addOnSuccessListener(mainExecutor) { faces ->
                        if (!disposed.get()) {
                            frameAspect = w.toFloat() / h
                            val f = faces.singleOrNull()
                            val text = when {
                                faces.isEmpty() -> "No face found — center the customer"
                                faces.size > 1 -> "Only one person should be in the frame"
                                f == null -> "Position the face"
                                abs(f.headEulerAngleY) > 12 || abs(f.headEulerAngleZ) > 8 || abs(f.headEulerAngleX) > 12 -> "Keep your head straight; look at the camera"
                                abs(f.boundingBox.centerX().toFloat() / w - .5f) > .12f || abs(f.boundingBox.centerY().toFloat() / h - .43f) > .15f -> "Center your face"
                                f.boundingBox.height().toFloat() / h < .28f -> "Move closer"
                                f.boundingBox.height().toFloat() / h > .65f -> "Move back"
                                (f.leftEyeOpenProbability ?: 1f) < .5f || (f.rightEyeOpenProbability ?: 1f) < .5f -> "Keep both eyes open"
                                brightness < 55 -> "Improve lighting"
                                brightness > 220 -> "Reduce glare and check exposure"
                                else -> "Position looks good — check lighting, then capture"
                            }
                            guidance = text; ready = text.startsWith("Position looks")
                        }
                    }.addOnFailureListener(mainExecutor) { if (!disposed.get()) { ready = false; guidance = "Face guidance unavailable. Use the outline and review the crop." } }
                    .addOnCompleteListener { frame.close(); analyzing.set(false); if (disposed.get()) detector.close() }
                } catch (_: Exception) { frame.close(); analyzing.set(false) }
            }
        }
        future.addListener({
            if (!disposed.get()) try {
                provider = future.get()
                val camera = provider?.bindToLifecycle(lifecycle, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
                flashAvailable = camera?.cameraInfo?.hasFlashUnit() == true
                bound = true
            } catch (_: Exception) { cameraError = "Camera unavailable. Switch cameras or use a gallery photo." }
        }, mainExecutor)
        onDispose {
            disposed.set(true); analysis.clearAnalyzer(); provider?.unbind(preview, capture, analysis)
            executor.shutdown(); if (!analyzing.get()) detector.close()
        }
    }
}
