package com.example.weglow.data.repository

import com.example.weglow.data.model.SavedProductInsert
import com.example.weglow.domain.model.Product
import com.example.weglow.domain.repository.SavedProductRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject

/**
 * Backs [SavedProductRepository] with `public.saved_products`, a table this class does not
 * create or migrate - it was provisioned and RLS-protected manually (see the confirmed schema
 * in the Saved Products/Cart investigation report). Every row is owned by `profile_id`, the same
 * `auth.uid()` identity [AuthRepository] already resolves, and related to the catalog through
 * `product_no` (`products."No"`), never the flexible [Product.id].
 */
class SupabaseSavedProductRepository(
    private val client: SupabaseClient,
) : SavedProductRepository {

    override suspend fun getSavedProducts(userId: String): Result<List<Product>> = runCatching {
        client.postgrest[SAVED_PRODUCTS_TABLE]
            .select(columns = Columns.raw("product_no, created_at, products(*)")) {
                filter { eq("profile_id", userId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<JsonObject>()
            // A saved row whose product could not be resolved (never expected - product_no has an
            // ON DELETE CASCADE FK into products - but never fabricated if it somehow happened).
            .mapNotNull { row -> (row["products"] as? JsonObject)?.toProductOrNull() }
    }

    override suspend fun saveProduct(userId: String, productNo: Int): Result<Unit> = runCatching {
        // ON CONFLICT (profile_id, product_no) DO NOTHING: re-saving an already-saved product is
        // a single atomic, race-safe no-op - never a duplicate row, never a thrown constraint error.
        client.postgrest[SAVED_PRODUCTS_TABLE].upsert(
            SavedProductInsert(profileId = userId, productNo = productNo),
        ) {
            onConflict = "profile_id,product_no"
            ignoreDuplicates = true
        }
        Unit
    }

    override suspend fun removeSavedProduct(userId: String, productNo: Int): Result<Unit> = runCatching {
        client.postgrest[SAVED_PRODUCTS_TABLE].delete {
            filter {
                eq("profile_id", userId)
                eq("product_no", productNo)
            }
        }
        Unit
    }

    private companion object {
        const val SAVED_PRODUCTS_TABLE = "saved_products"
    }
}
