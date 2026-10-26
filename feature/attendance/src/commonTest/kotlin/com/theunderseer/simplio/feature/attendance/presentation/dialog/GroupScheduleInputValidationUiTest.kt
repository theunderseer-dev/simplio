package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GroupScheduleInputValidationUiTest {
    @Test
    fun `ignored time edits keep confirmation enabled while invalid time values still block it`() = runComposeUiTest {
        setContent { StudentGroupCreationDialogForTest() }
        onNodeWithTag("group-name").performTextInput("Private 2")
        onNodeWithTag("Starts at").performTextReplacement("09:001111")
        onNodeWithTag("Starts at").assertTextContains("09:00")
        onNodeWithText("Use HH:mm, for example 09:00. Value was not changed.").assertDoesNotExist()
        waitUntil(timeoutMillis = 5000) { runCatching { onNodeWithText("Create").assertIsEnabled() }.isSuccess }
        onNodeWithTag("Starts at").performTextReplacement("0900")
        onNodeWithTag("Starts at").assertTextContains("0900")
        onNodeWithText("Create").assertIsNotEnabled()
        onNodeWithTag("Starts at").performTextReplacement("24:00")
        onNodeWithTag("Starts at").assertTextContains("24:00")
        onNodeWithText("Create").assertIsNotEnabled()
        onNodeWithTag("Starts at").performTextReplacement("23:00")
        onNodeWithTag("Ends at").performTextReplacement("23:30")
        waitUntil(timeoutMillis = 5000) { runCatching { onNodeWithText("Create").assertIsEnabled() }.isSuccess }
    }

    @Test
    fun `unknown time zone has a field error and corrected IANA zone enables creating the group`() = runComposeUiTest {
        setContent { StudentGroupCreationDialogForTest() }
        onNodeWithTag("group-name").performTextInput("Private 2")
        onNodeWithTag("group-time-zone").performScrollTo().performTextReplacement("MadeUp/Zone")
        onNodeWithText("Enter a valid time zone, e.g. Asia/Bangkok.").assertIsDisplayed()
        onNodeWithText("Create").assertIsNotEnabled()
        onNodeWithTag("group-time-zone").performTextReplacement("Asia /Bangkok")
        onNodeWithTag("group-time-zone").assertTextContains("MadeUp/Zone")
        onNodeWithText(
            "Use a time zone without spaces, up to 128 characters. Value was not changed.",
        ).assertIsDisplayed()
        onNodeWithTag("group-time-zone").performTextReplacement("Asia/Bangkok")
        waitUntil(timeoutMillis = 5000) { runCatching { onNodeWithText("Create").assertIsEnabled() }.isSuccess }
    }

    @Test
    fun `native time picker works after an ignored time edit`() = runComposeUiTest {
        setContent { StudentGroupCreationDialogForTest() }
        onNodeWithTag("group-name").performTextInput("Private 2")
        onNodeWithTag("Starts at").performTextReplacement("09:001111")
        waitUntil(timeoutMillis = 5000) { runCatching { onNodeWithText("Create").assertIsEnabled() }.isSuccess }
        onAllNodesWithText("Pick")[0].performClick()
        onNodeWithText("OK").performClick()
        onNodeWithTag("Starts at").assertTextContains("09:00")
        waitUntil(timeoutMillis = 5000) { runCatching { onNodeWithText("Create").assertIsEnabled() }.isSuccess }
    }

    @Test
    fun `schedule end date before its start explains the boundary and a later date corrects it`() = runComposeUiTest {
        setContent { StudentGroupCreationDialogForTest() }
        onNodeWithTag("group-name").performTextInput("Private 2")
        onNode(isToggleable()).performScrollTo().performClick()
        onNodeWithText(AttendanceTestData.currentDate.toString()).performScrollTo().performClick()
        // The fixture starts on October 8. October 7 is the explicit invalid end boundary.
        onNodeWithText("Wednesday, October 7, 2026", substring = true).performClick()
        onNodeWithText("OK").performClick()
        onNodeWithText("End date must be on or after the start date.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Create").assertIsNotEnabled()
        onNodeWithText("2026-10-07").performScrollTo().performClick()
        onNodeWithText("Friday, October 9, 2026", substring = true).performClick()
        onNodeWithText("OK").performClick()
        waitUntil(timeoutMillis = 5000) { runCatching { onNodeWithText("Create").assertIsEnabled() }.isSuccess }
    }

    @Test
    fun `inserting an extra digit at any cursor position keeps the valid time and allows explicit digit replacement`() =
        runComposeUiTest {
            setContent { StudentGroupCreationDialogForTest() }
            onNodeWithTag("group-name").performTextInput("Private 2")
            waitUntil(timeoutMillis = 5000) { runCatching { onNodeWithText("Create").assertIsEnabled() }.isSuccess }
            val expectedTime = "09:00"
            for (position in 0..expectedTime.length) {
                onNodeWithTag("Starts at").performTextInputSelection(TextRange(position, position))
                onNodeWithTag("Starts at").performTextInput("1")
                onNodeWithTag("Starts at").assertTextContains(expectedTime)
                onNodeWithText("Use HH:mm, for example 09:00. Value was not changed.").assertDoesNotExist()
                onNodeWithText("Create").assertIsEnabled()
            }
            onNodeWithTag("Starts at").performTextInputSelection(TextRange(3, 4))
            onNodeWithTag("Starts at").performTextInput("5")
            onNodeWithTag("Starts at").assertTextContains("09:50")
            waitUntil(timeoutMillis = 5000) { runCatching { onNodeWithText("Create").assertIsEnabled() }.isSuccess }
        }
}
