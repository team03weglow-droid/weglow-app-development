package com.example.weglow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.weglow.domain.progress.ProgressInterval
import com.example.weglow.feature.progress.ProgressUiState
import com.example.weglow.feature.progress.DemoProgress
import com.example.weglow.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun CombinedProgress(state: ProgressUiState, onRetry: () -> Unit) {
    val hasRealPoints = state.history.points.isNotEmpty()
    var showDemo by remember(hasRealPoints) { mutableStateOf(!hasRealPoints) }
    val demo = remember { ProgressUiState(loaded = true, loading = false, history = DemoProgress.history()) }
    CombinedProgressCard(if (showDemo) demo else state, onRetry, showDemo) { showDemo = !showDemo }
}

@Composable
private fun CombinedProgressCard(state: ProgressUiState, onRetry: () -> Unit, isDemo: Boolean, onToggleDemo: () -> Unit) {
    val history = state.history
    val intervals = history.intervals
    var selectedId by remember(isDemo) { mutableStateOf<String?>(null) }
    val selected = intervals.firstOrNull { it.current.id == selectedId } ?: intervals.lastOrNull()
    val latest = intervals.lastOrNull()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardWhite).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Combined Progress", style = MaterialTheme.typography.titleLarge, color = DarkGreen)
        if (isDemo) {
            Text("Demo results · Sample data", style = MaterialTheme.typography.titleSmall, color = AccentText)
            Text("These example scans and routine completions are not your results. Demo routine checkboxes do not change this preview.",
                style = MaterialTheme.typography.bodySmall, color = SoftGray)
        }
        TextButton(onClick = onToggleDemo) { Text(if (isDemo) "Show my results" else "Show demo results") }
        Text("50% scan improvement + 50% routine completion", style = MaterialTheme.typography.bodySmall, color = SoftGray)
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DarkGreen)
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) { Text("Retry progress") }
        }
        if (state.loaded) {
            Text("Latest interval: ${latest?.score?.let { "${number(it)}/100" } ?: "Unavailable"}",
                style = MaterialTheme.typography.titleMedium, color = DarkGreen)
            if (latest?.score != null) {
                LinearProgressIndicator(progress = { latest.score.toFloat() / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = DarkGreen, trackColor = MintChip)
            }
            latest?.signedImprovement?.let { change ->
                Text(when {
                    change < 0 -> "Latest scan: ${number(abs(change))}% more detected concerns"
                    change == 0.0 -> "Latest scan: No detected change (0%)"
                    else -> "Latest scan: ${number(change)}% fewer detected concerns"
                }, color = if (change < 0) LogoutRed else DarkGreen, style = MaterialTheme.typography.bodyMedium)
            }
            latest?.let {
                Text("Latest routine completion: ${it.routinePercentage?.let { percent -> "${number(percent)}%" } ?: "Unavailable"}",
                    style = MaterialTheme.typography.bodyMedium, color = DarkGreen)
            }
            when {
                history.scans.isEmpty() -> Text("Complete a skin scan to establish your baseline.", color = SoftGray)
                intervals.isEmpty() && history.scans.any { it.valid } -> Text("Complete another scan to measure progress.", color = DarkGreen)
                intervals.isEmpty() -> Text("Progress is unavailable: saved scans are missing valid counts, dates or detection settings. Complete a new scan to establish a comparable baseline.", color = SoftGray)
            }
            if (history.invalidScanCount > 0) Text(
                "${history.invalidScanCount} saved scan(s) cannot be compared because counts, dates or detection settings are missing or invalid. They are excluded, not scored as zero.",
                color = SoftGray, style = MaterialTheme.typography.bodySmall,
            )
            if (intervals.isNotEmpty()) {
                ProgressLineChart(intervals, selected?.current?.id, curved = isDemo) { selectedId = it }
                selected?.let { IntervalDetails(it) }
            }
        }
        Text("Each scan is compared with the previous valid scan. Points measure that interval, not cumulative improvement since signup.",
            style = MaterialTheme.typography.bodySmall, color = SoftGray)
        Text("An engagement and progress indicator, not a medically validated skin health score. Detection counts can vary with lighting and photo conditions.",
            style = MaterialTheme.typography.bodySmall, color = SoftGray)
    }
}

@Composable
private fun ProgressLineChart(intervals: List<ProgressInterval>, selectedId: String?, curved: Boolean, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.height(180.dp).padding(end = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf("100", "50", "0").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = SoftGray) }
        }
        Column(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            // Evenly spaced scan events, explicitly dated; no interpolated/smoothed observations.
            val chartWidth = (intervals.size.coerceAtLeast(3) * 88).dp
            Canvas(Modifier.width(chartWidth).height(180.dp).padding(vertical = 8.dp)
                .semantics { contentDescription = "Combined Progress line chart. Scan dates on the horizontal axis; combined score from 0 to 100 on the vertical axis. Select a dated point below for details." }
                .pointerInput(intervals) {
                    detectTapGestures { offset ->
                        val step = size.width.toFloat() / intervals.size.coerceAtLeast(3)
                        val index = (offset.x / step).toInt().coerceIn(0, intervals.lastIndex)
                        if (intervals[index].score != null) onSelect(intervals[index].current.id)
                    }
                }) {
                val step = size.width / intervals.size.coerceAtLeast(3)
                listOf(0f, 0.5f, 1f).forEach { fraction ->
                    drawLine(SoftGray.copy(alpha = 0.2f), Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction))
                }
                fun point(index: Int, score: Double) = Offset(step * (index + 0.5f), size.height * (1f - score.toFloat() / 100f))
                intervals.forEachIndexed { index, interval ->
                    interval.score?.let { score ->
                        if (index > 0) intervals[index - 1].score?.let { previous ->
                            val start = point(index - 1, previous)
                            val end = point(index, score)
                            if (curved) {
                                // Sample-data preview: rounded connections preserve every point
                                // and stay within each pair's values without overshooting.
                                val middleX = (start.x + end.x) / 2f
                                val path = Path().apply {
                                    moveTo(start.x, start.y)
                                    cubicTo(middleX, start.y, middleX, end.y, end.x, end.y)
                                }
                                drawPath(path, DarkGreen, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                            } else {
                                drawLine(DarkGreen, start, end, strokeWidth = 2.dp.toPx())
                            }
                        }
                        drawCircle(if (interval.current.id == selectedId) AccentText else DarkGreen,
                            radius = if (interval.current.id == selectedId) 6.dp.toPx() else 4.dp.toPx(), center = point(index, score))
                    }
                }
            }
            Row(Modifier.width(chartWidth)) {
                intervals.forEach { interval ->
                    TextButton(onClick = { onSelect(interval.current.id) }, modifier = Modifier.width(88.dp)
                        .semantics { contentDescription = "Scan ${date(interval.current.scannedAt!!)}: ${interval.score?.let { "${number(it)} out of 100" } ?: "unavailable"}. Show interval details." }) {
                        Text(date(interval.current.scannedAt!!, "MMM d\nyyyy"), style = MaterialTheme.typography.labelSmall,
                            color = if (interval.current.id == selectedId) AccentText else DarkGreen)
                    }
                }
            }
        }
    }
    Text("Scan dates · Tap a point or date for details. Gaps mean unavailable scores.", style = MaterialTheme.typography.bodySmall, color = SoftGray)
}

@Composable
private fun IntervalDetails(interval: ProgressInterval) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PageBackground).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${date(interval.previous.scannedAt!!)} → ${date(interval.current.scannedAt!!)}", style = MaterialTheme.typography.titleSmall, color = DarkGreen)
        Text("Previous concerns: ${interval.previous.concerns} · Current concerns: ${interval.current.concerns}", style = MaterialTheme.typography.bodyMedium, color = DarkGreen)
        val change = interval.signedImprovement
        val changeText = when {
            change == null -> "Scan change: Unavailable"
            change < 0 -> "Scan change: ${number(abs(change))}% more detected concerns (${number(change)}% improvement)"
            change == 0.0 -> "Scan change: 0% · No detected change"
            else -> "Scan change: ${number(change)}% fewer detected concerns (+${number(change)}% improvement)"
        }
        Text(changeText, style = MaterialTheme.typography.bodyMedium, color = if (change != null && change < 0) LogoutRed else DarkGreen)
        Text("Routine completion: ${interval.routinePercentage?.let { "${number(it)}% (${interval.completedTasks}/${interval.dueTasks} tasks)" } ?: "Unavailable"}",
            style = MaterialTheme.typography.bodyMedium, color = DarkGreen)
        Text("Scan points: ${interval.scanPoints?.let { "${number(it)}/50" } ?: "Unavailable"}", color = DarkGreen)
        Text("Routine points: ${interval.routinePoints?.let { "${number(it)}/50" } ?: "Unavailable"}", color = DarkGreen)
        Text("Combined score: ${interval.score?.let { "${number(it)}/100" } ?: "Unavailable"}", style = MaterialTheme.typography.titleMedium, color = DarkGreen)
        interval.unavailableReason?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = SoftGray) }
    }
}

private fun number(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)
private fun date(value: java.time.Instant, pattern: String = "MMM d, yyyy HH:mm"): String =
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).withZone(ZoneId.systemDefault()).format(value)
