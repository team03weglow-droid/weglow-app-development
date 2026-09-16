package com.example.weglow.domain.repository

import com.example.weglow.domain.model.CartItem

/**
 * Persists the authenticated user's cart. Every method takes the caller-resolved [userId] (the
 * same identity used everywhere else in the app - see [AuthRepository.currentUserId]) and a
 * [productNo], the catalog's real `products."No"` relational key
 * ([com.example.weglow.domain.model.Product.catalogNo]), never the flexible display id.
 */
interface CartRepository {
    /** The authenticated user's cart lines, each resolved to its current full catalog product. */
    suspend fun getCartItems(userId: String): Result<List<CartItem>>

    /**
     * Adds one unit of [productNo] to [userId]'s cart: creates a new line at quantity 1 if the
     * product is not yet in the cart, or atomically increments the existing line's quantity by
     * one otherwise. Race-safe against concurrent calls for the same (user, product) pair - see
     * the Supabase implementation of this interface for how.
     */
    suspend fun addToCart(userId: String, productNo: Int): Result<Unit>

    /** Sets [productNo]'s line to exactly [quantity] (must be > 0; callers enforce this). */
    suspend fun updateQuantity(userId: String, productNo: Int, quantity: Int): Result<Unit>

    /** Removes [productNo] from [userId]'s cart entirely, if present. */
    suspend fun removeFromCart(userId: String, productNo: Int): Result<Unit>
}
