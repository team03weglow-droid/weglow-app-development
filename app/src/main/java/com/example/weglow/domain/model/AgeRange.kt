package com.example.weglow.domain.model

/**
 * The age-range values the app supports end to end: presented during onboarding's
 * [com.example.weglow.ui.screens.AgeSelectionScreen] and persisted verbatim in the
 * `profiles.age_range` column. WeGlow requires a minimum age of 14 to create an account, so
 * "Under 14" is deliberately not one of these options - it is not just hidden from the picker,
 * it is a value [com.example.weglow.feature.onboarding.OnboardingViewModel.setAge] will not
 * accept, since that is the single point every age value passes through on its way into the
 * saved profile. Onboarding must always offer exactly this list so a value chosen in the UI is
 * always one the rest of the app already understands.
 */
object AgeRange {
    val OPTIONS: List<String> = listOf("14–24", "25–35", "36–45", "46–59", "60+")
}
