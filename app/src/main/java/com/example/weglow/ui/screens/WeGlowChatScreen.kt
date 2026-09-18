package com.example.weglow.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.weglow.R
import com.example.weglow.chatbot.VoiceAgentManager
import com.example.weglow.feature.chat.ChatMessage
import com.example.weglow.feature.chat.ChatViewModel
import com.example.weglow.ui.theme.CardWhite
import com.example.weglow.ui.theme.DarkGreen
import com.example.weglow.ui.theme.PageBackground
import com.example.weglow.ui.theme.SoftGray
import com.example.weglow.ui.theme.WeGlowRadius
import com.example.weglow.ui.theme.WeGlowSpacing
import kotlinx.coroutines.delay

@Composable
fun WeGlowChatScreen(
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
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
            onSpeechResult = { spokenText -> messageText = spokenText },
            onListeningStateChanged = { listening -> isListening = listening },
            onRecognitionError = { message -> voiceError = message },
        )
    }

    DisposableEffect(Unit) {
        onDispose { voiceAgentManager.destroy() }
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

    val scrollTarget = if (chatState.isSending) chatState.messages.size else chatState.messages.lastIndex
    LaunchedEffect(scrollTarget, chatState.messages.size) {
        if (scrollTarget >= 0) listState.animateScrollToItem(scrollTarget)
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            voiceAgentManager.startListening()
        } else {
            voiceError = "Microphone permission is required for voice input."
        }
    }

    fun startVoiceInput() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            voiceAgentManager.startListening()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
            .background(PageBackground)
            .imePadding(),
    ) {
        ChatHeader(onBack = onBack)

        voiceError?.let { error -> ChatErrorBanner(text = error) }
        chatState.errorMessage?.let { error -> ChatErrorBanner(text = error) }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(WeGlowSpacing.sm),
            contentPadding = PaddingValues(
                start = WeGlowSpacing.lg,
                top = WeGlowSpacing.lg,
                end = WeGlowSpacing.lg,
                bottom = WeGlowSpacing.md,
            ),
        ) {
            items(chatState.messages) { message ->
                ChatBubble(message = message)
            }

            if (chatState.isSending) {
                item { TypingIndicator() }
            }

            if (chatState.messages.none { it.isUser }) {
                item {
                    SuggestedQuestions(onQuestionClick = { question -> messageText = question })
                }
            }
        }

        ChatInput(
            value = messageText,
            onValueChange = { messageText = it },
            onSend = { sendMessage() },
            onMic = { startVoiceInput() },
            isListening = isListening,
        )
    }
}

@Composable
private fun ChatHeader(
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = WeGlowSpacing.sm,
                top = WeGlowSpacing.xs,
                end = WeGlowSpacing.lg,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = DarkGreen,
            )
        }

        Image(
            painter = painterResource(R.drawable.weglow_logo),
            contentDescription = "WeGlow",
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun ChatErrorBanner(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = WeGlowSpacing.lg, top = WeGlowSpacing.sm, end = WeGlowSpacing.lg),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(WeGlowRadius.medium),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = WeGlowSpacing.md, vertical = WeGlowSpacing.sm),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!message.isUser) {
            AssistantBadge()
            Spacer(Modifier.width(WeGlowSpacing.xs))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(if (message.isUser) 0.80f else 0.84f),
            shape = RoundedCornerShape(
                topStart = WeGlowRadius.medium,
                topEnd = WeGlowRadius.medium,
                bottomStart = if (message.isUser) WeGlowRadius.medium else WeGlowSpacing.xxs,
                bottomEnd = if (message.isUser) WeGlowSpacing.xxs else WeGlowRadius.medium,
            ),
            color = if (message.isUser) DarkGreen else CardWhite,
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = WeGlowSpacing.md, vertical = WeGlowSpacing.sm),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AssistantBadge() {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(CardWhite),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            tint = DarkGreen,
            modifier = Modifier.size(16.dp),
        )
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
        verticalAlignment = Alignment.Bottom,
    ) {
        AssistantBadge()
        Spacer(Modifier.width(WeGlowSpacing.xs))
        Surface(
            shape = RoundedCornerShape(
                topStart = WeGlowRadius.medium,
                topEnd = WeGlowRadius.medium,
                bottomStart = WeGlowSpacing.xxs,
                bottomEnd = WeGlowRadius.medium,
            ),
            color = CardWhite,
        ) {
            Text(
                text = "•".repeat(dotCount),
                modifier = Modifier.padding(horizontal = WeGlowSpacing.md, vertical = WeGlowSpacing.sm),
                style = MaterialTheme.typography.bodyMedium,
                color = SoftGray,
            )
        }
    }
}

@Composable
private fun SuggestedQuestions(onQuestionClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WeGlowSpacing.sm),
    ) {
        Text(
            text = "QUICK QUESTIONS",
            style = MaterialTheme.typography.labelSmall,
            color = SoftGray,
        )
        Spacer(Modifier.height(WeGlowSpacing.xs))

        listOf(
            "What did my latest skin analysis say?",
            "What is my skincare routine?",
            "What products are suitable for me?",
        ).forEachIndexed { index, question ->
            SuggestionChip(text = question, onClick = { onQuestionClick(question) })
            if (index < 2) Spacer(Modifier.height(WeGlowSpacing.xs))
        }
    }
}

@Composable
private fun SuggestionChip(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WeGlowRadius.medium))
            .border(1.dp, DarkGreen.copy(alpha = 0.14f), RoundedCornerShape(WeGlowRadius.medium)),
        color = CardWhite,
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = WeGlowSpacing.md, vertical = WeGlowSpacing.sm),
        ) {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = DarkGreen,
                textAlign = TextAlign.Start,
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
    isListening: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CardWhite,
        shape = RoundedCornerShape(
            topStart = WeGlowRadius.large,
            topEnd = WeGlowRadius.large,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WeGlowSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "Ask WeGlow AI...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftGray,
                    )
                },
                trailingIcon = {
                    IconButton(onClick = onMic) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = if (isListening) {
                                "Listening for voice input"
                            } else {
                                "Voice input"
                            },
                            tint = if (isListening) MaterialTheme.colorScheme.secondary else DarkGreen,
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(WeGlowRadius.pill),
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PageBackground,
                    unfocusedContainerColor = PageBackground,
                    focusedBorderColor = DarkGreen,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = DarkGreen,
                ),
            )

            Spacer(Modifier.width(WeGlowSpacing.xs))

            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (value.isNotBlank()) DarkGreen else PageBackground),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message",
                    tint = if (value.isNotBlank()) Color.White else SoftGray,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}
