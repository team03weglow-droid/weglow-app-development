package com.example.weglow.domain.model

/**
 * Typed classification of why [com.example.weglow.domain.repository.AcneScanRepository.analyze]
 * failed. Carries no presentation copy - mapping a failure to a user-facing message is the
 * ViewModel's responsibility, not the data layer's.
 */
sealed interface ScanFailure {
    /** The selected photo could not be read or decoded. */
    data object InvalidImage : ScanFailure

    /** The device ran out of memory while analyzing the photo. */
    data object DeviceOutOfMemory : ScanFailure

    /** The bundled analysis model is missing, corrupted, or incompatible with this build. */
    data object ModelUnavailable : ScanFailure

    /** Any other, unexpected technical failure. */
    data object Unknown : ScanFailure
}

/** Carries a typed [failure] through a Kotlin [Result] so callers never need to parse a message. */
class ScanFailureException(
    val failure: ScanFailure,
    cause: Throwable? = null,
) : Exception(failure.toString(), cause)
