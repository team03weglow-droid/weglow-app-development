package com.example.weglow.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.weglow.ui.theme.CoralAccent
import com.example.weglow.ui.theme.TextBlack

data class WeGlowNavItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun WeGlowBottomNavigation(
    items: List<WeGlowNavItem>,
    selectedRoute: String?,
    onSelect: (WeGlowNavItem) -> Unit,
) {
    NavigationBar(containerColor = Color.White) {
        items.forEach { item ->
            val selected = selectedRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CoralAccent,
                    selectedTextColor = CoralAccent,
                    unselectedIconColor = TextBlack,
                    unselectedTextColor = TextBlack,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}
