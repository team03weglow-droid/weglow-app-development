package com.example.weglow.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, contentDescription = item.label) },
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
