package com.example.weglow.feature.hairstyle

import androidx.lifecycle.ViewModel
import com.example.weglow.domain.model.HairstyleResult
import com.example.weglow.domain.repository.HairstyleRepository

class HairstyleViewModel(
    private val repository: HairstyleRepository,
) : ViewModel() {
    fun resultFor(gender: String?): HairstyleResult = repository.recommendationsFor(gender)
}
