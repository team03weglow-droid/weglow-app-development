package com.example.weglow.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverPriceFilterTest {
    @Test
    fun noRangeSet_includesEverything() {
        assertTrue(matchesPriceRange(priceLkr = 2500.0, minimumPrice = null, maximumPrice = null))
        assertTrue(matchesPriceRange(priceLkr = null, minimumPrice = null, maximumPrice = null))
    }

    @Test
    fun minOnly_includesPricesAtOrAboveMinimum() {
        assertTrue(matchesPriceRange(priceLkr = 2500.0, minimumPrice = 1000.0, maximumPrice = null))
        assertFalse(matchesPriceRange(priceLkr = 500.0, minimumPrice = 1000.0, maximumPrice = null))
    }

    @Test
    fun maxOnly_includesPricesAtOrBelowMaximum() {
        assertTrue(matchesPriceRange(priceLkr = 2500.0, minimumPrice = null, maximumPrice = 5000.0))
        assertFalse(matchesPriceRange(priceLkr = 6000.0, minimumPrice = null, maximumPrice = 5000.0))
    }

    @Test
    fun minAndMax_includesProductWithinRange() {
        // Product price = 2500, min = 1000, max = 5000 -> INCLUDED.
        assertTrue(matchesPriceRange(priceLkr = 2500.0, minimumPrice = 1000.0, maximumPrice = 5000.0))
    }

    @Test
    fun minAndMax_excludesProductOutsideRange() {
        assertFalse(matchesPriceRange(priceLkr = 500.0, minimumPrice = 1000.0, maximumPrice = 5000.0))
        assertFalse(matchesPriceRange(priceLkr = 6000.0, minimumPrice = 1000.0, maximumPrice = 5000.0))
    }

    @Test
    fun exactLowerBoundary_isIncluded() {
        assertTrue(matchesPriceRange(priceLkr = 1000.0, minimumPrice = 1000.0, maximumPrice = 5000.0))
    }

    @Test
    fun exactUpperBoundary_isIncluded() {
        assertTrue(matchesPriceRange(priceLkr = 5000.0, minimumPrice = 1000.0, maximumPrice = 5000.0))
    }

    @Test
    fun decimalPrice_isComparedCorrectly() {
        assertTrue(matchesPriceRange(priceLkr = 2499.50, minimumPrice = 2000.0, maximumPrice = 2500.0))
        assertFalse(matchesPriceRange(priceLkr = 2500.01, minimumPrice = 2000.0, maximumPrice = 2500.0))
    }

    @Test
    fun blankMinAndMax_treatedAsNoFilter() {
        val blankMin: Double? = null
        val blankMax: Double? = null
        assertTrue(matchesPriceRange(priceLkr = 2500.0, minimumPrice = blankMin, maximumPrice = blankMax))
    }

    @Test
    fun nullProductPrice_excludedWhenRangeIsActive() {
        assertFalse(matchesPriceRange(priceLkr = null, minimumPrice = 1000.0, maximumPrice = 5000.0))
        assertFalse(matchesPriceRange(priceLkr = null, minimumPrice = 1000.0, maximumPrice = null))
        assertFalse(matchesPriceRange(priceLkr = null, minimumPrice = null, maximumPrice = 5000.0))
    }
}
