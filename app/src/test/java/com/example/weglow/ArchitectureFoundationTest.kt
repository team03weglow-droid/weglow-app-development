package com.example.weglow

import com.example.weglow.data.model.toProfileRow
import com.example.weglow.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class ArchitectureFoundationTest {
    @Test
    fun domainProfile_mapsToDataRow_withoutBackendDependencyInDomain() {
        val profile = UserProfile(
            id = "user-1",
            fullName = "Test User",
            ageRange = "14 - 25",
            skinType = "Combination",
            gender = "Prefer not to say",
            isSkinSensitive = true,
            onboardingCompleted = true,
        )

        val row = profile.toProfileRow()

        assertEquals("user-1", row.id)
        assertEquals("Test User", row.full_name)
        assertEquals("Combination", row.skin_type)
        assertEquals(true, row.is_skin_sensitive)
        assertEquals(true, row.onboarding_completed)
    }
}
