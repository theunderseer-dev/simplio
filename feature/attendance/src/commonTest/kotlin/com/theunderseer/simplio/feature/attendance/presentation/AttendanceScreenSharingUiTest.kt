package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testAttendanceContentUiState
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenSharingUiTest {
    private val today = LocalDate(2026, 10, 5)
    private val startedOn = LocalDate(2026, 10, 1)
    private val secondAttendance = LocalDate(2026, 10, 3)

    private val groupA = DefaultStudentGroups.GROUP_A
    private val groupB = DefaultStudentGroups.GROUP_B
    private val packageType = testClassPackage()

    private val state = testAttendanceContentUiState(
        attendance = Attendance(
            entries = listOf(
                testStudent(groupId = groupA.id),
                testStudent(
                    id = 2,
                    fullName = "Bob Lee",
                    groupId = groupB.id,
                ),
            ).map { student ->
                RegisteredStudent(
                    student = student,
                    studentPackage = StudentPackage(
                        id = student.id,
                        studentId = student.id,
                        packageType = packageType,
                        startedOn = startedOn,
                        attendanceHistory = PackageAttendanceHistory(
                            visitsByDate = mapOf(
                                startedOn to 1,
                                secondAttendance to 1,
                            ),
                        ),
                    ),
                )
            },
        ),
        packageTypes = listOf(packageType),
        currentDate = today,
    )

    @Test
    fun `displays separate copy actions for student groups`() = runComposeUiTest {
        // When
        setContent {
            AttendanceScreenForTest(state = state)
        }

        // Then
        onNodeWithContentDescription(
            "Copy attendance for ${groupA.name}",
        ).assertIsDisplayed()

        onNodeWithContentDescription(
            "Copy attendance for ${groupB.name}",
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
            "Copy attendance for ${groupA.name}",
        ).performClick()

        // Then
        assertEquals(
            "Anna Smith 2/4 (until 29/10)",
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
            "Copy attendance for ${groupB.name}",
        ).performClick()

        // Then
        assertEquals(
            "Bob Lee 2/4 (until 29/10)",
            copiedText,
        )
    }

    @Test
    fun `shows copy confirmation after copying a group`() = runComposeUiTest {
        // Given
        setContent {
            AttendanceScreenForTest(state = state)
        }

        // When
        onNodeWithContentDescription(
            "Copy attendance for ${groupA.name}",
        ).performClick()

        // Then
        onNodeWithText("Copied to clipboard")
            .assertIsDisplayed()
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
            "Copy attendance for ${groupA.name}",
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
}
