package com.example.weglow.data.repository

import com.example.weglow.data.model.CartItemInsert
import com.example.weglow.data.model.CartQuantityRow
import com.example.weglow.domain.model.CartItem
import com.example.weglow.domain.model.Product
import com.example.weglow.domain.repository.CartRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Backs [CartRepository] with `public.cart_items`, a table this class does not create or
 * migrate - it was provisioned and RLS-protected manually (see the confirmed schema in the
 * Saved Products/Cart investigation report), including its own `updated_at` trigger. Every row
 * is owned by `profile_id` (the same `auth.uid()` identity [AuthRepository] already resolves)
 * and related to the catalog through `product_no` (`products."No"`), never the flexible
 * [Product.id].
 */
class SupabaseCartRepository(
    private val client: SupabaseClient,
) : CartRepository {

    override suspend fun getCartItems(userId: String): Result<List<CartItem>> = runCatching {
        client.postgrest[CART_ITEMS_TABLE]
            .select(columns = Columns.raw("product_no, quantity, created_at, products(*)")) {
                filter { eq("profile_id", userId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<JsonObject>()
            .mapNotNull { row -> row.toCartItemOrNull() }
    }

    /**
     * Adds one unit of [productNo], creating a new quantity-1 line or incrementing an existing
     * one, safely under concurrent calls for the same (user, product) pair.
     *
     * PostgREST's declarative upsert can only *replace* a conflicting row's columns with fixed
     * values (`ON CONFLICT ... DO UPDATE SET quantity = 1`), which would silently reset an
     * existing quantity back to 1 instead of incrementing it - so a blind upsert is wrong here,
     * and this project has no custom database function to do a true `quantity = quantity + 1`
     * atomically in one round trip. Instead this uses two Postgres-guaranteed atomic primitives,
     * retried as an optimistic-concurrency loop that can never double-count or lose an add:
     *
     * 1. No existing line: `INSERT ... ON CONFLICT (profile_id, product_no) DO NOTHING`
     *    (`upsert(ignoreDuplicates = true)`). This is one atomic statement - if a concurrent
     *    call wins the race and inserts first, this one is guaranteed to affect zero rows
     *    (PostgREST returns an empty result), never a duplicate row and never an exception.
     * 2. An existing line: `UPDATE ... SET quantity = quantity + 1 WHERE ... AND quantity =
     *    <value just read>` - a compare-and-swap. Postgres row-level locking makes this single
     *    UPDATE atomic; if a concurrent writer changed the quantity between our read and this
     *    write, the WHERE clause matches zero rows (returned as an empty result) instead of
     *    silently overwriting the other writer's increment.
     *
     * Either branch retries (re-reading current state) whenever it affects zero rows, up to
     * [MAX_ATTEMPTS] times, so the loser of a race simply becomes the next attempt rather than
     * losing the add.
     */
    override suspend fun addToCart(userId: String, productNo: Int): Result<Unit> = runCatching {
        val succeeded = (1..MAX_ATTEMPTS).any { tryAddOrIncrementOnce(userId, productNo) }
        check(succeeded) { "Your cart is busy right now. Please try again." }
    }

    override suspend fun updateQuantity(userId: String, productNo: Int, quantity: Int): Result<Unit> = runCatching {
        require(quantity > 0) { "Quantity must be greater than zero." }
        client.postgrest[CART_ITEMS_TABLE].update(
            update = { set("quantity", quantity) },
        ) {
            filter {
                eq("profile_id", userId)
                eq("product_no", productNo)
            }
        }
        Unit
    }

    override suspend fun removeFromCart(userId: String, productNo: Int): Result<Unit> = runCatching {
        client.postgrest[CART_ITEMS_TABLE].delete {
            filter {
                eq("profile_id", userId)
                eq("product_no", productNo)
            }
        }
        Unit
    }

    private suspend fun tryAddOrIncrementOnce(userId: String, productNo: Int): Boolean {
        val existingQuantity = currentQuantity(userId, productNo)
        return if (existingQuantity == null) {
            insertNewLine(userId, productNo)
        } else {
            incrementLine(userId, productNo, existingQuantity)
        }
    }

    private suspend fun currentQuantity(userId: String, productNo: Int): Int? =
        client.postgrest[CART_ITEMS_TABLE]
            .select(columns = Columns.raw("quantity")) {
                filter {
                    eq("profile_id", userId)
                    eq("product_no", productNo)
                }
            }
            .decodeList<CartQuantityRow>()
            .firstOrNull()
            ?.quantity

    private suspend fun insertNewLine(userId: String, productNo: Int): Boolean {
        val inserted = client.postgrest[CART_ITEMS_TABLE].upsert(
            CartItemInsert(profileId = userId, productNo = productNo, quantity = 1),
        ) {
            onConflict = "profile_id,product_no"
            ignoreDuplicates = true
        }.decodeList<JsonObject>()
        return inserted.isNotEmpty()
    }

    private suspend fun incrementLine(userId: String, productNo: Int, previousQuantity: Int): Boolean {
        val updated = client.postgrest[CART_ITEMS_TABLE].update(
            update = { set("quantity", previousQuantity + 1) },
        ) {
            filter {
                eq("profile_id", userId)
                eq("product_no", productNo)
                eq("quantity", previousQuantity)
            }
        }.decodeList<JsonObject>()
        return updated.isNotEmpty()
    }

    private companion object {
        const val CART_ITEMS_TABLE = "cart_items"
        const val MAX_ATTEMPTS = 5
    }
}

internal fun JsonObject.toCartItemOrNull(): CartItem? {
    val product = (this["products"] as? JsonObject)?.toProductOrNull() ?: return null
    val quantity = (this["quantity"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: return null
    return CartItem(product = product, quantity = quantity)
}
