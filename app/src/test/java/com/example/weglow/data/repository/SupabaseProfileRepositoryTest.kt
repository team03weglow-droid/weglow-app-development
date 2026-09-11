package com.example.weglow.data.repository

import com.example.weglow.data.model.ProfileRow
import com.example.weglow.data.model.toProfileRow
import com.example.weglow.data.model.toUserProfile
import com.example.weglow.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the pure decision + mapping logic used by [SupabaseProfileRepository]
 * without needing a live Supabase client.
 */
class SupabaseProfileRepositoryTest {

    // 6. Onboarding completion is decided by the onboarding_completed flag, not by
    //    a profiles row merely existing.
    @Test
    fun noRow_isNotCompleted() {
        assertFalse(emptyList<ProfileRow>().isOnboardingCompleted())
    }

    @Test
    fun rowExistsButFlagFalse_isNotCompleted() {
        val row = ProfileRow(
            id = "user-1",
            age_range = "14 - 25",
            gender = "Female",
            is_skin_sensitive = true,
            onboarding_completed = false,
        )
        assertFalse(listOf(row).isOnboardingCompleted())
    }

    @Test
    fun rowWithFlagTrue_isCompleted() {
        val row = ProfileRow(id = "user-1", onboarding_completed = true)
        assertTrue(listOf(row).isOnboardingCompleted())
    }

    // 7. ProfileRow -> UserProfile mapping (and round-trip) is faithful.
    @Test
    fun profileRow_mapsToUserProfile() {
        val row = ProfileRow(
            id = "user-1",
            full_name = "Sam Real",
            age_range = "25 - 35",
            skin_type = "Combination",
            gender = "Female",
            is_skin_sensitive = false,
            onboarding_completed = true,
        )

        val profile = row.toUserProfile()

        assertEquals(
            UserProfile(
                id = "user-1",
                fullName = "Sam Real",
                ageRange = "25 - 35",
                skinType = "Combination",
                gender = "Female",
                isSkinSensitive = false,
                onboardingCompleted = true,
            ),
            profile,
        )
    }

    @Test
    fun userProfile_roundTripsThroughProfileRow() {
        val profile = UserProfile(
            id = "user-1",
            fullName = "Sam Real",
            ageRange = "25 - 35",
            skinType = null,
            gender = "Male",
            isSkinSensitive = true,
            onboardingCompleted = true,
        )

        assertEquals(profile, profile.toProfileRow().toUserProfile())
    }

    // Phase 6: the profile-picture storage reference round-trips through the
    // profile_image_url column without leaking Supabase types into the domain.
    @Test
    fun profileImagePath_roundTripsThroughProfileImageUrlColumn() {
        val profile = UserProfile(
            id = "user-1",
            fullName = "Sam Real",
            profileImagePath = "user-1/1720000000000.jpg",
            onboardingCompleted = true,
        )

        val row = profile.toProfileRow()
        assertEquals("user-1/1720000000000.jpg", row.profile_image_url)
        assertEquals(profile, row.toUserProfile())
    }

    @Test
    fun profileRow_withoutImageUrl_mapsToNullPath() {
        assertNull(ProfileRow(id = "user-1").toUserProfile().profileImagePath)
    }

    @Test
    fun defaultProfileRow_isNotCompleted() {
        assertNull(ProfileRow(id = "user-1").skin_type)
        assertFalse(ProfileRow(id = "user-1").onboarding_completed)
        assertFalse(ProfileRow(id = "user-1").toUserProfile().onboardingCompleted)
    }
}
