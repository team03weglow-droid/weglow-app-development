package com.example.weglow.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weglow.R
import com.example.weglow.ui.theme.HurricaneFont
import com.example.weglow.ui.theme.LoadingBackgroundColor
import kotlinx.coroutines.delay

private val LoadingBackground = LoadingBackgroundColor

@Composable
fun LoadingScreen(onTimeout: () -> Unit) {
    val logoScale = remember { Animatable(0.6f) }
    val logoAlpha = remember { Animatable(0f) }
    val wordmarkAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, animationSpec = tween(500))
        logoScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        wordmarkAlpha.animateTo(1f, animationSpec = tween(450))
        delay(900)
        onTimeout()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LoadingBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTimeout
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.weglow_logo),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer(
                    scaleX = logoScale.value,
                    scaleY = logoScale.value,
                    alpha = logoAlpha.value
                )
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "WEGLOW",
            fontFamily = HurricaneFont,
            fontSize = 56.sp,
            modifier = Modifier.graphicsLayer(alpha = wordmarkAlpha.value)
        )
    }
}