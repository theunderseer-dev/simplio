package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.availablePackages
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.currentDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstAttendanceDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.nextAttendanceDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.student
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.studentGroups
import com.theunderseer.simplio.feature.attendance.fixtures.testClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.ClassHistoryUiState
import com.theunderseer.simplio.feature.attendance.presentation.StudentAttendanceUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelLoadingTest {
    @Test
    fun `loads attendance into content state`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()

        val studentPackageRepository = FakeStudentPackageRepository()

        val student = studentRepository.seed(student)

        val studentPackage = studentPackageRepository.seed(
            studentId = student.id,
            packageType = fourClassPackage,
            startedOn = firstAttendanceDate,
        )

        val expectedAttendance = Attendance(
            entries = listOf(
                RegisteredStudent(
                    student = student,
                    studentPackage = studentPackage,
                ),
            ),
        )

        // When
        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
            packageTypes = availablePackages,
        )

        observeUiState(viewModel)

        advanceUntilIdle()

        // Then
        assertEquals(
            AttendanceUiState.Content(
                attendance = expectedAttendance,
                packageTypes = availablePackages,
                classHistoryState = ClassHistoryUiState.Content(
                    entries = listOf(
                        testClassHistoryEntry(
                            date = nextAttendanceDate,
                        ),
                    ),
                    nextBeforeDate = null,
                ),
                studentGroups = studentGroups,
                currentDate = currentDate,
                latestClassDate = nextAttendanceDate,
                rows = listOf(
                    StudentAttendanceUiState(
                        registeredStudent = expectedAttendance.entries.single(),
                        checkState = AttendanceCheckState.Pending,
                        isCheckEnabled = true,
                    ),
                ),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `moves to error state when attendance loading fails`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()

        val studentPackageRepository = FakeStudentPackageRepository()

        val student = studentRepository.seed(student)

        // When
        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
            packageTypes = availablePackages,
        )

        observeUiState(viewModel)

        advanceUntilIdle()

        // Then
        assertEquals(
            AttendanceUiState.Error(
                message = "Registered student ${student.id} has no package",
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `starts in loading state`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()

        val studentPackageRepository = FakeStudentPackageRepository()

        // When
        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
            packageTypes = availablePackages,
        )

        observeUiState(viewModel)

        // Then
        assertEquals(
            AttendanceUiState.Loading,
            viewModel.uiState.value,
        )
    }

    @Test
    fun `recalculates attendance check state when current date changes`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val otherDay = LocalDate(2026, 10, 9)
        val currentDates = MutableStateFlow(currentDate)

        val student = studentRepository.seed(student)

        studentPackageRepository.seed(
            studentId = student.id,
            packageType = fourClassPackage,
            startedOn = nextAttendanceDate,
        )

        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
            currentDates = currentDates,
        )

        observeUiState(viewModel)
        advanceUntilIdle()

        // Then: Monday attendance is checked on Thursday
        val before =
            assertIs<AttendanceUiState.Content>(
                viewModel.uiState.value,
            )

        assertEquals(currentDate, before.currentDate)
        assertEquals(nextAttendanceDate, before.latestClassDate)
        assertEquals(AttendanceCheckState.Checked, before.rows.single().checkState)

        // When: the current date changes to Friday
        currentDates.value = otherDay
        advanceUntilIdle()

        // Then: Friday becomes the pending class; attendance history is preserved
        val after =
            assertIs<AttendanceUiState.Content>(
                viewModel.uiState.value,
            )
        val row = after.rows.single()

        assertEquals(otherDay, after.currentDate)
        assertEquals(otherDay, after.latestClassDate)
        assertEquals(AttendanceCheckState.Pending, row.checkState)
        assertEquals(student.id, row.registeredStudent.student.id)
        assertEquals(setOf(nextAttendanceDate), row.registeredStudent.studentPackage.attendanceDates)
        assertEquals(1, row.registeredStudent.studentPackage.usedClasses)
        assertEquals(before.attendance, after.attendance)
    }

    @Test
    fun `loads student groups into content state`() = runViewModelTest {
        // Given: the repository exposes only Group B
        val expectedGroups =
            studentGroups.filter {
                it.id == DefaultStudentGroups.GROUP_B_ID
            }

        val viewModel = createViewModel(
            studentRepository = FakeStudentRepository(),
            studentPackageRepository = FakeStudentPackageRepository(),
            studentGroups = expectedGroups,
        )

        observeUiState(viewModel)

        // When
        advanceUntilIdle()

        // Then
        val content =
            assertIs<AttendanceUiState.Content>(
                viewModel.uiState.value,
            )

        assertEquals(expectedGroups, content.studentGroups)
    }
}
