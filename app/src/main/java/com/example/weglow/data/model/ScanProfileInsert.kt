package com.example.weglow.data.model

import com.example.weglow.domain.model.AcneScanResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScanProfileInsert(
    @SerialName("Date_time") val dateTime: String,
    @SerialName("dark_spots") val darkSpots: Int,
    @SerialName("open_pores") val openPores: Int,
    @SerialName("black_heads") val blackHeads: Int,
    @SerialName("acne_scars") val acneScars: Int,
    val papules: Int,
    val whiteheads: Int,
    val nodules: Int,
    val cysts: Int,
    val pustules: Int,
    val freckles: Int,
    @SerialName("user_prof_id") val userProfileId: String,
)

fun AcneScanResult.toScanProfileInsert(userProfileId: String, dateTime: String): ScanProfileInsert {
    val counts = detections.groupingBy { it.label.trim().lowercase() }.eachCount()
    return ScanProfileInsert(
        dateTime = dateTime,
        darkSpots = counts["dark spots"] ?: 0,
        openPores = counts["open pores"] ?: 0,
        blackHeads = counts["black heads"] ?: 0,
        acneScars = counts["acne scars"] ?: 0,
        papules = counts["papules"] ?: 0,
        whiteheads = counts["whiteheads"] ?: 0,
        nodules = counts["nodules"] ?: 0,
        cysts = counts["cysts"] ?: 0,
        pustules = counts["pustules"] ?: 0,
        freckles = counts["freckles"] ?: 0,
        userProfileId = userProfileId,
    )
}
