package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.currentDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstAttendanceDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.nextAttendanceDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.student
import com.theunderseer.simplio.feature.attendance.fixtures.testAttendanceContentUiState
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenSharingUiTest {
    private val state = testAttendanceContentUiState(
        attendance = Attendance(
            entries = listOf(
                student.copy(
                    groupId = DefaultStudentGroups.GROUP_A_ID,
                ),
                testStudent(
                    id = 2,
                    fullName = "Bob Lee",
                    groupId = DefaultStudentGroups.GROUP_B_ID,
                ),
            ).map { groupStudent ->
                testStudentAttendanceUiState(
                    student = groupStudent,
                    visitsByDate = mapOf(
                        firstAttendanceDate to 1,
                        nextAttendanceDate to 1,
                    ),
                ).attendance.entries.single()
            },
        ),
        packageTypes = listOf(fourClassPackage),
        currentDate = currentDate,
    )

    @Test
    fun `displays separate copy actions for student groups`() = runComposeUiTest {
        // When
        setContent {
            AttendanceScreenForTest(state = state)
        }

        // Then
        onNodeWithContentDescription(
            "Copy attendance for ${DefaultStudentGroups.GROUP_A.name}",
        ).assertIsDisplayed()

        onNodeWithContentDescription(
            "Copy attendance for ${DefaultStudentGroups.GROUP_B.name}",
        ).assertIsDisplayed()

        onNodeWithContentDescription("Copy attendance")
            .assertDoesNotExist()
    }

    @Test
    fun `copying group A excludes students from group B`() = runComposeUiTest {
        // Given
        var copiedText: String? = null

        setContent {
            AttendanceScreenForTest(
                state = state,
                onCopyAttendance = { copiedText = it },
            )
        }

        // When
        onNodeWithContentDescription(
            "Copy attendance for ${DefaultStudentGroups.GROUP_A.name}",
        ).performClick()

        // Then
        assertEquals(
            "Anna Smith 2/4 (until 30/10)",
            copiedText,
        )
    }

    @Test
    fun `copying group B excludes students from group A`() = runComposeUiTest {
        // Given
        var copiedText: String? = null

        setContent {
            AttendanceScreenForTest(
                state = state,
                onCopyAttendance = { copiedText = it },
            )
        }

        // When
        onNodeWithContentDescription(
            "Copy attendance for ${DefaultStudentGroups.GROUP_B.name}",
        ).performClick()

        // Then
        assertEquals(
            "Bob Lee 2/4 (until 30/10)",
            copiedText,
        )
    }

    @Test
    fun `hides copy confirmation automatically`() = runComposeUiTest {
        // Given
        mainClock.autoAdvance = false

        setContent {
            AttendanceScreenForTest(state = state)
        }

        // When
        onNodeWithContentDescription(
            "Copy attendance for ${DefaultStudentGroups.GROUP_A.name}",
        ).performClick()

        mainClock.advanceTimeByFrame()

        // Then
        onNodeWithText("Copied to clipboard")
            .assertIsDisplayed()

        mainClock.advanceTimeBy(5_000)
        waitForIdle()

        onNodeWithText("Copied to clipboard")
            .assertDoesNotExist()
    }

    @Test
    fun `shows copy confirmation after copy action`() = runComposeUiTest {
        // Given
        val state = testStudentAttendanceUiState(student = student)

        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // When
        onNodeWithContentDescription(
            "Copy attendance for ${DefaultStudentGroups.GROUP_A.name}",
        ).performClick()

        // Then
        onNodeWithText("Copied to clipboard")
            .assertIsDisplayed()
    }
}
