package com.example.weglow.ui.screens

import com.example.weglow.ui.theme.*
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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


enum class ScanMode { ACNE, HAIRSTYLE }
private enum class ScanFlowState { MODE_SELECT, CAMERA, ANALYZING }

@Composable
fun ScanScreen(
    photoUri: Uri?,
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

    var flowState by remember { mutableStateOf(ScanFlowState.MODE_SELECT) }
    var scanMode by remember { mutableStateOf(ScanMode.ACNE) }

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

        !hasCameraPermission -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    "WeGlow needs camera access to scan your skin.",
                    fontFamily = JungeFont,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                PillButton(
                    text = "Grant camera access",
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )
                Spacer(Modifier.weight(1f))
            }
        }

        flowState == ScanFlowState.ANALYZING -> {
            AnalyzingScreen(
                mode = scanMode,
                photoUri = photoUri,
                onFinished = {
                    if (scanMode == ScanMode.HAIRSTYLE) onHairstyleScanComplete() else onAcneScanComplete()
                },
                onCancel = { flowState = ScanFlowState.CAMERA }
            )
        }

        else -> {
            CameraCaptureScreen(
                onBack = { flowState = ScanFlowState.MODE_SELECT },
                onPhotoReady = { uri ->
                    onPhotoCaptured(uri)
                    flowState = ScanFlowState.ANALYZING
                }
            )
        }
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
private fun CameraCaptureScreen(onBack: () -> Unit, onPhotoReady: (Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var flashOn by remember { mutableStateOf(false) }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPhotoReady(uri) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder().build()
                    imageCapture = capture

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
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
                "within the guide to begin analysis",
                fontFamily = JungeFont,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val guideRadius = size.width * 0.42f
            val center = Offset(size.width / 2f, size.height * 0.42f)
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = guideRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = 32.dp, end = 32.dp),
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
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
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
private fun AnalyzingScreen(mode: ScanMode, photoUri: Uri?, onFinished: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var photoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photoUri) {
        photoBitmap = photoUri?.let { loadImageBitmap(context, it) }
    }

    val progress = remember { Animatable(0f) }
    val messages = if (mode == ScanMode.HAIRSTYLE) {
        listOf(
            "Mapping facial contours..." to 0.2f,
            "Detecting face shape..." to 0.55f,
            "Matching hairstyles..." to 0.85f,
            "Finishing up..." to 1.0f,
        )
    } else {
        listOf(
            "Detecting facial landmarks..." to 0.15f,
            "Analyzing skin texture..." to 0.55f,
            "Calculating skin score..." to 0.85f,
            "Finishing up..." to 1.0f,
        )
    }
    var currentMessage by remember { mutableStateOf(messages.first().first) }

    LaunchedEffect(Unit) {
        for ((message, target) in messages) {
            currentMessage = message
            progress.animateTo(target, animationSpec = tween(durationMillis = 700, easing = LinearEasing))
        }
        delay(300)
        onFinished()
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
                currentMessage,
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
        LinearProgressIndicator(
            progress = { progress.value },
            color = CoralAccent,
            trackColor = Color.White.copy(alpha = 0.2f),
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "AI analysis in progress...",
            fontFamily = JungeFont,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
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
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }