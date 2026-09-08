package com.example.weglow.data

import com.example.weglow.data.remote.supabase.SupabaseClientProvider

/** Legacy alias retained so existing code remains source-compatible during the phased refactor. */
@Deprecated("Use SupabaseClientProvider only from infrastructure/data implementations")
object SupabaseService {
    val client get() = SupabaseClientProvider.client
}
