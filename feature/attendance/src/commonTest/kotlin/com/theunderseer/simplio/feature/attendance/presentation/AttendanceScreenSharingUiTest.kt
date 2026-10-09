package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenSharingUiTest {
    private val today = LocalDate(2026, 10, 5)
    private val packageType = testFourClassPackage()

    @Test
    fun `displays copy attendance action`() = runComposeUiTest {
        // Given
        val state = emptyAttendanceState()

        // When
        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _ -> },
                onRemoveStudentAttendance = { _, _ -> },
            )
        }

        // Then
        onNodeWithContentDescription("Copy attendance")
            .assertIsDisplayed()
    }

    @Test
    fun `provides formatted attendance when copy action is clicked`() = runComposeUiTest {
        // Given
        val state =
            attendanceContentUiState(
                attendance =
                Attendance(
                    entries =
                    listOf(
                        RegisteredStudent(
                            student =
                            Student(
                                id = 1,
                                fullName = "Anna Smith",
                            ),
                            studentPackage =
                            StudentPackage(
                                id = 1,
                                studentId = 1,
                                packageType = packageType,
                                startedOn = LocalDate(2026, 10, 1),
                                attendanceHistory =
                                AttendanceHistory(
                                    visitsByDate =
                                    mapOf(
                                        LocalDate(2026, 10, 1) to 1,
                                        LocalDate(2026, 10, 3) to 1,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = today,
            )

        var copiedText: String? = null

        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = { text ->
                    copiedText = text
                },
                onMarkStudentPresent = { _, _ -> },
                onRemoveStudentAttendance = { _, _ -> },
            )
        }

        // When
        onNodeWithContentDescription("Copy attendance")
            .performClick()

        // Then
        assertEquals(
            "\tAnna Smith\t2 / 4\t(until 29/10)",
            copiedText,
        )
    }

    @Test
    fun `shows copy confirmation after copy action`() = runComposeUiTest {
        // Given
        val state =
            emptyAttendanceState(
                currentDate = LocalDate(2026, 10, 6),
            )

        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _ -> },
                onRemoveStudentAttendance = { _, _ -> },
            )
        }

        // When
        onNodeWithContentDescription("Copy attendance")
            .performClick()

        // Then
        onNodeWithText("Copied to clipboard")
            .assertIsDisplayed()
    }

    @Test
    fun `hides copy confirmation automatically`() = runComposeUiTest {
        // Given
        mainClock.autoAdvance = false

        val state =
            emptyAttendanceState(
                currentDate = LocalDate(2026, 10, 6),
            )

        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _ -> },
                onRemoveStudentAttendance = { _, _ -> },
            )
        }

        // When
        onNodeWithContentDescription("Copy attendance")
            .performClick()

        mainClock.advanceTimeByFrame()

        // Then
        onNodeWithText("Copied to clipboard")
            .assertIsDisplayed()

        mainClock.advanceTimeBy(5_000)
        waitForIdle()

        onNodeWithText("Copied to clipboard")
            .assertDoesNotExist()
    }

    private fun emptyAttendanceState(
        packageTypes: List<PackageType> = emptyList(),
        currentDate: LocalDate = today,
    ): AttendanceUiState.Content = attendanceContentUiState(
        attendance = Attendance(entries = emptyList()),
        packageTypes = packageTypes,
        currentDate = currentDate,
    )
}
