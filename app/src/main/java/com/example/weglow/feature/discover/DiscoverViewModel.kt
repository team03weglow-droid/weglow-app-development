package com.example.weglow.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.domain.model.Product
import com.example.weglow.domain.repository.CatalogRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val products: List<Product> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

class DiscoverViewModel(
    private val repository: CatalogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /**
     * The catalog can hold 500+ products, so re-querying Supabase every time the user
     * revisits the Discover tab (this is called from a `LaunchedEffect(Unit)` that reruns on
     * every navigation to the tab) would be wasted network/DB work. Once a successful load is
     * in hand it is kept and reused for the lifetime of this ViewModel; a failed load still
     * retries so the existing "Try again" action keeps working.
     */
    fun loadProducts() {
        if (loadJob?.isActive == true) return
        val state = _uiState.value
        if (state.products.isNotEmpty() && state.errorMessage == null) return

        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            repository.products().fold(
                onSuccess = { products ->
                    _uiState.value = DiscoverUiState(products = products, isLoading = false)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Products could not be loaded.",
                    )
                },
            )
        }
    }
}
