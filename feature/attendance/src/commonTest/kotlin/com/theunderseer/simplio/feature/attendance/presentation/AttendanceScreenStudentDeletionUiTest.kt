package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenStudentDeletionUiTest {
    private val today = LocalDate(2026, 10, 5)

    private val student = testStudent()

    private val packageType = testClassPackage()

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
                            student = student,
                            studentPackage =
                            StudentPackage(
                                id = 1,
                                studentId = student.id,
                                packageType = packageType,
                                startedOn = today,
                                attendanceHistory =
                                PackageAttendanceHistory(
                                    visitsByDate = mapOf(today to 1),
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
            AttendanceScreenForTest(
                state = state,
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
                            student = student,
                            studentPackage =
                            StudentPackage(
                                id = 1,
                                studentId = student.id,
                                packageType = packageType,
                                startedOn = today,
                                attendanceHistory =
                                PackageAttendanceHistory(
                                    visitsByDate = mapOf(today to 1),
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
            AttendanceScreenForTest(
                state = state,
                onDeleteStudent = { studentId ->
                    deletedStudentId = studentId
                },
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
