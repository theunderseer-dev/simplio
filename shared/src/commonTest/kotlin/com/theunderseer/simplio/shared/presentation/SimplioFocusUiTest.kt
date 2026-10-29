package com.theunderseer.simplio.shared.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.shared.presentation.components.SimplioOutlinedButton
import com.theunderseer.simplio.shared.presentation.components.SimplioTextButton
import com.theunderseer.simplio.shared.presentation.fixtures.FocusTestAction
import com.theunderseer.simplio.shared.presentation.fixtures.FocusTestControl
import com.theunderseer.simplio.shared.presentation.fixtures.SimplioFocusTestData
import com.theunderseer.simplio.shared.presentation.theme.SimplioTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SimplioFocusUiTest {
    @Test
    fun `mouse focus stays unhighlighted while tab and shift tab retain visible focus`() {
        for (dark in listOf(false, true)) {
            for (kind in FocusTestControl.entries) {
                runComposeUiTest {
                    var clicks = 0
                    setContent {
                        SimplioTheme(dark) {
                            Surface {
                                Row {
                                    FocusTestAction(
                                        kind,
                                        SimplioFocusTestData.ACTION_LABEL,
                                        Modifier.testTag("first"),
                                    ) { clicks++ }
                                    FocusTestAction(
                                        kind,
                                        SimplioFocusTestData.NEXT_LABEL,
                                        Modifier.testTag("next"),
                                    ) { clicks++ }
                                }
                            }
                        }
                    }
                    val first = onNodeWithTag("first")
                    val next = onNodeWithTag("next")
                    val firstRest = first.captureToImage()
                    val nextRest = next.captureToImage()

                    first.performMouseInput {
                        enter()
                        click()
                        exit()
                    }
                    first.assertIsFocused()
                    assertTrue(samePixels(firstRest, first.captureToImage()), "$kind retained mouse focus styling")

                    first.performKeyInput { pressKey(Key.Tab) }
                    next.assertIsFocused()
                    assertFalse(samePixels(nextRest, next.captureToImage()), "$kind lost keyboard focus styling")

                    next.performKeyInput {
                        keyDown(Key.ShiftLeft)
                        pressKey(Key.Tab)
                        keyUp(Key.ShiftLeft)
                    }
                    first.assertIsFocused()
                    assertFalse(samePixels(firstRest, first.captureToImage()))

                    first.performMouseInput {
                        enter()
                        click()
                        exit()
                    }
                    first.assertIsFocused()
                    assertTrue(samePixels(firstRest, first.captureToImage()))
                    assertEquals(2, clicks)
                }
            }
        }
    }

    @Test
    fun `closing a mouse opened dialog restores focus without a ring and tab continues forward`() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                setContent {
                    SimplioTheme(dark) {
                        var open by remember { mutableStateOf(false) }
                        Surface {
                            Row {
                                SimplioOutlinedButton({ open = true }, Modifier.testTag("open")) {
                                    Text(SimplioFocusTestData.ACTION_LABEL)
                                }
                                SimplioOutlinedButton({}, Modifier.testTag("next")) {
                                    Text(SimplioFocusTestData.NEXT_LABEL)
                                }
                            }
                        }
                        if (open) {
                            AlertDialog(
                                onDismissRequest = { open = false },
                                title = { Text(SimplioFocusTestData.DIALOG_TITLE) },
                                confirmButton = {
                                    SimplioTextButton({ open = false }) { Text(SimplioFocusTestData.CLOSE_LABEL) }
                                },
                            )
                        }
                    }
                }
                val opener = onNodeWithTag("open")
                val rest = opener.captureToImage()
                opener.performMouseInput {
                    enter()
                    click()
                    exit()
                }
                onNodeWithText(SimplioFocusTestData.CLOSE_LABEL).performMouseInput {
                    enter()
                    click()
                    exit()
                }
                opener.assertIsFocused()
                assertTrue(samePixels(rest, opener.captureToImage()))
                opener.performKeyInput { pressKey(Key.Tab) }
                onNodeWithTag("next").assertIsFocused()
            }
        }
    }
}

private fun samePixels(first: ImageBitmap, second: ImageBitmap): Boolean {
    if (first.width != second.width || first.height != second.height) return false
    val a = first.toPixelMap()
    val b = second.toPixelMap()
    return (0 until first.height).all { y -> (0 until first.width).all { x -> a[x, y] == b[x, y] } }
}
