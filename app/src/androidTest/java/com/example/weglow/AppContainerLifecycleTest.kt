package com.example.weglow

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weglow.app.AppContainer
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [AppContainer] must treat the acne and hairstyle repositories as stable, reused
 * dependencies rather than factories: each one owns an expensive native inference engine
 * (a LiteRT/TFLite Interpreter, or an ONNX OrtEnvironment/OrtSession), so resolving the
 * dependency repeatedly must never construct a second, independent engine.
 */
@RunWith(AndroidJUnit4::class)
class AppContainerLifecycleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun hairstyleRepositoryIsAStableSingletonAcrossRepeatedResolution() {
        val container = AppContainer()

        val first = container.hairstyleRepository(context)
        val second = container.hairstyleRepository(context)
        val third = container.hairstyleRepository(context)

        assertSame(first, second)
        assertSame(first, third)
    }

    @Test fun acneScanRepositoryIsAStableSingletonAcrossRepeatedResolution() {
        val container = AppContainer()

        val first = container.acneScanRepository(context)
        val second = container.acneScanRepository(context)
        val third = container.acneScanRepository(context)

        assertSame(first, second)
        assertSame(first, third)
    }

    @Test fun differentContainersOwnIndependentRepositories() {
        // Two containers are two composition roots (e.g. two Activity instances after a
        // process restart) and must not secretly share state, only within-container reuse
        // is guaranteed.
        val first = AppContainer().hairstyleRepository(context)
        val second = AppContainer().hairstyleRepository(context)

        assertNotSame(first, second)
    }
}
