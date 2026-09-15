package com.example.weglow.domain.repository

import com.example.weglow.domain.model.HairstyleResult

interface HairstyleRepository {
    /**
     * Classifies a face photo and builds recommendations without uploading the image.
     * Failure is returned as a typed [com.example.weglow.domain.model.HairstyleFailure].
     */
    suspend fun analyze(photoReference: String, gender: String?): Result<HairstyleResult>
}
