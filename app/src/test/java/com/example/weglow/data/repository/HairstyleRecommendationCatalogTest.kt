package com.example.weglow.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HairstyleRecommendationCatalogTest {
    @Test fun detectedShapeIsIncludedAndRecommendationsAreRanked() {
        val result = recommendationResult("Square", 87, "Male")

        assertEquals("Square", result.faceShape)
        assertEquals(87, result.confidencePercent)
        assertEquals(4, result.recommendations.size)
        assertTrue(result.recommendations.zipWithNext().all { (left, right) ->
            left.matchPercent > right.matchPercent
        })
    }

    @Test fun everyModelClassHasRecommendationCopy() {
        listOf("Heart", "Oblong", "Oval", "Round", "Square").forEach { shape ->
            val result = recommendationResult(shape, 50, "Female")
            assertEquals(3, result.traits.size)
            assertEquals(4, result.recommendations.map { it.title }.distinct().size)
        }
    }
}
