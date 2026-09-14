package com.example.weglow.feature.scan

import com.example.weglow.domain.model.AcneScanResult
import com.example.weglow.domain.model.ScanFailure
import com.example.weglow.domain.model.ScanFailureException
import com.example.weglow.domain.repository.AcneScanRepository
import com.example.weglow.domain.repository.ScanProfileRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val emptyResult = AcneScanResult(emptyList(), 640, 480, "test", 0.25f)

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun waitsForPredictionAndAcceptsNoDetections() = runTest(dispatcher) {
        val prediction = CompletableDeferred<AcneScanResult>()
        val viewModel = viewModel { prediction.await() }
        viewModel.analyzeReference("photo")
        dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isAnalyzing)
        assertNull(viewModel.uiState.value.result)
        prediction.complete(emptyResult)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptyResult, viewModel.uiState.value.result)
        assertFalse(viewModel.uiState.value.isAnalyzing)
    }

    @Test fun failedScanCanBeRetriedWithoutStaleResults() = runTest(dispatcher) {
        var fail = true
        val viewModel = viewModelResult {
            if (fail) Result.failure(ScanFailureException(ScanFailure.InvalidImage)) else Result.success(emptyResult)
        }
        viewModel.analyzeReference("photo")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            "Cannot read this photo. Please choose it again or take a new photo.",
            viewModel.uiState.value.error,
        )
        assertNull(viewModel.uiState.value.result)
        fail = false
        viewModel.analyzeReference("photo")
        assertNull(viewModel.uiState.value.error)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptyResult, viewModel.uiState.value.result)
    }

    @Test fun eachTypedFailureMapsToItsOwnDeterministicMessage() = runTest(dispatcher) {
        val expected = mapOf(
            ScanFailure.InvalidImage to "Cannot read this photo. Please choose it again or take a new photo.",
            ScanFailure.DeviceOutOfMemory to "There is not enough memory to analyze this photo. Close other apps and try again.",
            ScanFailure.ModelUnavailable to "Skin analysis could not start on this device. Please update or reinstall the app.",
            ScanFailure.Unknown to "Analysis failed. Please retry or choose another photo.",
        )
        expected.forEach { (failure, message) ->
            val viewModel = viewModelResult { Result.failure(ScanFailureException(failure)) }
            viewModel.analyzeReference("photo")
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(message, viewModel.uiState.value.error)
        }
    }

    @Test fun unexpectedRepositoryExceptionNeverExposesRawMessage() = runTest(dispatcher) {
        val secret = "internal debug detail token=abc123"
        val viewModel = ScanViewModel(object : AcneScanRepository {
            override suspend fun analyze(photoReference: String): Result<AcneScanResult> =
                throw RuntimeException(secret)
        }, scanProfileRepository())
        viewModel.analyzeReference("photo")
        dispatcher.scheduler.advanceUntilIdle()
        val error = viewModel.uiState.value.error
        assertNotNull(error)
        assertFalse(error!!.contains(secret))
        assertEquals("Analysis failed. Please retry or choose another photo.", error)
    }

    @Test fun repositoryFailureResultAlsoNeverExposesRawMessage() = runTest(dispatcher) {
        val secret = "SQL error near line 42"
        val viewModel = viewModelResult { Result.failure(RuntimeException(secret)) }
        viewModel.analyzeReference("photo")
        dispatcher.scheduler.advanceUntilIdle()
        val error = viewModel.uiState.value.error
        assertNotNull(error)
        assertFalse(error!!.contains(secret))
        assertEquals("Analysis failed. Please retry or choose another photo.", error)
    }

    @Test fun cancellationDoesNotPublishLateResults() = runTest(dispatcher) {
        val prediction = CompletableDeferred<AcneScanResult>()
        val viewModel = viewModel { prediction.await() }
        viewModel.analyzeReference("photo")
        dispatcher.scheduler.runCurrent()
        viewModel.cancelAnalysis()
        prediction.complete(emptyResult)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.result)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isAnalyzing)
    }

    @Test fun validPhotoContinuesToAcneRepository() = runTest(dispatcher) {
        var repositoryCalls = 0
        val viewModel = viewModel {
            repositoryCalls++
            emptyResult
        }

        viewModel.analyzeReference("photo")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repositoryCalls)
        assertEquals(emptyResult, viewModel.uiState.value.result)
    }

    @Test fun duplicateAnalyzeRequestIsIgnoredWhileAnalysisIsRunning() = runTest(dispatcher) {
        val prediction = CompletableDeferred<AcneScanResult>()
        var repositoryCalls = 0
        val viewModel = viewModel {
            repositoryCalls++
            prediction.await()
        }

        viewModel.analyzeReference("photo")
        dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isAnalyzing)
        viewModel.analyzeReference("photo")
        dispatcher.scheduler.runCurrent()
        assertEquals(1, repositoryCalls)
        prediction.complete(emptyResult)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test fun acneAnalysisDoesNotPerformFaceValidation() = runTest(dispatcher) {
        val prediction = CompletableDeferred<AcneScanResult>()
        val viewModel = viewModel { prediction.await() }
        viewModel.analyzeReference("photo")
        dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isAnalyzing)
        assertFalse(viewModel.uiState.value.isValidating)
        prediction.complete(emptyResult)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptyResult, viewModel.uiState.value.result)
    }

    @Test fun completedAcneScan_isPersistedExactlyOnceBeforePublishingResult() = runTest(dispatcher) {
        var saves = 0
        val viewModel = ScanViewModel(
            repository { emptyResult },
            object : ScanProfileRepository {
                override suspend fun saveScanResult(result: AcneScanResult, scannedAt: Instant): Result<Unit> {
                    saves++
                    return Result.success(Unit)
                }
            },
        )

        viewModel.analyzeReference("photo")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, saves)
        assertEquals(emptyResult, viewModel.uiState.value.result)
        assertEquals(ScanSaveState.Saved, viewModel.uiState.value.saveState)
    }

    @Test fun failedPersistence_keepsCompletedResultVisible() = runTest(dispatcher) {
        val viewModel = ScanViewModel(
            repository { emptyResult },
            object : ScanProfileRepository {
                override suspend fun saveScanResult(result: AcneScanResult, scannedAt: Instant) =
                    Result.failure<Unit>(IOException("network unavailable"))
            },
        )

        viewModel.analyzeReference("photo")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyResult, viewModel.uiState.value.result)
        assertTrue(viewModel.uiState.value.saveState is ScanSaveState.Failed)
    }

    private fun viewModel(block: suspend () -> AcneScanResult) = ScanViewModel(
        repository(block),
        scanProfileRepository(),
    )

    private fun viewModelResult(block: suspend () -> Result<AcneScanResult>) =
        ScanViewModel(repositoryResult(block), scanProfileRepository())

    private fun scanProfileRepository() = object : ScanProfileRepository {
        override suspend fun saveScanResult(result: AcneScanResult, scannedAt: Instant) = Result.success(Unit)
    }

    private fun repository(block: suspend () -> AcneScanResult) = object : AcneScanRepository {
        override suspend fun analyze(photoReference: String) = Result.success(block())
    }

    private fun repositoryResult(block: suspend () -> Result<AcneScanResult>) = object : AcneScanRepository {
        override suspend fun analyze(photoReference: String) = block()
    }

}

// Scan photos are privacy-sensitive; cache cleanup must only ever target a file this app
// itself wrote for a scan capture, never a user's gallery photo.
class ScanCacheFileOwnershipTest {
    @Test fun ownScanCaptureFileName_isRecognized() {
        assertTrue(isOwnedScanCacheFileName("weglow_scan_1699999999999.jpg"))
    }

    @Test fun unrelatedFileName_isNotTreatedAsOwned() {
        assertFalse(isOwnedScanCacheFileName("IMG_20240101_120000.jpg"))
        assertFalse(isOwnedScanCacheFileName("profile-camera-photo.jpg"))
        assertFalse(isOwnedScanCacheFileName(""))
    }
}
