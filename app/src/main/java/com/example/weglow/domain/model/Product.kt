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
)
