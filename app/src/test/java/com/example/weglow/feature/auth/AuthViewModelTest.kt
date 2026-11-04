package com.example.weglow.feature.auth

import com.example.weglow.domain.model.UserProfile
import com.example.weglow.domain.repository.AccountSecurityRepository
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.AuthenticationState
import com.example.weglow.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun recoveryRejectsEmptyAndMalformedEmailsWithoutSending() = runTest(dispatcher) {
        val security = FakeRecoveryRepository()
        val vm = AuthViewModel(FakeAuthRepository(false), FakeProfileRepository(), security)
        advanceUntilIdle()
        listOf("", "person", "person@", "person@@example.com", ".person@example.com").forEach {
            vm.requestPasswordRecovery(it)
            assertTrue(vm.uiState.value.errorMessage != null)
        }
        assertEquals(0, security.requests)
    }

    @Test
    fun recoveryRequestsAreTrimmedAndDuplicatePressesAreIgnored() = runTest(dispatcher) {
        val security = FakeRecoveryRepository()
        val vm = AuthViewModel(FakeAuthRepository(false), FakeProfileRepository(), security)
        advanceUntilIdle()
        vm.requestPasswordRecovery(" person@example.com ")
        vm.requestPasswordRecovery("person@example.com")
        assertTrue(vm.uiState.value.isLoading)
        advanceUntilIdle()
        assertEquals(1, security.requests)
        assertEquals("person@example.com", security.email)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun requestFailureUsesSafeMessageAndAllowsRetry() = runTest(dispatcher) {
        val security = FakeRecoveryRepository().apply { failRequest = true }
        val vm = AuthViewModel(FakeAuthRepository(false), FakeProfileRepository(), security)
        advanceUntilIdle()
        vm.requestPasswordRecovery("person@example.com")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.errorMessage!!.contains("secret"))
        security.failRequest = false
        vm.requestPasswordRecovery("person@example.com")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.notice!!.startsWith("If an account exists"))
    }

    @Test
    fun existingLoginSessionCannotAuthorizePasswordReset() = runTest(dispatcher) {
        val security = FakeRecoveryRepository()
        val vm = AuthViewModel(FakeAuthRepository(true), FakeProfileRepository(), security)
        advanceUntilIdle()
        vm.resetPassword("password123", "password123")
        advanceUntilIdle()
        assertEquals(0, security.updates)
    }

    @Test
    fun invalidAndExpiredRecoveryLinksNeverEnablePasswordUpdate() = runTest(dispatcher) {
        val security = FakeRecoveryRepository().apply { failVerification = true }
        val vm = AuthViewModel(FakeAuthRepository(false), FakeProfileRepository(), security)
        advanceUntilIdle()
        vm.openPasswordRecovery("weglow://password-reset?code=oauth-code")
        advanceUntilIdle()
        assertEquals(0, security.verifications)
        vm.openPasswordRecovery(VALID_RECOVERY_LINK)
        advanceUntilIdle()
        assertEquals(1, security.verifications)
        assertFalse(vm.uiState.value.recoveryReady)
        assertTrue(vm.uiState.value.errorMessage!!.contains("expired"))
        vm.resetPassword("password123", "password123")
        advanceUntilIdle()
        assertEquals(0, security.updates)
    }

    @Test
    fun resetValidatesPolicyAndConfirmationAndPreventsDuplicateUpdates() = runTest(dispatcher) {
        val security = FakeRecoveryRepository()
        val vm = AuthViewModel(FakeAuthRepository(false), FakeProfileRepository(), security)
        advanceUntilIdle()
        vm.openPasswordRecovery(VALID_RECOVERY_LINK)
        advanceUntilIdle()
        listOf("", "short1", "noNumbers").forEach {
            vm.resetPassword(it, it)
            assertTrue(vm.uiState.value.errorMessage != null)
        }
        vm.resetPassword("password123", "different123")
        assertEquals("Passwords do not match.", vm.uiState.value.errorMessage)
        assertEquals(0, security.updates)
        vm.resetPassword("password123", "password123")
        vm.resetPassword("password123", "password123")
        advanceUntilIdle()
        assertEquals(1, security.updates)
        assertFalse(vm.uiState.value.recoveryReady)
        assertTrue(vm.uiState.value.notice!!.startsWith("Password updated"))
        vm.closePasswordRecovery()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.event is AuthEvent.SignedOut)
    }

    @Test
    fun failedPasswordUpdateDisablesConsumedRecoverySession() = runTest(dispatcher) {
        val security = FakeRecoveryRepository().apply { failUpdate = true }
        val vm = AuthViewModel(FakeAuthRepository(false), FakeProfileRepository(), security)
        advanceUntilIdle()
        vm.openPasswordRecovery(VALID_RECOVERY_LINK)
        advanceUntilIdle()
        vm.resetPassword("password123", "password123")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.recoveryReady)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.errorMessage!!.contains("Request a new link"))
        vm.resetPassword("password123", "password123")
        advanceUntilIdle()
        assertEquals(1, security.updates)
    }

    @Test
    fun externalSessionLossEmitsSignedOut() = runTest(dispatcher) {
        val auth = FakeAuthRepository(active = true)
        val vm = AuthViewModel(auth, FakeProfileRepository(completed = true))
        advanceUntilIdle()
        // Session invalidation outside the explicit ViewModel sign-out action.
        auth.signOut()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.event is AuthEvent.SignedOut)
        assertEquals(StartupDestination.LOGIN, vm.startupDestination.value)
    }

    @Test
    fun recoveryUsesGenericNoticeAndDoesNotNavigateIntoAccount() = runTest(dispatcher) {
        val auth = FakeAuthRepository(active = false)
        val security = object : AccountSecurityRepository {
            override suspend fun requestPasswordRecovery(email: String) = Result.success(Unit)
            override suspend fun verifyPasswordRecovery(tokenHash: String): Result<Unit> {
                auth.signIn("person@example.com", "unused")
                kotlinx.coroutines.yield()
                return Result.success(Unit)
            }
            override suspend fun cancelPasswordRecovery() = Result.success(Unit)
            override suspend fun resetPassword(password: String): Result<Unit> {
                kotlinx.coroutines.yield()
                auth.signOut()
                return Result.success(Unit)
            }
            override suspend fun deleteAccount(): Result<String> = Result.failure(IllegalStateException())
        }
        val vm = AuthViewModel(auth, FakeProfileRepository(), security)
        advanceUntilIdle()
        vm.requestPasswordRecovery("person@example.com")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.notice!!.startsWith("If an account exists"))
        vm.openPasswordRecovery("weglow://password-reset?token_hash=${"a".repeat(64)}&type=recovery")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.recoveryReady)
        assertNull(vm.uiState.value.event)
        vm.resetPassword("password123", "password123")
        advanceUntilIdle()
        assertNull(vm.uiState.value.event)
        assertFalse(auth.hasActiveSession())
        assertEquals("Password updated. Sign in with your new password.", vm.uiState.value.notice)
    }

    @Test
    fun deletionSuccessCarriesIdentityForLocalJournalCleanup() = runTest(dispatcher) {
        val auth = FakeAuthRepository(active = true)
        val security = object : AccountSecurityRepository {
            override suspend fun requestPasswordRecovery(email: String) = Result.success(Unit)
            override suspend fun verifyPasswordRecovery(tokenHash: String) = Result.success(Unit)
            override suspend fun cancelPasswordRecovery() = Result.success(Unit)
            override suspend fun resetPassword(password: String) = Result.success(Unit)
            override suspend fun deleteAccount(): Result<String> {
                auth.signOut()
                return Result.success("user-1")
            }
        }
        val vm = AuthViewModel(auth, FakeProfileRepository(), security)
        advanceUntilIdle()
        vm.deleteAccount()
        advanceUntilIdle()
        assertEquals(AuthEvent.AccountDeleted("user-1"), vm.uiState.value.event)
    }

    @Test
    fun startupWithoutSession_routesToLogin() = runTest(dispatcher) {
        val viewModel = AuthViewModel(
            FakeAuthRepository(active = false),
            FakeProfileRepository(completed = false),
        )

        advanceUntilIdle()

        assertEquals(
            StartupDestination.LOGIN,
            viewModel.startupDestination.value,
        )
    }

    @Test
    fun restoredSessionWithCompletedProfile_routesToHome() =
        runTest(dispatcher) {

            val viewModel = AuthViewModel(
                FakeAuthRepository(active = true),
                FakeProfileRepository(completed = true),
            )

            advanceUntilIdle()

            assertEquals(
                StartupDestination.HOME,
                viewModel.startupDestination.value,
            )

            assertNull(viewModel.uiState.value.event)
        }

    @Test
    fun restoredSessionWithoutCompletedProfile_routesToOnboarding() =
        runTest(dispatcher) {

            val viewModel = AuthViewModel(
                FakeAuthRepository(active = true),
                FakeProfileRepository(completed = false),
            )

            advanceUntilIdle()

            assertEquals(
                StartupDestination.ONBOARDING,
                viewModel.startupDestination.value,
            )
        }

    @Test
    fun restoredSession_withProfileRowButIncompleteOnboarding_routesToOnboarding() =
        runTest(dispatcher) {

            // A profiles row exists, but onboarding_completed is false. Routing must
            // key off the flag, not the row's existence.
            val viewModel = AuthViewModel(
                FakeAuthRepository(active = true),
                FakeProfileRepository(
                    storedProfile = UserProfile(
                        id = "user-1",
                        ageRange = "14 - 25",
                        onboardingCompleted = false,
                    ),
                ),
            )

            advanceUntilIdle()

            assertEquals(
                StartupDestination.ONBOARDING,
                viewModel.startupDestination.value,
            )
        }

    @Test
    fun signIn_routesCompletedProfileToHome() =
        runTest(dispatcher) {

            val auth = FakeAuthRepository(active = false)
            val profile = FakeProfileRepository(completed = true)
            val viewModel = AuthViewModel(auth, profile)

            advanceUntilIdle()

            viewModel.signIn(
                "person@example.com",
                "password123",
            )

            advanceUntilIdle()

            val event = viewModel.uiState.value.event

            assertTrue(event is AuthEvent.SignedIn)

            assertEquals(
                StartupDestination.HOME,
                (event as AuthEvent.SignedIn).destination,
            )
        }

    @Test
    fun signIn_routesIncompleteProfileToOnboarding() =
        runTest(dispatcher) {

            val auth = FakeAuthRepository(active = false)
            val profile = FakeProfileRepository(completed = false)
            val viewModel = AuthViewModel(auth, profile)

            advanceUntilIdle()

            viewModel.signIn(
                "person@example.com",
                "password123",
            )

            advanceUntilIdle()

            val event = viewModel.uiState.value.event

            assertTrue(event is AuthEvent.SignedIn)

            assertEquals(
                StartupDestination.ONBOARDING,
                (event as AuthEvent.SignedIn).destination,
            )
        }

    @Test
    fun googleAuthentication_routesCompletedProfileToHome() =
        runTest(dispatcher) {

            val auth = FakeAuthRepository(
                active = false,
                googleCompletesImmediately = false,
            )

            val viewModel = AuthViewModel(
                auth,
                FakeProfileRepository(completed = true),
            )

            advanceUntilIdle()

            viewModel.signInWithGoogle()
            advanceUntilIdle()

            // Opening OAuth alone must not authenticate the user.
            assertNull(viewModel.uiState.value.event)
            assertFalse(auth.hasActiveSession())

            // Simulate Google returning through the deep link and
            // Supabase emitting AUTHENTICATED.
            auth.completeGoogleAuthentication()
            advanceUntilIdle()

            val event = viewModel.uiState.value.event

            assertTrue(event is AuthEvent.SignedIn)

            assertEquals(
                StartupDestination.HOME,
                (event as AuthEvent.SignedIn).destination,
            )
        }

    @Test
    fun signUp_preservesSignedUpEventAndFullName() =
        runTest(dispatcher) {

            val auth = FakeAuthRepository(active = false)

            val viewModel = AuthViewModel(
                auth,
                FakeProfileRepository(completed = false),
            )

            advanceUntilIdle()

            viewModel.signUp(
                fullName = "Asher",
                email = "asher@example.com",
                password = "password123",
            )

            advanceUntilIdle()

            val event = viewModel.uiState.value.event

            // Critical Phase 4 race regression test:
            // AUTHENTICATED must not replace SignedUp.
            assertTrue(event is AuthEvent.SignedUp)

            assertEquals(
                "Asher",
                (event as AuthEvent.SignedUp).fullName,
            )
        }

    @Test
    fun signOut_emitsSignedOutAndClearsSession() =
        runTest(dispatcher) {

            val auth = FakeAuthRepository(active = true)

            val viewModel = AuthViewModel(
                auth,
                FakeProfileRepository(completed = true),
            )

            advanceUntilIdle()

            viewModel.signOut()
            advanceUntilIdle()

            assertFalse(auth.hasActiveSession())
            assertTrue(
                viewModel.uiState.value.event is AuthEvent.SignedOut
            )
        }
}

private class FakeAuthRepository(
    active: Boolean,
    private val googleCompletesImmediately: Boolean = false,
) : AuthRepository {

    private var activeSession = active

    private val _authenticationState =
        MutableStateFlow(
            if (active) {
                AuthenticationState.AUTHENTICATED
            } else {
                AuthenticationState.NOT_AUTHENTICATED
            }
        )

    override val authenticationState: Flow<AuthenticationState> =
        _authenticationState

    override suspend fun signUp(
        email: String,
        password: String,
    ): Result<Unit> {

        activeSession = true

        _authenticationState.value =
            AuthenticationState.AUTHENTICATED

        return Result.success(Unit)
    }

    override suspend fun signIn(
        email: String,
        password: String,
    ): Result<Unit> {

        activeSession = true

        _authenticationState.value =
            AuthenticationState.AUTHENTICATED

        return Result.success(Unit)
    }

    override suspend fun signInWithGoogle(): Result<Unit> {

        if (googleCompletesImmediately) {
            completeGoogleAuthentication()
        }

        return Result.success(Unit)
    }

    fun completeGoogleAuthentication() {
        activeSession = true

        _authenticationState.value =
            AuthenticationState.AUTHENTICATED
    }

    override suspend fun signOut(): Result<Unit> {

        activeSession = false

        _authenticationState.value =
            AuthenticationState.NOT_AUTHENTICATED

        return Result.success(Unit)
    }

    override fun currentUserId(): String? =
        if (activeSession) {
            "user-1"
        } else {
            null
        }

    override fun hasActiveSession(): Boolean =
        activeSession

    override fun currentUserDisplayName(): String? = null

    override fun currentUserEmail(): String? = null
}

private class FakeProfileRepository(
    private val completed: Boolean = false,
    private val storedProfile: UserProfile? = null,
) : ProfileRepository {

    override suspend fun saveProfile(
        profile: UserProfile,
    ): Result<Unit> =
        Result.success(Unit)

    override suspend fun getProfile(
        userId: String,
    ): Result<UserProfile?> =
        Result.success(storedProfile)

    override suspend fun hasCompletedOnboarding(
        userId: String,
    ): Result<Boolean> =
        Result.success(storedProfile?.onboardingCompleted ?: completed)

    override suspend fun updateProfileImagePath(
        userId: String,
        path: String?,
    ): Result<Unit> =
        Result.success(Unit)

    override suspend fun updateFaceShape(userId: String, faceShape: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun updateScanSummary(userId: String, concerns: String, scannedAt: java.time.Instant): Result<Unit> =
        Result.success(Unit)

    override suspend fun updateEnvironment(userId: String, uvIndex: Double, uvCategory: String, humidity: Int, locationName: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun updateGender(
        userId: String,
        gender: String,
    ): Result<Unit> =
        Result.success(Unit)}

private val VALID_RECOVERY_LINK = "weglow://password-reset?token_hash=${"a".repeat(64)}&type=recovery"

private class FakeRecoveryRepository : AccountSecurityRepository {
    var requests = 0
    var verifications = 0
    var updates = 0
    var email: String? = null
    var failRequest = false
    var failVerification = false
    var failUpdate = false
    override suspend fun requestPasswordRecovery(email: String): Result<Unit> {
        requests++
        this.email = email
        kotlinx.coroutines.yield()
        return if (failRequest) Result.failure(IllegalStateException("secret")) else Result.success(Unit)
    }
    override suspend fun verifyPasswordRecovery(tokenHash: String): Result<Unit> {
        verifications++
        return if (failVerification) Result.failure(IllegalStateException("expired")) else Result.success(Unit)
    }
    override suspend fun resetPassword(password: String): Result<Unit> {
        updates++
        kotlinx.coroutines.yield()
        return if (failUpdate) Result.failure(IllegalStateException("secret")) else Result.success(Unit)
    }
    override suspend fun cancelPasswordRecovery() = Result.success(Unit)
    override suspend fun deleteAccount(): Result<String> = Result.failure(IllegalStateException())
}
