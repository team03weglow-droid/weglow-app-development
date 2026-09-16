package com.example.weglow.feature.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.domain.model.CartItem
import com.example.weglow.domain.model.Product
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.CartRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CartUiState(
    val isLoading: Boolean = false,
    val items: List<CartItem> = emptyList(),
    val errorMessage: String? = null,
    /** [Product.catalogNo] values with a cart change currently in flight - guards double-taps. */
    val pendingProductNumbers: Set<Int> = emptySet(),
) {
    /** Computed from each line's CURRENT catalog price, never a price captured at add-time. */
    val subtotalLkr: Double get() = items.sumOf { (it.product.priceLkr ?: 0.0) * it.quantity }
    val itemCount: Int get() = items.sumOf { it.quantity }
}

/**
 * Owns the authenticated user's cart end to end. Shared between Discover ("Add to Bag"), Saved
 * Products ("Add to Cart"), and the Cart screen itself, so there is exactly one cart-mutation
 * path in the app.
 */
class CartViewModel(
    private val authRepository: AuthRepository,
    private val cartRepository: CartRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CartUiState())
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    fun load() {
        val userId = authRepository.currentUserId()
        if (userId == null) {
            _uiState.value = CartUiState()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            cartRepository.getCartItems(userId).fold(
                onSuccess = { items ->
                    _uiState.value = _uiState.value.copy(isLoading = false, items = items)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "We couldn't load your cart.",
                    )
                },
            )
        }
    }

    /** Adds one unit of [product]; increments the existing line if it is already in the cart. */
    fun addToCart(product: Product) {
        val userId = authRepository.currentUserId() ?: return
        val productNo = product.catalogNo ?: return
        runChange(productNo) { cartRepository.addToCart(userId, productNo) }
    }

    fun incrementQuantity(item: CartItem) {
        val userId = authRepository.currentUserId() ?: return
        val productNo = item.product.catalogNo ?: return
        runChange(productNo) { cartRepository.updateQuantity(userId, productNo, item.quantity + 1) }
    }

    /**
     * Decrementing a line already at quantity 1 removes it entirely rather than allowing a
     * zero/negative quantity - this is the one consistent decrement behavior this screen (and
     * any future one reusing this ViewModel) follows.
     */
    fun decrementQuantity(item: CartItem) {
        val userId = authRepository.currentUserId() ?: return
        val productNo = item.product.catalogNo ?: return
        if (item.quantity <= 1) {
            runChange(productNo) { cartRepository.removeFromCart(userId, productNo) }
        } else {
            runChange(productNo) { cartRepository.updateQuantity(userId, productNo, item.quantity - 1) }
        }
    }

    fun removeFromCart(item: CartItem) {
        val userId = authRepository.currentUserId() ?: return
        val productNo = item.product.catalogNo ?: return
        runChange(productNo) { cartRepository.removeFromCart(userId, productNo) }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /** Resets to a clean slate on sign-out so the next signed-in user never sees a stale cart. */
    fun clear() {
        _uiState.value = CartUiState()
    }

    /**
     * Runs one cart-mutating [operation] for [productNo]: guards against a second tap while one
     * is already in flight, then reloads the authoritative cart from the repository on success
     * (never assumes the resulting quantity locally, since [CartRepository.addToCart] may have
     * incremented an existing line rather than created a new one).
     */
    private fun runChange(productNo: Int, operation: suspend () -> Result<Unit>) {
        if (productNo in _uiState.value.pendingProductNumbers) return
        _uiState.value = _uiState.value.copy(
            pendingProductNumbers = _uiState.value.pendingProductNumbers + productNo,
            errorMessage = null,
        )
        viewModelScope.launch {
            val result = operation()
            _uiState.value = _uiState.value.copy(
                pendingProductNumbers = _uiState.value.pendingProductNumbers - productNo,
            )
            result.fold(
                onSuccess = { load() },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error.message ?: "Couldn't update your cart.",
                    )
                },
            )
        }
    }
}
