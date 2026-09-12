package com.example.weglow.ui.screens

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.weglow.domain.model.AcneScanResult
import com.example.weglow.ui.theme.*

@Composable
fun ScanResultsScreen(
    photoUri: Uri?,
    result: AcneScanResult?,
    onBack: () -> Unit,
    onViewRecommendations: () -> Unit,
) {
    var showAreas by remember { mutableStateOf(true) }
    var showPhotoDetails by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var bitmap by remember(photoUri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photoUri) { bitmap = photoUri?.let { loadImageBitmap(context, it) } }
    val palette = listOf(ConcernRose, ConcernLavender, ConcernBrown, ConcernGold, ConcernBlue)
    val labels = result?.detections?.map { it.label }?.distinct()?.sorted().orEmpty()
    val colors = labels.mapIndexed { index, label -> label to palette[index % palette.size] }.toMap()

    Column(Modifier.fillMaxSize().background(PageBackground).verticalScroll(rememberScrollState())) {
        if (result == null) {
            TextButton(onClick = onBack, modifier = Modifier.padding(12.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp)); Text("Back")
            }
            Text("No completed scan is available. Please take or select a new photo.", Modifier.padding(20.dp))
            return@Column
        }
        Box(Modifier.fillMaxWidth().height(320.dp).background(Color(0xFF1C1917))) {
            bitmap?.let { photo ->
                Image(photo, "Scanned photo with detected areas", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (showAreas) Canvas(Modifier.fillMaxSize()) {
                    // ContentScale.Crop fills the hero without black side strips. Map the
                    // model's normalized coordinates into the cropped rendered rect.
                    val imageScale = maxOf(size.width / result.imageWidth, size.height / result.imageHeight)
                    val renderedWidth = result.imageWidth * imageScale
                    val renderedHeight = result.imageHeight * imageScale
                    val imageLeft = (size.width - renderedWidth) / 2f
                    val imageTop = (size.height - renderedHeight) / 2f
                    result.detections.forEach { detection ->
                        val topLeft = Offset(
                            imageLeft + detection.left * renderedWidth,
                            imageTop + detection.top * renderedHeight,
                        )
                        drawRect(
                            color = colors.getValue(detection.label),
                            topLeft = topLeft,
                            size = Size(
                                (detection.right - detection.left) * renderedWidth,
                                (detection.bottom - detection.top) * renderedHeight,
                            ),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                }
            }
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().zIndex(1f).background(DarkGreen.copy(alpha = 0.86f)).padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(12.dp)); Text("Scan results", style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().zIndex(1f).padding(16.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF171717)).padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ResultMetric("DETECTIONS", result.detections.size.toString())
                Box(Modifier.width(1.dp).height(34.dp).background(Color.White.copy(alpha = 0.2f)))
                ResultMetric("CONCERN TYPES", labels.size.toString())
                Box(Modifier.width(1.dp).height(34.dp).background(Color.White.copy(alpha = 0.2f)))
                ResultMetric("RESULT", "Preview")
            }
        }
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Automated predictions can be incorrect. This is not a medical diagnosis.",
                color = SoftGray, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show detected areas", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = showAreas, onCheckedChange = { showAreas = it })
            }
            TextButton(onClick = { showPhotoDetails = !showPhotoDetails }) { Text(if (showPhotoDetails) "Hide photo details" else "Photo details") }
            if (showPhotoDetails) Text("Photo size: ${result.imageWidth} × ${result.imageHeight}", style = MaterialTheme.typography.bodySmall, color = SoftGray)
            Text("Detected Concerns", style = MaterialTheme.typography.titleLarge, color = PrimaryBlack)
            Text("Possible skin concerns identified in your scan.", color = SoftGray, style = MaterialTheme.typography.bodyMedium)
            if (result.detections.isEmpty()) {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("No concerns detected", style = MaterialTheme.typography.titleSmall, color = PrimaryBlack)
                        Spacer(Modifier.height(6.dp))
                        Text("No findings were detected. This does not guarantee that the photo contains no acne.", color = SoftGray, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                labels.forEach { label ->
                    val count = result.detections.count { it.label == label }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardWhite).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(24.dp)) {
                                drawCircle(
                                    color = colors.getValue(label),
                                    radius = size.minDimension / 2f - 2.dp.toPx(),
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge, color = PrimaryBlack)
                            Text("$count detected", color = SoftGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Text("Automated predictions can be incorrect. These results are not a medical diagnosis.", color = SoftGray, style = MaterialTheme.typography.bodySmall)
            PillButton("View Recommendations", onViewRecommendations)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ResultMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFFD4D4D4), letterSpacing = 0.5.sp)
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}
