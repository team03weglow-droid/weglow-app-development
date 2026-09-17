package com.example.weglow.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DestinationTest {

    private val allDestinations = listOf(
        Destination.Loading,
        Destination.Login,
        Destination.Signup,
        Destination.AgeSelection,
        Destination.SkinType,
        Destination.GenderSelection,
        Destination.SkinSensitivity,
        Destination.WelcomeIntro,
        Destination.Home,
        Destination.Discover,
        Destination.Scan,
        Destination.ScanResults,
        Destination.HairstyleResults,
        Destination.Recommendations,
        Destination.Routines,
        Destination.Profile,
        Destination.AccountSettings,
        Destination.SavedProducts,
        Destination.Cart,
        Destination.PrivacyPolicy,
        Destination.TermsConditions,
    )

    @Test
    fun privacyPolicyDestination_exists() {
        assertEquals("privacy_policy", Destination.PrivacyPolicy.route)
    }

    @Test
    fun termsConditionsDestination_exists() {
        assertEquals("terms_conditions", Destination.TermsConditions.route)
    }

    @Test
    fun legalDestinations_areDistinctFromEachOther() {
        assertNotEquals(Destination.PrivacyPolicy.route, Destination.TermsConditions.route)
    }

    // A NavHost silently mis-registers a screen if two destinations share a route string -
    // this guards every destination in the graph, not just the two just added, so the new
    // legal routes can never accidentally collide with an existing one.
    @Test
    fun everyDestination_hasAUniqueRoute() {
        val routes = allDestinations.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }
}
