package com.example.weglow.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weglow.ui.theme.DarkGreen
import com.example.weglow.ui.theme.MintChip
import com.example.weglow.ui.theme.SoftGray

data class WeGlowNavItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun WeGlowBottomNavigation(
    items: List<WeGlowNavItem>,
    selectedRoute: String?,
    onSelect: (WeGlowNavItem) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.72f),
        contentColor = DarkGreen,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.78f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 5.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = selectedRoute == item.route
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) MintChip.copy(alpha = 0.72f) else Color.Transparent)
                        .clickable { onSelect(item) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (selected) DarkGreen else SoftGray,
                        modifier = Modifier.size(19.dp),
                    )
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) DarkGreen else SoftGray,
                    )
                }
            }
        }
    }
}
