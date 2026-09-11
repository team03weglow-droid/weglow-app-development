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

    /**
     * `fromScan` is a small, stable navigation argument (never a global mutable holder or a
     * serialized scan result) that tells the Recommendations screen whether it should read the
     * current scan state at all, so a profile-only entry point never picks up stale scan data.
     */
    data object Recommendations : Destination("recommendations?fromScan={fromScan}") {
        fun routeFor(fromScan: Boolean) = "recommendations?fromScan=$fromScan"
    }
    data object Routines : Destination("routines")
    data object Profile : Destination("profile")
}
