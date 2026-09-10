package com.example.weglow.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weglow.R
import com.example.weglow.ui.theme.*


@Composable
fun ProfileScreen(onLogout: () -> Unit, displayName: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.weglow_logo),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Image(
                painter = painterResource(R.drawable.avatar_rukman),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Box {
                Image(
                    painter = painterResource(R.drawable.avatar_rukman),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Clay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(displayName ?: "Your Profile", fontFamily = JungeFont, fontSize = 30.sp, color = TextBlack)
            Spacer(modifier = Modifier.height(4.dp))
            Text("GLOW MEMBER", fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray)

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardWhite)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Skin Score", fontFamily = JungeFont, fontSize = 20.sp, color = TextBlack)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Your botanical glow index is improving.",
                        fontFamily = JungeFont,
                        fontSize = 13.sp,
                        color = SoftGray
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = { },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("View Details", fontFamily = JungeFont, fontSize = 12.sp, color = TextBlack)
                    }
                }
                Box(modifier = Modifier.size(90.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = TrackGray,
                            radius = size.minDimension / 2 - 6.dp.toPx(),
                            style = Stroke(width = 6.dp.toPx())
                        )
                        drawArc(
                            color = DarkGreen,
                            startAngle = -90f,
                            sweepAngle = 360f * 0.84f,
                            useCenter = false,
                            style = Stroke(width = 6.dp.toPx())
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("84", fontFamily = JungeFont, fontSize = 22.sp, color = TextBlack)
                        Text("/100", fontFamily = JungeFont, fontSize = 11.sp, color = SoftGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Skin Analysis", fontFamily = JungeFont, fontSize = 24.sp, color = TextBlack)
                Text("See all", fontFamily = JungeFont, fontSize = 14.sp, color = SoftGray)
            }
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardWhite)
                    .clickable { }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(CardGray, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = TextBlack)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Latest Scan", fontFamily = JungeFont, fontSize = 16.sp, color = TextBlack)
                    Text("2 days ago · Hydration focused", fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SoftGray)
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                "My Skincare Shelf",
                fontFamily = JungeFont,
                fontSize = 24.sp,
                color = TextBlack,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                ShelfCard(
                    title = "Morning Routine",
                    itemCount = "4 items",
                    imageRes = R.drawable.shelf_morning_routine,
                    modifier = Modifier.weight(1f)
                )
                ShelfCard(
                    title = "Night Repair",
                    itemCount = "3 items",
                    imageRes = R.drawable.shelf_night_repair,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardWhite)
            ) {
                SettingsRow(icon = Icons.Default.ShoppingBag, label = "Order History")
                HorizontalDivider(color = TrackGray)
                SettingsRow(icon = Icons.Default.FavoriteBorder, label = "Saved Products")
                HorizontalDivider(color = TrackGray)
                SettingsRow(icon = Icons.Default.Person, label = "Account Settings")
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Log Out",
                fontFamily = JungeFont,
                fontSize = 16.sp,
                color = LogoutRed,
                modifier = Modifier.clickable(onClick = onLogout)
            )

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun ShelfCard(title: String, itemCount: String, imageRes: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(12.dp)
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(title, fontFamily = JungeFont, fontSize = 15.sp, color = TextBlack)
        Text(itemCount, fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray)
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = DarkGreen)
            Spacer(modifier = Modifier.width(14.dp))
            Text(label, fontFamily = JungeFont, fontSize = 16.sp, color = TextBlack)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SoftGray)
    }
}