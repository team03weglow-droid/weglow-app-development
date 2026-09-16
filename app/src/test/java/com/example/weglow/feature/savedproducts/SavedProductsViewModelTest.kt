package com.example.weglow.feature.savedproducts

import com.example.weglow.domain.model.Product
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.AuthenticationState
import com.example.weglow.domain.repository.SavedProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class SavedProductsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun product(no: Int, name: String = "Product $no") =
        Product(id = "p-$no", name = name, priceLabel = "LKR 1000", imageUrl = null, catalogNo = no)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // 1. Loading state is surfaced, then cleared once the load completes.
    @Test
    fun load_success_populatesProductsAndClearsLoading() = runTest(dispatcher) {
        val vm = SavedProductsViewModel(
            FakeAuthRepository(userId = "u1"),
            FakeSavedProductRepository(stored = listOf(product(1), product(2))),
        )

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(product(1), product(2)), vm.uiState.value.products)
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.errorMessage)
    }

    // 2. Empty state: no saved products, no error.
    @Test
    fun load_withNoSavedProducts_leavesListEmpty() = runTest(dispatcher) {
        val vm = SavedProductsViewModel(FakeAuthRepository(userId = "u1"), FakeSavedProductRepository())

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.products.isEmpty())
        assertNull(vm.uiState.value.errorMessage)
    }

    // 3. Repository failure surfaces as an error, not a crash or silently empty state.
    @Test
    fun load_repositoryFailure_surfacesError() = runTest(dispatcher) {
        val vm = SavedProductsViewModel(
            FakeAuthRepository(userId = "u1"),
            FakeSavedProductRepository(getResult = Result.failure(RuntimeException("network down"))),
        )

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("network down", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isLoading)
    }

    // 4. Saving a product persists it and optimistically reflects it in state.
    @Test
    fun toggleSaved_notYetSaved_savesAndAppends() = runTest(dispatcher) {
        val repo = FakeSavedProductRepository()
        val vm = SavedProductsViewModel(FakeAuthRepository(userId = "u1"), repo)
        val newProduct = product(5)

        vm.toggleSaved(newProduct)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(5), repo.savedProductNumbers)
        assertEquals("u1", repo.savedForUserId)
        assertTrue(newProduct.catalogNo in vm.uiState.value.savedProductNumbers)
    }

    // 5. Toggling an already-saved product removes it instead.
    @Test
    fun toggleSaved_alreadySaved_removesIt() = runTest(dispatcher) {
        val existing = product(7)
        val repo = FakeSavedProductRepository(stored = listOf(existing))
        val vm = SavedProductsViewModel(FakeAuthRepository(userId = "u1"), repo)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        vm.toggleSaved(existing)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(7), repo.removedProductNumbers)
        assertTrue(vm.uiState.value.products.isEmpty())
    }

    // 6. A failed remove does not falsely drop the product from the visible list.
    @Test
    fun remove_repositoryFailure_keepsProductVisibleAndReportsError() = runTest(dispatcher) {
        val existing = product(9)
        val repo = FakeSavedProductRepository(
            stored = listOf(existing),
            removeResult = Result.failure(RuntimeException("db write denied")),
        )
        val vm = SavedProductsViewModel(FakeAuthRepository(userId = "u1"), repo)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        vm.remove(existing)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(existing), vm.uiState.value.products)
        assertEquals("db write denied", vm.uiState.value.errorMessage)
    }

    // 7. A duplicate tap while a save for the same product is still in flight is ignored -
    // the pending-set guard, not just the database's unique constraint, prevents a second
    // request (and therefore any chance of a duplicate list entry) from ever being sent.
    @Test
    fun toggleSaved_duplicateTapWhileSaveInFlight_isIgnored() = runTest(dispatcher) {
        val repo = FakeSavedProductRepository()
        val vm = SavedProductsViewModel(FakeAuthRepository(userId = "u1"), repo)
        val newProduct = product(11)

        vm.toggleSaved(newProduct)
        vm.toggleSaved(newProduct) // fired before the first call's coroutine has resumed
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.savedProductNumbers.size)
        assertEquals(1, vm.uiState.value.products.count { it.catalogNo == 11 })
    }

    // 8. A product without a resolvable catalogNo can never be saved (never fabricated).
    @Test
    fun toggleSaved_productWithoutCatalogNo_isNoOp() = runTest(dispatcher) {
        val repo = FakeSavedProductRepository()
        val vm = SavedProductsViewModel(FakeAuthRepository(userId = "u1"), repo)
        val productWithoutNo = Product(id = "p-x", name = "No Catalog No", priceLabel = "LKR 500", imageUrl = null)

        vm.toggleSaved(productWithoutNo)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(repo.savedProductNumbers.isEmpty())
        assertTrue(vm.uiState.value.products.isEmpty())
    }

    @Test
    fun consumeError_clearsError() = runTest(dispatcher) {
        val vm = SavedProductsViewModel(
            FakeAuthRepository(userId = "u1"),
            FakeSavedProductRepository(getResult = Result.failure(RuntimeException("boom"))),
        )
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.errorMessage)

        vm.consumeError()

        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun clear_resetsToCleanSlate() = runTest(dispatcher) {
        val vm = SavedProductsViewModel(
            FakeAuthRepository(userId = "u1"),
            FakeSavedProductRepository(stored = listOf(product(1))),
        )
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        vm.clear()

        assertEquals(SavedProductsUiState(), vm.uiState.value)
    }
}

private class FakeAuthRepository(private val userId: String?) : AuthRepository {
    override val authenticationState: Flow<AuthenticationState> =
        MutableStateFlow(
            if (userId == null) AuthenticationState.NOT_AUTHENTICATED else AuthenticationState.AUTHENTICATED,
        )

    override suspend fun signUp(email: String, password: String) = Result.success(Unit)
    override suspend fun signIn(email: String, password: String) = Result.success(Unit)
    override suspend fun signInWithGoogle() = Result.success(Unit)
    override suspend fun signOut() = Result.success(Unit)
    override fun currentUserId(): String? = userId
    override fun hasActiveSession(): Boolean = userId != null
    override fun currentUserDisplayName(): String? = null
    override fun currentUserEmail(): String? = null
}

private class FakeSavedProductRepository(
    stored: List<Product> = emptyList(),
    private val getResult: Result<List<Product>>? = null,
    private val saveResult: Result<Unit> = Result.success(Unit),
    private val removeResult: Result<Unit> = Result.success(Unit),
) : SavedProductRepository {

    private val storedByNo = stored.associateBy { requireNotNull(it.catalogNo) }.toMutableMap()

    val savedProductNumbers = mutableListOf<Int>()
    val removedProductNumbers = mutableListOf<Int>()
    var savedForUserId: String? = null
        private set

    override suspend fun getSavedProducts(userId: String): Result<List<Product>> =
        getResult ?: Result.success(storedByNo.values.toList())

    override suspend fun saveProduct(userId: String, productNo: Int): Result<Unit> {
        savedForUserId = userId
        savedProductNumbers += productNo
        return saveResult
    }

    override suspend fun removeSavedProduct(userId: String, productNo: Int): Result<Unit> {
        removedProductNumbers += productNo
        return removeResult
    }
}
