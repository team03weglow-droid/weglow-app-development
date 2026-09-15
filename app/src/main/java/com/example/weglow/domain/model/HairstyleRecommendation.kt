package com.example.weglow.domain.model

data class HairstyleRecommendation(
    val matchPercent: Int,
    val category: String,
    val title: String,
    val description: String,
    val imageUrl: String? = null,
)

data class HairstyleResult(
    val faceShape: String,
    val confidencePercent: Int,
    val traits: List<String>,
    val description: String,
    val recommendations: List<HairstyleRecommendation>,
)
