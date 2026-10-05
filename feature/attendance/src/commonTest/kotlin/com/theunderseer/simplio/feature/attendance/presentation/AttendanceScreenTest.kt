package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenTest {
    @Test
    fun `displays registered student attendance`() = runComposeUiTest {
        // Given
        val packageType =
            PackageType(
                id = 1,
                name = "5-class package",
                totalClasses = 5,
                validityWeeks = 6,
                price = 1700,
            )

        val studentPackage =
            StudentPackage(
                id = 1,
                studentId = 1,
                packageType = packageType,
                startedOn = LocalDate(2026, 10, 1),
                attendanceDates =
                setOf(
                    LocalDate(2026, 10, 1),
                    LocalDate(2026, 10, 3),
                    LocalDate(2026, 10, 5),
                ),
            )

        val state =
            AttendanceUiState.Content(
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
                            studentPackage = studentPackage,
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
            )

        // When
        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _ -> },
            )
        }

        // Then
        onNodeWithText("Anna Smith")
            .assertIsDisplayed()

        onNodeWithText("3 / 5")
            .assertIsDisplayed()

        onNodeWithText(studentPackage.startedOn.toString())
            .assertIsDisplayed()

        onNodeWithText(studentPackage.expiresOn.toString())
            .assertIsDisplayed()

        onNodeWithText("5-class package")
            .assertIsDisplayed()
    }

    @Test
    fun `displays empty attendance state`() = runComposeUiTest {
        // Given
        val state =
            AttendanceUiState.Content(
                attendance =
                Attendance(
                    entries = emptyList(),
                ),
                packageTypes = emptyList(),
            )

        // When
        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _ -> },
            )
        }

        // Then
        onNodeWithText("No students yet")
            .assertIsDisplayed()

        onNodeWithText("Add Student")
            .assertIsDisplayed()
    }

    @Test
    fun `opens student registration dialog when add student is clicked`() = runComposeUiTest {
        val packageType =
            PackageType(
                id = 1,
                name = "4-class package",
                totalClasses = 4,
                validityWeeks = 5,
                price = 1400,
            )

        val state =
            AttendanceUiState.Content(
                attendance =
                Attendance(
                    entries = emptyList(),
                ),
                packageTypes = listOf(packageType),
            )

        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _ -> },
            )
        }

        onNodeWithText("Add Student")
            .performClick()

        onNodeWithText("Full name")
            .assertIsDisplayed()

        onNodeWithText("4-class package")
            .assertIsDisplayed()

        onNodeWithText("Register")
            .assertIsDisplayed()
    }

    @Test
    fun `displays add student action when attendance has students`() = runComposeUiTest {
        // Given
        val packageType =
            PackageType(
                id = 1,
                name = "4-class package",
                totalClasses = 4,
                validityWeeks = 5,
                price = 1400,
            )

        val state =
            AttendanceUiState.Content(
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
                                attendanceDates =
                                setOf(
                                    LocalDate(2026, 10, 5),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
            )

        // When
        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _ -> },
            )
        }

        // Then
        onNodeWithText("Add Student")
            .assertIsDisplayed()
    }
}
