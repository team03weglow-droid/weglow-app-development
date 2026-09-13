package com.example.weglow.data.repository

import android.content.Context
import com.example.weglow.domain.model.HairstyleResult
import com.example.weglow.domain.model.HairstyleRecommendation
import com.example.weglow.domain.repository.HairstyleRepository
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.ProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.CancellationException

/** Runs the on-device classifier, then loads the matching cards from Supabase. */
class SupabaseHairstyleRepository(
    context: Context,
    private val client: SupabaseClient,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : HairstyleRepository {
    private val classifier = LocalHairstyleRepository(context)

    override suspend fun analyze(photoReference: String, gender: String?): HairstyleResult {
        val classified = classifier.analyze(photoReference, gender)
        val userId = authRepository.currentUserId()
            ?: throw IllegalStateException("Sign in to save your face shape and see hairstyle recommendations.")
        profileRepository.updateFaceShape(userId, classified.faceShape).getOrElse { error ->
            throw IllegalStateException("Could not save your face shape. Please retry the scan.", error)
        }
        val profile = profileRepository.getProfile(userId).getOrElse { error ->
            throw IllegalStateException("Could not load your profile. Please retry the scan.", error)
        } ?: throw IllegalStateException("Your profile is unavailable. Please complete onboarding and retry.")
        val savedShape = profile.faceShape?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw IllegalStateException("Could not confirm your saved face shape. Please retry the scan.")

        val rows = try {
            client.postgrest["hairstyles"]
                .select()
                .decodeList<JsonObject>()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw IllegalStateException("Hairstyle recommendations are temporarily unavailable.", error)
        }
        check(rows.isNotEmpty()) { "Hairstyles are unavailable. Please check database read access." }
        val recommendations = matchingHairstyles(rows, savedShape, profile.gender)

        return classified.copy(recommendations = recommendations)
    }
}

internal fun matchingHairstyles(
    rows: List<JsonObject>,
    faceShape: String,
    gender: String?,
): List<HairstyleRecommendation> {
    val normalizedGender = gender?.trim()?.lowercase()
    return rows.asSequence()
        .filter { row -> row.text("face_shape")?.equals(faceShape, ignoreCase = true) == true }
        .filter { row ->
            normalizedGender !in setOf("male", "female") || row.matchesGender(normalizedGender)
        }
        .mapNotNull(JsonObject::toHairstyleRecommendationOrNull)
        .toList()
}

/** Maps the columns in public.hairstyles. */
internal fun JsonObject.toHairstyleRecommendationOrNull(): HairstyleRecommendation? {
    val title = text("hairstyle_name") ?: return null
    val length = text("hair_length")
    return HairstyleRecommendation(
        matchPercent = 0,
        category = length ?: "HAIRSTYLE",
        title = title,
        description = length?.let { "Hair length: $it" } ?: "",
        imageUrl = text("image_url"),
    )
}

private fun JsonObject.text(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.trim()
        ?.takeIf(String::isNotEmpty)

private fun JsonObject.matchesGender(gender: String?): Boolean {
    val value = text("gender") ?: return false
    return value.equals(gender, ignoreCase = true) || when (gender) {
        "male" -> value.lowercase() in setOf("men", "mens", "man")
        "female" -> value.lowercase() in setOf("women", "womens", "woman")
        else -> true
    }
}
