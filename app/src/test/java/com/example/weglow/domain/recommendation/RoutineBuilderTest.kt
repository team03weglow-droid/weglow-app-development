package com.example.weglow.domain.recommendation

import com.example.weglow.domain.model.Product
import com.example.weglow.domain.model.ProductRecommendation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineBuilderTest {
    private fun product(id: String, category: String) =
        Product(id = id, name = id, priceLabel = "LKR 1000", imageUrl = null, category = category)

    @Test
    fun picksHighestScoredPersonalizedProductPerCategory() {
        val cleanser = ProductRecommendation(product("c1", "Face Wash"), listOf("Matches your Oily skin profile"), score = 2)
        val moisturizer = ProductRecommendation(product("m1", "Daily Moisturizer"), listOf("Matches your Oily skin profile"), score = 2)
        val sunscreen = ProductRecommendation(product("s1", "SPF 50 Sunscreen"), listOf("Matches your Oily skin profile"), score = 2)

        val plan = RoutineBuilder.build(listOf(cleanser, moisturizer, sunscreen), catalog = emptyList())

        assertEquals("c1", plan.morning[0].product?.id)
        assertEquals("m1", plan.morning[1].product?.id)
        assertEquals("s1", plan.morning[2].product?.id)
    }

    @Test
    fun eveningRoutineUsesTreatmentInsteadOfSunscreen() {
        val cleanser = ProductRecommendation(product("c1", "Cleansing Gel"), emptyList(), score = 1)
        val serum = ProductRecommendation(product("t1", "Repair Serum"), emptyList(), score = 1)
        val moisturizer = ProductRecommendation(product("m1", "Night Cream"), emptyList(), score = 1)

        val plan = RoutineBuilder.build(listOf(cleanser, serum, moisturizer), catalog = emptyList())

        assertEquals(listOf("Cleanser", "Treatment", "Moisturizer"), plan.evening.map { it.label })
        assertEquals("t1", plan.evening[1].product?.id)
    }

    @Test
    fun missingCategoryAndNoCatalogFallback_isReturnedAsNullProductNotInvented() {
        val plan = RoutineBuilder.build(recommendations = emptyList(), catalog = emptyList())

        plan.morning.forEach { step ->
            assertNull(step.product)
            assertTrue(step.reasons.isEmpty())
        }
    }

    @Test
    fun noPersonalizedMatch_fallsBackToRealCatalogProductWithHonestReason() {
        val catalog = listOf(product("c1", "Gentle Face Wash"))

        val plan = RoutineBuilder.build(recommendations = emptyList(), catalog = catalog)

        val cleanserStep = plan.morning.first { it.label == "Cleanser" }
        assertEquals("c1", cleanserStep.product?.id)
        assertEquals(listOf("General pick for this step"), cleanserStep.reasons)
    }

    @Test
    fun sameProductIsNotReusedTwiceWithinTheSamePeriod() {
        // A product whose category text could satisfy both cleanser and moisturizer keywords
        // must still only fill one slot in the same period.
        val ambiguous = ProductRecommendation(product("a1", "Cream Cleanser"), emptyList(), score = 5)

        val plan = RoutineBuilder.build(listOf(ambiguous), catalog = emptyList())

        val usedIds = plan.morning.mapNotNull { it.product?.id }
        assertEquals(usedIds.size, usedIds.distinct().size)
    }
}
