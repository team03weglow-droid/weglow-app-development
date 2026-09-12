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
    fun generatesSevenConsecutiveDaysCenteredOnAnchor() {
        val anchor = LocalDate.of(2026, 9, 12)

        val days = calendarDays(anchor, anchor, anchor, locale)

        assertEquals(7, days.size)
        assertEquals(anchor.minusDays(3), days.first().date)
        assertEquals(anchor.plusDays(3), days.last().date)
        assertEquals("SAT", days[3].dayName)
        assertEquals("12", days[3].dayNumber)
    }

    @Test
    fun crossesEndOfMonth() {
        val anchor = LocalDate.of(2026, 1, 30)

        val dates = calendarDays(anchor, anchor, anchor, locale).map { it.date }

        assertTrue(LocalDate.of(2026, 1, 31) in dates)
        assertTrue(LocalDate.of(2026, 2, 1) in dates)
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
        val selection = today.plusDays(2)

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
