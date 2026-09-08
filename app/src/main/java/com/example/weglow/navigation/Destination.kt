package com.example.weglow.navigation

sealed class Destination(val route: String) {
    data object Loading : Destination("loading")
    data object Login : Destination("login")
    data object Signup : Destination("signup")
    data object AgeSelection : Destination("age_selection")
    data object SkinType : Destination("skin_type")
    data object GenderSelection : Destination("gender_selection")
    data object SkinSensitivity : Destination("skin_sensitivity")
    data object WelcomeIntro : Destination("welcome_intro")
    data object Home : Destination("home")
    data object Discover : Destination("discover")
    data object Scan : Destination("scan")
    data object ScanResults : Destination("scan_results")
    data object HairstyleResults : Destination("hairstyle_results")
    data object Routines : Destination("routines")
    data object Profile : Destination("profile")
}
