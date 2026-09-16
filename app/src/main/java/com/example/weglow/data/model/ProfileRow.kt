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
    val face_shape: String? = null,
    val is_skin_sensitive: Boolean? = null,
    val profile_image_url: String? = null,
    val onboarding_completed: Boolean = false,
    val skin_concerns: String? = null,
    val last_scan_at: String? = null,
    val uv_index: Double? = null,
    val uv_category: String? = null,
    val humidity: Int? = null,
    val location_name: String? = null,
    val last_env_at: String? = null,
)

fun UserProfile.toProfileRow() = ProfileRow(
    id = id,
    full_name = fullName,
    age_range = ageRange,
    skin_type = skinType,
    gender = gender,
    face_shape = faceShape,
    is_skin_sensitive = isSkinSensitive,
    profile_image_url = profileImagePath,
    onboarding_completed = onboardingCompleted,
    skin_concerns = null,
    last_scan_at = null,
    uv_index = null,
    uv_category = null,
    humidity = null,
    location_name = null,
    last_env_at = null,
)

fun ProfileRow.toUserProfile() = UserProfile(
    id = id,
    fullName = full_name,
    ageRange = age_range,
    skinType = skin_type,
    gender = gender,
    faceShape = face_shape,
    isSkinSensitive = is_skin_sensitive,
    profileImagePath = profile_image_url,
    onboardingCompleted = onboarding_completed,
)
