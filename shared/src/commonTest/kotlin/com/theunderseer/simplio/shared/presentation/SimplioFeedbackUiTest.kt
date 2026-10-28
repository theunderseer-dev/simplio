package com.theunderseer.simplio.shared.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.shared.presentation.components.SimplioFilterChip
import com.theunderseer.simplio.shared.presentation.components.SimplioTextButton
import com.theunderseer.simplio.shared.presentation.components.simplioClickable
import com.theunderseer.simplio.shared.presentation.components.simplioHoverSurface
import com.theunderseer.simplio.shared.presentation.components.simplioToggleable
import com.theunderseer.simplio.shared.presentation.theme.SimplioTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SimplioFeedbackUiTest {
    @Test
    fun `row and text action hover never flash beyond their settled colors`() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                val rowSource = MutableInteractionSource()
                val actionSource = MutableInteractionSource()
                setContent {
                    SimplioTheme(dark) {
                        Surface {
                            Column {
                                Box(
                                    Modifier.size(160.dp, 56.dp)
                                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                        .simplioHoverSurface(rowSource)
                                        .testTag("row"),
                                )
                                SimplioTextButton(
                                    onClick = {},
                                    modifier = Modifier.testTag("action"),
                                    interactionSource = actionSource,
                                ) { Text("Cancel class") }
                            }
                        }
                    }
                }
                waitForIdle()
                mainClock.autoAdvance = false
                for ((tag, source) in listOf("row" to rowSource, "action" to actionSource)) {
                    val rest = sample(tag).luminance()
                    val enter = HoverInteraction.Enter()
                    runOnIdle { assertTrue(source.tryEmit(enter)) }
                    val entering = (1..12).map {
                        mainClock.advanceTimeBy(16)
                        sample(tag).luminance()
                    }
                    val hover = entering.last()
                    assertTrue(abs(hover - rest) > (if (tag == "row") 0.0001f else 0.001f), "Hover must remain visible")
                    assertTrue(abs(hover - rest) < 0.10f, "Hover must be a subtle tint")
                    runOnIdle { assertTrue(source.tryEmit(HoverInteraction.Exit(enter))) }
                    val leaving = (1..12).map {
                        mainClock.advanceTimeBy(16)
                        sample(tag).luminance()
                    }
                    for (value in entering + leaving) {
                        assertTrue(
                            value >= minOf(rest, hover) - 0.005f && value <= maxOf(rest, hover) + 0.005f,
                            "Hover transition overshot its endpoints: $value ($rest to $hover)",
                        )
                    }
                    assertTrue(abs(leaving.last() - rest) < 0.005f, "Hover must clear on exit")
                }
            }
        }
    }

    @Test
    fun `read only field overlay keeps feedback inside rounded corners`() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                val source = MutableInteractionSource()
                setContent {
                    SimplioTheme(dark) {
                        Surface {
                            Box(
                                Modifier.size(160.dp, 56.dp)
                                    .simplioClickable(interactionSource = source, onClick = {})
                                    .testTag("field"),
                            )
                        }
                    }
                }
                val corner = sample("field", 0f, 0f)
                val center = sample("field")
                runOnIdle { assertTrue(source.tryEmit(HoverInteraction.Enter())) }
                waitForIdle()
                assertEquals(corner, sample("field", 0f, 0f), "Hover must not paint a square outside the field")
                assertTrue(center != sample("field"), "The field must still respond to hover")
            }
        }
    }

    @Test
    fun `flat chips retain selection and disabled semantics`() = runComposeUiTest {
        var clicks = 0
        setContent {
            SimplioTheme {
                Column {
                    SimplioFilterChip(true, { clicks++ }, { Text("Tue/Thu evening") }, Modifier.testTag("selected"))
                    SimplioFilterChip(false, { clicks++ }, { Text("Other group") }, Modifier.testTag("other"))
                    SimplioFilterChip(
                        false,
                        { clicks++ },
                        { Text("Disabled") },
                        Modifier.testTag("disabled"),
                        enabled = false,
                    )
                }
            }
        }
        onNodeWithTag("selected").assertIsSelected().performClick()
        onNodeWithTag("other").assertIsNotSelected().performClick()
        onNodeWithTag("disabled").assertIsNotEnabled().performClick()
        assertEquals(2, clicks)
    }

    @Test
    fun `hovered rows retain visible boundaries and do not merge into the light page`() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                val source = MutableInteractionSource()
                var page = Color.Unspecified
                setContent {
                    SimplioTheme(dark) {
                        page = MaterialTheme.colorScheme.background
                        Surface(color = page) {
                            Box(
                                Modifier.size(160.dp, 56.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                    .simplioHoverSurface(source)
                                    .testTag("row"),
                            )
                        }
                    }
                }
                runOnIdle { assertTrue(source.tryEmit(HoverInteraction.Enter())) }
                waitForIdle()
                val fill = sample("row").luminance()
                val edge = sample("row", 0.5f, 0f).luminance()
                assertTrue(abs(edge - fill) > 0.03f, "The row needs a visible outline in both themes")
                if (!dark) {
                    assertTrue(
                        abs(fill - page.luminance()) > 0.05f,
                        "Hovered card must stay distinct from the page",
                    )
                }
            }
        }
    }

    @Test
    fun `rounded checkbox rows retain keyboard activation and disabled state`() = runComposeUiTest {
        setContent {
            SimplioTheme {
                var checked by remember { mutableStateOf(false) }
                Column {
                    Box(
                        Modifier.size(160.dp, 48.dp)
                            .simplioToggleable(checked, onValueChange = { checked = it })
                            .testTag("toggle-row"),
                    )
                    Box(
                        Modifier.size(160.dp, 48.dp)
                            .simplioToggleable(false, enabled = false, onValueChange = { checked = it })
                            .testTag("disabled-row"),
                    )
                }
            }
        }
        val row = onNodeWithTag("toggle-row").assertIsOff()
        row.performSemanticsAction(SemanticsActions.RequestFocus)
        row.performKeyInput { pressKey(Key.Spacebar) }
        row.assertIsOn()
        onNodeWithTag("disabled-row").assertIsNotEnabled().performClick().assertIsOff()
        row.assertIsOn()
    }

    private fun ComposeUiTest.sample(tag: String, x: Float = 0.25f, y: Float = 0.8f): Color {
        val pixels = onNodeWithTag(tag).captureToImage().toPixelMap()
        return pixels[(pixels.width * x).toInt(), (pixels.height * y).toInt()]
    }
}
