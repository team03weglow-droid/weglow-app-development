package com.example.weglow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.weglow.ui.components.WeGlowErrorView
import com.example.weglow.ui.components.WeGlowPasswordField
import com.example.weglow.ui.components.WeGlowPrimaryButton
import com.example.weglow.ui.components.WeGlowTextField
import com.example.weglow.ui.theme.*

@Composable
fun SignUpScreen(
    onCreateAccount: (String, String, String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onTermsConditionsClick: () -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {},
) {
    // rememberSaveable (not remember): opening Terms & Conditions or the Privacy Policy
    // navigates away to a different destination, which disposes this screen's composition.
    // Only rememberSaveable state survives that and is restored when the user presses Back -
    // a plain remember would silently reset everything the user had already typed.
    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var agreed by rememberSaveable { mutableStateOf(false) }

    val formValid = fullName.isNotBlank() && email.isNotBlank() && password.length >= 8 && password.any(Char::isDigit) && agreed

    Column(
        modifier = Modifier.fillMaxSize().background(PageBackground).verticalScroll(rememberScrollState()).padding(horizontal = WeGlowSpacing.lg),
    ) {
        Spacer(Modifier.height(56.dp))
        Text("Join WEGLOW", style = MaterialTheme.typography.titleMedium, color = GoldBrown)
        Spacer(Modifier.height(WeGlowSpacing.xs))
        Text("Start your glow", style = MaterialTheme.typography.displayLarge, color = TextBlack)
        Spacer(Modifier.height(10.dp))
        Text("Create an account to build a routine tuned to your skin and hair", style = MaterialTheme.typography.bodyMedium, color = TextBlack)
        Spacer(Modifier.height(28.dp))

        WeGlowTextField(fullName, { fullName = it }, "Full name", enabled = !isLoading)
        Spacer(Modifier.height(14.dp))
        WeGlowTextField(email, { email = it }, "Email", enabled = !isLoading)
        Spacer(Modifier.height(14.dp))
        WeGlowPasswordField(password, { password = it }, enabled = !isLoading)
        Text("Use at least 8 characters, with a number.", style = MaterialTheme.typography.bodySmall, color = SoftGray, modifier = Modifier.padding(top = WeGlowSpacing.xs, start = WeGlowSpacing.xxs))

        Spacer(Modifier.height(WeGlowSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = agreed,
                onCheckedChange = { agreed = it },
                enabled = !isLoading,
                colors = CheckboxDefaults.colors(checkedColor = CheckboxGreen, uncheckedColor = CheckboxGreen),
            )
            // The checkbox alone still gates account creation (see formValid below) - the
            // links inside this text only open the documents, they never set `agreed`.
            Text(
                text = buildAnnotatedString {
                    append("I agree to WeGlow's ")
                    withLink(LinkAnnotation.Clickable("terms") { onTermsConditionsClick() }) {
                        withStyle(SpanStyle(color = DarkGreen, textDecoration = TextDecoration.Underline)) {
                            append("Terms & Conditions")
                        }
                    }
                    append(" and acknowledge the ")
                    withLink(LinkAnnotation.Clickable("privacy") { onPrivacyPolicyClick() }) {
                        withStyle(SpanStyle(color = DarkGreen, textDecoration = TextDecoration.Underline)) {
                            append("Privacy Policy")
                        }
                    }
                    append(".")
                },
                style = MaterialTheme.typography.bodySmall,
                color = GoldBrown,
            )
        }

        errorMessage?.let { WeGlowErrorView(it) }
        Spacer(Modifier.height(WeGlowSpacing.md))
        WeGlowPrimaryButton(
            text = "Create account",
            onClick = { onCreateAccount(fullName.trim(), email.trim(), password) },
            enabled = formValid,
            loading = isLoading,
        )
        Spacer(Modifier.height(WeGlowSpacing.xl))
    }
}
