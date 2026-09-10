package com.example.weglow.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.AuthenticationState
import com.example.weglow.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val event: AuthEvent? = null,
)

sealed interface AuthEvent {
    data class SignedIn(
        val destination: StartupDestination,
    ) : AuthEvent

    data class SignedUp(
        val fullName: String,
    ) : AuthEvent

    data object SignedOut : AuthEvent
}

enum class StartupDestination {
    LOGIN,
    ONBOARDING,
    HOME,
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> =
        _uiState.asStateFlow()

    private val _startupDestination =
        MutableStateFlow<StartupDestination?>(null)

    val startupDestination: StateFlow<StartupDestination?> =
        _startupDestination.asStateFlow()

    private var hadAuthenticatedSession = false

    /*
     * Keeps track of what caused an authentication change.
     *
     * This is important because Supabase can emit AUTHENTICATED
     * during email signup as well as normal login / Google OAuth.
     *
     * During SIGN_UP we must allow signUp() itself to emit
     * SignedUp(fullName), otherwise the session observer could
     * replace it with SignedIn and lose the entered name.
     */
    private enum class AuthOperation {
        NONE,
        SIGN_IN,
        SIGN_UP,
        GOOGLE,
    }

    private var currentOperation = AuthOperation.NONE

    init {
        observeAuthenticationState()
    }

    private fun observeAuthenticationState() {
        viewModelScope.launch {
            authRepository.authenticationState.collect { state ->

                when (state) {

                    AuthenticationState.INITIALIZING -> {
                        // Wait for Supabase to determine whether
                        // a stored session exists.
                    }

                    AuthenticationState.NOT_AUTHENTICATED -> {
                        hadAuthenticatedSession = false

                        if (_startupDestination.value == null) {
                            _startupDestination.value =
                                StartupDestination.LOGIN
                        }
                    }

                    AuthenticationState.AUTHENTICATED -> {

                        /*
                         * Email signup is handled by signUp().
                         *
                         * Do NOT emit SignedIn here because signUp()
                         * needs to emit SignedUp(fullName).
                         */
                        if (currentOperation == AuthOperation.SIGN_UP) {
                            hadAuthenticatedSession = true
                            return@collect
                        }

                        val destination = determineDestination()

                        if (_startupDestination.value == null) {

                            /*
                             * Existing authenticated session restored
                             * when the application starts.
                             */
                            _startupDestination.value = destination

                        } else if (!hadAuthenticatedSession) {

                            /*
                             * A new authenticated session was created.
                             *
                             * This covers:
                             * - email/password login
                             * - Google OAuth returning through deep link
                             */
                            _uiState.value = AuthUiState(
                                event = AuthEvent.SignedIn(
                                    destination
                                )
                            )
                        }

                        hadAuthenticatedSession = true
                        currentOperation = AuthOperation.NONE
                    }
                }
            }
        }
    }

    fun resolveStartupDestination() {
        viewModelScope.launch {

            runCatching {
                determineDestination()
            }.onSuccess { destination ->

                _startupDestination.value = destination

            }.onFailure {

                _startupDestination.value =
                    StartupDestination.LOGIN
            }
        }
    }

    fun signIn(
        email: String,
        password: String,
    ) {

        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState(
                errorMessage =
                    "Enter your email and password."
            )
            return
        }

        if (_uiState.value.isLoading) return

        currentOperation = AuthOperation.SIGN_IN

        viewModelScope.launch {

            _uiState.value =
                AuthUiState(isLoading = true)

            authRepository.signIn(
                email = email.trim(),
                password = password,
            ).onSuccess {

                /*
                 * Do not navigate here.
                 *
                 * Supabase authenticationState will emit
                 * AUTHENTICATED and the observer will perform
                 * navigation.
                 */

            }.onFailure { error ->

                currentOperation = AuthOperation.NONE

                _uiState.value = AuthUiState(
                    errorMessage =
                        error.message
                            ?: "Unable to sign in."
                )
            }
        }
    }

    fun signInWithGoogle() {

        if (_uiState.value.isLoading) return

        currentOperation = AuthOperation.GOOGLE

        viewModelScope.launch {

            _uiState.value =
                AuthUiState(isLoading = true)

            authRepository
                .signInWithGoogle()
                .onSuccess {

                    /*
                     * Opening the OAuth browser is not the same
                     * as being authenticated.
                     *
                     * Authentication finishes when Google returns
                     * through the WeGlow deep link and Supabase
                     * emits AUTHENTICATED.
                     */
                    _uiState.value = AuthUiState()

                }.onFailure { error ->

                    currentOperation = AuthOperation.NONE

                    _uiState.value = AuthUiState(
                        errorMessage =
                            error.message
                                ?: "Unable to start Google sign in."
                    )
                }
        }
    }

    fun signUp(
        fullName: String,
        email: String,
        password: String,
    ) {

        if (
            fullName.isBlank() ||
            email.isBlank() ||
            password.length < 8 ||
            password.none { it.isDigit() }
        ) {

            _uiState.value = AuthUiState(
                errorMessage =
                    "Enter your name, a valid email and a password " +
                            "of at least 8 characters with a number."
            )

            return
        }

        if (_uiState.value.isLoading) return

        currentOperation = AuthOperation.SIGN_UP

        viewModelScope.launch {

            _uiState.value =
                AuthUiState(isLoading = true)

            authRepository.signUp(
                email = email.trim(),
                password = password,
            ).onSuccess {

                /*
                 * SIGN_UP deliberately owns this navigation event.
                 *
                 * The authentication-state observer suppresses
                 * SignedIn while SIGN_UP is active so that the
                 * user's full name cannot be lost.
                 */
                hadAuthenticatedSession =
                    authRepository.hasActiveSession()

                currentOperation = AuthOperation.NONE

                _uiState.value = AuthUiState(
                    event = AuthEvent.SignedUp(
                        fullName = fullName.trim()
                    )
                )

            }.onFailure { error ->

                currentOperation = AuthOperation.NONE

                _uiState.value = AuthUiState(
                    errorMessage =
                        error.message
                            ?: "Unable to create account."
                )
            }
        }
    }

    fun signOut() {

        if (_uiState.value.isLoading) return

        viewModelScope.launch {

            _uiState.value =
                AuthUiState(isLoading = true)

            authRepository
                .signOut()
                .onSuccess {

                    hadAuthenticatedSession = false
                    currentOperation = AuthOperation.NONE

                    _uiState.value = AuthUiState(
                        event = AuthEvent.SignedOut
                    )

                }.onFailure { error ->

                    currentOperation = AuthOperation.NONE

                    _uiState.value = AuthUiState(
                        errorMessage =
                            error.message
                                ?: "Unable to sign out."
                    )
                }
        }
    }

    fun consumeEvent() {

        _uiState.value =
            _uiState.value.copy(
                event = null
            )
    }

    private suspend fun determineDestination():
            StartupDestination {

        val userId =
            authRepository.currentUserId()

        if (
            !authRepository.hasActiveSession() ||
            userId == null
        ) {
            return StartupDestination.LOGIN
        }

        val completed =
            profileRepository
                .hasCompletedOnboarding(userId)
                .getOrDefault(false)

        return if (completed) {
            StartupDestination.HOME
        } else {
            StartupDestination.ONBOARDING
        }
    }
}