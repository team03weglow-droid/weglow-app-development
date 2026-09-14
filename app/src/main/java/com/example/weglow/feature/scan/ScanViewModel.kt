package com.example.weglow.feature.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.domain.model.AcneScanResult
import com.example.weglow.domain.model.ScanFailure
import com.example.weglow.domain.model.ScanFailureException
import com.example.weglow.domain.repository.AcneScanRepository
import com.example.weglow.domain.repository.ScanProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
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
    val saveState: ScanSaveState = ScanSaveState.Idle,
    val error: String? = null,
)

sealed interface ScanSaveState {
    data object Idle : ScanSaveState
    data object Saving : ScanSaveState
    data object Saved : ScanSaveState
    data class Failed(val message: String) : ScanSaveState
}

class ScanViewModel(
    private val repository: AcneScanRepository,
    private val scanProfileRepository: ScanProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var analysis: Job? = null
    private var completedScanAt: Instant? = null

    fun setPhoto(uri: Uri) {
        analysis?.cancel()
        completedScanAt = null
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
            saveState = ScanSaveState.Idle,
            error = null,
        )
        analysis = viewModelScope.launch {
            try {
                val outcome = repository.analyze(reference)
                ensureActive()
                outcome.fold(
                    onSuccess = { result ->
                        completedScanAt = Instant.now()
                        _uiState.value = _uiState.value.copy(saveState = ScanSaveState.Saving)
                        val saveResult = persist(result, completedScanAt!!)
                        ensureActive()
                        _uiState.value = _uiState.value.copy(
                            isValidating = false,
                            isAnalyzing = false,
                            result = result,
                            saveState = saveResult.fold(
                                onSuccess = { ScanSaveState.Saved },
                                onFailure = { error ->
                                    logSaveFailure(error)
                                    ScanSaveState.Failed(error.message ?: "Unable to save this scan.")
                                },
                            ),
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isValidating = false,
                            isAnalyzing = false,
                            error = error.toScanFailure().toUserMessage(),
                        )
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ensureActive()
                _uiState.value = _uiState.value.copy(
                    isValidating = false,
                    isAnalyzing = false,
                    error = ScanFailure.Unknown.toUserMessage(),
                )
            }
        }
    }

    fun cancelAnalysis() {
        analysis?.cancel()
        completedScanAt = null
        _uiState.value = _uiState.value.copy(
            isValidating = false,
            isAnalyzing = false,
            result = null,
            saveState = ScanSaveState.Idle,
            error = null,
        )
    }

    /** Retries only a failed upload of the already-completed acne scan; it never reruns the model. */
    fun retrySave() {
        val result = _uiState.value.result ?: return
        val scannedAt = completedScanAt ?: return
        if (_uiState.value.saveState !is ScanSaveState.Failed || analysis?.isActive == true) return
        analysis = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saveState = ScanSaveState.Saving)
            val saveResult = persist(result, scannedAt)
            ensureActive()
            _uiState.value = _uiState.value.copy(
                saveState = saveResult.fold(
                    onSuccess = { ScanSaveState.Saved },
                    onFailure = { error ->
                        logSaveFailure(error)
                        ScanSaveState.Failed(error.message ?: "Unable to save this scan.")
                    },
                ),
            )
        }
    }

    fun clear() {
        analysis?.cancel()
        completedScanAt = null
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

    private fun logSaveFailure(error: Throwable) {
        runCatching { android.util.Log.w("ScanViewModel", "Unable to save completed skin scan.", error) }
    }

    private suspend fun persist(result: AcneScanResult, scannedAt: Instant): Result<Unit> =
        runCatching { scanProfileRepository.saveScanResult(result, scannedAt).getOrThrow() }
}

/**
 * A well-behaved [AcneScanRepository] always fails with a [ScanFailureException]; this only
 * falls back to [ScanFailure.Unknown] for a repository that misbehaves and throws/returns some
 * other [Throwable] instead, so an implementation bug can never leak a raw message to the UI.
 */
private fun Throwable.toScanFailure(): ScanFailure = (this as? ScanFailureException)?.failure ?: ScanFailure.Unknown

private fun ScanFailure.toUserMessage(): String = when (this) {
    ScanFailure.InvalidImage -> "Cannot read this photo. Please choose it again or take a new photo."
    ScanFailure.DeviceOutOfMemory -> "There is not enough memory to analyze this photo. Close other apps and try again."
    ScanFailure.ModelUnavailable -> "Skin analysis could not start on this device. Please update or reinstall the app."
    ScanFailure.Unknown -> "Analysis failed. Please retry or choose another photo."
}
