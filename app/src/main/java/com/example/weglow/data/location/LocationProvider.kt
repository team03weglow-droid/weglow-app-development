package com.example.weglow.data.location

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class EnvironmentLocation(
    val latitude: Double,
    val longitude: Double,
    val displayName: String? = null,
    val isCountryFallback: Boolean = false,
)

class LocationServicesDisabledException : IllegalStateException(
    "Device Location is off. Turn it on to load local weather and UV readings.",
)

interface LocationProvider {
    /** Requests one foreground fix; this never starts continuous location tracking. */
    suspend fun getCurrentLocation(): Result<EnvironmentLocation>
    suspend fun getCountryMainCity(): Result<EnvironmentLocation>
}

/** Permission denial and failed fixes still allow a labeled country default. */
suspend fun LocationProvider.resolveWeatherLocation(hasPermission: Boolean): Result<EnvironmentLocation> {
    if (hasPermission) {
        val current = getCurrentLocation()
        currentCoroutineContext().ensureActive()
        if (current.isSuccess) return current
    }
    return getCountryMainCity()
}
