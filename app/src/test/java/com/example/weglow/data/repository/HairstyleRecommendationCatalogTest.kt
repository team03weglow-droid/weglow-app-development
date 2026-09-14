package com.example.weglow.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HairstyleRecommendationCatalogTest {
    @Test fun classificationIncludesDetectedShapeAndConfidence() {
        val result = recommendationResult("Square", 87, "Male")

        assertEquals("Square", result.faceShape)
        assertEquals(87, result.confidencePercent)
    }

    @Test fun localClassificationDoesNotProduceACompetingHairstyleCatalog() {
        // Supabase is the single source of truth for hairstyle recommendations; the local
        // classifier must only describe the detected face shape, never fabricate styles.
        listOf("Heart", "Oblong", "Oval", "Round", "Square").forEach { shape ->
            listOf("Male", "Female", null).forEach { gender ->
                val result = recommendationResult(shape, 50, gender)
                assertTrue(result.recommendations.isEmpty())
            }
        }
    }

    @Test fun everyModelClassHasDescriptiveTraitsAndDescription() {
        listOf("Heart", "Oblong", "Oval", "Round", "Square").forEach { shape ->
            val result = recommendationResult(shape, 50, "Female")
            assertEquals(3, result.traits.size)
            assertTrue(result.description.isNotBlank())
        }
    }
}
