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
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The exact prefix CameraCaptureScreen uses for a scan capture written to the app cache. */
internal const val SCAN_CACHE_FILE_PREFIX = "weglow_scan_"

/**
 * True only for a file name this app itself wrote for a scan capture. Kept separate from
 * [Uri] handling so it is unit-testable without the Android framework, and so cache cleanup
 * can never be widened to delete a user-picked gallery file by mistake.
 */
internal fun isOwnedScanCacheFileName(fileName: String): Boolean =
    fileName.startsWith(SCAN_CACHE_FILE_PREFIX)

data class ScanUiState(
    val photoUri: Uri? = null,
    val isValidating: Boolean = false,
    val isAnalyzing: Boolean = false,
    val result: AcneScanResult? = null,
    val error: String? = null,
)

class ScanViewModel(
    private val repository: AcneScanRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var analysis: Job? = null

    fun setPhoto(uri: Uri) {
        analysis?.cancel()
        val previousPhoto = _uiState.value.photoUri
        _uiState.value = ScanUiState(photoUri = uri)
        // A skin photo is privacy-sensitive; once it is replaced by a new capture it should
        // not linger in the app cache indefinitely. Only ever deletes a file this app wrote
        // for a scan capture - never a content:// gallery-picked photo.
        if (previousPhoto != uri) deleteIfOwnedScanCacheFile(previousPhoto)
    }

    fun analyze() {
        val photo = _uiState.value.photoUri ?: return
        analyzeReference(photo.toString())
    }

    internal fun analyzeReference(reference: String) {
        if (analysis?.isActive == true) return
        _uiState.value = _uiState.value.copy(
            isValidating = false,
            isAnalyzing = true,
            result = null,
            error = null,
        )
        analysis = viewModelScope.launch {
            try {
                val result = repository.analyze(reference)
                ensureActive()
                _uiState.value = _uiState.value.copy(
                    isValidating = false,
                    isAnalyzing = false,
                    result = result,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ensureActive()
                _uiState.value = _uiState.value.copy(
                    isValidating = false,
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
        _uiState.value = _uiState.value.copy(
            isValidating = false,
            isAnalyzing = false,
            result = null,
            error = null,
        )
    }

    fun clear() {
        analysis?.cancel()
        deleteIfOwnedScanCacheFile(_uiState.value.photoUri)
        _uiState.value = ScanUiState()
    }

    override fun onCleared() {
        analysis?.cancel()
        super.onCleared()
    }

    private fun deleteIfOwnedScanCacheFile(uri: Uri?) {
        if (uri == null || uri.scheme != "file") return
        val path = uri.path ?: return
        val file = File(path)
        if (isOwnedScanCacheFileName(file.name)) {
            runCatching { file.delete() }
        }
    }
}
