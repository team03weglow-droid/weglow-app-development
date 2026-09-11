package com.example.weglow.data.remote.supabase

import com.example.weglow.core.config.AppConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Single infrastructure-level owner of the Supabase client.
 *
 * The client is lazy so offline UI development does not fail merely because
 * backend configuration is absent. A backend feature that actually accesses
 * the client must be configured.
 */
object SupabaseClientProvider {

    val client by lazy {
        AppConfig.requireSupabaseConfiguration()

        createSupabaseClient(
            supabaseUrl = AppConfig.supabaseUrl,
            supabaseKey = AppConfig.supabasePublishableKey,
        ) {
            install(Postgrest)

            // Phase 6: private bucket "profile-images" for user profile pictures.
            install(Storage)

            install(Auth) {
                scheme = "weglow"
                host = "login-callback"
            }
        }
    }
}