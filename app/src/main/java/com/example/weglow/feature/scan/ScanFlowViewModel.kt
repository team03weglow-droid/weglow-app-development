package com.example.weglow.feature.scan

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.core.image.FaceValidationResult
import com.example.weglow.feature.hairstyle.HairstyleViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which kind of scan the user selected. */
enum class ScanMode { ACNE, HAIRSTYLE }

/** The Scan destination's workflow stage - not a UI/animation concern. */
enum class ScanFlowState { MODE_SELECT, CAMERA, ANALYZING }

data class ScanFlowUiState(
    val scanMode: ScanMode = ScanMode.ACNE,
    val stage: ScanFlowState = ScanFlowState.MODE_SELECT,
    val displayedValidationError: FaceValidationResult? = null,
)

/** One-shot workflow-completion intents. [com.example.weglow.navigation.WeGlowApp] performs the
 * actual navigation; this ViewModel never touches a NavController. */
sealed interface ScanFlowEffect {
    data object NavigateToAcneResults : ScanFlowEffect
    data object NavigateToHairstyleResults : ScanFlowEffect
}

/**
 * Owns the Scan destination's workflow/state machine (which mode is selected, which stage is
 * active, and when analysis is "done enough" to navigate to results) so [ui.screens.ScanScreen]
 * only has to render state and forward user actions. It coordinates the existing, unmodified
 * [ScanViewModel] and [HairstyleViewModel] - it never talks to ONNX/TFLite/Supabase directly.
 *
 * [elapsedRealtimeMs] defaults to the real clock and exists only so tests can advance the
 * minimum-visible-analysis timers deterministically instead of waiting multiple real seconds.
 */
class ScanFlowViewModel(
    private val scanViewModel: ScanViewModel,
    private val hairstyleViewModel: HairstyleViewModel,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanFlowUiState())
    val uiState: StateFlow<ScanFlowUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ScanFlowEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<ScanFlowEffect> = _effects

    private var analysisStartedAt: Long = 0L
    private var acneCompletionJob: Job? = null
    private var hairstyleCompletionJob: Job? = null
    private var validationRevealJob: Job? = null

    init {
        scanViewModel.uiState
            .onEach { state ->
                val flow = _uiState.value
                if (flow.stage == ScanFlowState.ANALYZING && flow.scanMode == ScanMode.ACNE && state.result != null) {
                    scheduleAcneCompletion()
                }
            }
            .launchIn(viewModelScope)

        hairstyleViewModel.uiState
            .onEach { state ->
                val flow = _uiState.value
                if (flow.stage == ScanFlowState.ANALYZING && flow.scanMode == ScanMode.HAIRSTYLE && state.result != null) {
                    scheduleHairstyleCompletion()
                }
                onHairstyleValidationStateChanged(flow, state.validationError)
            }
            .launchIn(viewModelScope)
    }

    /** Reached every time the user freshly lands on the Scan destination (see WeGlowApp). */
    fun resetFlow() {
        cancelPendingCompletion()
        _uiState.value = ScanFlowUiState()
    }

    fun selectMode(mode: ScanMode) {
        _uiState.update { it.copy(scanMode = mode, stage = ScanFlowState.CAMERA) }
    }

    fun returnToModeSelection() {
        _uiState.update { it.copy(stage = ScanFlowState.MODE_SELECT) }
    }

    /** A photo was captured or picked from the gallery, for whichever mode is currently active. */
    fun onPhotoReady(uri: Uri, hairstyleGender: String?) {
        scanViewModel.setPhoto(uri)
        beginAnalysisTiming()
        when (_uiState.value.scanMode) {
            ScanMode.ACNE -> scanViewModel.analyze()
            ScanMode.HAIRSTYLE -> hairstyleViewModel.analyze(uri.toString(), hairstyleGender)
        }
    }

    fun retryAcne() {
        beginAnalysisTiming()
        scanViewModel.analyze()
    }

    fun retryHairstyle(hairstyleGender: String?) {
        val uri = scanViewModel.uiState.value.photoUri ?: return
        beginAnalysisTiming()
        hairstyleViewModel.analyze(uri.toString(), hairstyleGender)
    }

    /** Cancels whichever mode is currently analyzing and returns to the camera stage. Used by
     * the ANALYZING BackHandler, both analyzing screens' cancel controls, and the photo
     * validation-error screen's "choose another photo" action - they were all the same
     * transition before this refactor. */
    fun cancelAnalysis() {
        cancelPendingCompletion()
        when (_uiState.value.scanMode) {
            ScanMode.ACNE -> scanViewModel.cancelAnalysis()
            ScanMode.HAIRSTYLE -> hairstyleViewModel.cancelAnalysis()
        }
        _uiState.update { it.copy(stage = ScanFlowState.CAMERA, displayedValidationError = null) }
    }

    private fun beginAnalysisTiming() {
        analysisStartedAt = elapsedRealtimeMs()
        _uiState.update { it.copy(stage = ScanFlowState.ANALYZING, displayedValidationError = null) }
    }

    private fun scheduleAcneCompletion() {
        if (acneCompletionJob?.isActive == true) return
        acneCompletionJob = viewModelScope.launch {
            awaitMinimumDuration(ACNE_MINIMUM_ANALYSIS_MS)
            // Lets the progress-bar animation visibly finish reaching 100% before navigating.
            delay(ACNE_COMPLETION_GRACE_MS)
            _effects.emit(ScanFlowEffect.NavigateToAcneResults)
        }
    }

    private fun scheduleHairstyleCompletion() {
        if (hairstyleCompletionJob?.isActive == true) return
        hairstyleCompletionJob = viewModelScope.launch {
            awaitMinimumDuration(HAIRSTYLE_MINIMUM_ANALYSIS_MS)
            _effects.emit(ScanFlowEffect.NavigateToHairstyleResults)
        }
    }

    private fun onHairstyleValidationStateChanged(flow: ScanFlowUiState, validationError: FaceValidationResult?) {
        val currentValidationError = if (flow.scanMode == ScanMode.HAIRSTYLE) validationError else null
        if (flow.stage == ScanFlowState.ANALYZING && currentValidationError != null) {
            if (validationRevealJob?.isActive == true) return
            validationRevealJob = viewModelScope.launch {
                awaitMinimumDuration(VALIDATION_ERROR_REVEAL_DELAY_MS)
                _uiState.update { it.copy(displayedValidationError = currentValidationError) }
            }
        } else {
            validationRevealJob?.cancel()
            validationRevealJob = null
            if (_uiState.value.displayedValidationError != null) {
                _uiState.update { it.copy(displayedValidationError = null) }
            }
        }
    }

    private suspend fun awaitMinimumDuration(minimumMs: Long) {
        val elapsed = elapsedRealtimeMs() - analysisStartedAt
        val remaining = (minimumMs - elapsed).coerceAtLeast(0L)
        if (remaining > 0L) delay(remaining)
    }

    private fun cancelPendingCompletion() {
        acneCompletionJob?.cancel()
        hairstyleCompletionJob?.cancel()
        validationRevealJob?.cancel()
    }

    override fun onCleared() {
        cancelPendingCompletion()
        super.onCleared()
    }

    private companion object {
        /** Keeps the acne analyzing screen visible for at least this long, even on a very fast
         * device, so the progress animation never looks like it skipped straight to done. */
        const val ACNE_MINIMUM_ANALYSIS_MS = 5_000L

        /** Extra pause after the minimum duration so the progress bar can visibly reach 100%. */
        const val ACNE_COMPLETION_GRACE_MS = 750L

        /** Mirrors the hairstyle analyzer's four presentation stages (4 x 700ms + 300ms). */
        const val HAIRSTYLE_MINIMUM_ANALYSIS_MS = 3_100L

        /** Keeps the "checking your photo" animation visible briefly before switching to the
         * validation-error screen, so a near-instant failure doesn't feel like a UI glitch. */
        const val VALIDATION_ERROR_REVEAL_DELAY_MS = 650L
    }
}
