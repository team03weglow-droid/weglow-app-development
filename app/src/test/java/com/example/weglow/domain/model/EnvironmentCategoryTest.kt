package com.example.weglow.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EnvironmentCategoryTest {
    @Test fun uvCategoriesUsePublishedBoundaries() {
        assertEquals("Low", uvCategoryFor(2.0))
        assertEquals("Moderate", uvCategoryFor(3.0))
        assertEquals("High", uvCategoryFor(6.0))
        assertEquals("Very High", uvCategoryFor(8.0))
        assertEquals("Extreme", uvCategoryFor(11.0))
    }

    @Test fun weatherApiUsEpaIndexUsesUsEpaLabels() {
        assertEquals("Good", usEpaAirQualityLabel(1))
        assertEquals("Unhealthy for sensitive groups", usEpaAirQualityLabel(3))
        assertEquals("Hazardous", usEpaAirQualityLabel(6))
    }
}
