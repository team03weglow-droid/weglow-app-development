package com.example.weglow.feature.environment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.data.location.LocationProvider
import com.example.weglow.domain.model.EnvironmentInfo
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.EnvironmentRepository
import com.example.weglow.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

sealed interface EnvironmentUiState {
    data object Loading : EnvironmentUiState
    data object PermissionRequired : EnvironmentUiState
    data class Success(val environment: EnvironmentInfo) : EnvironmentUiState
    data class Error(val message: String) : EnvironmentUiState
}

class EnvironmentViewModel(
    private val locationProvider: LocationProvider,
    private val repository: EnvironmentRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private var refreshJob: Job? = null
    private val _uiState = MutableStateFlow<EnvironmentUiState>(EnvironmentUiState.Loading)
    val uiState: StateFlow<EnvironmentUiState> = _uiState.asStateFlow()

    /** Safe on Home entry/resume: repository caching prevents repeat HTTP requests. */
    fun refresh(hasLocationPermission: Boolean) {
        if (!hasLocationPermission) {
            _uiState.value = EnvironmentUiState.PermissionRequired
            return
        }
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.value = EnvironmentUiState.Loading
            locationProvider.getCurrentLocation().fold(
                onSuccess = { coordinates ->
                    repository.getEnvironmentForLocation(coordinates.latitude, coordinates.longitude).fold(
                        onSuccess = { env ->
                            _uiState.value = EnvironmentUiState.Success(env)
                            authRepository.currentUserId()?.let { userId ->
                                viewModelScope.launch {
                                    runCatching {
                                        profileRepository.updateEnvironment(
                                            userId,
                                            env.uvIndex,
                                            env.uvCategory,
                                            env.humidity,
                                            env.locationName ?: "",
                                        )
                                    }
                                }
                            }
                        },
                        onFailure = { _uiState.value = EnvironmentUiState.Error(it.userMessage()) },
                    )
                },
                onFailure = { _uiState.value = EnvironmentUiState.Error(it.userMessage()) },
            )
        }
    }

    private fun Throwable.userMessage(): String = message?.takeIf(String::isNotBlank)
        ?: "Environmental readings are unavailable right now."
}