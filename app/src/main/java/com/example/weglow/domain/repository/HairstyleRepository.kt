package com.example.weglow.domain.repository

import com.example.weglow.domain.model.HairstyleResult

interface HairstyleRepository {
    fun recommendationsFor(gender: String?): HairstyleResult
}
