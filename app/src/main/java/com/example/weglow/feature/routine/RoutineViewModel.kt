package com.example.weglow.feature.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.domain.model.RoutinePlan
import com.example.weglow.domain.recommendation.RecommendationEngine
import com.example.weglow.domain.recommendation.RoutineBuilder
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.CatalogRepository
import com.example.weglow.domain.repository.ProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RoutineUiState(
    val isLoading: Boolean = true,
    val plan: RoutinePlan? = null,
    val errorMessage: String? = null,
)

/**
 * Builds the user's morning/evening routine from their real profile and the real Supabase
 * catalog. Independent of any single scan session - a routine is a standing daily structure,
 * not a one-off scan result.
 */
class RoutineViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoutineUiState())
    val uiState: StateFlow<RoutineUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedUserId: String? = null

    /**
     * Depends on the same catalog fetch as Discover, so a successful plan is kept and reused
     * for the lifetime of this ViewModel rather than re-querying every time the Routines tab
     * is revisited. A failed load still retries.
     */
    fun load() {
        val currentUserId = authRepository.currentUserId()
        if (loadedUserId != currentUserId) {
            loadJob?.cancel()
            _uiState.value = RoutineUiState()
            loadedUserId = currentUserId
        }
        if (loadJob?.isActive == true) return
        if (_uiState.value.plan != null && _uiState.value.errorMessage == null) return

        loadJob = viewModelScope.launch {
            _uiState.value = RoutineUiState(isLoading = true)

            val userId = authRepository.currentUserId()
            if (userId == null) {
                _uiState.value = RoutineUiState(
                    isLoading = false,
                    errorMessage = "Sign in to see your routine.",
                )
                return@launch
            }

            val profileResult = profileRepository.getProfile(userId)
            if (profileResult.isFailure) {
                _uiState.value = RoutineUiState(
                    isLoading = false,
                    errorMessage = profileResult.exceptionOrNull()?.message
                        ?: "We couldn't load your profile.",
                )
                return@launch
            }

            catalogRepository.products().fold(
                onSuccess = { products ->
                    val recommendations = RecommendationEngine.recommend(profileResult.getOrNull(), null, products)
                        .recommendations
                    val plan = RoutineBuilder.build(recommendations, products)
                    _uiState.value = RoutineUiState(isLoading = false, plan = plan)
                },
                onFailure = { error ->
                    _uiState.value = RoutineUiState(
                        isLoading = false,
                        errorMessage = error.message ?: "Products could not be loaded.",
                    )
                },
            )
        }
    }
}
