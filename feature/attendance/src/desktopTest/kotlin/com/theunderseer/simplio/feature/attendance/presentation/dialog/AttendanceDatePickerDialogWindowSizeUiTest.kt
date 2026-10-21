package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AttendanceDatePickerDialogWindowSizeUiTest {
    @Test
    fun `duo calendar shows the last week and people selection with no available dates`() =
        runSkikoComposeUiTest(size = Size(800f, 812f)) {
            setContent {
                AttendanceDatePickerDialogForTest(
                    initialDate = LocalDate(2026, 10, 21),
                    currentDate = LocalDate(2026, 10, 21),
                    purchasedOn = LocalDate(2026, 10, 20),
                    startedOn = null,
                    expiresOn = null,
                    maxVisitsPerAttendance = 2,
                    remainingClasses = 8,
                )
            }

            val firstWeek = onNodeWithText("Saturday, October 3, 2026", substring = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val lastWeek = onNodeWithText("Saturday, October 31, 2026", substring = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val people = onNodeWithText("People attending").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertEquals(firstWeek.height, lastWeek.height, 1f)
            assertTrue(lastWeek.bottom <= people.top, "People selection must follow the whole calendar")
            onNodeWithText("Scroll for more").assertDoesNotExist()
            onNodeWithText("1 person").assertIsDisplayed()
            onNodeWithText("2 people").assertIsDisplayed()
            onNodeWithText("No classes to check yet").assertIsDisplayed()
            onNodeWithText("OK").assertIsDisplayed().assertIsNotEnabled()
            onNodeWithText("Cancel").assertIsDisplayed().assertIsEnabled()
        }

    @Test
    fun `single and duo calendars show the whole month in a tall viewport`() {
        for (type in listOf(
            AttendanceTestData.fourClassPackage,
            AttendanceTestData.fiveClassPackage,
            AttendanceTestData.duoSpecialPackage,
        )) {
            runSkikoComposeUiTest(size = Size(800f, 812f)) {
                setContent {
                    AttendanceDatePickerDialogForTest(
                        initialDate = AttendanceTestData.sixthClassDate,
                        currentDate = LocalDate(2026, 10, 21),
                        maxVisitsPerAttendance = type.maxVisitsPerAttendance,
                        remainingClasses = type.totalClasses,
                    )
                }
                onNodeWithText("Saturday, October 31, 2026", substring = true).assertIsDisplayed()
                onNodeWithText("Scroll for more").assertDoesNotExist()
                onNodeWithText("OK").assertIsDisplayed().assertIsEnabled()
                onNodeWithText("Cancel").assertIsDisplayed().assertIsEnabled()
                if (type.maxVisitsPerAttendance == 2) {
                    onNodeWithText("1 person").assertIsDisplayed()
                    onNodeWithText("2 people").assertIsDisplayed()
                } else {
                    onNodeWithText("People attending").assertDoesNotExist()
                }
            }
        }
    }

    @Test
    fun `short duo viewport scrolls to the last week and people without hiding buttons`() =
        runSkikoComposeUiTest(size = Size(800f, 480f)) {
            val date = LocalDate(2026, 10, 30)
            var submission: Pair<LocalDate, Int>? = null
            setContent {
                AttendanceDatePickerDialogForTest(
                    initialDate = date,
                    currentDate = date,
                    maxVisitsPerAttendance = 2,
                    remainingClasses = 8,
                    onConfirm = { selectedDate, visits -> submission = selectedDate to visits },
                )
            }
            onNodeWithText("Scroll for more").assertIsDisplayed()
            onNodeWithText("OK").assertIsDisplayed()
            onNodeWithText("Cancel").assertIsDisplayed()

            val day = onNodeWithText("Friday, October 30, 2026", substring = true)
            val content = onNodeWithTag("class-date-picker-content")
            val scrollRange = content.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            content.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
                scrollBy(0f, scrollRange.maxValue())
            }
            day.assertIsDisplayed().performClick()
            onNodeWithText("2 people").performScrollTo().assertIsDisplayed().performClick()

            onNodeWithText("Scroll for more").assertDoesNotExist()
            onNodeWithText("OK").assertIsDisplayed().assertIsEnabled().performClick()
            onNodeWithText("Cancel").assertIsDisplayed()
            assertEquals(date to 2, submission)
        }

    @Test
    fun `short duo viewport keeps remaining visit and error messages reachable`() =
        runSkikoComposeUiTest(size = Size(800f, 480f)) {
            setContent {
                AttendanceDatePickerDialogForTest(
                    maxVisitsPerAttendance = 2,
                    remainingClasses = 1,
                    errorMessage = "Try again",
                )
            }
            onNodeWithText("Scroll for more").assertIsDisplayed()
            onNodeWithText("Try again").performScrollTo().assertIsDisplayed()
            onNodeWithText("Only 1 visit remaining.").assertIsDisplayed()
            onNodeWithText("2 people").assertIsDisplayed().assertIsNotEnabled()
            onNodeWithText("OK").assertIsDisplayed().assertIsEnabled()
            onNodeWithText("Cancel").assertIsDisplayed().assertIsEnabled()
        }
}
