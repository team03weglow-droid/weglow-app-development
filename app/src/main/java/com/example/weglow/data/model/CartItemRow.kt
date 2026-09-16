package com.example.weglow.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Insert payload for a brand-new `public.cart_items` row (always created at quantity 1). */
@Serializable
data class CartItemInsert(
    @SerialName("profile_id") val profileId: String,
    @SerialName("product_no") val productNo: Int,
    val quantity: Int = 1,
)

/** Narrow read shape used only to inspect an existing line's current quantity. */
@Serializable
data class CartQuantityRow(
    val quantity: Int,
)
