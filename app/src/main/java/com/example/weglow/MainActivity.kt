package com.example.weglow

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.weglow.data.remote.supabase.SupabaseClientProvider
import com.example.weglow.core.config.AppConfig
import com.example.weglow.navigation.WeGlowApp
import com.example.weglow.ui.theme.WeGlowTheme
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var recoveryLink by androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle Supabase OAuth / authentication deep links.
        // Example: weglow://login-callback
        handleAuthCallback(intent)

        enableEdgeToEdge()

        setContent {
            WeGlowTheme {
                WeGlowApp(recoveryLink = recoveryLink, onRecoveryLinkConsumed = { recoveryLink = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
    }

    private fun handleAuthCallback(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme == AppConfig.authScheme && uri.host == "password-reset") {
            recoveryLink = uri.toString()
            // Do not retain recovery credentials in the Activity intent across recreation.
            intent.data = null
            return
        }
        if (uri.scheme != AppConfig.authScheme || uri.host != "login-callback") return
        val code = runCatching { uri.getQueryParameter("code") }.getOrNull()
            ?.takeIf { it.isNotBlank() && it.length <= 4096 } ?: return
        lifecycleScope.launch {
            try {
                SupabaseClientProvider.client.auth.exchangeCodeForSession(code)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Invalid/replayed/intercepted codes must not crash the app or import a session.
                // The user can retry sign-in; never log the callback URL or authorization code.
            }
        }
    }
}
