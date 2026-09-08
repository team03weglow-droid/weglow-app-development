package com.example.weglow.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weglow.R
import com.example.weglow.ui.components.WeGlowErrorView
import com.example.weglow.ui.components.WeGlowPasswordField
import com.example.weglow.ui.components.WeGlowPrimaryButton
import com.example.weglow.ui.components.WeGlowTextField
import com.example.weglow.ui.theme.*

@Composable
fun LoginScreen(
    onLoginClick: (String, String) -> Unit,
    onCreateAccountClick: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(PageBackground).padding(horizontal = WeGlowSpacing.lg).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(WeGlowSpacing.xxl))
        Image(painterResource(R.drawable.weglow_logo), null, Modifier.size(48.dp))
        Spacer(Modifier.height(WeGlowSpacing.xs))
        Text("WEGLOW", fontFamily = HurricaneFont, fontSize = 44.sp, color = TextBlack)
        Spacer(Modifier.height(36.dp))
        Text("Welcome back", style = MaterialTheme.typography.headlineLarge, color = TextBlack)
        Spacer(Modifier.height(6.dp))
        Text("Log in to pick up your routine where you left it", style = MaterialTheme.typography.bodySmall, color = SoftGray)
        Spacer(Modifier.height(28.dp))

        WeGlowTextField(email, { email = it }, "Email", enabled = !isLoading)
        Spacer(Modifier.height(14.dp))
        WeGlowPasswordField(password, { password = it }, enabled = !isLoading)

        Text(
            "Forgot password?",
            style = MaterialTheme.typography.bodySmall,
            color = SoftGray,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, end = 4.dp),
        )
        Spacer(Modifier.height(20.dp))
        WeGlowPrimaryButton("Log in", { onLoginClick(email.trim(), password) }, loading = isLoading, enabled = email.isNotBlank() && password.isNotBlank())
        errorMessage?.let { WeGlowErrorView(it) }

        Spacer(Modifier.height(WeGlowSpacing.lg))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(Modifier.weight(1f), color = SoftGray.copy(alpha = 0.4f))
            Text("SOCIAL LOGIN COMING SOON", style = MaterialTheme.typography.labelMedium, color = SoftGray, modifier = Modifier.padding(horizontal = 10.dp))
            HorizontalDivider(Modifier.weight(1f), color = SoftGray.copy(alpha = 0.4f))
        }

        Spacer(Modifier.height(WeGlowSpacing.xxl))
        Row {
            Text("New to WeGlow  ", style = MaterialTheme.typography.bodyMedium, color = SoftGray)
            Text(
                "Create an account",
                style = MaterialTheme.typography.bodyMedium,
                color = TextBlack,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onCreateAccountClick),
            )
        }
        Spacer(Modifier.height(WeGlowSpacing.lg))
    }
}
