package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenStudentDeletionUiTest {
    private val today = LocalDate(2026, 10, 5)

    private val packageType = testFourClassPackage()

    @Test
    fun `shows delete action for registered student`() = runComposeUiTest {
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
                                startedOn = LocalDate(2026, 10, 5),
                                attendanceHistory =
                                AttendanceHistory(
                                    visitsByDate =
                                    mapOf(
                                        LocalDate(2026, 10, 5) to 1,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = today,
            )

        // When
        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _ -> },
                onRemoveStudentAttendance = { _, _ -> },
            )
        }

        // Then
        onNodeWithContentDescription("Delete Anna Smith")
            .assertIsDisplayed()
    }

    @Test
    fun `shows confirmation dialog before deleting student`() = runComposeUiTest {
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
                                startedOn = LocalDate(2026, 10, 5),
                                attendanceHistory =
                                AttendanceHistory(
                                    visitsByDate =
                                    mapOf(
                                        LocalDate(2026, 10, 5) to 1,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = today,
            )

        var deletedStudentId: Int? = null

        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = { studentId ->
                    deletedStudentId = studentId
                },
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _ -> },
                onRemoveStudentAttendance = { _, _ -> },
            )
        }

        // When
        onNodeWithContentDescription("Delete Anna Smith")
            .performClick()

        // Then
        onNodeWithText("Delete student?")
            .assertIsDisplayed()

        onNodeWithText("Delete \"Anna Smith\"?")
            .assertIsDisplayed()

        onNodeWithText("Cancel")
            .assertIsDisplayed()

        onNodeWithText("Delete")
            .assertIsDisplayed()

        assertEquals(
            null,
            deletedStudentId,
        )
    }
}
