package com.example.weglow.data.repository

import com.example.weglow.domain.model.HairstyleFailure
import com.example.weglow.domain.model.HairstyleFailureException
import com.example.weglow.domain.model.HairstyleResult
import com.example.weglow.domain.model.HairstyleRecommendation
import com.example.weglow.domain.repository.HairstyleRepository
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.ProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Count
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.CancellationException

/**
 * Loads Supabase-backed hairstyle recommendation cards for the face shape produced by
 * [classifier]. This class owns only the remote lookup; it does not construct or know how
 * the classifier itself works (on-device model, remote model, or a test fake) — that
 * decision belongs to whoever assembles this repository (see [com.example.weglow.app.AppContainer]).
 */
class SupabaseHairstyleRepository(
    private val classifier: HairstyleRepository,
    private val client: SupabaseClient,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : HairstyleRepository {

    override suspend fun analyze(photoReference: String, gender: String?): Result<HairstyleResult> {
        val classified = classifier.analyze(photoReference, gender).getOrElse { error ->
            return Result.failure(error)
        }
        val userId = authRepository.currentUserId()
            ?: return Result.failure(HairstyleFailureException(HairstyleFailure.NotSignedIn))

        profileRepository.updateFaceShape(userId, classified.faceShape).getOrElse { error ->
            return Result.failure(HairstyleFailureException(HairstyleFailure.ProfileUnavailable, error))
        }
        val profile = profileRepository.getProfile(userId).getOrElse { error ->
            return Result.failure(HairstyleFailureException(HairstyleFailure.ProfileUnavailable, error))
        } ?: return Result.failure(HairstyleFailureException(HairstyleFailure.ProfileUnavailable))
        val savedShape = profile.faceShape?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return Result.failure(HairstyleFailureException(HairstyleFailure.ProfileUnavailable))

        val normalizedGender = profile.gender?.trim()?.lowercase()

        // Filtered server-side: only rows for this face shape (and, when the profile's
        // gender is determinate, this gender) are downloaded, instead of the whole table.
        val rows = try {
            client.postgrest["hairstyles"]
                .select {
                    filter {
                        ilike("face_shape", savedShape)
                        if (normalizedGender == "male" || normalizedGender == "female") {
                            or {
                                ilike("gender", normalizedGender)
                                genderSynonyms(normalizedGender).forEach { synonym ->
                                    ilike("gender", synonym)
                                }
                            }
                        }
                    }
                }
                .decodeList<JsonObject>()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return Result.failure(HairstyleFailureException(HairstyleFailure.RecommendationsUnavailable, error))
        }

        // An empty result here is only a real problem if the whole table is unreadable or
        // unseeded; zero rows for this specific face shape/gender is a legitimate outcome
        // (e.g. that combination has no catalogued styles yet) and must not be reported as
        // a backend failure, nor papered over with fabricated recommendations.
        if (rows.isEmpty()) {
            ensureHairstylesTableIsReadable().getOrElse { error ->
                return Result.failure(error)
            }
        }

        val recommendations = matchingHairstyles(rows, savedShape, profile.gender)

        return Result.success(classified.copy(recommendations = recommendations))
    }

    /** Cheap existence check (no rows transferred) used only when a filtered fetch is empty. */
    private suspend fun ensureHairstylesTableIsReadable(): Result<Unit> {
        val totalRows = try {
            client.postgrest["hairstyles"]
                .select {
                    head = true
                    count(Count.EXACT)
                }
                .countOrNull()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return Result.failure(HairstyleFailureException(HairstyleFailure.RecommendationsUnavailable, error))
        }
        return if ((totalRows ?: 0L) > 0L) {
            Result.success(Unit)
        } else {
            Result.failure(HairstyleFailureException(HairstyleFailure.RecommendationsUnavailable))
        }
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
        "male", "female" -> value.lowercase() in genderSynonyms(gender)
        else -> true
    }
}

/** Alternate spellings accepted for a normalized ("male"/"female") gender value. */
internal fun genderSynonyms(normalizedGender: String): List<String> = when (normalizedGender) {
    "male" -> listOf("men", "mens", "man")
    "female" -> listOf("women", "womens", "woman")
    else -> emptyList()
}
