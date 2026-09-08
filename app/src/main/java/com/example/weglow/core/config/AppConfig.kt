package com.example.weglow.core.config

import com.example.weglow.BuildConfig

/**
 * Central access point for build-time application configuration.
 *
 * Phase 1 intentionally does not initialize backend features from the UI. Later phases should
 * depend on this abstraction rather than reading Gradle/BuildConfig values throughout the app.
 */
object AppConfig {
    val supabaseUrl: String get() = BuildConfig.SUPABASE_URL
    val supabasePublishableKey: String get() = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    fun requireSupabaseConfiguration() {
        check(supabaseUrl.isNotBlank()) {
            "Missing WEGLOW_SUPABASE_URL. Add it to local.properties or a Gradle property."
        }
        check(supabasePublishableKey.isNotBlank()) {
            "Missing WEGLOW_SUPABASE_PUBLISHABLE_KEY. Add it to local.properties or a Gradle property."
        }
    }
}
