package com.example.weglow.feature.discover

import com.example.weglow.domain.model.Product
import com.example.weglow.domain.repository.CatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun successfulLoad_exposesProductsAndStopsLoading() = runTest(dispatcher) {
        val expected = listOf(Product("1", "Serum", "LKR 1000", null))
        val viewModel = DiscoverViewModel(FakeCatalogRepository(Result.success(expected)))

        viewModel.loadProducts()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(expected, viewModel.uiState.value.products)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun failedLoad_exposesRetryableError() = runTest(dispatcher) {
        val viewModel = DiscoverViewModel(
            FakeCatalogRepository(Result.failure(IllegalStateException("Access denied")))
        )

        viewModel.loadProducts()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Access denied", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}

private class FakeCatalogRepository(
    private val result: Result<List<Product>>,
) : CatalogRepository {
    override suspend fun products(): Result<List<Product>> = result
}
