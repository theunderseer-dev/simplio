package com.theunderseer.simplio.shared.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.shared.presentation.components.SimplioIconButton
import com.theunderseer.simplio.shared.presentation.components.SimplioToggleIconButton
import com.theunderseer.simplio.shared.presentation.theme.SimplioTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SimplioActionsUiTest {
    @Test
    fun `actions preserve keyboard activation toggle semantics and disabled state without motion`() {
        for (dark in listOf(false, true)) {
            runComposeUiTest(effectContext = NoMotion) {
                var clicks = 0
                setContent {
                    SimplioTheme(darkTheme = dark) {
                        Surface {
                            var checked by remember { mutableStateOf(false) }
                            Row {
                                SimplioIconButton(
                                    ColorPainter(Color.Black),
                                    "Action",
                                    { clicks++ },
                                    Modifier.testTag("action"),
                                )
                                SimplioIconButton(
                                    ColorPainter(Color.Black),
                                    "Disabled",
                                    { clicks++ },
                                    Modifier.testTag("disabled"),
                                    enabled = false,
                                )
                                SimplioToggleIconButton(
                                    ColorPainter(Color.Black),
                                    "Attendance",
                                    checked,
                                    { checked = it },
                                    Modifier.testTag("toggle"),
                                )
                            }
                        }
                    }
                }
                val action = onNodeWithTag("action").assertIsEnabled()
                val bounds = action.fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.width >= 48 && bounds.height >= 48)
                action.performSemanticsAction(SemanticsActions.RequestFocus)
                action.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
                assertEquals(1, clicks)
                onNodeWithTag("disabled").assertIsNotEnabled().performClick()
                assertEquals(1, clicks)
                onNodeWithTag("toggle").assertIsOff().performClick().assertIsOn()
                onNodeWithTag("toggle").performClick().assertIsOff()
            }
        }
    }
}

private object NoMotion : MotionDurationScale {
    override val scaleFactor: Float = 0f
}
