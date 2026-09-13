package com.example.weglow.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class TextContrastTest {
    @Test
    fun smallTextMeetsContrastOnItsActualSurfaces() {
        val pairs = listOf(
            Triple("Secondary on page", SoftGray, PageBackground),
            Triple("Secondary on card", SoftGray, CardWhite),
            Triple("Secondary on field", SoftGray, FieldWhite),
            Triple("Secondary on subtle surface", SoftGray, SurfaceSubtle),
            Triple("Secondary on cool surface", SoftGray, SurfaceCool),
            Triple("Secondary on cream", SoftGray, Cream),
            Triple("Secondary on cream variant", SoftGray, CreamDim),
            Triple("Link on page", AccentText, PageBackground),
            Triple("Link on card", AccentText, CardWhite),
            Triple("Hair action", OnCoral, CoralAccent),
            Triple("Pairing badge", OnCoral, PairingCoral),
            Triple("Gold caption", GoldBrown, PageBackground),
            Triple("Selected navigation", DarkGreen, PageBackground),
            Triple("Primary button", Color.White, ButtonGreen),
            Triple("Dark green button", Color.White, DarkGreen),
            Triple("Dark surface supporting text", TextOnDark, DarkGreen),
        )
        pairs.forEach { (name, foreground, background) ->
            val lighter = maxOf(luminance(foreground), luminance(background))
            val darker = minOf(luminance(foreground), luminance(background))
            val ratio = (lighter + 0.05) / (darker + 0.05)
            assertTrue("$name has contrast $ratio; requires at least 4.5:1", ratio >= 4.5)
        }
    }

    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double = if (channel <= 0.04045f) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }
}
