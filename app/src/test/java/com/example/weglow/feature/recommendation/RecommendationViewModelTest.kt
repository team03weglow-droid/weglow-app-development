package com.example.weglow.feature.recommendation

import com.example.weglow.domain.model.AcneDetection
import com.example.weglow.domain.model.AcneScanResult
import com.example.weglow.domain.model.Product
import com.example.weglow.domain.model.RecommendationBasis
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val oilyProduct = Product(id = "1", name = "Oil Control Gel", priceLabel = "LKR 1500", imageUrl = null, targetSkinType = "Oily")

    @Test
    fun profileOnly_yieldsProfileOnlyBasis() = runTest(dispatcher) {
        val vm = RecommendationViewModel(
            FakeAuthRepository("u1"),
            FakeProfileRepository(UserProfile(id = "u1", skinType = "Oily")),
            FakeCatalogRepository(Result.success(listOf(oilyProduct))),
        )

        vm.load(scanConcerns = null)
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(RecommendationBasis.PROFILE_ONLY, state.result?.basis)
        assertEquals(1, state.result?.recommendations?.size)
    }

    @Test
    fun withScanConcerns_yieldsProfileAndScanBasis() = runTest(dispatcher) {
        val vm = RecommendationViewModel(
            FakeAuthRepository("u1"),
            FakeProfileRepository(UserProfile(id = "u1", skinType = "Oily")),
            FakeCatalogRepository(Result.success(listOf(oilyProduct))),
        )

        vm.load(scanConcerns = listOf("blackheads"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(RecommendationBasis.PROFILE_AND_SCAN, vm.uiState.value.result?.basis)
    }

    @Test
    fun noSignedInUser_reportsErrorWithoutFabricatingResults() = runTest(dispatcher) {
        val vm = RecommendationViewModel(
            FakeAuthRepository(null),
            FakeProfileRepository(null),
            FakeCatalogRepository(Result.success(listOf(oilyProduct))),
        )

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.uiState.value.errorMessage)
        assertEquals(null, vm.uiState.value.result)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun profileRepositoryFailure_surfacesRealErrorNotFakeProducts() = runTest(dispatcher) {
        val vm = RecommendationViewModel(
            FakeAuthRepository("u1"),
            FakeProfileRepository(result = Result.failure(RuntimeException("profile RLS denied"))),
            FakeCatalogRepository(Result.success(listOf(oilyProduct))),
        )

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("profile RLS denied", vm.uiState.value.errorMessage)
    }

    @Test
    fun catalogRepositoryFailure_surfacesRealError() = runTest(dispatcher) {
        val vm = RecommendationViewModel(
            FakeAuthRepository("u1"),
            FakeProfileRepository(UserProfile(id = "u1", skinType = "Oily")),
            FakeCatalogRepository(Result.failure(RuntimeException("no product rows"))),
        )

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("no product rows", vm.uiState.value.errorMessage)
    }

    @Test
    fun emptyCatalog_yieldsNoMatchesRatherThanError() = runTest(dispatcher) {
        val vm = RecommendationViewModel(
            FakeAuthRepository("u1"),
            FakeProfileRepository(UserProfile(id = "u1", skinType = "Oily")),
            FakeCatalogRepository(Result.success(emptyList())),
        )

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.result?.recommendations.orEmpty().isEmpty())
        assertEquals(null, vm.uiState.value.errorMessage)
    }

    // Reproduces exactly what WeGlowApp does at the navigation call site:
    // `scanState.result?.detections?.map { it.label }` from a real completed
    // ScanViewModel/AcneScanResult, never a hardcoded concern list.
    @Test
    fun realScanResultDetectionLabels_driveMatchingScoringAndBasis() = runTest(dispatcher) {
        val blackheadTreatment = Product(
            id = "1",
            name = "Blackhead Clearing Gel",
            priceLabel = "LKR 1800",
            imageUrl = null,
            targetSkinType = "Oily",
            targetConcerns = "Blackheads, Open Pores",
        )
        val unrelatedProduct = Product(
            id = "2",
            name = "Lip Balm",
            priceLabel = "LKR 500",
            imageUrl = null,
            targetSkinType = "Dry",
            targetConcerns = "Chapped Lips",
        )

        val realScanResult = AcneScanResult(
            detections = listOf(
                AcneDetection(label = "black heads", confidence = 0.82f, left = 0.1f, top = 0.1f, right = 0.2f, bottom = 0.2f),
                AcneDetection(label = "papules", confidence = 0.55f, left = 0.3f, top = 0.3f, right = 0.4f, bottom = 0.4f),
            ),
            imageWidth = 640,
            imageHeight = 640,
            modelVersion = "test-model",
            confidenceThreshold = 0.25f,
        )

        val vm = RecommendationViewModel(
            FakeAuthRepository("u1"),
            FakeProfileRepository(UserProfile(id = "u1", skinType = "Oily")),
            FakeCatalogRepository(Result.success(listOf(blackheadTreatment, unrelatedProduct))),
        )

        // Same extraction WeGlowApp performs when fromScan = true - no hardcoded concerns.
        val scanConcerns = realScanResult.detections.map { it.label }
        vm.load(scanConcerns)
        dispatcher.scheduler.advanceUntilIdle()

        val result = vm.uiState.value.result
        requireNotNull(result)
        assertEquals(RecommendationBasis.PROFILE_AND_SCAN, result.basis)

        // The product whose real Target_Concerns matches a real detected label is included,
        // with its scan-based reason attached, and outscores a skin-type-only match would.
        val match = result.recommendations.single { it.product.id == "1" }
        assertTrue(match.reasons.any { it.contains("Black heads", ignoreCase = true) })
        assertEquals(2 /* skin type */ + 3 /* one matched concern */, match.score)

        // The unrelated, zero-score product never appears in the results at all.
        assertTrue(result.recommendations.none { it.product.id == "2" })
    }

    @Test
    fun duplicateLoadCalls_whileInFlight_doNotDoubleLoad() = runTest(dispatcher) {
        val catalog = FakeCatalogRepository(Result.success(listOf(oilyProduct)))
        val vm = RecommendationViewModel(
            FakeAuthRepository("u1"),
            FakeProfileRepository(UserProfile(id = "u1", skinType = "Oily")),
            catalog,
        )

        vm.load()
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

private class FakeProfileRepository(
    private val profile: UserProfile? = null,
    private val result: Result<UserProfile?>? = null,
) : ProfileRepository {
    override suspend fun saveProfile(profile: UserProfile): Result<Unit> = Result.success(Unit)
    override suspend fun getProfile(userId: String): Result<UserProfile?> = result ?: Result.success(profile)
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
