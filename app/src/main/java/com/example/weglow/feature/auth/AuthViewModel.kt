package com.example.weglow.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val event: AuthEvent? = null,
)

sealed interface AuthEvent {
    data class SignedIn(val destination: StartupDestination) : AuthEvent
    data class SignedUp(val fullName: String) : AuthEvent
    data object SignedOut : AuthEvent
}

enum class StartupDestination { LOGIN, ONBOARDING, HOME }

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _startupDestination = MutableStateFlow<StartupDestination?>(null)
    val startupDestination: StateFlow<StartupDestination?> = _startupDestination.asStateFlow()

    init {
        resolveStartupDestination()
    }

    fun resolveStartupDestination() {
        viewModelScope.launch {
            runCatching {
                val userId = authRepository.currentUserId()

                if (!authRepository.hasActiveSession() || userId == null) {
                    StartupDestination.LOGIN
                } else {
                    val completed = profileRepository
                        .hasCompletedOnboarding(userId)
                        .getOrDefault(false)

                    if (completed) {
                        StartupDestination.HOME
                    } else {
                        StartupDestination.ONBOARDING
                    }
                }
            }.onSuccess { destination ->
                _startupDestination.value = destination
            }.onFailure {
                _startupDestination.value = StartupDestination.LOGIN
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState(errorMessage = "Enter your email and password.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            authRepository.signIn(email.trim(), password)
                .onSuccess {
                    val userId = authRepository.currentUserId()
                    val completed = userId != null && profileRepository.hasCompletedOnboarding(userId).getOrDefault(false)
                    val destination = if (completed) StartupDestination.HOME else StartupDestination.ONBOARDING
                    _uiState.value = AuthUiState(event = AuthEvent.SignedIn(destination))
                }
                .onFailure { _uiState.value = AuthUiState(errorMessage = it.message ?: "Unable to sign in.") }
        }
    }

    fun signUp(fullName: String, email: String, password: String) {
        if (
            fullName.isBlank() ||
            email.isBlank() ||
            password.length < 8 ||
            password.none { it.isDigit() }
        ) {
            _uiState.value = AuthUiState(
                errorMessage = "Enter your name, a valid email and a password of at least 8 characters with a number."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            authRepository.signUp(email.trim(), password)
                .onSuccess { _uiState.value = AuthUiState(event = AuthEvent.SignedUp(fullName.trim())) }
                .onFailure { _uiState.value = AuthUiState(errorMessage = it.message ?: "Unable to create account.") }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            authRepository.signOut()
                .onSuccess { _uiState.value = AuthUiState(event = AuthEvent.SignedOut) }
                .onFailure { _uiState.value = AuthUiState(errorMessage = it.message ?: "Unable to sign out.") }
        }
    }

    fun consumeEvent() {
        _uiState.value = _uiState.value.copy(event = null)
    }
}
