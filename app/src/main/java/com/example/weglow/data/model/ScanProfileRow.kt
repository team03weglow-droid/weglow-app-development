package com.example.weglow.data.model

import com.example.weglow.domain.model.SkinScanConcerns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScanProfileRow(
    @SerialName("dark_spots") val darkSpots: Int? = null,
    @SerialName("open_pores") val openPores: Int? = null,
    @SerialName("black_heads") val blackHeads: Int? = null,
    @SerialName("acne_scars") val acneScars: Int? = null,
    val papules: Int? = null,
    val whiteheads: Int? = null,
    val nodules: Int? = null,
    val cysts: Int? = null,
    val pustules: Int? = null,
    val freckles: Int? = null,
) {
    fun toSkinScanConcerns() = SkinScanConcerns(mapOf(
        "Dark spots" to (darkSpots ?: 0),
        "open pores" to (openPores ?: 0),
        "black heads" to (blackHeads ?: 0),
        "Acne scars" to (acneScars ?: 0),
        "papules" to (papules ?: 0),
        "whiteheads" to (whiteheads ?: 0),
        "nodules" to (nodules ?: 0),
        "cysts" to (cysts ?: 0),
        "pustules" to (pustules ?: 0),
        "freckles" to (freckles ?: 0),
    ))
}
