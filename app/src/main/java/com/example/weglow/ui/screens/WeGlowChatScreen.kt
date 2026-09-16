package com.example.weglow.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.example.weglow.chatbot.VoiceAgentManager
import com.example.weglow.feature.chat.ChatMessage
import com.example.weglow.feature.chat.ChatViewModel
import com.example.weglow.ui.theme.WeGlowRadius
import com.example.weglow.ui.theme.WeGlowSize
import com.example.weglow.ui.theme.WeGlowSpacing
import kotlinx.coroutines.delay

@Composable
fun WeGlowChatScreen(
    chatViewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val chatState by chatViewModel.uiState.collectAsState()

    var messageText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }

    val voiceAgentManager = remember {
        VoiceAgentManager(
            context = context,
            onSpeechResult = { spokenText ->
                messageText = spokenText
            },
            onListeningStateChanged = { listening ->
                isListening = listening
            },
            onRecognitionError = { message ->
                voiceError = message
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceAgentManager.destroy()
        }
    }

    LaunchedEffect(voiceError) {
        if (voiceError != null) {
            delay(3000)
            voiceError = null
        }
    }

    LaunchedEffect(chatState.errorMessage) {
        if (chatState.errorMessage != null) {
            delay(4000)
            chatViewModel.consumeError()
        }
    }

    val scrollTarget =
        if (chatState.isSending) {
            chatState.messages.size
        } else {
            chatState.messages.lastIndex
        }

    LaunchedEffect(scrollTarget, chatState.messages.size) {
        if (scrollTarget >= 0) {
            listState.animateScrollToItem(scrollTarget)
        }
    }

    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                voiceAgentManager.startListening()
            } else {
                voiceError = "Microphone permission is required for voice input."
            }
        }

    fun startVoiceInput() {
        val permissionGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            voiceAgentManager.startListening()
        } else {
            microphonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    fun sendMessage() {
        val text = messageText.trim()

        if (text.isEmpty()) return

        chatViewModel.sendMessage(text)

        messageText = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = WeGlowSpacing.sm,
                    vertical = WeGlowSpacing.xs
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            IconButton(
                onClick = onBack,
                modifier = Modifier.size(WeGlowSize.iconLarge)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(
                modifier = Modifier.size(WeGlowSpacing.xs)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "WeGlow AI",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = if (isListening) {
                        "Listening..."
                    } else {
                        "Your beauty & skincare assistant"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isListening) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        if (voiceError != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WeGlowSpacing.md),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(WeGlowRadius.medium)
            ) {
                Text(
                    text = voiceError ?: "",
                    modifier = Modifier.padding(
                        horizontal = WeGlowSpacing.md,
                        vertical = WeGlowSpacing.sm
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Spacer(
                modifier = Modifier.height(WeGlowSpacing.xs)
            )
        }

        chatState.errorMessage?.let { errorText ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WeGlowSpacing.md),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(WeGlowRadius.medium)
            ) {
                Text(
                    text = errorText,
                    modifier = Modifier.padding(
                        horizontal = WeGlowSpacing.md,
                        vertical = WeGlowSpacing.sm
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Spacer(
                modifier = Modifier.height(WeGlowSpacing.xs)
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = WeGlowSpacing.md),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(
                WeGlowSpacing.sm
            ),
            contentPadding = PaddingValues(
                top = WeGlowSpacing.md,
                bottom = WeGlowSpacing.md
            )
        ) {

            items(chatState.messages) { message ->
                ChatBubble(message = message)
            }

            if (chatState.isSending) {
                item {
                    TypingIndicator()
                }
            }

            item {
                SuggestedQuestions(
                    onQuestionClick = { question ->
                        messageText = question
                    }
                )
            }
        }

        ChatInput(
            value = messageText,
            onValueChange = { messageText = it },
            onSend = { sendMessage() },
            onMic = { startVoiceInput() },
            isListening = isListening
        )
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {

        Surface(
            modifier = Modifier.fillMaxWidth(
                fraction = if (message.isUser) 0.82f else 0.88f
            ),
            shape = RoundedCornerShape(
                topStart = WeGlowRadius.medium,
                topEnd = WeGlowRadius.medium,
                bottomStart = if (message.isUser) {
                    WeGlowRadius.medium
                } else {
                    WeGlowRadius.small
                },
                bottomEnd = if (message.isUser) {
                    WeGlowRadius.small
                } else {
                    WeGlowRadius.medium
                }
            ),
            color = if (message.isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            tonalElevation = WeGlowSpacing.xxs
        ) {

            Text(
                text = message.text,
                modifier = Modifier.padding(
                    horizontal = WeGlowSpacing.md,
                    vertical = WeGlowSpacing.sm
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    var dotCount by remember { mutableStateOf(1) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dotCount = if (dotCount >= 3) 1 else dotCount + 1
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {

        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = RoundedCornerShape(
                topStart = WeGlowRadius.medium,
                topEnd = WeGlowRadius.medium,
                bottomStart = WeGlowRadius.small,
                bottomEnd = WeGlowRadius.medium
            ),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = WeGlowSpacing.xxs
        ) {

            Text(
                text = ".".repeat(dotCount),
                modifier = Modifier.padding(
                    horizontal = WeGlowSpacing.md,
                    vertical = WeGlowSpacing.sm
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SuggestedQuestions(
    onQuestionClick: (String) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WeGlowSpacing.sm),
        horizontalAlignment = Alignment.Start
    ) {

        Text(
            text = "You can ask me...",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(
            modifier = Modifier.height(WeGlowSpacing.xs)
        )

        SuggestionChip(
            text = "What did my latest skin analysis say?",
            onClick = {
                onQuestionClick(
                    "What did my latest skin analysis say?"
                )
            }
        )

        Spacer(
            modifier = Modifier.height(WeGlowSpacing.xs)
        )

        SuggestionChip(
            text = "What is my skincare routine?",
            onClick = {
                onQuestionClick(
                    "What is my skincare routine?"
                )
            }
        )

        Spacer(
            modifier = Modifier.height(WeGlowSpacing.xs)
        )

        SuggestionChip(
            text = "What products are suitable for me?",
            onClick = {
                onQuestionClick(
                    "What products are suitable for me?"
                )
            }
        )
    }
}

@Composable
private fun SuggestionChip(
    text: String,
    onClick: () -> Unit
) {

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(WeGlowRadius.pill)
            ),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {

        TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    isListening: Boolean
) {

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = WeGlowSpacing.xxs
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = WeGlowSpacing.sm,
                    vertical = WeGlowSpacing.xs
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "Ask WeGlow AI...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(
                    WeGlowRadius.pill
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            IconButton(
                onClick = onMic,
                modifier = Modifier.size(
                    WeGlowSize.iconLarge
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = if (isListening) {
                        "Listening for voice input"
                    } else {
                        "Voice input"
                    },
                    tint = if (isListening) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }

            IconButton(
                onClick = onSend,
                modifier = Modifier.size(
                    WeGlowSize.iconLarge
                ),
                enabled = value.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send message",
                    tint = if (value.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}