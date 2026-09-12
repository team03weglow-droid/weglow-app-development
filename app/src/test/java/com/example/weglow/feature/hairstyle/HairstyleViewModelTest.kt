package com.example.weglow.feature.hairstyle

import com.example.weglow.core.image.FaceValidationResult
import com.example.weglow.core.image.FaceValidator
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
        val viewModel = HairstyleViewModel(repository { _, _ -> prediction.await() }, validator())

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
        }, validator())

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

    @Test fun invalidPhotoNeverReachesHairstyleRepository() = runTest(dispatcher) {
        var repositoryCalls = 0
        val viewModel = HairstyleViewModel(
            repository { _, _ ->
                repositoryCalls++
                result
            },
            validator(FaceValidationResult.MultipleFaces),
        )

        viewModel.analyze("photo", null)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repositoryCalls)
        assertEquals(FaceValidationResult.MultipleFaces, viewModel.uiState.value.validationError)
        assertNull(viewModel.uiState.value.error)
    }

    @Test fun validPhotoContinuesToHairstyleRepository() = runTest(dispatcher) {
        var repositoryCalls = 0
        val viewModel = HairstyleViewModel(
            repository { _, _ ->
                repositoryCalls++
                result
            },
            validator(),
        )

        viewModel.analyze("photo", "Female")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repositoryCalls)
        assertEquals(result, viewModel.uiState.value.result)
    }

    @Test fun duplicateAnalyzeRequestIsIgnoredWhileValidationIsRunning() = runTest(dispatcher) {
        val validation = CompletableDeferred<FaceValidationResult>()
        var validationCalls = 0
        var repositoryCalls = 0
        val viewModel = HairstyleViewModel(
            repository { _, _ ->
                repositoryCalls++
                result
            },
            object : FaceValidator {
                override suspend fun validate(photoReference: String): FaceValidationResult {
                    validationCalls++
                    return validation.await()
                }
            },
        )

        viewModel.analyze("photo", null)
        dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isValidating)
        viewModel.analyze("photo", null)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, validationCalls)
        assertEquals(0, repositoryCalls)

        validation.complete(FaceValidationResult.Valid)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repositoryCalls)
    }

    private fun repository(
        block: suspend (String, String?) -> HairstyleResult,
    ) = object : HairstyleRepository {
        override suspend fun analyze(photoReference: String, gender: String?) =
            block(photoReference, gender)
    }

    private fun validator(
        validation: FaceValidationResult = FaceValidationResult.Valid,
    ) = object : FaceValidator {
        override suspend fun validate(photoReference: String) = validation
    }
}
