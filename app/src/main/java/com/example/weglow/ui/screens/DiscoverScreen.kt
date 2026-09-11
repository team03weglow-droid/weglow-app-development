package com.example.weglow.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weglow.R
import com.example.weglow.ui.components.WeGlowProductImage
import com.example.weglow.ui.theme.*
import com.example.weglow.domain.model.Product


private val CATEGORIES = listOf("All Products", "Trending", "Serums", "Moisturizers", "Hair")
private enum class ProductViewMode { Gallery, List }

@Composable
fun DiscoverScreen(
    products: List<Product>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All Products") }
    var viewMode by remember { mutableStateOf(ProductViewMode.Gallery) }
    var showPriceFilter by remember { mutableStateOf(false) }
    var minimumPrice by remember { mutableStateOf<Double?>(null) }
    var maximumPrice by remember { mutableStateOf<Double?>(null) }
    val filteredProducts = remember(
        products,
        searchQuery,
        selectedCategory,
        minimumPrice,
        maximumPrice,
    ) {
        products.filter { product ->
            val matchesSearch = searchQuery.isBlank() || listOfNotNull(
                product.name,
                product.brandName,
                product.description,
                product.category,
            ).any { it.contains(searchQuery.trim(), ignoreCase = true) }
            val matchesCategory = selectedCategory == "All Products" ||
                product.category?.contains(selectedCategory.removeSuffix("s"), ignoreCase = true) == true
            val matchesPrice = if (minimumPrice == null && maximumPrice == null) {
                true
            } else {
                product.priceLkr?.let { price ->
                    (minimumPrice == null || price >= minimumPrice!!) &&
                        (maximumPrice == null || price <= maximumPrice!!)
                } == true
            }
            matchesSearch && matchesCategory && matchesPrice
        }
    }
    val featuredProduct = filteredProducts.firstOrNull()
    val pairedProduct = filteredProducts.getOrNull(1)

    if (showPriceFilter) {
        PriceFilterDialog(
            currentMinimum = minimumPrice,
            currentMaximum = maximumPrice,
            onDismiss = { showPriceFilter = false },
            onApply = { minimum, maximum ->
                minimumPrice = minimum
                maximumPrice = maximum
                showPriceFilter = false
            },
            onReset = {
                minimumPrice = null
                maximumPrice = null
                showPriceFilter = false
            },
        )
    }

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

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { showPriceFilter = true },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkGreen),
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (minimumPrice != null || maximumPrice != null) "Price filter on" else "Price filter",
                        fontFamily = JungeFont,
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(CardWhite)
                        .padding(3.dp),
                ) {
                    ViewModeButton(
                        selected = viewMode == ProductViewMode.Gallery,
                        icon = { Icon(Icons.Default.GridView, contentDescription = "Gallery view") },
                        onClick = { viewMode = ProductViewMode.Gallery },
                    )
                    ViewModeButton(
                        selected = viewMode == ProductViewMode.List,
                        icon = { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "List view") },
                        onClick = { viewMode = ProductViewMode.List },
                    )
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
                    WeGlowProductImage(
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
                    WeGlowProductImage(
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

            Text(
                "All Products (${filteredProducts.size})",
                fontFamily = JungeFont,
                fontSize = 26.sp,
                color = TextBlack,
            )
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
            items = if (viewMode == ProductViewMode.Gallery) {
                filteredProducts.chunked(2)
            } else {
                filteredProducts.map(::listOf)
            },
            key = { rowItems -> rowItems.joinToString(separator = "|") { it.id } },
        ) { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                if (viewMode == ProductViewMode.Gallery) {
                    rowItems.forEach { product ->
                        ProductGridCard(product = product, modifier = Modifier.weight(1f))
                    }
                    if (rowItems.size < 2) Spacer(modifier = Modifier.weight(1f))
                } else {
                    ProductListCard(product = rowItems.first())
                }
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
            WeGlowProductImage(
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
private fun ProductListCard(product: Product) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeGlowProductImage(
            imageUrl = product.imageUrl,
            contentDescription = product.name,
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            product.brandName?.let { brand ->
                Text(brand, fontFamily = JungeFont, fontSize = 11.sp, color = SoftGray)
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(product.name, fontFamily = JungeFont, fontSize = 16.sp, color = TextBlack)
            product.description?.takeIf(String::isNotBlank)?.let { description ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description,
                    fontFamily = JungeFont,
                    fontSize = 12.sp,
                    color = SoftGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(product.priceLabel, fontFamily = JungeFont, fontSize = 14.sp, color = TextBlack)
                product.ratingLabel?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = WarmGold,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(rating, fontFamily = JungeFont, fontSize = 12.sp, color = TextBlack)
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewModeButton(
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (selected) DarkGreen else Color.Transparent),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = if (selected) Color.White else SoftGray,
        ),
    ) {
        icon()
    }
}

@Composable
private fun PriceFilterDialog(
    currentMinimum: Double?,
    currentMaximum: Double?,
    onDismiss: () -> Unit,
    onApply: (Double?, Double?) -> Unit,
    onReset: () -> Unit,
) {
    var minimumInput by remember(currentMinimum) {
        mutableStateOf(currentMinimum?.toPriceInput().orEmpty())
    }
    var maximumInput by remember(currentMaximum) {
        mutableStateOf(currentMaximum?.toPriceInput().orEmpty())
    }
    val parsedMinimum = minimumInput.toDoubleOrNull()
    val parsedMaximum = maximumInput.toDoubleOrNull()
    val invalidNumber = (minimumInput.isNotBlank() && parsedMinimum == null) ||
        (maximumInput.isNotBlank() && parsedMaximum == null)
    val invalidRange = parsedMinimum != null && parsedMaximum != null && parsedMinimum > parsedMaximum

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by price", fontFamily = JungeFont) },
        text = {
            Column {
                Text("Enter the price range in LKR.", fontFamily = JungeFont, color = SoftGray)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = minimumInput,
                    onValueChange = { minimumInput = it.filterPriceInput() },
                    label = { Text("Minimum price") },
                    prefix = { Text("LKR ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = invalidNumber || invalidRange,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = maximumInput,
                    onValueChange = { maximumInput = it.filterPriceInput() },
                    label = { Text("Maximum price") },
                    prefix = { Text("LKR ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = invalidNumber || invalidRange,
                )
                if (invalidNumber || invalidRange) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (invalidRange) "Minimum price cannot exceed maximum price."
                        else "Enter a valid price.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(parsedMinimum, parsedMaximum) },
                enabled = !invalidNumber && !invalidRange,
            ) {
                Text("Apply", color = DarkGreen)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("Reset", color = SoftGray) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = SoftGray) }
            }
        },
    )
}

private fun String.filterPriceInput(): String {
    var decimalSeen = false
    return filter { character ->
        character.isDigit() || (character == '.' && !decimalSeen).also {
            if (character == '.' && !decimalSeen) decimalSeen = true
        }
    }
}

private fun Double.toPriceInput(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

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
