package com.example.weglow.feature.routine

import java.time.LocalDate

/**
 * Builds the key `RoutinesScreen` uses to track one routine step's completion checkbox for a
 * specific selected date and period, for the current app session only.
 *
 * No routine-history persistence exists anywhere in this app (no Supabase table, no local
 * database), so this deliberately never claims to survive an app restart - it only has to keep
 * checkmarks from leaking between dates and between Morning/Evening within one running session.
 * [date] is the complete ISO-8601 calendar date, [isMorning] keeps Morning and Evening independent
 * of each other, [stepIndex] is the step's position within that period, and [productId]
 * disambiguates steps so two different steps with no matched product (`null`) still get distinct
 * keys instead of colliding.
 */
internal fun routineStepKey(
    date: LocalDate,
    isMorning: Boolean,
    stepIndex: Int,
    productId: String?,
): String = "${date}-${if (isMorning) "AM" else "PM"}-$stepIndex-$productId"
