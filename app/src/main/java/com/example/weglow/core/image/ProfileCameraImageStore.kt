package com.example.weglow.core.image

import java.io.File

/**
 * Owns the lifecycle of the throw-away image file that the system camera app
 * writes a profile-picture capture into.
 *
 * The file always lives inside one private sub-directory of the app's internal
 * cache ([DIR_NAME]) — the same directory, and the only one, that the app's
 * `FileProvider` exposes to the camera. Nothing here is a persisted profile
 * picture: once [ProfileImageReader] has turned a capture into neutral
 * `ProfileImageUpload` bytes the temp file is deleted, and the picture shown
 * afterwards still comes from Supabase Storage.
 *
 * Pure `java.io.File` logic so it is unit-testable without Android or a camera.
 */
object ProfileCameraImageStore {

    /** Must match the `<cache-path>` entry in `res/xml/file_paths.xml`. */
    const val DIR_NAME: String = "profile-camera"

    private const val PREFIX = "capture_"
    private const val SUFFIX = ".jpg"

    /**
     * Creates a fresh, empty file for a pending capture and returns it. Any
     * capture files left over from an earlier attempt (the user backed out of
     * the camera, or the process was killed mid-capture) are removed first so
     * the cache cannot accumulate orphaned images.
     */
    fun createCaptureFile(cacheDir: File, timestamp: Long = System.currentTimeMillis()): File {
        val dir = File(cacheDir, DIR_NAME)
        clear(dir)
        dir.mkdirs()
        return File(dir, "$PREFIX$timestamp$SUFFIX")
    }

    /**
     * Best-effort deletion of a single capture file. Safe to call with `null`
     * and refuses to touch anything that is not one of our capture files.
     */
    fun discard(file: File?) {
        if (file != null && isCaptureFile(file) && file.exists()) {
            file.delete()
        }
    }

    fun isCaptureFile(file: File): Boolean =
        file.name.startsWith(PREFIX) && file.name.endsWith(SUFFIX)

    private fun clear(dir: File) {
        dir.listFiles()?.forEach { child ->
            if (child.isFile && isCaptureFile(child)) child.delete()
        }
    }
}
