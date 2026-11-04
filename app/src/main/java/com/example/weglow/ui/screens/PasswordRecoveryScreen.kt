package com.example.weglow.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.example.weglow.feature.auth.AuthUiState
import com.example.weglow.ui.components.*
import com.example.weglow.ui.theme.*

@Composable
fun PasswordRecoveryScreen(
    state: AuthUiState,
    isReset: Boolean,
    onSendLink: (String) -> Unit,
    onSavePassword: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords are never written to saved instance state.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    LaunchedEffect(state.recoveryReady) {
        if (!state.recoveryReady) {
            password = ""
            confirmation = ""
        }
    }
    BackHandler { if (!state.isLoading) onBack() }
    Column(
        Modifier.fillMaxSize().background(PageBackground).safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(WeGlowSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(WeGlowSpacing.md),
    ) {
        Text("WEGLOW", style = MaterialTheme.typography.titleMedium, color = GoldBrown)
        Text(if (isReset) "Reset password" else "Forgot password?", style = MaterialTheme.typography.displayLarge, color = TextBlack)
        if (isReset) {
            if (state.recoveryReady) {
                WeGlowPasswordField(password, { password = it }, placeholder = "New password", enabled = !state.isLoading)
                WeGlowPasswordField(confirmation, { confirmation = it }, placeholder = "Confirm password", enabled = !state.isLoading)
                Text("Use at least 8 characters, with a number.", style = MaterialTheme.typography.bodySmall, color = SoftGray)
                WeGlowPrimaryButton("Save New Password", onClick = {
                    onSavePassword(password, confirmation)
                }, loading = state.isLoading)
            } else if (state.isLoading) {
                WeGlowLoadingView("Checking reset link…")
            }
        } else {
            Text("Enter your email to request a password reset link.", style = MaterialTheme.typography.bodyMedium, color = TextBlack)
            WeGlowTextField(email, { email = it }, "Email", enabled = !state.isLoading)
            WeGlowPrimaryButton("Send Reset Link", onClick = { onSendLink(email) }, loading = state.isLoading, enabled = state.notice == null)
        }
        state.errorMessage?.let { WeGlowErrorView(it) }
        state.notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = DarkGreen) }
        TextButton(onClick = onBack, enabled = !state.isLoading) { Text("Back to login") }
    }
}
