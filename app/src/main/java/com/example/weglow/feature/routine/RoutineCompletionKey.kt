package com.example.weglow.feature.routine

import java.time.LocalDate

/**
 * Builds the key `RoutinesScreen` uses to track one routine step's completion checkbox for a
 * specific selected date and period. Shared by Home and the device-local routine journal.
 * Always pass the original plan index, not an index after filtering unmatched products.
 * [date] is the complete ISO-8601 calendar date, [isMorning] keeps Morning and Evening independent
 * of each other, [stepIndex] is the step's position within that period, and [productId]
 * disambiguates steps so two different steps with no matched product (`null`) still get distinct
 * keys instead of colliding.
 */
fun routineStepKey(
    date: LocalDate,
    isMorning: Boolean,
    stepIndex: Int,
    productId: String?,
): String = "${date}-${if (isMorning) "AM" else "PM"}-$stepIndex-$productId"
