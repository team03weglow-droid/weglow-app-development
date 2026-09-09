package com.example.weglow.ui.screens

import com.example.weglow.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CardGreen = CheckboxGreen

private val AGE_OPTIONS = listOf("Under 14", "14 - 25", "25 - 35", "36 - 45", "45 - 50")

@Composable
fun AgeSelectionScreen(onContinue: (String) -> Unit) {
    var selected by remember { mutableStateOf("14 - 25") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("STEP 1 OF 4", fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray)
            Text("AGE", fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray)
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
                    .fillMaxWidth(0.25f)
                    .fillMaxHeight()
                    .background(DarkGreen, RoundedCornerShape(2.dp))
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "How old are you?",
            fontFamily = JungeFont,
            fontSize = 34.sp,
            color = DarkGreen,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "At different ages, your skin needs different skincare treatments. Tell us to personalize your routine.",
            fontFamily = JungeFont,
            fontSize = 14.sp,
            color = TextBlack,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(36.dp))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AGE_OPTIONS.forEach { option ->
                val isSelected = option == selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isSelected) CardGreen else CardGray,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .then(
                            if (isSelected) Modifier.border(1.5.dp, DarkGreen, RoundedCornerShape(16.dp))
                            else Modifier
                        )
                        .clickable { selected = option }
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Text(option, fontFamily = JungeFont, fontSize = 18.sp, color = DarkGreen)
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .border(1.5.dp, SoftGray, CircleShape)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onContinue(selected) },
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
        ) {
            Text("CONTINUE", fontFamily = JungeFont, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}