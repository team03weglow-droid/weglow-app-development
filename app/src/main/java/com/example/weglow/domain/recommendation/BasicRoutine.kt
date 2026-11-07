package com.example.weglow.domain.recommendation

import com.example.weglow.domain.model.RoutinePlan
import com.example.weglow.domain.model.RoutineStep

/** General daily checklist using the user's own products, not catalog recommendations. */
object BasicRoutine {
    fun create() = RoutinePlan(
        morning = listOf("Cleanser", "Moisturizer", "Sunscreen").map(::step),
        evening = listOf("Cleanser", "Moisturizer").map(::step),
    )
    private fun step(label: String) = RoutineStep(label, null, listOf("Use your own product"), trackable = true)
}
