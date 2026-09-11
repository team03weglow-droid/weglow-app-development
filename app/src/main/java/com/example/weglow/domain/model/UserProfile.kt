package com.example.weglow.domain.model

/** Domain representation of profile information. It is intentionally backend-agnostic. */
data class UserProfile(
    val id: String,
    val fullName: String? = null,
    val ageRange: String? = null,
    val skinType: String? = null,
    val gender: String? = null,
    val isSkinSensitive: Boolean? = null,
    /**
     * Stable Supabase Storage object path of the user's profile picture (for
     * example `<userId>/1699999999999.jpg`), persisted in the `profile_image_url`
     * column. It is deliberately a storage reference, never a short-lived signed
     * URL; authenticated access is resolved at display time.
     */
    val profileImagePath: String? = null,
    /** True only once every onboarding answer has been persisted successfully. */
    val onboardingCompleted: Boolean = false,
)
