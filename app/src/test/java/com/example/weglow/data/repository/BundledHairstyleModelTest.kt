package com.example.weglow.data.repository

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledHairstyleModelTest {
    @Test fun bundledModelMatchesItsManifest() {
        val assets = File(requireNotNull(System.getProperty("weglow.hairstyle.assets")))
        val model = assets.resolve("face_shape.tflite")
        val manifest = assets.resolve("model.json").readText()
        val bytes = model.readBytes()

        assertTrue("Bundled hairstyle model is unexpectedly small", bytes.size > 10_000_000)
        assertEquals("TFL3", bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII))
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertTrue("Model hash does not match model.json", manifest.contains(hash))
        listOf("Heart", "Oblong", "Oval", "Round", "Square").forEach { label ->
            assertTrue("Missing class label $label", manifest.contains("\"$label\""))
        }
    }
}
