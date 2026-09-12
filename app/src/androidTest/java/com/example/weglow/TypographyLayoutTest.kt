package com.example.weglow

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import com.example.weglow.feature.routine.routineStepKey
import java.time.LocalDate
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weglow.domain.model.Product
import com.example.weglow.domain.model.RoutinePlan
import com.example.weglow.domain.model.RoutineStep
import com.example.weglow.ui.screens.AgeSelectionScreen
import com.example.weglow.ui.screens.DiscoverScreen
import com.example.weglow.ui.screens.HomeScreen
import com.example.weglow.ui.screens.RoutinesScreen
import com.example.weglow.ui.screens.SkinTypeScreen
import com.example.weglow.ui.theme.WeGlowTheme
import java.io.File
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Renders isolated fixtures; never signs in, scans photos, or writes a user's routine. */
@RunWith(AndroidJUnit4::class)
class TypographyLayoutTest {
    @get:Rule val compose = createComposeRule()

    private val cleanser = Product(
        id = "typography-fixture",
        name = "Gentle Daily Facial Cleanser",
        priceLabel = "LKR 2,450",
        imageUrl = "android.resource://com.example.weglow/${R.drawable.matcha_cleansing_foam}",
        description = "A gentle daily cleanser for your morning routine.",
        brandName = "WeGlow",
        category = "Cleanser",
    )
    private val step = RoutineStep("Cleanser", cleanser, emptyList())

    @Test fun homeUsesReadableHierarchy() {
        render {
            HomeScreen({}, {}, {}, displayName = "Amaya", morningRoutine = listOf(step))
        }
        compose.onNodeWithText("Hello, Amaya").assertIsDisplayed()
        compose.onNodeWithText("Start Scan").assertIsDisplayed()
        screenshot("home")
    }

    @Test fun homeActionsRemainReachableWithLargeText() {
        render(fontScale = 1.3f) {
            HomeScreen({}, {}, {}, displayName = "Amaya", morningRoutine = listOf(step))
        }
        assertSingleLine("Hello, Amaya")
        compose.onNodeWithTag("home").performScrollToNode(hasText("Find hairstyles"))
        compose.onNodeWithText("Find hairstyles").assertIsDisplayed()
        screenshot("home-large-text")
        compose.onNodeWithTag("home").performScrollToNode(hasText(cleanser.name))
        compose.onNodeWithText(cleanser.name).assertIsDisplayed()
    }

    @Test fun ageContinueRemainsReachableWithLargeText() {
        render(fontScale = 1.3f) { AgeSelectionScreen({}) }
        compose.onNodeWithText("25–35").performScrollTo().performClick()
        compose.onNodeWithText("Continue").performScrollTo().assertIsDisplayed()
        screenshot("age-large-text")
    }

    @Test fun skinSelectionAndContinueRemainReachableWithLargeText() {
        render(fontScale = 1.3f) { SkinTypeScreen({}, {}) }
        compose.onNodeWithText("Combination").performScrollTo().performClick()
        assertSingleLine("Combination")
        compose.onNodeWithText("Next Step  →").performScrollTo().assertIsDisplayed()
        screenshot("skin-type-large-text")
    }

    @Test fun discoverShowsReadableProductDetails() {
        render { DiscoverScreen(listOf(cleanser), false, null, {}) }
        compose.onNodeWithText("Discover").assertIsDisplayed()
        screenshot("discover")
        compose.onNodeWithText("Add to Bag · Coming soon").performScrollTo().assertIsDisplayed()
        screenshot("discover-product")
    }

    @Test fun routineNoteRemainsReachableWithLargeText() {
        render(fontScale = 1.3f) {
            RoutinesScreen(false, null, RoutinePlan(listOf(step), listOf(step)), {})
        }
        compose.onNodeWithText(cleanser.name).performScrollTo().assertIsDisplayed()
        screenshot("routine-large-text")
        compose.onNodeWithText("Save Note").performScrollTo().assertIsDisplayed()
        screenshot("routine-note-large-text")
    }

    @Test fun unfinishedShoppingIsClearlyDisabled() {
        render { DiscoverScreen(listOf(cleanser), false, null, {}) }
        compose.onNodeWithText("Add to Bag · Coming soon").performScrollTo().assertIsNotEnabled()
    }

    @Test fun homeCompletionUsesOriginalPlanIndex() {
        var completedKey: String? = null
        val missing = RoutineStep("Unmatched", null, emptyList())
        render {
            HomeScreen({}, {}, {}, morningRoutine = listOf(missing, step),
                onToggleRoutineStep = { completedKey = it })
        }
        compose.onNodeWithTag("home").performScrollToNode(hasContentDescription("Complete ${cleanser.name}"))
        compose.onNodeWithContentDescription("Complete ${cleanser.name}").performClick()
        compose.runOnIdle { assertEquals(routineStepKey(LocalDate.now(), true, 1, cleanser.id), completedKey) }
    }

    @Test fun noteSavePassesSelectedDateAndText() {
        var saved: Triple<String, String?, String>? = null
        render {
            RoutinesScreen(false, null, RoutinePlan(listOf(step), listOf(step)), {},
                onSaveNote = { date, feeling, text -> saved = Triple(date, feeling, text) })
        }
        compose.onNodeWithText("Hydrated").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Comfortable today")
        compose.onNodeWithText("Save Note").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Triple(LocalDate.now().toString(), "Hydrated", "Comfortable today"), saved) }
    }

    @Test fun homeFigmaSectionsRemainReachableWithLargeText() {
        render(fontScale = 1.3f) { HomeScreen({}, {}, {}, morningRoutine = listOf(step)) }
        listOf("Today's Skin Environment", "WeGlow Insights", "Your progress", "Curated For You").forEachIndexed { index, title ->
            compose.onNodeWithTag("home").performScrollToNode(hasText(title))
            compose.onNodeWithText(title).assertIsDisplayed()
            screenshot("home-figma-section-$index")
        }
        compose.onNodeWithText("See all guides · Coming soon").performScrollTo().assertIsNotEnabled()
    }

    @Test fun homeEveningUsesSeparateKeysAndOpensTheSelectedPeriod() {
        var toggled: String? = null
        var opened: Boolean? = null
        render {
            HomeScreen({}, {}, {}, morningRoutine = listOf(step), eveningRoutine = listOf(step),
                completedRoutineKeys = setOf(routineStepKey(LocalDate.now(), true, 0, cleanser.id)),
                onToggleRoutineStep = { toggled = it }, onRoutinesPeriodClick = { opened = it })
        }
        compose.onNodeWithTag("home").performScrollToNode(hasContentDescription("Complete ${cleanser.name}"))
        compose.onNodeWithContentDescription("Complete ${cleanser.name}").assertIsOn()
        compose.onNodeWithText("Evening").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Complete ${cleanser.name}").assertIsOff().performClick()
        compose.onNodeWithText("Continue Routine").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(routineStepKey(LocalDate.now(), false, 0, cleanser.id), toggled)
            assertEquals(false, opened)
        }
    }

    private fun render(fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WeGlowTheme {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) { content() }
                }
            }
        }
    }

    private fun screenshot(name: String) {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("ui-review")!!
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun assertSingleLine(text: String) {
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val layouts = mutableListOf<TextLayoutResult>()
            action(layouts)
            assertEquals("Expected one measured text layout for $text", 1, layouts.size)
            assertEquals("$text should not break in the middle of a word", 1, layouts.single().lineCount)
        }
    }
}
