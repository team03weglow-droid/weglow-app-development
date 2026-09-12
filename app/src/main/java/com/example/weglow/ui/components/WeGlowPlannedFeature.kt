package com.example.weglow.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.weglow.ui.theme.CardWhite
import com.example.weglow.ui.theme.SoftGray

/** Keeps planned integrations discoverable without suggesting that they already work. */
@Composable
fun WeGlowPlannedFeature(label: String, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            disabledContainerColor = CardWhite,
            disabledContentColor = SoftGray,
        ),
    ) {
        Text("$label · Coming soon", style = MaterialTheme.typography.labelMedium)
    }
}
