package com.example.weglow.feature.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScanUiState(val photoUri: Uri? = null)

class ScanViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun setPhoto(uri: Uri) { _uiState.value = ScanUiState(photoUri = uri) }
    fun clear() { _uiState.value = ScanUiState() }
}
