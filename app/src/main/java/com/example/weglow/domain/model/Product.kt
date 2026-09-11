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
)
