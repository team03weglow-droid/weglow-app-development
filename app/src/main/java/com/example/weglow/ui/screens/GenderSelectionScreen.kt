package com.example.weglow.ui.screens

import com.example.weglow.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


private val GENDER_OPTIONS = listOf("Female", "Male", "Other/Prefer not to say")

@Composable
fun GenderSelectionScreen(onBack: () -> Unit, onContinue: (String) -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }

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

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "What's your gender?",
            fontFamily = JungeFont,
            fontSize = 32.sp,
            color = DarkGreen,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Hormones have a big impact on how our skin looks and feels at every age.",
            fontFamily = JungeFont,
            fontSize = 14.sp,
            color = TextBlack,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GENDER_OPTIONS.forEach { option ->
                val isSelected = option == selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) SelectedGreen else CardGray, RoundedCornerShape(999.dp))
                        .then(
                            if (isSelected) Modifier.border(2.dp, DarkGreen, RoundedCornerShape(999.dp))
                            else Modifier
                        )
                        .clickable { selected = option }
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Text(option, fontFamily = JungeFont, fontSize = 17.sp, color = TextBlack)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.weight(1f).height(54.dp)
            ) {
                Text("Back", fontFamily = JungeFont, fontSize = 15.sp, color = TextBlack)
            }
            Button(
                onClick = {
                    selected?.let(onContinue)
                },
                enabled = selected != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkGreen,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.weight(1f).height(54.dp)
            ) {
                Text("Continue", fontFamily = JungeFont, fontSize = 15.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}