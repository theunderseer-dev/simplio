package com.theunderseer.simplio.feature.attendance.di

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassAttendanceRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassScheduleRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomPackageTypeCreationRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomPackageTypeRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentGroupCreationRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentGroupRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.seed.PackageTypeSeeder
import com.theunderseer.simplio.feature.attendance.data.local.seed.StudentGroupSeeder
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncOutbox
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassAttendanceRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassScheduleRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeCreationRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentGroupCreationRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentGroupRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentRepository
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.usecase.ClassStudentActionsUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.CreatePackageTypeUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.CreateStudentGroupUseCase
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
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceViewModel
import com.theunderseer.simplio.feature.attendance.presentation.attendanceClockTicks
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

fun attendanceModule() = module {
    single<PackageTypeCreationRepository> { RoomPackageTypeCreationRepository(get()) }
    factory { CreatePackageTypeUseCase(get()) }
    single<StudentGroupCreationRepository> { RoomStudentGroupCreationRepository(get()) }
    factory { CreateStudentGroupUseCase(get()) }
    single<ClassScheduleRepository> { RoomClassScheduleRepository(database = get()) }

    single { RoomSyncOutbox(database = get()) }

    single {
        get<AttendanceDatabase>().studentDao()
    }

    single {
        get<AttendanceDatabase>().studentPackageDao()
    }

    single {
        get<AttendanceDatabase>().studentGroupDao()
    }

    single {
        get<AttendanceDatabase>().attendanceDao()
    }

    single {
        get<AttendanceDatabase>().packageTypeDao()
    }

    single<StudentRepository> {
        RoomStudentRepository(database = get())
    }

    single<StudentPackageRepository> {
        RoomStudentPackageRepository(database = get())
    }

    single<StudentGroupRepository> {
        RoomStudentGroupRepository(
            studentGroupDao = get(),
        )
    }

    single<PackageTypeRepository> {
        RoomPackageTypeRepository(
            packageTypeDao = get(),
        )
    }

    single<TransactionRunner> {
        RoomTransactionRunner(
            database = get(),
        )
    }

    single {
        PackageTypeSeeder(database = get())
    }

    single {
        StudentGroupSeeder(database = get())
    }

    single<ClassHistoryRepository> {
        RoomClassHistoryRepository(
            attendanceDao = get(),
            database = get(),
        )
    }

    factory {
        GetAttendanceUseCase(
            studentRepository = get(),
            studentPackageRepository = get(),
        )
    }

    factory {
        GetPackageTypesUseCase(
            packageTypeRepository = get(),
        )
    }

    factory {
        GetStudentGroupsUseCase(
            repository = get(),
        )
    }

    factory {
        RegisterStudentUseCase(
            studentRepository = get(),
            studentPackageRepository = get(),
            transactionRunner = get(),
        )
    }

    factory {
        RenewStudentPackageUseCase(
            repository = get(),
            studentRepository = get(),
            transactionRunner = get(),
        )
    }

    factory {
        MarkStudentPresentUseCase(
            repository = get(),
            transactionRunner = get(),
        )
    }

    factory {
        RemoveStudentAttendanceUseCase(
            repository = get(),
            transactionRunner = get(),
        )
    }

    factory {
        DeleteStudentUseCase(
            studentRepository = get(),
        )
    }

    factory {
        GetClassHistoryUseCase(
            repository = get(),
        )
    }

    factory {
        UpdateStudentNoteUseCase(
            studentRepository = get(),
        )
    }

    single<ClassAttendanceRepository> {
        RoomClassAttendanceRepository(
            get(),
            getOrNull<Clock>(named("attendanceClock")) ?: Clock.System,
        )
    }

    factory { ClassStudentActionsUseCase(get(), get(), get(), get()) }

    viewModel {
        AttendanceViewModel(
            classAttendanceRepository = get(),
            classStudentActions = get(),
            getAttendanceUseCase = get(),
            getPackageTypesUseCase = get(),
            createPackageTypeUseCase = get(),
            getStudentGroupsUseCase = get(),
            registerStudentUseCase = get(),
            renewStudentPackageUseCase = get(),
            markStudentPresentUseCase = get(),
            removeStudentAttendanceUseCase = get(),
            deleteStudentUseCase = get(),
            getClassHistoryUseCase = get(),
            updateStudentNoteUseCase = get(),
            createStudentGroupUseCase = get(),
            classScheduleRepository = get(),
            currentInstants = attendanceClockTicks(getOrNull<Clock>(named("attendanceClock")) ?: Clock.System),
            transactionRunner = get(),
            localChanges = get<AttendanceDatabase>().invalidationTracker.createFlow(
                "students",
                "student_packages",
                "attendance", "class_session_states",
                "package_types",
                "student_groups",
                "class_schedules", "class_schedule_slots", "student_group_schedules",
                emitInitialState = true,
            ).map { Unit },
            currentDates = get(
                qualifier = named("attendanceCurrentDates"),
            ),
        )
    }
}
