package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
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
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenTest {
    private val today = LocalDate(2026, 10, 5)

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
                currentDate = today,
                onRegisterStudent = { _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
            )
        }

        // Then
        onNodeWithText("Anna Smith")
            .assertIsDisplayed()

        onNodeWithText("3 / 5")
            .assertIsDisplayed()

        onNodeWithText("Oct 1")
            .assertIsDisplayed()

        onNodeWithText("Nov 12")
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
                currentDate = today,
                onRegisterStudent = { _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
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
                currentDate = today,
                onRegisterStudent = { _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
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
                currentDate = today,
                onRegisterStudent = { _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
            )
        }

        // Then
        onNodeWithText("Add Student")
            .assertIsDisplayed()
    }

    @Test
    fun `opens date picker when registration date is clicked`() = runComposeUiTest {
        // Given
        val packageType =
            PackageType(
                id = 1,
                name = "4-class package",
                totalClasses = 4,
                validityWeeks = 5,
                price = 1400,
            )

        setContent {
            StudentRegistrationDialog(
                packageTypes = listOf(packageType),
                initialDate = today,
                onRegister = { _, _ -> },
                onDismiss = {},
            )
        }

        // When
        onNodeWithContentDescription("Registration date")
            .performClick()

        // Then
        onNodeWithText("Select registration date")
            .assertIsDisplayed()
    }

    @Test
    fun `updates registration date after selecting past date`() = runComposeUiTest {
        // Given
        val today =
            LocalDate(
                2026,
                10,
                5,
            )

        val packageType =
            PackageType(
                id = 1,
                name = "4-class package",
                totalClasses = 4,
                validityWeeks = 5,
                price = 1400,
            )

        setContent {
            StudentRegistrationDialog(
                packageTypes = listOf(packageType),
                initialDate = today,
                onRegister = { _, _ -> },
                onDismiss = {},
            )
        }

        // When
        onNodeWithContentDescription("Registration date")
            .performClick()

        onNodeWithContentDescription("Registration date")
            .performClick()

        onNodeWithText("Saturday, October 3, 2026")
            .performClick()

        onNodeWithText("OK")
            .performClick()

        onNodeWithText("Oct 3")
            .assertIsDisplayed()
    }

    @Test
    fun `shows delete action for registered student`() = runComposeUiTest {
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
                currentDate = today,
                onRegisterStudent = { _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
            )
        }

        // Then
        onNodeWithContentDescription("Delete Anna Smith")
            .assertIsDisplayed()
    }

    @Test
    fun `shows confirmation dialog before deleting student`() = runComposeUiTest {
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

        var deletedStudentId: Int? = null

        setContent {
            AttendanceScreen(
                state = state,
                currentDate = today,
                onRegisterStudent = { _, _ -> },
                onDeleteStudent = { studentId ->
                    deletedStudentId = studentId
                },
                onCopyAttendance = {},
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

    @Test
    fun `displays copy attendance action`() = runComposeUiTest {
        // Given
        val state =
            AttendanceUiState.Content(
                attendance =
                Attendance(
                    entries = emptyList(),
                ),
                packageTypes = emptyList(),
            )

        val today =
            LocalDate(
                2026,
                10,
                6,
            )

        // When
        setContent {
            AttendanceScreen(
                state = state,
                currentDate = today,
                onRegisterStudent = { _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
            )
        }

        // Then
        onNodeWithContentDescription("Copy attendance")
            .assertIsDisplayed()
    }

    @Test
    fun `provides formatted attendance when copy action is clicked`() = runComposeUiTest {
        // Given
        val packageType =
            PackageType(
                id = 1,
                name = "5-class package",
                totalClasses = 5,
                validityWeeks = 6,
                price = 1700,
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
                                startedOn = LocalDate(2026, 10, 1),
                                attendanceDates =
                                setOf(
                                    LocalDate(2026, 10, 1),
                                    LocalDate(2026, 10, 3),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
            )

        val today =
            LocalDate(
                2026,
                10,
                6,
            )

        var copiedText: String? = null

        setContent {
            AttendanceScreen(
                state = state,
                currentDate = today,
                onRegisterStudent = { _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = { text ->
                    copiedText = text
                },
            )
        }

        // When
        onNodeWithContentDescription("Copy attendance")
            .performClick()

        // Then
        assertEquals(
            "\tAnna Smith\t2 / 5\t(until 12/11)",
            copiedText,
        )
    }

    @Test
    fun `shows copy confirmation after copy action`() = runComposeUiTest {
        // Given
        val state =
            AttendanceUiState.Content(
                attendance = Attendance(entries = emptyList()),
                packageTypes = emptyList(),
            )

        setContent {
            AttendanceScreen(
                state = state,
                currentDate = LocalDate(2026, 10, 6),
                onRegisterStudent = { _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
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
            AttendanceUiState.Content(
                attendance = Attendance(entries = emptyList()),
                packageTypes = emptyList(),
            )

        setContent {
            AttendanceScreen(
                state = state,
                currentDate = LocalDate(2026, 10, 6),
                onRegisterStudent = { _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
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
}
