package com.example.weglow.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.domain.model.AgeRange
import com.example.weglow.domain.model.UserProfile
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val fullName: String = "",
    val ageRange: String? = null,
    val skinType: String? = null,
    val gender: String? = null,
    val isSkinSensitive: Boolean? = null,
    val isSaving: Boolean = false,
    val saveCompleted: Boolean = false,
    val errorMessage: String? = null,
)

class OnboardingViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /**
     * Begins (or restarts) onboarding with a clean state so answers from a previous
     * attempt or a different user can never survive. [fullName] is the name captured
     * during manual signup; when it is absent (sign-in, Google, restored session) the
     * name falls back to the authenticated identity provider's metadata, otherwise "".
     */
    fun start(fullName: String? = null) {
        val resolvedName = fullName?.trim()?.takeIf { it.isNotBlank() }
            ?: authRepository.currentUserDisplayName()?.trim()?.takeIf { it.isNotBlank() }
        _uiState.value = OnboardingUiState(fullName = resolvedName.orEmpty())
    }
    /**
     * Only accepts a value from [AgeRange.OPTIONS] - WeGlow's minimum age of 14 means "Under 14"
     * (and anything else outside that list) is not a value this app will save, regardless of
     * whether the UI still offers it. This is the single point every age answer passes through
     * on its way into the saved profile, so guarding here is enough to keep an under-14 value
     * from ever being submitted through the normal application flow.
     */
    fun setAge(value: String) {
        if (value !in AgeRange.OPTIONS) return
        _uiState.value = _uiState.value.copy(ageRange = value)
    }
    fun setSkinType(value: String?) { _uiState.value = _uiState.value.copy(skinType = value) }
    fun setGender(value: String) { _uiState.value = _uiState.value.copy(gender = value) }

    fun setSensitivityAndSave(value: Boolean) {
        _uiState.value = _uiState.value.copy(isSkinSensitive = value)
        save()
    }

    private fun save() {
        val userId = authRepository.currentUserId()
        if (userId == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Your session is no longer active.")
            return
        }
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, errorMessage = null)
            profileRepository.saveProfile(
                UserProfile(
                    id = userId,
                    fullName = state.fullName.ifBlank { null },
                    ageRange = state.ageRange,
                    skinType = state.skinType,
                    gender = state.gender,
                    isSkinSensitive = state.isSkinSensitive,
                    onboardingCompleted = true,
                )
            ).onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false, saveCompleted = true)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = it.message ?: "Unable to save profile.")
            }
        }
    }

    fun consumeSaveCompleted() { _uiState.value = _uiState.value.copy(saveCompleted = false) }
}
