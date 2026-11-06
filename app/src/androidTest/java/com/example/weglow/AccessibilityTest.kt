package com.example.weglow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.weglow.domain.model.*
import com.example.weglow.feature.routine.RoutineJournalState
import com.example.weglow.feature.routine.routineStepKey
import com.example.weglow.ui.components.*
import com.example.weglow.ui.screens.*
import com.example.weglow.ui.theme.WeGlowTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Isolated fixtures: no user accounts, network requests or saved routine writes. */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {
    @get:Rule val compose = createComposeRule()

    private fun render(content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                WeGlowTheme { Box(Modifier.requiredSize(360.dp, 640.dp)) { content() } }
            }
        }
    }

    @Test fun sharedFieldKeepsItsNameAndLongButtonsGrowAtDoubleTextSize() {
        val button = "Continue with your personal skincare routine"
        render {
            var email by remember { mutableStateOf("") }
            Column {
                WeGlowTextField(email, { email = it }, "Email address")
                WeGlowPrimaryButton(button, {})
            }
        }
        compose.onNodeWithContentDescription("Email address").performTextInput("sample@example.invalid")
        compose.onNodeWithContentDescription("Email address").assertTextContains("sample@example.invalid")
        compose.onNodeWithText(button).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(action(layouts))
            assertFalse(layouts.single().hasVisualOverflow)
        }
    }

    @Test fun ageOptionsAnnounceSelectionAndContinueIsReachable() {
        render { AgeSelectionScreen {} }
        compose.onNodeWithText("18–24").performScrollTo().assertIsNotSelected().performClick().assertIsSelected()
        compose.onNodeWithText("25–35").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Continue").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    @Test fun routineCheckboxAnnouncesItsCheckedState() {
        val product = Product("accessibility-fixture", "Sample cleanser", "", null)
        val step = RoutineStep("Cleanser", product, emptyList())
        val key = routineStepKey(LocalDate.now(), true, 0, product.id)
        render {
            var done by remember { mutableStateOf(false) }
            RoutinesScreen(false, null, RoutinePlan(listOf(step), emptyList()), {},
                initialMorning = true,
                journal = RoutineJournalState(completedKeys = if (done) setOf(key) else emptySet()),
                onToggleRoutineStep = { done = !done })
        }
        compose.onNodeWithContentDescription("Complete Sample cleanser")
            .performScrollTo().assertIsOff().performClick().assertIsOn()
    }

    @Test fun homeHeaderControlsHaveFullTouchTargets() {
        render { HomeScreen({}, {}, {}) }
        compose.onNodeWithContentDescription("Open profile").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Open alerts").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test fun ownProductTaskHasAWorkingCompletionControl() {
        val step = RoutineStep("Cleanser", null, emptyList(), trackable = true)
        val key = routineStepKey(LocalDate.now(), true, 0, null)
        render {
            var done by remember { mutableStateOf(false) }
            RoutinesScreen(false, null, RoutinePlan(listOf(step), emptyList()), {}, initialMorning = true,
                journal = RoutineJournalState(completedKeys = if (done) setOf(key) else emptySet()),
                completableRoutineKeys = setOf(key),
                onSetRoutineCompletion = { actual, value -> assertEquals(key, actual); done = value })
        }
        compose.onNodeWithContentDescription("Complete Cleanser").performScrollTo().assertIsOff().performClick().assertIsOn()
    }

    @Test fun errorsAreAnnouncedAndOfferAWorkingRetry() {
        var retried = false
        render { WeGlowErrorView("Connection unavailable", onRetry = { retried = true }) }
        compose.onNodeWithText("Connection unavailable")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Connection unavailable"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertTrue(retried) }
    }
}
