package com.example.weglow.feature.hairstyle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.domain.model.HairstyleResult
import com.example.weglow.domain.repository.HairstyleRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HairstyleUiState(
    val isAnalyzing: Boolean = false,
    val result: HairstyleResult? = null,
    val error: String? = null,
)

class HairstyleViewModel(
    private val repository: HairstyleRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HairstyleUiState())
    val uiState: StateFlow<HairstyleUiState> = _uiState.asStateFlow()

    private var analysis: Job? = null

    fun analyze(photoReference: String, gender: String?) {
        analysis?.cancel()
        _uiState.value = HairstyleUiState(isAnalyzing = true)
        analysis = viewModelScope.launch {
            try {
                val result = repository.analyze(photoReference, gender)
                ensureActive()
                _uiState.value = HairstyleUiState(result = result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ensureActive()
                _uiState.value = HairstyleUiState(
                    error = when (error) {
                        is IOException -> "Cannot read this photo. Please choose it again or take a new photo."
                        is IllegalStateException -> error.message ?: "Face-shape analysis failed. Please retry."
                        else -> "Face-shape analysis failed. Please retry or choose another photo."
                    },
                )
            }
        }
    }

    fun cancelAnalysis() {
        analysis?.cancel()
        _uiState.value = HairstyleUiState()
    }

    fun clear() = cancelAnalysis()
}
