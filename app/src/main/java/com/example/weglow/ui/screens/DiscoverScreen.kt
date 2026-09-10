package com.example.weglow.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.weglow.R
import com.example.weglow.ui.theme.*
import com.example.weglow.domain.model.Product


private val CATEGORIES = listOf("All Products", "Trending", "Serums", "Moisturizers", "Hair")

@Composable
fun DiscoverScreen(
    products: List<Product>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All Products") }
    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        products.filter { product ->
            val matchesSearch = searchQuery.isBlank() || listOfNotNull(
                product.name,
                product.brandName,
                product.description,
                product.category,
            ).any { it.contains(searchQuery.trim(), ignoreCase = true) }
            val matchesCategory = selectedCategory == "All Products" ||
                product.category?.contains(selectedCategory.removeSuffix("s"), ignoreCase = true) == true
            matchesSearch && matchesCategory
        }
    }
    val featuredProduct = filteredProducts.firstOrNull()
    val pairedProduct = filteredProducts.getOrNull(1)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground),
    ) {
        item {
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
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Discover", fontFamily = JungeFont, fontSize = 30.sp, color = TextBlack)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Tips, trends and picks curated for your skin.",
                fontFamily = JungeFont,
                fontSize = 14.sp,
                color = SoftGray
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search for radiance...", fontFamily = JungeFont, color = SoftGray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SoftGray) },
                singleLine = true,
                shape = RoundedCornerShape(999.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = CardWhite,
                    focusedContainerColor = CardWhite,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(CATEGORIES) { category ->
                    val isSelected = category == selectedCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSelected) DarkGreen else CardWhite)
                            .clickable { selectedCategory = category }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            category,
                            fontFamily = JungeFont,
                            fontSize = 13.sp,
                            color = if (isSelected) Color.White else TextBlack
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    "Recommended for your Dry Skin",
                    fontFamily = JungeFont,
                    fontSize = 24.sp,
                    color = TextBlack,
                    modifier = Modifier.weight(1f)
                )
                Text("✦", fontSize = 20.sp, color = Clay)
            }

            Spacer(modifier = Modifier.height(16.dp))

            featuredProduct?.let { product -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardWhite)
            ) {
                Box {
                    ProductImage(
                        imageUrl = product.imageUrl,
                        contentDescription = product.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DarkGreen, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("98% Match", fontFamily = JungeFont, fontSize = 12.sp, color = TextBlack)
                    }
                }
                Column(modifier = Modifier.padding(16.dp)) {
                    product.brandName?.let {
                        Text(it, fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray)
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                    Text(product.name, fontFamily = JungeFont, fontSize = 22.sp, color = TextBlack)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        product.description.orEmpty(),
                        fontFamily = JungeFont,
                        fontSize = 13.sp,
                        color = SoftGray
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(product.priceLabel, fontFamily = JungeFont, fontSize = 18.sp, color = TextBlack)
                        Button(
                            onClick = { },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text("Add to Bag", fontFamily = JungeFont, fontSize = 14.sp)
                        }
                    }
                }
            } }

            Spacer(modifier = Modifier.height(20.dp))

            pairedProduct?.let { product -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardWhite)
            ) {
                Box {
                    ProductImage(
                        imageUrl = product.imageUrl,
                        contentDescription = product.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    )
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(PairingCoral)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("♥", color = Color.White, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Perfect Pairing", fontFamily = JungeFont, fontSize = 12.sp, color = Color.White)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(product.name, fontFamily = JungeFont, fontSize = 18.sp, color = TextBlack)
                        product.brandName?.let { brand ->
                            Text(brand, fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray)
                        }
                        Text(product.priceLabel, fontFamily = JungeFont, fontSize = 14.sp, color = SoftGray)
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PillGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = TextBlack)
                    }
                }
            } }

            Spacer(modifier = Modifier.height(32.dp))

            Text("All Products", fontFamily = JungeFont, fontSize = 26.sp, color = TextBlack)
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = DarkGreen)
                }
            } else if (errorMessage != null) {
                ProductLoadMessage(
                    message = "We couldn't load products. $errorMessage",
                    actionLabel = "Try again",
                    onAction = onRetry,
                )
            } else if (filteredProducts.isEmpty()) {
                ProductLoadMessage(
                    message = if (products.isEmpty()) {
                        "No products are available yet."
                    } else {
                        "No products match your search."
                    },
                )
            }
            }
        }

        items(
            items = filteredProducts.chunked(2),
            key = { rowItems -> rowItems.joinToString(separator = "|") { it.id } },
        ) { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                rowItems.forEach { product ->
                    ProductGridCard(product = product, modifier = Modifier.weight(1f))
                }
                if (rowItems.size < 2) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        item {
            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}

@Composable
private fun ProductGridCard(product: Product, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box {
            ProductImage(
                imageUrl = product.imageUrl,
                contentDescription = product.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
            )
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = TextBlack, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        product.ratingLabel?.let { rating ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = WarmGold, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(rating, fontFamily = JungeFont, fontSize = 12.sp, color = TextBlack)
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
        product.brandName?.let { brand ->
            Text(brand, fontFamily = JungeFont, fontSize = 11.sp, color = SoftGray)
            Spacer(modifier = Modifier.height(2.dp))
        }
        Text(product.name, fontFamily = JungeFont, fontSize = 14.sp, color = TextBlack)
        Spacer(modifier = Modifier.height(2.dp))
        Text(product.priceLabel, fontFamily = JungeFont, fontSize = 13.sp, color = SoftGray)
    }
}

@Composable
private fun ProductImage(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        placeholder = painterResource(R.drawable.weglow_logo),
        error = painterResource(R.drawable.weglow_logo),
        fallback = painterResource(R.drawable.weglow_logo),
        contentScale = ContentScale.Crop,
        modifier = modifier.background(PillGray),
    )
}

@Composable
private fun ProductLoadMessage(
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, fontFamily = JungeFont, fontSize = 14.sp, color = SoftGray)
        actionLabel?.let {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onAction) {
                Text(it, fontFamily = JungeFont, color = DarkGreen)
            }
        }
    }
}
