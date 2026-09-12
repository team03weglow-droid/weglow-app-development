package com.example.weglow.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weglow.R
import com.example.weglow.domain.model.RoutinePlan
import com.example.weglow.feature.routine.calendarDays
import com.example.weglow.feature.routine.routineStepKey
import com.example.weglow.feature.routine.RoutineJournalState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import java.time.format.DateTimeFormatter
import com.example.weglow.ui.components.ProfileAvatar
import com.example.weglow.ui.components.WeGlowErrorView
import com.example.weglow.ui.components.WeGlowLoadingView
import com.example.weglow.ui.components.WeGlowProductImage
import com.example.weglow.ui.components.rememberDecodedBitmap
import com.example.weglow.ui.theme.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

private val feelingChips = listOf(
    "Dry",
    "Hydrated",
    "Oily",
    "Breakout",
    "Sensitive"
)

@Composable
fun RoutinesScreen(
    isLoading: Boolean,
    errorMessage: String?,
    plan: RoutinePlan?,
    onRetry: () -> Unit,
    displayName: String? = null,
    profileImage: ByteArray? = null,
    journal: RoutineJournalState = RoutineJournalState(),
    onToggleRoutineStep: (String) -> Unit = {},
    onSaveNote: (String, String?, String) -> Unit = { _, _, _ -> },
    initialMorning: Boolean? = null,
) {

    var isMorning by remember(initialMorning) { mutableStateOf(initialMorning ?: (LocalTime.now().hour < 12)) }
    val initialToday = remember { LocalDate.now() }
    var today by remember { mutableStateOf(initialToday) }
    var selectedDate by remember { mutableStateOf(initialToday) }
    var calendarAnchor by remember { mutableStateOf(initialToday) }

    fun refreshDeviceDateAndPeriod() {
        val deviceToday = LocalDate.now()
        if (deviceToday != today) {
            isMorning = LocalTime.now().hour < 12
            val selectionWasToday = selectedDate == today
            today = deviceToday
            if (selectionWasToday) {
                selectedDate = deviceToday
                calendarAnchor = deviceToday
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, today, selectedDate) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshDeviceDateAndPeriod()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Polling also catches midnight and device date/timezone changes while this screen stays open.
    LaunchedEffect(today, selectedDate) {
        while (true) {
            delay(30_000L)
            refreshDeviceDateAndPeriod()
        }
    }

    val locale = LocalConfiguration.current.locales[0]

    val doneState = journal.completedKeys
    val noteKey = selectedDate.toString()
    val savedNote = journal.notes[noteKey]
    var selectedFeeling by remember(noteKey, savedNote) { mutableStateOf(savedNote?.feeling) }
    var observations by remember(noteKey, savedNote) { mutableStateOf(savedNote?.observations.orEmpty()) }

    val activeSteps = if (isMorning) {
        plan?.morning.orEmpty()
    } else {
        plan?.evening.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = 24.dp,
                vertical = 20.dp
            )
    ) {

        // TOP LOGO AND AVATAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Image(
                painter = painterResource(R.drawable.weglow_logo),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )

            ProfileAvatar(image = rememberDecodedBitmap(profileImage), size = 36.dp)
        }

        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(calendarAnchor.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                style = MaterialTheme.typography.titleMedium, color = DarkGreen, modifier = Modifier.weight(1f))
            TextButton(onClick = { selectedDate = today; calendarAnchor = today }) { Text("Today") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { calendarAnchor = calendarAnchor.minusWeeks(1) }) { Text("Previous week") }
            TextButton(onClick = { calendarAnchor = calendarAnchor.plusWeeks(1) }) { Text("Next week") }
        }
        journal.errorMessage?.let { WeGlowErrorView(it) }

        AnimatedContent(
            targetState = calendarAnchor,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally(
                        animationSpec = tween(400),
                        initialOffsetX = { fullWidth -> fullWidth },
                    ) togetherWith slideOutHorizontally(
                        animationSpec = tween(400),
                        targetOffsetX = { fullWidth -> -fullWidth },
                    )
                } else {
                    slideInHorizontally(
                        animationSpec = tween(400),
                        initialOffsetX = { fullWidth -> -fullWidth },
                    ) togetherWith slideOutHorizontally(
                        animationSpec = tween(400),
                        targetOffsetX = { fullWidth -> fullWidth },
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth(),
            label = "Calendar week",
        ) { weekAnchor ->
            val visibleDays = calendarDays(weekAnchor, selectedDate, today, locale)

            Row(
                modifier = Modifier
                .fillMaxWidth()
                .pointerInput(weekAnchor) {
                    var horizontalDrag = 0f
                    val swipeThreshold = 48.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { horizontalDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            horizontalDrag += dragAmount
                        },
                        onDragEnd = {
                            if (kotlin.math.abs(horizontalDrag) >= swipeThreshold) {
                                val dayOffset = if (horizontalDrag < 0f) 7L else -7L
                                val newAnchor = weekAnchor.plusDays(dayOffset)
                                calendarAnchor = newAnchor
                                selectedDate = newAnchor
                            }
                        },
                    )
                    },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

            visibleDays.forEach { day ->
                key(day.date.toString()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            selectedDate = day.date
                        }
                    ) {

                    Text(
                        day.dayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (day.isSelected || day.isToday) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (day.isSelected || day.isToday) {
                            PrimaryBlack
                        } else {
                            SoftGray
                        }
                    )

                    Spacer(Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    day.isSelected -> DarkGreen
                                    day.isToday -> MintChip
                                    else -> CardWhite
                                }
                            )
                            .then(
                                if (day.isToday && !day.isSelected) {
                                    Modifier.border(2.dp, DarkGreen, CircleShape)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {

                        Text(
                            day.dayNumber,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (day.isSelected) {
                                Color.White
                            } else {
                                PrimaryBlack
                            }
                        )
                    }
                }
                }
            }
        }
        }

        Spacer(Modifier.height(16.dp))

        // MORNING / EVENING TOGGLE
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(CardWhite)
                .padding(4.dp)
        ) {

            ToggleHalf(
                label = "Morning",
                selected = isMorning
            ) {
                isMorning = true
            }

            ToggleHalf(
                label = "Evening",
                selected = !isMorning
            ) {
                isMorning = false
            }
        }

        Text(
            if (isMorning) {
                "A simple routine to start your day."
            } else {
                "Time to repair and recover overnight."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = PrimaryBlack,
            modifier = Modifier.padding(
                top = 2.dp,
                bottom = 18.dp
            )
        )

        Text(
            "Step-by-Step",
            style = MaterialTheme.typography.bodyLarge,
            color = PrimaryBlack
        )

        Spacer(Modifier.height(10.dp))

        // ROUTINE STEPS
        when {
            isLoading -> WeGlowLoadingView(message = "Building your routine…")
            errorMessage != null -> WeGlowErrorView(message = errorMessage, onRetry = onRetry)
            activeSteps.isEmpty() -> Text(
                "No routine steps are available yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftGray,
            )
            else -> activeSteps.forEachIndexed { index, step ->
                // Keyed by the selected date so completion never leaks between dates (each date
                // gets its own independent checkmarks for this session) - see routineStepKey.
                val stepKey = routineStepKey(selectedDate, isMorning, index, step.product?.id)
                val done = stepKey in doneState
                val product = step.product

                Box(
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardWhite)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        // PRODUCT IMAGE
                        // A missing product for this step (no match found) is a different
                        // state from a matched product whose image failed to load - only the
                        // former gets an empty placeholder box. A matched product always goes
                        // through WeGlowProductImage so a null/blank/malformed image_url or a
                        // failed load falls back to the WeGlow logo instead of a blank square.
                        if (product != null) {

                            WeGlowProductImage(
                                imageUrl = product.imageUrl,
                                contentDescription = product.name,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )

                        } else {

                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(
                                        RoundedCornerShape(12.dp)
                                    )
                                    .background(PageBackground)
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {

                            Text(
                                "STEP ${index + 1} • ${step.label.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftGray
                            )

                            if (product != null) {
                                Text(
                                    product.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PrimaryBlack,
                                    textDecoration = if (done) {
                                        TextDecoration.LineThrough
                                    } else {
                                        TextDecoration.None
                                    },
                                    modifier = Modifier.padding(top = 3.dp)
                                )

                                Text(
                                    listOfNotNull(product.brandName, product.priceLabel).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SoftGray,
                                    textDecoration = if (done) {
                                        TextDecoration.LineThrough
                                    } else {
                                        TextDecoration.None
                                    }
                                )
                            } else {
                                Text(
                                    "No suitable product found for this step yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SoftGray,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        // CHECK BUTTON
                        if (product != null) {
                            Checkbox(
                                checked = done,
                                onCheckedChange = { onToggleRoutineStep(stepKey) },
                                modifier = Modifier.semantics { contentDescription = "Complete ${product.name}" },
                                colors = CheckboxDefaults.colors(checkedColor = DarkGreen),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // DAILY SKIN NOTE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardWhite)
                .padding(16.dp)
        ) {

            Text(
                "Daily Skin Note",
                style = MaterialTheme.typography.bodyLarge,
                color = PrimaryBlack
            )

            Text(
                "How does your skin feel today?",
                style = MaterialTheme.typography.bodySmall,
                color = SoftGray,
                modifier = Modifier.padding(
                    top = 2.dp,
                    bottom = 10.dp
                )
            )

            // FIRST ROW OF FEELING CHIPS
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                feelingChips
                    .take(3)
                    .forEach { feeling ->

                        FeelingChip(
                            feeling,
                            selectedFeeling == feeling
                        ) {
                            selectedFeeling = feeling
                        }
                    }
            }

            Spacer(Modifier.height(8.dp))

            // SECOND ROW OF FEELING CHIPS
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                feelingChips
                    .drop(3)
                    .forEach { feeling ->

                        FeelingChip(
                            feeling,
                            selectedFeeling == feeling
                        ) {
                            selectedFeeling = feeling
                        }
                    }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Observations",
                style = MaterialTheme.typography.bodyMedium,
                color = PrimaryBlack
            )

            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = observations,
                onValueChange = {
                    observations = it
                },
                placeholder = {

                    Text(
                        "Noticed anything different today? E.g., slight redness around nose after cleansing...",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PageBackground,
                    unfocusedContainerColor = PageBackground,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 112.dp)
            )

            Spacer(Modifier.height(12.dp))

            Text("Notes stay on this device. No cloud sync.",
                style = MaterialTheme.typography.bodySmall, color = SoftGray)
            if (journal.lastSavedNoteKey == noteKey && savedNote != null && savedNote.feeling == selectedFeeling && savedNote.observations == observations.trim()) {
                Text("Note saved", style = MaterialTheme.typography.labelMedium, color = DarkGreen)
            }
            // SAVE NOTE BUTTON
            Button(
                onClick = { onSaveNote(noteKey, selectedFeeling, observations) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {

                Text(
                    "Save Note",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FeelingChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        color = if (selected) {
            DarkGreen
        } else {
            PrimaryBlack
        },
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    MintChip
                } else {
                    CardWhite
                }
            )
            .then(
                if (selected) {
                    Modifier.border(
                        1.dp,
                        DarkGreen,
                        RoundedCornerShape(50)
                    )
                } else {
                    Modifier.border(
                        1.dp,
                        PageBackground,
                        RoundedCornerShape(50)
                    )
                }
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = 12.dp,
                vertical = 8.dp
            )
    )
}

@Composable
private fun RowScope.ToggleHalf(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) {
            Color.White
        } else {
            PrimaryBlack
        },
        textAlign = TextAlign.Center,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    DarkGreen
                } else {
                    Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    )
}
