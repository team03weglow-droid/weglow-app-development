package com.example.weglow.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weglow.R
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
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextBlack)
            }
            Text("Hairstyle Match", fontFamily = JungeFont, fontSize = 18.sp, color = TextBlack)
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
                        "Why Oval Works With Everything",
                        fontFamily = JungeFont,
                        fontSize = 20.sp,
                        color = TextBlack
                    )
                }
                Text(
                    "IDEAL\nSYMMETRY",
                    fontFamily = JungeFont,
                    fontSize = 10.sp,
                    color = TextBlack,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CardWhite)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(description, fontFamily = JungeFont, fontSize = 13.sp, color = SoftGray)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TraitChip(traits[0], Modifier.weight(1f, fill = false))
                TraitChip(traits[1], Modifier.weight(1f, fill = false))
            }
            Spacer(Modifier.height(8.dp))
            TraitChip(traits[2])
        }

        Spacer(Modifier.height(28.dp))
        Text("CURATED SELECTION", fontFamily = JungeFont, fontSize = 11.sp, color = DarkGreen)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text("RECOMMENDED FOR YOU", fontFamily = JungeFont, fontSize = 22.sp, color = TextBlack)
            Text(
                "Filter (4)",
                fontFamily = JungeFont,
                fontSize = 13.sp,
                color = TextBlack,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { }
            )
        }
        Spacer(Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(((styles.size + 1) / 2 * 300).dp)
        ) {
            items(styles) { style ->
                HairstyleCard(style)
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(DarkGreen)
                .clickable { }
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Launch 3D AR Mirror", fontFamily = JungeFont, fontSize = 15.sp, color = Color.White)
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
        Text(label, fontFamily = JungeFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextBlack)
    }
}

@Composable
private fun HairstyleCard(style: HairstyleRecommendation) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
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
                fontFamily = JungeFont,
                fontSize = 11.sp,
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
        Text(style.category, fontFamily = JungeFont, fontSize = 10.sp, color = DarkGreen)
        Text(style.title, fontFamily = JungeFont, fontSize = 18.sp, color = TextBlack, modifier = Modifier.padding(top = 2.dp))
        Text(
            style.description,
            fontFamily = JungeFont,
            fontSize = 11.sp,
            color = SoftGray,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(CardWhite)
                .clickable { }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Try in AR", fontFamily = JungeFont, fontSize = 12.sp, color = TextBlack)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = TextBlack, modifier = Modifier.size(14.dp))
        }
    }
}