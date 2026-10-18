package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentGroupRepository
import com.theunderseer.simplio.feature.attendance.domain.usecase.DeleteStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetClassHistoryUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetPackageTypesUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetStudentGroupsUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.MarkStudentPresentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RemoveStudentAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.fake.FakeClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeTransactionRunner
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentGroups
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import com.theunderseer.simplio.feature.attendance.domain.usecase.UpdateStudentNoteUseCase

@OptIn(ExperimentalCoroutinesApi::class)
internal fun runViewModelTest(block: suspend TestScope.() -> Unit) {
    val testScope = TestScope()
    val mainDispatcher =
        StandardTestDispatcher(
            scheduler = testScope.testScheduler,
        )

    Dispatchers.setMain(mainDispatcher)

    try {
        testScope.runTest {
            block()
        }
    } finally {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.observeUiState(viewModel: AttendanceViewModel) {
    backgroundScope.launch(
        UnconfinedTestDispatcher(testScheduler),
    ) {
        viewModel.uiState.collect {}
    }
}

internal fun createViewModel(
    studentRepository: FakeStudentRepository,
    studentPackageRepository: FakeStudentPackageRepository,
    classHistoryRepository: ClassHistoryRepository = FakeClassHistoryRepository(),
    packageTypes: List<PackageType> = listOf(AttendanceTestData.fourClassPackage),
    studentGroups: List<StudentGroup> = testStudentGroups(),
    currentDates: Flow<LocalDate> = flowOf(AttendanceTestData.currentDate),
    markStudentPresentUseCase: MarkStudentPresentUseCase =
        createMarkStudentPresentUseCase(
            studentPackageRepository = studentPackageRepository,
        ),
    removeStudentAttendanceUseCase: RemoveStudentAttendanceUseCase =
        createRemoveStudentAttendanceUseCase(
            studentPackageRepository = studentPackageRepository,
        ),
): AttendanceViewModel = AttendanceViewModel(
    updateStudentNoteUseCase = UpdateStudentNoteUseCase(studentRepository = studentRepository),
    getAttendanceUseCase = GetAttendanceUseCase(
        studentRepository = studentRepository,
        studentPackageRepository = studentPackageRepository,
    ),
    getPackageTypesUseCase = createGetPackageTypesUseCase(
        packageTypes = packageTypes,
    ),
    getStudentGroupsUseCase = createGetStudentGroupsUseCase(
        studentGroups = studentGroups,
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
    getClassHistoryUseCase = GetClassHistoryUseCase(
        repository = classHistoryRepository,
    ),
    currentDates = currentDates,
)

private fun createGetPackageTypesUseCase(packageTypes: List<PackageType>): GetPackageTypesUseCase =
    GetPackageTypesUseCase(
        packageTypeRepository = object : PackageTypeRepository {
            override suspend fun getAll(): List<PackageType> = packageTypes

            override suspend fun getById(id: Int): PackageType? = packageTypes.firstOrNull {
                it.id == id
            }
        },
    )

private fun createGetStudentGroupsUseCase(studentGroups: List<StudentGroup>): GetStudentGroupsUseCase =
    GetStudentGroupsUseCase(
        repository = object : StudentGroupRepository {
            override suspend fun getAll(): List<StudentGroup> = studentGroups
        },
    )

private fun createRegisterStudentUseCase(
    studentRepository: FakeStudentRepository,
    studentPackageRepository: FakeStudentPackageRepository,
): RegisterStudentUseCase = RegisterStudentUseCase(
    studentRepository = studentRepository,
    studentPackageRepository = studentPackageRepository,
    transactionRunner = FakeTransactionRunner(),
)

private fun createMarkStudentPresentUseCase(
    studentPackageRepository: FakeStudentPackageRepository,
): MarkStudentPresentUseCase = MarkStudentPresentUseCase(
    repository = studentPackageRepository,
    transactionRunner = FakeTransactionRunner(),
)

private fun createRemoveStudentAttendanceUseCase(
    studentPackageRepository: FakeStudentPackageRepository,
): RemoveStudentAttendanceUseCase = RemoveStudentAttendanceUseCase(
    repository = studentPackageRepository,
    transactionRunner = FakeTransactionRunner(),
)
