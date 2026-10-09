package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelTest {
    private val today = LocalDate(2026, 10, 8)

    private val packageType = testFourClassPackage()

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
                packageType,
                testFiveClassPackage(id = 2),
            )

        val studentPackage = studentPackageRepository.seed(
            studentId = student.id,
            packageType = packageType,
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
                packageType,
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
                packageType,
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

        val packageTypes = listOf(packageType)

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
            packageType = packageType,
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
            packageType,
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
            packageType = packageType,
            startedOn = LocalDate(2026, 10, 5),
        )

        val getAttendance = GetAttendanceUseCase(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        )

        val getPackageTypes = createGetPackageTypesUseCase(
            packageTypes = listOf(packageType),
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
            packageType = packageType,
            startedOn = LocalDate(2026, 10, 2),
        )

        val getAttendance =
            GetAttendanceUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            )

        val getPackageTypes =
            createGetPackageTypesUseCase(
                packageTypes = listOf(packageType),
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
            packageType = packageType,
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
                    packageTypes = listOf(packageType),
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
                packageType = packageType,
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
                    packageTypes = listOf(packageType),
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
                AttendanceHistory(
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
        assertEquals(listOf(packageType), after.packageTypes)
        assertEquals(today, after.currentDate)
        assertEquals(attendanceToRemove, after.latestClassDate)
        assertEquals(
            expectedPackage,
            studentPackageRepository.getLatestPackageForStudent(student.id),
        )
    }

    @Test
    fun `reports first attendance removal rejection and preserves stored package`() = runViewModelTest {
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
                packageType = packageType,
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
                    packageTypes = listOf(packageType),
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
        assertIs<AttendanceUiState.Content>(viewModel.uiState.value)

        // When
        viewModel.removeStudentAttendance(
            studentId = student.id,
            attendanceDate = firstAttendance,
        )
        advanceUntilIdle()

        // Then
        assertEquals(
            AttendanceUiState.Error(message = "The first attendance cannot be removed"),
            viewModel.uiState.value,
        )
        assertEquals(
            studentPackage,
            studentPackageRepository.getLatestPackageForStudent(student.id),
        )
    }

    @Test
    fun `registers paired student with two visits and refreshes attendance`() = runViewModelTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()

        val pairedPackage =
            PackageType(
                id = 3,
                name = "Duo 8 · Special rate",
                totalClasses = 8,
                validityWeeks = 4,
                price = 2400,
                maxVisitsPerAttendance = 2,
            )

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
                    packageTypes = listOf(pairedPackage),
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
            packageType = pairedPackage,
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
        assertEquals(pairedPackage, studentPackage.packageType)
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
