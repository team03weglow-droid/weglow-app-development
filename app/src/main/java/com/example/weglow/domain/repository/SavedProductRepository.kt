package com.example.weglow.domain.repository

import com.example.weglow.domain.model.Product

/**
 * Persists which catalog products the authenticated user has saved. Every method takes the
 * caller-resolved [userId] (the same `profiles.id` / `auth.uid()` identity used everywhere
 * else in the app - see [AuthRepository.currentUserId]) and a [productNo], the catalog's real
 * `products."No"` relational key ([com.example.weglow.domain.model.Product.catalogNo]), never
 * the flexible display [com.example.weglow.domain.model.Product.id].
 */
interface SavedProductRepository {
    /** The authenticated user's saved products, each resolved to its current full catalog data. */
    suspend fun getSavedProducts(userId: String): Result<List<Product>>

    /**
     * Saves [productNo] for [userId]. Saving an already-saved product is a safe no-op (the
     * database's `UNIQUE(profile_id, product_no)` constraint, honored via an ignore-duplicates
     * upsert, guarantees this without ever creating a second row).
     */
    suspend fun saveProduct(userId: String, productNo: Int): Result<Unit>

    /** Removes [productNo] from [userId]'s saved products, if present. */
    suspend fun removeSavedProduct(userId: String, productNo: Int): Result<Unit>
}
