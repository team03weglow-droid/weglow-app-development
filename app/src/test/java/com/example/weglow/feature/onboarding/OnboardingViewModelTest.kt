package com.example.weglow.feature.onboarding

import com.example.weglow.domain.model.UserProfile
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.AuthenticationState
import com.example.weglow.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    // 1. Onboarding answers are persisted with the authenticated user ID.
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
                onboardingCompleted = true,
            ),
            profiles.savedProfile,
        )
        assertTrue(viewModel.uiState.value.saveCompleted)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    // 2. onboardingCompleted is true in the final saved profile.
    // 3. Save success produces saveCompleted.
    @Test
    fun successfulSave_marksOnboardingCompletedAndSaveCompleted() = runTest(dispatcher) {
        val profiles = RecordingProfileRepository()
        val viewModel = OnboardingViewModel(SignedInAuthRepository(), profiles)

        viewModel.start("Alex Doe")
        viewModel.setAge("25-34")
        viewModel.setSkinType(null) // "Skip for now" is a valid answer
        viewModel.setGender("Female")
        viewModel.setSensitivityAndSave(false)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(profiles.savedProfile?.onboardingCompleted == true)
        assertNull(profiles.savedProfile?.skinType)
        assertTrue(viewModel.uiState.value.saveCompleted)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // 4. Save failure does NOT produce saveCompleted (and does not mark completion).
    @Test
    fun failedSave_doesNotProduceSaveCompleted() = runTest(dispatcher) {
        val profiles = RecordingProfileRepository(
            saveResult = Result.failure(RuntimeException("network down")),
        )
        val viewModel = OnboardingViewModel(SignedInAuthRepository(), profiles)

        viewModel.start("Alex Doe")
        viewModel.setAge("25-34")
        viewModel.setGender("Female")
        viewModel.setSensitivityAndSave(true)
        dispatcher.scheduler.advanceUntilIdle()

        // The completion flag was attempted, but persistence failed...
        assertTrue(profiles.lastAttemptedProfile?.onboardingCompleted == true)
        // ...so nothing advances and the existing error behaviour is retained.
        assertFalse(viewModel.uiState.value.saveCompleted)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals("network down", viewModel.uiState.value.errorMessage)
    }

    // 5. Missing authenticated user does NOT save.
    @Test
    fun missingAuthenticatedUser_doesNotSave() = runTest(dispatcher) {
        val profiles = RecordingProfileRepository()
        val viewModel = OnboardingViewModel(SignedOutAuthRepository(), profiles)

        viewModel.start("Alex Doe")
        viewModel.setAge("25-34")
        viewModel.setGender("Female")
        viewModel.setSensitivityAndSave(true)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(profiles.lastAttemptedProfile)
        assertFalse(viewModel.uiState.value.saveCompleted)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    // 3 (identity). start() without a manual name falls back to provider metadata
    // (e.g. Google) and never fabricates a value.
    @Test
    fun start_withoutManualName_usesAuthenticatedDisplayName() = runTest(dispatcher) {
        val profiles = RecordingProfileRepository()
        val viewModel = OnboardingViewModel(
            SignedInAuthRepository(displayName = "Google Person"),
            profiles,
        )

        viewModel.start()
        viewModel.setAge("25-34")
        viewModel.setGender("Female")
        viewModel.setSensitivityAndSave(false)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Google Person", profiles.savedProfile?.fullName)
    }

    @Test
    fun start_withoutNameOrMetadata_persistsNullName() = runTest(dispatcher) {
        val profiles = RecordingProfileRepository()
        val viewModel = OnboardingViewModel(SignedInAuthRepository(displayName = null), profiles)

        viewModel.start()
        viewModel.setAge("25-34")
        viewModel.setGender("Female")
        viewModel.setSensitivityAndSave(false)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(profiles.savedProfile?.fullName)
    }

    // 6 (identity safety). Restarting onboarding wipes answers from a prior attempt
    // so one user's data can never leak into another's save.
    @Test
    fun start_clearsStaleAnswersFromPreviousAttempt() = runTest(dispatcher) {
        val viewModel = OnboardingViewModel(SignedInAuthRepository(), RecordingProfileRepository())

        viewModel.start("First User")
        viewModel.setAge("45 - 50")
        viewModel.setSkinType("Oily")
        viewModel.setGender("Male")

        viewModel.start("Second User")

        val state = viewModel.uiState.value
        assertEquals("Second User", state.fullName)
        assertNull(state.ageRange)
        assertNull(state.skinType)
        assertNull(state.gender)
        assertNull(state.isSkinSensitive)
        assertFalse(state.saveCompleted)
    }
}

private class SignedInAuthRepository(
    private val displayName: String? = null,
) : AuthRepository {
    override val authenticationState: Flow<AuthenticationState> =
        MutableStateFlow(AuthenticationState.AUTHENTICATED)

    override suspend fun signUp(email: String, password: String) = Result.success(Unit)
    override suspend fun signIn(email: String, password: String) = Result.success(Unit)
    override suspend fun signInWithGoogle() = Result.success(Unit)
    override suspend fun signOut() = Result.success(Unit)
    override fun currentUserId(): String = "signed-in-user"
    override fun hasActiveSession(): Boolean = true
    override fun currentUserDisplayName(): String? = displayName
}

private class SignedOutAuthRepository : AuthRepository {
    override val authenticationState: Flow<AuthenticationState> =
        MutableStateFlow(AuthenticationState.NOT_AUTHENTICATED)

    override suspend fun signUp(email: String, password: String) = Result.success(Unit)
    override suspend fun signIn(email: String, password: String) = Result.success(Unit)
    override suspend fun signInWithGoogle() = Result.success(Unit)
    override suspend fun signOut() = Result.success(Unit)
    override fun currentUserId(): String? = null
    override fun hasActiveSession(): Boolean = false
    override fun currentUserDisplayName(): String? = null
}

private class RecordingProfileRepository(
    private val saveResult: Result<Unit> = Result.success(Unit),
    private val stored: UserProfile? = null,
) : ProfileRepository {
    /** The last profile handed to saveProfile, regardless of outcome. */
    var lastAttemptedProfile: UserProfile? = null
        private set

    /** The last profile that was actually persisted (save succeeded). */
    var savedProfile: UserProfile? = null
        private set

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> {
        lastAttemptedProfile = profile
        if (saveResult.isSuccess) savedProfile = profile
        return saveResult
    }

    override suspend fun getProfile(userId: String): Result<UserProfile?> = Result.success(stored)

    override suspend fun hasCompletedOnboarding(userId: String): Result<Boolean> =
        Result.success(stored?.onboardingCompleted ?: false)
}
