package com.example.weglow.feature.progress

import org.junit.Assert.*
import org.junit.Test

class DemoProgressTest {
    @Test fun previewUsesActualFormulaAndIncludesVisibleWorsening() {
        val intervals = DemoProgress.history().intervals
        assertEquals(listOf(40.0, 45.0, 60.0, 65.0), intervals.map { it.score })
        assertEquals(-25.0, intervals[1].signedImprovement!!, 0.001)
        assertEquals(0.0, intervals[1].scanPoints!!, 0.001)
        val latest = intervals.last()
        assertEquals(50, latest.previous.concerns)
        assertEquals(25, latest.current.concerns)
        assertEquals(80.0, latest.routinePercentage!!, 0.001)
        assertEquals(25.0, latest.scanPoints!!, 0.001)
        assertEquals(40.0, latest.routinePoints!!, 0.001)
    }
}
