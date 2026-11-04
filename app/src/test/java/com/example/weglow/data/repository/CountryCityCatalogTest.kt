package com.example.weglow.data.repository

import com.example.weglow.data.location.CountryCityCatalog
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CountryCityCatalogTest {
    private val catalog = CountryCityCatalog(File("src/main/assets/weather/country_cities.json").readText())

    @Test fun sriLankaUsesColomboAndLabelsItAsFallback() {
        val city = requireNotNull(catalog.find(listOf("lk")))
        assertEquals("Colombo, Sri Lanka", city.displayName)
        assertEquals(6.9271, city.latitude, 0.0001)
        assertTrue(city.isCountryFallback)
    }

    @Test fun networkCountryWinsOverSimAndRegion() {
        assertEquals("Paris, France", catalog.find(listOf("FR", "LK", "GB"))?.displayName)
        assertEquals("London, United Kingdom", catalog.find(listOf("", "gb", "LK"))?.displayName)
    }

    @Test fun unknownCountryDoesNotInventCoordinates() {
        assertNull(catalog.find(listOf("", "ZZ")))
    }
}
