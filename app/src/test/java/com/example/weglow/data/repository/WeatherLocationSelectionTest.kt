package com.example.weglow.data.repository

import com.example.weglow.data.location.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WeatherLocationSelectionTest {
    private val current = EnvironmentLocation(48.85, 2.35)
    private val fallback = EnvironmentLocation(6.9271, 79.8612, "Colombo, Sri Lanka", true)

    private class FakeLocation(
        val current: Result<EnvironmentLocation>,
        val fallback: Result<EnvironmentLocation>,
    ) : LocationProvider {
        var currentCalls = 0
        var fallbackCalls = 0
        override suspend fun getCurrentLocation(): Result<EnvironmentLocation> { currentCalls++; return current }
        override suspend fun getCountryMainCity(): Result<EnvironmentLocation> { fallbackCalls++; return fallback }
    }

    @Test fun currentLocationWinsWhenAvailable() = runTest {
        val provider = FakeLocation(Result.success(current), Result.success(fallback))
        assertEquals(current, provider.resolveWeatherLocation(true).getOrThrow())
        assertEquals(0, provider.fallbackCalls)
    }

    @Test fun deniedPermissionNeverReadsDeviceLocation() = runTest {
        val provider = FakeLocation(Result.success(current), Result.success(fallback))
        assertEquals(fallback, provider.resolveWeatherLocation(false).getOrThrow())
        assertEquals(0, provider.currentCalls)
    }

    @Test fun disabledOrTimedOutLocationUsesCountryDefault() = runTest {
        val provider = FakeLocation(Result.failure(LocationServicesDisabledException()), Result.success(fallback))
        assertEquals(fallback, provider.resolveWeatherLocation(true).getOrThrow())
        assertEquals(1, provider.fallbackCalls)
    }

    @Test fun unknownCountryRemainsUnavailable() = runTest {
        val provider = FakeLocation(Result.failure(IllegalStateException("No fix")), Result.failure(IllegalStateException("Unknown country")))
        assertTrue(provider.resolveWeatherLocation(true).isFailure)
    }
}
