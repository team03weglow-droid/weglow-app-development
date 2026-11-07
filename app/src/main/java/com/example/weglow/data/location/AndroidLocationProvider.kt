package com.example.weglow.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.telephony.TelephonyManager
import androidx.core.os.ConfigurationCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext
    private val countryCities by lazy {
        CountryCityCatalog(appContext.assets.open("weather/country_cities.json").bufferedReader().use { it.readText() })
    }
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
    private val locationManager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission") // The caller only invokes this after runtime permission is granted.
    override suspend fun getCurrentLocation(): Result<EnvironmentLocation> = runCatching {
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
            throw LocationServicesDisabledException()
        }
        val cancellation = CancellationTokenSource()
        val current = try {
            withTimeoutOrNull(12_000L) {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token).await()
            }
        } finally {
            cancellation.cancel()
        }
        val location = current ?: withTimeoutOrNull(2_000L) {
            client.lastLocation.await()?.takeIf { it.isRecentFallback() }
        }
        requireNotNull(location) { "Could not get your location. Check that Location is on, then try again near a window or outdoors." }
        EnvironmentLocation(location.latitude, location.longitude)
    }

    private fun Location.isRecentFallback(): Boolean =
        time > 0L && System.currentTimeMillis() - time in 0..MAX_FALLBACK_LOCATION_AGE_MS

    override suspend fun getCountryMainCity(): Result<EnvironmentLocation> = withContext(Dispatchers.IO) {
        runCatching {
            val telephony = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val countries = listOf(
                runCatching { telephony?.networkCountryIso }.getOrNull().orEmpty(),
                runCatching { telephony?.simCountryIso }.getOrNull().orEmpty(),
                ConfigurationCompat.getLocales(appContext.resources.configuration)[0]?.country.orEmpty(),
            )
            requireNotNull(countryCities.find(countries)) {
                "Could not identify your country. Allow location access or set your phone's region, then retry."
            }
        }
    }

    private companion object {
        // A fallback location older than ten minutes can represent a different area after travel.
        const val MAX_FALLBACK_LOCATION_AGE_MS = 10 * 60 * 1000L
    }
}
