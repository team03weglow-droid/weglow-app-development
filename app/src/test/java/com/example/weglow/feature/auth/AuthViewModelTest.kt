package com.example.weglow.feature.auth

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun signIn_routesCompletedProfileToHome() = runTest(dispatcher) {
        val auth = FakeAuthRepository(active = false)
        val profile = FakeProfileRepository(completed = true)
        val viewModel = AuthViewModel(auth, profile)

        viewModel.signIn("person@example.com", "password123")
        dispatcher.scheduler.advanceUntilIdle()

        val event = viewModel.uiState.value.event
        assertTrue(event is AuthEvent.SignedIn)
        assertEquals(StartupDestination.HOME, (event as AuthEvent.SignedIn).destination)
    }

    @Test
    fun startupWithoutSession_routesToLogin() = runTest(dispatcher) {
        val viewModel = AuthViewModel(FakeAuthRepository(active = false), FakeProfileRepository(false))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(StartupDestination.LOGIN, viewModel.startupDestination.value)
    }
}

private class FakeAuthRepository(private var active: Boolean) : AuthRepository {
    override suspend fun signUp(email: String, password: String) = Result.success(Unit).also { active = true }
    override suspend fun signIn(email: String, password: String) = Result.success(Unit).also { active = true }
    override suspend fun signOut() = Result.success(Unit).also { active = false }
    override fun currentUserId(): String? = if (active) "user-1" else null
    override fun hasActiveSession(): Boolean = active
}

private class FakeProfileRepository(private val completed: Boolean) : ProfileRepository {
    override suspend fun saveProfile(profile: UserProfile): Result<Unit> = Result.success(Unit)
    override suspend fun hasCompletedOnboarding(userId: String): Result<Boolean> = Result.success(completed)
}
