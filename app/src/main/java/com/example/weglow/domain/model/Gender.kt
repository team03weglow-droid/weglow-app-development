package com.example.weglow.domain.model

/**
 * The gender values the app supports end to end: presented during onboarding's
 * [com.example.weglow.ui.screens.GenderSelectionScreen], editable afterwards from Profile, persisted
 * verbatim in the `profiles.gender` column, and matched (case-insensitively, with a few accepted
 * synonyms - see `genderSynonyms` in `SupabaseHairstyleRepository`) against `hairstyles.gender` when
 * building recommendations. Onboarding and Profile must always offer this same list so a value
 * chosen in either place is always one the other already understands.
 */
object Gender {
    val OPTIONS: List<String> = listOf("Female", "Male", "Other/Prefer not to say")
}
