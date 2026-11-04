package com.example.weglow.data.location

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/** Bundled public city coordinates; never substitutes a different country's city. */
class CountryCityCatalog(json: String) {
    private val cities = Json.parseToJsonElement(json).jsonArray.associate { item ->
        val row = item.jsonObject
        fun value(key: String) = row.getValue(key).jsonPrimitive.content
        value("countryCode") to EnvironmentLocation(
            value("latitude").toDouble(), value("longitude").toDouble(),
            "${value("city")}, ${value("countryName")}", isCountryFallback = true,
        )
    }

    fun find(countryCodes: List<String>): EnvironmentLocation? = countryCodes
        .firstNotNullOfOrNull { cities[it.trim().uppercase(Locale.ROOT)] }
}
