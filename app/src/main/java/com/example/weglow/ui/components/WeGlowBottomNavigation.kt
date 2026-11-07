package com.example.weglow.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.example.weglow.ui.theme.DarkGreen
import com.example.weglow.ui.theme.SoftGray

data class WeGlowNavItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun WeGlowBottomNavigation(
    items: List<WeGlowNavItem>,
    selectedRoute: String?,
    onSelect: (WeGlowNavItem) -> Unit,
) {
    NavigationBar(containerColor = com.example.weglow.ui.theme.PageBackground) {
        items.forEach { item ->
            val selected = selectedRoute == item.route
            val scale by animateFloatAsState(if (selected) 1.12f else 1f, tween(220), label = "Selected tab")
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, contentDescription = null, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) },
                label = {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = DarkGreen,
                    selectedTextColor = DarkGreen,
                    unselectedIconColor = SoftGray,
                    unselectedTextColor = SoftGray,
                    indicatorColor = com.example.weglow.ui.theme.CardWhite,
                ),
            )
        }
    }
}
