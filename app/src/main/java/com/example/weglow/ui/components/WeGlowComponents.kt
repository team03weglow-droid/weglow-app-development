package com.example.weglow.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.weglow.ui.theme.*

@Composable
fun WeGlowPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        colors = ButtonDefaults.buttonColors(containerColor = ButtonGreen, contentColor = Color.White),
        shape = RoundedCornerShape(WeGlowRadius.pill),
        modifier = modifier.fillMaxWidth().height(WeGlowSize.buttonHeight),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun WeGlowSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(WeGlowRadius.pill),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ButtonGreen),
        modifier = modifier.fillMaxWidth().height(WeGlowSize.buttonHeight),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun WeGlowTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: (@Composable (() -> Unit))? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = SoftGray) },
        singleLine = true,
        shape = RoundedCornerShape(WeGlowRadius.medium),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = FieldWhite,
            focusedContainerColor = FieldWhite,
            disabledContainerColor = FieldWhite,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = ButtonGreen.copy(alpha = 0.45f),
            disabledBorderColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth().height(WeGlowSize.fieldHeight),
    )
}

@Composable
fun WeGlowPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Password",
    enabled: Boolean = true,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    WeGlowTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                    tint = SoftGray,
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
fun WeGlowCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(WeGlowRadius.large),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = WeGlowElevation.card),
    ) { Column(modifier = Modifier.padding(WeGlowSpacing.md), content = content) }
}

@Composable
fun WeGlowLoadingView(message: String = "Loading…", modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(WeGlowSpacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = ButtonGreen)
        Spacer(Modifier.height(WeGlowSpacing.sm))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = SoftGray)
    }
}

@Composable
fun WeGlowErrorView(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier = modifier.fillMaxWidth().padding(WeGlowSpacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(WeGlowSpacing.sm))
            WeGlowSecondaryButton("Try again", onRetry)
        }
    }
}

@Composable
fun WeGlowProgressIndicator(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth(),
        color = CoralAccent,
        trackColor = TrackGray,
    )
}

@Composable
fun WeGlowEmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(WeGlowSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextBlack, textAlign = TextAlign.Center)
        Spacer(Modifier.height(WeGlowSpacing.xs))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = SoftGray, textAlign = TextAlign.Center)
    }
}
