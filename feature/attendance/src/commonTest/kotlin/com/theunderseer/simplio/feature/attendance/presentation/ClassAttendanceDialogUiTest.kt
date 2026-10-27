package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceDialogForTest
import com.theunderseer.simplio.feature.attendance.fixtures.testClassAttendanceSheet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class ClassAttendanceDialogUiTest {
    @Test
    fun `duo selection previews counts and saves only when confirmed`() = runComposeUiTest {
        val sheet = testClassAttendanceSheet(duo = true)
        var saved: Map<Int, Int>? = null
        setContent {
            ClassAttendanceDialogForTest(ClassAttendanceUiState.Content(sheet), {}, {}, { visits, _ -> saved = visits })
        }
        onNodeWithTag("class-attendance-people-1-2").assertIsNotEnabled()
        onNodeWithTag("class-attendance-student-1").performClick()
        onNodeWithTag("class-attendance-people-1-2").performClick()
        onNodeWithText("2 / 8").assertExists()
        assertNull(saved)
        onNodeWithText("Cancel class").assertIsNotEnabled()
        onNodeWithText("Save").performClick()
        assertEquals(mapOf(1 to 2), saved)
    }

    @Test
    fun `closing changed draft requires discard and never dispatches attendance`() = runComposeUiTest {
        var closed = false
        var saves = 0
        setContent {
            ClassAttendanceDialogForTest(
                ClassAttendanceUiState.Content(testClassAttendanceSheet()),
                { closed = true },
                {},
                { _, _ -> saves++ },
            )
        }
        onNodeWithTag("class-attendance-student-1").performClick()
        onNodeWithText("Close").performClick()
        onNodeWithText("Discard attendance changes?").assertExists()
        assertEquals(false, closed)
        onNodeWithText("Continue editing").performClick()
        onNodeWithTag("class-attendance-student-1").assertIsOn()
        onNodeWithText("Close").performClick()
        onNodeWithText("Discard").performClick()
        assertEquals(true, closed)
        assertEquals(0, saves)
    }

    @Test
    fun `cancel requires confirmation and saved attendance disables cancellation`() = runComposeUiTest {
        var cancelled = false
        setContent {
            ClassAttendanceDialogForTest(
                ClassAttendanceUiState.Content(testClassAttendanceSheet()),
                {},
                {},
                { visits, cancel ->
                    assertEquals(emptyMap(), visits)
                    cancelled = cancel
                },
            )
        }
        onNodeWithText("Cancel class").performClick()
        onNodeWithText("Cancel this class?").assertExists()
        assertEquals(false, cancelled)
        onNodeWithText("Keep class").performClick()
        assertEquals(false, cancelled)
        onNodeWithText("Cancel class").performClick()
        onNodeWithTag("confirm-class-cancel").performClick()
        assertEquals(true, cancelled)
    }

    @Test
    fun `existing attendance stays checked and cancellation is unavailable`() = runComposeUiTest {
        setContent {
            ClassAttendanceDialogForTest(
                ClassAttendanceUiState.Content(testClassAttendanceSheet(recordedVisitCount = 1)),
                {},
                {},
                { _, _ -> },
            )
        }
        onNodeWithTag("class-attendance-student-1").assertIsOn()
        onNodeWithText("Cancel class").assertIsNotEnabled()
        onNodeWithTag("class-attendance-student-1").performClick()
        onNodeWithText("Cancel class").assertIsNotEnabled()
        onNodeWithText("Save").assertIsEnabled()
    }
}
