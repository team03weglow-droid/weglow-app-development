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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.weglow.app.AppContainer
import com.example.weglow.app.viewModelFactory
import com.example.weglow.feature.auth.AuthEvent
import com.example.weglow.feature.auth.AuthViewModel
import com.example.weglow.feature.auth.StartupDestination
import com.example.weglow.feature.discover.DiscoverViewModel
import com.example.weglow.feature.hairstyle.HairstyleViewModel
import com.example.weglow.feature.onboarding.OnboardingViewModel
import com.example.weglow.feature.scan.ScanViewModel
import com.example.weglow.ui.screens.*
import com.example.weglow.ui.components.WeGlowBottomNavigation
import com.example.weglow.ui.components.WeGlowNavItem
import com.example.weglow.ui.theme.JungeFont

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
        factory = viewModelFactory { AuthViewModel(container.authRepository, container.profileRepository) }
    )
    val onboardingViewModel: OnboardingViewModel = viewModel(
        factory = viewModelFactory { OnboardingViewModel(container.authRepository, container.profileRepository) }
    )
    val scanViewModel: ScanViewModel = viewModel()
    val discoverViewModel: DiscoverViewModel = viewModel(
        factory = viewModelFactory { DiscoverViewModel(container.catalogRepository) }
    )
    val hairstyleViewModel: HairstyleViewModel = viewModel(
        factory = viewModelFactory { HairstyleViewModel(container.hairstyleRepository) }
    )

    val authState by authViewModel.uiState.collectAsState()
    val startupDestination by authViewModel.startupDestination.collectAsState()
    val onboardingState by onboardingViewModel.uiState.collectAsState()
    val scanState by scanViewModel.uiState.collectAsState()
    val discoverState by discoverViewModel.uiState.collectAsState()

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            val currentRoute = currentDestination?.route

            if (currentRoute in tabs.map { it.route }) {
                WeGlowBottomNavigation(
                    items = tabs,
                    selectedRoute = currentRoute,
                    onSelect = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = false }
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
            composable(Destination.Loading.route) {
                var animationFinished by remember { mutableStateOf(false) }
                LoadingScreen(onTimeout = { animationFinished = true })

                LaunchedEffect(animationFinished, startupDestination) {
                    val target = startupDestination ?: return@LaunchedEffect
                    if (!animationFinished) return@LaunchedEffect
                    val route = when (target) {
                        StartupDestination.LOGIN -> Destination.Login.route
                        StartupDestination.ONBOARDING -> Destination.AgeSelection.route
                        StartupDestination.HOME -> Destination.Home.route
                    }
                    navController.navigate(route) {
                        popUpTo(Destination.Loading.route) { inclusive = true }
                    }
                }
            }

            composable(Destination.Login.route) {
                LoginScreen(
                    onLoginClick = authViewModel::signIn,
                    onCreateAccountClick = { navController.navigate(Destination.Signup.route) },
                    isLoading = authState.isLoading,
                    errorMessage = authState.errorMessage,
                )
                LaunchedEffect(authState.event) {
                    when (val event = authState.event) {
                        is AuthEvent.SignedIn -> {
                            val target = if (event.destination == StartupDestination.HOME) Destination.Home else Destination.AgeSelection
                            navController.navigate(target.route) {
                                popUpTo(Destination.Login.route) { inclusive = true }
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
                        onboardingViewModel.start(event.fullName)
                        navController.navigate(Destination.AgeSelection.route)
                        authViewModel.consumeEvent()
                    }
                }
            }

            composable(Destination.AgeSelection.route) {
                AgeSelectionScreen(onContinue = { age ->
                    onboardingViewModel.setAge(age)
                    navController.navigate(Destination.SkinType.route)
                })
            }

            composable(Destination.SkinType.route) {
                SkinTypeScreen(
                    onNext = { skinType ->
                        onboardingViewModel.setSkinType(skinType)
                        navController.navigate(Destination.GenderSelection.route)
                    },
                    onSkip = {
                        onboardingViewModel.setSkinType(null)
                        navController.navigate(Destination.GenderSelection.route)
                    },
                )
            }

            composable(Destination.GenderSelection.route) {
                GenderSelectionScreen(
                    onBack = { navController.popBackStack() },
                    onContinue = { gender ->
                        onboardingViewModel.setGender(gender)
                        navController.navigate(Destination.SkinSensitivity.route)
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
                        navController.navigate(Destination.WelcomeIntro.route)
                    }
                }
            }

            composable(Destination.WelcomeIntro.route) {
                WelcomeIntroScreen(onStartGlow = {
                    navController.navigate(Destination.Home.route) {
                        popUpTo(Destination.AgeSelection.route) { inclusive = true }
                    }
                })
            }

            composable(Destination.Home.route) {
                HomeScreen(
                    onScanClick = { navController.navigate(Destination.Scan.route) },
                    onDiscoverClick = { navController.navigate(Destination.Discover.route) },
                )
            }

            composable(Destination.Discover.route) { DiscoverScreen(products = discoverState.products) }

            composable(Destination.Scan.route) {
                ScanScreen(
                    photoUri = scanState.photoUri,
                    onPhotoCaptured = scanViewModel::setPhoto,
                    onBack = { navController.popBackStack() },
                    onAcneScanComplete = {
                        navController.navigate(Destination.ScanResults.route) {
                            popUpTo(Destination.Scan.route) { inclusive = true }
                        }
                    },
                    onHairstyleScanComplete = {
                        navController.navigate(Destination.HairstyleResults.route) {
                            popUpTo(Destination.Scan.route) { inclusive = true }
                        }
                    },
                )
            }

            composable(Destination.ScanResults.route) {
                ScanResultsScreen(
                    photoUri = scanState.photoUri,
                    onBack = { navController.popBackStack() },
                    onViewRecommendations = {
                        navController.navigate(Destination.Routines.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

            composable(Destination.HairstyleResults.route) {
                HairstyleResultsScreen(
                    result = hairstyleViewModel.resultFor(onboardingState.gender),
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Destination.Routines.route) { RoutinesScreen() }

            composable(Destination.Profile.route) {
                ProfileScreen(onLogout = authViewModel::signOut)
                LaunchedEffect(authState.event) {
                    if (authState.event is AuthEvent.SignedOut) {
                        scanViewModel.clear()
                        navController.navigate(Destination.Login.route) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                        authViewModel.consumeEvent()
                    }
                }
            }
        }
    }
}
