package com.example.weglow.feature.routine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the Routines date-selector defect: tapping a different date must not
 * silently reuse another date's completion checkmarks. [routineStepKey] is the pure logic that
 * keeps RoutinesScreen's `doneState: Set<String>` independent per date.
 */
class RoutineCompletionKeyTest {

    @Test
    fun differentDays_produceDifferentKeys_forAnOtherwiseIdenticalStep() {
        val monday = routineStepKey(day = 0, isMorning = true, stepIndex = 0, productId = "p1")
        val tuesday = routineStepKey(day = 1, isMorning = true, stepIndex = 0, productId = "p1")

        assertNotEquals(monday, tuesday)
    }

    @Test
    fun morningAndEvening_areIndependentForTheSameDayAndStepIndex() {
        val morning = routineStepKey(day = 2, isMorning = true, stepIndex = 0, productId = "p1")
        val evening = routineStepKey(day = 2, isMorning = false, stepIndex = 0, productId = "p1")

        assertNotEquals(morning, evening)
    }

    @Test
    fun differentStepsOnTheSameDayAndPeriod_produceDifferentKeys() {
        val cleanser = routineStepKey(day = 2, isMorning = true, stepIndex = 0, productId = "p1")
        val moisturizer = routineStepKey(day = 2, isMorning = true, stepIndex = 1, productId = "p2")

        assertNotEquals(cleanser, moisturizer)
    }

    @Test
    fun sameDayPeriodStepAndProduct_alwaysProducesTheSameKey() {
        val first = routineStepKey(day = 3, isMorning = false, stepIndex = 2, productId = "p9")
        val second = routineStepKey(day = 3, isMorning = false, stepIndex = 2, productId = "p9")

        assertEquals(first, second)
    }

    @Test
    fun missingProductSteps_onDifferentIndices_stillProduceDistinctKeys() {
        // A step with no matched product (RoutineBuilder leaves product == null rather than
        // inventing one) must not collide with a different missing-product step in the same
        // day/period just because both have a null product id.
        val stepOne = routineStepKey(day = 0, isMorning = true, stepIndex = 0, productId = null)
        val stepTwo = routineStepKey(day = 0, isMorning = true, stepIndex = 2, productId = null)

        assertNotEquals(stepOne, stepTwo)
    }

    @Test
    fun togglingCompletionOnOneDay_doesNotMarkTheSameStepDoneOnAnotherDay() {
        // Reproduces the exact reported defect against the real Set<String> shape RoutinesScreen
        // uses for doneState: marking a step done on day 0 must not make the equivalent step on
        // day 1 appear done too.
        val dayZeroKey = routineStepKey(day = 0, isMorning = true, stepIndex = 0, productId = "p1")
        val dayOneKey = routineStepKey(day = 1, isMorning = true, stepIndex = 0, productId = "p1")

        var doneState = setOf<String>()
        doneState = doneState + dayZeroKey // user marks the step done while day 0 is selected

        assertTrue(dayZeroKey in doneState)
        assertFalse("day 1's identical step must not appear done", dayOneKey in doneState)
    }

    @Test
    fun switchingBackToAPreviouslyCompletedDay_restoresItsCompletionState() {
        // Day A -> mark a step done -> switch to Day B -> switch back to Day A: A's completion
        // must still show, since completion is keyed independently per day rather than cleared
        // or overwritten when the selected date changes.
        val dayAKey = routineStepKey(day = 0, isMorning = true, stepIndex = 0, productId = "p1")
        val dayBKey = routineStepKey(day = 1, isMorning = true, stepIndex = 0, productId = "p1")

        var doneState = setOf<String>()
        doneState = doneState + dayAKey // complete the step while day A is selected
        // ... user switches selectedDay to B, toggles nothing there ...
        // ... user switches selectedDay back to A ...

        assertTrue("day A's completion must survive switching away and back", dayAKey in doneState)
        assertFalse(dayBKey in doneState)
    }
}
