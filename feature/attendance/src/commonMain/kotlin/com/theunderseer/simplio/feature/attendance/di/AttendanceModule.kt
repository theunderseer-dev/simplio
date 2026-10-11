package com.theunderseer.simplio.feature.attendance.di

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomPackageTypeRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentGroupRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.seed.PackageTypeSeeder
import com.theunderseer.simplio.feature.attendance.data.local.seed.StudentGroupSeeder
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentGroupRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentRepository
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.usecase.DeleteStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetPackageTypesUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetStudentGroupsUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.MarkStudentPresentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RemoveStudentAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RenewStudentPackageUseCase
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val attendanceModule = module {
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
        RoomStudentRepository(
            studentDao = get(),
        )
    }

    single<StudentPackageRepository> {
        RoomStudentPackageRepository(
            studentPackageDao = get(),
            attendanceDao = get(),
        )
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
        PackageTypeSeeder(
            packageTypeDao = get(),
            transactionRunner = get(),
        )
    }

    single {
        StudentGroupSeeder(
            studentGroupDao = get(),
            transactionRunner = get(),
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

    viewModel {
        AttendanceViewModel(
            getAttendanceUseCase = get(),
            getPackageTypesUseCase = get(),
            getStudentGroupsUseCase = get(),
            registerStudentUseCase = get(),
            markStudentPresentUseCase = get(),
            removeStudentAttendanceUseCase = get(),
            deleteStudentUseCase = get(),
            currentDates = get(
                qualifier = named("attendanceCurrentDates"),
            ),
        )
    }
}
