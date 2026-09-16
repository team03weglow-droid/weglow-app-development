package com.example.weglow.domain.model

data class Product(
    val id: String,
    val name: String,
    val priceLabel: String,
    val imageUrl: String?,
    val priceLkr: Double? = null,
    val description: String? = null,
    val brandName: String? = null,
    val category: String? = null,
    val ratingLabel: String? = null,
    /** Raw `Target_Skin_Type` text from the catalog. Free-form; never assume a fixed format. */
    val targetSkinType: String? = null,
    /** Raw `Target_Concerns` text from the catalog. Free-form; never assume a fixed format. */
    val targetConcerns: String? = null,
    val texture: String? = null,
    /**
     * The real `products."No"` smallint primary key - the only column Saved Products, Cart, and
     * any future Orders may use as a foreign key into the catalog. [id] above is a flexible,
     * display/matching-oriented identifier (often the catalog's `Product_ID` text code, e.g.
     * "SPA_0001") and must never be parsed into this or used as a database relationship. Null
     * only if a row's "No" column could not be read, in which case that product cannot be
     * saved or added to cart - callers must treat null here as "not save/cart-able", never
     * invent a number.
     */
    val catalogNo: Int? = null,
)
