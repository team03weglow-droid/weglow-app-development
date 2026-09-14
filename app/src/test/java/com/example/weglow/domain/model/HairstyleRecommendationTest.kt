package com.example.weglow.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Guards the architectural invariant that the hairstyle domain model carries only
 * hairstyle data, never an Android presentation/resource concept such as a
 * drawable resource ID. Presentation fallback imagery is a UI-layer decision.
 */
class HairstyleRecommendationTest {
    @Test fun constructsFromDataAloneWithoutAnyAndroidResourceId() {
        val recommendation = HairstyleRecommendation(
            matchPercent = 91,
            category = "Short",
            title = "Textured Crop",
            description = "Hair length: Short",
            imageUrl = "https://example.com/crop.jpg",
        )

        assertEquals("Textured Crop", recommendation.title)
        assertEquals("https://example.com/crop.jpg", recommendation.imageUrl)
    }

    @Test fun hasNoDrawableResourceField() {
        val fieldNames = HairstyleRecommendation::class.java.declaredFields.map { it.name }
        assertFalse("Domain model must not carry an Android drawable resource field", fieldNames.contains("imageRes"))
    }
}
