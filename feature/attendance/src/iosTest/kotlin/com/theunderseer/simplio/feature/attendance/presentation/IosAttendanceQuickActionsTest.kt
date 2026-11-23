package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class IosAttendanceQuickActionsTest {
    @Test
    fun `outside tap dismisses the menu without dimming or activating the underlying control`() = runComposeUiTest {
        var underlyingClicks = 0
        var selection: IosAttendanceQuickAction? = null
        setContent {
            var expanded by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxSize().background(Color.White).testTag("quick-actions-host")) {
                Box(Modifier.size(72.dp).testTag("underlying-control").clickable { underlyingClicks++ })
                IosAttendanceQuickActions(
                    expanded = expanded,
                    reduceMotion = true,
                    onExpandedChange = { expanded = it },
                    onAction = { selection = it },
                    modifier = Modifier.matchParentSize(),
                )
            }
        }

        val host = onNodeWithTag("quick-actions-host")
        val before = host.captureToImage().toPixelMap()[0, 0]
        onNodeWithContentDescription("Add actions").performClick()
        assertEquals(before, host.captureToImage().toPixelMap()[0, 0])

        val underlying = onNodeWithTag("underlying-control")
        underlying.performTouchInput { click() }
        onNodeWithText("Add student").assertDoesNotExist()
        runOnIdle {
            assertEquals(0, underlyingClicks)
            assertNull(selection)
        }
        underlying.performTouchInput { click() }
        runOnIdle { assertEquals(1, underlyingClicks) }
    }
}
