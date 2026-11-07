package com.example.weglow.data.model

import com.example.weglow.data.repository.ProgressScanRow
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ProgressScanRowTest {
    private val row = """{"progress_scan_id":"a","Date_time":"2026-11-01T00:00:00+00:00","detection_settings":"model-v1","dark_spots":25,"open_pores":0,"black_heads":0,"acne_scars":0,"papules":0,"whiteheads":0,"nodules":0,"cysts":0,"pustules":0,"freckles":0}"""
    @Test fun actualCountsAndTimestampAreReadWithoutUsingConfidence() {
        val scan = Json.decodeFromString<ProgressScanRow>(row).toDomain()
        assertEquals(25, scan.concerns); assertTrue(scan.valid)
    }
    @Test fun nullNegativeAndMissingCountsRemainInvalid_notZero() {
        listOf(row.replace("\"dark_spots\":25", "\"dark_spots\":null"), row.replace("\"dark_spots\":25", "\"dark_spots\":-1"),
            row.replace("\"dark_spots\":25,", ""), row.replace("2026-11-01T00:00:00+00:00", "bad-date"),
            row.replace("\"detection_settings\":\"model-v1\",", "")).forEach {
            assertFalse(Json.decodeFromString<ProgressScanRow>(it).toDomain().valid)
        }
    }
}
