package com.example.weglow.domain.model

/**
 * One slot in a routine (for example "Cleanser"). [product] is null when no real catalog
 * product could be matched to this category, which the UI must show as a missing/optional
 * step rather than inventing one.
 */
data class RoutineStep(
    val label: String,
    val product: Product?,
    val reasons: List<String>,
)

data class RoutinePlan(
    val morning: List<RoutineStep>,
    val evening: List<RoutineStep>,
)
