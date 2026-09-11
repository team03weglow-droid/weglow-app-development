package com.example.weglow.domain.model

/** Whether a [RecommendationResult] was informed only by the stored profile, or also by a scan. */
enum class RecommendationBasis { PROFILE_ONLY, PROFILE_AND_SCAN }

/** A real catalog [product] paired with the explainable, non-medical reasons it was suggested. */
data class ProductRecommendation(
    val product: Product,
    val reasons: List<String>,
    val score: Int,
)

data class RecommendationResult(
    val basis: RecommendationBasis,
    val skinType: String?,
    val concerns: List<String>,
    /**
     * False when neither a usable skin type, a sensitivity flag, nor scan concerns were
     * available to personalize with. Lets the UI distinguish "nothing matched" from
     * "there was nothing to match against".
     */
    val hasPersonalizationSignal: Boolean,
    val recommendations: List<ProductRecommendation>,
)
