package com.example.weglow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.weglow.data.remote.supabase.SupabaseClientProvider
import com.example.weglow.navigation.WeGlowApp
import com.example.weglow.ui.theme.WeGlowTheme
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle Supabase OAuth / authentication deep links.
        // Example: weglow://login-callback
        SupabaseClientProvider.client.handleDeeplinks(intent)

        enableEdgeToEdge()

        setContent {
            WeGlowTheme {
                WeGlowApp()
            }
        }
    }
}