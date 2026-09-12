package com.example.weglow.feature.routine

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class CalendarDay(
    val date: LocalDate,
    val dayNumber: String,
    val dayName: String,
    val isToday: Boolean,
    val isSelected: Boolean,
)

/** Builds the Sunday-through-Saturday week containing [anchorDate]. */
internal fun calendarDays(
    anchorDate: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    locale: Locale = Locale.getDefault(),
): List<CalendarDay> {
    val daysSinceSunday = anchorDate.dayOfWeek.value % 7
    val firstDate = anchorDate.minusDays(daysSinceSunday.toLong())

    return List(7) { offset ->
        val date = firstDate.plusDays(offset.toLong())
        CalendarDay(
            date = date,
            dayNumber = date.dayOfMonth.toString(),
            dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale),
            isToday = date == today,
            isSelected = date == selectedDate,
        )
    }
}
