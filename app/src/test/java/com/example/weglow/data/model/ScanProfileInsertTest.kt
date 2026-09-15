package com.example.weglow.data.model

import com.example.weglow.domain.model.AcneDetection
import com.example.weglow.domain.model.AcneScanResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanProfileInsertTest {
    @Test fun supportedModelLabels_areCountedAndAbsentLabelsAreZero() {
        val result = AcneScanResult(
            detections = listOf(
                detection("Dark spots"), detection("Dark spots"), detection("Dark spots"), detection("Dark spots"),
                detection("papules"), detection("papules"), detection("pustules"), detection("Acne scars"),
            ),
            imageWidth = 640,
            imageHeight = 480,
            modelVersion = "test",
            confidenceThreshold = 0.05f,
        )

        val insert = result.toScanProfileInsert("profile-id", "2026-09-14T12:00:00Z")

        assertEquals("profile-id", insert.userProfileId)
        assertEquals("2026-09-14T12:00:00Z", insert.dateTime)
        assertEquals(4, insert.darkSpots)
        assertEquals(1, insert.acneScars)
        assertEquals(2, insert.papules)
        assertEquals(1, insert.pustules)
        assertEquals(0, insert.openPores)
        assertEquals(0, insert.blackHeads)
        assertEquals(0, insert.whiteheads)
        assertEquals(0, insert.nodules)
        assertEquals(0, insert.cysts)
        assertEquals(0, insert.freckles)
    }

    private fun detection(label: String) = AcneDetection(label, 0.9f, 0f, 0f, 1f, 1f)
}
