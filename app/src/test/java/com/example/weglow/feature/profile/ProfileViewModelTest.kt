package com.example.weglow.feature.profile

import com.example.weglow.domain.model.ProfileImageUpload
import com.example.weglow.domain.model.UserProfile
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.AuthenticationState
import com.example.weglow.domain.repository.ProfileImageRepository
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    // Minimal 12-byte JPEG magic so ProfileImageValidator accepts the upload.
    private val jpegBytes = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
        0x00, 0x10, 'J'.code.toByte(), 'F'.code.toByte(),
        'I'.code.toByte(), 'F'.code.toByte(), 0x00, 0x01,
    )
    private val validUpload get() = ProfileImageUpload(jpegBytes, "image/jpeg")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // 1. Loads the persisted profile (real name from the profiles row).
    @Test
    fun refresh_loadsPersistedDisplayName() = runTest(dispatcher) {
        val vm = ProfileViewModel(
            FakeAuthRepository(userId = "u1"),
            FakeProfileRepository(stored = UserProfile(id = "u1", fullName = "Real Persisted Name")),
            FakeProfileImageRepository(),
        )

        vm.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Real Persisted Name", vm.uiState.value.displayName)
        assertFalse(vm.uiState.value.isLoading)
    }

    // 2. An existing profile-image reference is resolved and exposed for display.
    @Test
    fun refresh_resolvesPersistedProfileImage() = runTest(dispatcher) {
        val images = FakeProfileImageRepository(downloadBytes = byteArrayOf(1, 2, 3, 4))
        val vm = ProfileViewModel(
            FakeAuthRepository(userId = "u1"),
            FakeProfileRepository(
                stored = UserProfile(id = "u1", fullName = "N", profileImagePath = "u1/123.jpg"),
            ),
            images,
        )

        vm.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("u1/123.jpg", vm.uiState.value.profileImagePath)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), vm.uiState.value.profileImage)
        assertEquals(listOf("u1/123.jpg"), images.downloadedPaths)
    }

    // 3. The no-profile-image state works (nothing to resolve, no download attempted).
    @Test
    fun refresh_withNoImage_leavesImageNullAndSkipsDownload() = runTest(dispatcher) {
        val images = FakeProfileImageRepository()
        val vm = ProfileViewModel(
            FakeAuthRepository(userId = "u1"),
            FakeProfileRepository(stored = UserProfile(id = "u1", fullName = "N", profileImagePath = null)),
            images,
        )

        vm.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.uiState.value.profileImagePath)
        assertNull(vm.uiState.value.profileImage)
        assertTrue(images.downloadedPaths.isEmpty())
    }

    // 4. Upload requires an authenticated user.
    @Test
    fun onProfileImagePicked_withoutAuthenticatedUser_doesNotUpload() = runTest(dispatcher) {
        val images = FakeProfileImageRepository()
        val profiles = FakeProfileRepository()
        val vm = ProfileViewModel(FakeAuthRepository(userId = null), profiles, images)

        vm.onProfileImagePicked(validUpload)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, images.uploadCount)
        assertNull(profiles.updatedImagePath)
        assertNotNull(vm.uiState.value.imageError)
        assertFalse(vm.uiState.value.isUploadingImage)
    }

    // 5. The user identity used for the upload comes from the auth session, not the UI.
    @Test
    fun onProfileImagePicked_usesAuthenticatedUserIdForStoragePath() = runTest(dispatcher) {
        val images = FakeProfileImageRepository()
        val auth = FakeAuthRepository(userId = "session-user-42")
        val vm = ProfileViewModel(auth, FakeProfileRepository(), images)

        vm.onProfileImagePicked(validUpload)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("session-user-42", images.uploadedForUserId)
    }

    // 6. A successful upload updates the persisted profile-image reference.
    @Test
    fun onProfileImagePicked_success_persistsReferenceAndUpdatesState() = runTest(dispatcher) {
        val images = FakeProfileImageRepository(uploadPath = "session-user/999.jpg")
        val profiles = FakeProfileRepository()
        val vm = ProfileViewModel(FakeAuthRepository(userId = "session-user"), profiles, images)

        vm.onProfileImagePicked(validUpload)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("session-user/999.jpg", profiles.updatedImagePath)
        assertEquals("session-user", profiles.updatedImageForUserId)
        assertEquals("session-user/999.jpg", vm.uiState.value.profileImagePath)
        assertArrayEquals(jpegBytes, vm.uiState.value.profileImage)
        assertFalse(vm.uiState.value.isUploadingImage)
        assertNull(vm.uiState.value.imageError)
    }

    // 7. A failed Storage upload does not falsely report success.
    @Test
    fun onProfileImagePicked_storageFailure_reportsErrorAndDoesNotPersist() = runTest(dispatcher) {
        val images = FakeProfileImageRepository(
            uploadResult = Result.failure(RuntimeException("storage 500")),
        )
        val profiles = FakeProfileRepository()
        val vm = ProfileViewModel(FakeAuthRepository(userId = "u1"), profiles, images)

        vm.onProfileImagePicked(validUpload)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(profiles.updatedImagePath)
        assertNull(vm.uiState.value.profileImagePath)
        assertFalse(vm.uiState.value.isUploadingImage)
        assertEquals("storage 500", vm.uiState.value.imageError)
    }

    // 8. A failed database profile update does not falsely report full success,
    //    and the just-uploaded object is rolled back.
    @Test
    fun onProfileImagePicked_dbFailure_rollsBackUploadAndReportsError() = runTest(dispatcher) {
        val images = FakeProfileImageRepository(uploadPath = "u1/new.jpg")
        val profiles = FakeProfileRepository(
            updateImageResult = Result.failure(RuntimeException("db write denied")),
        )
        val vm = ProfileViewModel(FakeAuthRepository(userId = "u1"), profiles, images)

        vm.onProfileImagePicked(validUpload)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("db write denied", vm.uiState.value.imageError)
        assertNull(vm.uiState.value.profileImagePath)
        assertNull(vm.uiState.value.profileImage)
        assertEquals(listOf("u1/new.jpg"), images.deletedPaths) // rollback
        assertFalse(vm.uiState.value.isUploadingImage)
    }

    // 8b. Replacing an existing picture deletes the previous object once the new
    //     reference is safely persisted (no orphan accumulation).
    @Test
    fun onProfileImagePicked_replacingExistingImage_deletesPreviousObject() = runTest(dispatcher) {
        val images = FakeProfileImageRepository(
            uploadPath = "u1/new.jpg",
            downloadBytes = byteArrayOf(9),
        )
        val profiles = FakeProfileRepository(
            stored = UserProfile(id = "u1", fullName = "N", profileImagePath = "u1/old.jpg"),
        )
        val vm = ProfileViewModel(FakeAuthRepository(userId = "u1"), profiles, images)

        vm.refresh()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onProfileImagePicked(validUpload)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("u1/new.jpg", vm.uiState.value.profileImagePath)
        assertEquals(listOf("u1/old.jpg"), images.deletedPaths)
    }

    // 9. Existing full-name behaviour is preserved: persisted name wins, else the
    //    authenticated identity name, and "Team 03" is never reintroduced.
    @Test
    fun refresh_fallsBackToIdentityNameWhenNoPersistedName() = runTest(dispatcher) {
        val vm = ProfileViewModel(
            FakeAuthRepository(userId = "u1", displayName = "Identity Name"),
            FakeProfileRepository(stored = UserProfile(id = "u1", fullName = null)),
            FakeProfileImageRepository(),
        )

        vm.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Identity Name", vm.uiState.value.displayName)
    }

    // 10. Loading / error-state behaviour.
    @Test
    fun signedOutRefresh_resetsState() = runTest(dispatcher) {
        val vm = ProfileViewModel(
            FakeAuthRepository(userId = null),
            FakeProfileRepository(),
            FakeProfileImageRepository(),
        )

        vm.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProfileUiState(), vm.uiState.value)
    }

    @Test
    fun consumeImageError_clearsError() = runTest(dispatcher) {
        val vm = ProfileViewModel(
            FakeAuthRepository(userId = "u1"),
            FakeProfileRepository(),
            FakeProfileImageRepository(),
        )

        vm.onProfileImagePicked(ProfileImageUpload("not an image".toByteArray(), "image/png"))
        assertNotNull(vm.uiState.value.imageError)

        vm.consumeImageError()
        assertNull(vm.uiState.value.imageError)
    }

    @Test
    fun onProfileImagePicked_invalidImage_isRejectedBeforeAnyUpload() = runTest(dispatcher) {
        val images = FakeProfileImageRepository()
        val vm = ProfileViewModel(FakeAuthRepository(userId = "u1"), FakeProfileRepository(), images)

        vm.onProfileImagePicked(ProfileImageUpload(ByteArray(0), "image/jpeg"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, images.uploadCount)
        assertNotNull(vm.uiState.value.imageError)
    }

    @Test
    fun onProfileImagePicked_ignoresDuplicateTapsWhileUploading() = runTest(dispatcher) {
        val images = FakeProfileImageRepository(uploadPath = "u1/1.jpg")
        val vm = ProfileViewModel(FakeAuthRepository(userId = "u1"), FakeProfileRepository(), images)

        vm.onProfileImagePicked(validUpload)
        vm.onProfileImagePicked(validUpload) // should be ignored: upload in flight
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, images.uploadCount)
    }
}

// ---------------------------------------------------------------------------
// Fakes — repository boundary only; no Supabase, no network.
// ---------------------------------------------------------------------------

private class FakeAuthRepository(
    private val userId: String?,
    private val displayName: String? = null,
) : AuthRepository {
    override val authenticationState: Flow<AuthenticationState> =
        MutableStateFlow(
            if (userId == null) AuthenticationState.NOT_AUTHENTICATED
            else AuthenticationState.AUTHENTICATED,
        )

    override suspend fun signUp(email: String, password: String) = Result.success(Unit)
    override suspend fun signIn(email: String, password: String) = Result.success(Unit)
    override suspend fun signInWithGoogle() = Result.success(Unit)
    override suspend fun signOut() = Result.success(Unit)
    override fun currentUserId(): String? = userId
    override fun hasActiveSession(): Boolean = userId != null
    override fun currentUserDisplayName(): String? = displayName
}

private class FakeProfileRepository(
    private val stored: UserProfile? = null,
    private val updateImageResult: Result<Unit> = Result.success(Unit),
) : ProfileRepository {

    var updatedImagePath: String? = null
        private set
    var updatedImageForUserId: String? = null
        private set

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> = Result.success(Unit)

    override suspend fun getProfile(userId: String): Result<UserProfile?> = Result.success(stored)

    override suspend fun hasCompletedOnboarding(userId: String): Result<Boolean> =
        Result.success(stored?.onboardingCompleted ?: false)

    override suspend fun updateProfileImagePath(userId: String, path: String?): Result<Unit> {
        if (updateImageResult.isSuccess) {
            updatedImageForUserId = userId
            updatedImagePath = path
        }
        return updateImageResult
    }
}

private class FakeProfileImageRepository(
    private val uploadPath: String = "user/generated.jpg",
    private val uploadResult: Result<String>? = null,
    private val downloadBytes: ByteArray? = null,
) : ProfileImageRepository {

    var uploadCount: Int = 0
        private set
    var uploadedForUserId: String? = null
        private set
    val downloadedPaths = mutableListOf<String>()
    val deletedPaths = mutableListOf<String>()

    override suspend fun uploadProfileImage(
        userId: String,
        upload: ProfileImageUpload,
    ): Result<String> {
        uploadCount++
        uploadedForUserId = userId
        return uploadResult ?: Result.success(uploadPath)
    }

    override suspend fun downloadProfileImage(path: String): Result<ByteArray> {
        downloadedPaths += path
        return downloadBytes?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("no bytes for $path"))
    }

    override suspend fun deleteProfileImage(path: String): Result<Unit> {
        deletedPaths += path
        return Result.success(Unit)
    }
}
