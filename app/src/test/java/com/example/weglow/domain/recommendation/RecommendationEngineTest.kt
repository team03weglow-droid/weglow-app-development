package com.example.weglow.domain.recommendation

import com.example.weglow.domain.model.Product
import com.example.weglow.domain.model.RecommendationBasis
import com.example.weglow.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {
    private fun product(
        id: String,
        name: String = id,
        targetSkinType: String? = null,
        targetConcerns: String? = null,
    ) = Product(id = id, name = name, priceLabel = "LKR 1000", imageUrl = null, targetSkinType = targetSkinType, targetConcerns = targetConcerns)

    private fun profile(skinType: String? = null, sensitive: Boolean? = null) =
        UserProfile(id = "u1", skinType = skinType, isSkinSensitive = sensitive)

    @Test
    fun skinTypeMatch_isCaseAndFormatInsensitive() {
        val oily = product("1", targetSkinType = "Oily, Combination")
        val dry = product("2", targetSkinType = "DRY SKIN")

        val result = RecommendationEngine.recommend(profile(skinType = "Oily"), null, listOf(oily, dry))

        assertEquals(listOf("1"), result.recommendations.map { it.product.id })
        assertTrue(result.recommendations.first().reasons.any { it.contains("Oily", ignoreCase = true) })
    }

    @Test
    fun onboardingSkinTypeLabel_normalizesToCoreKeyword() {
        // Onboarding persists "Dry & Tight", not the bare word "Dry".
        val dryProduct = product("1", targetSkinType = "Dry")

        val result = RecommendationEngine.recommend(profile(skinType = "Dry & Tight"), null, listOf(dryProduct))

        assertEquals(listOf("1"), result.recommendations.map { it.product.id })
    }

    @Test
    fun universalSkinTypePhrase_matchesAnyUserSkinType() {
        val universal = product("1", targetSkinType = "Suitable for all skin types")

        val result = RecommendationEngine.recommend(profile(skinType = "Sensitive"), null, listOf(universal))

        assertEquals(listOf("1"), result.recommendations.map { it.product.id })
    }

    @Test
    fun concernMatch_isInsensitiveToSpacingAndCasing() {
        // Model label is "black heads"; catalog text may say "Blackheads".
        val product = product("1", targetConcerns = "Blackheads, Oily Skin")

        val result = RecommendationEngine.recommend(null, listOf("black heads"), listOf(product))

        assertEquals(listOf("1"), result.recommendations.map { it.product.id })
        assertEquals(RecommendationBasis.PROFILE_AND_SCAN, result.basis)
    }

    @Test
    fun concernMatch_toleratesPluralMismatch() {
        val product = product("1", targetConcerns = "Papule")

        val result = RecommendationEngine.recommend(null, listOf("papules"), listOf(product))

        assertEquals(listOf("1"), result.recommendations.map { it.product.id })
    }

    @Test
    fun multipleDetectedConcerns_eachContributeAndAreExplained() {
        val product = product("1", targetConcerns = "Blackheads, Whiteheads")

        val result = RecommendationEngine.recommend(null, listOf("black heads", "whiteheads", "freckles"), listOf(product))

        // The reason echoes the raw detected label (as the model reported it), not the
        // catalog's own spelling of the concern.
        val recommendation = result.recommendations.single()
        assertTrue(recommendation.reasons.single().contains("Black heads", ignoreCase = true))
        assertTrue(recommendation.reasons.single().contains("Whiteheads", ignoreCase = true))
    }

    @Test
    fun sensitiveClaim_onlyAppliesWhenCatalogTextSupportsIt() {
        val supported = product("1", targetSkinType = "Sensitive")
        val unsupported = product("2", targetSkinType = "Oily")

        val result = RecommendationEngine.recommend(profile(sensitive = true), null, listOf(supported, unsupported))

        val ids = result.recommendations.map { it.product.id }
        assertTrue(ids.contains("1"))
        assertFalse(ids.contains("2"))
    }

    @Test
    fun noScanPerformed_basisIsProfileOnly() {
        val result = RecommendationEngine.recommend(profile(skinType = "Oily"), null, listOf(product("1", targetSkinType = "Oily")))
        assertEquals(RecommendationBasis.PROFILE_ONLY, result.basis)
    }

    @Test
    fun scanPerformedWithNoConcerns_basisIsProfileAndScan() {
        val result = RecommendationEngine.recommend(profile(skinType = "Oily"), emptyList(), listOf(product("1", targetSkinType = "Oily")))
        assertEquals(RecommendationBasis.PROFILE_AND_SCAN, result.basis)
        assertTrue(result.concerns.isEmpty())
    }

    @Test
    fun noProfileAndNoScan_hasNoPersonalizationSignal() {
        val result = RecommendationEngine.recommend(null, null, listOf(product("1", targetSkinType = "Oily")))
        assertFalse(result.hasPersonalizationSignal)
        assertTrue(result.recommendations.isEmpty())
    }

    @Test
    fun noMatchingProducts_returnsEmptyRecommendationsNotFabricatedOnes() {
        val result = RecommendationEngine.recommend(
            profile(skinType = "Oily"),
            null,
            listOf(product("1", targetSkinType = "Dry")),
        )
        assertTrue(result.recommendations.isEmpty())
    }

    @Test
    fun emptyCatalog_returnsEmptyRecommendations() {
        val result = RecommendationEngine.recommend(profile(skinType = "Oily"), listOf("papules"), emptyList())
        assertTrue(result.recommendations.isEmpty())
    }

    @Test
    fun higherScoringProduct_isRankedFirst() {
        val skinOnly = product("1", targetSkinType = "Oily")
        val skinAndConcern = product("2", targetSkinType = "Oily", targetConcerns = "Blackheads")

        val result = RecommendationEngine.recommend(profile(skinType = "Oily"), listOf("black heads"), listOf(skinOnly, skinAndConcern))

        assertEquals(listOf("2", "1"), result.recommendations.map { it.product.id })
    }

    @Test
    fun unrelatedConcern_doesNotMatchAndProductIsExcluded() {
        // A detected concern that shares no letters with the catalog's Target_Concerns text
        // must never contribute score or appear as a false match.
        val product = product("1", targetConcerns = "Blackheads")

        val result = RecommendationEngine.recommend(null, listOf("freckles"), listOf(product))

        assertTrue(result.recommendations.isEmpty())
    }

    @Test
    fun moreThanTwelveMatches_areCappedAtTwelve() {
        // 20 equally-scoring products; only the top 12 (by the sort's tie-break) may be returned.
        val products = (1..20).map { index ->
            product(id = "p$index", name = "Product %02d".format(index), targetSkinType = "Oily")
        }

        val result = RecommendationEngine.recommend(profile(skinType = "Oily"), null, products)

        assertEquals(12, result.recommendations.size)
    }

    @Test
    fun equalScoringProducts_tieBreakAlphabeticallyByName() {
        val zebra = product("1", name = "Zebra Cream", targetSkinType = "Oily")
        val apple = product("2", name = "Apple Cream", targetSkinType = "Oily")
        val mango = product("3", name = "Mango Cream", targetSkinType = "Oily")

        val result = RecommendationEngine.recommend(profile(skinType = "Oily"), null, listOf(zebra, apple, mango))

        assertEquals(listOf("Apple Cream", "Mango Cream", "Zebra Cream"), result.recommendations.map { it.product.name })
    }
}
