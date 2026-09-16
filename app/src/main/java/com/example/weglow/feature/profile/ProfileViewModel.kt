package com.example.weglow.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.domain.model.ProfileImageUpload
import com.example.weglow.domain.model.ProfileImageValidation
import com.example.weglow.domain.model.ProfileImageValidator
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.ProfileImageRepository
import com.example.weglow.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val displayName: String? = null,
    /** The authenticated account's own email, for read-only display (e.g. Account Settings). */
    val email: String? = null,
    val isLoading: Boolean = false,
    /** Stable Supabase Storage object path persisted in the profile, or null. */
    val profileImagePath: String? = null,
    /** Raw bytes of the persisted profile picture, resolved for display. */
    val profileImage: ByteArray? = null,
    val isUploadingImage: Boolean = false,
    val imageError: String? = null,
    /**
     * The current persisted `profiles.gender` value (one of
     * [com.example.weglow.domain.model.Gender.OPTIONS]), or null when never set. This is the
     * same column onboarding writes to - it is the single value every other screen (hairstyle
     * recommendations included) must read, so it is refreshed from the repository like every
     * other profile field rather than cached anywhere else.
     */
    val gender: String? = null,
    val isUpdatingGender: Boolean = false,
    val genderError: String? = null,
) {
    // ByteArray needs structural equals/hashCode for predictable state comparisons.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProfileUiState) return false
        return displayName == other.displayName &&
            email == other.email &&
            isLoading == other.isLoading &&
            profileImagePath == other.profileImagePath &&
            isUploadingImage == other.isUploadingImage &&
            imageError == other.imageError &&
            gender == other.gender &&
            isUpdatingGender == other.isUpdatingGender &&
            genderError == other.genderError &&
            profileImageContentEquals(other.profileImage)
    }

    override fun hashCode(): Int {
        var result = displayName?.hashCode() ?: 0
        result = 31 * result + (email?.hashCode() ?: 0)
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + (profileImagePath?.hashCode() ?: 0)
        result = 31 * result + (profileImage?.contentHashCode() ?: 0)
        result = 31 * result + isUploadingImage.hashCode()
        result = 31 * result + (imageError?.hashCode() ?: 0)
        result = 31 * result + (gender?.hashCode() ?: 0)
        result = 31 * result + isUpdatingGender.hashCode()
        result = 31 * result + (genderError?.hashCode() ?: 0)
        return result
    }

    private fun profileImageContentEquals(other: ByteArray?): Boolean =
        when {
            profileImage == null -> other == null
            other == null -> false
            else -> profileImage.contentEquals(other)
        }
}

/**
 * Loads the signed-in user's persisted profile (real name + profile picture) and
 * coordinates profile-picture changes.
 *
 * All persistence goes through the domain repositories; this class never touches
 * Supabase, Android `Uri`s, or a user id supplied by the UI. The authenticated
 * identity is always resolved through [AuthRepository].
 */
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val profileImageRepository: ProfileImageRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun refresh() {
        val userId = authRepository.currentUserId()
        if (userId == null) {
            _uiState.value = ProfileUiState()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val profile = profileRepository.getProfile(userId).getOrNull()

            val persistedName = profile?.fullName?.trim()?.takeIf { it.isNotBlank() }
            val identityName = authRepository.currentUserDisplayName()?.trim()?.takeIf { it.isNotBlank() }

            val imagePath = profile?.profileImagePath?.trim()?.takeIf { it.isNotBlank() }
            val imageBytes = imagePath?.let { path ->
                profileImageRepository.downloadProfileImage(path).getOrNull()
            }

            _uiState.value = _uiState.value.copy(
                displayName = persistedName ?: identityName,
                email = authRepository.currentUserEmail(),
                profileImagePath = imagePath,
                profileImage = imageBytes,
                gender = profile?.gender?.trim()?.takeIf { it.isNotBlank() },
                isLoading = false,
            )
        }
    }

    /**
     * Persists a Profile-initiated gender change to the same `profiles.gender` column
     * onboarding writes to, then updates local state only once that write is confirmed - a
     * failed update must never leave the UI showing a gender that was not actually saved.
     * Reusing [refresh]'s single-column-update pattern ([ProfileRepository.updateGender])
     * means no other screen needs to change: anything that re-reads the profile (in
     * particular hairstyle recommendations) sees the new value on its very next read.
     */
    fun onGenderSelected(gender: String) {
        if (_uiState.value.isUpdatingGender || gender == _uiState.value.gender) return

        val userId = authRepository.currentUserId()
        if (userId == null) {
            _uiState.value = _uiState.value.copy(
                genderError = "Your session is no longer active. Sign in again to update your gender.",
            )
            return
        }

        _uiState.value = _uiState.value.copy(isUpdatingGender = true, genderError = null)

        viewModelScope.launch {
            profileRepository.updateGender(userId, gender).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isUpdatingGender = false,
                        gender = gender,
                        genderError = null,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isUpdatingGender = false,
                        genderError = error.message ?: "We couldn't save your gender. Please try again.",
                    )
                },
            )
        }
    }

    fun consumeGenderError() {
        _uiState.value = _uiState.value.copy(genderError = null)
    }

    /**
     * Validates then uploads a newly picked image, persists the storage
     * reference, and only then updates the visible state. Duplicate taps are
     * ignored while an upload is in flight.
     */
    fun onProfileImagePicked(upload: ProfileImageUpload) {
        if (_uiState.value.isUploadingImage) return

        val validation = ProfileImageValidator.validate(upload)
        if (validation != ProfileImageValidation.Valid) {
            _uiState.value = _uiState.value.copy(imageError = messageFor(validation))
            return
        }

        val userId = authRepository.currentUserId()
        if (userId == null) {
            _uiState.value = _uiState.value.copy(
                imageError = "Your session is no longer active. Sign in again to update your photo.",
            )
            return
        }

        val previousPath = _uiState.value.profileImagePath
        _uiState.value = _uiState.value.copy(isUploadingImage = true, imageError = null)

        viewModelScope.launch {
            val uploadedPath = profileImageRepository.uploadProfileImage(userId, upload).getOrElse { error ->
                // Storage upload failed: nothing was persisted, report it honestly.
                _uiState.value = _uiState.value.copy(
                    isUploadingImage = false,
                    imageError = error.message ?: "Upload failed. Please try again.",
                )
                return@launch
            }

            val persisted = profileRepository.updateProfileImagePath(userId, uploadedPath)
            if (persisted.isFailure) {
                // Storage succeeded but the profile row did not. Roll the new
                // object back so it cannot linger unreferenced, and surface the
                // real failure instead of a false success.
                profileImageRepository.deleteProfileImage(uploadedPath)
                _uiState.value = _uiState.value.copy(
                    isUploadingImage = false,
                    imageError = persisted.exceptionOrNull()?.message
                        ?: "We couldn't save your new photo. Please try again.",
                )
                return@launch
            }

            // Persistence confirmed: safe to show the new image and to remove the
            // previous object so old pictures don't accumulate.
            if (previousPath != null && previousPath != uploadedPath) {
                profileImageRepository.deleteProfileImage(previousPath)
            }

            _uiState.value = _uiState.value.copy(
                isUploadingImage = false,
                profileImagePath = uploadedPath,
                profileImage = upload.bytes,
                imageError = null,
            )
        }
    }

    /** Called by the Android layer when a picked file could not be read/decoded. */
    fun onProfileImageUnreadable() {
        _uiState.value = _uiState.value.copy(
            imageError = "We couldn't read that file. Pick a different photo.",
        )
    }

    fun consumeImageError() {
        _uiState.value = _uiState.value.copy(imageError = null)
    }

    private fun messageFor(validation: ProfileImageValidation): String = when (validation) {
        ProfileImageValidation.Empty -> "That file is empty. Pick a different photo."
        ProfileImageValidation.NotAnImage -> "That file doesn't look like an image."
        is ProfileImageValidation.TooLarge ->
            "That image is too large. Choose one under ${validation.maxBytes / (1024 * 1024)} MB."
        is ProfileImageValidation.UnsupportedType ->
            "Unsupported image type. Use JPEG, PNG, or WebP."
        ProfileImageValidation.Valid -> ""
    }
}
