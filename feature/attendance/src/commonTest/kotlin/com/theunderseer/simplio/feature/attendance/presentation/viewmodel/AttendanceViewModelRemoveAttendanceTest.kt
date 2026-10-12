package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.domain.usecase.RemoveStudentAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeTransactionRunner
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.RemoveStudentAttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.viewmodel.AttendanceViewModelTestData.attendanceDate
import com.theunderseer.simplio.feature.attendance.presentation.viewmodel.AttendanceViewModelTestData.duoSpecialPackage
import com.theunderseer.simplio.feature.attendance.presentation.viewmodel.AttendanceViewModelTestData.firstAttendance
import com.theunderseer.simplio.feature.attendance.presentation.viewmodel.AttendanceViewModelTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.presentation.viewmodel.AttendanceViewModelTestData.today
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelRemoveAttendanceTest {
    @Test
    fun `removes student attendance and refreshes row check state`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val student = studentRepository.seed(testStudent(id = 7))
        val studentPackage =
            studentPackageRepository.seed(
                studentId = student.id,
                packageType = fourClassPackage,
                startedOn = firstAttendance,
                attendanceDates = setOf(firstAttendance, attendanceDate),
            )
        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        )

        observeUiState(viewModel)
        advanceUntilIdle()

        val before = assertIs<AttendanceUiState.Content>(viewModel.uiState.value)

        assertEquals(attendanceDate, before.latestClassDate)
        assertEquals(AttendanceCheckState.Checked, before.rows.single().checkState)
        assertEquals(2, before.rows.single().registeredStudent.studentPackage.usedClasses)

        // When
        viewModel.removeStudentAttendance(
            studentId = student.id,
            attendanceDate = attendanceDate,
        )
        advanceUntilIdle()

        // Then
        val after = assertIs<AttendanceUiState.Content>(viewModel.uiState.value)
        val expectedPackage =
            studentPackage.copy(
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate = mapOf(firstAttendance to 1),
                ),
            )
        val expectedAttendance =
            Attendance(
                entries = listOf(
                    RegisteredStudent(
                        student = student,
                        studentPackage = expectedPackage,
                    ),
                ),
            )
        val row = after.rows.single()

        assertEquals(expectedAttendance, after.attendance)
        assertEquals(expectedAttendance.entries.single(), row.registeredStudent)
        assertEquals(1, row.registeredStudent.studentPackage.usedClasses)
        assertEquals(AttendanceCheckState.Pending, row.checkState)
        assertEquals(listOf(fourClassPackage), after.packageTypes)
        assertEquals(today, after.currentDate)
        assertEquals(attendanceDate, after.latestClassDate)
        assertEquals(
            expectedPackage,
            studentPackageRepository.getLatestPackageForStudent(student.id),
        )
    }

    @Test
    fun `keeps attendance content when first attendance removal is rejected`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val student = studentRepository.seed(testStudent(id = 7))
        val studentPackage =
            studentPackageRepository.seed(
                studentId = student.id,
                packageType = fourClassPackage,
                startedOn = firstAttendance,
            )
        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
            currentDates = flowOf(firstAttendance),
        )

        observeUiState(viewModel)
        advanceUntilIdle()
        val before =
            assertIs<AttendanceUiState.Content>(viewModel.uiState.value)

        // When
        viewModel.removeStudentAttendance(
            studentId = student.id,
            attendanceDate = firstAttendance,
        )
        advanceUntilIdle()

        // Then: the table remains available with an operation error
        val after =
            assertIs<AttendanceUiState.Content>(viewModel.uiState.value)

        assertEquals(
            before.copy(
                removeStudentAttendanceState =
                RemoveStudentAttendanceUiState.Error(
                    message = "The first attendance cannot be removed",
                ),
            ),
            after,
        )

        // And: the stored package remains unchanged
        assertEquals(
            studentPackage,
            studentPackageRepository.getLatestPackageForStudent(student.id),
        )
    }

    @Test
    fun `ignores repeated removal requests while saving and restores all visits for removed date`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val student = studentRepository.seed(testStudent(id = 7, fullName = "Anna and John"))

        val studentPackage = studentPackageRepository.create(
            studentId = student.id,
            packageType = duoSpecialPackage,
            startedOn = firstAttendance,
            visitCount = 2,
        )

        studentPackageRepository.recordAttendance(
            packageId = studentPackage.id,
            attendedOn = attendanceDate,
            visitCount = 2,
        )

        val allowRemoval = CompletableDeferred<Unit>()
        var removalCalls = 0

        val delayedRepository =
            object : StudentPackageRepository by studentPackageRepository {
                override suspend fun removeAttendance(packageId: Int, attendedOn: LocalDate) {
                    removalCalls++
                    allowRemoval.await()

                    studentPackageRepository.removeAttendance(
                        packageId = packageId,
                        attendedOn = attendedOn,
                    )
                }
            }

        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
            packageTypes = listOf(duoSpecialPackage),
            removeStudentAttendanceUseCase = RemoveStudentAttendanceUseCase(
                repository = delayedRepository,
                transactionRunner = FakeTransactionRunner(),
            ),
        )

        observeUiState(viewModel)
        advanceUntilIdle()

        val before =
            assertIs<AttendanceUiState.Content>(viewModel.uiState.value)

        assertEquals(
            4,
            before.attendance.entries.single().studentPackage.remainingClasses,
        )

        // When: removal starts and remains suspended
        viewModel.removeStudentAttendance(
            studentId = student.id,
            attendanceDate = attendanceDate,
        )
        runCurrent()

        val savingState =
            assertIs<AttendanceUiState.Content>(viewModel.uiState.value)

        // Then: attendance is preserved while saving
        assertEquals(
            before.copy(
                removeStudentAttendanceState = RemoveStudentAttendanceUiState.Saving,
            ),
            savingState,
        )
        assertEquals(1, removalCalls)

        // When: another request and state clearing are attempted
        viewModel.removeStudentAttendance(
            studentId = student.id,
            attendanceDate = attendanceDate,
        )
        viewModel.clearRemoveStudentAttendanceState()
        runCurrent()

        // Then: no second removal starts
        assertEquals(1, removalCalls)
        assertEquals(savingState, viewModel.uiState.value)

        // When: removal finishes
        allowRemoval.complete(Unit)
        advanceUntilIdle()

        // Then: the date is removed and both visits are restored
        val savedState =
            assertIs<AttendanceUiState.Content>(viewModel.uiState.value)
        val updatedPackage =
            savedState.attendance.entries.single().studentPackage

        assertEquals(
            RemoveStudentAttendanceUiState.Success,
            savedState.removeStudentAttendanceState,
        )
        assertEquals(
            mapOf(firstAttendance to 2),
            updatedPackage.attendanceHistory.visitsByDate,
        )
        assertEquals(2, updatedPackage.usedClasses)
        assertEquals(6, updatedPackage.remainingClasses)
        assertEquals(
            updatedPackage,
            studentPackageRepository.getLatestPackageForStudent(student.id),
        )
        assertEquals(1, removalCalls)

        // When: the handled result is cleared
        viewModel.clearRemoveStudentAttendanceState()
        advanceUntilIdle()

        // Then: only the operation state changes
        assertEquals(
            savedState.copy(
                removeStudentAttendanceState = RemoveStudentAttendanceUiState.Idle,
            ),
            viewModel.uiState.value,
        )
    }
}
