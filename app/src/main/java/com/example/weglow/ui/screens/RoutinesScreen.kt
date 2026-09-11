package com.example.weglow.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weglow.R
import com.example.weglow.domain.model.RoutinePlan
import com.example.weglow.ui.components.ProfileAvatar
import com.example.weglow.ui.components.WeGlowErrorView
import com.example.weglow.ui.components.WeGlowLoadingView
import com.example.weglow.ui.components.WeGlowProductImage
import com.example.weglow.ui.components.rememberDecodedBitmap
import com.example.weglow.ui.theme.*

private data class DayEntry(
    val label: String,
    val dayNumber: Int
)

private val exampleWeek = listOf(
    DayEntry("MON", 12),
    DayEntry("TUE", 13),
    DayEntry("WED", 14),
    DayEntry("THU", 15),
    DayEntry("FRI", 16),
    DayEntry("SAT", 17),
    DayEntry("SUN", 18),
)

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
) {

    var isMorning by remember { mutableStateOf(true) }
    var selectedDay by remember { mutableStateOf(2) }

    var doneState by remember { mutableStateOf(setOf<String>()) }

    var selectedFeeling by remember { mutableStateOf<String?>(null) }
    var observations by remember { mutableStateOf("") }

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
                horizontal = 20.dp,
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

        // GREETING
        // Uses the real persisted profile name (via ProfileViewModel); falls back to neutral
        // copy rather than a placeholder name when none is available yet.
        val greetingName = displayName?.trim()?.takeIf(String::isNotEmpty)
        Text(
            when {
                isMorning && greetingName != null -> "Good Morning, $greetingName"
                isMorning -> "Good Morning"
                greetingName != null -> "Good Evening, $greetingName"
                else -> "Good Evening"
            },
            fontFamily = JungeFont,
            fontSize = 24.sp,
            color = PrimaryBlack
        )

        Text(
            if (isMorning) {
                "Your skin needs a little extra hydration today."
            } else {
                "Time to repair and recover overnight."
            },
            fontFamily = JungeFont,
            fontSize = 14.sp,
            color = PrimaryBlack,
            modifier = Modifier.padding(
                top = 2.dp,
                bottom = 18.dp
            )
        )

        // WEEK DAYS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            exampleWeek.forEachIndexed { index, day ->

                val selected = selectedDay == index

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        selectedDay = index
                    }
                ) {

                    Text(
                        day.label,
                        fontFamily = JungeFont,
                        fontSize = 11.sp,
                        fontWeight = if (selected) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (selected) {
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
                                if (selected) {
                                    DarkGreen
                                } else {
                                    CardWhite
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {

                        Text(
                            "${day.dayNumber}",
                            fontFamily = JungeFont,
                            fontSize = 15.sp,
                            color = if (selected) {
                                Color.White
                            } else {
                                PrimaryBlack
                            }
                        )
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

        Spacer(Modifier.height(20.dp))

        Text(
            "Step-by-Step",
            fontFamily = JungeFont,
            fontSize = 16.sp,
            color = PrimaryBlack
        )

        Spacer(Modifier.height(10.dp))

        // ROUTINE STEPS
        when {
            isLoading -> WeGlowLoadingView(message = "Building your routine…")
            errorMessage != null -> WeGlowErrorView(message = errorMessage, onRetry = onRetry)
            activeSteps.isEmpty() -> Text(
                "No routine steps are available yet.",
                fontFamily = JungeFont,
                fontSize = 13.sp,
                color = SoftGray,
            )
            else -> activeSteps.forEachIndexed { index, step ->
                val stepKey = "${if (isMorning) "AM" else "PM"}-$index-${step.product?.id}"
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
                                fontFamily = JungeFont,
                                fontSize = 11.sp,
                                color = DarkGreen.copy(alpha = 0.65f)
                            )

                            if (product != null) {
                                Text(
                                    product.name,
                                    fontFamily = JungeFont,
                                    fontSize = 14.sp,
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
                                    fontFamily = JungeFont,
                                    fontSize = 12.sp,
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
                                    fontFamily = JungeFont,
                                    fontSize = 12.sp,
                                    color = SoftGray,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        // CHECK BUTTON
                        if (product != null) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (done) {
                                            DoneGreen
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .then(
                                        if (!done) {
                                            Modifier.border(
                                                1.5.dp,
                                                SoftGray,
                                                CircleShape
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable {
                                        doneState = if (done) doneState - stepKey else doneState + stepKey
                                    },
                                contentAlignment = Alignment.Center
                            ) {

                                if (done) {

                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Mark step undone",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
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
                fontFamily = JungeFont,
                fontSize = 15.sp,
                color = PrimaryBlack
            )

            Text(
                "How does your skin feel today?",
                fontFamily = JungeFont,
                fontSize = 12.sp,
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
                fontFamily = JungeFont,
                fontSize = 13.sp,
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
                        fontFamily = JungeFont,
                        fontSize = 12.sp
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PageBackground,
                    unfocusedContainerColor = PageBackground,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            )

            Spacer(Modifier.height(12.dp))

            // SAVE NOTE BUTTON
            Button(
                onClick = { },
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
                    fontFamily = JungeFont,
                    fontSize = 15.sp
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
        fontFamily = JungeFont,
        fontSize = 12.sp,
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
        fontFamily = JungeFont,
        fontSize = 14.sp,
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