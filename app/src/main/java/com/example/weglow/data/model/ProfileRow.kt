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
)

fun UserProfile.toProfileRow() = ProfileRow(
    id = id,
    full_name = fullName,
    age_range = ageRange,
    skin_type = skinType,
    gender = gender,
    is_skin_sensitive = isSkinSensitive,
)
