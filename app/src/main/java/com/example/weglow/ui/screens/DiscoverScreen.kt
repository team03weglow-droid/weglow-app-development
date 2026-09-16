package com.example.weglow.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weglow.R
import com.example.weglow.ui.components.WeGlowPlannedFeature
import com.example.weglow.ui.components.ProfileAvatar
import com.example.weglow.ui.components.WeGlowProductImage
import com.example.weglow.ui.components.rememberDecodedBitmap
import com.example.weglow.ui.theme.*
import com.example.weglow.domain.model.Product


private const val ALL_PRODUCTS_CATEGORY = "All Products"
private const val FOR_YOU_CATEGORY = "For You"
private val CATEGORIES = listOf(ALL_PRODUCTS_CATEGORY, FOR_YOU_CATEGORY, "Trending", "Serums", "Moisturizers", "Hair")
private enum class ProductViewMode { Gallery, List }

internal fun matchesPriceRange(priceLkr: Double?, minimumPrice: Double?, maximumPrice: Double?): Boolean {
    if (minimumPrice == null && maximumPrice == null) return true
    return priceLkr?.let { price ->
        (minimumPrice == null || price >= minimumPrice) &&
            (maximumPrice == null || price <= maximumPrice)
    } == true
}

@Composable
fun DiscoverScreen(
    products: List<Product>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    profileImage: ByteArray? = null,
    recommendedProducts: List<Product> = emptyList(),
    recommendationsLoading: Boolean = false,
    recommendationsErrorMessage: String? = null,
    onRecommendationsRetry: () -> Unit = {},
    /** [Product.catalogNo] values the current user has already saved. */
    savedProductNumbers: Set<Int> = emptySet(),
    /** [Product.catalogNo] values with a save/unsave currently in flight. */
    pendingSaveProductNumbers: Set<Int> = emptySet(),
    onToggleSaved: (Product) -> Unit = {},
    /** [Product.catalogNo] values with an add-to-cart currently in flight. */
    pendingCartProductNumbers: Set<Int> = emptySet(),
    onAddToCart: (Product) -> Unit = {},
    onCartClick: () -> Unit = {},
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ALL_PRODUCTS_CATEGORY) }
    var viewMode by remember { mutableStateOf(ProductViewMode.Gallery) }
    var showPriceFilter by remember { mutableStateOf(false) }
    var minimumPrice by remember { mutableStateOf<Double?>(null) }
    var maximumPrice by remember { mutableStateOf<Double?>(null) }
    val filteredProducts = remember(
        products,
        recommendedProducts,
        searchQuery,
        selectedCategory,
        minimumPrice,
        maximumPrice,
    ) {
        val visibleProducts = if (selectedCategory == FOR_YOU_CATEGORY) recommendedProducts else products
        visibleProducts.filter { product ->
            val matchesSearch = searchQuery.isBlank() || listOfNotNull(
                product.name,
                product.brandName,
                product.description,
                product.category,
            ).any { it.contains(searchQuery.trim(), ignoreCase = true) }
            val matchesCategory = selectedCategory == ALL_PRODUCTS_CATEGORY ||
                selectedCategory == FOR_YOU_CATEGORY ||
                product.category?.contains(selectedCategory.removeSuffix("s"), ignoreCase = true) == true
            val matchesPrice = matchesPriceRange(product.priceLkr, minimumPrice, maximumPrice)
            matchesSearch && matchesCategory && matchesPrice
        }
    }
    val featuredProduct = filteredProducts.firstOrNull()
    val visibleProductsAreLoading = if (selectedCategory == FOR_YOU_CATEGORY) recommendationsLoading else isLoading
    val visibleProductsError = if (selectedCategory == FOR_YOU_CATEGORY) recommendationsErrorMessage else errorMessage
    val retryVisibleProducts = if (selectedCategory == FOR_YOU_CATEGORY) onRecommendationsRetry else onRetry

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
        // Intentionally not the default rememberSaveable-backed state: this tab's
        // NavBackStackEntry is destroyed and recreated when the bottom nav leaves
        // and returns to it, so a plain remember resets the viewport to the top
        // on return without touching any business/data state.
        state = remember { LazyListState() },
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCartClick,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = "Open cart", tint = DarkGreen)
                    }
                    ProfileAvatar(image = rememberDecodedBitmap(profileImage), size = 36.dp)
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Discover", style = MaterialTheme.typography.headlineLarge, color = TextBlack)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Browse skincare and haircare products in the WeGlow catalog.",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftGray
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search for radiance...", style = MaterialTheme.typography.bodyMedium, color = SoftGray) },
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
                            .clickable(enabled = category != "Trending") { selectedCategory = category }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            if (category == "Trending") "Trending · Coming soon" else category,
                            style = MaterialTheme.typography.bodyMedium,
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
                        style = MaterialTheme.typography.bodyMedium,
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
                    // This section is not personalized from the signed-in user's real skin
                    // type (that lives in Recommendations, driven by RecommendationEngine) -
                    // it must never claim a specific skin type it hasn't actually matched.
                    "Featured Products",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextBlack,
                    modifier = Modifier.weight(1f)
                )
                Text("✦", style = MaterialTheme.typography.titleLarge, color = Clay)
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
                        // No real per-user match score is computed here; a specific percentage
                        // would be fabricated, so this only claims "Featured" placement.
                        Text("Featured", style = MaterialTheme.typography.bodySmall, color = TextBlack)
                    }
                }
                Column(modifier = Modifier.padding(16.dp)) {
                    product.brandName?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = SoftGray)
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                    Text(product.name, style = MaterialTheme.typography.titleLarge, color = TextBlack)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        product.description.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftGray
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(product.priceLabel, style = MaterialTheme.typography.titleMedium, color = TextBlack)
                    Spacer(modifier = Modifier.height(10.dp))
                    val isAddingToCart = product.catalogNo != null && product.catalogNo in pendingCartProductNumbers
                    Button(
                        onClick = { onAddToCart(product) },
                        enabled = product.catalogNo != null && !isAddingToCart,
                        colors = ButtonDefaults.buttonColors(containerColor = DarkGreen, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        if (isAddingToCart) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Add to Bag", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            } }

            Spacer(modifier = Modifier.height(32.dp))

            WeGlowPlannedFeature("Perfect Pairing")
            Spacer(Modifier.height(16.dp))
            Text(
                "${if (selectedCategory == FOR_YOU_CATEGORY) FOR_YOU_CATEGORY else ALL_PRODUCTS_CATEGORY} (${filteredProducts.size})",
                style = MaterialTheme.typography.headlineMedium,
                color = TextBlack,
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (visibleProductsAreLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = DarkGreen)
                }
            } else if (visibleProductsError != null) {
                ProductLoadMessage(
                    message = "We couldn't load products. $visibleProductsError",
                    actionLabel = "Try again",
                    onAction = retryVisibleProducts,
                )
            } else if (filteredProducts.isEmpty()) {
                ProductLoadMessage(
                    message = if (selectedCategory == FOR_YOU_CATEGORY) {
                        "No recommended products are available yet."
                    } else if (products.isEmpty()) {
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
                        ProductGridCard(
                            product = product,
                            isSaved = product.catalogNo != null && product.catalogNo in savedProductNumbers,
                            isSavePending = product.catalogNo != null && product.catalogNo in pendingSaveProductNumbers,
                            onToggleSaved = { onToggleSaved(product) },
                            modifier = Modifier.weight(1f),
                        )
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
private fun ProductGridCard(
    product: Product,
    isSaved: Boolean,
    isSavePending: Boolean,
    onToggleSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        }
        Spacer(modifier = Modifier.height(8.dp))
        product.ratingLabel?.let { rating ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = WarmGold, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(rating, style = MaterialTheme.typography.bodySmall, color = TextBlack)
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
        product.brandName?.let { brand ->
            Text(brand, style = MaterialTheme.typography.labelSmall, color = SoftGray)
            Spacer(modifier = Modifier.height(2.dp))
        }
        Text(product.name, style = MaterialTheme.typography.titleSmall, color = TextBlack)
        Spacer(modifier = Modifier.height(2.dp))
        Text(product.priceLabel, style = MaterialTheme.typography.bodyMedium, color = SoftGray)
        Spacer(Modifier.height(8.dp))
        SaveToggleRow(
            isSaved = isSaved,
            isPending = isSavePending,
            enabled = product.catalogNo != null,
            onClick = onToggleSaved,
        )
    }
}

@Composable
private fun SaveToggleRow(isSaved: Boolean, isPending: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(enabled = enabled && !isPending, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isPending) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DarkGreen)
        } else {
            Icon(
                if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isSaved) "Remove from saved products" else "Save product",
                tint = if (isSaved) LogoutRed else SoftGray,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            if (isSaved) "Saved" else "Save",
            style = MaterialTheme.typography.labelMedium,
            color = if (isSaved) LogoutRed else SoftGray,
        )
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
                Text(brand, style = MaterialTheme.typography.labelSmall, color = SoftGray)
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(product.name, style = MaterialTheme.typography.titleSmall, color = TextBlack)
            product.description?.takeIf(String::isNotBlank)?.let { description ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
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
                Text(product.priceLabel, style = MaterialTheme.typography.bodyMedium, color = TextBlack)
                product.ratingLabel?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = WarmGold,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(rating, style = MaterialTheme.typography.bodySmall, color = TextBlack)
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
        title = { Text("Filter by price", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text("Enter the price range in LKR.", style = MaterialTheme.typography.bodyMedium, color = SoftGray)
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
                        style = MaterialTheme.typography.bodySmall,
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
        Text(message, style = MaterialTheme.typography.bodyMedium, color = SoftGray)
        actionLabel?.let {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onAction) {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = DarkGreen)
            }
        }
    }
}
