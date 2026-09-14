package com.example.weglow.domain.model

/**
 * Typed classification of why [com.example.weglow.domain.repository.HairstyleRepository.analyze]
 * failed. Carries no presentation copy - mapping a failure to a user-facing message is the
 * ViewModel's responsibility, not the data layer's.
 */
sealed interface HairstyleFailure {
    /** The selected photo could not be read or decoded. */
    data object InvalidImage : HairstyleFailure

    /** The device ran out of memory while classifying the photo. */
    data object DeviceOutOfMemory : HairstyleFailure

    /** The bundled face-shape model is missing, corrupted, or incompatible with this build. */
    data object ModelUnavailable : HairstyleFailure

    /** No authenticated user is available to save the detected face shape against. */
    data object NotSignedIn : HairstyleFailure

    /** The user's profile could not be read or written. */
    data object ProfileUnavailable : HairstyleFailure

    /** Supabase hairstyle recommendations could not be retrieved. */
    data object RecommendationsUnavailable : HairstyleFailure

    /** Any other, unexpected technical failure. */
    data object Unknown : HairstyleFailure
}

/** Carries a typed [failure] through a Kotlin [Result] so callers never need to parse a message. */
class HairstyleFailureException(
    val failure: HairstyleFailure,
    cause: Throwable? = null,
) : Exception(failure.toString(), cause)
