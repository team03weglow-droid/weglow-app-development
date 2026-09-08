package com.example.weglow.app

import com.example.weglow.data.remote.supabase.SupabaseClientProvider
import com.example.weglow.data.repository.InMemoryCatalogRepository
import com.example.weglow.data.repository.InMemoryHairstyleRepository
import com.example.weglow.data.repository.SupabaseAuthRepository
import com.example.weglow.data.repository.SupabaseProfileRepository
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.CatalogRepository
import com.example.weglow.domain.repository.HairstyleRepository
import com.example.weglow.domain.repository.ProfileRepository

/**
 * Simple dependency container for Phase 1.
 *
 * Dependencies are created here and passed into presentation code. Repositories no longer reach
 * into global infrastructure singletons themselves, which makes the domain/presentation seams
 * replaceable in tests.
 */
class AppContainer {
    private val supabaseClient by lazy { SupabaseClientProvider.client }

    val authRepository: AuthRepository by lazy { SupabaseAuthRepository(supabaseClient) }
    val profileRepository: ProfileRepository by lazy { SupabaseProfileRepository(supabaseClient) }
    val catalogRepository: CatalogRepository by lazy { InMemoryCatalogRepository() }
    val hairstyleRepository: HairstyleRepository by lazy { InMemoryHairstyleRepository() }
}
