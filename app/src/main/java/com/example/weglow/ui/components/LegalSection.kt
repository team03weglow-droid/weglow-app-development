package com.example.weglow.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.weglow.ui.theme.DarkGreen
import com.example.weglow.ui.theme.TextBlack

/** One titled section of a legal document (Privacy Policy, Terms & Conditions). */
@Composable
fun LegalSection(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = DarkGreen)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TextBlack)
    }
}
