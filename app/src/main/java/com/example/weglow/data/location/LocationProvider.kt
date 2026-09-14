package com.example.weglow.data.location

data class EnvironmentLocation(val latitude: Double, val longitude: Double)

interface LocationProvider {
    /** Requests one foreground fix; this never starts continuous location tracking. */
    suspend fun getCurrentLocation(): Result<EnvironmentLocation>
}
