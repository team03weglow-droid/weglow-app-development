package com.example.weglow.domain.model

/**
 * One line of the authenticated user's cart: a catalog [product] (full, current data - never a
 * price/name snapshot) paired with how many of it is in the cart. The live [Product.priceLkr]
 * is always what subtotals are computed from, so a catalog price change is reflected immediately
 * rather than showing a stale add-time price.
 */
data class CartItem(
    val product: Product,
    val quantity: Int,
)
