package com.example.weglow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.weglow.domain.model.Product
import com.example.weglow.ui.components.WeGlowEmptyState
import com.example.weglow.ui.components.WeGlowErrorView
import com.example.weglow.ui.components.WeGlowLoadingView
import com.example.weglow.ui.components.WeGlowProductImage
import com.example.weglow.ui.theme.*

/**
 * Products the authenticated user saved from Discover. Reads/writes go through the same
 * [com.example.weglow.feature.savedproducts.SavedProductsViewModel] Discover's heart toggle
 * uses, and "Add to Cart" here reuses [com.example.weglow.feature.cart.CartViewModel] - this
 * screen introduces no separate saved-product or cart logic of its own.
 */
@Composable
fun SavedProductsScreen(
    isLoading: Boolean,
    products: List<Product>,
    errorMessage: String?,
    pendingProductNumbers: Set<Int>,
    cartPendingProductNumbers: Set<Int>,
    onBack: () -> Unit,
    onRemove: (Product) -> Unit,
    onAddToCart: (Product) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(PageBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextBlack)
            }
            Text("Saved Products", style = MaterialTheme.typography.titleLarge, color = TextBlack)
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WeGlowLoadingView(message = "Loading your saved products…")
            }
            errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WeGlowErrorView(message = errorMessage, onRetry = onRetry)
            }
            products.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WeGlowEmptyState(
                    title = "No saved products yet",
                    message = "Tap the heart on a product in Discover to save it here.",
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(products, key = { it.catalogNo ?: it.id }) { product ->
                    SavedProductCard(
                        product = product,
                        isRemoving = product.catalogNo in pendingProductNumbers,
                        isAddingToCart = product.catalogNo in cartPendingProductNumbers,
                        onRemove = { onRemove(product) },
                        onAddToCart = { onAddToCart(product) },
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun SavedProductCard(
    product: Product,
    isRemoving: Boolean,
    isAddingToCart: Boolean,
    onRemove: () -> Unit,
    onAddToCart: () -> Unit,
) {
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
            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            product.brandName?.let { brand ->
                Text(brand, style = MaterialTheme.typography.labelSmall, color = SoftGray)
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                product.name,
                style = MaterialTheme.typography.titleSmall,
                color = TextBlack,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(product.priceLabel, style = MaterialTheme.typography.bodyMedium, color = TextBlack)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAddToCart, enabled = !isAddingToCart && product.catalogNo != null) {
                    if (isAddingToCart) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = DarkGreen)
                    } else {
                        Text("Add to Cart", style = MaterialTheme.typography.labelMedium, color = DarkGreen)
                    }
                }
            }
        }
        IconButton(onClick = onRemove, enabled = !isRemoving) {
            if (isRemoving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = LogoutRed)
            } else {
                Icon(Icons.Default.Delete, contentDescription = "Remove from saved products", tint = LogoutRed)
            }
        }
    }
}
