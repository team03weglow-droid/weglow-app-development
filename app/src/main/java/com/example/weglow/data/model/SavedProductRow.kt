package com.example.weglow.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Insert payload for `public.saved_products`. */
@Serializable
data class SavedProductInsert(
    @SerialName("profile_id") val profileId: String,
    @SerialName("product_no") val productNo: Int,
)
