package com.example.weglow.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weglow.R
import com.example.weglow.ui.components.WeGlowPlannedFeature
import com.example.weglow.ui.theme.*
import com.example.weglow.domain.model.HairstyleResult
import com.example.weglow.domain.model.HairstyleRecommendation


@Composable
fun HairstyleResultsScreen(result: HairstyleResult, onBack: () -> Unit) {
    val traits = result.traits
    val description = result.description
    val styles = result.recommendations

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextBlack)
            }
            Text("Hairstyle Match", style = MaterialTheme.typography.titleMedium, color = TextBlack)
        }
        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceCool)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape).background(DarkGreen))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Your ${result.faceShape} Face Shape",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextBlack
                    )
                }
                Text(
                    "${result.confidencePercent}%\nCONFIDENCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextBlack,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CardWhite)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = SoftGray)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                traits.take(2).forEach { trait ->
                    TraitChip(trait, Modifier.weight(1f, fill = false))
                }
            }
            traits.drop(2).firstOrNull()?.let { trait ->
                Spacer(Modifier.height(8.dp))
                TraitChip(trait)
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("CURATED SELECTION", style = MaterialTheme.typography.labelSmall, color = DarkGreen)
        Text("Recommended for you", style = MaterialTheme.typography.titleLarge, color = TextBlack)
        Spacer(Modifier.height(14.dp))

        WeGlowPlannedFeature("Style filters")
        WeGlowPlannedFeature("AR Mirror")
        Spacer(Modifier.height(14.dp))

        // Content-sized rows let the new type scale grow without cutting off card actions.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            styles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { style ->
                        Box(Modifier.weight(1f)) { HairstyleCard(style) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TraitChip(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(CardWhite)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(DarkGreen))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TextBlack)
    }
}

@Composable
private fun HairstyleCard(style: HairstyleRecommendation) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCool)
            .padding(12.dp)
    ) {
        Box {
            Image(
                painter = painterResource(style.imageRes),
                contentDescription = style.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
                    .clip(RoundedCornerShape(16.dp))
            )
            Text(
                "${style.matchPercent}% Match",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(DarkGreen)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        WeGlowPlannedFeature("Try AR")
        Text(style.category, style = MaterialTheme.typography.labelSmall, color = DarkGreen)
        Text(style.title, style = MaterialTheme.typography.titleMedium, color = TextBlack, modifier = Modifier.padding(top = 2.dp))
        Text(
            style.description,
            style = MaterialTheme.typography.bodySmall,
            color = SoftGray,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
