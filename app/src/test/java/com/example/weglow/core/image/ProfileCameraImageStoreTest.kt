package com.example.weglow.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Phase 6 follow-up: lifecycle of the throw-away file the system camera writes a
 * profile-picture capture into. Pure `java.io.File` logic — no Android, no camera.
 */
class ProfileCameraImageStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val cacheDir: File get() = tempFolder.root

    @Test
    fun createCaptureFile_livesInsideThePrivateCaptureSubdirectory() {
        val file = ProfileCameraImageStore.createCaptureFile(cacheDir, timestamp = 111L)

        assertEquals(File(cacheDir, ProfileCameraImageStore.DIR_NAME), file.parentFile)
        assertTrue(file.parentFile!!.isDirectory)
        assertTrue(ProfileCameraImageStore.isCaptureFile(file))
    }

    @Test
    fun createCaptureFile_usesAFreshNamePerTimestamp() {
        val a = ProfileCameraImageStore.createCaptureFile(cacheDir, timestamp = 1L)
        val b = ProfileCameraImageStore.createCaptureFile(cacheDir, timestamp = 2L)

        assertNotEquals(a.name, b.name)
    }

    @Test
    fun createCaptureFile_removesLeftoverCapturesFromAnEarlierAttempt() {
        val stale = ProfileCameraImageStore.createCaptureFile(cacheDir, timestamp = 1L)
        stale.writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(stale.exists())

        ProfileCameraImageStore.createCaptureFile(cacheDir, timestamp = 2L)

        assertFalse("a previous capture must not linger in the cache", stale.exists())
    }

    @Test
    fun discard_deletesACaptureFile() {
        val file = ProfileCameraImageStore.createCaptureFile(cacheDir, timestamp = 1L)
        file.writeBytes(byteArrayOf(9))

        ProfileCameraImageStore.discard(file)

        assertFalse(file.exists())
    }

    @Test
    fun discard_toleratesNullAndMissingFiles() {
        ProfileCameraImageStore.discard(null)
        ProfileCameraImageStore.discard(
            File(cacheDir, "${ProfileCameraImageStore.DIR_NAME}/capture_absent.jpg"),
        )
        // Reaching here without an exception is the assertion.
    }

    @Test
    fun discard_ignoresFilesItDoesNotOwn() {
        val unrelated = File(tempFolder.newFolder("keep"), "family-photo.jpg")
        unrelated.writeBytes(byteArrayOf(1))

        ProfileCameraImageStore.discard(unrelated)

        assertTrue("only capture_*.jpg files may be deleted", unrelated.exists())
    }
}
