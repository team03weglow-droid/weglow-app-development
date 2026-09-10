package com.example.weglow.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val displayName: String? = null,
    val isLoading: Boolean = false,
)

/**
 * Loads the signed-in user's persisted profile so screens can show the real name
 * instead of a placeholder. All Supabase access stays behind [ProfileRepository];
 * this class only ever touches domain interfaces.
 */
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun refresh() {
        val userId = authRepository.currentUserId()
        if (userId == null) {
            _uiState.value = ProfileUiState()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val persistedName = profileRepository.getProfile(userId)
                .getOrNull()
                ?.fullName
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            val identityName = authRepository.currentUserDisplayName()
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            _uiState.value = ProfileUiState(
                displayName = persistedName ?: identityName,
                isLoading = false,
            )
        }
    }
}
