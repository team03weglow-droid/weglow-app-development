package com.example.weglow.feature.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.domain.model.AcneScanResult
import com.example.weglow.domain.repository.AcneScanRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScanUiState(
    val photoUri: Uri? = null,
    val isAnalyzing: Boolean = false,
    val result: AcneScanResult? = null,
    val error: String? = null,
)

class ScanViewModel(private val repository: AcneScanRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var analysis: Job? = null

    fun setPhoto(uri: Uri) {
        analysis?.cancel()
        _uiState.value = ScanUiState(photoUri = uri)
    }

    fun analyze() {
        val photo = _uiState.value.photoUri ?: return
        analyzeReference(photo.toString())
    }

    internal fun analyzeReference(reference: String) {
        analysis?.cancel()
        _uiState.value = _uiState.value.copy(isAnalyzing = true, result = null, error = null)
        analysis = viewModelScope.launch {
            try {
                val result = repository.analyze(reference)
                ensureActive()
                _uiState.value = _uiState.value.copy(isAnalyzing = false, result = result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ensureActive()
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    error = when (error) {
                        is IOException -> "Cannot read this photo. Please choose it again or take a new photo."
                        is IllegalStateException -> error.message ?: "Analysis failed. Please retry."
                        else -> "Analysis failed. Please retry or choose another photo."
                    },
                )
            }
        }
    }

    fun cancelAnalysis() {
        analysis?.cancel()
        _uiState.value = _uiState.value.copy(isAnalyzing = false, result = null, error = null)
    }

    fun clear() {
        analysis?.cancel()
        _uiState.value = ScanUiState()
    }
}
