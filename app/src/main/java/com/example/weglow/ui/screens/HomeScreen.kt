package com.example.weglow.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.example.weglow.R
import com.example.weglow.domain.model.RoutineStep
import com.example.weglow.domain.model.UvDailyReading
import com.example.weglow.feature.routine.routineStepKey
import com.example.weglow.feature.environment.EnvironmentUiState
import com.example.weglow.ui.components.ProfileAvatar
import com.example.weglow.ui.components.WeGlowPlannedFeature
import com.example.weglow.ui.components.WeGlowProductImage
import com.example.weglow.ui.components.rememberDecodedBitmap
import com.example.weglow.ui.theme.*
import kotlinx.coroutines.delay
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
    environmentState: EnvironmentUiState = EnvironmentUiState.Loading,
    onEnvironmentRetry: () -> Unit = {},
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
    progressState: com.example.weglow.feature.progress.ProgressUiState = com.example.weglow.feature.progress.ProgressUiState(),
    onProgressRetry: () -> Unit = {},
    onSetRoutineCompletion: ((String, Boolean) -> Unit)? = null,
    completableRoutineKeys: Set<String>? = null,
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
    // Intentionally not rememberSaveable: this tab's NavBackStackEntry is
    // destroyed and recreated when the bottom nav leaves and returns to it
    // (saveState/restoreState still preserves the entry's other saved state),
    // so a plain remember here resets the viewport to the top on return
    // without touching any business/data state.
    val listState = remember { LazyListState() }
    val activeSteps = if (isMorning) morningRoutine else eveningRoutine
    val openRoutine = { onRoutinesPeriodClick?.invoke(isMorning) ?: onRoutinesClick() }

    if (showNotifications) {
        AlertDialog(
            onDismissRequest = { showNotifications = false },
            title = { Text("Notifications") },
            text = { Text("You'll receive a reminder to apply sunscreen when your local UV index is high.") },
            confirmButton = { TextButton(onClick = { showNotifications = false }) { Text("Got it") } },
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PageBackground).testTag("home"),
        // top = 16.dp reproduces DiscoverScreen's header Row `padding(vertical = 16.dp)`:
        // Discover applies that 16dp directly on its header Row (its LazyColumn has no
        // contentPadding), while Home's horizontal 24dp is already supplied here for every
        // item, so the header Row itself adds no additional padding of its own.
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "greeting") {
            // padding(bottom = 16.dp) placed after clip()/background() (same convention
            // as HomeScanHero's own 20dp-corner card, padded by 20dp) so the clip's
            // rounded-corner shape is computed over the full card including this gap:
            // the last line of text then sits 16dp above the bottom edge, clearing the
            // 16dp corner radius entirely instead of having its left edge (e.g. the "S"
            // in "Skin sync") sliced by the bottom-left corner arc.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF8FAF7))
                    .padding(bottom = 16.dp),
            ) {
                // Header geometry matches DiscoverScreen's header Row exactly (same
                // logo size, same avatar size, same SpaceBetween/CenterVertically
                // convention); the notification icon is the only Home-only addition,
                // inserted immediately before the avatar and capped to the avatar's
                // size so it cannot make the row taller than Discover's.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.weglow_logo),
                        contentDescription = "WeGlow",
                        modifier = Modifier.size(24.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showNotifications = true },
                            modifier = Modifier
                                .size(36.dp)
                                .semantics { contentDescription = "Open alerts" },
                        ) {
                            Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = DarkGreen)
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onProfileClick)
                                .semantics { contentDescription = "Open profile" },
                        ) {
                            ProfileAvatar(image = rememberDecodedBitmap(profileImage), size = 36.dp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                // LocalConfiguration.current (not Locale.getDefault()) so this recomposes if the
                // user changes their system locale while the app is open.
                val currentLocale = LocalConfiguration.current.locales[0]
                Text(today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", currentLocale)),
                    style = MaterialTheme.typography.labelMedium, color = SoftGray)
                Spacer(Modifier.height(4.dp))
                Text(displayName?.let { "Hello, ${homeGreetingName(it)}" } ?: "Hello there",
                    style = MaterialTheme.typography.headlineLarge, color = DarkGreen)
                Text("A little care, every day.", style = MaterialTheme.typography.bodyMedium, color = SoftGray)
                Text("Skin sync · Coming soon", style = MaterialTheme.typography.bodySmall, color = SoftGray)
            }
        }
        item(key = "scan") { HomeScanHero(onScanClick) }
        // Environment is an early daily decision point, so keep UV, humidity and air quality
        // above the routine and progress content.
        item(key = "environment") { HomeEnvironment(environmentState, onEnvironmentRetry) }
        item(key = "routine") {
            HomeRoutine(
                activeSteps, today, isMorning, { isMorning = it }, completedRoutineKeys,
                { key -> onSetRoutineCompletion?.invoke(key, key !in completedRoutineKeys) ?: onToggleRoutineStep(key) },
                isRoutineLoading, routineError, openRoutine, completableRoutineKeys,
            )
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
        item(key = "progress") {
            com.example.weglow.ui.components.CombinedProgress(progressState, onProgressRetry)
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
    completableKeys: Set<String>?,
) {
    val matched = steps.withIndex().filter { it.value.product != null }
    val doneCount = matched.count { routineStepKey(today, morning, it.index, it.value.product?.id) in completed }
    val nextIndex = matched.firstOrNull { routineStepKey(today, morning, it.index, it.value.product?.id) !in completed }?.index
    HomeSurface {
        HomeSectionHeading("Your daily routine", if (matched.isEmpty() && !loading) "Sample routine" else "$doneCount of ${matched.size} done")
        if (matched.isNotEmpty()) Text("Daily checkmarks are separate from scan-interval progress. Tasks are due at 8 AM and 8 PM; checkmarks unlock when due.",
            style = MaterialTheme.typography.bodySmall, color = SoftGray)
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
            if (matched.isEmpty()) com.example.weglow.ui.components.DemoRoutine(morning)
        } else if (matched.isEmpty()) {
            com.example.weglow.ui.components.DemoRoutine(morning)
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
                    IconToggleButton(
                        checked = done,
                        enabled = product != null && (completableKeys == null || key in completableKeys),
                        onCheckedChange = { onToggle(key) },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Complete ${product?.name ?: step.label}" },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (done) DarkGreen else Color.Transparent)
                                .then(if (done) Modifier else Modifier.border(2.dp, SoftGray, CircleShape)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (done) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
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
private fun HomeEnvironment(environmentState: EnvironmentUiState, onRetry: () -> Unit) {
    val uvHistory = (environmentState as? EnvironmentUiState.Success)?.environment?.uvDailyHistory.orEmpty()
    val display = when (environmentState) {
        EnvironmentUiState.Loading -> EnvironmentDisplay("Loading…", "Loading…", "Loading…", "Getting local conditions…")
        is EnvironmentUiState.Error -> EnvironmentDisplay("Unavailable", "Unavailable", "Unavailable", environmentState.message)
        is EnvironmentUiState.Success -> environmentState.environment.let { environment ->
            EnvironmentDisplay(
                "${formatUv(environment.uvIndex)} · ${environment.uvCategory}",
                "${environment.humidity}%",
                environment.airQualityIndex?.let { "$it · ${environment.airQualityLabel}" } ?: environment.airQualityLabel,
                if (environment.isCountryFallback) {
                    "Using your country's default city because current location is unavailable."
                } else environment.locationName?.let { "Current conditions for $it" } ?: "Current local conditions",
            )
        }
    }
    HomeSurface {
        HomeSectionHeading("Today's Skin Environment",
            (environmentState as? EnvironmentUiState.Success)?.environment?.locationName ?: "Local weather and UV")
        UvHistoryGraph(uvHistory)
        HomeMetric("UV index", display.uv, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeMetric("Humidity", display.humidity, Modifier.weight(1f))
            HomeMetric("Air quality", display.airQuality, Modifier.weight(1f))
        }
        Text(display.detail,
            style = MaterialTheme.typography.bodySmall, color = SoftGray)
        if (environmentState is EnvironmentUiState.Error ||
            (environmentState as? EnvironmentUiState.Success)?.environment?.isCountryFallback == true) {
            TextButton(onClick = onRetry) {
                Text(if (environmentState is EnvironmentUiState.Success) "Use current location" else "Retry", color = DarkGreen)
            }
        }
        WeGlowPlannedFeature("Log SPF")
    }
}

@Composable
private fun UvHistoryGraph(readings: List<UvDailyReading>) {
    Column(
        Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(12.dp)).background(PageBackground).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (readings.isEmpty()) {
            Text("UV history unavailable", style = MaterialTheme.typography.bodySmall, color = SoftGray)
        } else {
            val scrollState = rememberScrollState()
            Column(Modifier.fillMaxWidth().weight(1f).horizontalScroll(scrollState)) {
            Canvas(
                modifier = Modifier.width((readings.size * 44).dp).weight(1f).semantics {
                    contentDescription = "31-day daily UV index history"
                },
            ) {
                val observedMinimum = readings.minOf { it.uvIndex }
                val observedMaximum = readings.maxOf { it.uvIndex }
                val variation = maxOf(1.0, observedMaximum - observedMinimum)
                val verticalPadding = variation * 0.2
                val graphMinimum = maxOf(0.0, observedMinimum - verticalPadding)
                val graphMaximum = observedMaximum + verticalPadding
                val graphRange = maxOf(1.0, graphMaximum - graphMinimum)
                val xStep = if (readings.size == 1) 0f else size.width / (readings.size - 1)
                fun point(index: Int, uv: Double) = androidx.compose.ui.geometry.Offset(
                    x = if (readings.size == 1) size.width / 2 else index * xStep,
                    y = size.height - (((uv - graphMinimum) / graphRange) * size.height).toFloat(),
                )
                repeat(3) { index ->
                    val y = size.height * index / 2f
                    drawLine(
                        SoftGray.copy(alpha = 0.16f),
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                    )
                }
                val points = readings.mapIndexed { index, reading -> point(index, reading.uvIndex) }
                if (points.size > 1) {
                    // Catmull-Rom-style cubic Béziers smooth the real day-to-day values
                    // without adding, averaging, or otherwise changing any data point.
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (index in 0 until points.lastIndex) {
                            val previous = points[(index - 1).coerceAtLeast(0)]
                            val start = points[index]
                            val end = points[index + 1]
                            val following = points[(index + 2).coerceAtMost(points.lastIndex)]
                            cubicTo(
                                start.x + (end.x - previous.x) / 6f,
                                start.y + (end.y - previous.y) / 6f,
                                end.x - (following.x - start.x) / 6f,
                                end.y - (following.y - start.y) / 6f,
                                end.x,
                                end.y,
                            )
                        }
                    }
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(points.last().x, size.height)
                        lineTo(points.first().x, size.height)
                        close()
                    }
                    drawPath(fillPath, color = DarkGreen.copy(alpha = 0.08f))
                    drawPath(path, color = DarkGreen, style = Stroke(width = 3.dp.toPx()))
                }
                readings.forEachIndexed { index, reading -> drawCircle(CoralAccent, radius = 4.dp.toPx(), center = point(index, reading.uvIndex)) }
            }
            Row(Modifier.width((readings.size * 44).dp)) {
                readings.forEach { reading ->
                    Text("${reading.date.takeLast(5)}\n${formatUv(reading.uvIndex)}", modifier = Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, color = SoftGray)
                }
            }
            }
        }
    }
}

private data class EnvironmentDisplay(val uv: String, val humidity: String, val airQuality: String, val detail: String)

private fun formatUv(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(Locale.US, value)

private fun homeGreetingName(fullName: String): String =
    fullName.trim().split(Regex("\\s+")).filter(String::isNotBlank).take(2).joinToString(" ")

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
