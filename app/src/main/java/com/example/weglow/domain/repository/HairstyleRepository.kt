package com.example.weglow.domain.repository

import com.example.weglow.domain.model.HairstyleResult

interface HairstyleRepository {
    /** Classifies a face photo and builds recommendations without uploading the image. */
    suspend fun analyze(photoReference: String, gender: String?): HairstyleResult
}
