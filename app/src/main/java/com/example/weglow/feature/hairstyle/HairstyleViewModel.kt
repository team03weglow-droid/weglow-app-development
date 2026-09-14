package com.example.weglow.feature.hairstyle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.core.image.FaceValidationResult
import com.example.weglow.core.image.FaceValidator
import com.example.weglow.domain.model.HairstyleFailure
import com.example.weglow.domain.model.HairstyleFailureException
import com.example.weglow.domain.model.HairstyleResult
import com.example.weglow.domain.repository.HairstyleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HairstyleUiState(
    val isValidating: Boolean = false,
    val isAnalyzing: Boolean = false,
    val result: HairstyleResult? = null,
    val validationError: FaceValidationResult? = null,
    val error: String? = null,
)

class HairstyleViewModel(
    private val repository: HairstyleRepository,
    private val faceValidator: FaceValidator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HairstyleUiState())
    val uiState: StateFlow<HairstyleUiState> = _uiState.asStateFlow()

    private var analysis: Job? = null

    fun analyze(photoReference: String, gender: String?) {
        if (analysis?.isActive == true) return
        _uiState.value = HairstyleUiState(isValidating = true)
        analysis = viewModelScope.launch {
            try {
                val validation = faceValidator.validate(photoReference)
                ensureActive()
                if (validation != FaceValidationResult.Valid) {
                    _uiState.value = HairstyleUiState(validationError = validation)
                    return@launch
                }
                _uiState.value = HairstyleUiState(isAnalyzing = true)
                val outcome = repository.analyze(photoReference, gender)
                ensureActive()
                outcome.fold(
                    onSuccess = { result -> _uiState.value = HairstyleUiState(result = result) },
                    onFailure = { error ->
                        _uiState.value = HairstyleUiState(error = error.toHairstyleFailure().toUserMessage())
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ensureActive()
                val wasValidating = _uiState.value.isValidating
                _uiState.value = if (wasValidating) {
                    HairstyleUiState(validationError = FaceValidationResult.ProcessingError)
                } else {
                    HairstyleUiState(error = HairstyleFailure.Unknown.toUserMessage())
                }
            }
        }
    }

    fun cancelAnalysis() {
        analysis?.cancel()
        _uiState.value = HairstyleUiState()
    }

    fun clear() = cancelAnalysis()

    override fun onCleared() {
        analysis?.cancel()
        faceValidator.close()
        super.onCleared()
    }
}

/**
 * A well-behaved [HairstyleRepository] always fails with a [HairstyleFailureException]; this
 * only falls back to [HairstyleFailure.Unknown] for a repository that misbehaves and
 * throws/returns some other [Throwable] instead, so an implementation bug can never leak a raw
 * message to the UI.
 */
private fun Throwable.toHairstyleFailure(): HairstyleFailure =
    (this as? HairstyleFailureException)?.failure ?: HairstyleFailure.Unknown

private fun HairstyleFailure.toUserMessage(): String = when (this) {
    HairstyleFailure.InvalidImage -> "Cannot read this photo. Please choose it again or take a new photo."
    HairstyleFailure.DeviceOutOfMemory -> "There is not enough memory to analyze this photo. Close other apps and try again."
    HairstyleFailure.ModelUnavailable -> "Face-shape analysis could not start on this device. Please update or reinstall the app."
    HairstyleFailure.NotSignedIn -> "Sign in to save your face shape and see hairstyle recommendations."
    HairstyleFailure.ProfileUnavailable -> "We couldn't load your profile. Please retry the scan."
    HairstyleFailure.RecommendationsUnavailable -> "Hairstyle recommendations are temporarily unavailable. Please try again later."
    HairstyleFailure.Unknown -> "Face-shape analysis failed. Please retry or choose another photo."
}
