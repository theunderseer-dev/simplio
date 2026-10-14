package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.domain.usecase.MarkStudentPresentUseCase
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeTransactionRunner
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.duoSpecialPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.student
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.MarkStudentPresentUiState
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
class AttendanceViewModelMarkStudentPresentTest {
    @Test
    fun `marks student present and refreshes attendance`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val student = studentRepository.seed(student)

        studentPackageRepository.seed(
            studentId = student.id,
            packageType = fourClassPackage,
            startedOn = firstClassDate,
        )

        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        )

        observeUiState(viewModel)

        advanceUntilIdle()

        // When
        viewModel.markStudentPresent(
            studentId = student.id,
            attendanceDate = secondClassDate,
        )

        advanceUntilIdle()

        // Then
        val state =
            assertIs<AttendanceUiState.Content>(
                viewModel.uiState.value,
            )

        assertEquals(
            MarkStudentPresentUiState.Success,
            state.markStudentPresentState,
        )

        val registeredStudent =
            state.attendance.entries.single()

        assertEquals(
            setOf(
                firstClassDate,
                secondClassDate,
            ),
            registeredStudent.studentPackage.attendanceDates,
        )

        assertEquals(
            2,
            registeredStudent.studentPackage.usedClasses,
        )
    }

    @Test
    fun `records two visits for duo attendance and refreshes content`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val student = studentRepository.seed(testStudent(id = 7))

        studentPackageRepository.create(
            studentId = student.id,
            packageType = duoSpecialPackage,
            startedOn = firstClassDate,
            visitCount = 2,
        )

        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
            packageTypes = listOf(duoSpecialPackage),
            currentDates = flowOf(secondClassDate),
        )

        observeUiState(viewModel)
        advanceUntilIdle()

        // When
        viewModel.markStudentPresent(
            studentId = student.id,
            attendanceDate = secondClassDate,
            visitCount = 2,
        )

        advanceUntilIdle()

        // Then
        val state =
            assertIs<AttendanceUiState.Content>(
                viewModel.uiState.value,
            )
        val row = state.rows.single()
        val updatedPackage = row.registeredStudent.studentPackage

        assertEquals(student, row.registeredStudent.student)
        assertEquals(
            mapOf(
                firstClassDate to 2,
                secondClassDate to 2,
            ),
            updatedPackage.attendanceHistory.visitsByDate,
        )
        assertEquals(4, updatedPackage.usedClasses)
        assertEquals(4, updatedPackage.remainingClasses)
        assertEquals(AttendanceCheckState.Checked, row.checkState)
        assertEquals(
            updatedPackage,
            studentPackageRepository.getLatestPackageForStudent(student.id),
        )

        assertEquals(
            MarkStudentPresentUiState.Success,
            state.markStudentPresentState,
        )
    }

    @Test
    fun `reports insufficient remaining visits and preserves stored package`() = runViewModelTest {
        // Given
        val firstAttendance = LocalDate(2026, 9, 21)
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val student = studentRepository.seed(student)

        val studentPackage =
            studentPackageRepository.create(
                studentId = student.id,
                packageType = duoSpecialPackage,
                startedOn = firstAttendance,
                visitCount = 2,
            )

        mapOf(
            LocalDate(2026, 9, 25) to 2,
            LocalDate(2026, 9, 28) to 2,
            LocalDate(2026, 10, 2) to 1,
        ).forEach { (date, visitCount) ->
            studentPackageRepository.recordAttendance(
                packageId = studentPackage.id,
                attendedOn = date,
                visitCount = visitCount,
            )
        }

        val storedPackageBefore =
            studentPackageRepository.getLatestPackageForStudent(student.id)

        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
            packageTypes = listOf(duoSpecialPackage),
            currentDates = flowOf(secondClassDate),
        )

        observeUiState(viewModel)
        advanceUntilIdle()

        val before =
            assertIs<AttendanceUiState.Content>(
                viewModel.uiState.value,
            )

        assertEquals(
            1,
            before.attendance.entries.single().studentPackage.remainingClasses,
        )

        // When
        viewModel.markStudentPresent(
            studentId = student.id,
            attendanceDate = secondClassDate,
            visitCount = 2,
        )

        advanceUntilIdle()

        // Then: the error is reported inside content; table data is preserved
        val after =
            assertIs<AttendanceUiState.Content>(
                viewModel.uiState.value,
            )

        assertEquals(
            before.copy(
                markStudentPresentState =
                MarkStudentPresentUiState.Error(
                    message = "Not enough visits remaining",
                ),
            ),
            after,
        )

        assertEquals(
            storedPackageBefore,
            studentPackageRepository.getLatestPackageForStudent(student.id),
        )
    }

    @Test
    fun `reports invalid visit count and preserves stored package`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val student = studentRepository.seed(student)
        val storedPackage =
            studentPackageRepository.seed(
                studentId = student.id,
                packageType = fourClassPackage,
                startedOn = firstClassDate,
            )

        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
            currentDates = flowOf(secondClassDate),
        )

        observeUiState(viewModel)
        advanceUntilIdle()

        val before =
            assertIs<AttendanceUiState.Content>(
                viewModel.uiState.value,
            )

        assertEquals(3, storedPackage.remainingClasses)

        // When
        viewModel.markStudentPresent(
            studentId = student.id,
            attendanceDate = secondClassDate,
            visitCount = 2,
        )

        advanceUntilIdle()

        // Then: the error is reported inside content; table data is preserved
        val after =
            assertIs<AttendanceUiState.Content>(
                viewModel.uiState.value,
            )

        assertEquals(
            before.copy(
                markStudentPresentState =
                MarkStudentPresentUiState.Error(
                    message = "Visit count is not allowed for this package",
                ),
            ),
            after,
        )

        assertEquals(
            storedPackage,
            studentPackageRepository.getLatestPackageForStudent(student.id),
        )

        assertEquals(
            emptyList(),
            studentPackageRepository.recordedAttendances,
        )

        // When: the error is cleared
        viewModel.clearMarkStudentPresentState()
        advanceUntilIdle()

        // Then: the original content is restored without changing attendance
        assertEquals(before, viewModel.uiState.value)
    }

    @Test
    fun `ignores repeated mark requests and state clearing while saving`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val student = studentRepository.seed(student)

        studentPackageRepository.seed(
            studentId = student.id,
            packageType = fourClassPackage,
            startedOn = firstClassDate,
        )

        val allowSaving = CompletableDeferred<Unit>()
        var recordCalls = 0

        val delayedRepository =
            object : StudentPackageRepository by studentPackageRepository {
                override suspend fun recordAttendance(packageId: Int, attendedOn: LocalDate, visitCount: Int) {
                    recordCalls++
                    allowSaving.await()

                    studentPackageRepository.recordAttendance(
                        packageId = packageId,
                        attendedOn = attendedOn,
                        visitCount = visitCount,
                    )
                }
            }

        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
            markStudentPresentUseCase = MarkStudentPresentUseCase(
                repository = delayedRepository,
                transactionRunner = FakeTransactionRunner(),
            ),
        )

        observeUiState(viewModel)
        advanceUntilIdle()

        // When: saving starts and remains suspended
        viewModel.markStudentPresent(
            studentId = student.id,
            attendanceDate = secondClassDate,
        )
        runCurrent()

        val savingState =
            assertIs<AttendanceUiState.Content>(viewModel.uiState.value)

        assertEquals(
            MarkStudentPresentUiState.Saving,
            savingState.markStudentPresentState,
        )
        assertEquals(1, recordCalls)

        // When: another request and state clearing are attempted
        viewModel.markStudentPresent(
            studentId = student.id,
            attendanceDate = secondClassDate,
        )
        viewModel.clearMarkStudentPresentState()
        runCurrent()

        // Then: no additional write starts; saving remains active
        assertEquals(1, recordCalls)
        assertEquals(savingState, viewModel.uiState.value)

        // When: saving finishes
        allowSaving.complete(Unit)
        advanceUntilIdle()

        // Then: updated attendance and success are exposed together
        val savedState =
            assertIs<AttendanceUiState.Content>(viewModel.uiState.value)

        assertEquals(
            MarkStudentPresentUiState.Success,
            savedState.markStudentPresentState,
        )
        assertEquals(
            mapOf(
                firstClassDate to 1,
                secondClassDate to 1,
            ),
            savedState.attendance.entries.single()
                .studentPackage.attendanceHistory.visitsByDate,
        )
        assertEquals(1, recordCalls)

        // When: the handled result is cleared
        viewModel.clearMarkStudentPresentState()
        advanceUntilIdle()

        // Then: only the operation state changes
        assertEquals(
            savedState.copy(
                markStudentPresentState = MarkStudentPresentUiState.Idle,
            ),
            viewModel.uiState.value,
        )
    }
}
