package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenRegistrationUiTest {
    private val today = LocalDate(2026, 10, 5)

    private val packageType = testFourClassPackage()

    @Test
    fun `opens student registration dialog when add student is clicked`() = runComposeUiTest {
        val state = emptyAttendanceState(packageTypes = listOf(packageType))

        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _, _ -> },
                onRemoveStudentAttendance = { _, _ -> },
                onClearMarkStudentPresentState = {},
                onClearRemoveStudentAttendanceState = {},

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
                                PackageAttendanceHistory(
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
                onMarkStudentPresent = { _, _, _ -> },
                onRemoveStudentAttendance = { _, _ -> },
                onClearMarkStudentPresentState = {},
                onClearRemoveStudentAttendanceState = {},

            )
        }

        // Then
        onNodeWithText("Add Student")
            .assertIsDisplayed()
    }

    @Test
    fun `provides selected registration date when student is registered`() = runComposeUiTest {
        // Given
        val state =
            emptyAttendanceState(
                packageTypes = listOf(packageType),
                currentDate = LocalDate(2026, 10, 6),
            )

        var registeredOn: LocalDate? = null

        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, date, _ ->
                    registeredOn = date
                },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _, _ -> },
                onRemoveStudentAttendance = { _, _ -> },
                onClearMarkStudentPresentState = {},
                onClearRemoveStudentAttendanceState = {},

            )
        }

        // When
        onNodeWithText("Add Student")
            .performClick()

        onNodeWithContentDescription("Registration date")
            .performClick()

        onNodeWithText(
            text = "Saturday, October 3, 2026",
            substring = true,
        )
            .performClick()

        onNodeWithText("OK")
            .performClick()

        onNode(hasSetTextAction())
            .performTextInput("Anna Smith")

        onNodeWithText("Register")
            .performClick()

        // Then
        assertEquals(
            LocalDate(2026, 10, 3),
            registeredOn,
        )
    }

    @Test
    fun `provides two visits when duo student is registered`() = runComposeUiTest {
        // Given
        val duoPackage =
            PackageType(
                id = 3,
                name = "Duo 8 · Special rate",
                totalClasses = 8,
                validityWeeks = 4,
                price = 2400,
                maxVisitsPerAttendance = 2,
            )

        val state =
            emptyAttendanceState(
                packageTypes = listOf(packageType, duoPackage),
            )

        var registeredVisitCount: Int? = null

        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _, visitCount ->
                    registeredVisitCount = visitCount
                },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _, _ -> },
                onRemoveStudentAttendance = { _, _ -> },
                onClearMarkStudentPresentState = {},
                onClearRemoveStudentAttendanceState = {},

            )
        }

        // When
        onNodeWithText("Add Student")
            .performClick()

        onNodeWithText("Package")
            .performClick()

        onNodeWithText("Duo 8 · Special rate")
            .performClick()

        onNode(hasSetTextAction())
            .performTextInput("Anna & John")

        onNodeWithText("Register")
            .performClick()

        // Then
        assertEquals(
            2,
            registeredVisitCount,
        )
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
