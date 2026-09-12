package com.example.weglow.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.example.weglow.R
import com.example.weglow.ui.theme.*

private val WelcomePageBackground = WelcomeBackground

@Composable
fun WelcomeIntroScreen(onStartGlow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WelcomePageBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.weglow_logo),
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "WEGLOW",
                fontFamily = HurricaneFont,
                fontSize = 26.sp,
                color = DarkGreen
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Divided by Acne",
            style = MaterialTheme.typography.headlineLarge,
            color = DarkGreen,
            textAlign = TextAlign.Center
        )

        Row {
            Text(
                text = "United by ",
                style = MaterialTheme.typography.headlineLarge,
                color = DarkGreen
            )

            Text(
                text = "WeGlow",
                fontFamily = HurricaneFont,
                fontSize = 34.sp,
                color = DarkGreen
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onStartGlow,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = DarkGreen
            ),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.height(52.dp)
        ) {
            Text(
                "Start your Glow",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Image(
            painter = painterResource(R.drawable.welcome_hero),
            contentDescription = "Welcome hero",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
        )
    }
}
