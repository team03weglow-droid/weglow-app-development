package com.example.weglow.ui.screens

import com.example.weglow.ui.theme.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


private data class Concern(
    val name: String,
    val severity: String,
    val count: Int,
    val location: String,
    val dotColor: Color
)

private val exampleConcerns = listOf(
    Concern("Blackheads", "Moderate", 12, "T-zone", ConcernDark),
    Concern("Dark Spots", "Moderate", 8, "Cheeks", ConcernBrown),
    Concern("Whiteheads", "Mild", 5, "Chin", ConcernLavender),
    Concern("Papules", "Mild", 3, "Jawline", ConcernRose),
)

@Composable
fun ScanResultsScreen(photoUri: android.net.Uri?, onBack: () -> Unit, onViewRecommendations: () -> Unit) {
    val context = LocalContext.current
    var photoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photoUri) {
        photoBitmap = photoUri?.let { loadImageBitmap(context, it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .verticalScroll(rememberScrollState())
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.95f)
            ) {
                val bitmap = photoBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Your scanned photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(MintChip))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.ArrowBackIosNew, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(ConcernBlue))
                    }
                    Text(
                        "History",
                        fontFamily = JungeFont,
                        fontSize = 13.sp,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable { }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                ConcernDot(Modifier.align(Alignment.TopCenter).offset(y = 90.dp), Color.White, 14.dp)
                ConcernDot(Modifier.align(Alignment.TopCenter).offset(y = 150.dp), ConcernLavender, 20.dp)
                ConcernDot(Modifier.align(Alignment.Center).offset(x = (-70).dp, y = 40.dp), ConcernGold, 12.dp)
                ConcernDot(Modifier.align(Alignment.Center).offset(x = 90.dp, y = 10.dp), ConcernAmber, 22.dp)

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 28.dp)
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatColumn("SKIN SCORE", "74")
                    StatDivider()
                    StatColumn("CONCERNS", "${exampleConcerns.size}")
                    StatDivider()
                    StatColumn("CHANGE", "+3", valueColor = SuccessGreen)
                }
            }
        }

        Spacer(Modifier.height(44.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardWhite)
                    .padding(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PageBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = DarkGreen)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("STRUCTURAL ANALYSIS", fontFamily = JungeFont, fontSize = 10.sp, color = SoftGray)
                        Text(
                            "High Precision",
                            fontFamily = JungeFont,
                            fontSize = 10.sp,
                            color = DarkGreen,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MintChip)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text("Detected Face Shape: ", fontFamily = JungeFont, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                        Text("Triangle (Heart)", fontFamily = JungeFont, fontSize = 15.sp, fontStyle = FontStyle.Italic, color = PrimaryBlack)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Wider forehead tapering to a contoured jawline & chin. Recommendations optimized for balanced cheek hydration and T-zone pore refinement.",
                        fontFamily = JungeFont,
                        fontSize = 12.sp,
                        color = SoftGray
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Detected Concerns", fontFamily = JungeFont, fontSize = 18.sp, color = PrimaryBlack)
                Text("Aug 28, 2026", fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray)
            }
            Spacer(Modifier.height(12.dp))

            exampleConcerns.forEach { concern ->
                ConcernRow(concern)
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))
            PillButton(
                text = "View Recommendations  →",
                onClick = onViewRecommendations
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConcernDot(modifier: Modifier, color: Color, size: Dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f))
            .padding(3.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(color))
    }
}

@Composable
private fun StatColumn(label: String, value: String, valueColor: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontFamily = JungeFont, fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(4.dp))
        Text(value, fontFamily = JungeFont, fontSize = 22.sp, color = valueColor)
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(Color.White.copy(alpha = 0.25f))
    )
}

@Composable
private fun ConcernRow(concern: Concern) {
    val severityColor = if (concern.severity == "Moderate") WarningOrange else SuccessGreen
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .clickable { }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(concern.dotColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(concern.dotColor))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(concern.name, fontFamily = JungeFont, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                Spacer(Modifier.width(8.dp))
                Text(
                    concern.severity,
                    fontFamily = JungeFont,
                    fontSize = 10.sp,
                    color = severityColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(severityColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Text(
                "${concern.count} detected · ${concern.location}",
                fontFamily = JungeFont,
                fontSize = 12.sp,
                color = SoftGray
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = SoftGray)
    }
}