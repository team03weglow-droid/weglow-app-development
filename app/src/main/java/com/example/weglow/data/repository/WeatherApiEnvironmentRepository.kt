package com.example.weglow.data.repository

import android.location.Location
import com.example.weglow.core.config.AppConfig
import com.example.weglow.domain.model.EnvironmentInfo
import com.example.weglow.domain.model.UvDailyReading
import com.example.weglow.domain.model.usEpaAirQualityLabel
import com.example.weglow.domain.model.uvCategoryFor
import com.example.weglow.domain.repository.EnvironmentRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

class WeatherApiEnvironmentRepository(
    private val accessTokenProvider: () -> String?,
    private val supabaseUrl: String = AppConfig.supabaseUrl,
    private val supabasePublishableKey: String = AppConfig.supabasePublishableKey,
    private val client: HttpClient = HttpClient(Android) {
        install(HttpTimeout) { requestTimeoutMillis = NETWORK_TIMEOUT_MS }
    },
) : EnvironmentRepository {
    private var cached: CachedEnvironment? = null

    override suspend fun getEnvironmentForLocation(
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean,
    ): Result<EnvironmentInfo> = runCatching {
        require(supabaseUrl.isNotBlank() && supabasePublishableKey.isNotBlank()) {
            "Weather service is not configured. Add the Supabase configuration to local.properties."
        }

        val fresh = cached?.takeIf {
            !forceRefresh && it.isFreshFor(latitude, longitude)
        }

        fresh?.environment ?: fetch(latitude, longitude).also {
            cached = CachedEnvironment(it, System.currentTimeMillis())
        }
    }

    override suspend fun getLatestEnvironment(): EnvironmentInfo? = cached?.environment

    private suspend fun fetch(
        latitude: Double,
        longitude: Double,
    ): EnvironmentInfo {
        val response = client.get(weatherFunctionUrl()) {
            addAuthenticationHeaders()
            parameter("lat", latitude)
            parameter("lon", longitude)
        }

        check(response.status.value in 200..299) {
            "Weather service is unavailable (${response.status.value})."
        }

        val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val current = root.requiredObject("current")
        val airQuality = current["air_quality"]?.jsonObject
        val uv = current.requiredDouble("uv")
        val aqi = airQuality
            ?.get("us-epa-index")
            ?.jsonPrimitive
            ?.content
            ?.toDoubleOrNull()
            ?.toInt()

        val locationName = root["location"]
            ?.jsonObject
            ?.get("name")
            ?.jsonPrimitive
            ?.content

        val currentDate = root["location"]
            ?.jsonObject
            ?.get("localtime")
            ?.jsonPrimitive
            ?.content
            ?.take(10)
            ?: LocalDate.now().toString()

        // A 31-day trend needs WeatherAPI Pro+ history access.
        // Failure must not hide today's reading.
        val history = fetchUvHistory(
            latitude,
            longitude,
            currentDate,
        ).getOrDefault(emptyList())

        return EnvironmentInfo(
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            uvIndex = uv,
            uvCategory = uvCategoryFor(uv),
            humidity = current.requiredDouble("humidity").toInt(),
            airQualityIndex = aqi,
            airQualityLabel = usEpaAirQualityLabel(aqi),
            pm25 = airQuality
                ?.get("pm2_5")
                ?.jsonPrimitive
                ?.content
                ?.toDoubleOrNull(),
            pm10 = airQuality
                ?.get("pm10")
                ?.jsonPrimitive
                ?.content
                ?.toDoubleOrNull(),
            lastUpdated = current["last_updated"]
                ?.jsonPrimitive
                ?.content,
            uvDailyHistory = (history + UvDailyReading(currentDate, uv))
                .distinctBy { it.date },
        )
    }

    private suspend fun fetchUvHistory(
        latitude: Double,
        longitude: Double,
        currentDate: String,
    ): Result<List<UvDailyReading>> = runCatching {
        val today = runCatching {
            LocalDate.parse(currentDate)
        }.getOrDefault(LocalDate.now())

        val response = client.get(OPEN_METEO_FORECAST_URL) {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("daily", "uv_index_max")
            parameter(
                "start_date",
                today.minusDays(HISTORY_DAY_COUNT).toString(),
            )
            parameter(
                "end_date",
                today.minusDays(1).toString(),
            )
            parameter("timezone", "auto")
        }

        check(response.status.value in 200..299) {
            "UV history is unavailable."
        }

        val daily = Json
            .parseToJsonElement(response.bodyAsText())
            .jsonObject
            .requiredObject("daily")

        val dates = daily.requiredArray("time")
        val uvValues = daily.requiredArray("uv_index_max")

        check(dates.size == uvValues.size) {
            "Malformed UV history response."
        }

        dates.indices.map { index ->
            UvDailyReading(
                date = dates[index].jsonPrimitive.content,
                uvIndex = uvValues[index]
                    .jsonPrimitive
                    .content
                    .toDoubleOrNull()
                    ?: error("Missing UV history value"),
            )
        }
    }

    private fun weatherFunctionUrl(): String =
        "${supabaseUrl.trimEnd('/')}/functions/v1/smart-endpoint"

    private fun io.ktor.client.request.HttpRequestBuilder.addAuthenticationHeaders() {
        val accessToken = accessTokenProvider()

        require(!accessToken.isNullOrBlank()) {
            "Sign in before requesting weather."
        }

        header(
            HttpHeaders.Authorization,
            "Bearer $accessToken",
        )
        header(
            "apikey",
            supabasePublishableKey,
        )
    }

    private fun kotlinx.serialization.json.JsonObject.requiredObject(
        name: String,
    ) = get(name)?.jsonObject
        ?: error("Malformed weather response: missing $name")

    private fun kotlinx.serialization.json.JsonObject.requiredDouble(
        name: String,
    ): Double =
        get(name)
            ?.jsonPrimitive
            ?.content
            ?.toDoubleOrNull()
            ?: error("Malformed weather response: missing $name")

    private fun kotlinx.serialization.json.JsonObject.requiredArray(
        name: String,
    ) = get(name)?.jsonArray
        ?: error("Malformed weather response: missing $name")

    private data class CachedEnvironment(
        val environment: EnvironmentInfo,
        val fetchedAtMs: Long,
    ) {
        fun isFreshFor(
            latitude: Double,
            longitude: Double,
        ): Boolean {
            val distance = FloatArray(1)

            Location.distanceBetween(
                environment.latitude,
                environment.longitude,
                latitude,
                longitude,
                distance,
            )

            return System.currentTimeMillis() - fetchedAtMs <=
                    ENVIRONMENT_CACHE_FRESHNESS_MS &&
                    distance[0] < ENVIRONMENT_LOCATION_REFRESH_DISTANCE_METERS
        }
    }

    private companion object {
        const val OPEN_METEO_FORECAST_URL =
            "https://api.open-meteo.com/v1/forecast"

        const val NETWORK_TIMEOUT_MS = 15_000L

        const val ENVIRONMENT_CACHE_FRESHNESS_MS =
            20 * 60 * 1000L

        const val ENVIRONMENT_LOCATION_REFRESH_DISTANCE_METERS =
            7_000f

        const val HISTORY_DAY_COUNT = 30L
    }
}
