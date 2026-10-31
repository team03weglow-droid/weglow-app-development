package com.example.weglow.core.image

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class BoundedInputTest {
    @Test fun acceptsExactLimitAndRejectsUnknownLengthOversizeInput() {
        assertArrayEquals(ByteArray(1024), readBounded(ByteArrayInputStream(ByteArray(1024)), 1024))
        assertNull(readBounded(ByteArrayInputStream(ByteArray(1025)), 1024))
        assertArrayEquals(ByteArray(0), readBounded(ByteArrayInputStream(ByteArray(0)), 1024))
    }
}
