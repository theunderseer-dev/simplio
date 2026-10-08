package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
class AttendanceScreenUiTest {
    private val today = LocalDate(2026, 10, 5)

    private val packageType = testFourClassPackage()

    @Test
    fun `displays registered student attendance`() = runComposeUiTest {
        // Given
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
                            studentPackage = studentPackage,
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
                onRegisterStudent = { _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _ -> },
                onRemoveStudentAttendance = { _, _ -> },
            )
        }

        // Then
        onNodeWithText("Anna Smith")
            .assertIsDisplayed()

        onNodeWithText("3 / 4")
            .assertIsDisplayed()

        onNodeWithText("Oct 1")
            .assertIsDisplayed()

        onNodeWithText("Oct 29")
            .assertIsDisplayed()

        onNodeWithText("4-class package")
            .assertIsDisplayed()
    }

    @Test
    fun `displays empty attendance state`() = runComposeUiTest {
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
        onNodeWithText("No students yet")
            .assertIsDisplayed()

        onNodeWithText("Add Student")
            .assertIsDisplayed()
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
    fun `shows attendance dates when classes count is clicked`() = runComposeUiTest {
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
                                attendanceDates =
                                setOf(
                                    LocalDate(2026, 10, 1),
                                    LocalDate(2026, 10, 3),
                                    LocalDate(2026, 10, 5),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = today,
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
        onNodeWithText("3 / 4")
            .performClick()

        // Then
        onNodeWithText("Attendance dates")
            .assertIsDisplayed()

        onNode(
            hasTestTag("attendance-dates-menu") and
                hasAnyDescendant(hasText("1. Oct 1")) and
                hasAnyDescendant(hasText("2. Oct 3")) and
                hasAnyDescendant(hasText("3. Oct 5")),
        ).assertIsDisplayed()
    }

    @Test
    fun `exposes attendance count as attendance history action`() = runComposeUiTest {
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
                                attendanceDates =
                                setOf(
                                    LocalDate(2026, 10, 1),
                                    LocalDate(2026, 10, 3),
                                    LocalDate(2026, 10, 5),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = today,
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

        // Then
        onNodeWithContentDescription(
            "View attendance dates for Anna Smith",
        ).assertIsDisplayed()
    }

    @Test
    fun `confirms attendance with student id and selected date and closes picker`() = runComposeUiTest {
        // Given
        val student = Student(
            id = 7,
            fullName = "Anna Smith",
        )
        val studentPackage = StudentPackage(
            id = 1,
            studentId = student.id,
            packageType = packageType,
            startedOn = LocalDate(2026, 10, 2),
            attendanceDates = setOf(LocalDate(2026, 10, 2)),
        )
        val state = attendanceContentUiState(
            attendance = Attendance(
                entries = listOf(
                    RegisteredStudent(
                        student = student,
                        studentPackage = studentPackage,
                    ),
                ),
            ),
            packageTypes = listOf(packageType),
            currentDate = LocalDate(2026, 10, 9),
        )
        val confirmedAttendance = mutableListOf<Pair<Int, LocalDate>>()

        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { studentId, date ->
                    confirmedAttendance += studentId to date
                },
                onRemoveStudentAttendance = { _, _ -> },
            )
        }

        // When: picker is opened and a past class date is selected
        onNodeWithContentDescription("Mark Anna Smith present")
            .performClick()

        onNodeWithText(
            text = "Monday, October 5, 2026",
            substring = true,
        )
            .performClick()

        // Then: attendance is not submitted yet
        assertEquals(
            emptyList(),
            confirmedAttendance,
        )

        // When
        onNodeWithText("OK")
            .performClick()

        // Then
        assertEquals(
            listOf(student.id to LocalDate(2026, 10, 5)),
            confirmedAttendance,
        )

        onNodeWithText("Select attendance date")
            .assertDoesNotExist()
    }

    @Test
    fun `shows checked state when latest scheduled attendance is recorded`() = runComposeUiTest {
        // Given
        val student = Student(
            id = 7,
            fullName = "Anna Smith",
        )

        val state = attendanceContentUiState(
            attendance = Attendance(
                entries = listOf(
                    RegisteredStudent(
                        student = student,
                        studentPackage = StudentPackage(
                            id = 1,
                            studentId = student.id,
                            packageType = packageType,
                            startedOn = LocalDate(2026, 10, 2),
                            attendanceDates = setOf(
                                LocalDate(2026, 10, 2),
                                LocalDate(2026, 10, 5),
                            ),
                        ),
                    ),
                ),
            ),
            packageTypes = listOf(packageType),
            currentDate = LocalDate(2026, 10, 6),
        )

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
        onNodeWithText("✓")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Checked",
                ),
            )
    }

    @Test
    fun `opens attendance date picker when pending check is clicked`() = runComposeUiTest {
        // Given
        val student =
            Student(
                id = 7,
                fullName = "Anna Smith",
            )

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
                                startedOn = LocalDate(2026, 10, 2),
                                attendanceDates =
                                setOf(
                                    LocalDate(2026, 10, 2),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
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
        onNodeWithContentDescription("Mark Anna Smith present")
            .performClick()

        // Then
        onNodeWithText("Select attendance date")
            .assertIsDisplayed()
    }

    @Test
    fun `confirms attendance removal with student id and date and closes dialog`() = runComposeUiTest {
        // Given
        val student =
            Student(
                id = 7,
                fullName = "Anna Smith",
            )

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
                                startedOn = LocalDate(2026, 10, 2),
                                attendanceDates = setOf(
                                    LocalDate(2026, 10, 2),
                                    LocalDate(2026, 10, 5),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = LocalDate(2026, 10, 6),
            )

        val removedAttendance = mutableListOf<Pair<Int, LocalDate>>()

        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _ -> },
                onRemoveStudentAttendance = { studentId, date ->
                    removedAttendance += studentId to date
                },
            )
        }

        // When
        onNodeWithContentDescription("Mark Anna Smith present")
            .performClick()

        // Then
        onNodeWithText("Remove attendance?")
            .assertIsDisplayed()

        // Opening the dialog does not remove attendance
        assertEquals(
            emptyList(),
            removedAttendance,
        )

        // When
        onNodeWithText("Remove")
            .performClick()

        // Then
        assertEquals(
            listOf(student.id to LocalDate(2026, 10, 5)),
            removedAttendance,
        )

        onNodeWithText("Remove attendance?")
            .assertDoesNotExist()
    }

    @Test
    fun `initially selects latest scheduled class date in attendance picker`() = runComposeUiTest {
        // Given
        val student =
            Student(
                id = 7,
                fullName = "Anna Smith",
            )

        val studentPackage =
            StudentPackage(
                id = 1,
                studentId = student.id,
                packageType = packageType,
                startedOn = LocalDate(2026, 10, 2),
                attendanceDates = setOf(LocalDate(2026, 10, 2)),
            )

        val state =
            attendanceContentUiState(
                attendance =
                Attendance(
                    entries =
                    listOf(
                        RegisteredStudent(
                            student = student,
                            studentPackage = studentPackage,
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = LocalDate(2026, 10, 8),
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
        onNodeWithContentDescription("Mark Anna Smith present")
            .performClick()

        // Then
        onNodeWithText(
            text = "Monday, October 5, 2026",
            substring = true,
        )
            .assertIsDisplayed()
            .assertIsSelected()
    }

    @Test
    fun `allows only scheduled attendance dates up to current date`() = runComposeUiTest {
        // Given
        val student =
            Student(
                id = 7,
                fullName = "Anna Smith",
            )

        val studentPackage =
            StudentPackage(
                id = 1,
                studentId = student.id,
                packageType = packageType,
                startedOn = LocalDate(2026, 10, 2),
                attendanceDates = setOf(LocalDate(2026, 10, 2)),
            )

        val state =
            attendanceContentUiState(
                attendance =
                Attendance(
                    entries =
                    listOf(
                        RegisteredStudent(
                            student = student,
                            studentPackage = studentPackage,
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = LocalDate(2026, 10, 9),
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
        onNodeWithContentDescription("Mark Anna Smith present")
            .performClick()

        // Then: past Monday and today's Friday are available
        onNodeWithText(
            text = "Monday, October 5, 2026",
            substring = true,
        ).assertIsEnabled()

        onNodeWithText(
            text = "Friday, October 9, 2026",
            substring = true,
        ).assertIsEnabled()

        onNodeWithText(
            text = "Thursday, October 8, 2026",
            substring = true,
        ).assertIsNotEnabled()

        onNodeWithText(
            text = "Monday, October 12, 2026",
            substring = true,
        ).assertIsNotEnabled()
    }

    @Test
    fun `opens attendance removal confirmation when checked attendance is clicked`() = runComposeUiTest {
        // Given
        val student = Student(
            id = 7,
            fullName = "Anna Smith",
        )
        val studentPackage = StudentPackage(
            id = 1,
            studentId = student.id,
            packageType = packageType,
            startedOn = LocalDate(2026, 10, 2),
            attendanceDates = setOf(
                LocalDate(2026, 10, 2),
                LocalDate(2026, 10, 5),
            ),
        )
        val state = attendanceContentUiState(
            attendance = Attendance(
                entries = listOf(
                    RegisteredStudent(
                        student = student,
                        studentPackage = studentPackage,
                    ),
                ),
            ),
            packageTypes = listOf(packageType),
            currentDate = LocalDate(2026, 10, 8),
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
        onNodeWithContentDescription("Mark Anna Smith present")
            .performClick()

        // Then
        onNodeWithText("Remove attendance?")
            .assertIsDisplayed()

        onNodeWithText(
            "Remove attendance for Anna Smith on Oct 5?",
        ).assertIsDisplayed()

        onNodeWithText("Remove")
            .assertIsDisplayed()

        onNodeWithText("Cancel")
            .assertIsDisplayed()

        onNodeWithText("Select attendance date")
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
