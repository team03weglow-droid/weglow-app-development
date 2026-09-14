package com.example.weglow.feature.scan

import android.net.Uri
import android.net.fakeUri
import com.example.weglow.core.image.FaceValidationResult
import com.example.weglow.core.image.FaceValidator
import com.example.weglow.domain.model.AcneScanResult
import com.example.weglow.domain.model.HairstyleFailure
import com.example.weglow.domain.model.HairstyleFailureException
import com.example.weglow.domain.model.HairstyleResult
import com.example.weglow.domain.model.ScanFailure
import com.example.weglow.domain.model.ScanFailureException
import com.example.weglow.domain.repository.AcneScanRepository
import com.example.weglow.domain.repository.HairstyleRepository
import com.example.weglow.feature.hairstyle.HairstyleViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the Scan destination's workflow/state machine now that it lives in
 * [ScanFlowViewModel] instead of scattered through ScanScreen's LaunchedEffects. Uses real
 * [ScanViewModel]/[HairstyleViewModel] instances (as the app wires them) backed by fakes, so
 * this also proves the coordinator drives the existing, unmodified analysis ViewModels rather
 * than duplicating their ONNX/TFLite/Supabase logic. A fixed fake clock plus a
 * [StandardTestDispatcher] make every minimum-visible-duration wait resolve in virtual time,
 * never a real multi-second wait.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanFlowViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val acneResult = AcneScanResult(emptyList(), 640, 480, "test", 0.25f)
    private val hairstyleResult = HairstyleResult("Oval", 91, listOf("Balanced"), "Result", emptyList())

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun initialStateIsModeSelectionWithAcneDefaultAndNoValidationError() = runTest(dispatcher) {
        val viewModel = flowViewModel()

        assertEquals(ScanFlowUiState(), viewModel.uiState.value)
        assertEquals(ScanMode.ACNE, viewModel.uiState.value.scanMode)
        assertEquals(ScanFlowState.MODE_SELECT, viewModel.uiState.value.stage)
    }

    @Test fun selectingAcneModeMovesToCameraStage() = runTest(dispatcher) {
        val viewModel = flowViewModel()

        viewModel.selectMode(ScanMode.ACNE)

        assertEquals(ScanMode.ACNE, viewModel.uiState.value.scanMode)
        assertEquals(ScanFlowState.CAMERA, viewModel.uiState.value.stage)
    }

    @Test fun selectingHairstyleModeMovesToCameraStage() = runTest(dispatcher) {
        val viewModel = flowViewModel()

        viewModel.selectMode(ScanMode.HAIRSTYLE)

        assertEquals(ScanMode.HAIRSTYLE, viewModel.uiState.value.scanMode)
        assertEquals(ScanFlowState.CAMERA, viewModel.uiState.value.stage)
    }

    @Test fun photoReadyMovesToAnalyzingAndSetsThePhotoRegardlessOfMode() = runTest(dispatcher) {
        var acneCalls = 0
        val scan = ScanViewModel(fakeAcneRepository { acneCalls++; Result.success(acneResult) })
        val hairstyle = HairstyleViewModel(fakeHairstyleRepository { _, _ -> Result.success(hairstyleResult) }, fakeValidator())
        val viewModel = ScanFlowViewModel(scan, hairstyle, elapsedRealtimeMs = { 0L })
        viewModel.selectMode(ScanMode.HAIRSTYLE)

        viewModel.onPhotoReady(FAKE_URI, "Female")
        dispatcher.scheduler.runCurrent()

        assertEquals(ScanFlowState.ANALYZING, viewModel.uiState.value.stage)
        // The shared analyzing-screen preview reads scanViewModel's photo regardless of mode.
        assertEquals(FAKE_URI, scan.uiState.value.photoUri)
        assertEquals(0, acneCalls)
    }

    @Test fun acneProcessingProgressesThroughAnalyzingToSuccess() = runTest(dispatcher) {
        val prediction = CompletableDeferred<Result<AcneScanResult>>()
        val scan = ScanViewModel(fakeAcneRepository { prediction.await() })
        val hairstyle = HairstyleViewModel(fakeHairstyleRepository { _, _ -> Result.success(hairstyleResult) }, fakeValidator())
        val viewModel = ScanFlowViewModel(scan, hairstyle, elapsedRealtimeMs = { 0L })
        viewModel.selectMode(ScanMode.ACNE)

        viewModel.onPhotoReady(FAKE_URI, null)
        dispatcher.scheduler.runCurrent()
        assertTrue(scan.uiState.value.isAnalyzing)

        prediction.complete(Result.success(acneResult))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(acneResult, scan.uiState.value.result)
    }

    @Test fun hairstyleProcessingProgressesThroughAnalyzingToSuccess() = runTest(dispatcher) {
        val prediction = CompletableDeferred<Result<HairstyleResult>>()
        val scan = ScanViewModel(fakeAcneRepository { Result.success(acneResult) })
        val hairstyle = HairstyleViewModel(fakeHairstyleRepository { _, _ -> prediction.await() }, fakeValidator())
        val viewModel = ScanFlowViewModel(scan, hairstyle, elapsedRealtimeMs = { 0L })
        viewModel.selectMode(ScanMode.HAIRSTYLE)

        viewModel.onPhotoReady(FAKE_URI, "Female")
        dispatcher.scheduler.runCurrent()
        assertTrue(hairstyle.uiState.value.isAnalyzing)

        prediction.complete(Result.success(hairstyleResult))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(hairstyleResult, hairstyle.uiState.value.result)
    }

    @Test fun acneSuccessProducesExactlyOneNavigationEffect() = runTest(dispatcher) {
        val scan = ScanViewModel(fakeAcneRepository { Result.success(acneResult) })
        val hairstyle = HairstyleViewModel(fakeHairstyleRepository { _, _ -> Result.success(hairstyleResult) }, fakeValidator())
        val viewModel = ScanFlowViewModel(scan, hairstyle, elapsedRealtimeMs = { 0L })
        val (effects, collectorJob) = startEffectCollector(viewModel)
        viewModel.selectMode(ScanMode.ACNE)

        viewModel.onPhotoReady(FAKE_URI, null)
        dispatcher.scheduler.advanceUntilIdle()
        collectorJob.cancel()

        assertEquals(listOf(ScanFlowEffect.NavigateToAcneResults), effects)
    }

    @Test fun hairstyleSuccessProducesExactlyOneNavigationEffect() = runTest(dispatcher) {
        val scan = ScanViewModel(fakeAcneRepository { Result.success(acneResult) })
        val hairstyle = HairstyleViewModel(fakeHairstyleRepository { _, _ -> Result.success(hairstyleResult) }, fakeValidator())
        val viewModel = ScanFlowViewModel(scan, hairstyle, elapsedRealtimeMs = { 0L })
        val (effects, collectorJob) = startEffectCollector(viewModel)
        viewModel.selectMode(ScanMode.HAIRSTYLE)

        viewModel.onPhotoReady(FAKE_URI, "Female")
        dispatcher.scheduler.advanceUntilIdle()
        collectorJob.cancel()

        assertEquals(listOf(ScanFlowEffect.NavigateToHairstyleResults), effects)
    }

    @Test fun failureDoesNotNavigate() = runTest(dispatcher) {
        val scan = ScanViewModel(fakeAcneRepository { Result.failure(ScanFailureException(ScanFailure.InvalidImage)) })
        val hairstyle = HairstyleViewModel(fakeHairstyleRepository { _, _ -> Result.success(hairstyleResult) }, fakeValidator())
        val viewModel = ScanFlowViewModel(scan, hairstyle, elapsedRealtimeMs = { 0L })
        val (effects, collectorJob) = startEffectCollector(viewModel)
        viewModel.selectMode(ScanMode.ACNE)

        viewModel.onPhotoReady(FAKE_URI, null)
        dispatcher.scheduler.advanceUntilIdle()
        collectorJob.cancel()

        assertTrue(effects.isEmpty())
        assertNotNull(scan.uiState.value.error)
    }

    @Test fun retryAfterFailureFollowsTheSameTransitionAndCanSucceed() = runTest(dispatcher) {
        var shouldFail = true
        val scan = ScanViewModel(fakeAcneRepository {
            if (shouldFail) Result.failure(ScanFailureException(ScanFailure.InvalidImage)) else Result.success(acneResult)
        })
        val hairstyle = HairstyleViewModel(fakeHairstyleRepository { _, _ -> Result.success(hairstyleResult) }, fakeValidator())
        val viewModel = ScanFlowViewModel(scan, hairstyle, elapsedRealtimeMs = { 0L })
        val (effects, collectorJob) = startEffectCollector(viewModel)
        viewModel.selectMode(ScanMode.ACNE)
        viewModel.onPhotoReady(FAKE_URI, null)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(effects.isEmpty())

        shouldFail = false
        viewModel.retryAcne()
        dispatcher.scheduler.advanceUntilIdle()
        collectorJob.cancel()

        assertEquals(listOf(ScanFlowEffect.NavigateToAcneResults), effects)
        assertEquals(acneResult, scan.uiState.value.result)
    }

    @Test fun cancelDuringAnalysisReturnsToCameraAndSuppressesTheAlreadyScheduledCompletion() = runTest(dispatcher) {
        val prediction = CompletableDeferred<Result<AcneScanResult>>()
        val scan = ScanViewModel(fakeAcneRepository { prediction.await() })
        val hairstyle = HairstyleViewModel(fakeHairstyleRepository { _, _ -> Result.success(hairstyleResult) }, fakeValidator())
        val viewModel = ScanFlowViewModel(scan, hairstyle, elapsedRealtimeMs = { 0L })
        val (effects, collectorJob) = startEffectCollector(viewModel)
        viewModel.selectMode(ScanMode.ACNE)
        viewModel.onPhotoReady(FAKE_URI, null)
        dispatcher.scheduler.runCurrent()

        // The result arrives (scheduling the completion wait) but the user cancels first.
        prediction.complete(Result.success(acneResult))
        dispatcher.scheduler.runCurrent()
        viewModel.cancelAnalysis()
        dispatcher.scheduler.advanceUntilIdle()
        collectorJob.cancel()

        assertEquals(ScanFlowState.CAMERA, viewModel.uiState.value.stage)
        assertNull(scan.uiState.value.result)
        assertTrue("cancelling must suppress the already-scheduled navigation", effects.isEmpty())
    }

    @Test fun resetFlowStartsASecondScanFromCleanState() = runTest(dispatcher) {
        val scan = ScanViewModel(fakeAcneRepository { Result.success(acneResult) })
        val hairstyle = HairstyleViewModel(fakeHairstyleRepository { _, _ -> Result.success(hairstyleResult) }, fakeValidator())
        val viewModel = ScanFlowViewModel(scan, hairstyle, elapsedRealtimeMs = { 0L })
        viewModel.selectMode(ScanMode.HAIRSTYLE)
        viewModel.onPhotoReady(FAKE_URI, "Female")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.resetFlow()

        assertEquals(ScanFlowUiState(), viewModel.uiState.value)
    }

    @Test fun duplicateAnalyzeActionsDoNotCreateDuplicateFlowProgression() = runTest(dispatcher) {
        var calls = 0
        val scan = ScanViewModel(fakeAcneRepository { Result.success(acneResult) })
        val hairstyle = HairstyleViewModel(
            fakeHairstyleRepository { _, _ -> calls++; Result.success(hairstyleResult) },
            fakeValidator(),
        )
        val viewModel = ScanFlowViewModel(scan, hairstyle, elapsedRealtimeMs = { 0L })
        val (effects, collectorJob) = startEffectCollector(viewModel)
        viewModel.selectMode(ScanMode.HAIRSTYLE)

        viewModel.onPhotoReady(FAKE_URI, "Female")
        viewModel.onPhotoReady(FAKE_URI, "Female")
        dispatcher.scheduler.advanceUntilIdle()
        collectorJob.cancel()

        assertEquals(1, calls)
        assertEquals(listOf(ScanFlowEffect.NavigateToHairstyleResults), effects)
    }

    @Test fun navigationEffectIsOneShotAndIsNeverReplayedToALateObserver() = runTest(dispatcher) {
        val scan = ScanViewModel(fakeAcneRepository { Result.success(acneResult) })
        val hairstyle = HairstyleViewModel(fakeHairstyleRepository { _, _ -> Result.success(hairstyleResult) }, fakeValidator())
        val viewModel = ScanFlowViewModel(scan, hairstyle, elapsedRealtimeMs = { 0L })
        viewModel.selectMode(ScanMode.ACNE)
        viewModel.onPhotoReady(FAKE_URI, null)
        dispatcher.scheduler.advanceUntilIdle()

        // A SharedFlow with no replay never hands an already-emitted effect to a new collector -
        // this is what stops the results screen from opening a second time after recomposition.
        assertTrue(viewModel.effects.replayCache.isEmpty())
    }

    @Test fun typedHairstyleFailureStillReachesTheUnderlyingUiStateThroughTheCoordinator() = runTest(dispatcher) {
        val scan = ScanViewModel(fakeAcneRepository { Result.success(acneResult) })
        val hairstyle = HairstyleViewModel(
            fakeHairstyleRepository { _, _ -> Result.failure(HairstyleFailureException(HairstyleFailure.NotSignedIn)) },
            fakeValidator(),
        )
        val viewModel = ScanFlowViewModel(scan, hairstyle, elapsedRealtimeMs = { 0L })
        viewModel.selectMode(ScanMode.HAIRSTYLE)

        viewModel.onPhotoReady(FAKE_URI, "Female")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "Sign in to save your face shape and see hairstyle recommendations.",
            hairstyle.uiState.value.error,
        )
        assertFalse(scan.uiState.value.isAnalyzing)
    }

    /**
     * A plain [TestScope.launch] rather than `backgroundScope` - collector jobs started via
     * `backgroundScope.launch` were observed not to receive [ScanFlowViewModel.effects]
     * emissions when driven purely through explicit [kotlinx.coroutines.test.TestCoroutineScheduler]
     * advancement in this project's coroutines-test version, whereas a plain child job of the
     * test body does. The caller must cancel the returned [Job] once done asserting, since a
     * SharedFlow collector never completes on its own.
     */
    private fun TestScope.startEffectCollector(viewModel: ScanFlowViewModel): Pair<MutableList<ScanFlowEffect>, Job> {
        val effects = mutableListOf<ScanFlowEffect>()
        val job = launch { viewModel.effects.collect { effects.add(it) } }
        return effects to job
    }

    private fun flowViewModel(): ScanFlowViewModel {
        val scan = ScanViewModel(fakeAcneRepository { Result.success(acneResult) })
        val hairstyle = HairstyleViewModel(fakeHairstyleRepository { _, _ -> Result.success(hairstyleResult) }, fakeValidator())
        return ScanFlowViewModel(scan, hairstyle, elapsedRealtimeMs = { 0L })
    }

    private fun fakeAcneRepository(block: suspend () -> Result<AcneScanResult>) = object : AcneScanRepository {
        override suspend fun analyze(photoReference: String) = block()
    }

    private fun fakeHairstyleRepository(
        block: suspend (String, String?) -> Result<HairstyleResult>,
    ) = object : HairstyleRepository {
        override suspend fun analyze(photoReference: String, gender: String?) = block(photoReference, gender)
    }

    private fun fakeValidator(validation: FaceValidationResult = FaceValidationResult.Valid) = object : FaceValidator {
        override suspend fun validate(photoReference: String) = validation
    }

    private companion object {
        val FAKE_URI: Uri = fakeUri("content://fake/photo.jpg")
    }
}
