package com.example.weglow.ui.screens

import com.example.weglow.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun SkinSensitivityScreen(
    onAnswer: (Boolean) -> Unit,
    isSaving: Boolean = false,
    errorMessage: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("STEP 3 OF 5", fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray)
            Text("GENDER", fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray)
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
                    .fillMaxWidth(0.6f)
                    .fillMaxHeight()
                    .background(DarkGreen, RoundedCornerShape(2.dp))
            )
        }

        Spacer(modifier = Modifier.height(120.dp))

        Text(
            text = "Is your skin sensitive?",
            fontFamily = JungeFont,
            fontSize = 32.sp,
            color = DarkGreen,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Skin sensitivity has a big impact on some products lead to skin allergies.",
            fontFamily = JungeFont,
            fontSize = 14.sp,
            color = TextBlack,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        errorMessage?.let {
            Text(it, fontFamily = JungeFont, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(10.dp))
        }

        Button(
            onClick = { onAnswer(true) },
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = DarkGreen, contentColor = MaterialTheme.colorScheme.onPrimary),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.fillMaxWidth().height(58.dp)
        ) {
            Text("Yes", fontFamily = JungeFont, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = { onAnswer(false) },
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = DarkGreen, contentColor = MaterialTheme.colorScheme.onPrimary),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.fillMaxWidth().height(58.dp)
        ) {
            Text("No", fontFamily = JungeFont, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}