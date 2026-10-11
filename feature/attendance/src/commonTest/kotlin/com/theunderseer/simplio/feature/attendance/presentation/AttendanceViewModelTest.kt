package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.PackagePlan
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.usecase.DeleteStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetPackageTypesUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.MarkStudentPresentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RemoveStudentAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelTest {
    private val student = testStudent()
    private val today = LocalDate(2026, 10, 8)
    private val firstAttendance = LocalDate(2026, 10, 2)
    private val attendanceDate = LocalDate(2026, 10, 5)
    private val registeredOn = LocalDate(2026, 10, 3)
    private val fourClassPackage = testClassPackage()
    private val fiveClassPackage = testClassPackage(
        id = 2,
        plan = PackagePlan.FIVE_CLASSES,
    )
    private val availablePackages = listOf(
        fourClassPackage,
        fiveClassPackage,
    )
    private val duoPackage = testClassPackage(
        id = 3,
        plan = PackagePlan.DUO_SPECIAL,
    )

    @Test
    fun `loads attendance into content state`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()

        val studentPackageRepository = FakeStudentPackageRepository()

        val student = studentRepository.seed(student)

        val studentPackage = studentPackageRepository.seed(
            studentId = student.id,
            packageType = fourClassPackage,
            startedOn = firstAttendance,
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
                currentDate = today,
                latestClassDate = attendanceDate,
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
            "Anna Smith",
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

    @Test
    fun `marks student present and refreshes attendance`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()

        val student = studentRepository.seed(student)

        studentPackageRepository.seed(
            studentId = student.id,
            packageType = fourClassPackage,
            startedOn = firstAttendance,
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
            attendanceDate = attendanceDate,
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
                firstAttendance,
                attendanceDate,
            ),
            registeredStudent.studentPackage.attendanceDates,
        )

        assertEquals(
            2,
            registeredStudent.studentPackage.usedClasses,
        )
    }

    @Test
    fun `recalculates attendance check state when current date changes`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val otherDay = LocalDate(2026, 10, 9)
        val currentDates = MutableStateFlow(today)

        val student = studentRepository.seed(student)

        studentPackageRepository.seed(
            studentId = student.id,
            packageType = fourClassPackage,
            startedOn = attendanceDate,
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

        assertEquals(today, before.currentDate)
        assertEquals(attendanceDate, before.latestClassDate)
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
        assertEquals(setOf(attendanceDate), row.registeredStudent.studentPackage.attendanceDates)
        assertEquals(1, row.registeredStudent.studentPackage.usedClasses)
        assertEquals(before.attendance, after.attendance)
    }

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
    fun `registers duo student with two visits and refreshes attendance`() = runViewModelTest {
        // Given
        val duoStudent = testStudent(fullName = "Anna & John")
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
            packageTypes = listOf(duoPackage),
        )

        observeUiState(viewModel)
        advanceUntilIdle()

        // When
        viewModel.registerStudent(
            fullName = duoStudent.fullName,
            groupId = duoStudent.groupId,
            packageType = duoPackage,
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
        assertEquals(duoPackage, studentPackage.packageType)
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
    fun `records two visits for duo attendance and refreshes content`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val student = studentRepository.seed(testStudent(id = 7, fullName = "Anna & John"))

        studentPackageRepository.create(
            studentId = student.id,
            packageType = duoPackage,
            startedOn = firstAttendance,
            visitCount = 2,
        )

        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
            packageTypes = listOf(duoPackage),
            currentDates = flowOf(attendanceDate),
        )

        observeUiState(viewModel)
        advanceUntilIdle()

        // When
        viewModel.markStudentPresent(
            studentId = student.id,
            attendanceDate = attendanceDate,
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
                firstAttendance to 2,
                attendanceDate to 2,
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

        val student = studentRepository.seed(
            testStudent(id = 7, fullName = "Anna & John"),
        )

        val studentPackage =
            studentPackageRepository.create(
                studentId = student.id,
                packageType = duoPackage,
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
            packageTypes = listOf(duoPackage),
            currentDates = flowOf(attendanceDate),
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
            attendanceDate = attendanceDate,
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

        val student = studentRepository.seed(testStudent(id = 7))

        val storedPackage =
            studentPackageRepository.seed(
                studentId = student.id,
                packageType = fourClassPackage,
                startedOn = firstAttendance,
            )

        val viewModel = createViewModel(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
            currentDates = flowOf(attendanceDate),
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
            attendanceDate = attendanceDate,
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
        val student = studentRepository.seed(testStudent(id = 7))

        studentPackageRepository.seed(
            studentId = student.id,
            packageType = fourClassPackage,
            startedOn = firstAttendance,
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
                transactionRunner = transactionRunner,
            ),
        )

        observeUiState(viewModel)
        advanceUntilIdle()

        // When: saving starts and remains suspended
        viewModel.markStudentPresent(
            studentId = student.id,
            attendanceDate = attendanceDate,
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
            attendanceDate = attendanceDate,
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
                firstAttendance to 1,
                attendanceDate to 1,
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

    @Test
    fun `ignores repeated removal requests while saving and restores all visits for removed date`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val student = studentRepository.seed(testStudent(id = 7, fullName = "Anna and John"))

        val studentPackage = studentPackageRepository.create(
            studentId = student.id,
            packageType = duoPackage,
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
            packageTypes = listOf(duoPackage),
            removeStudentAttendanceUseCase = RemoveStudentAttendanceUseCase(
                repository = delayedRepository,
                transactionRunner = transactionRunner,
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

    private fun createGetPackageTypesUseCase(packageTypes: List<PackageType>): GetPackageTypesUseCase =
        GetPackageTypesUseCase(
            packageTypeRepository = object : PackageTypeRepository {
                override suspend fun getAll(): List<PackageType> = packageTypes

                override suspend fun getById(id: Int): PackageType? = packageTypes.firstOrNull {
                    it.id == id
                }
            },
        )

    private fun createRegisterStudentUseCase(
        studentRepository: FakeStudentRepository,
        studentPackageRepository: FakeStudentPackageRepository,
    ): RegisterStudentUseCase = RegisterStudentUseCase(
        studentRepository = studentRepository,
        studentPackageRepository = studentPackageRepository,
        transactionRunner = transactionRunner,
    )

    private fun createMarkStudentPresentUseCase(
        studentPackageRepository: FakeStudentPackageRepository,
    ): MarkStudentPresentUseCase = MarkStudentPresentUseCase(
        repository = studentPackageRepository,
        transactionRunner = transactionRunner,
    )

    private fun createRemoveStudentAttendanceUseCase(
        studentPackageRepository: FakeStudentPackageRepository,
    ): RemoveStudentAttendanceUseCase = RemoveStudentAttendanceUseCase(
        repository = studentPackageRepository,
        transactionRunner = transactionRunner,
    )

    private fun TestScope.observeUiState(viewModel: AttendanceViewModel) {
        backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler),
        ) {
            viewModel.uiState.collect {}
        }
    }

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) {
        val testScope = TestScope()
        val mainDispatcher =
            StandardTestDispatcher(
                scheduler = testScope.testScheduler,
            )

        Dispatchers.setMain(
            mainDispatcher,
        )

        try {
            testScope.runTest {
                block()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private val transactionRunner = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    private fun createViewModel(
        studentRepository: FakeStudentRepository,
        studentPackageRepository: FakeStudentPackageRepository,
        packageTypes: List<PackageType> = listOf(fourClassPackage),
        currentDates: Flow<LocalDate> = flowOf(today),
        markStudentPresentUseCase: MarkStudentPresentUseCase =
            createMarkStudentPresentUseCase(
                studentPackageRepository = studentPackageRepository,
            ),
        removeStudentAttendanceUseCase: RemoveStudentAttendanceUseCase =
            createRemoveStudentAttendanceUseCase(
                studentPackageRepository = studentPackageRepository,
            ),
    ): AttendanceViewModel = AttendanceViewModel(
        getAttendanceUseCase = GetAttendanceUseCase(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        ),
        getPackageTypesUseCase = createGetPackageTypesUseCase(
            packageTypes = packageTypes,
        ),
        registerStudentUseCase = createRegisterStudentUseCase(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        ),
        markStudentPresentUseCase = markStudentPresentUseCase,
        removeStudentAttendanceUseCase = removeStudentAttendanceUseCase,
        deleteStudentUseCase = DeleteStudentUseCase(
            studentRepository = studentRepository,
        ),
        currentDates = currentDates,
    )
}
