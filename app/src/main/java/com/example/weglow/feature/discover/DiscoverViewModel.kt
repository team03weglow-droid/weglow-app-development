package com.example.weglow.feature.discover

import androidx.lifecycle.ViewModel
import com.example.weglow.domain.model.Product
import com.example.weglow.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoverUiState(
    val products: List<Product> = emptyList(),
)

class DiscoverViewModel(repository: CatalogRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(DiscoverUiState(products = repository.products()))
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()
}
