package com.example.weglow

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.weglow.domain.progress.*
import com.example.weglow.feature.progress.ProgressUiState
import com.example.weglow.ui.components.CombinedProgress
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Synthetic UI fixtures only; production reads real records from Supabase. */
class CombinedProgressTest {
    @get:Rule val compose = createComposeRule()
    private val start = Instant.parse("2026-11-01T00:00:00Z")
    private fun scan(id: String, count: Int, days: Long) = ProgressScan(id, start.plusSeconds(days * 86400), count, "test-settings")
    private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault())

    @Test fun selectingAnOlderPointShowsItsBreakdown_whileLatestWorseningStaysVisible() {
        val scans = listOf(scan("a", 50, 0), scan("b", 25, 1), scan("c", 50, 2))
        val tasks = (0 until 10).map { ScheduledTask("s", java.time.LocalDate.of(2026, 11, 1), true, it, start.plusSeconds(it + 1L)) }
        val secondIntervalTasks = (0 until 10).map { ScheduledTask("s", java.time.LocalDate.of(2026, 11, 2), true, it, start.plusSeconds(86400 + it + 1L)) }
        val history = ProgressCalculator.calculate(scans, tasks + secondIntervalTasks,
            (tasks.take(8) + secondIntervalTasks).map { it.id }.toSet(), start, start.plusSeconds(3 * 86400))
        compose.setContent { MaterialTheme { CombinedProgress(ProgressUiState(loaded = true, history = history), {}) } }
        compose.onNodeWithText("Latest interval: 50.0/100").assertExists()
        compose.onNodeWithText("Latest scan: 100.0% more detected concerns").assertExists()
        compose.onNode(hasContentDescription("Scan ${dateFormat.format(scans[1].scannedAt)}: 65.0 out of 100. Show interval details.")).performClick()
        compose.onNodeWithText("Previous concerns: 50 · Current concerns: 25").assertExists()
        compose.onNodeWithText("Routine completion: 80.0% (8/10 tasks)").assertExists()
        compose.onNodeWithText("Scan points: 25.0/50").assertExists()
        compose.onNodeWithText("Routine points: 40.0/50").assertExists()
        compose.onNodeWithText("Combined score: 65.0/100").assertExists()
        compose.onNodeWithText("Latest scan: 100.0% more detected concerns").assertExists()
    }

    @Test fun firstScanIsABaselineWithUnavailableScore() {
        compose.setContent { MaterialTheme { CombinedProgress(ProgressUiState(loaded = true,
            history = ProgressHistory(scans = listOf(scan("a", 50, 0)))), {}) } }
        compose.onNodeWithText("Complete another scan to measure progress.").assertExists()
        compose.onNodeWithText("Latest interval: Unavailable").assertExists()
    }
}
