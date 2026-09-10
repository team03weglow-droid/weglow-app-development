package com.example.weglow.data.repository

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupabaseCatalogRepositoryTest {
    @Test
    fun productRow_mapsDatabaseColumnsToDomainProduct() {
        val product = buildJsonObject {
            put("product_id", 502)
            put("product_name", "Hydrating Serum")
            put("image_url", "https://example.com/serum.jpg")
            put("price_lkr", 4800)
            put("small_description", "Lightweight daily hydration")
            put("brand_name", "WeGlow")
            put("category", "Serum")
            put("rating", 4.8)
        }.toProductOrNull()

        requireNotNull(product)
        assertEquals("502", product.id)
        assertEquals("Hydrating Serum", product.name)
        assertEquals("LKR 4800", product.priceLabel)
        assertEquals("https://example.com/serum.jpg", product.imageUrl)
        assertEquals("Lightweight daily hydration", product.description)
        assertEquals("WeGlow", product.brandName)
        assertEquals("Serum", product.category)
        assertEquals("4.8", product.ratingLabel)
    }

    @Test
    fun productRow_withoutIdentityOrName_isIgnored() {
        assertNull(buildJsonObject { put("price_lkr", 1000) }.toProductOrNull())
    }

    @Test
    fun productRow_mapsCaseAndSeparatorVariants() {
        val product = buildJsonObject {
            put("Product ID", "p-20")
            put("productName", "Vitamin C Cream")
            put("priceLKR", "3,500")
            put("Product Image", "https://example.com/cream.jpg")
            put("Brand Name", "Glow Lab")
            put("Product Description", "Brightening face cream")
        }.toProductOrNull()

        requireNotNull(product)
        assertEquals("p-20", product.id)
        assertEquals("Vitamin C Cream", product.name)
        assertEquals("LKR 3,500", product.priceLabel)
        assertEquals("https://example.com/cream.jpg", product.imageUrl)
        assertEquals("Glow Lab", product.brandName)
        assertEquals("Brightening face cream", product.description)
    }

    @Test
    fun productRow_extractsFirstImageFromImportedImageArray() {
        val product = buildJsonObject {
            put("Product ID", "p-21")
            put("Product Name", "Night Cream")
            put("Images", buildJsonArray {
                add(JsonPrimitive("https://example.com/night-cream.jpg"))
                add(JsonPrimitive("https://example.com/night-cream-2.jpg"))
            })
        }.toProductOrNull()

        assertEquals("https://example.com/night-cream.jpg", product?.imageUrl)
    }

    @Test
    fun productRow_extractsUrlFromJsonWrappedString() {
        val product = buildJsonObject {
            put("id", "p-22")
            put("name", "Face Wash")
            put("image_urls", "[\"https:\\/\\/example.com\\/face-wash.jpg\"]")
        }.toProductOrNull()

        assertEquals("https://example.com/face-wash.jpg", product?.imageUrl)
    }
}
