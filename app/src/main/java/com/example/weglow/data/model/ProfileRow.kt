package com.example.weglow.data.model

import com.example.weglow.domain.model.UserProfile
import kotlinx.serialization.Serializable

@Serializable
data class ProfileRow(
    val id: String,
    val full_name: String? = null,
    val age_range: String? = null,
    val skin_type: String? = null,
    val gender: String? = null,
    val is_skin_sensitive: Boolean? = null,
    val profile_image_url: String? = null,
    val onboarding_completed: Boolean = false,
)

fun UserProfile.toProfileRow() = ProfileRow(
    id = id,
    full_name = fullName,
    age_range = ageRange,
    skin_type = skinType,
    gender = gender,
    is_skin_sensitive = isSkinSensitive,
    profile_image_url = profileImagePath,
    onboarding_completed = onboardingCompleted,
)

fun ProfileRow.toUserProfile() = UserProfile(
    id = id,
    fullName = full_name,
    ageRange = age_range,
    skinType = skin_type,
    gender = gender,
    isSkinSensitive = is_skin_sensitive,
    profileImagePath = profile_image_url,
    onboardingCompleted = onboarding_completed,
)
