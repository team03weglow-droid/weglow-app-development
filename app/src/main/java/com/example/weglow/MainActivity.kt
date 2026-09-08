package com.example.weglow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.weglow.navigation.WeGlowApp
import com.example.weglow.ui.theme.WeGlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeGlowTheme {
                WeGlowApp()
            }
        }
    }
}
