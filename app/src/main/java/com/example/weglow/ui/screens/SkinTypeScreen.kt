package com.example.weglow.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weglow.R
import com.example.weglow.ui.theme.*

private val CardGreen = CardWhite

private data class SkinTypeOption(val label: String, val description: String, val imageRes: Int)

private val SKIN_TYPES = listOf(
    SkinTypeOption("Dry & Tight", "Often feels parched, rough, or tight.", R.drawable.skin_type_dry),
    SkinTypeOption("Combination", "Oily T-zone but normal or dry cheeks.", R.drawable.skin_type_combination),
    SkinTypeOption("Oily", "Shiny all over. Prone to enlarged pores.", R.drawable.skin_type_oily),
    SkinTypeOption("Sensitive", "Easily irritated, prone to redness.", R.drawable.skin_type_sensitive),
)

@Composable
fun SkinTypeScreen(onNext: (String) -> Unit, onSkip: () -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("STEP 2 OF 4", fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray)
            Text("SKIN TYPE", fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(SoftGray.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .fillMaxHeight()
                    .background(DarkGreen, RoundedCornerShape(2.dp))
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = "What's a skin type?",
            fontFamily = JungeFont,
            fontSize = 32.sp,
            color = DarkGreen,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(28.dp))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SKIN_TYPES.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    rowItems.forEach { option ->
                        val isSelected = option.label == selected
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) SelectedGreen else CardGreen)
                                .then(
                                    if (isSelected) Modifier.border(2.dp, DarkGreen, RoundedCornerShape(16.dp))
                                    else Modifier
                                )
                                .clickable { selected = option.label }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                            ) {
                                Image(
                                    painter = painterResource(option.imageRes),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Text(
                                    text = option.label,
                                    color = TextBlack,
                                    fontFamily = JungeFont,
                                    fontSize = 18.sp,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(10.dp)
                                        .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = option.description,
                                fontFamily = JungeFont,
                                fontSize = 12.sp,
                                color = TextBlack,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { selected?.let { onNext(it) } },
            enabled = selected != null,
            colors = ButtonDefaults.buttonColors(containerColor = DarkGreen, contentColor = Color.White),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.fillMaxWidth().height(58.dp)
        ) {
            Text("Next Step  →", fontFamily = JungeFont, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Skip for now",
            fontFamily = JungeFont,
            fontSize = 13.sp,
            color = SoftGray,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSkip)
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}