package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class IosStudentSwipeRowTest {
    @Test
    fun `RTL keeps the left swipe and offers the delete action without swiping`() = runComposeUiTest {
        var deleteRequests = 0
        setContent {
            var expanded by remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                IosStudentSwipeRow(
                    studentName = "Sample student",
                    expanded = expanded,
                    onExpandedChange = {
                        expanded = it
                        true
                    },
                    onRequestDelete = { deleteRequests++ },
                    modifier = Modifier.testTag("student-row"),
                ) {
                    Box(Modifier.height(64.dp)) { Text("Sample student") }
                }
            }
        }

        val description = "Delete student Sample student"
        val deleteButton = onNodeWithContentDescription(description)
        deleteButton.assertDoesNotExist()
        onNodeWithText("Sample student").performCustomAccessibilityActionWithLabel(description)
        runOnIdle { assertEquals(1, deleteRequests) }

        val row = onNodeWithTag("student-row")
        row.performTouchInput { swipeRight() }
        deleteButton.assertDoesNotExist()
        row.performTouchInput { swipeLeft() }
        deleteButton.assertIsDisplayed()
        runOnIdle { assertEquals(1, deleteRequests) }
    }
}
