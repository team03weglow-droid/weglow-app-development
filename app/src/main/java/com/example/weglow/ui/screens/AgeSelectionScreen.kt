package com.example.weglow.ui.screens

import com.example.weglow.ui.theme.*
import com.example.weglow.ui.components.WeGlowOnboardingLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CardGreen = CheckboxGreen

private val AGE_OPTIONS = listOf("Under 14", "14–24", "25–35", "36–45", "46–59", "60+")

@Composable
fun AgeSelectionScreen(onContinue: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    WeGlowOnboardingLayout {
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("STEP 1 OF 4", style = MaterialTheme.typography.bodySmall, color = SoftGray)
            Text("AGE", style = MaterialTheme.typography.bodySmall, color = SoftGray)
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
            style = MaterialTheme.typography.headlineLarge,
            color = DarkGreen,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "At different ages, your skin needs different skincare treatments. Tell us to personalize your routine.",
            style = MaterialTheme.typography.bodyMedium,
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
                    Text(option, style = MaterialTheme.typography.titleMedium, color = DarkGreen)
                    RadioButton(selected = isSelected, onClick = { selected = option })
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { selected?.let(onContinue) },
            enabled = selected != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
        ) {
            Text("Continue", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
