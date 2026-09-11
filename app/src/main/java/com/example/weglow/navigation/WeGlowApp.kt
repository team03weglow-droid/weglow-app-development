package com.example.weglow.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.weglow.app.AppContainer
import com.example.weglow.app.viewModelFactory
import com.example.weglow.feature.auth.AuthEvent
import com.example.weglow.feature.auth.AuthViewModel
import com.example.weglow.feature.auth.StartupDestination
import com.example.weglow.feature.discover.DiscoverViewModel
import com.example.weglow.feature.hairstyle.HairstyleViewModel
import com.example.weglow.feature.onboarding.OnboardingViewModel
import com.example.weglow.feature.profile.ProfileViewModel
import com.example.weglow.feature.recommendation.RecommendationViewModel
import com.example.weglow.feature.routine.RoutineViewModel
import com.example.weglow.feature.scan.ScanViewModel
import com.example.weglow.ui.components.WeGlowBottomNavigation
import com.example.weglow.ui.components.WeGlowNavItem
import com.example.weglow.ui.screens.*

private val tabs = listOf(
    WeGlowNavItem(Destination.Home.route, "Home", Icons.Default.Home),
    WeGlowNavItem(Destination.Discover.route, "Discover", Icons.Default.Explore),
    WeGlowNavItem(Destination.Scan.route, "Scan", Icons.Default.CameraAlt),
    WeGlowNavItem(Destination.Routines.route, "Routines", Icons.Default.Spa),
    WeGlowNavItem(Destination.Profile.route, "Profile", Icons.Default.Person),
)

@Composable
fun WeGlowApp() {

    val navController = rememberNavController()
    val container = remember { AppContainer() }

    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            AuthViewModel(
                container.authRepository,
                container.profileRepository
            )
        }
    )

    val onboardingViewModel: OnboardingViewModel = viewModel(
        factory = viewModelFactory {
            OnboardingViewModel(
                container.authRepository,
                container.profileRepository
            )
        }
    )

    val context = LocalContext.current.applicationContext
    val scanViewModel: ScanViewModel = viewModel(
        factory = viewModelFactory { ScanViewModel(container.acneScanRepository(context)) }
    )

    val discoverViewModel: DiscoverViewModel = viewModel(
        factory = viewModelFactory {
            DiscoverViewModel(container.catalogRepository)
        }
    )

    val hairstyleViewModel: HairstyleViewModel = viewModel(
        factory = viewModelFactory {
            HairstyleViewModel(container.hairstyleRepository(context))
        }
    )

    val profileViewModel: ProfileViewModel = viewModel(
        factory = viewModelFactory {
            ProfileViewModel(
                container.authRepository,
                container.profileRepository,
                container.profileImageRepository
            )
        }
    )

    val recommendationViewModel: RecommendationViewModel = viewModel(
        factory = viewModelFactory {
            RecommendationViewModel(
                container.authRepository,
                container.profileRepository,
                container.catalogRepository,
            )
        }
    )

    val routineViewModel: RoutineViewModel = viewModel(
        factory = viewModelFactory {
            RoutineViewModel(
                container.authRepository,
                container.profileRepository,
                container.catalogRepository,
            )
        }
    )

    val authState by authViewModel.uiState.collectAsState()
    val startupDestination by authViewModel.startupDestination.collectAsState()
    val onboardingState by onboardingViewModel.uiState.collectAsState()
    val scanState by scanViewModel.uiState.collectAsState()
    val hairstyleState by hairstyleViewModel.uiState.collectAsState()
    val discoverState by discoverViewModel.uiState.collectAsState()
    val profileState by profileViewModel.uiState.collectAsState()
    val recommendationState by recommendationViewModel.uiState.collectAsState()
    val routineState by routineViewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {

            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route

            if (currentRoute in tabs.map { it.route }) {

                WeGlowBottomNavigation(
                    items = tabs,
                    selectedRoute = currentRoute,
                    onSelect = { tab ->

                        navController.navigate(tab.route) {

                            // Phase 3:
                            // Home is the stable anchor for main-app navigation.
                            popUpTo(Destination.Home.route) {
                                saveState = true
                            }

                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    ) { padding ->

        NavHost(
            navController = navController,
            startDestination = Destination.Loading.route,
            modifier = Modifier.padding(padding),
        ) {

            // ---------------------------------------------------------
            // STARTUP
            // ---------------------------------------------------------

            composable(Destination.Loading.route) {

                var animationFinished by remember {
                    mutableStateOf(false)
                }

                LoadingScreen(
                    onTimeout = {
                        animationFinished = true
                    }
                )

                LaunchedEffect(
                    animationFinished,
                    startupDestination
                ) {

                    val target =
                        startupDestination ?: return@LaunchedEffect

                    if (!animationFinished) {
                        return@LaunchedEffect
                    }

                    val route = when (target) {

                        StartupDestination.LOGIN ->
                            Destination.Login.route

                        StartupDestination.ONBOARDING -> {
                            // Restored session with unfinished onboarding: reset the
                            // wizard and resolve identity before the first question.
                            onboardingViewModel.start()
                            Destination.AgeSelection.route
                        }

                        StartupDestination.HOME ->
                            Destination.Home.route
                    }

                    navController.navigate(route) {

                        popUpTo(Destination.Loading.route) {
                            inclusive = true
                        }

                        launchSingleTop = true
                    }
                }
            }

            // ---------------------------------------------------------
            // AUTHENTICATION
            // ---------------------------------------------------------

            composable(Destination.Login.route) {

                LoginScreen(
                    onLoginClick = authViewModel::signIn,

                    onGoogleLoginClick = {
                        authViewModel.signInWithGoogle()
                    },

                    onCreateAccountClick = {
                        navController.navigate(
                            Destination.Signup.route
                        ) {
                            launchSingleTop = true
                        }
                    },

                    isLoading = authState.isLoading,
                    errorMessage = authState.errorMessage,
                )

                LaunchedEffect(authState.event) {

                    when (val event = authState.event) {

                        is AuthEvent.SignedIn -> {

                            val target =
                                if (
                                    event.destination ==
                                    StartupDestination.HOME
                                ) {
                                    Destination.Home
                                } else {
                                    // Sign-in / Google for a user who has not
                                    // finished onboarding: reset the wizard and
                                    // resolve identity before the first question.
                                    onboardingViewModel.start()
                                    Destination.AgeSelection
                                }

                            navController.navigate(target.route) {

                                popUpTo(Destination.Login.route) {
                                    inclusive = true
                                }

                                launchSingleTop = true
                            }

                            authViewModel.consumeEvent()
                        }

                        else -> Unit
                    }
                }
            }

            composable(Destination.Signup.route) {

                SignUpScreen(
                    onCreateAccount = authViewModel::signUp,
                    isLoading = authState.isLoading,
                    errorMessage = authState.errorMessage,
                )

                LaunchedEffect(authState.event) {

                    val event = authState.event

                    if (event is AuthEvent.SignedUp) {

                        onboardingViewModel.start(
                            event.fullName
                        )

                        navController.navigate(
                            Destination.AgeSelection.route
                        ) {

                            // Remove authentication screens after signup.
                            popUpTo(Destination.Login.route) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }

                        authViewModel.consumeEvent()
                    }
                }
            }

            // ---------------------------------------------------------
            // ONBOARDING
            // ---------------------------------------------------------

            composable(Destination.AgeSelection.route) {

                AgeSelectionScreen(
                    onContinue = { age ->

                        onboardingViewModel.setAge(age)

                        navController.navigate(
                            Destination.SkinType.route
                        ) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Destination.SkinType.route) {

                SkinTypeScreen(

                    onNext = { skinType ->

                        onboardingViewModel.setSkinType(skinType)

                        navController.navigate(
                            Destination.GenderSelection.route
                        ) {
                            launchSingleTop = true
                        }
                    },

                    onSkip = {

                        onboardingViewModel.setSkinType(null)

                        navController.navigate(
                            Destination.GenderSelection.route
                        ) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Destination.GenderSelection.route) {

                GenderSelectionScreen(

                    onBack = {
                        navController.popBackStack()
                    },

                    onContinue = { gender ->

                        onboardingViewModel.setGender(gender)

                        navController.navigate(
                            Destination.SkinSensitivity.route
                        ) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Destination.SkinSensitivity.route) {

                SkinSensitivityScreen(
                    onAnswer = onboardingViewModel::setSensitivityAndSave,
                    isSaving = onboardingState.isSaving,
                    errorMessage = onboardingState.errorMessage,
                )

                LaunchedEffect(onboardingState.saveCompleted) {

                    if (onboardingState.saveCompleted) {

                        onboardingViewModel.consumeSaveCompleted()

                        navController.navigate(
                            Destination.WelcomeIntro.route
                        ) {
                            launchSingleTop = true
                        }
                    }
                }
            }

            composable(Destination.WelcomeIntro.route) {

                WelcomeIntroScreen(
                    onStartGlow = {

                        navController.navigate(
                            Destination.Home.route
                        ) {

                            /*
                             * Phase 3 blocker fix:
                             * Clear the ENTIRE onboarding wizard.
                             *
                             * Removes:
                             * AgeSelection
                             * SkinType
                             * GenderSelection
                             * SkinSensitivity
                             * WelcomeIntro
                             */
                            popUpTo(Destination.AgeSelection.route) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }
                    }
                )
            }

            // ---------------------------------------------------------
            // MAIN APP
            // ---------------------------------------------------------

            composable(Destination.Home.route) {

                LaunchedEffect(Unit) {
                    profileViewModel.refresh()
                    routineViewModel.load()
                }

                HomeScreen(

                    displayName = profileState.displayName,
                    profileImage = profileState.profileImage,
                    morningRoutine = routineState.plan?.morning.orEmpty(),

                    onScanClick = {

                        navController.navigate(
                            Destination.Scan.route
                        ) {
                            launchSingleTop = true
                        }
                    },

                    onDiscoverClick = {

                        navController.navigate(
                            Destination.Discover.route
                        ) {
                            launchSingleTop = true
                        }
                    },

                    onRecommendationsClick = {

                        navController.navigate(
                            Destination.Recommendations.routeFor(fromScan = false)
                        ) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Destination.Discover.route) {
                LaunchedEffect(Unit) {
                    discoverViewModel.loadProducts()
                    profileViewModel.refresh()
                }

                DiscoverScreen(
                    products = discoverState.products,
                    isLoading = discoverState.isLoading,
                    errorMessage = discoverState.errorMessage,
                    onRetry = discoverViewModel::loadProducts,
                    profileImage = profileState.profileImage,
                )
            }

            // ---------------------------------------------------------
            // SCAN
            // ---------------------------------------------------------

            composable(Destination.Scan.route) {

                ScanScreen(
                    photoUri = scanState.photoUri,
                    acneState = scanState,
                    hairstyleState = hairstyleState,
                    onAnalyzeAcne = scanViewModel::analyze,
                    onCancelAcne = scanViewModel::cancelAnalysis,
                    onAnalyzeHairstyle = { uri ->
                        hairstyleViewModel.analyze(uri.toString(), onboardingState.gender)
                    },
                    onCancelHairstyle = hairstyleViewModel::cancelAnalysis,
                    onPhotoCaptured = scanViewModel::setPhoto,

                    onBack = {
                        navController.popBackStack()
                    },

                    onAcneScanComplete = {

                        navController.navigate(
                            Destination.ScanResults.route
                        ) {

                            popUpTo(Destination.Scan.route) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }
                    },

                    onHairstyleScanComplete = {

                        navController.navigate(
                            Destination.HairstyleResults.route
                        ) {

                            popUpTo(Destination.Scan.route) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Destination.ScanResults.route) {

                ScanResultsScreen(
                    photoUri = scanState.photoUri,
                    result = scanState.result,

                    onBack = {
                        navController.popBackStack()
                    },

                    onViewRecommendations = {

                        navController.navigate(
                            Destination.Recommendations.routeFor(fromScan = true)
                        ) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                route = Destination.Recommendations.route,
                arguments = listOf(navArgument("fromScan") { type = NavType.BoolType; defaultValue = false }),
            ) { backStackEntry ->

                val fromScan = backStackEntry.arguments?.getBoolean("fromScan") == true
                val scanConcerns = if (fromScan) scanState.result?.detections?.map { it.label } else null

                LaunchedEffect(Unit) {
                    recommendationViewModel.load(scanConcerns)
                }

                RecommendationsScreen(
                    isLoading = recommendationState.isLoading,
                    result = recommendationState.result,
                    errorMessage = recommendationState.errorMessage,
                    onBack = {
                        navController.popBackStack()
                    },
                    onRetry = {
                        recommendationViewModel.load(scanConcerns)
                    },
                )
            }

            composable(Destination.HairstyleResults.route) {
                val result = hairstyleState.result
                if (result != null) {
                    HairstyleResultsScreen(
                        result = result,
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }

            // ---------------------------------------------------------
            // ROUTINES
            // ---------------------------------------------------------

            composable(Destination.Routines.route) {

                LaunchedEffect(Unit) {
                    routineViewModel.load()
                    profileViewModel.refresh()
                }

                RoutinesScreen(
                    isLoading = routineState.isLoading,
                    errorMessage = routineState.errorMessage,
                    plan = routineState.plan,
                    onRetry = routineViewModel::load,
                    displayName = profileState.displayName,
                    profileImage = profileState.profileImage,
                )
            }

            // ---------------------------------------------------------
            // PROFILE
            // ---------------------------------------------------------

            composable(Destination.Profile.route) {

                LaunchedEffect(Unit) { profileViewModel.refresh() }

                ProfileScreen(
                    displayName = profileState.displayName,
                    profileImage = profileState.profileImage,
                    isUploadingImage = profileState.isUploadingImage,
                    imageError = profileState.imageError,
                    onProfileImagePicked = profileViewModel::onProfileImagePicked,
                    onProfileImageUnreadable = profileViewModel::onProfileImageUnreadable,
                    onConsumeImageError = profileViewModel::consumeImageError,
                    onLogout = authViewModel::signOut
                )

                LaunchedEffect(authState.event) {

                    if (authState.event is AuthEvent.SignedOut) {

                        scanViewModel.clear()
                        hairstyleViewModel.clear()

                        navController.navigate(
                            Destination.Login.route
                        ) {

                            // Completely clear authenticated navigation.
                            popUpTo(navController.graph.id) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }

                        authViewModel.consumeEvent()
                    }
                }
            }
        }
    }
}
