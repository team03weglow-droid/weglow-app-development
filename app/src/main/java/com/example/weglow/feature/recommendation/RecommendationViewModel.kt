package com.example.weglow.feature.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.domain.model.RecommendationResult
import com.example.weglow.domain.recommendation.RecommendationEngine
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.CatalogRepository
import com.example.weglow.domain.repository.ProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecommendationUiState(
    val isLoading: Boolean = true,
    val result: RecommendationResult? = null,
    val errorMessage: String? = null,
)

/**
 * Loads the signed-in user's real profile and the real Supabase catalog, then runs the
 * deterministic [RecommendationEngine] over them. Never touches Supabase directly and never
 * fabricates a result when either dependency fails.
 */
class RecommendationViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecommendationUiState())
    val uiState: StateFlow<RecommendationUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /**
     * @param scanConcerns null means this request is profile-only (no scan was used); an
     * empty list means a scan was used but detected no concerns.
     */
    fun load(scanConcerns: List<String>? = null) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _uiState.value = RecommendationUiState(isLoading = true)

            val userId = authRepository.currentUserId()
            if (userId == null) {
                _uiState.value = RecommendationUiState(
                    isLoading = false,
                    errorMessage = "Sign in to see your personalized recommendations.",
                )
                return@launch
            }

            val profileResult = profileRepository.getProfile(userId)
            if (profileResult.isFailure) {
                _uiState.value = RecommendationUiState(
                    isLoading = false,
                    errorMessage = profileResult.exceptionOrNull()?.message
                        ?: "We couldn't load your profile.",
                )
                return@launch
            }

            catalogRepository.products().fold(
                onSuccess = { products ->
                    val result = RecommendationEngine.recommend(profileResult.getOrNull(), scanConcerns, products)
                    _uiState.value = RecommendationUiState(isLoading = false, result = result)
                },
                onFailure = { error ->
                    _uiState.value = RecommendationUiState(
                        isLoading = false,
                        errorMessage = error.message ?: "Products could not be loaded.",
                    )
                },
            )
        }
    }
}
