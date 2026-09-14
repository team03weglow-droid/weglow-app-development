package com.example.weglow.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

class AndroidLocationProvider(context: Context) : LocationProvider {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission") // The caller only invokes this after runtime permission is granted.
    override suspend fun getCurrentLocation(): Result<EnvironmentLocation> = runCatching {
        val current = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        val location = current ?: client.lastLocation.await()?.takeIf { it.isRecentFallback() }
        requireNotNull(location) { "Location is unavailable. Turn on location services and try again." }
        EnvironmentLocation(location.latitude, location.longitude)
    }

    private fun Location.isRecentFallback(): Boolean =
        time > 0L && System.currentTimeMillis() - time <= MAX_FALLBACK_LOCATION_AGE_MS

    private companion object {
        // A fallback location older than ten minutes can represent a different area after travel.
        const val MAX_FALLBACK_LOCATION_AGE_MS = 10 * 60 * 1000L
    }
}
