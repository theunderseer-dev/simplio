package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.viewmodel.AttendanceViewModelTestData.attendanceDate
import com.theunderseer.simplio.feature.attendance.presentation.viewmodel.AttendanceViewModelTestData.duoSpecialPackage
import com.theunderseer.simplio.feature.attendance.presentation.viewmodel.AttendanceViewModelTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.presentation.viewmodel.AttendanceViewModelTestData.registeredOn
import com.theunderseer.simplio.feature.attendance.presentation.viewmodel.AttendanceViewModelTestData.student
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelStudentManagementTest {
    @Test
    fun `registers student and refreshes attendance`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val packageTypes = listOf(fourClassPackage)
        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        )

        observeUiState(viewModel)

        advanceUntilIdle()

        // When
        viewModel.registerStudent(
            fullName = student.fullName,
            groupId = student.groupId,
            packageType = fourClassPackage,
            registeredOn = registeredOn,
        )

        advanceUntilIdle()

        // Then
        val state =
            assertIs<AttendanceUiState.Content>(
                viewModel.uiState.value,
            )

        assertEquals(
            packageTypes,
            state.packageTypes,
        )

        assertEquals(
            1,
            state.attendance.entries.size,
        )

        val registeredStudent = state.attendance.entries.single()

        assertEquals(
            student.fullName,
            registeredStudent.student.fullName,
        )

        assertEquals(
            fourClassPackage,
            registeredStudent.studentPackage.packageType,
        )

        assertEquals(
            registeredOn,
            registeredStudent.studentPackage.startedOn,
        )

        assertEquals(
            setOf(registeredOn),
            registeredStudent.studentPackage.attendanceDates,
        )
    }

    @Test
    fun `registers duo student with two visits and refreshes attendance`() = runViewModelTest {
        // Given
        val duoStudent = testStudent(fullName = "Anna & John")
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
            packageTypes = listOf(duoSpecialPackage),
        )

        observeUiState(viewModel)
        advanceUntilIdle()

        // When
        viewModel.registerStudent(
            fullName = duoStudent.fullName,
            groupId = duoStudent.groupId,
            packageType = duoSpecialPackage,
            registeredOn = registeredOn,
            visitCount = 2,
        )

        advanceUntilIdle()

        // Then
        val state =
            assertIs<AttendanceUiState.Content>(
                viewModel.uiState.value,
            )

        val registeredStudent = state.attendance.entries.single()
        val studentPackage = registeredStudent.studentPackage

        assertEquals(duoStudent.fullName, registeredStudent.student.fullName)
        assertEquals(duoSpecialPackage, studentPackage.packageType)
        assertEquals(registeredOn, studentPackage.startedOn)

        assertEquals(
            mapOf(registeredOn to 2),
            studentPackage.attendanceHistory.visitsByDate,
        )

        assertEquals(2, studentPackage.usedClasses)
        assertEquals(6, studentPackage.remainingClasses)

        assertEquals(
            studentPackage,
            studentPackageRepository.getLatestPackageForStudent(
                registeredStudent.student.id,
            ),
        )
    }

    @Test
    fun `deletes student and refreshes attendance`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()

        val studentPackageRepository = FakeStudentPackageRepository()

        val student = studentRepository.seed(student)

        studentPackageRepository.seed(
            studentId = student.id,
            packageType = fourClassPackage,
            startedOn = attendanceDate,
        )

        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        )

        observeUiState(viewModel)

        advanceUntilIdle()

        // When
        viewModel.deleteStudent(
            studentId = student.id,
        )

        advanceUntilIdle()

        // Then
        val state =
            assertIs<AttendanceUiState.Content>(
                viewModel.uiState.value,
            )

        assertEquals(
            emptyList(),
            state.attendance.entries,
        )
    }
}
