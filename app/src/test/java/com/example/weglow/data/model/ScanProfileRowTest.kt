package com.example.weglow.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanProfileRowTest {
    @Test fun databaseCounts_mapToModelLabelsAndNullCountsAreInactive() {
        val scan = ScanProfileRow(darkSpots = 10, openPores = null, blackHeads = 2).toSkinScanConcerns()

        assertEquals(10, scan.active["Dark spots"])
        assertEquals(2, scan.active["black heads"])
        assertEquals(false, scan.active.containsKey("open pores"))
    }
}
