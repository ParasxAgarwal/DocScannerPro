package com.rebelroot.docscannerpro.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rebelroot.docscannerpro.core.model.QuadCorners
import com.rebelroot.docscannerpro.ui.viewmodel.CaptureState
import com.rebelroot.docscannerpro.ui.viewmodel.IdCardSide
import com.rebelroot.docscannerpro.ui.viewmodel.ScanMode
import com.rebelroot.docscannerpro.ui.viewmodel.ScanViewModel
import com.rebelroot.docscannerpro.ui.viewmodel.ScannerStatus
import com.rebelroot.docscannerpro.ui.viewmodel.ScannedPageDraft
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    viewModel: ScanViewModel,
    onClose: () -> Unit,
    onNavigateToCrop: () -> Unit,
    onBatchFinished: (documentId: String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val hapticFeedback = LocalHapticFeedback.current

    val scanMode by viewModel.scanMode.collectAsState()
    val idSide by viewModel.idCardSide.collectAsState()
    val isFlashOn by viewModel.isFlashOn.collectAsState()
    val isAutoCapture by viewModel.isAutoCaptureEnabled.collectAsState()
    val detectedCorners by viewModel.detectedCorners.collectAsState()
    val hasRealDetection by viewModel.hasRealDetection.collectAsState()
    val guidanceMessage by viewModel.guidanceMessage.collectAsState()
    val isStable by viewModel.isStableAndReady.collectAsState()
    val capturedPages by viewModel.capturedPages.collectAsState()
    val scannedBarcode by viewModel.scannedBarcode.collectAsState()
    val qrResult by viewModel.qrResult.collectAsState()
    val analyzedFrameSize by viewModel.analyzedFrameSize.collectAsState()
    val scannerStatus by viewModel.scannerStatus.collectAsState()
    val captureState by viewModel.captureState.collectAsState()

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var cameraControl: androidx.camera.core.CameraControl? by remember { mutableStateOf(null) }
    var rebindTrigger by remember { mutableIntStateOf(0) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
            analysisExecutor.shutdown()
            captureExecutor.shutdown()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val bitmaps = uris.mapNotNull { uri ->
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(stream)
                    stream?.close()
                    bitmap
                } catch (t: Throwable) {
                    Log.e("ScannerScreen", "Import of $uri failed", t)
                    null
                }
            }
            if (bitmaps.isEmpty()) {
                viewModel.setCaptureFailed("Could not read the selected images")
            } else {
                viewModel.importImages(bitmaps)
            }
        }
    }

    fun bindCamera() {
        viewModel.setScannerStatus(ScannerStatus.Starting)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(rotation)
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(rotation)
                    .build()
                    .also { analyzer ->
                        var lastAnalysisNanos = 0L
                        analyzer.setAnalyzer(analysisExecutor) { imageProxy ->
                            try {
                                val now = System.nanoTime()
                                if (now - lastAnalysisNanos < 100_000_000L) return@setAnalyzer
                                lastAnalysisNanos = now
                                val bitmap = imageProxyToBitmap(imageProxy)
                                if (bitmap != null) viewModel.onFrameAnalyzed(bitmap)
                            } catch (t: Throwable) {
                                Log.e("ScannerScreen", "Frame analysis failed", t)
                            } finally {
                                runCatching { imageProxy.close() }
                            }
                        }
                    }
                val cam = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                    analysis
                )
                imageCapture = capture
                cameraControl = cam.cameraControl
                cam.cameraInfo.cameraState.observe(lifecycleOwner) { state ->
                    val error = state.error
                    when {
                        error != null -> viewModel.setScannerStatus(
                            ScannerStatus.RecoverableError(describeCameraError(error.code))
                        )
                        state.type == CameraState.Type.OPEN -> viewModel.setScannerStatus(ScannerStatus.Ready)
                    }
                }
            } catch (t: Throwable) {
                Log.e("ScannerScreen", "Camera binding failed", t)
                viewModel.setScannerStatus(
                    ScannerStatus.Unavailable(
                        "Camera could not start. Another app may be holding it, or the device reports no cameras. Try again in a moment."
                    )
                )
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(rebindTrigger) {
        bindCamera()
    }
    LaunchedEffect(isFlashOn) {
        runCatching { cameraControl?.enableTorch(isFlashOn) }
    }
    LaunchedEffect(qrResult?.timestamp) {
        if (qrResult != null) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        if (scanMode == ScanMode.QR_BARCODE) {
            QrReticleOverlay()
        } else {
            DocumentQuadOverlay(
                corners = detectedCorners,
                isRealDetection = hasRealDetection,
                isStable = isStable,
                scanMode = scanMode,
                idCardSide = idSide,
                sourceWidth = analyzedFrameSize.width,
                sourceHeight = analyzedFrameSize.height
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            ScannerControlButton(
                icon = Icons.Default.Close,
                description = "Close scanner",
                onClick = onClose,
                selected = false,
                modifier = Modifier.testTag("btn_close_scanner")
            )
            if (scanMode != ScanMode.QR_BARCODE && scanMode != ScanMode.PHOTO) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.50f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isStable) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.72f))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = guidanceMessage,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.32f)
                    ) {
                        Text(
                            text = when (scanMode) {
                                ScanMode.DOCUMENT -> "Document"
                                ScanMode.ID_CARD -> "ID card ${if (idSide == IdCardSide.FRONT) "· Front" else "· Back"}"
                                ScanMode.RECEIPT -> "Receipt"
                                ScanMode.BUSINESS_CARD -> "Business card"
                                ScanMode.BOOK -> "Book"
                                ScanMode.PHOTO -> "Photo"
                                ScanMode.QR_BARCODE -> "QR / Barcode"
                                ScanMode.MULTI_PAGE -> "Document"
                            },
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (scanMode != ScanMode.QR_BARCODE && scanMode != ScanMode.PHOTO) {
                    ScannerControlButton(
                        icon = Icons.Default.CenterFocusStrong,
                        description = if (isAutoCapture) "Auto capture on" else "Auto capture off",
                        onClick = { viewModel.toggleAutoCapture() },
                        selected = isAutoCapture
                    )
                }
                ScannerControlButton(
                    icon = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    description = if (isFlashOn) "Flash off" else "Flash on",
                    onClick = { viewModel.toggleFlash() },
                    selected = isFlashOn
                )
            }
        }

        when (val status = scannerStatus) {
            is ScannerStatus.RecoverableError -> CameraErrorBanner(
                modifier = Modifier.align(Alignment.Center),
                message = status.message,
                actionLabel = "Retry",
                onAction = { rebindTrigger++ },
                onDismiss = { viewModel.setScannerStatus(ScannerStatus.Ready) }
            )
            is ScannerStatus.Unavailable -> CameraErrorBanner(
                modifier = Modifier.align(Alignment.Center),
                message = status.message,
                actionLabel = "Try again",
                onAction = { rebindTrigger++ },
                onDismiss = onClose
            )
            else -> Unit
        }

        when (val state = captureState) {
            is CaptureState.Failed -> CameraErrorBanner(
                modifier = Modifier.align(Alignment.Center),
                message = state.message,
                actionLabel = "OK",
                onAction = { viewModel.dismissCaptureFailure() },
                onDismiss = null
            )
            else -> Unit
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.62f))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 8.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (scanMode != ScanMode.QR_BARCODE) {
                if (capturedPages.isNotEmpty()) {
                    PageThumbnailStrip(
                        pages = capturedPages,
                        onTapPage = { draft ->
                            viewModel.editPage(draft)
                            onNavigateToCrop()
                        },
                        onRemovePage = { viewModel.retakePage(it) }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${capturedPages.size} ${if (capturedPages.size == 1) "page" else "pages"}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(4.dp))
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    modifier = Modifier.padding(bottom = 11.dp)
                ) {
                    items(listOf(ScanMode.DOCUMENT, ScanMode.ID_CARD, ScanMode.RECEIPT, ScanMode.BUSINESS_CARD, ScanMode.BOOK, ScanMode.PHOTO, ScanMode.QR_BARCODE)) { mode ->
                        val isSelected = scanMode == mode
                        val label = when (mode) {
                            ScanMode.DOCUMENT -> "Document"
                            ScanMode.ID_CARD -> "ID card"
                            ScanMode.RECEIPT -> "Receipt"
                            ScanMode.BUSINESS_CARD -> "Card"
                            ScanMode.BOOK -> "Book"
                            ScanMode.PHOTO -> "Photo"
                            ScanMode.QR_BARCODE -> "QR / Barcode"
                            ScanMode.MULTI_PAGE -> "Document"
                        }
                        Column(
                            modifier = Modifier
                                .clickable { viewModel.setScanMode(mode) }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                label,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.58f),
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(Modifier.height(5.dp))
                            Box(
                                modifier = Modifier
                                    .width(if (isSelected) 18.dp else 0.dp)
                                    .height(2.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF5AA7FF))
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Hold steady over the code",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScannerUtilityButton(
                    icon = Icons.Default.PhotoLibrary,
                    label = "Import",
                    onClick = {
                        photoPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    testTag = "btn_gallery_import"
                )
                if (scanMode != ScanMode.QR_BARCODE) {
                    val canCapture = scannerStatus == ScannerStatus.Ready &&
                        captureState != CaptureState.Capturing &&
                        captureState != CaptureState.Processing
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(84.dp)
                            .clickable(
                                enabled = canCapture,
                                onClickLabel = "Take scan"
                            ) {
                                viewModel.beginCapture()
                                val capture = imageCapture
                                if (capture == null) {
                                    viewModel.setCaptureFailed("Camera is not ready yet")
                                } else {
                                    capture.takePicture(
                                        captureExecutor,
                                        object : ImageCapture.OnImageCapturedCallback() {
                                            override fun onCaptureSuccess(image: ImageProxy) {
                                                val bitmap = try {
                                                    imageProxyToBitmap(image)
                                                } finally {
                                                    runCatching { image.close() }
                                                }
                                                if (bitmap != null) {
                                                    viewModel.captureFrame(bitmap)
                                                } else {
                                                    viewModel.setCaptureFailed("Captured image could not be read")
                                                }
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                viewModel.setCaptureFailed(
                                                    "Capture failed: ${exception.message ?: "camera error"}"
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                            .semantics { role = Role.Button }
                            .testTag("btn_shutter")
                    ) {
                        Canvas(modifier = Modifier.size(84.dp)) {
                            drawCircle(
                                color = if (isStable) Color(0xFF5AA7FF) else Color.White.copy(alpha = 0.88f),
                                style = Stroke(width = 3.5.dp.toPx())
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = when {
                                captureState == CaptureState.Processing -> Color(0xFFB9B9BC)
                                captureState == CaptureState.Capturing -> Color(0xFFDCDCE0)
                                else -> Color.White
                            },
                            modifier = Modifier.size(68.dp),
                            shadowElevation = 3.dp
                        ) {
                            if (captureState == CaptureState.Processing) {
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF3D3D42),
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.size(84.dp))
                }
                if (scanMode != ScanMode.QR_BARCODE && capturedPages.isNotEmpty()) {
                    ScannerUtilityButton(
                        icon = Icons.Default.Check,
                        label = "Done",
                        onClick = { viewModel.finishBatchAndSave { docId -> onBatchFinished(docId) } },
                        testTag = "btn_finish_batch"
                    )
                } else {
                    Box(Modifier.size(58.dp))
                }
            }
        }

        scannedBarcode?.let { barcode ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissScannedBarcode() },
                title = { Text(barcode.formatName) },
                text = { Text(barcode.displayValue) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissScannedBarcode() }) {
                        Text("Close")
                    }
                }
            )
        }

        qrResult?.let { result ->
            QrResultSheet(
                result = result,
                history = viewModel.qrHistory.collectAsState(initial = emptyList()).value,
                onDismiss = { viewModel.dismissQrResult() },
                onDeleteHistory = { viewModel.deleteHistoryEntry(it) },
                onClearHistory = { viewModel.clearHistory() }
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CameraErrorBanner(
    modifier: Modifier = Modifier,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: (() -> Unit)?
) {
    Surface(
        color = Color(0xFF7F1D1D).copy(alpha = 0.94f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAction) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(actionLabel)
                }
                if (onDismiss != null) {
                    TextButton(onClick = onDismiss) {
                        Text("Continue anyway", color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PageThumbnailStrip(
    pages: List<ScannedPageDraft>,
    onTapPage: (ScannedPageDraft) -> Unit,
    onRemovePage: (ScannedPageDraft) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        items(pages, key = { it.id }) { page ->
            val thumb = page.thumbnail ?: page.processedBitmap
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF22252B))
                    .border(1.5.dp, Color(0xFF5AA7FF), RoundedCornerShape(10.dp))
                    .pointerInput(page.id) {
                        detectTapGestures(
                            onLongPress = { onRemovePage(page) },
                            onTap = { onTapPage(page) }
                        )
                    }
            ) {
                androidx.compose.foundation.Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = "Captured page ${pages.indexOf(page) + 1}. Tap to crop, long-press to remove.",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun QrReticleOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val side = minOf(size.width, size.height) * 0.68f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val dim = Color.Black.copy(alpha = 0.45f)
        drawRect(dim, Offset(0f, 0f), Size(size.width, top))
        drawRect(dim, Offset(0f, top + side), Size(size.width, size.height - top - side))
        drawRect(dim, Offset(0f, top), Size(left, side))
        drawRect(dim, Offset(left + side, top), Size(size.width - left - side, side))
        val stroke = 4.dp.toPx()
        val bracket = 26.dp.toPx()
        val c = Color(0xFF5AA7FF)
        listOf(
            Offset(left, top) to Offset(1f, 1f),
            Offset(left + side, top) to Offset(-1f, 1f),
            Offset(left + side, top + side) to Offset(-1f, -1f),
            Offset(left, top + side) to Offset(1f, -1f)
        ).forEach { (p, dir) ->
            drawLine(c, p, Offset(p.x + bracket * dir.x, p.y), stroke, StrokeCap.Round)
            drawLine(c, p, Offset(p.x, p.y + bracket * dir.y), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun ScannerControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .background(if (selected) Color(0xFF2D82C7).copy(alpha = 0.86f) else Color.Black.copy(alpha = 0.48f), CircleShape)
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun ScannerUtilityButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp)) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(50.dp)
                .background(Color.Black.copy(alpha = 0.42f), CircleShape)
                .testTag(testTag)
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun DocumentQuadOverlay(
    corners: QuadCorners,
    isRealDetection: Boolean,
    isStable: Boolean,
    scanMode: ScanMode,
    idCardSide: IdCardSide,
    sourceWidth: Int,
    sourceHeight: Int
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val viewW = size.width
        val viewH = size.height
        val srcW = sourceWidth.toFloat().coerceAtLeast(1f)
        val srcH = sourceHeight.toFloat().coerceAtLeast(1f)
        val scale = maxOf(viewW / srcW, viewH / srcH)
        val renderedW = srcW * scale
        val renderedH = srcH * scale
        val offsetX = (viewW - renderedW) / 2f
        val offsetY = (viewH - renderedH) / 2f
        fun map(p: android.graphics.PointF): Offset = Offset(
            p.x * renderedW + offsetX,
            p.y * renderedH + offsetY
        )
        val p1 = map(corners.topLeft)
        val p2 = map(corners.topRight)
        val p3 = map(corners.bottomRight)
        val p4 = map(corners.bottomLeft)
        val quadColor = if (isStable) Color(0xFF43A047) else Color.White.copy(alpha = 0.88f)
        if (isRealDetection) {
            val path = Path().apply {
                moveTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
                lineTo(p3.x, p3.y)
                lineTo(p4.x, p4.y)
                close()
            }
            drawPath(path, color = Color.Black.copy(alpha = 0.42f), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, color = quadColor, style = Stroke(width = if (isStable) 3.5.dp.toPx() else 2.dp.toPx(), cap = StrokeCap.Round))
            if (scanMode == ScanMode.BOOK) {
                val topMid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                val bottomMid = Offset((p4.x + p3.x) / 2f, (p4.y + p3.y) / 2f)
                drawLine(Color.White.copy(alpha = 0.6f), topMid, bottomMid, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            val bracket = 22.dp.toPx()
            listOf(
                p1 to Offset(1f, 1f),
                p2 to Offset(-1f, 1f),
                p3 to Offset(-1f, -1f),
                p4 to Offset(1f, -1f)
            ).forEach { (p, dir) ->
                drawLine(quadColor, p, p + Offset(bracket * dir.x, 0f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                drawLine(quadColor, p, p + Offset(0f, bracket * dir.y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            }
        } else {
            val left = size.width * 0.09f
            val right = size.width * 0.91f
            val top = when (scanMode) {
                ScanMode.ID_CARD, ScanMode.BUSINESS_CARD -> size.height * 0.36f
                ScanMode.BOOK -> size.height * 0.28f
                else -> size.height * 0.23f
            }
            val bottom = when (scanMode) {
                ScanMode.ID_CARD, ScanMode.BUSINESS_CARD -> size.height * 0.64f
                ScanMode.BOOK -> size.height * 0.72f
                else -> size.height * 0.77f
            }
            val guide = 24.dp.toPx()
            val c = Color.White.copy(alpha = 0.48f)
            drawLine(c, Offset(left, top), Offset(left + guide, top), 3.dp.toPx(), StrokeCap.Round)
            drawLine(c, Offset(left, top), Offset(left, top + guide), 3.dp.toPx(), StrokeCap.Round)
            drawLine(c, Offset(right, top), Offset(right - guide, top), 3.dp.toPx(), StrokeCap.Round)
            drawLine(c, Offset(right, top), Offset(right, top + guide), 3.dp.toPx(), StrokeCap.Round)
            drawLine(c, Offset(left, bottom), Offset(left + guide, bottom), 3.dp.toPx(), StrokeCap.Round)
            drawLine(c, Offset(left, bottom), Offset(left, bottom - guide), 3.dp.toPx(), StrokeCap.Round)
            drawLine(c, Offset(right, bottom), Offset(right - guide, bottom), 3.dp.toPx(), StrokeCap.Round)
            drawLine(c, Offset(right, bottom), Offset(right, bottom - guide), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}

private fun describeCameraError(code: Int): String = when (code) {
    CameraState.ERROR_CAMERA_IN_USE -> "Camera is in use by another app. Close it and retry."
    CameraState.ERROR_MAX_CAMERAS_IN_USE -> "Too many camera apps are open. Close others and retry."
    CameraState.ERROR_CAMERA_DISABLED -> "Camera is disabled by device policy."
    CameraState.ERROR_STREAM_CONFIG -> "Camera configuration failed on this device."
    CameraState.ERROR_CAMERA_FATAL_ERROR -> "Camera failed unexpectedly."
    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> "Camera hit a recoverable error."
    CameraState.ERROR_CAMERA_REMOVED -> "Camera is no longer available on this device."
    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> "Camera is blocked by Do Not Disturb mode."
    else -> "Camera error."
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    return try {
        when (image.format) {
            // ImageCapture delivers JPEG frames — decode the plane directly.
            // Without this branch every real-device capture failed with
            // "Captured image could not be read".
            ImageFormat.JPEG -> {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                rotateBitmap(raw, image.imageInfo.rotationDegrees)
            }
            ImageFormat.YUV_420_888 -> {
                val width = image.width
                val height = image.height
                val y = image.planes[0]
                val u = image.planes[1]
                val v = image.planes[2]
                val nv21 = ByteArray(width * height + width * height / 2)
                copyPlane(y, width, height, nv21, 0)
                var offset = width * height
                val chromaH = height / 2
                val chromaW = width / 2
                val uRow = ByteArray(u.rowStride)
                val vRow = ByteArray(v.rowStride)
                for (row in 0 until chromaH) {
                    val uBuffer = u.buffer.duplicate().apply { position(row * u.rowStride) }
                    val vBuffer = v.buffer.duplicate().apply { position(row * v.rowStride) }
                    val uCount = minOf(uRow.size, uBuffer.remaining())
                    val vCount = minOf(vRow.size, vBuffer.remaining())
                    uBuffer.get(uRow, 0, uCount)
                    vBuffer.get(vRow, 0, vCount)
                    for (col in 0 until chromaW) {
                        val uIndex = col * u.pixelStride
                        val vIndex = col * v.pixelStride
                        if (vIndex < vCount && offset < nv21.size) nv21[offset++] = vRow[vIndex]
                        if (uIndex < uCount && offset < nv21.size) nv21[offset++] = uRow[uIndex]
                    }
                }
                // Use OpenCV to convert YUV NV21 directly to RGBA — no JPEG encode/decode round-trip
                // which eliminates the ~1 MB/sec GC pressure from the old YuvImage→BitmapFactory path.
                val nv21Mat = org.opencv.core.Mat(height * 3 / 2, width, org.opencv.core.CvType.CV_8UC1)
                nv21Mat.put(0, 0, nv21)
                val rgbaMat = org.opencv.core.Mat(height, width, org.opencv.core.CvType.CV_8UC4)
                org.opencv.imgproc.Imgproc.cvtColor(nv21Mat, rgbaMat, org.opencv.imgproc.Imgproc.COLOR_YUV2RGBA_NV21)
                nv21Mat.release()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val buffer = java.nio.ByteBuffer.allocate(width * height * 4)
                rgbaMat.get(0, 0, buffer.array())
                bitmap.copyPixelsFromBuffer(buffer)
                rgbaMat.release()
                rotateBitmap(bitmap, image.imageInfo.rotationDegrees)
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

private fun copyPlane(plane: ImageProxy.PlaneProxy, width: Int, height: Int, out: ByteArray, outOffset: Int, pixelStrideOverride: Int? = null) {
    val buffer = plane.buffer.duplicate()
    val rowStride = plane.rowStride
    val pixelStride = pixelStrideOverride ?: plane.pixelStride
    var output = outOffset
    val row = ByteArray(rowStride)
    for (r in 0 until height) {
        buffer.position(minOf(r * rowStride, buffer.limit()))
        val count = minOf(row.size, buffer.remaining())
        buffer.get(row, 0, count)
        for (c in 0 until width) {
            val index = c * pixelStride
            if (index < count && output < out.size) out[output++] = row[index]
        }
    }
}

private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
        if (it !== bitmap) bitmap.recycle()
    }
}
