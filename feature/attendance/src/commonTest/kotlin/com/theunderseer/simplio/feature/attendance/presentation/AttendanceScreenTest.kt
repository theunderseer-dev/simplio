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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

    private val fourClassPackage =
        PackageType(
            id = 1,
            name = "4-class package",
            totalClasses = 4,
            validityWeeks = 4,
            price = 1400,
        )

    private val fiveClassPackage =
        PackageType(
            id = 1,
            name = "5-class package",
            totalClasses = 5,
            validityWeeks = 5,
            price = 1700,
        )

    @Test
    fun `displays registered student attendance`() = runComposeUiTest {
        // Given
        val studentPackage =
            StudentPackage(
                id = 1,
                studentId = 1,
                packageType = fiveClassPackage,
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
                packageTypes = listOf(fiveClassPackage),
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

        onNodeWithText("3 / 5")
            .assertIsDisplayed()

        onNodeWithText("Oct 1")
            .assertIsDisplayed()

        onNodeWithText("Nov 5")
            .assertIsDisplayed()

        onNodeWithText("5-class package")
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
    fun `opens student registration dialog when add student is clicked`() = runComposeUiTest {
        val state = emptyAttendanceState(packageTypes = listOf(fourClassPackage))

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
                                packageType = fourClassPackage,
                                startedOn = LocalDate(2026, 10, 5),
                                attendanceDates =
                                setOf(
                                    LocalDate(2026, 10, 5),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(fourClassPackage),
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
        onNodeWithText("Add Student")
            .assertIsDisplayed()
    }

    @Test
    fun `provides selected registration date when student is registered`() = runComposeUiTest {
        // Given
        val state =
            emptyAttendanceState(
                packageTypes = listOf(fiveClassPackage),
                currentDate = LocalDate(2026, 10, 6),
            )

        var registeredOn: LocalDate? = null

        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, date ->
                    registeredOn = date
                },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _ -> },
                onRemoveStudentAttendance = { _, _ -> },
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

        onNodeWithText("5-class package")
            .performClick()

        onNodeWithText("Register")
            .performClick()

        // Then
        assertEquals(
            LocalDate(2026, 10, 3),
            registeredOn,
        )
    }

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
                                packageType = fourClassPackage,
                                startedOn = LocalDate(2026, 10, 5),
                                attendanceDates =
                                setOf(
                                    LocalDate(2026, 10, 5),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(fourClassPackage),
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
                                packageType = fourClassPackage,
                                startedOn = LocalDate(2026, 10, 5),
                                attendanceDates =
                                setOf(
                                    LocalDate(2026, 10, 5),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(fourClassPackage),
                currentDate = today,
            )

        var deletedStudentId: Int? = null

        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _ -> },
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
        val packageType = fiveClassPackage

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
        onNodeWithText("3 / 5")
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
                                packageType = fiveClassPackage,
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
                packageTypes = listOf(fiveClassPackage),
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
            packageType = fiveClassPackage,
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
            packageTypes = listOf(fiveClassPackage),
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
                            packageType = fiveClassPackage,
                            startedOn = LocalDate(2026, 10, 2),
                            attendanceDates = setOf(
                                LocalDate(2026, 10, 2),
                                LocalDate(2026, 10, 5),
                            ),
                        ),
                    ),
                ),
            ),
            packageTypes = listOf(fiveClassPackage),
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
                                packageType = fiveClassPackage,
                                startedOn = LocalDate(2026, 10, 2),
                                attendanceDates =
                                setOf(
                                    LocalDate(2026, 10, 2),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(fiveClassPackage),
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
                                packageType = fiveClassPackage,
                                startedOn = LocalDate(2026, 10, 2),
                                attendanceDates = setOf(
                                    LocalDate(2026, 10, 2),
                                    LocalDate(2026, 10, 5),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(fiveClassPackage),
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
                packageType = fiveClassPackage,
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
                packageTypes = listOf(fiveClassPackage),
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
                packageType = fiveClassPackage,
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
                packageTypes = listOf(fiveClassPackage),
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
            packageType = fiveClassPackage,
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
            packageTypes = listOf(fiveClassPackage),
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
