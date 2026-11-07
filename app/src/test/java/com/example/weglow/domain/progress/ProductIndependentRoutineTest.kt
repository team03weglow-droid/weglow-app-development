package com.example.weglow.domain.progress

import com.example.weglow.domain.recommendation.BasicRoutine
import com.example.weglow.domain.model.RoutineStep
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ProductIndependentRoutineTest {
    @Test fun ownProductTasksAreDueWithoutInventingCatalogRecords() {
        val plan = BasicRoutine.create()
        assertTrue((plan.morning + plan.evening).all { it.product == null && it.isTrackable })
        val start = Instant.parse("2026-11-01T00:00:00Z")
        val end = Instant.parse("2026-11-01T21:00:00Z")
        val tasks = RoutineScheduleTasks.due(listOf(RoutineSchedule("own", start, ZoneId.of("UTC"), plan)), end)
        assertEquals(5, tasks.size)
        val scans = listOf(ProgressScan("a", start, 50, "same"), ProgressScan("b", end, 25, "same"))
        val score = ProgressCalculator.calculate(scans, tasks, tasks.take(4).map { it.id }.toSet(), start, end).intervals.single()
        assertEquals(65.0, score.score!!, 0.001)
        assertFalse(RoutineStep("Legacy unmatched", null, emptyList()).isTrackable)
    }
}
