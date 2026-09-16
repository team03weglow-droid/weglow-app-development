package com.example.weglow.feature.onboarding

import com.example.weglow.domain.model.AgeRange
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
        viewModel.setAge("25–35")
        viewModel.setSkinType("Combination")
        viewModel.setGender("Non-binary")
        viewModel.setSensitivityAndSave(true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            UserProfile(
                id = "signed-in-user",
                fullName = "Alex Doe",
                ageRange = "25–35",
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
        viewModel.setAge("25–35")
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
        viewModel.setAge("25–35")
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
        viewModel.setAge("25–35")
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
        viewModel.setAge("25–35")
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
        viewModel.setAge("25–35")
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
        viewModel.setAge("36–45")
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

    // --- Minimum age of 14: setAge() is the single point every age value passes through on
    // its way into the saved profile, so these confirm it never accepts "Under 14" (or any
    // other value outside the real AgeRange.OPTIONS), regardless of what a caller passes it.

    // 7. "Under 14" is never accepted into onboarding state.
    @Test
    fun setAge_underFourteen_isRejected() = runTest(dispatcher) {
        val viewModel = OnboardingViewModel(SignedInAuthRepository(), RecordingProfileRepository())

        viewModel.setAge("Under 14")

        assertNull(viewModel.uiState.value.ageRange)
    }

    // 8. Any value outside the real, supported age ranges is rejected the same way -
    // this is a defense-in-depth guard, not a hardcoded special case for "Under 14".
    @Test
    fun setAge_unrecognizedValue_isRejected() = runTest(dispatcher) {
        val viewModel = OnboardingViewModel(SignedInAuthRepository(), RecordingProfileRepository())

        viewModel.setAge("not a real age range")

        assertNull(viewModel.uiState.value.ageRange)
    }

    // 9. Every currently supported age range is still accepted.
    @Test
    fun setAge_everySupportedAgeRange_isAccepted() = runTest(dispatcher) {
        AgeRange.OPTIONS.forEach { option ->
            val viewModel = OnboardingViewModel(SignedInAuthRepository(), RecordingProfileRepository())

            viewModel.setAge(option)

            assertEquals(option, viewModel.uiState.value.ageRange)
        }
    }

    // 10. End-to-end: even if "Under 14" were somehow passed to setAge(), it can never reach
    // the saved profile - onboarding completes with ageRange left null, not "Under 14".
    @Test
    fun finalAnswer_afterRejectedAge_neverPersistsIt() = runTest(dispatcher) {
        val profiles = RecordingProfileRepository()
        val viewModel = OnboardingViewModel(SignedInAuthRepository(), profiles)

        viewModel.start("Alex Doe")
        viewModel.setAge("Under 14")
        viewModel.setSkinType("Combination")
        viewModel.setGender("Female")
        viewModel.setSensitivityAndSave(true)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(profiles.savedProfile?.ageRange)
        assertTrue(profiles.savedProfile?.onboardingCompleted == true)
    }

    // 11. A rejected setAge() call does not disturb an already-valid answer.
    @Test
    fun setAge_rejectedValueAfterValidOne_leavesValidAnswerInPlace() = runTest(dispatcher) {
        val viewModel = OnboardingViewModel(SignedInAuthRepository(), RecordingProfileRepository())

        viewModel.setAge("25–35")
        viewModel.setAge("Under 14")

        assertEquals("25–35", viewModel.uiState.value.ageRange)
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
    override fun currentUserEmail(): String? = null
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
    override fun currentUserEmail(): String? = null
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

    override suspend fun updateProfileImagePath(userId: String, path: String?): Result<Unit> =
        Result.success(Unit)

    override suspend fun updateFaceShape(userId: String, faceShape: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun updateScanSummary(
        userId: String,
        concerns: String,
        scannedAt: java.time.Instant,
    ): Result<Unit> =
        Result.success(Unit)

    override suspend fun updateEnvironment(
        userId: String,
        uvIndex: Double,
        uvCategory: String,
        humidity: Int,
        locationName: String,
    ): Result<Unit> =
        Result.success(Unit)

    override suspend fun updateGender(
        userId: String,
        gender: String,
    ): Result<Unit> =
        Result.success(Unit)
        Result.success(Unit)
}
