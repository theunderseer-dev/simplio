package com.theunderseer.simplio.shared.presentation

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.theunderseer.simplio.shared.presentation.theme.SilverDark
import com.theunderseer.simplio.shared.presentation.theme.SilverLight
import com.theunderseer.simplio.shared.presentation.theme.positive
import com.theunderseer.simplio.shared.presentation.theme.warning
import kotlin.test.Test
import kotlin.test.assertTrue

class SimplioContrastTest {
    @Test
    fun `text and semantic colors remain legible on both appearances`() {
        for (scheme in listOf(SilverLight, SilverDark)) {
            for ((ink, paper) in textPairs(scheme)) {
                assertTrue(contrast(ink, paper) >= 4.5f, "Insufficient text contrast: $ink on $paper")
            }
            for (paper in listOf(scheme.surfaceContainerLowest, scheme.surfaceContainerHigh)) {
                for (ink in listOf(scheme.positive, scheme.error, scheme.onSurfaceVariant)) {
                    for (alpha in listOf(0.08f, 0.12f, 0.18f)) {
                        val background = ink.copy(alpha = alpha).compositeOver(paper)
                        assertTrue(contrast(ink, background) >= 3f, "Insufficient icon contrast")
                    }
                }
            }
            assertTrue(contrast(scheme.tertiary, scheme.surface) >= 3f, "Focus must remain visible")
        }
    }

    private fun textPairs(s: ColorScheme): List<Pair<Color, Color>> = buildList {
        add(s.onPrimary to s.primary)
        add(s.onPrimary to s.secondary)
        add(s.onPrimaryContainer to s.primaryContainer)
        add(s.onSecondaryContainer to s.secondaryContainer)
        add(s.onTertiaryContainer to s.tertiaryContainer)
        add(s.onErrorContainer to s.errorContainer)
        add(s.inverseOnSurface to s.inverseSurface)
        for (paper in listOf(s.background, s.surface, s.surfaceContainerLowest, s.surfaceContainerHighest)) {
            for (ink in listOf(s.onSurface, s.onSurfaceVariant, s.primary, s.error, s.warning, s.positive)) {
                add(ink to paper)
            }
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance()
        val y = b.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }
}
