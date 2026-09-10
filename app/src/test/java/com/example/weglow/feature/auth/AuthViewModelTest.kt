package com.example.weglow.feature.auth

import com.example.weglow.domain.model.UserProfile
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
}

private class FakeProfileRepository(
    private val completed: Boolean,
) : ProfileRepository {

    override suspend fun saveProfile(
        profile: UserProfile,
    ): Result<Unit> =
        Result.success(Unit)

    override suspend fun hasCompletedOnboarding(
        userId: String,
    ): Result<Boolean> =
        Result.success(completed)
}