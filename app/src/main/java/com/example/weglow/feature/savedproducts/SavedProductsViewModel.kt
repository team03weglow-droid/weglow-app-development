package com.example.weglow.feature.savedproducts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.domain.model.Product
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.SavedProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SavedProductsUiState(
    val isLoading: Boolean = false,
    val products: List<Product> = emptyList(),
    val errorMessage: String? = null,
    /** [Product.catalogNo] values with a save/remove currently in flight - guards double-taps. */
    val pendingProductNumbers: Set<Int> = emptySet(),
) {
    val savedProductNumbers: Set<Int> get() = products.mapNotNull(Product::catalogNo).toSet()
}

/**
 * Owns the authenticated user's saved products end to end. Shared between Discover (the
 * heart/save toggle on each product card) and the Saved Products screen, so both always show
 * the exact same state and never run their own separate save/remove logic.
 */
class SavedProductsViewModel(
    private val authRepository: AuthRepository,
    private val savedProductRepository: SavedProductRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedProductsUiState())
    val uiState: StateFlow<SavedProductsUiState> = _uiState.asStateFlow()

    fun load() {
        val userId = authRepository.currentUserId()
        if (userId == null) {
            _uiState.value = SavedProductsUiState()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            savedProductRepository.getSavedProducts(userId).fold(
                onSuccess = { products ->
                    _uiState.value = _uiState.value.copy(isLoading = false, products = products)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "We couldn't load your saved products.",
                    )
                },
            )
        }
    }

    /** Saves [product] if it is not currently saved, otherwise removes it. */
    fun toggleSaved(product: Product) {
        if (product.catalogNo in _uiState.value.savedProductNumbers) remove(product) else save(product)
    }

    fun remove(product: Product) {
        val userId = authRepository.currentUserId() ?: return
        val productNo = product.catalogNo ?: return
        if (productNo in _uiState.value.pendingProductNumbers) return

        _uiState.value = _uiState.value.copy(
            pendingProductNumbers = _uiState.value.pendingProductNumbers + productNo,
            errorMessage = null,
        )
        viewModelScope.launch {
            val result = savedProductRepository.removeSavedProduct(userId, productNo)
            _uiState.value = _uiState.value.copy(
                pendingProductNumbers = _uiState.value.pendingProductNumbers - productNo,
            )
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        products = _uiState.value.products.filterNot { it.catalogNo == productNo },
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error.message ?: "Couldn't remove this product.",
                    )
                },
            )
        }
    }

    private fun save(product: Product) {
        val userId = authRepository.currentUserId() ?: return
        val productNo = product.catalogNo ?: return
        if (productNo in _uiState.value.pendingProductNumbers) return

        _uiState.value = _uiState.value.copy(
            pendingProductNumbers = _uiState.value.pendingProductNumbers + productNo,
            errorMessage = null,
        )
        viewModelScope.launch {
            val result = savedProductRepository.saveProduct(userId, productNo)
            _uiState.value = _uiState.value.copy(
                pendingProductNumbers = _uiState.value.pendingProductNumbers - productNo,
            )
            result.fold(
                onSuccess = {
                    // Optimistic append using the caller's already-loaded catalog Product (e.g.
                    // Discover's own list) instead of a redundant reload; the database's
                    // UNIQUE(profile_id, product_no) plus the ignore-duplicates upsert already
                    // make a repeat save impossible to duplicate server-side.
                    if (productNo !in _uiState.value.savedProductNumbers) {
                        _uiState.value = _uiState.value.copy(products = _uiState.value.products + product)
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error.message ?: "Couldn't save this product.",
                    )
                },
            )
        }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /** Resets to a clean slate on sign-out so the next signed-in user never sees a stale list. */
    fun clear() {
        _uiState.value = SavedProductsUiState()
    }
}
