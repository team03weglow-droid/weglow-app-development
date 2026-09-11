package com.example.weglow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weglow.domain.model.ProductRecommendation
import com.example.weglow.domain.model.RecommendationBasis
import com.example.weglow.domain.model.RecommendationResult
import com.example.weglow.ui.components.WeGlowErrorView
import com.example.weglow.ui.components.WeGlowLoadingView
import com.example.weglow.ui.components.WeGlowProductImage
import com.example.weglow.ui.theme.*

@Composable
fun RecommendationsScreen(
    isLoading: Boolean,
    result: RecommendationResult?,
    errorMessage: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(PageBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryBlack)
            }
            Text("Recommended for You", fontFamily = JungeFont, fontSize = 20.sp, color = PrimaryBlack)
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WeGlowLoadingView(message = "Finding products for you…")
            }
            errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WeGlowErrorView(message = errorMessage, onRetry = onRetry)
            }
            result == null -> Unit
            !result.hasPersonalizationSignal -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Add your skin type or run a scan",
                        fontFamily = JungeFont,
                        fontSize = 18.sp,
                        color = PrimaryBlack,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "We need at least your skin profile or a recent scan to personalize recommendations.",
                        color = SoftGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            result.recommendations.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No matching products yet", fontFamily = JungeFont, fontSize = 18.sp, color = PrimaryBlack)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "None of the current products in Discover matched your profile or scan.",
                        color = SoftGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> RecommendationsList(result)
        }
    }
}

@Composable
private fun RecommendationsList(result: RecommendationResult) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(contextSummary(result), color = SoftGray, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
            }
        }
        items(result.recommendations, key = { it.product.id }) { recommendation ->
            Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                RecommendationCard(recommendation)
            }
        }
        item {
            Text(
                "Recommendations are for general skincare guidance and are not medical advice.",
                color = SoftGray,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
            Spacer(Modifier.height(74.dp))
        }
    }
}

private fun contextSummary(result: RecommendationResult): String {
    val skinPart = result.skinType?.takeIf(String::isNotBlank)?.let { "your $it skin profile" }
    return when {
        result.basis == RecommendationBasis.PROFILE_AND_SCAN && result.concerns.isNotEmpty() ->
            "Based on ${skinPart ?: "your profile"} and concerns detected in your recent scan."
        result.basis == RecommendationBasis.PROFILE_AND_SCAN ->
            "Based on ${skinPart ?: "your profile"}. Your recent scan detected no concerns."
        skinPart != null -> "Based on $skinPart."
        else -> "Based on your profile."
    }
}

@Composable
private fun RecommendationCard(recommendation: ProductRecommendation) {
    val product = recommendation.product
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeGlowProductImage(
            imageUrl = product.imageUrl,
            contentDescription = product.name,
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            product.brandName?.let { brand ->
                Text(brand, fontFamily = JungeFont, fontSize = 11.sp, color = SoftGray)
                Spacer(Modifier.height(2.dp))
            }
            Text(product.name, fontFamily = JungeFont, fontSize = 15.sp, color = PrimaryBlack)
            Spacer(Modifier.height(3.dp))
            Text(product.priceLabel, fontFamily = JungeFont, fontSize = 13.sp, color = PrimaryBlack)
            if (recommendation.reasons.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                recommendation.reasons.forEach { reason ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(DarkGreen))
                        Spacer(Modifier.width(6.dp))
                        Text(reason, color = DarkGreen, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}
