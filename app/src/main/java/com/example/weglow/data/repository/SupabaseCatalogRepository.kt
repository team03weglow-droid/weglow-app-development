package com.example.weglow.data.repository

import com.example.weglow.domain.model.Product
import com.example.weglow.domain.repository.CatalogRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class SupabaseCatalogRepository(
    private val client: SupabaseClient,
) : CatalogRepository {
    override suspend fun products(): Result<List<Product>> = runCatching {
        val rows = client.postgrest[PRODUCTS_TABLE]
            .select()
            .decodeList<JsonObject>()
        // This message reaches end users verbatim via Result.failure -> ViewModel ->
        // UiState.errorMessage (there is no separate error-mapping layer), so it must never
        // include internal details. The likely developer-facing cause - during setup or a
        // Supabase config change - is a missing/incorrect SELECT policy on `products`.
        check(rows.isNotEmpty()) {
            "Products are temporarily unavailable."
        }

        rows.mapNotNull { row -> row.toProductOrNull() }.also { products ->
            // As above: the real cause here (if it ever fires) is the imported dataset's
            // column names no longer matching the mapping in toProductOrNull() below - a
            // build-time/integration problem, not something to expose to a user.
            check(products.isNotEmpty()) {
                "Products are temporarily unavailable."
            }
        }
    }
}

internal fun JsonObject.toProductOrNull(): Product? {
    val id = text("product_id", "id", "product_code", "sku", "uniq_id", "unique_id")
        ?: return null
    val name = text("product_name", "name", "title", "product", "product_title")
        ?: return null
    // Price_LKR (and its known aliases) is the authoritative numeric price. price_text_raw is a
    // free-text column that is looked up separately and only used as a fallback - matchingElement()
    // resolves exact-name matches before normalized ones, so keeping price_text_raw in the same
    // candidate list as price_lkr let it win over Price_LKR whenever both existed on a row.
    val numericPrice = text(
        "price_lkr",
        "price",
        "lkr_price",
        "product_price",
        "selling_price",
        "sale_price",
    )
    val rawTextPrice = text("price_text_raw")
    val price = numericPrice ?: rawTextPrice

    return Product(
        id = id,
        name = name,
        priceLabel = price.toLkrLabel(),
        priceLkr = numericPrice.toLkrAmount() ?: rawTextPrice.toLkrAmount(),
        imageUrl = imageUrl(
            "image_url",
            "product_image_url",
            "product_image",
            "image",
            "images",
            "image_urls",
            "image_path",
            "image_link",
            "thumbnail",
            "thumbnail_url",
            "photo",
            "photo_url",
        ),
        description = text(
            "description",
            "product_description",
            "small_description",
            "short_description",
            "about_product",
        ),
        brandName = text("brand_name", "brand"),
        category = text("category", "product_category", "type"),
        ratingLabel = text("rating", "rating_label", "average_rating"),
        targetSkinType = text("target_skin_type", "skin_type_target", "suitable_skin_type"),
        targetConcerns = text("target_concerns", "concern", "concerns", "skin_concerns"),
        texture = text("texture", "product_texture"),
    )
}

private fun JsonObject.text(vararg keys: String): String? {
    return matchingElement(*keys)?.let(::primitiveText)
}

private fun JsonObject.imageUrl(vararg keys: String): String? =
    matchingElement(*keys)?.firstImageUrl()

private fun JsonObject.matchingElement(vararg keys: String): JsonElement? {
    keys.firstNotNullOfOrNull { key -> this[key] }?.let { return it }

    val normalizedKeys = keys.mapTo(mutableSetOf(), String::normalizedColumnName)
    return entries.firstOrNull { (columnName, _) ->
        columnName.normalizedColumnName() in normalizedKeys
    }?.value
}

private fun primitiveText(element: JsonElement?): String? =
    (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

private fun JsonElement.firstImageUrl(): String? = when (this) {
    is JsonArray -> firstNotNullOfOrNull(JsonElement::firstImageUrl)
    is JsonObject -> {
        val preferredKeys = setOf("url", "src", "image", "imageurl", "publicurl")
        entries.firstNotNullOfOrNull { (key, value) ->
            if (key.normalizedColumnName() in preferredKeys) value.firstImageUrl() else null
        } ?: values.firstNotNullOfOrNull(JsonElement::firstImageUrl)
    }
    is JsonPrimitive -> contentOrNull?.toImageUrl()
}

private fun String.toImageUrl(): String? {
    val cleaned = trim().replace("\\/", "/")
    val embeddedUrl = HTTPS_URL.find(cleaned)?.value
    return when {
        embeddedUrl != null -> embeddedUrl
        cleaned.startsWith("//") -> "https:$cleaned"
        cleaned.startsWith("data:image/") -> cleaned
        else -> cleaned.takeIf(String::isNotEmpty)
    }
}

private fun String.normalizedColumnName(): String =
    lowercase().filter(Char::isLetterOrDigit)

private fun String?.toLkrLabel(): String = when {
    isNullOrBlank() -> "Price unavailable"
    startsWith("LKR", ignoreCase = true) -> this
    else -> "LKR $this"
}

private fun String?.toLkrAmount(): Double? = this
    ?.replace(",", "")
    ?.let { PRICE_NUMBER.find(it)?.value }
    ?.toDoubleOrNull()

private const val PRODUCTS_TABLE = "products"
private val HTTPS_URL = Regex("https?://[^\\s\\\"'\\],}]+")
private val PRICE_NUMBER = Regex("\\d+(?:\\.\\d+)?")
