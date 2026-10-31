package com.example.weglow.core.image

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Returns null before retaining more than [limit] bytes, including for unknown-length sources. */
internal fun readBounded(input: InputStream, limit: Int): ByteArray? {
    require(limit >= 0)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer, 0, minOf(buffer.size, limit - total + 1))
        if (count < 0) return output.toByteArray()
        total += count
        if (total > limit) return null
        output.write(buffer, 0, count)
    }
}
