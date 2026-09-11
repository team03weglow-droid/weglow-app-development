package com.example.weglow.ui.screens

import com.example.weglow.ui.theme.*
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import com.example.weglow.ui.components.WeGlowPrimaryButton
import com.example.weglow.feature.scan.ScanUiState
import com.example.weglow.feature.hairstyle.HairstyleUiState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable


enum class ScanMode { ACNE, HAIRSTYLE }
private enum class ScanFlowState { MODE_SELECT, CAMERA, ANALYZING }

@Composable
fun ScanScreen(
    photoUri: Uri?,
    acneState: ScanUiState,
    hairstyleState: HairstyleUiState,
    onAnalyzeAcne: () -> Unit,
    onCancelAcne: () -> Unit,
    onAnalyzeHairstyle: (Uri) -> Unit,
    onCancelHairstyle: () -> Unit,
    onPhotoCaptured: (Uri) -> Unit,
    onBack: () -> Unit,
    onAcneScanComplete: () -> Unit,
    onHairstyleScanComplete: () -> Unit,
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    var flowState by rememberSaveable { mutableStateOf(ScanFlowState.MODE_SELECT) }
    var scanMode by rememberSaveable { mutableStateOf(ScanMode.ACNE) }
    var acneAnalysisStartedAt by remember { mutableLongStateOf(0L) }
    fun beginAcneAnalysis() {
        acneAnalysisStartedAt = SystemClock.elapsedRealtime()
        onAnalyzeAcne()
    }
    val galleryWithoutCamera = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPhotoCaptured(uri)
            flowState = ScanFlowState.ANALYZING
            if (scanMode == ScanMode.ACNE) beginAcneAnalysis() else onAnalyzeHairstyle(uri)
        }
    }
    BackHandler(flowState == ScanFlowState.ANALYZING) {
        if (scanMode == ScanMode.ACNE) onCancelAcne() else onCancelHairstyle()
        flowState = ScanFlowState.CAMERA
    }
    LaunchedEffect(acneState.result, flowState) {
        if (flowState == ScanFlowState.ANALYZING && scanMode == ScanMode.ACNE && acneState.result != null) {
            val elapsed = SystemClock.elapsedRealtime() - acneAnalysisStartedAt
            val remaining = (5_000L - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) delay(remaining)
            // Allow the UI progress animation to visibly finish at 100%.
            delay(750L)
            onAcneScanComplete()
        }
    }
    LaunchedEffect(hairstyleState.result, flowState, scanMode) {
        if (
            flowState == ScanFlowState.ANALYZING &&
            scanMode == ScanMode.HAIRSTYLE &&
            hairstyleState.result != null
        ) {
            delay(500L)
            onHairstyleScanComplete()
        }
    }

    when {
        flowState == ScanFlowState.MODE_SELECT -> {
            ScanModeSelectScreen(
                onBack = onBack,
                onModeSelected = { mode ->
                    scanMode = mode
                    flowState = ScanFlowState.CAMERA
                    if (!hasCameraPermission) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            )
        }

        flowState == ScanFlowState.ANALYZING && scanMode == ScanMode.ACNE -> {
            FigmaAcneAnalyzingScreen(
                state = acneState,
                onRetry = ::beginAcneAnalysis,
                onCancel = { onCancelAcne(); flowState = ScanFlowState.CAMERA },
            )
        }

        !hasCameraPermission && flowState != ScanFlowState.ANALYZING -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    "WeGlow needs camera access to scan your face.",
                    fontFamily = JungeFont,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                PillButton(
                    text = "Grant camera access",
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )
                Spacer(Modifier.height(16.dp))
                PillButton("Choose from gallery") {
                    galleryWithoutCamera.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                Spacer(Modifier.weight(1f))
            }
        }

        flowState == ScanFlowState.ANALYZING -> {
            AnalyzingScreen(
                photoUri = photoUri,
                state = hairstyleState,
                onRetry = {
                    photoUri?.let(onAnalyzeHairstyle)
                },
                onCancel = {
                    onCancelHairstyle()
                    flowState = ScanFlowState.CAMERA
                },
            )
        }

        else -> {
            CameraCaptureScreen(
                mode = scanMode,
                onBack = { flowState = ScanFlowState.MODE_SELECT },
                onPhotoReady = { uri ->
                    onPhotoCaptured(uri)
                    flowState = ScanFlowState.ANALYZING
                    if (scanMode == ScanMode.ACNE) beginAcneAnalysis() else onAnalyzeHairstyle(uri)
                }
            )
        }
    }
}

@Composable
private fun FigmaAcneAnalyzingScreen(state: ScanUiState, onRetry: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var photoBitmap by remember(state.photoUri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(state.photoUri) { photoBitmap = state.photoUri?.let { loadImageBitmap(context, it) } }
    val progress = remember { Animatable(0.26f) }
    val scanLinePosition by rememberInfiniteTransition(label = "skin scan line").animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_250, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scan line position",
    )
    LaunchedEffect(state.isAnalyzing) {
        while (state.isAnalyzing) {
            // Progress is deliberately capped until the real model result arrives.
            progress.snapTo((progress.value + 0.008f).coerceAtMost(0.92f))
            delay(80)
        }
    }
    LaunchedEffect(state.result) {
        if (state.result != null) progress.animateTo(1f, tween(700, easing = EaseInOutSine))
    }
    Column(Modifier.fillMaxSize().background(ButtonGreen).padding(horizontal = 20.dp)) {
        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onCancel), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel analysis", tint = Color.White)
            }
        }
        Column(Modifier.fillMaxSize().padding(bottom = 34.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.fillMaxWidth(0.78f).aspectRatio(3f / 4f).clip(RoundedCornerShape(100.dp)).background(Color.White.copy(alpha = 0.06f)).padding(2.dp)) {
                photoBitmap?.let { Image(it, "Your photo being analyzed", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, ButtonGreen.copy(alpha = 0.45f)))))
                Canvas(Modifier.fillMaxSize()) {
                    val scanY = size.height * scanLinePosition
                    drawLine(CoralAccent.copy(alpha = 0.9f), Offset(0f, scanY), Offset(size.width, scanY), 3.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            Spacer(Modifier.height(48.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.Bottom) {
                Text(if (state.error == null) "Analyzing skin\ntexture..." else "Analysis paused", fontFamily = JungeFont, fontSize = 28.sp, lineHeight = 36.sp, color = Color.White, modifier = Modifier.weight(1f))
                Text("${(progress.value * 100).toInt()}%", fontSize = 14.sp, color = CoralAccent)
            }
            Spacer(Modifier.height(8.dp))
            if (state.error == null) {
                LinearProgressIndicator(progress = { progress.value }, color = Color.White, trackColor = Color.White.copy(alpha = 0.2f), modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)))
                Spacer(Modifier.height(16.dp))
                Text("AI analysis in progress...", color = Sage, fontSize = 14.sp, textAlign = TextAlign.Center)
            } else {
                Text(state.error, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                PillButton("Retry analysis", onRetry)
            }
        }
    }
}

@Composable
private fun AcneAnalyzingScreen(state: ScanUiState, onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(DarkGreen).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (state.isAnalyzing) {
            CircularProgressIndicator(color = CoralAccent)
            Spacer(Modifier.height(24.dp))
            Text("Analyzing your photo…", color = Color.White, fontSize = 24.sp)
            Spacer(Modifier.height(12.dp))
            Text("Analyzing on your phone. Your photo stays on this device.", color = Color.White, textAlign = TextAlign.Center)
        } else if (state.error != null) {
            Text(state.error, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            PillButton("Retry analysis", onRetry)
        } else if (state.result == null) {
            Text("Select a photo to start a new scan.", color = Color.White)
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCancel) { Text("Choose another photo", color = Color.White) }
    }
}

@Composable
private fun ScanModeSelectScreen(onBack: () -> Unit, onModeSelected: (ScanMode) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGreen.copy(alpha = 0.94f))
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            "Make a New Scan",
            fontFamily = JungeFont,
            fontSize = 26.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ScanTypeCard(
                icon = Icons.Filled.Face,
                title = "Skin Scan",
                description = "See the condition of your skin",
                modifier = Modifier.weight(1f),
                onClick = { onModeSelected(ScanMode.ACNE) }
            )
            ScanTypeCard(
                icon = Icons.Filled.ContentCut,
                title = "Face Shape",
                description = "Get hairstyles matched to your face",
                modifier = Modifier.weight(1f),
                onClick = { onModeSelected(ScanMode.HAIRSTYLE) }
            )
        }
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.9f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextBlack)
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ScanTypeCard(icon: ImageVector, title: String, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(220.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(CardWhite)
            .clickable(onClick = onClick)
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MintChip),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = DarkGreen, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontFamily = JungeFont, fontSize = 18.sp, color = TextBlack)
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            fontFamily = JungeFont,
            fontSize = 12.sp,
            color = SoftGray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CameraCaptureScreen(mode: ScanMode, onBack: () -> Unit, onPhotoReady: (Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var flashOn by remember { mutableStateOf(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // CameraX binds to the Activity's lifecycleOwner, not this composable's, so the
    // preview/capture session otherwise keeps the camera hardware open (streaming,
    // torch left on) after the user navigates away from this screen to analyze a photo.
    DisposableEffect(Unit) {
        onDispose { cameraProvider?.unbindAll() }
    }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPhotoReady(uri) }
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setTargetRotation(previewView.display.rotation)
                        .build()
                    imageCapture = capture

                    try {
                        provider.unbindAll()
                        camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview,
                            capture
                        )
                    } catch (_: Exception) {
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Figma-inspired vignette keeps camera content visible while improving text contrast.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to DarkGreen.copy(alpha = 0.48f),
                    0.5f to Color.Transparent,
                    1f to DarkGreen.copy(alpha = 0.82f),
                )
            )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, start = 16.dp, end = 16.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Align your face",
                fontFamily = JungeFont,
                fontSize = 28.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (mode == ScanMode.HAIRSTYLE) {
                    "within the guide to detect your face shape"
                } else {
                    "within the guide to begin skin analysis"
                },
                fontFamily = JungeFont,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Keep the face guide clearly oval and leave comfortable space above it
            // for the instructions and below it for the camera controls.
            val guideWidth = size.width * 0.76f
            val guideHeight = size.height * 0.58f
            val left = (size.width - guideWidth) / 2f
            val top = size.height * 0.21f
            drawOval(
                color = Color.White.copy(alpha = 0.9f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(guideWidth, guideHeight),
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp, start = 32.dp, end = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScanCircleIconButton(
                icon = Icons.Filled.PhotoLibrary,
                contentDescription = "Choose from gallery",
                onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable {
                        val capture = imageCapture ?: return@clickable
                        val photoFile = File(context.cacheDir, "weglow_scan_${System.currentTimeMillis()}.jpg")
                        val metadata = ImageCapture.Metadata().apply {
                            // Match the mirrored front-camera preview so the saved photo
                            // appears exactly as the user saw it while taking the scan.
                            isReversedHorizontal = true
                        }
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                            .setMetadata(metadata)
                            .build()
                        capture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    onPhotoReady(Uri.fromFile(photoFile))
                                }

                                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                                    // Do not emit a URI for a file that CameraX failed to write.
                                }
                            }
                        )
                    }
            )

            ScanCircleIconButton(
                icon = if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = "Toggle flash",
                onClick = {
                    flashOn = !flashOn
                    camera?.cameraControl?.enableTorch(flashOn)
                }
            )
        }
    }
}

@Composable
private fun ScanCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White)
    }
}

@Composable
private fun AnalyzingScreen(
    photoUri: Uri?,
    state: HairstyleUiState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var photoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photoUri) {
        photoBitmap = photoUri?.let { loadImageBitmap(context, it) }
    }

    val progress = remember { Animatable(0.08f) }
    LaunchedEffect(state.isAnalyzing) {
        while (state.isAnalyzing) {
            progress.animateTo(
                (progress.value + 0.08f).coerceAtMost(0.92f),
                animationSpec = tween(durationMillis = 240, easing = LinearEasing),
            )
        }
    }
    LaunchedEffect(state.result) {
        if (state.result != null) progress.animateTo(1f, tween(350))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGreen)
            .padding(24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
        }

        Spacer(Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .aspectRatio(0.85f)
                .clip(RoundedCornerShape(topStart = 90.dp, topEnd = 90.dp, bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = photoBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Your captured photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                if (state.error == null) "Detecting face shape..." else "Analysis paused",
                fontFamily = JungeFont,
                fontSize = 26.sp,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${(progress.value * 100).toInt()}%",
                fontFamily = JungeFont,
                fontSize = 22.sp,
                color = CoralAccent
            )
        }
        Spacer(Modifier.height(10.dp))
        if (state.error == null) {
            LinearProgressIndicator(
                progress = { progress.value },
                color = CoralAccent,
                trackColor = Color.White.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Running privately on your device. Your photo is not uploaded.",
                fontFamily = JungeFont,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(state.error, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            PillButton("Retry analysis", onRetry)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit) {
    WeGlowPrimaryButton(
        text = text,
        onClick = onClick
    )
}

internal suspend fun loadImageBitmap(context: android.content.Context, uri: Uri): ImageBitmap? =
    withContext(Dispatchers.IO) {
        try {
            com.example.weglow.core.image.ScanPhotoDecoder.decode(context.contentResolver, uri).asImageBitmap()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
