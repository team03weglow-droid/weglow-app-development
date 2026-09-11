package com.example.weglow.domain.recommendation

import com.example.weglow.domain.model.Product
import com.example.weglow.domain.model.ProductRecommendation
import com.example.weglow.domain.model.RecommendationBasis
import com.example.weglow.domain.model.RecommendationResult
import com.example.weglow.domain.model.UserProfile

/**
 * Deterministic, explainable matching between a user's real profile/scan data and the real
 * Supabase catalog. Intentionally rule-based (no ML model, no network/database access) so
 * every recommendation can be traced back to a concrete, honest reason.
 *
 * The imported product dataset stores `Target_Skin_Type` / `Target_Concerns` as free-form
 * text with inconsistent casing and separators, so matching is done defensively: both sides
 * are normalized before comparison, and a signal only contributes to the score (and to the
 * displayed reasons) when the catalog text actually supports it. Nothing is ever invented.
 */
object RecommendationEngine {
    private const val MAX_RESULTS = 12
    private const val SKIN_TYPE_SCORE = 2
    private const val CONCERN_SCORE = 3
    private const val SENSITIVE_SCORE = 1

    private val SKIN_TYPE_KEYWORDS = listOf("oily", "dry", "combination", "sensitive", "normal")
    private val UNIVERSAL_SKIN_TYPE_PHRASES = listOf("all skin", "all types", "any skin", "every skin")

    /**
     * @param scanConcerns null means "no scan was used for this request" (profile-only);
     * an empty list means a scan was used but it found no concerns. This distinction is what
     * lets the UI say "Based on your profile" vs. "Based on your profile and recent scan".
     */
    fun recommend(
        profile: UserProfile?,
        scanConcerns: List<String>?,
        products: List<Product>,
    ): RecommendationResult {
        val normalizedSkin = normalizeSkinType(profile?.skinType)
        val concerns = scanConcerns.orEmpty().filter { it.isNotBlank() }.distinct()

        val recommendations = products
            .mapNotNull { product -> scoreProduct(product, normalizedSkin, concerns, profile) }
            .sortedWith(compareByDescending<ProductRecommendation> { it.score }.thenBy { it.product.name })
            .take(MAX_RESULTS)

        val hasSignal = normalizedSkin != null || concerns.isNotEmpty() || profile?.isSkinSensitive == true

        return RecommendationResult(
            basis = if (scanConcerns != null) RecommendationBasis.PROFILE_AND_SCAN else RecommendationBasis.PROFILE_ONLY,
            skinType = profile?.skinType,
            concerns = concerns,
            hasPersonalizationSignal = hasSignal,
            recommendations = recommendations,
        )
    }

    private fun scoreProduct(
        product: Product,
        normalizedSkin: String?,
        concerns: List<String>,
        profile: UserProfile?,
    ): ProductRecommendation? {
        var score = 0
        val reasons = mutableListOf<String>()

        if (normalizedSkin != null && productMatchesSkinType(product, normalizedSkin)) {
            score += SKIN_TYPE_SCORE
            reasons += "Matches your ${normalizedSkin.replaceFirstChar(Char::uppercase)} skin profile"
        }

        val matchedConcerns = concerns.filter { productMatchesConcern(product, it) }
        if (matchedConcerns.isNotEmpty()) {
            score += CONCERN_SCORE * matchedConcerns.size
            val labels = matchedConcerns.joinToString(", ") { it.trim().replaceFirstChar(Char::uppercase) }
            reasons += "Matches concerns detected in your scan: $labels"
        }

        if (profile?.isSkinSensitive == true && productSupportsSensitiveSkin(product)) {
            score += SENSITIVE_SCORE
            reasons += "Formulated for sensitive skin"
        }

        return if (score <= 0) null else ProductRecommendation(product, reasons, score)
    }

    private fun normalizeSkinType(raw: String?): String? {
        val lowered = raw?.lowercase() ?: return null
        return SKIN_TYPE_KEYWORDS.firstOrNull { lowered.contains(it) }
    }

    private fun productMatchesSkinType(product: Product, normalizedSkin: String): Boolean {
        val text = product.targetSkinType?.lowercase() ?: return false
        return text.contains(normalizedSkin) || UNIVERSAL_SKIN_TYPE_PHRASES.any(text::contains)
    }

    /** Collapses to letters only so "black heads" / "Black-Heads" / "blackheads" all agree. */
    private fun letterToken(raw: String): String = raw.lowercase().filter(Char::isLetter)

    private fun productMatchesConcern(product: Product, rawConcern: String): Boolean {
        val haystack = product.targetConcerns?.let(::letterToken) ?: return false
        val needle = letterToken(rawConcern)
        if (needle.isEmpty()) return false
        if (haystack.contains(needle)) return true
        val singularNeedle = needle.removeSuffix("s")
        return singularNeedle.length >= 3 && haystack.contains(singularNeedle)
    }

    /**
     * Sensitivity compatibility is only claimed when the catalog text itself says so - never
     * inferred from category or price. Absence of evidence is treated as no match.
     */
    private fun productSupportsSensitiveSkin(product: Product): Boolean {
        val text = listOfNotNull(product.targetSkinType, product.targetConcerns)
            .joinToString(" ") { it.lowercase() }
        return text.contains("sensitive")
    }
}
