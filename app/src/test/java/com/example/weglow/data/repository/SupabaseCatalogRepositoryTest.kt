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
        assertEquals(4800.0, product.priceLkr)
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
        assertEquals(3500.0, product.priceLkr)
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
    fun productRow_mapsRealDatasetColumnCasing() {
        // Mirrors the live Supabase schema: Product_ID, Target_Skin_Type, Target_Concerns, etc.
        val product = buildJsonObject {
            put("Product_ID", "p-99")
            put("Product_Name", "Purifying Clay Mask")
            put("Brand", "WeGlow Labs")
            put("Category", "Treatment")
            put("Price_LKR", "2500")
            put("Target_Skin_Type", "Oily, Combination")
            put("Target_Concerns", "Blackheads; Open Pores")
            put("Texture", "Gel")
        }.toProductOrNull()

        requireNotNull(product)
        assertEquals("p-99", product.id)
        assertEquals("Oily, Combination", product.targetSkinType)
        assertEquals("Blackheads; Open Pores", product.targetConcerns)
        assertEquals("Gel", product.texture)
    }

    @Test
    fun productRow_withoutTargetFields_leavesThemNull() {
        val product = buildJsonObject {
            put("product_id", "p-1")
            put("product_name", "Basic Cleanser")
        }.toProductOrNull()

        requireNotNull(product)
        assertNull(product.targetSkinType)
        assertNull(product.targetConcerns)
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

    @Test
    fun productRow_withBlankPriceTextRaw_usesNumericPriceLkr() {
        // Live-shaped row: Price_LKR = 2500, price_text_raw = "".
        val product = buildJsonObject {
            put("Product_ID", "p-30")
            put("Product_Name", "Barrier Repair Cream")
            put("Price_LKR", 2500)
            put("price_text_raw", "")
        }.toProductOrNull()

        requireNotNull(product)
        assertEquals(2500.0, product.priceLkr)
    }

    @Test
    fun productRow_withConflictingPriceTextRaw_numericPriceLkrWins() {
        val product = buildJsonObject {
            put("Product_ID", "p-31")
            put("Product_Name", "Soothing Toner")
            put("Price_LKR", 2500)
            put("price_text_raw", "9999")
        }.toProductOrNull()

        requireNotNull(product)
        assertEquals(2500.0, product.priceLkr)
    }

    @Test
    fun productRow_withDecimalPriceLkr_parsesDecimalValue() {
        val product = buildJsonObject {
            put("Product_ID", "p-32")
            put("Product_Name", "Exfoliating Toner")
            put("Price_LKR", "2499.50")
            put("price_text_raw", "")
        }.toProductOrNull()

        requireNotNull(product)
        assertEquals(2499.50, product.priceLkr)
    }

    @Test
    fun productRow_withValidPriceLkrAndNonNumericPriceTextRaw_usesNumericPriceLkr() {
        val product = buildJsonObject {
            put("Product_ID", "p-33")
            put("Product_Name", "Clarifying Gel")
            put("Price_LKR", 3200)
            put("price_text_raw", "Contact for price")
        }.toProductOrNull()

        requireNotNull(product)
        assertEquals(3200.0, product.priceLkr)
    }

    @Test
    fun productRow_withoutPriceLkr_fallsBackToPriceTextRaw() {
        val product = buildJsonObject {
            put("Product_ID", "p-34")
            put("Product_Name", "Mineral Sunscreen")
            put("price_text_raw", "1800")
        }.toProductOrNull()

        requireNotNull(product)
        assertEquals(1800.0, product.priceLkr)
    }
}
