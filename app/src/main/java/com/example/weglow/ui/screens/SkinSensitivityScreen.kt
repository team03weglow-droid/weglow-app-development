package com.example.weglow.ui.screens

import com.example.weglow.ui.theme.*
import com.example.weglow.ui.components.WeGlowOnboardingLayout
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
    onBack: () -> Unit = {},
    isSaving: Boolean = false,
    errorMessage: String? = null,
) {
    WeGlowOnboardingLayout {
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("STEP 4 OF 4", style = MaterialTheme.typography.bodySmall, color = SoftGray)
            Text("SENSITIVITY", style = MaterialTheme.typography.bodySmall, color = SoftGray)
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
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(DarkGreen, RoundedCornerShape(2.dp))
            )
        }

        Spacer(modifier = Modifier.height(72.dp))

        Text(
            text = "Is your skin sensitive?",
            style = MaterialTheme.typography.headlineLarge,
            color = DarkGreen,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Some products can irritate sensitive skin. Your answer helps us choose gentler options.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextBlack,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        errorMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(10.dp))
        }

        Button(
            onClick = { onAnswer(true) },
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = DarkGreen, contentColor = MaterialTheme.colorScheme.onPrimary),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.fillMaxWidth().height(58.dp)
        ) {
            Text("Yes, it is sensitive", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedButton(
            onClick = { onAnswer(false) },
            enabled = !isSaving,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkGreen),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.fillMaxWidth().height(58.dp)
        ) {
            Text("No, not usually", style = MaterialTheme.typography.labelLarge)
        }

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back", style = MaterialTheme.typography.labelMedium, color = SoftGray)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
