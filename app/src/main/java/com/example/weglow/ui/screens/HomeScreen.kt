package com.example.weglow.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weglow.R
import com.example.weglow.domain.model.RoutineStep
import com.example.weglow.ui.components.ProfileAvatar
import com.example.weglow.ui.components.rememberDecodedBitmap
import com.example.weglow.ui.theme.*


private data class Insight(val icon: ImageVector, val title: String, val body: String)

private val insights = listOf(
    Insight(Icons.Filled.WaterDrop, "Hydration Low", "Drink more water & use hydrating serum"),
    Insight(Icons.Filled.WbSunny, "SPF Reminder", "Apply sunscreen before you head out"),
    Insight(Icons.Filled.NightsStay, "Sleep & Skin", "Aim for 7+ hours to help skin repair"),
)

@Composable
fun HomeScreen(
    onScanClick: () -> Unit,
    onDiscoverClick: () -> Unit,
    onRecommendationsClick: () -> Unit,
    displayName: String? = null,
    profileImage: ByteArray? = null,
    morningRoutine: List<RoutineStep> = emptyList(),
) {
    val avatarBitmap = rememberDecodedBitmap(profileImage)
    val skinScore = 74
    val scoreDelta = 3
    val hydration = 0.82f
    val clarity = 0.68f
    val texture = 0.71f

    // Local, ephemeral "done today" state for the real routine steps below - not persisted,
    // matching the same pattern already used on the dedicated Routines screen.
    var doneSteps by remember(morningRoutine) { mutableStateOf(setOf<Int>()) }
    val stepsWithProduct = morningRoutine.filter { it.product != null }
    val doneCount = stepsWithProduct.indices.count { it in doneSteps }
    val totalCount = stepsWithProduct.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.weglow_logo),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            ProfileAvatar(image = avatarBitmap, size = 36.dp)
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = displayName?.let { "Hello, $it" } ?: "Hello there", fontFamily = JungeFont, fontSize = 32.sp, color = TextBlack)

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardWhite)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkinScoreRing(score = skinScore, modifier = Modifier.size(140.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("THIS WEEK", fontFamily = JungeFont, fontSize = 11.sp, color = SoftGray)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = CoralAccent, modifier = Modifier.size(13.dp))
                        Text("+$scoreDelta from last scan", fontFamily = JungeFont, fontSize = 12.sp, color = CoralAccent)
                    }
                    Text("Your skin is improving!", fontFamily = JungeFont, fontSize = 14.sp, color = TextBlack)

                    Spacer(modifier = Modifier.height(12.dp))
                    LabeledProgress("Hydration", hydration)
                    Spacer(modifier = Modifier.height(8.dp))
                    LabeledProgress("Clarity", clarity)
                    Spacer(modifier = Modifier.height(8.dp))
                    LabeledProgress("Texture", texture)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkGreen)
                    .clickable(onClick = onScanClick)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CenterFocusStrong, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Scan My Face", fontFamily = JungeFont, fontSize = 16.sp, color = Color.White)
                        Text("AI analysis in 30 seconds", fontFamily = JungeFont, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CoralAccent)
                    .clickable(onClick = onDiscoverClick)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CenterFocusStrong, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Find hairstyles", fontFamily = JungeFont, fontSize = 16.sp, color = Color.White)
                        Text(
                            "Find the best hairstyle recommended for your face shape",
                            fontFamily = JungeFont,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardWhite)
                    .clickable(onClick = onRecommendationsClick)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MintChip),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DarkGreen)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Recommended for You", fontFamily = JungeFont, fontSize = 16.sp, color = TextBlack)
                        Text(
                            "Products picked from your profile and latest scan",
                            fontFamily = JungeFont,
                            fontSize = 12.sp,
                            color = SoftGray,
                        )
                    }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = DarkGreen)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardWhite)
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text("Morning Routine", fontFamily = JungeFont, fontSize = 18.sp, color = TextBlack)
                        Text("$doneCount/$totalCount steps done", fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray)
                    }
                    Text("See All", fontFamily = JungeFont, fontSize = 13.sp, color = CoralAccent)
                }
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { if (totalCount > 0) doneCount / totalCount.toFloat() else 0f },
                    color = DarkGreen,
                    trackColor = MintChip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (stepsWithProduct.isEmpty()) {
                    Text(
                        "Your personalized routine will appear here once it's ready.",
                        fontFamily = JungeFont,
                        fontSize = 12.sp,
                        color = SoftGray,
                    )
                }

                stepsWithProduct.forEachIndexed { index, step ->
                    val done = index in doneSteps
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (index != stepsWithProduct.lastIndex) 10.dp else 0.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceSubtle)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(step.product?.name.orEmpty(), fontFamily = JungeFont, fontSize = 14.sp, color = TextBlack)
                            Text(step.label, fontFamily = JungeFont, fontSize = 11.sp, color = SoftGray)
                        }
                        Icon(
                            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = "Mark step done",
                            tint = if (done) DarkGreen else SoftGray,
                            modifier = Modifier.clickable {
                                doneSteps = if (done) doneSteps - index else doneSteps + index
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("AI Insights", fontFamily = JungeFont, fontSize = 18.sp, color = TextBlack)
            Spacer(modifier = Modifier.height(10.dp))
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp)
        ) {
            items(insights) { insight ->
                InsightCard(insight.icon, insight.title, insight.body, Modifier.width(200.dp))
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("For You", fontFamily = JungeFont, fontSize = 18.sp, color = TextBlack)
                Text("See All", fontFamily = JungeFont, fontSize = 13.sp, color = CoralAccent)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardWhite)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.radiance_serum),
                    contentDescription = "Article",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.6f)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                )
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Basics",
                        fontFamily = JungeFont,
                        fontSize = 11.sp,
                        color = DarkGreen,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MintChip)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    Text(
                        "The Complete Guide to Layering Skincare Products",
                        fontFamily = JungeFont,
                        fontSize = 15.sp,
                        color = TextBlack,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text("5 min read", fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}

@Composable
private fun LabeledProgress(label: String, progress: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray, modifier = Modifier.width(64.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = CoralAccent,
            trackColor = MintChip,
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("${(progress * 100).toInt()}%", fontFamily = JungeFont, fontSize = 11.sp, color = TextBlack)
    }
}

@Composable
private fun InsightCard(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = DarkGreen, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, fontFamily = JungeFont, fontSize = 13.sp, color = TextBlack)
        Text(body, fontFamily = JungeFont, fontSize = 11.sp, color = SoftGray, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SkinScoreRing(score: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = CoralAccent,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", fontFamily = JungeFont, fontSize = 34.sp, color = TextBlack)
            Text("Skin Score", fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray)
        }
    }
}