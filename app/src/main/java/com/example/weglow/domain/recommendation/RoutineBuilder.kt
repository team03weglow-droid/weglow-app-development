package com.example.weglow.domain.recommendation

import com.example.weglow.domain.model.Product
import com.example.weglow.domain.model.ProductRecommendation
import com.example.weglow.domain.model.RoutinePlan
import com.example.weglow.domain.model.RoutineStep

/**
 * Organizes real, already-scored recommendations (see [RecommendationEngine]) into a basic
 * morning/evening routine, using only the `Category` text that is actually present in the
 * catalog. A step whose category has no matching product is returned with a null product
 * rather than inventing one - the UI is responsible for showing that as a missing/optional
 * step.
 *
 * The step categories below (Cleanser, Moisturizer, Sunscreen, Treatment) are a minimal,
 * widely-applicable skincare structure, not a claim about the exact taxonomy of the imported
 * dataset - each step matches by keyword against whatever free-form `Category` text a product
 * has, so it degrades gracefully if a category is absent from the catalog.
 */
object RoutineBuilder {
    private data class Slot(val label: String, val keywords: List<String>)

    private val CLEANSER = Slot("Cleanser", listOf("cleans", "face wash", "wash"))
    private val MOISTURIZER = Slot("Moisturizer", listOf("moistur", "cream", "lotion"))
    private val SUNSCREEN = Slot("Sunscreen", listOf("sunscreen", "spf", "sun screen", "sun protect"))
    private val TREATMENT = Slot("Treatment", listOf("serum", "treatment", "essence", "ampoule", "spot"))

    private val MORNING = listOf(CLEANSER, MOISTURIZER, SUNSCREEN)
    private val EVENING = listOf(CLEANSER, TREATMENT, MOISTURIZER)

    fun build(recommendations: List<ProductRecommendation>, catalog: List<Product>): RoutinePlan {
        val usedInMorning = mutableSetOf<String>()
        val usedInEvening = mutableSetOf<String>()
        return RoutinePlan(
            morning = MORNING.map { slot -> pick(slot, recommendations, catalog, usedInMorning) },
            evening = EVENING.map { slot -> pick(slot, recommendations, catalog, usedInEvening) },
        )
    }

    private fun pick(
        slot: Slot,
        recommendations: List<ProductRecommendation>,
        catalog: List<Product>,
        usedInThisPeriod: MutableSet<String>,
    ): RoutineStep {
        val personalized = recommendations
            .filter { it.product.id !in usedInThisPeriod && matchesCategory(it.product, slot) }
            .maxByOrNull { it.score }
        if (personalized != null) {
            usedInThisPeriod += personalized.product.id
            return RoutineStep(slot.label, personalized.product, personalized.reasons)
        }

        // No personalized match for this category: fall back to a real catalog product so the
        // routine still has basic category coverage, but never claim a personalized reason.
        val fallback = catalog
            .filter { it.id !in usedInThisPeriod && matchesCategory(it, slot) }
            .minByOrNull { it.name }
        if (fallback != null) {
            usedInThisPeriod += fallback.id
            return RoutineStep(slot.label, fallback, listOf("General pick for this step"))
        }

        return RoutineStep(slot.label, null, emptyList())
    }

    private fun matchesCategory(product: Product, slot: Slot): Boolean {
        val category = product.category?.lowercase() ?: return false
        return slot.keywords.any(category::contains)
    }
}
