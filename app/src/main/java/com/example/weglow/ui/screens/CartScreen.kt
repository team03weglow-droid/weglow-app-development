package com.example.weglow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.weglow.domain.model.CartItem
import com.example.weglow.ui.components.WeGlowEmptyState
import com.example.weglow.ui.components.WeGlowErrorView
import com.example.weglow.ui.components.WeGlowLoadingView
import com.example.weglow.ui.components.WeGlowPlannedFeature
import com.example.weglow.ui.components.WeGlowProductImage
import com.example.weglow.ui.theme.*
import java.util.Locale

/**
 * The authenticated user's cart. Every mutation goes through
 * [com.example.weglow.feature.cart.CartViewModel] - the same instance Discover's "Add to Bag"
 * and Saved Products' "Add to Cart" use, so this screen never runs separate cart logic.
 *
 * There is no checkout/payment flow anywhere in this app yet, so this screen honestly stops at
 * the subtotal: "Checkout" is shown as a planned feature rather than pretending an order or
 * payment completed.
 */
@Composable
fun CartScreen(
    isLoading: Boolean,
    items: List<CartItem>,
    subtotalLkr: Double,
    errorMessage: String?,
    pendingProductNumbers: Set<Int>,
    onBack: () -> Unit,
    onIncrement: (CartItem) -> Unit,
    onDecrement: (CartItem) -> Unit,
    onRemove: (CartItem) -> Unit,
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
            Text("Cart", style = MaterialTheme.typography.titleLarge, color = TextBlack)
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WeGlowLoadingView(message = "Loading your cart…")
            }
            errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WeGlowErrorView(message = errorMessage, onRetry = onRetry)
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WeGlowEmptyState(
                    title = "Your cart is empty",
                    message = "Add products from Discover or your saved products.",
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(items, key = { it.product.catalogNo ?: it.product.id }) { item ->
                        CartLineCard(
                            item = item,
                            isUpdating = item.product.catalogNo in pendingProductNumbers,
                            onIncrement = { onIncrement(item) },
                            onDecrement = { onDecrement(item) },
                            onRemove = { onRemove(item) },
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(CardWhite)
                        .padding(20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Subtotal", style = MaterialTheme.typography.titleMedium, color = TextBlack)
                        Text(subtotalLkr.toLkrLabel(), style = MaterialTheme.typography.titleMedium, color = TextBlack)
                    }
                    Spacer(Modifier.height(14.dp))
                    WeGlowPlannedFeature("Checkout")
                }
            }
        }
    }
}

@Composable
private fun CartLineCard(
    item: CartItem,
    isUpdating: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
) {
    val product = item.product
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
            Text(product.priceLabel, style = MaterialTheme.typography.bodyMedium, color = SoftGray)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(PageBackground),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDecrement, enabled = !isUpdating, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease quantity", tint = DarkGreen)
                    }
                    if (isUpdating) {
                        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = DarkGreen)
                        }
                    } else {
                        Text(
                            "${item.quantity}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextBlack,
                            modifier = Modifier.width(28.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                    IconButton(onClick = onIncrement, enabled = !isUpdating, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Increase quantity", tint = DarkGreen)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onRemove, enabled = !isUpdating, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove from cart", tint = LogoutRed)
                }
            }
        }
    }
}

private fun Double.toLkrLabel(): String {
    val formatted = if (this % 1.0 == 0.0) this.toLong().toString() else "%.2f".format(Locale.US, this)
    return "LKR $formatted"
}
