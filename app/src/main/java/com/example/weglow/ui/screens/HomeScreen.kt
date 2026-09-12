package com.example.weglow.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.example.weglow.R
import com.example.weglow.domain.model.RoutineStep
import com.example.weglow.feature.routine.routineStepKey
import com.example.weglow.ui.components.ProfileAvatar
import com.example.weglow.ui.components.WeGlowPlannedFeature
import com.example.weglow.ui.components.WeGlowProductImage
import com.example.weglow.ui.components.rememberDecodedBitmap
import com.example.weglow.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Daily actions first; unconnected Figma integrations remain explicit preview states.
 * All completion data comes from the same account-scoped journal as Routines.
 */
@Composable
fun HomeScreen(
    onScanClick: () -> Unit,
    onDiscoverClick: () -> Unit,
    onRecommendationsClick: () -> Unit,
    onRoutinesClick: () -> Unit = {},
    displayName: String? = null,
    profileImage: ByteArray? = null,
    morningRoutine: List<RoutineStep> = emptyList(),
    completedRoutineKeys: Set<String> = emptySet(),
    onToggleRoutineStep: (String) -> Unit = {},
    eveningRoutine: List<RoutineStep> = emptyList(),
    isRoutineLoading: Boolean = false,
    routineError: String? = null,
    onProfileClick: () -> Unit = {},
    onRoutinesPeriodClick: ((Boolean) -> Unit)? = null,
) {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDate.now()
            delay(30_000)
        }
    }
    var isMorning by rememberSaveable { mutableStateOf(true) }
    var showNotifications by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val activeSteps = if (isMorning) morningRoutine else eveningRoutine
    val openRoutine = { onRoutinesPeriodClick?.invoke(isMorning) ?: onRoutinesClick() }

    if (showNotifications) {
        AlertDialog(
            onDismissRequest = { showNotifications = false },
            title = { Text("Notifications") },
            text = { Text("Reminders and alerts are coming soon. No notifications are scheduled yet.") },
            confirmButton = { TextButton(onClick = { showNotifications = false }) { Text("Got it") } },
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PageBackground).testTag("home"),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "greeting") {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.weglow_logo), "WeGlow", Modifier.size(28.dp))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showNotifications = true }) { Text("Alerts", color = SoftGray) }
                    IconButton(onClick = onProfileClick, modifier = Modifier.semantics { contentDescription = "Open profile" }) {
                        ProfileAvatar(image = rememberDecodedBitmap(profileImage), size = 36.dp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())),
                    style = MaterialTheme.typography.labelMedium, color = SoftGray)
                Spacer(Modifier.height(4.dp))
                Text(displayName?.let { "Hello, $it" } ?: "Hello there",
                    style = MaterialTheme.typography.headlineLarge, color = DarkGreen)
                Text("A little care, every day.", style = MaterialTheme.typography.bodyMedium, color = SoftGray)
                Text("Skin sync · Coming soon", style = MaterialTheme.typography.bodySmall, color = SoftGray)
            }
        }
        item(key = "scan") { HomeScanHero(onScanClick) }
        item(key = "routine") {
            HomeRoutine(
                activeSteps, today, isMorning, { isMorning = it }, completedRoutineKeys,
                onToggleRoutineStep, isRoutineLoading, routineError, openRoutine,
            )
        }
        item(key = "environment") { HomeEnvironment() }
        item(key = "shortcuts") {
            val largeText = LocalDensity.current.fontScale > 1.15f
            val shortcuts: @Composable RowScope.() -> Unit = {
                HomeShortcut("Find hairstyles", "Face-shape analysis", onScanClick, Modifier.weight(1f))
                HomeShortcut("Routines", "Daily steps", openRoutine, Modifier.weight(1f))
                HomeShortcut("Progress", "Skin history", {
                    scope.launch { listState.animateScrollToItem(7) }
                }, Modifier.weight(1f))
            }
            if (largeText) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeShortcut("Find hairstyles", "Face-shape analysis", onScanClick)
                    HomeShortcut("Routines", "Daily steps", openRoutine)
                    HomeShortcut("Progress", "Skin history", { scope.launch { listState.animateScrollToItem(7) } })
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = shortcuts)
        }
        item(key = "recommendations") {
            Card(onClick = onRecommendationsClick, colors = CardDefaults.cardColors(containerColor = CardWhite)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Recommended for You", style = MaterialTheme.typography.titleMedium, color = DarkGreen)
                        Text("Explore products for your skin profile", style = MaterialTheme.typography.bodyMedium, color = SoftGray)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = DarkGreen)
                }
            }
        }
        item(key = "insights") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeSectionHeading("WeGlow Insights", "Daily care tips")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        HomeInsight("Gentle hydration", "Keep track of how your skin feels in your daily note.",
                            "Adjust routine", openRoutine, R.drawable.home_hydration)
                    }
                    item {
                        HomeInsight("UV & barrier alerts", "Personalized UV alerts and SPF reminders are not connected yet.",
                            "Set reminder", null, R.drawable.home_environment)
                    }
                    item {
                        HomeInsight("Barrier recovery", "Your skin-history trends will appear when progress tracking is connected.",
                            "View progress", { scope.launch { listState.animateScrollToItem(7) } }, R.drawable.home_hydration)
                    }
                    item {
                        HomeInsight("Daily sun care", "Your everyday sun-care step belongs in your morning routine.",
                            "Open routine", openRoutine, R.drawable.home_environment)
                    }
                    item {
                        HomeInsight("Evening reset", "Keep your night routine simple and consistent.",
                            "Open routine", { onRoutinesPeriodClick?.invoke(false) ?: onRoutinesClick() }, R.drawable.home_hydration)
                    }
                }
            }
        }
        item(key = "progress") {
            HomeSurface {
                HomeSectionHeading("Your progress", "Skin history · Coming soon")
                Text("Skin scores and changes over time are not available yet.",
                    style = MaterialTheme.typography.bodyMedium, color = SoftGray)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeMetric("Hydration", "Not connected", Modifier.weight(1f))
                    HomeMetric("Clarity", "Not connected", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeMetric("Texture", "Not connected", Modifier.weight(1f))
                    HomeMetric("Skin score", "Not connected", Modifier.weight(1f))
                }
                WeGlowPlannedFeature("View skin history")
            }
        }
        item(key = "editorial") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeSectionHeading("Curated For You", "Skincare & hair inspiration")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { HomeEditorial("Layering Botanicals Guide", "Skincare", R.drawable.home_botanicals) }
                    item { HomeEditorial("Scalp Barrier for Waves", "Hair & scalp", R.drawable.home_scalp) }
                    item { HomeEditorial("The Complete Guide to Layering Skincare Products", "Basics", R.drawable.home_botanicals) }
                }
                WeGlowPlannedFeature("See all guides")
                TextButton(onClick = onDiscoverClick, modifier = Modifier.fillMaxWidth()) { Text("Explore products") }
            }
        }
    }
}

@Composable
private fun HomeScanHero(onScanClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DarkGreen)) {
        Image(painterResource(R.drawable.home_scan), null, Modifier.matchParentSize(),
            contentScale = ContentScale.Crop, alpha = 0.2f)
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CenterFocusStrong, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("AI Face Analysis", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("Explore possible skin concerns", style = MaterialTheme.typography.bodyMedium, color = TextOnDark)
                }
            }
            Button(onClick = onScanClick,
                colors = ButtonDefaults.buttonColors(containerColor = CoralAccent, contentColor = OnCoral),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Start Scan", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
        }
    }
}

@Composable
private fun HomeRoutine(
    steps: List<RoutineStep>, today: LocalDate, morning: Boolean, onPeriodChange: (Boolean) -> Unit,
    completed: Set<String>, onToggle: (String) -> Unit, loading: Boolean, error: String?, onOpen: () -> Unit,
) {
    val matched = steps.withIndex().filter { it.value.product != null }
    val doneCount = matched.count { routineStepKey(today, morning, it.index, it.value.product?.id) in completed }
    val nextIndex = matched.firstOrNull { routineStepKey(today, morning, it.index, it.value.product?.id) !in completed }?.index
    HomeSurface {
        HomeSectionHeading("Your daily routine", "$doneCount of ${matched.size} done")
        val periodColors = FilterChipDefaults.filterChipColors(
            containerColor = PageBackground,
            labelColor = DarkGreen,
            selectedContainerColor = DarkGreen,
            selectedLabelColor = Color.White,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = morning, onClick = { onPeriodChange(true) }, colors = periodColors, label = { Text("Morning") })
            FilterChip(selected = !morning, onClick = { onPeriodChange(false) }, colors = periodColors, label = { Text("Evening") })
        }
        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = DarkGreen)
            Text("Preparing your routine…", style = MaterialTheme.typography.bodyMedium, color = SoftGray)
        } else if (error != null) {
            Text(error, style = MaterialTheme.typography.bodyMedium, color = SoftGray)
        } else {
            LinearProgressIndicator(progress = { if (matched.isEmpty()) 0f else doneCount.toFloat() / matched.size },
                modifier = Modifier.fillMaxWidth(), color = DarkGreen, trackColor = MintChip)
            if (steps.isEmpty()) Text("Your routine is not available yet. Open Routines to get started.",
                style = MaterialTheme.typography.bodyMedium, color = SoftGray)
            steps.forEachIndexed { index, step ->
                val product = step.product
                val key = routineStepKey(today, morning, index, product?.id)
                val done = key in completed
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = done, enabled = product != null,
                        onCheckedChange = { onToggle(key) },
                        modifier = Modifier.semantics { contentDescription = "Complete ${product?.name ?: step.label}" },
                        colors = CheckboxDefaults.colors(checkedColor = DarkGreen))
                    if (product != null && LocalDensity.current.fontScale <= 1.15f) {
                        WeGlowProductImage(product.imageUrl, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(product?.name ?: step.label, style = MaterialTheme.typography.titleSmall, color = DarkGreen)
                        Text(step.label, style = MaterialTheme.typography.bodySmall, color = SoftGray)
                        Text(when { product == null -> "Product not matched yet"; done -> "Done"; index == nextIndex -> "Up next"; else -> "Queued" },
                            style = MaterialTheme.typography.labelMedium, color = if (index == nextIndex) AccentText else SoftGray)
                    }
                }
            }
        }
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkGreen, contentColor = Color.White)) {
            Text(if (matched.isNotEmpty() && doneCount == matched.size) "Review routine" else "Continue Routine")
        }
    }
}

@Composable
private fun HomeEnvironment() {
    HomeSurface {
        HomeSectionHeading("Today's Skin Environment", "Location & weather · Not connected")
        Image(painterResource(R.drawable.home_environment), null,
            modifier = Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        HomeMetric("UV index", "Live readings coming soon", Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeMetric("Humidity", "Not connected", Modifier.weight(1f))
            HomeMetric("Air quality", "Not connected", Modifier.weight(1f))
        }
        Text("Local readings and peak UV times will appear here once weather is connected.",
            style = MaterialTheme.typography.bodySmall, color = SoftGray)
        Text("Last SPF layer · Logging not connected", style = MaterialTheme.typography.bodySmall, color = SoftGray)
        WeGlowPlannedFeature("Log SPF")
    }
}

@Composable
private fun HomeSurface(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardWhite).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun HomeSectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = DarkGreen)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SoftGray)
    }
}

@Composable
private fun HomeMetric(title: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(PageBackground).padding(12.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = DarkGreen)
        Text(value, style = MaterialTheme.typography.bodySmall, color = SoftGray)
    }
}

@Composable
private fun HomeShortcut(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = CardWhite)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = DarkGreen)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SoftGray)
        }
    }
}

@Composable
private fun HomeInsight(title: String, body: String, action: String, onClick: (() -> Unit)?, image: Int) {
    Column(Modifier.width(264.dp).clip(RoundedCornerShape(16.dp)).background(CardWhite)) {
        Image(painterResource(image), null, Modifier.fillMaxWidth().height(88.dp), contentScale = ContentScale.Crop)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = DarkGreen)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = SoftGray)
            if (onClick == null) WeGlowPlannedFeature(action)
            else TextButton(onClick = onClick) { Text(action, color = DarkGreen) }
        }
    }
}

@Composable
private fun HomeEditorial(title: String, category: String, image: Int) {
    Column(Modifier.width(264.dp).clip(RoundedCornerShape(16.dp)).background(CardWhite)) {
        Image(painterResource(image), null, Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Crop)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(category, style = MaterialTheme.typography.labelMedium, color = SoftGray)
            Text(title, style = MaterialTheme.typography.titleMedium, color = DarkGreen)
            WeGlowPlannedFeature("Read guide")
        }
    }
}
