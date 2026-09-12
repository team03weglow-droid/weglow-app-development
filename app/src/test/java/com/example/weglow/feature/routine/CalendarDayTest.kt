package com.example.weglow.feature.routine

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarDayTest {
    private val locale = Locale.ENGLISH

    @Test
    fun todayIsSelectedInitially() {
        val today = LocalDate.of(2026, 9, 12)

        val days = calendarDays(today, today, today, locale)

        assertEquals(today, days.single { it.isSelected }.date)
        assertTrue(days.single { it.date == today }.isToday)
    }

    @Test
    fun generatesSundayThroughSaturdayWeekContainingAnchor() {
        val anchor = LocalDate.of(2026, 9, 12)

        val days = calendarDays(anchor, anchor, anchor, locale)

        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 9, 6), days.first().date)
        assertEquals(LocalDate.of(2026, 9, 12), days.last().date)
        assertEquals("SUN", days.first().dayName)
        assertEquals("SAT", days.last().dayName)
    }

    @Test
    fun crossesEndOfMonth() {
        val anchor = LocalDate.of(2026, 4, 30)

        val dates = calendarDays(anchor, anchor, anchor, locale).map { it.date }

        assertTrue(LocalDate.of(2026, 4, 30) in dates)
        assertTrue(LocalDate.of(2026, 5, 1) in dates)
    }

    @Test
    fun crossesEndOfYear() {
        val anchor = LocalDate.of(2026, 12, 30)

        val dates = calendarDays(anchor, anchor, anchor, locale).map { it.date }

        assertTrue(LocalDate.of(2026, 12, 31) in dates)
        assertTrue(LocalDate.of(2027, 1, 1) in dates)
    }

    @Test
    fun includesLeapDay() {
        val leapDay = LocalDate.of(2028, 2, 29)

        val days = calendarDays(leapDay, leapDay, leapDay, locale)

        assertTrue(days.any { it.date == leapDay && it.dayNumber == "29" })
    }

    @Test
    fun anotherFullDateCanBeSelected() {
        val today = LocalDate.of(2026, 9, 12)
        val selection = today.minusDays(2)

        val days = calendarDays(today, selection, today, locale)

        assertEquals(selection, days.single { it.isSelected }.date)
        assertFalse(days.single { it.date == selection }.isToday)
    }

    @Test
    fun equalDayNumbersInDifferentMonthsHaveDifferentIdentities() {
        val september = LocalDate.of(2026, 9, 12)
        val october = LocalDate.of(2026, 10, 12)

        assertEquals(september.dayOfMonth, october.dayOfMonth)
        assertNotEquals(september, october)
        assertNotEquals(september.toString(), october.toString())
    }
}
