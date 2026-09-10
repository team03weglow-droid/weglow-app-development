package com.example.weglow.ui.screens

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weglow.domain.model.AcneScanResult
import com.example.weglow.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun ScanResultsScreen(
    photoUri: Uri?,
    result: AcneScanResult?,
    onBack: () -> Unit,
    onViewRecommendations: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(photoUri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photoUri) { bitmap = photoUri?.let { loadImageBitmap(context, it) } }
    val palette = listOf(ConcernRose, ConcernLavender, ConcernBrown, ConcernGold, ConcernBlue)
    val labels = result?.detections?.map { it.label }?.distinct()?.sorted().orEmpty()
    val colors = labels.mapIndexed { index, label -> label to palette[index % palette.size] }.toMap()

    Column(
        Modifier.fillMaxSize().background(PageBackground)
            .verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        TextButton(onClick = onBack) { Text("Back", color = DarkGreen) }
        Text("Your scan results", fontFamily = JungeFont, fontSize = 26.sp, color = PrimaryBlack)
        Spacer(Modifier.height(20.dp))
        if (result == null) {
            Text("No completed scan is available. Please take or select a new photo.")
            return@Column
        }
        Box(
            Modifier.fillMaxWidth()
                .aspectRatio(result.imageWidth.toFloat() / result.imageHeight)
                .clip(RoundedCornerShape(20.dp)).background(MintChip)
        ) {
            bitmap?.let { photo ->
                Image(photo, "Scanned photo with detected areas", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Canvas(Modifier.fillMaxSize()) {
                    result.detections.forEach { detection ->
                        // Keep the detector's bounding coordinates for the center, but present
                        // each finding as a circular marker on the result image.
                        val left = detection.left * size.width
                        val top = detection.top * size.height
                        val right = detection.right * size.width
                        val bottom = detection.bottom * size.height
                        drawCircle(
                            color = colors.getValue(detection.label),
                            center = Offset((left + right) / 2f, (top + bottom) / 2f),
                            radius = maxOf((right - left) / 2f, (bottom - top) / 2f),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("${result.detections.size} detections · ${labels.size} concern types", fontSize = 18.sp, color = DarkGreen)
        Spacer(Modifier.height(8.dp))
        Text("Model-detected areas from your scan.", color = SoftGray, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        if (result.detections.isEmpty()) {
            Text("No acne detected above the model’s ${(result.confidenceThreshold * 100).roundToInt()}% confidence threshold.", color = PrimaryBlack)
            Spacer(Modifier.height(8.dp))
            Text("This does not guarantee that the photo contains no acne.", color = SoftGray, fontSize = 13.sp)
        }
        labels.forEach { label ->
            val detections = result.detections.filter { it.label == label }
            Column(
                Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(16.dp)).background(CardWhite).padding(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(18.dp).background(colors.getValue(label), RoundedCornerShape(4.dp)))
                    Text("$label · ${detections.size} detected", fontSize = 17.sp, color = PrimaryBlack)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Automated predictions can be incorrect. These results are not a medical diagnosis.", color = SoftGray, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
        PillButton("View routines", onViewRecommendations)
        Spacer(Modifier.height(24.dp))
    }
}
