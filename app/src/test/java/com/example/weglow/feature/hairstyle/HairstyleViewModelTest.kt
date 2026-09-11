package com.example.weglow.feature.hairstyle

import com.example.weglow.domain.model.HairstyleResult
import com.example.weglow.domain.repository.HairstyleRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class HairstyleViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val result = HairstyleResult("Oval", 91, listOf("Balanced"), "Result", emptyList())

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun publishesRealModelResult() = runTest(dispatcher) {
        val prediction = CompletableDeferred<HairstyleResult>()
        val viewModel = HairstyleViewModel(repository { _, _ -> prediction.await() })

        viewModel.analyze("photo", "Female")
        dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isAnalyzing)
        prediction.complete(result)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(result, viewModel.uiState.value.result)
        assertFalse(viewModel.uiState.value.isAnalyzing)
    }

    @Test fun failureCanBeRetriedWithoutStaleResult() = runTest(dispatcher) {
        var shouldFail = true
        val viewModel = HairstyleViewModel(repository { _, _ ->
            if (shouldFail) error("model failed") else result
        })

        viewModel.analyze("photo", null)
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.result)

        shouldFail = false
        viewModel.analyze("photo", null)
        assertNull(viewModel.uiState.value.error)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(result, viewModel.uiState.value.result)
    }

    private fun repository(
        block: suspend (String, String?) -> HairstyleResult,
    ) = object : HairstyleRepository {
        override suspend fun analyze(photoReference: String, gender: String?) =
            block(photoReference, gender)
    }
}
