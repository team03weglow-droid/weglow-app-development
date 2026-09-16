package com.example.weglow.feature.cart

import com.example.weglow.domain.model.CartItem
import com.example.weglow.domain.model.Product
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.AuthenticationState
import com.example.weglow.domain.repository.CartRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun product(no: Int, priceLkr: Double = 1000.0) =
        Product(id = "p-$no", name = "Product $no", priceLabel = "LKR $priceLkr", imageUrl = null, priceLkr = priceLkr, catalogNo = no)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // 1. Loading, then success.
    @Test
    fun load_success_populatesItemsAndClearsLoading() = runTest(dispatcher) {
        val items = listOf(CartItem(product(1), 2), CartItem(product(2), 1))
        val vm = CartViewModel(FakeAuthRepository(userId = "u1"), FakeCartRepository(stored = items))

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(items, vm.uiState.value.items)
        assertFalse(vm.uiState.value.isLoading)
    }

    // 2. Empty state.
    @Test
    fun load_withEmptyCart_leavesItemsEmpty() = runTest(dispatcher) {
        val vm = CartViewModel(FakeAuthRepository(userId = "u1"), FakeCartRepository())

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.items.isEmpty())
        assertEquals(0.0, vm.uiState.value.subtotalLkr, 0.0)
    }

    // 3. Repository failure surfaces as an error.
    @Test
    fun load_repositoryFailure_surfacesError() = runTest(dispatcher) {
        val vm = CartViewModel(
            FakeAuthRepository(userId = "u1"),
            FakeCartRepository(getResult = Result.failure(RuntimeException("offline"))),
        )

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("offline", vm.uiState.value.errorMessage)
    }

    // 4. Adding a brand-new product calls the repository and reloads the cart.
    @Test
    fun addToCart_newProduct_addsAndReloads() = runTest(dispatcher) {
        val repo = FakeCartRepository()
        val vm = CartViewModel(FakeAuthRepository(userId = "u1"), repo)

        vm.addToCart(product(3))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(3), repo.addedProductNumbers)
        assertEquals(1, vm.uiState.value.items.size)
        assertEquals(1, vm.uiState.value.items.first().quantity)
    }

    // 5. Adding an already-present product increments its quantity rather than creating a
    // second UI/database row - the repository owns the increment; the ViewModel just reloads.
    @Test
    fun addToCart_existingProduct_incrementsRatherThanDuplicates() = runTest(dispatcher) {
        val repo = FakeCartRepository(stored = listOf(CartItem(product(4), 1)))
        val vm = CartViewModel(FakeAuthRepository(userId = "u1"), repo)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        vm.addToCart(product(4))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.items.size) // still one line, not two
        assertEquals(2, vm.uiState.value.items.first().quantity)
    }

    // 6. Explicit increment.
    @Test
    fun incrementQuantity_increasesByOne() = runTest(dispatcher) {
        val repo = FakeCartRepository(stored = listOf(CartItem(product(5), 1)))
        val vm = CartViewModel(FakeAuthRepository(userId = "u1"), repo)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        vm.incrementQuantity(vm.uiState.value.items.first())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, vm.uiState.value.items.first().quantity)
    }

    // 7. Decrementing above 1 reduces the quantity, never removes the line.
    @Test
    fun decrementQuantity_above1_reducesQuantity() = runTest(dispatcher) {
        val repo = FakeCartRepository(stored = listOf(CartItem(product(6), 3)))
        val vm = CartViewModel(FakeAuthRepository(userId = "u1"), repo)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        vm.decrementQuantity(vm.uiState.value.items.first())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.items.size)
        assertEquals(2, vm.uiState.value.items.first().quantity)
        assertTrue(repo.removedProductNumbers.isEmpty())
    }

    // 8. Decrementing from 1 removes the line entirely - the one documented, consistent
    // behavior for reaching zero, instead of ever allowing a quantity <= 0.
    @Test
    fun decrementQuantity_from1_removesLineInsteadOfZeroQuantity() = runTest(dispatcher) {
        val repo = FakeCartRepository(stored = listOf(CartItem(product(7), 1)))
        val vm = CartViewModel(FakeAuthRepository(userId = "u1"), repo)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        vm.decrementQuantity(vm.uiState.value.items.first())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(7), repo.removedProductNumbers)
        assertTrue(repo.updatedQuantities.isEmpty())
        assertTrue(vm.uiState.value.items.isEmpty())
    }

    // 9. Explicit removal.
    @Test
    fun removeFromCart_removesTheLine() = runTest(dispatcher) {
        val repo = FakeCartRepository(stored = listOf(CartItem(product(8), 4)))
        val vm = CartViewModel(FakeAuthRepository(userId = "u1"), repo)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        vm.removeFromCart(vm.uiState.value.items.first())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(8), repo.removedProductNumbers)
        assertTrue(vm.uiState.value.items.isEmpty())
    }

    // 10. A failed mutation surfaces an error and does not silently change the cart.
    @Test
    fun incrementQuantity_repositoryFailure_reportsErrorAndKeepsPreviousState() = runTest(dispatcher) {
        val repo = FakeCartRepository(
            stored = listOf(CartItem(product(9), 1)),
            updateResult = Result.failure(RuntimeException("quantity rejected")),
        )
        val vm = CartViewModel(FakeAuthRepository(userId = "u1"), repo)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        vm.incrementQuantity(vm.uiState.value.items.first())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("quantity rejected", vm.uiState.value.errorMessage)
        assertEquals(1, vm.uiState.value.items.first().quantity) // unchanged
    }

    // 11. Subtotal is computed from each line's CURRENT catalog price, not a stored/snapshot one.
    @Test
    fun subtotal_isComputedFromCurrentCatalogPricesAndQuantities() = runTest(dispatcher) {
        val items = listOf(
            CartItem(product(1, priceLkr = 1500.0), 2), // 3000
            CartItem(product(2, priceLkr = 800.0), 3),  // 2400
        )
        val vm = CartViewModel(FakeAuthRepository(userId = "u1"), FakeCartRepository(stored = items))

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(5400.0, vm.uiState.value.subtotalLkr, 0.001)
    }

    // 12. A duplicate tap while a change for the same product is in flight is ignored.
    @Test
    fun addToCart_duplicateTapWhileInFlight_isIgnored() = runTest(dispatcher) {
        val repo = FakeCartRepository()
        val vm = CartViewModel(FakeAuthRepository(userId = "u1"), repo)
        val p = product(10)

        vm.addToCart(p)
        vm.addToCart(p)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.addedProductNumbers.size)
    }

    @Test
    fun consumeError_clearsError() = runTest(dispatcher) {
        val vm = CartViewModel(
            FakeAuthRepository(userId = "u1"),
            FakeCartRepository(getResult = Result.failure(RuntimeException("boom"))),
        )
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        vm.consumeError()

        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun clear_resetsToCleanSlate() = runTest(dispatcher) {
        val vm = CartViewModel(
            FakeAuthRepository(userId = "u1"),
            FakeCartRepository(stored = listOf(CartItem(product(1), 1))),
        )
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        vm.clear()

        assertEquals(CartUiState(), vm.uiState.value)
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

private class FakeCartRepository(
    stored: List<CartItem> = emptyList(),
    private val getResult: Result<List<CartItem>>? = null,
    private val addResult: Result<Unit> = Result.success(Unit),
    private val updateResult: Result<Unit> = Result.success(Unit),
    private val removeResult: Result<Unit> = Result.success(Unit),
) : CartRepository {

    private val itemsByNo = stored.associateBy { requireNotNull(it.product.catalogNo) }.toMutableMap()

    val addedProductNumbers = mutableListOf<Int>()
    val updatedQuantities = mutableListOf<Pair<Int, Int>>()
    val removedProductNumbers = mutableListOf<Int>()

    override suspend fun getCartItems(userId: String): Result<List<CartItem>> =
        getResult ?: Result.success(itemsByNo.values.toList())

    override suspend fun addToCart(userId: String, productNo: Int): Result<Unit> {
        addedProductNumbers += productNo
        if (addResult.isSuccess) {
            val existing = itemsByNo[productNo]
            itemsByNo[productNo] = if (existing != null) {
                existing.copy(quantity = existing.quantity + 1)
            } else {
                // A fresh add needs a real Product; tests exercising this path provide one
                // via `stored` first, or accept a minimal placeholder here.
                CartItem(
                    product = com.example.weglow.domain.model.Product(
                        id = "p-$productNo", name = "Product $productNo", priceLabel = "LKR 1000",
                        imageUrl = null, priceLkr = 1000.0, catalogNo = productNo,
                    ),
                    quantity = 1,
                )
            }
        }
        return addResult
    }

    override suspend fun updateQuantity(userId: String, productNo: Int, quantity: Int): Result<Unit> {
        updatedQuantities += productNo to quantity
        if (updateResult.isSuccess) {
            itemsByNo[productNo]?.let { itemsByNo[productNo] = it.copy(quantity = quantity) }
        }
        return updateResult
    }

    override suspend fun removeFromCart(userId: String, productNo: Int): Result<Unit> {
        removedProductNumbers += productNo
        if (removeResult.isSuccess) itemsByNo.remove(productNo)
        return removeResult
    }
}
