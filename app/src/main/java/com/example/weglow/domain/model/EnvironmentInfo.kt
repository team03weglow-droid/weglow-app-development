package com.example.weglow.domain.model

/** A current, non-persisted environmental reading for the device's approximate area. */
data class EnvironmentInfo(
    val latitude: Double,
    val longitude: Double,
    val locationName: String?,
    val uvIndex: Double,
    val uvCategory: String,
    val humidity: Int,
    /** WeatherAPI's US EPA index (1-6), never the UK DEFRA index. */
    val airQualityIndex: Int?,
    val airQualityLabel: String,
    val pm25: Double?,
    val pm10: Double?,
    val lastUpdated: String?,
    val uvDailyHistory: List<UvDailyReading> = emptyList(),
)

/** A real daily UV value returned by WeatherAPI, used only for the Home card trend graph. */
data class UvDailyReading(val date: String, val uvIndex: Double)

fun uvCategoryFor(value: Double): String = when {
    value <= 2.0 -> "Low"
    value <= 5.0 -> "Moderate"
    value <= 7.0 -> "High"
    value <= 10.0 -> "Very High"
    else -> "Extreme"
}

/** Mapping defined by WeatherAPI's `us-epa-index` field (US EPA scale 1-6). */
fun usEpaAirQualityLabel(index: Int?): String = when (index) {
    1 -> "Good"
    2 -> "Moderate"
    3 -> "Unhealthy for sensitive groups"
    4 -> "Unhealthy"
    5 -> "Very unhealthy"
    6 -> "Hazardous"
    else -> "Unavailable"
}
