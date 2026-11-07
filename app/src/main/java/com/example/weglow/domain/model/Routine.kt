package com.example.weglow.domain.model

/**
 * One slot in a routine (for example "Cleanser"). [product] is null when no real catalog
 * product could be matched. Explicit [RoutineStep.trackable] steps can instead
 * use the user's own product; a legacy unmatched slot stays untracked.
 */
@kotlinx.serialization.Serializable
data class RoutineStep(
    val label: String,
    val product: Product?,
    val reasons: List<String>,
    /** Explicit product-independent task. Legacy unmatched slots remain untracked. */
    val trackable: Boolean = false,
) {
    val isTrackable: Boolean get() = trackable || product != null
}

@kotlinx.serialization.Serializable
data class RoutinePlan(
    val morning: List<RoutineStep>,
    val evening: List<RoutineStep>,
)
