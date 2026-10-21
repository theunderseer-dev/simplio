package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.presentation.ClassDatePickerDialog
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class ClassDatePickerDialogUiTest {
    @Test
    fun `calendar titles have space from edges and replace the default title`() {
        for (title in listOf("Select registration date", "Select purchase date", "Select attendance date")) {
            runComposeUiTest {
                setContent {
                    ClassDatePickerDialog(
                        title = title,
                        state = rememberDatePickerState(),
                        canConfirm = false,
                        onConfirm = {},
                        onDismiss = {},
                    )
                }

                val titleBounds = onNodeWithText(title).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val calendarBounds = onNodeWithTag("class-date-picker").fetchSemanticsNode().boundsInRoot
                assertTrue(titleBounds.left > calendarBounds.left)
                assertTrue(titleBounds.top > calendarBounds.top)
                assertTrue(titleBounds.right <= calendarBounds.right)
                assertTrue(titleBounds.bottom <= calendarBounds.bottom)
                onNodeWithText("Select date").assertDoesNotExist()
            }
        }
    }
}
