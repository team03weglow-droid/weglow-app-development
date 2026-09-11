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
    val isLoading: Boolean = false,
    /** Stable Supabase Storage object path persisted in the profile, or null. */
    val profileImagePath: String? = null,
    /** Raw bytes of the persisted profile picture, resolved for display. */
    val profileImage: ByteArray? = null,
    val isUploadingImage: Boolean = false,
    val imageError: String? = null,
) {
    // ByteArray needs structural equals/hashCode for predictable state comparisons.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProfileUiState) return false
        return displayName == other.displayName &&
            isLoading == other.isLoading &&
            profileImagePath == other.profileImagePath &&
            isUploadingImage == other.isUploadingImage &&
            imageError == other.imageError &&
            profileImageContentEquals(other.profileImage)
    }

    override fun hashCode(): Int {
        var result = displayName?.hashCode() ?: 0
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + (profileImagePath?.hashCode() ?: 0)
        result = 31 * result + (profileImage?.contentHashCode() ?: 0)
        result = 31 * result + isUploadingImage.hashCode()
        result = 31 * result + (imageError?.hashCode() ?: 0)
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
                profileImagePath = imagePath,
                profileImage = imageBytes,
                isLoading = false,
            )
        }
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
