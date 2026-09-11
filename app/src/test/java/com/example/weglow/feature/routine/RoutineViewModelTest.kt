package com.example.weglow.feature.routine

import com.example.weglow.domain.model.Product
import com.example.weglow.domain.model.UserProfile
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.AuthenticationState
import com.example.weglow.domain.repository.CatalogRepository
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val cleanser = Product(id = "1", name = "Gentle Face Wash", priceLabel = "LKR 900", imageUrl = null, category = "Cleanser", targetSkinType = "Oily")
    private val moisturizer = Product(id = "2", name = "Hydra Moisturizer", priceLabel = "LKR 1200", imageUrl = null, category = "Moisturizer", targetSkinType = "Oily")

    @Test
    fun successfulLoad_buildsPlanFromRealCatalog() = runTest(dispatcher) {
        val vm = RoutineViewModel(
            FakeAuthRepository("u1"),
            FakeProfileRepository(UserProfile(id = "u1", skinType = "Oily")),
            FakeCatalogRepository(Result.success(listOf(cleanser, moisturizer))),
        )

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        val plan = vm.uiState.value.plan
        assertNotNull(plan)
        assertEquals("1", plan?.morning?.first { it.label == "Cleanser" }?.product?.id)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun noSignedInUser_reportsErrorWithoutFakePlan() = runTest(dispatcher) {
        val vm = RoutineViewModel(
            FakeAuthRepository(null),
            FakeProfileRepository(null),
            FakeCatalogRepository(Result.success(listOf(cleanser))),
        )

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.uiState.value.errorMessage)
        assertNull(vm.uiState.value.plan)
    }

    @Test
    fun catalogFailure_surfacesRealErrorNotFakeSteps() = runTest(dispatcher) {
        val vm = RoutineViewModel(
            FakeAuthRepository("u1"),
            FakeProfileRepository(UserProfile(id = "u1", skinType = "Oily")),
            FakeCatalogRepository(Result.failure(RuntimeException("catalog unavailable"))),
        )

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("catalog unavailable", vm.uiState.value.errorMessage)
        assertNull(vm.uiState.value.plan)
    }

    @Test
    fun categoryWithNoProduct_isMissingStepNotInvented() = runTest(dispatcher) {
        val vm = RoutineViewModel(
            FakeAuthRepository("u1"),
            FakeProfileRepository(UserProfile(id = "u1", skinType = "Oily")),
            FakeCatalogRepository(Result.success(listOf(cleanser))), // no sunscreen in catalog
        )

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        val sunscreenStep = vm.uiState.value.plan?.morning?.first { it.label == "Sunscreen" }
        assertNull(sunscreenStep?.product)
    }

    // Routines and Discover share the same catalog fetch, so revisiting the Routines tab
    // should not re-query Supabase once a plan has already been built successfully.
    @Test
    fun load_whenPlanAlreadyBuilt_doesNotRefetchCatalog() = runTest(dispatcher) {
        val catalog = FakeCatalogRepository(Result.success(listOf(cleanser, moisturizer)))
        val vm = RoutineViewModel(
            FakeAuthRepository("u1"),
            FakeProfileRepository(UserProfile(id = "u1", skinType = "Oily")),
            catalog,
        )

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, catalog.callCount)
    }
}

private class FakeAuthRepository(private val userId: String?) : AuthRepository {
    override val authenticationState: Flow<AuthenticationState> =
        MutableStateFlow(if (userId == null) AuthenticationState.NOT_AUTHENTICATED else AuthenticationState.AUTHENTICATED)

    override suspend fun signUp(email: String, password: String) = Result.success(Unit)
    override suspend fun signIn(email: String, password: String) = Result.success(Unit)
    override suspend fun signInWithGoogle() = Result.success(Unit)
    override suspend fun signOut() = Result.success(Unit)
    override fun currentUserId(): String? = userId
    override fun hasActiveSession(): Boolean = userId != null
    override fun currentUserDisplayName(): String? = null
}

private class FakeProfileRepository(private val profile: UserProfile?) : ProfileRepository {
    override suspend fun saveProfile(profile: UserProfile): Result<Unit> = Result.success(Unit)
    override suspend fun getProfile(userId: String): Result<UserProfile?> = Result.success(profile)
    override suspend fun hasCompletedOnboarding(userId: String): Result<Boolean> = Result.success(true)
    override suspend fun updateProfileImagePath(userId: String, path: String?): Result<Unit> = Result.success(Unit)
}

private class FakeCatalogRepository(private val result: Result<List<Product>>) : CatalogRepository {
    var callCount: Int = 0
        private set

    override suspend fun products(): Result<List<Product>> {
        callCount++
        return result
    }
}
