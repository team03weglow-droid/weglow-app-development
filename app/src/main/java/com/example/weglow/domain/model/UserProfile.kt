package com.example.weglow.domain.model

/** Domain representation of profile information. It is intentionally backend-agnostic. */
data class UserProfile(
    val id: String,
    val fullName: String? = null,
    val ageRange: String? = null,
    val skinType: String? = null,
    val gender: String? = null,
    val isSkinSensitive: Boolean? = null,
    /** True only once every onboarding answer has been persisted successfully. */
    val onboardingCompleted: Boolean = false,
)
