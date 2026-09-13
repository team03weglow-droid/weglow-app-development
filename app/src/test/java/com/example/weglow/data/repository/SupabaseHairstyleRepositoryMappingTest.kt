package com.example.weglow.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseHairstyleRepositoryMappingTest {
    @Test fun mapsLiveStyleFieldsAndImageUrl() {
        val row = Json.parseToJsonElement(
            """{"id":1,"hairstyle_name":"Textured Crop","gender":"Male","face_shape":"Oval","hair_length":"Short","image_url":"https://example.com/crop.jpg"}"""
        ).jsonObject

        val style = row.toHairstyleRecommendationOrNull()

        assertNotNull(style)
        assertEquals("Textured Crop", style?.title)
        assertEquals("Short", style?.category)
        assertEquals("Hair length: Short", style?.description)
        assertEquals("https://example.com/crop.jpg", style?.imageUrl)
    }

    @Test fun filtersByStoredShapeAndProfileGender() {
        val rows = Json.parseToJsonElement(
            """[
                {"hairstyle_name":"Round Men","gender":"Male","face_shape":"Round","hair_length":"Short"},
                {"hairstyle_name":"Round Women","gender":"Female","face_shape":"Round","hair_length":"Long"},
                {"hairstyle_name":"Oval Men","gender":"Male","face_shape":"Oval","hair_length":"Short"}
            ]"""
        ).jsonArray.map { it.jsonObject }

        assertEquals(listOf("Round Men"), matchingHairstyles(rows, "round", "Male").map { it.title })
        assertEquals(listOf("Round Men", "Round Women"), matchingHairstyles(rows, "Round", null).map { it.title })
        assertTrue(matchingHairstyles(rows, "Square", null).isEmpty())
    }
}
