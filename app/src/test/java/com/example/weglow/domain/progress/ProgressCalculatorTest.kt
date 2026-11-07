package com.example.weglow.domain.progress

import com.example.weglow.domain.model.Product
import com.example.weglow.domain.model.RoutinePlan
import com.example.weglow.domain.model.RoutineStep
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ProgressCalculatorTest {
    private val start = Instant.parse("2026-11-01T00:00:00Z")
    private val end = start.plusSeconds(86400)
    private fun scan(id: String, count: Int?, at: Instant = start, settings: String? = "model/settings-v1") = ProgressScan(id, at, count, settings)
    private fun task(index: Int, at: Instant = start.plusSeconds(index + 1L)) =
        ScheduledTask("schedule", LocalDate.of(2026, 11, 1), true, index, at)
    private val tasks = (0 until 10).map { task(it) }
    private fun score(previous: Int, latest: Int) = ProgressCalculator.interval(scan("a", previous), scan("b", latest, end), tasks, tasks.take(8).map { it.id }.toSet(), start, end)

    @Test fun example_fiftyToTwentyFiveAndEightyPercentRoutineIsSixtyFive() {
        val result = score(50, 25)
        assertEquals(50.0, result.signedImprovement!!, 0.00001)
        assertEquals(80.0, result.routinePercentage!!, 0.00001)
        assertEquals(25.0, result.scanPoints!!, 0.00001)
        assertEquals(40.0, result.routinePoints!!, 0.00001)
        assertEquals(65.0, result.score!!, 0.00001)
    }

    @Test fun worseningKeepsSignedChangeAndAwardsNoScanPoints() {
        val result = score(25, 50)
        assertEquals(-100.0, result.signedImprovement!!, 0.0)
        assertEquals(0.0, result.scanPoints!!, 0.0)
        assertEquals(40.0, result.score!!, 0.0)
    }

    @Test fun unchangedIsGenuineZero_notUnavailable() {
        val result = score(25, 25)
        assertEquals(0.0, result.signedImprovement!!, 0.0)
        assertEquals(0.0, result.scanPoints!!, 0.0)
        assertNotNull(result.score)
    }

    @Test fun zeroBaselineDoesNotDivideByZero_evenWithRoutineCredit() {
        val zero = score(0, 0)
        assertNull(zero.signedImprovement); assertNull(zero.score)
        assertEquals("No concerns detected in either scan", zero.unavailableReason)
        val new = score(0, 5)
        assertNull(new.scanPoints); assertNull(new.score)
        assertEquals("New concerns detected", new.unavailableReason)
        assertEquals(80.0, new.routinePercentage!!, 0.0)
    }

    @Test fun noDueTasksIsUnavailable_notZeroOrPerfect() {
        val result = ProgressCalculator.interval(scan("a", 50), scan("b", 25, end), emptyList(), emptySet(), start, end)
        assertEquals(0, result.dueTasks); assertNull(result.routinePercentage); assertNull(result.score)
        assertEquals(25.0, result.scanPoints!!, 0.0)
    }

    @Test fun zeroCompletionsIsRealZero_andFullImprovementIsCappedAtFifty() {
        val result = ProgressCalculator.interval(scan("a", 50), scan("b", 0, end), tasks, emptySet(), start, end)
        assertEquals(0.0, result.routinePercentage!!, 0.0)
        assertEquals(50.0, result.scanPoints!!, 0.0)
        assertEquals(50.0, result.score!!, 0.0)
    }

    @Test fun intervalIsStartExclusiveEndInclusive_futureAndDuplicateTasksNeverCount() {
        val before = task(0, start.minusSeconds(1))
        val atStart = task(1, start)
        val inside = task(2, start.plusSeconds(1))
        val atEnd = task(3, end)
        val future = task(4, end.plusSeconds(1))
        val all = listOf(before, atStart, inside, inside, atEnd, future)
        val result = ProgressCalculator.interval(scan("a", 50), scan("b", 25, end), all, all.map { it.id }.toSet(), start, end)
        assertEquals(2, result.dueTasks); assertEquals(2, result.completedTasks)
        assertEquals(100.0, result.routinePercentage!!, 0.0)
        assertEquals(75.0, result.score!!, 0.0)
    }

    @Test fun missingCountsSettingsDatesOrIncompatibleSettingsNeverBecomeZeroScores() {
        val previous = scan("a", 50)
        listOf(scan("b", null, end), scan("b", -1, end), scan("b", 10, end, null),
            ProgressScan("b", null, 10, "model/settings-v1"), scan("b", 10, end, "different"),
            scan("b", 10, start), scan("b", 10, end.plusSeconds(1))).forEach {
            val result = ProgressCalculator.interval(previous, it, tasks, emptySet(), start, end)
            assertNull(result.score); assertNotNull(result.unavailableReason)
        }
    }

    @Test fun firstScanIsBaseline_andOnlyNewValidScansCreateIntervals() {
        val first = scan("a", 50)
        assertTrue(ProgressCalculator.calculate(listOf(first), tasks, emptySet(), start, end).intervals.isEmpty())
        val history = ProgressCalculator.calculate(listOf(scan("b", 25, end), first, first, scan("invalid", null, start.plusSeconds(1))), tasks, emptySet(), start, end)
        assertEquals(1, history.intervals.size); assertEquals(1, history.invalidScanCount)
        assertEquals("a", history.intervals.single().previous.id)
    }

    @Test fun partialRoutineCoverageDoesNotInventHistoricalTasks() {
        val result = ProgressCalculator.interval(scan("a", 50), scan("b", 25, end), tasks, emptySet(), start.plusSeconds(1), end)
        assertNull(result.routinePercentage); assertNull(result.score)
        assertTrue(result.unavailableReason!!.contains("full interval"))
    }

    @Test fun comparisonsAreBetweenConsecutiveScans_notSignupBaseline() {
        val history = ProgressCalculator.calculate(listOf(scan("a", 50), scan("b", 25, start.plusSeconds(100)), scan("c", 20, end)),
            listOf(task(0, start.plusSeconds(1)), task(1, start.plusSeconds(101))), emptySet(), start, end)
        assertEquals(50.0, history.intervals[0].signedImprovement!!, 0.0)
        assertEquals(20.0, history.intervals[1].signedImprovement!!, 0.0)
    }

    @Test fun immutableSchedulesGenerateMissedDays_andEditsDontRewritePastDenominators() {
        val product = Product("one", "Cleanser", "", null)
        val step = RoutineStep("Cleanse", product, emptyList())
        val old = RoutineSchedule("old", start, ZoneId.of("UTC"), RoutinePlan(listOf(step), listOf(step)))
        val newer = RoutineSchedule("new", end, ZoneId.of("UTC"), RoutinePlan(listOf(step, step), emptyList()))
        val generated = RoutineScheduleTasks.due(listOf(old, newer), end.plusSeconds(8 * 3600))
        assertEquals(2, generated.count { it.scheduleId == "old" })
        assertEquals(2, generated.count { it.scheduleId == "new" })
        assertEquals(4, generated.map { it.id }.toSet().size)
        assertEquals(old.plan, RoutineScheduleTasks.scheduleOn(listOf(old, newer), LocalDate.of(2026, 11, 1))!!.plan)
        val before = ProgressCalculator.interval(scan("a", 50), scan("b", 25, end), RoutineScheduleTasks.due(listOf(old), end), emptySet(), start, end)
        val after = ProgressCalculator.interval(scan("a", 50), scan("b", 25, end), generated, emptySet(), start, end)
        assertEquals(before, after)
    }

    @Test fun unmatchedStepsAndTasksBeforeActivationAreExcluded_andDueTimesUseStoredZone() {
        val product = Product("one", "Cleanser", "", null)
        val steps = listOf(RoutineStep("unmatched", null, emptyList()), RoutineStep("Cleanse", product, emptyList()))
        val zone = ZoneId.of("Asia/Colombo")
        val activation = Instant.parse("2026-11-01T03:00:00Z") // after 8 AM local
        val schedule = RoutineSchedule("s", activation, zone, RoutinePlan(steps, steps))
        val generated = RoutineScheduleTasks.due(listOf(schedule), Instant.parse("2026-11-01T14:30:00Z"))
        assertEquals(1, generated.size); assertFalse(generated.single().morning)
        assertEquals(1, generated.single().stepIndex)
        assertEquals(Instant.parse("2026-11-01T14:30:00Z"), generated.single().dueAt)
        val displayed = RoutineScheduleTasks.planOn(listOf(schedule), LocalDate.of(2026, 11, 1))!!
        assertTrue(displayed.morning.isEmpty())
        assertEquals(steps, displayed.evening)
    }
}
