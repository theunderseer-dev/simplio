package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassAttendanceRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassScheduleRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.DeleteStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentGroupDeletionRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentGroupRepository
import com.theunderseer.simplio.feature.attendance.domain.usecase.ClassStudentActionsUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.CreatePackageTypeUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.CreateStudentGroupUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.DeleteStudentGroupUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.DeleteStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetClassHistoryUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetPackageTypesUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetStudentGroupsUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.MarkStudentPresentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RemoveStudentAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RenewStudentPackageUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.UpdateStudentNoteUseCase
import com.theunderseer.simplio.feature.attendance.fake.FakeClassAttendanceRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.fake.FakePackageTypeRepository
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
import kotlin.time.Instant

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
    classAttendanceRepository: ClassAttendanceRepository =
        FakeClassAttendanceRepository(),
    packageTypes: List<PackageType> = listOf(AttendanceTestData.fourClassPackage),
    packageCatalog: FakePackageTypeRepository = FakePackageTypeRepository(packageTypes),
    createPackageTypeUseCase: CreatePackageTypeUseCase = CreatePackageTypeUseCase(packageCatalog),
    studentGroups: List<StudentGroup> = testStudentGroups(),
    groupDeletionRepository: StudentGroupDeletionRepository = object : StudentGroupDeletionRepository {
        override suspend fun delete(groupId: Int) = DeleteStudentGroupResult.DELETED
    },
    currentDates: Flow<LocalDate> = flowOf(AttendanceTestData.currentDate),
    localChanges: Flow<Unit> = flowOf(Unit),
    createStudentGroupUseCase: CreateStudentGroupUseCase? = null,
    currentInstants: Flow<Instant?> = flowOf(null),
    classScheduleRepository: ClassScheduleRepository? = null,
    markStudentPresentUseCase: MarkStudentPresentUseCase =
        createMarkStudentPresentUseCase(
            studentPackageRepository = studentPackageRepository,
        ),
    removeStudentAttendanceUseCase: RemoveStudentAttendanceUseCase =
        createRemoveStudentAttendanceUseCase(
            studentPackageRepository = studentPackageRepository,
        ),
    renewStudentPackageUseCase: RenewStudentPackageUseCase = RenewStudentPackageUseCase(
        repository = studentPackageRepository,
        studentRepository = studentRepository,
        transactionRunner = FakeTransactionRunner(),
    ),
): AttendanceViewModel = AttendanceViewModel(
    deleteStudentGroupUseCase = DeleteStudentGroupUseCase(groupDeletionRepository),
    classAttendanceRepository = classAttendanceRepository,
    classStudentActions = ClassStudentActionsUseCase(
        classAttendanceRepository,
        createRegisterStudentUseCase(studentRepository, studentPackageRepository),
        renewStudentPackageUseCase,
        FakeTransactionRunner(),
    ),
    renewStudentPackageUseCase = renewStudentPackageUseCase,
    createStudentGroupUseCase = createStudentGroupUseCase,
    currentInstants = currentInstants,
    classScheduleRepository = classScheduleRepository,
    updateStudentNoteUseCase = UpdateStudentNoteUseCase(studentRepository = studentRepository),
    getAttendanceUseCase = GetAttendanceUseCase(
        studentRepository = studentRepository,
        studentPackageRepository = studentPackageRepository,
    ),
    getPackageTypesUseCase = GetPackageTypesUseCase(packageCatalog),
    createPackageTypeUseCase = createPackageTypeUseCase,
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
    localChanges = localChanges,
    transactionRunner = FakeTransactionRunner(),
)

private fun createGetStudentGroupsUseCase(studentGroups: List<StudentGroup>): GetStudentGroupsUseCase =
    GetStudentGroupsUseCase(
        repository = object : StudentGroupRepository {
            override suspend fun getAll(): List<StudentGroup> = studentGroups.toList()
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
