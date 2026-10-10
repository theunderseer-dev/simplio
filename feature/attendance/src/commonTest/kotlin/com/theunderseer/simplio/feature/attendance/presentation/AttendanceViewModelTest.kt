package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val today = LocalDate(2026, 10, 8)

    private val singlePackage = testFourClassPackage()
    private val duoPackage =
        PackageType(
            id = 3,
            name = "Duo 8 · Special rate",
            totalClasses = 8,
            validityWeeks = 4,
            price = 2400,
            maxVisitsPerAttendance = 2,
        )

    @Test
    fun `loads attendance into content state`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()

        val studentPackageRepository = FakeStudentPackageRepository()

        val student = studentRepository.seed(
            Student(
                id = 1,
                fullName = "Anna Smith",
            ),
        )

        val packageTypes =
            listOf(
                singlePackage,
                testFiveClassPackage(id = 2),
            )

        val studentPackage = studentPackageRepository.seed(
            studentId = student.id,
            packageType = singlePackage,
            startedOn = LocalDate(2026, 10, 2),
        )

        val getAttendance = GetAttendanceUseCase(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        )

        val getPackageTypes = createGetPackageTypesUseCase(
            packageTypes = packageTypes,
        )

        val deleteStudentUseCase = DeleteStudentUseCase(
            studentRepository = studentRepository,
        )

        val registerStudent = createRegisterStudentUseCase(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
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
        val viewModel = AttendanceViewModel(
            getAttendanceUseCase = getAttendance,
            getPackageTypesUseCase = getPackageTypes,
            registerStudentUseCase = registerStudent,
            markStudentPresentUseCase =
            createMarkStudentPresentUseCase(
                studentPackageRepository = studentPackageRepository,
            ),
            removeStudentAttendanceUseCase =
            createRemoveStudentAttendanceUseCase(
                studentPackageRepository = studentPackageRepository,
            ),
            deleteStudentUseCase = deleteStudentUseCase,
            currentDates = flowOf(today),
        )

        observeUiState(viewModel)

        advanceUntilIdle()

        // Then
        assertEquals(
            AttendanceUiState.Content(
                attendance = expectedAttendance,
                packageTypes = packageTypes,
                currentDate = today,
                latestClassDate = LocalDate(2026, 10, 5),
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

        val student = studentRepository.seed(
            Student(
                id = 1,
                fullName = "Anna Smith",
            ),
        )

        val packageTypes =
            listOf(
                singlePackage,
                testFiveClassPackage(id = 2),
            )

        val getAttendance = GetAttendanceUseCase(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        )

        val getPackageTypes = createGetPackageTypesUseCase(
            packageTypes = packageTypes,
        )

        val registerStudent = createRegisterStudentUseCase(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        )

        val deleteStudentUseCase = DeleteStudentUseCase(
            studentRepository = studentRepository,
        )

        // When
        val viewModel = AttendanceViewModel(
            getAttendanceUseCase = getAttendance,
            getPackageTypesUseCase = getPackageTypes,
            registerStudentUseCase = registerStudent,
            markStudentPresentUseCase =
            createMarkStudentPresentUseCase(
                studentPackageRepository = studentPackageRepository,
            ),
            removeStudentAttendanceUseCase =
            createRemoveStudentAttendanceUseCase(
                studentPackageRepository = studentPackageRepository,
            ),
            deleteStudentUseCase = deleteStudentUseCase,
            currentDates = flowOf(today),
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

        val packageTypes =
            listOf(
                singlePackage,
                testFiveClassPackage(id = 2),
            )

        val getAttendance = GetAttendanceUseCase(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        )

        val getPackageTypes = createGetPackageTypesUseCase(
            packageTypes = packageTypes,
        )

        val registerStudent = createRegisterStudentUseCase(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        )

        val deleteStudentUseCase =
            DeleteStudentUseCase(
                studentRepository = studentRepository,
            )

        // When
        val viewModel = AttendanceViewModel(
            getAttendanceUseCase = getAttendance,
            getPackageTypesUseCase = getPackageTypes,
            registerStudentUseCase = registerStudent,
            markStudentPresentUseCase =
            createMarkStudentPresentUseCase(
                studentPackageRepository = studentPackageRepository,
            ),
            removeStudentAttendanceUseCase =
            createRemoveStudentAttendanceUseCase(
                studentPackageRepository = studentPackageRepository,
            ),
            deleteStudentUseCase = deleteStudentUseCase,
            currentDates = flowOf(today),
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

        val packageTypes = listOf(singlePackage)

        val getAttendance = GetAttendanceUseCase(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        )

        val getPackageTypes = createGetPackageTypesUseCase(
            packageTypes = packageTypes,
        )

        val registerStudent = createRegisterStudentUseCase(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        )

        val deleteStudentUseCase =
            DeleteStudentUseCase(
                studentRepository = studentRepository,
            )

        val registeredOn = LocalDate(
            2026,
            10,
            3,
        )

        val viewModel = AttendanceViewModel(
            getAttendanceUseCase = getAttendance,
            getPackageTypesUseCase = getPackageTypes,
            registerStudentUseCase = registerStudent,
            markStudentPresentUseCase =
            createMarkStudentPresentUseCase(
                studentPackageRepository = studentPackageRepository,
            ),
            removeStudentAttendanceUseCase =
            createRemoveStudentAttendanceUseCase(
                studentPackageRepository = studentPackageRepository,
            ),
            deleteStudentUseCase = deleteStudentUseCase,
            currentDates = flowOf(today),
        )

        observeUiState(viewModel)

        advanceUntilIdle()

        // When
        viewModel.registerStudent(
            fullName = "Anna Smith",
            packageType = singlePackage,
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
            singlePackage,
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

        val student = studentRepository.seed(
            Student(
                id = 1,
                fullName = "Anna Smith",
            ),
        )

        studentPackageRepository.seed(
            studentId = student.id,
            packageType = singlePackage,
            startedOn = LocalDate(2026, 10, 5),
        )

        val getAttendance = GetAttendanceUseCase(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        )

        val getPackageTypes = createGetPackageTypesUseCase(
            packageTypes = listOf(singlePackage),
        )

        val deleteStudentUseCase =
            DeleteStudentUseCase(
                studentRepository = studentRepository,
            )

        val viewModel = AttendanceViewModel(
            getAttendanceUseCase = getAttendance,
            getPackageTypesUseCase = getPackageTypes,
            registerStudentUseCase = createRegisterStudentUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            ),
            markStudentPresentUseCase =
            createMarkStudentPresentUseCase(
                studentPackageRepository = studentPackageRepository,
            ),
            removeStudentAttendanceUseCase =
            createRemoveStudentAttendanceUseCase(
                studentPackageRepository = studentPackageRepository,
            ),
            deleteStudentUseCase = deleteStudentUseCase,
            currentDates = flowOf(today),
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

        val student =
            studentRepository.seed(
                Student(
                    id = 1,
                    fullName = "Anna Smith",
                ),
            )

        studentPackageRepository.seed(
            studentId = student.id,
            packageType = singlePackage,
            startedOn = LocalDate(2026, 10, 2),
        )

        val getAttendance =
            GetAttendanceUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            )

        val getPackageTypes =
            createGetPackageTypesUseCase(
                packageTypes = listOf(singlePackage),
            )

        val registerStudent =
            createRegisterStudentUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            )

        val markStudentPresent =
            createMarkStudentPresentUseCase(
                studentPackageRepository = studentPackageRepository,
            )

        val deleteStudent =
            DeleteStudentUseCase(
                studentRepository = studentRepository,
            )

        val viewModel =
            AttendanceViewModel(
                getAttendanceUseCase = getAttendance,
                getPackageTypesUseCase = getPackageTypes,
                registerStudentUseCase = registerStudent,
                markStudentPresentUseCase = markStudentPresent,
                removeStudentAttendanceUseCase =
                createRemoveStudentAttendanceUseCase(
                    studentPackageRepository = studentPackageRepository,
                ),
                deleteStudentUseCase = deleteStudent,
                currentDates = flowOf(today),
            )

        observeUiState(viewModel)

        advanceUntilIdle()

        val attendanceDate = LocalDate(2026, 10, 5)

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
                LocalDate(2026, 10, 2),
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
        val recordedDate = LocalDate(2026, 10, 5)
        val thursday = LocalDate(2026, 10, 8)
        val friday = LocalDate(2026, 10, 9)
        val currentDates = MutableStateFlow(thursday)

        val student =
            studentRepository.seed(
                Student(
                    id = 1,
                    fullName = "Anna Smith",
                ),
            )

        studentPackageRepository.seed(
            studentId = student.id,
            packageType = singlePackage,
            startedOn = recordedDate,
        )

        val viewModel =
            AttendanceViewModel(
                getAttendanceUseCase =
                GetAttendanceUseCase(
                    studentRepository = studentRepository,
                    studentPackageRepository = studentPackageRepository,
                ),
                getPackageTypesUseCase =
                createGetPackageTypesUseCase(
                    packageTypes = listOf(singlePackage),
                ),
                registerStudentUseCase =
                createRegisterStudentUseCase(
                    studentRepository = studentRepository,
                    studentPackageRepository = studentPackageRepository,
                ),
                markStudentPresentUseCase =
                createMarkStudentPresentUseCase(
                    studentPackageRepository = studentPackageRepository,
                ),
                removeStudentAttendanceUseCase =
                createRemoveStudentAttendanceUseCase(
                    studentPackageRepository = studentPackageRepository,
                ),
                deleteStudentUseCase =
                DeleteStudentUseCase(
                    studentRepository = studentRepository,
                ),
                currentDates = currentDates,
            )

        observeUiState(viewModel)
        advanceUntilIdle()

        // Then: Monday attendance is checked on Thursday
        val before =
            assertIs<AttendanceUiState.Content>(
                viewModel.uiState.value,
            )

        assertEquals(thursday, before.currentDate)
        assertEquals(recordedDate, before.latestClassDate)
        assertEquals(AttendanceCheckState.Checked, before.rows.single().checkState)

        // When: the current date changes to Friday
        currentDates.value = friday
        advanceUntilIdle()

        // Then: Friday becomes the pending class; attendance history is preserved
        val after =
            assertIs<AttendanceUiState.Content>(
                viewModel.uiState.value,
            )
        val row = after.rows.single()

        assertEquals(friday, after.currentDate)
        assertEquals(friday, after.latestClassDate)
        assertEquals(AttendanceCheckState.Pending, row.checkState)
        assertEquals(student.id, row.registeredStudent.student.id)
        assertEquals(setOf(recordedDate), row.registeredStudent.studentPackage.attendanceDates)
        assertEquals(1, row.registeredStudent.studentPackage.usedClasses)
        assertEquals(before.attendance, after.attendance)
    }

    @Test
    fun `removes student attendance and refreshes row check state`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val firstAttendance = LocalDate(2026, 10, 2)
        val attendanceToRemove = LocalDate(2026, 10, 5)
        val student =
            studentRepository.seed(
                Student(
                    id = 7,
                    fullName = "Anna Smith",
                ),
            )
        val studentPackage =
            studentPackageRepository.seed(
                studentId = student.id,
                packageType = singlePackage,
                startedOn = firstAttendance,
                attendanceDates = setOf(firstAttendance, attendanceToRemove),
            )
        val viewModel =
            AttendanceViewModel(
                getAttendanceUseCase =
                GetAttendanceUseCase(
                    studentRepository = studentRepository,
                    studentPackageRepository = studentPackageRepository,
                ),
                getPackageTypesUseCase =
                createGetPackageTypesUseCase(
                    packageTypes = listOf(singlePackage),
                ),
                registerStudentUseCase =
                createRegisterStudentUseCase(
                    studentRepository = studentRepository,
                    studentPackageRepository = studentPackageRepository,
                ),
                markStudentPresentUseCase =
                createMarkStudentPresentUseCase(
                    studentPackageRepository = studentPackageRepository,
                ),
                removeStudentAttendanceUseCase =
                createRemoveStudentAttendanceUseCase(
                    studentPackageRepository = studentPackageRepository,
                ),
                deleteStudentUseCase =
                DeleteStudentUseCase(
                    studentRepository = studentRepository,
                ),
                currentDates = flowOf(today),
            )

        observeUiState(viewModel)
        advanceUntilIdle()

        val before = assertIs<AttendanceUiState.Content>(viewModel.uiState.value)

        assertEquals(attendanceToRemove, before.latestClassDate)
        assertEquals(AttendanceCheckState.Checked, before.rows.single().checkState)
        assertEquals(2, before.rows.single().registeredStudent.studentPackage.usedClasses)

        // When
        viewModel.removeStudentAttendance(
            studentId = student.id,
            attendanceDate = attendanceToRemove,
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
        assertEquals(listOf(singlePackage), after.packageTypes)
        assertEquals(today, after.currentDate)
        assertEquals(attendanceToRemove, after.latestClassDate)
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
        val firstAttendance = LocalDate(2026, 10, 2)
        val student =
            studentRepository.seed(
                Student(
                    id = 7,
                    fullName = "Anna Smith",
                ),
            )
        val studentPackage =
            studentPackageRepository.seed(
                studentId = student.id,
                packageType = singlePackage,
                startedOn = firstAttendance,
            )
        val viewModel =
            AttendanceViewModel(
                getAttendanceUseCase =
                GetAttendanceUseCase(
                    studentRepository = studentRepository,
                    studentPackageRepository = studentPackageRepository,
                ),
                getPackageTypesUseCase =
                createGetPackageTypesUseCase(
                    packageTypes = listOf(singlePackage),
                ),
                registerStudentUseCase =
                createRegisterStudentUseCase(
                    studentRepository = studentRepository,
                    studentPackageRepository = studentPackageRepository,
                ),
                markStudentPresentUseCase =
                createMarkStudentPresentUseCase(
                    studentPackageRepository = studentPackageRepository,
                ),
                removeStudentAttendanceUseCase =
                createRemoveStudentAttendanceUseCase(
                    studentPackageRepository = studentPackageRepository,
                ),
                deleteStudentUseCase =
                DeleteStudentUseCase(
                    studentRepository = studentRepository,
                ),
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
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val registeredOn = LocalDate(2026, 10, 3)

        val viewModel =
            AttendanceViewModel(
                getAttendanceUseCase =
                GetAttendanceUseCase(
                    studentRepository = studentRepository,
                    studentPackageRepository = studentPackageRepository,
                ),
                getPackageTypesUseCase =
                createGetPackageTypesUseCase(
                    packageTypes = listOf(duoPackage),
                ),
                registerStudentUseCase =
                createRegisterStudentUseCase(
                    studentRepository = studentRepository,
                    studentPackageRepository = studentPackageRepository,
                ),
                markStudentPresentUseCase =
                createMarkStudentPresentUseCase(
                    studentPackageRepository = studentPackageRepository,
                ),
                removeStudentAttendanceUseCase =
                createRemoveStudentAttendanceUseCase(
                    studentPackageRepository = studentPackageRepository,
                ),
                deleteStudentUseCase =
                DeleteStudentUseCase(
                    studentRepository = studentRepository,
                ),
                currentDates = flowOf(today),
            )

        observeUiState(viewModel)
        advanceUntilIdle()

        // When
        viewModel.registerStudent(
            fullName = "Anna & John",
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

        assertEquals("Anna & John", registeredStudent.student.fullName)
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
        val firstAttendance = LocalDate(2026, 10, 2)
        val attendanceDate = LocalDate(2026, 10, 5)
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()

        val student =
            studentRepository.seed(
                Student(
                    id = 7,
                    fullName = "Anna & John",
                ),
            )

        studentPackageRepository.create(
            studentId = student.id,
            packageType = duoPackage,
            startedOn = firstAttendance,
            visitCount = 2,
        )

        val viewModel =
            AttendanceViewModel(
                getAttendanceUseCase =
                GetAttendanceUseCase(
                    studentRepository = studentRepository,
                    studentPackageRepository = studentPackageRepository,
                ),
                getPackageTypesUseCase =
                createGetPackageTypesUseCase(
                    packageTypes = listOf(duoPackage),
                ),
                registerStudentUseCase =
                createRegisterStudentUseCase(
                    studentRepository = studentRepository,
                    studentPackageRepository = studentPackageRepository,
                ),
                markStudentPresentUseCase =
                createMarkStudentPresentUseCase(
                    studentPackageRepository = studentPackageRepository,
                ),
                removeStudentAttendanceUseCase =
                createRemoveStudentAttendanceUseCase(
                    studentPackageRepository = studentPackageRepository,
                ),
                deleteStudentUseCase =
                DeleteStudentUseCase(
                    studentRepository = studentRepository,
                ),
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
        val attendanceDate = LocalDate(2026, 10, 5)
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()

        val student =
            studentRepository.seed(
                Student(
                    id = 7,
                    fullName = "Anna & John",
                ),
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

        val viewModel =
            AttendanceViewModel(
                getAttendanceUseCase =
                GetAttendanceUseCase(
                    studentRepository = studentRepository,
                    studentPackageRepository = studentPackageRepository,
                ),
                getPackageTypesUseCase =
                createGetPackageTypesUseCase(
                    packageTypes = listOf(duoPackage),
                ),
                registerStudentUseCase =
                createRegisterStudentUseCase(
                    studentRepository = studentRepository,
                    studentPackageRepository = studentPackageRepository,
                ),
                markStudentPresentUseCase =
                createMarkStudentPresentUseCase(
                    studentPackageRepository = studentPackageRepository,
                ),
                removeStudentAttendanceUseCase =
                createRemoveStudentAttendanceUseCase(
                    studentPackageRepository = studentPackageRepository,
                ),
                deleteStudentUseCase =
                DeleteStudentUseCase(
                    studentRepository = studentRepository,
                ),
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
        val firstAttendance = LocalDate(2026, 10, 2)
        val attendanceDate = LocalDate(2026, 10, 5)
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()

        val student =
            studentRepository.seed(
                Student(
                    id = 7,
                    fullName = "Anna Smith",
                ),
            )

        val storedPackage =
            studentPackageRepository.seed(
                studentId = student.id,
                packageType = singlePackage,
                startedOn = firstAttendance,
            )

        val viewModel =
            AttendanceViewModel(
                getAttendanceUseCase =
                GetAttendanceUseCase(
                    studentRepository = studentRepository,
                    studentPackageRepository = studentPackageRepository,
                ),
                getPackageTypesUseCase =
                createGetPackageTypesUseCase(
                    packageTypes = listOf(singlePackage),
                ),
                registerStudentUseCase =
                createRegisterStudentUseCase(
                    studentRepository = studentRepository,
                    studentPackageRepository = studentPackageRepository,
                ),
                markStudentPresentUseCase =
                createMarkStudentPresentUseCase(
                    studentPackageRepository = studentPackageRepository,
                ),
                removeStudentAttendanceUseCase =
                createRemoveStudentAttendanceUseCase(
                    studentPackageRepository = studentPackageRepository,
                ),
                deleteStudentUseCase =
                DeleteStudentUseCase(
                    studentRepository = studentRepository,
                ),
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
        val firstAttendance = LocalDate(2026, 10, 2)
        val attendanceDate = LocalDate(2026, 10, 5)

        val student = studentRepository.seed(
            Student(
                id = 7,
                fullName = "Anna Smith",
            ),
        )

        studentPackageRepository.seed(
            studentId = student.id,
            packageType = singlePackage,
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

        val viewModel = AttendanceViewModel(
            getAttendanceUseCase = GetAttendanceUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            ),
            getPackageTypesUseCase = createGetPackageTypesUseCase(
                packageTypes = listOf(singlePackage),
            ),
            registerStudentUseCase = createRegisterStudentUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            ),
            markStudentPresentUseCase = MarkStudentPresentUseCase(
                repository = delayedRepository,
                transactionRunner = transactionRunner,
            ),
            removeStudentAttendanceUseCase =
            createRemoveStudentAttendanceUseCase(
                studentPackageRepository = studentPackageRepository,
            ),
            deleteStudentUseCase = DeleteStudentUseCase(
                studentRepository = studentRepository,
            ),
            currentDates = flowOf(today),
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
        val firstAttendance = LocalDate(2026, 10, 2)
        val attendanceToRemove = LocalDate(2026, 10, 5)

        val student = studentRepository.seed(
            Student(
                id = 7,
                fullName = "Anna and John",
            ),
        )

        val studentPackage = studentPackageRepository.create(
            studentId = student.id,
            packageType = duoPackage,
            startedOn = firstAttendance,
            visitCount = 2,
        )

        studentPackageRepository.recordAttendance(
            packageId = studentPackage.id,
            attendedOn = attendanceToRemove,
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

        val viewModel = AttendanceViewModel(
            getAttendanceUseCase = GetAttendanceUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            ),
            getPackageTypesUseCase = createGetPackageTypesUseCase(
                packageTypes = listOf(duoPackage),
            ),
            registerStudentUseCase = createRegisterStudentUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            ),
            markStudentPresentUseCase = createMarkStudentPresentUseCase(
                studentPackageRepository = studentPackageRepository,
            ),
            removeStudentAttendanceUseCase = RemoveStudentAttendanceUseCase(
                repository = delayedRepository,
                transactionRunner = transactionRunner,
            ),
            deleteStudentUseCase = DeleteStudentUseCase(
                studentRepository = studentRepository,
            ),
            currentDates = flowOf(today),
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
            attendanceDate = attendanceToRemove,
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
            attendanceDate = attendanceToRemove,
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
}
