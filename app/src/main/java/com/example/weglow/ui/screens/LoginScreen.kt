package com.example.weglow.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weglow.R
import com.example.weglow.ui.components.WeGlowPlannedFeature
import com.example.weglow.ui.components.WeGlowErrorView
import com.example.weglow.ui.components.WeGlowPasswordField
import com.example.weglow.ui.components.WeGlowPrimaryButton
import com.example.weglow.ui.components.WeGlowTextField
import com.example.weglow.ui.theme.*

@Composable
fun LoginScreen(
    onLoginClick: (String, String) -> Unit,
    onGoogleLoginClick: () -> Unit,
    onCreateAccountClick: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .padding(horizontal = WeGlowSpacing.lg)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        Spacer(Modifier.height(WeGlowSpacing.xxl))

        Image(
            painter = painterResource(R.drawable.weglow_logo),
            contentDescription = "WeGlow logo",
            modifier = Modifier.size(48.dp),
        )

        Spacer(Modifier.height(WeGlowSpacing.xs))

        Text(
            text = "WEGLOW",
            fontFamily = HurricaneFont,
            fontSize = 44.sp,
            color = TextBlack,
        )

        Spacer(Modifier.height(36.dp))

        Text(
            text = "Welcome back",
            style = MaterialTheme.typography.headlineLarge,
            color = TextBlack,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Log in to pick up your routine where you left it",
            style = MaterialTheme.typography.bodySmall,
            color = SoftGray,
        )

        Spacer(Modifier.height(28.dp))

        // Email
        WeGlowTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "Email",
            enabled = !isLoading,
        )

        Spacer(Modifier.height(14.dp))

        // Password
        WeGlowPasswordField(
            value = password,
            onValueChange = { password = it },
            enabled = !isLoading,
        )

        Spacer(Modifier.height(24.dp))

        WeGlowPlannedFeature("Forgot password?")
        Spacer(Modifier.height(12.dp))

        // Email/password login
        WeGlowPrimaryButton(
            text = "Log in",
            onClick = {
                onLoginClick(
                    email.trim(),
                    password,
                )
            },
            loading = isLoading,
            enabled = email.isNotBlank() &&
                    password.isNotBlank(),
        )

        errorMessage?.let {
            WeGlowErrorView(it)
        }

        Spacer(Modifier.height(WeGlowSpacing.lg))

        // OR divider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {

            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = SoftGray.copy(alpha = 0.4f),
            )

            Text(
                text = "OR",
                style = MaterialTheme.typography.labelMedium,
                color = SoftGray,
                modifier = Modifier.padding(horizontal = 14.dp),
            )

            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = SoftGray.copy(alpha = 0.4f),
            )
        }

        Spacer(Modifier.height(WeGlowSpacing.md))

        // Google OAuth button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .border(
                    width = 1.dp,
                    color = SoftGray.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(
                        WeGlowRadius.medium
                    ),
                )
                .clickable(
                    enabled = !isLoading,
                    onClick = onGoogleLoginClick,
                )
                .padding(horizontal = WeGlowSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {

            Image(
                painter = painterResource(id = R.drawable.google_logo),
                contentDescription = "Google",
                modifier = Modifier.size(22.dp)
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = "Continue with Google",
                color = TextBlack,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(WeGlowSpacing.xxl))

        // Create account
        Row {

            Text(
                text = "New to WeGlow  ",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftGray,
            )

            Text(
                text = "Create an account",
                style = MaterialTheme.typography.bodyMedium,
                color = TextBlack,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(
                    enabled = !isLoading,
                    onClick = onCreateAccountClick,
                ),
            )
        }

        Spacer(Modifier.height(WeGlowSpacing.lg))
    }
}
