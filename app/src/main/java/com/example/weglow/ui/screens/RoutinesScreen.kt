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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weglow.R
import com.example.weglow.ui.theme.*


private data class RoutineStepData(
    val id: Int,
    val category: String,
    val badge: String?,
    val name: String,
    val detail: String,
    val imageRes: Int?,
    val essential: Boolean = false,
    val initialDone: Boolean = false
)

private val morningSteps = listOf(
    RoutineStepData(
        id = 101,
        category = "CLEANSE",
        badge = "PURIFYING",
        name = "Purifying Neem Face Wash",
        detail = "Himalaya",
        imageRes = R.drawable.himalaya_neem_face_wash,
        initialDone = true
    ),
    RoutineStepData(
        id = 102,
        category = "TONE",
        badge = "1 MIN",
        name = "Balancing Rose Water Toner",
        detail = "Botanical Glow Essentials",
        imageRes = R.drawable.radiance_serum,
        essential = true
    ),
    RoutineStepData(
        id = 103,
        category = "MOISTURIZE",
        badge = "HYDRATING",
        name = "Clear Complexion Day Cream",
        detail = "Himalaya",
        imageRes = R.drawable.aloe_95_soothing_gel
    ),
)

private val eveningSteps = listOf(
    RoutineStepData(
        id = 201,
        category = "DOUBLE CLEANSE",
        badge = null,
        name = "Green Tea and Matcha Cleansing Foam",
        detail = "2 mins",
        imageRes = R.drawable.matcha_cleansing_foam,
        initialDone = true
    ),
    RoutineStepData(
        id = 202,
        category = "TONE",
        badge = null,
        name = "Organic Cucumber and Mint Toner",
        detail = "1 min",
        imageRes = R.drawable.botanica,
        initialDone = true
    ),
    RoutineStepData(
        id = 203,
        category = "TREATMENT",
        badge = null,
        name = "Rosehip and Argan Hair Serum",
        detail = "Apply to ends",
        imageRes = R.drawable.aurora,
        initialDone = true
    ),
    RoutineStepData(
        id = 204,
        category = "REPAIR",
        badge = null,
        name = "Chamomile Soothing Night Cream",
        detail = "Massage in",
        imageRes = R.drawable.chamomile_night_cream,
        initialDone = true
    ),
    RoutineStepData(
        id = 205,
        category = "SLEEP SUPPORT",
        badge = null,
        name = "Lavender & Ashwagandha Mist",
        detail = "Mist face & pillow",
        imageRes = R.drawable.lavender_sleeping_mist,
        essential = true
    ),
)

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
fun RoutinesScreen() {

    var isMorning by remember { mutableStateOf(true) }
    var selectedDay by remember { mutableStateOf(2) }

    var doneState by remember {
        mutableStateOf(
            (morningSteps + eveningSteps)
                .associate { it.id to it.initialDone }
        )
    }

    var selectedFeeling by remember { mutableStateOf<String?>(null) }
    var observations by remember { mutableStateOf("") }

    val activeSteps = if (isMorning) {
        morningSteps
    } else {
        eveningSteps
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

            Image(
                painter = painterResource(R.drawable.avatar_rukman),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            )
        }

        Spacer(Modifier.height(14.dp))

        // GREETING
        Text(
            if (isMorning) {
                "Good Morning, Team 03"
            } else {
                "Good Evening, Team 03"
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
        activeSteps.forEachIndexed { index, step ->

            val done = doneState[step.id] == true
            val highlighted = step.essential && !done

            Box(
                modifier = Modifier.padding(bottom = 16.dp)
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardWhite)
                        .then(
                            if (highlighted) {
                                Modifier.border(
                                    2.dp,
                                    DarkGreen,
                                    RoundedCornerShape(16.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // PRODUCT IMAGE
                    if (step.imageRes != null) {

                        Image(
                            painter = painterResource(
                                id = step.imageRes
                            ),
                            contentDescription = step.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(
                                    RoundedCornerShape(12.dp)
                                )
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

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                "STEP ${index + 1} • ${step.category}",
                                fontFamily = JungeFont,
                                fontSize = 11.sp,
                                color = DarkGreen.copy(alpha = 0.65f)
                            )

                            if (step.badge != null) {

                                Spacer(Modifier.width(6.dp))

                                Text(
                                    step.badge,
                                    fontFamily = JungeFont,
                                    fontSize = 9.sp,
                                    color = DarkGreen,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(MintChip)
                                        .padding(
                                            horizontal = 6.dp,
                                            vertical = 2.dp
                                        )
                                )
                            }
                        }

                        Text(
                            step.name,
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
                            step.detail,
                            fontFamily = JungeFont,
                            fontSize = 12.sp,
                            color = SoftGray,
                            textDecoration = if (done) {
                                TextDecoration.LineThrough
                            } else {
                                TextDecoration.None
                            }
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // CHECK BUTTON
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

                                doneState = doneState
                                    .toMutableMap()
                                    .also {
                                        it[step.id] = !done
                                    }
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

                // ESSENTIAL BADGE
                if (highlighted) {

                    Text(
                        "Essential",
                        fontFamily = JungeFont,
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(
                                y = (-8).dp,
                                x = (-8).dp
                            )
                            .clip(RoundedCornerShape(50))
                            .background(CoralAccent)
                            .padding(
                                horizontal = 8.dp,
                                vertical = 3.dp
                            )
                    )
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