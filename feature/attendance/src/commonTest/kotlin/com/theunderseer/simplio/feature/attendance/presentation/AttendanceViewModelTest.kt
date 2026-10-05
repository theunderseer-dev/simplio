package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeRepository
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetPackageTypesUseCase
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
    @Test
    fun `loads attendance into content state`() = runViewModelTest {
        // Given
        val studentRepository =
            FakeStudentRepository()

        val studentPackageRepository =
            FakeStudentPackageRepository()

        val student =
            studentRepository.seed(
                Student(
                    id = 1,
                    fullName = "Anna Smith",
                ),
            )

        val packageTypes =
            listOf(
                PackageType(
                    id = 1,
                    name = "4-class package",
                    totalClasses = 4,
                    validityWeeks = 5,
                    price = 1400,
                ),
                PackageType(
                    id = 2,
                    name = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 6,
                    price = 1700,
                ),
            )

        val packageType =
            packageTypes.first()

        val studentPackage =
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
                packageTypes = packageTypes,
            )

        val registerStudent =
            createRegisterStudentUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            )

        val expectedAttendance =
            Attendance(
                entries =
                listOf(
                    RegisteredStudent(
                        student = student,
                        studentPackage = studentPackage,
                    ),
                ),
            )

        // When
        val viewModel =
            AttendanceViewModel(
                getAttendanceUseCase = getAttendance,
                getPackageTypesUseCase = getPackageTypes,
                registerStudentUseCase = registerStudent,
                currentDate = {
                    LocalDate(2026, 10, 3)
                },
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
        val studentRepository =
            FakeStudentRepository()

        val studentPackageRepository =
            FakeStudentPackageRepository()

        val student =
            studentRepository.seed(
                Student(
                    id = 1,
                    fullName = "Anna Smith",
                ),
            )

        val packageTypes =
            listOf(
                PackageType(
                    id = 1,
                    name = "4-class package",
                    totalClasses = 4,
                    validityWeeks = 5,
                    price = 1400,
                ),
                PackageType(
                    id = 2,
                    name = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 6,
                    price = 1700,
                ),
            )

        val getAttendance =
            GetAttendanceUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            )

        val getPackageTypes =
            createGetPackageTypesUseCase(
                packageTypes = packageTypes,
            )

        val registerStudent =
            createRegisterStudentUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            )

        // When
        val viewModel =
            AttendanceViewModel(
                getAttendanceUseCase = getAttendance,
                getPackageTypesUseCase = getPackageTypes,
                registerStudentUseCase = registerStudent,
                currentDate = {
                    LocalDate(2026, 10, 3)
                },
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
        val studentRepository =
            FakeStudentRepository()

        val studentPackageRepository =
            FakeStudentPackageRepository()

        val packageTypes =
            listOf(
                PackageType(
                    id = 1,
                    name = "4-class package",
                    totalClasses = 4,
                    validityWeeks = 5,
                    price = 1400,
                ),
                PackageType(
                    id = 2,
                    name = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 6,
                    price = 1700,
                ),
            )

        val getAttendance =
            GetAttendanceUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            )

        val getPackageTypes =
            createGetPackageTypesUseCase(
                packageTypes = packageTypes,
            )

        val registerStudent =
            createRegisterStudentUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            )

        // When
        val viewModel =
            AttendanceViewModel(
                getAttendanceUseCase = getAttendance,
                getPackageTypesUseCase = getPackageTypes,
                registerStudentUseCase = registerStudent,
                currentDate = {
                    LocalDate(2026, 10, 3)
                },
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
        val studentRepository =
            FakeStudentRepository()

        val studentPackageRepository =
            FakeStudentPackageRepository()

        val packageType =
            PackageType(
                id = 1,
                name = "4-class package",
                totalClasses = 4,
                validityWeeks = 5,
                price = 1400,
            )

        val packageTypes =
            listOf(
                packageType,
            )

        val getAttendance =
            GetAttendanceUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            )

        val getPackageTypes =
            createGetPackageTypesUseCase(
                packageTypes = packageTypes,
            )

        val registerStudent =
            createRegisterStudentUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            )

        val registeredOn =
            LocalDate(
                2026,
                10,
                3,
            )

        val viewModel =
            AttendanceViewModel(
                getAttendanceUseCase = getAttendance,
                getPackageTypesUseCase = getPackageTypes,
                registerStudentUseCase = registerStudent,
                currentDate = {
                    registeredOn
                },
            )

        advanceUntilIdle()

        // When
        viewModel.registerStudent(
            fullName = "Anna Smith",
            packageType = packageType,
        )

        advanceUntilIdle()

        // Then
        val state =
            viewModel.uiState.value as AttendanceUiState.Content

        assertEquals(
            packageTypes,
            state.packageTypes,
        )

        assertEquals(
            1,
            state.attendance.entries.size,
        )

        val registeredStudent =
            state.attendance.entries.single()

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
        val studentRepository =
            FakeStudentRepository()

        val studentPackageRepository =
            FakeStudentPackageRepository()

        val student =
            studentRepository.seed(
                Student(
                    id = 1,
                    fullName = "Anna Smith",
                ),
            )

        val packageType =
            PackageType(
                id = 1,
                name = "4-class package",
                totalClasses = 4,
                validityWeeks = 5,
                price = 1400,
            )

        studentPackageRepository.seed(
            studentId = student.id,
            packageType = packageType,
            startedOn = LocalDate(2026, 10, 5),
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

        val deleteStudent =
            DeleteStudentUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
                transactionRunner = transactionRunner,
            )

        val viewModel =
            AttendanceViewModel(
                getAttendanceUseCase = getAttendance,
                getPackageTypesUseCase = getPackageTypes,
                registerStudentUseCase = createRegisterStudentUseCase(
                    studentRepository = studentRepository,
                    studentPackageRepository = studentPackageRepository,
                ),
                deleteStudentUseCase = deleteStudent,
                currentDate = {
                    LocalDate(2026, 10, 5)
                },
            )

        advanceUntilIdle()

        // When
        viewModel.deleteStudent(
            studentId = student.id,
        )

        advanceUntilIdle()

        // Then
        val state =
            viewModel.uiState.value as AttendanceUiState.Content

        assertEquals(
            emptyList(),
            state.attendance.entries,
        )
    }

    private fun createGetPackageTypesUseCase(packageTypes: List<PackageType>): GetPackageTypesUseCase =
        GetPackageTypesUseCase(
            packageTypeRepository =
            object : PackageTypeRepository {
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

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) = runTest {
        val mainDispatcher =
            StandardTestDispatcher(
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

    private val transactionRunner =
        object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }
}
