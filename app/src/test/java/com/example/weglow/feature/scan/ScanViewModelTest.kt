package com.example.weglow.feature.scan

import com.example.weglow.domain.model.AcneScanResult
import com.example.weglow.domain.repository.AcneScanRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val emptyResult = AcneScanResult(emptyList(), 640, 480, "test", 0.25f)

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun waitsForPredictionAndAcceptsNoDetections() = runTest(dispatcher) {
        val prediction = CompletableDeferred<AcneScanResult>()
        val viewModel = ScanViewModel(repository { prediction.await() })
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
        val viewModel = ScanViewModel(repository {
            if (fail) throw IOException("cannot read photo")
            emptyResult
        })
        viewModel.analyzeReference("photo")
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.error!!.contains("photo"))
        assertNull(viewModel.uiState.value.result)
        fail = false
        viewModel.analyzeReference("photo")
        assertNull(viewModel.uiState.value.error)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptyResult, viewModel.uiState.value.result)
    }

    @Test fun cancellationDoesNotPublishLateResults() = runTest(dispatcher) {
        val prediction = CompletableDeferred<AcneScanResult>()
        val viewModel = ScanViewModel(repository { prediction.await() })
        viewModel.analyzeReference("photo")
        dispatcher.scheduler.runCurrent()
        viewModel.cancelAnalysis()
        prediction.complete(emptyResult)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.result)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isAnalyzing)
    }

    private fun repository(block: suspend () -> AcneScanResult) = object : AcneScanRepository {
        override suspend fun analyze(photoReference: String) = block()
    }
}
