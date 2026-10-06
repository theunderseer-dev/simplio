package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeRepository
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.usecase.DeleteStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetPackageTypesUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.MarkStudentPresentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelTest {
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
            id = 2,
            name = "5-class package",
            totalClasses = 5,
            validityWeeks = 5,
            price = 1700,
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

        val packageTypes = listOf(
            fourClassPackage,
            fiveClassPackage,
        )

        val packageType = packageTypes.first()

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
            deleteStudentUseCase = deleteStudentUseCase,
        )

        advanceUntilIdle()

        // Then
        assertEquals(
            AttendanceUiState.Content(
                attendance = expectedAttendance,
                packageTypes = packageTypes,
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

        val packageTypes = listOf(
            fourClassPackage,
            fiveClassPackage,
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
            deleteStudentUseCase = deleteStudentUseCase,
        )

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

        val packageTypes = listOf(
            fourClassPackage,
            fiveClassPackage,
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
            deleteStudentUseCase = deleteStudentUseCase,
        )

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
            deleteStudentUseCase = deleteStudentUseCase,
        )

        advanceUntilIdle()

        // When
        viewModel.registerStudent(
            fullName = "Anna Smith",
            packageType = fourClassPackage,
            registeredOn = registeredOn,
        )

        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value as AttendanceUiState.Content

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

        val student = studentRepository.seed(
            Student(
                id = 1,
                fullName = "Anna Smith",
            ),
        )

        studentPackageRepository.seed(
            studentId = student.id,
            packageType = fourClassPackage,
            startedOn = LocalDate(2026, 10, 5),
        )

        val getAttendance = GetAttendanceUseCase(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        )

        val getPackageTypes = createGetPackageTypesUseCase(
            packageTypes = listOf(fourClassPackage),
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
            deleteStudentUseCase = deleteStudentUseCase,
        )

        advanceUntilIdle()

        // When
        viewModel.deleteStudent(
            studentId = student.id,
        )

        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value as AttendanceUiState.Content

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
            packageType = fiveClassPackage,
            startedOn = LocalDate(2026, 10, 2),
        )

        val getAttendance =
            GetAttendanceUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            )

        val getPackageTypes =
            GetPackageTypesUseCase(
                packageTypeRepository =
                object : PackageTypeRepository {
                    override suspend fun getAll(): List<PackageType> = listOf(fiveClassPackage)

                    override suspend fun getById(id: Int): PackageType? = fiveClassPackage.takeIf { it.id == id }
                },
            )

        val registerStudent =
            createRegisterStudentUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            )

        val markStudentPresent =
            MarkStudentPresentUseCase(
                repository = studentPackageRepository,
                transactionRunner = transactionRunner,
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
                deleteStudentUseCase = deleteStudent,
            )

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
            viewModel.uiState.value as AttendanceUiState.Content

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

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) = runTest {
        val mainDispatcher = StandardTestDispatcher(
            scheduler = testScheduler,
        )

        Dispatchers.setMain(
            mainDispatcher,
        )

        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private val transactionRunner = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }
}
