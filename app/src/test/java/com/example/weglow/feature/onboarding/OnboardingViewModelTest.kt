package com.example.weglow.feature.onboarding

import com.example.weglow.domain.model.UserProfile
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun finalAnswer_savesAllAnswersForSignedInUser() = runTest(dispatcher) {
        val profiles = RecordingProfileRepository()
        val viewModel = OnboardingViewModel(SignedInAuthRepository(), profiles)

        viewModel.start("Alex Doe")
        viewModel.setAge("25-34")
        viewModel.setSkinType("Combination")
        viewModel.setGender("Non-binary")
        viewModel.setSensitivityAndSave(true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            UserProfile(
                id = "signed-in-user",
                fullName = "Alex Doe",
                ageRange = "25-34",
                skinType = "Combination",
                gender = "Non-binary",
                isSkinSensitive = true,
            ),
            profiles.savedProfile,
        )
        assertTrue(viewModel.uiState.value.saveCompleted)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}

private class SignedInAuthRepository : AuthRepository {
    override suspend fun signUp(email: String, password: String) = Result.success(Unit)
    override suspend fun signIn(email: String, password: String) = Result.success(Unit)
    override suspend fun signOut() = Result.success(Unit)
    override fun currentUserId(): String = "signed-in-user"
    override fun hasActiveSession(): Boolean = true
}

private class RecordingProfileRepository : ProfileRepository {
    var savedProfile: UserProfile? = null

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> {
        savedProfile = profile
        return Result.success(Unit)
    }

    override suspend fun hasCompletedOnboarding(userId: String) = Result.success(false)
}
