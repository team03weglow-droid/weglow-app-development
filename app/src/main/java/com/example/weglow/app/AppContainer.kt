package com.example.weglow.app

import android.content.Context
import com.example.weglow.data.remote.supabase.SupabaseClientProvider
import com.example.weglow.data.repository.LocalHairstyleRepository
import com.example.weglow.data.repository.LocalAcneScanRepository
import com.example.weglow.data.repository.SupabaseAuthRepository
import com.example.weglow.data.repository.SupabaseCatalogRepository
import com.example.weglow.data.repository.SupabaseProfileImageRepository
import com.example.weglow.data.repository.SupabaseProfileRepository
import com.example.weglow.domain.repository.AcneScanRepository
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.CatalogRepository
import com.example.weglow.domain.repository.HairstyleRepository
import com.example.weglow.domain.repository.ProfileImageRepository
import com.example.weglow.domain.repository.ProfileRepository

/**
 * Application-level dependency container.
 *
 * Dependencies are created here and passed into presentation code.
 * Repositories do not reach into global infrastructure singletons themselves,
 * keeping domain and presentation layers replaceable and testable.
 */
class AppContainer {

    private val supabaseClient by lazy {
        SupabaseClientProvider.client
    }

    fun acneScanRepository(context: Context): AcneScanRepository =
        LocalAcneScanRepository(context)

    val authRepository: AuthRepository by lazy {
        SupabaseAuthRepository(supabaseClient)
    }

    val profileRepository: ProfileRepository by lazy {
        SupabaseProfileRepository(supabaseClient)
    }

    val profileImageRepository: ProfileImageRepository by lazy {
        SupabaseProfileImageRepository(supabaseClient)
    }

    val catalogRepository: CatalogRepository by lazy {
        SupabaseCatalogRepository(supabaseClient)
    }

    fun hairstyleRepository(context: Context): HairstyleRepository =
        LocalHairstyleRepository(context)
}
