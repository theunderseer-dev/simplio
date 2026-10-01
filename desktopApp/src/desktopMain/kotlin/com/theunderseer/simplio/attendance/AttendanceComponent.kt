package com.theunderseer.simplio.attendance

import com.theunderseer.simplio.database.prepareSimplioDatabasePath
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.database.createAttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomPackageTypeRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.seed.PackageTypeSeeder
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeRepository
import com.theunderseer.simplio.feature.attendance.domain.usecase.MarkStudentPresentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RenewStudentPackageUseCase
import java.nio.file.Path

class AttendanceComponent(
    internal val database: AttendanceDatabase,
    val packageTypeRepository: PackageTypeRepository,
    val registerStudent: RegisterStudentUseCase,
    val renewStudentPackage: RenewStudentPackageUseCase,
    val markStudentPresent: MarkStudentPresentUseCase,
    private val packageTypeSeeder: PackageTypeSeeder,
) : AutoCloseable {
    suspend fun initialize() {
        packageTypeSeeder.seedDefaults()
    }

    override fun close() {
        database.close()
    }
}

fun createAttendanceComponent(userHome: Path): AttendanceComponent {
    val databasePath =
        prepareSimplioDatabasePath(
            userHome = userHome,
        )

    val database =
        createAttendanceDatabase(
            path = databasePath.toString(),
        )

    val studentRepository =
        RoomStudentRepository(
            studentDao = database.studentDao(),
        )

    val studentPackageRepository =
        RoomStudentPackageRepository(
            studentPackageDao = database.studentPackageDao(),
            attendanceDao = database.attendanceDao(),
        )

    val packageTypeRepository =
        RoomPackageTypeRepository(
            packageTypeDao = database.packageTypeDao(),
        )

    val transactionRunner =
        RoomTransactionRunner(
            database = database,
        )

    val packageTypeSeeder =
        PackageTypeSeeder(
            packageTypeDao = database.packageTypeDao(),
            transactionRunner = transactionRunner,
        )

    val registerStudent =
        RegisterStudentUseCase(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
            transactionRunner = transactionRunner,
        )

    val renewStudentPackage =
        RenewStudentPackageUseCase(
            repository = studentPackageRepository,
            transactionRunner = transactionRunner,
        )

    val markStudentPresent =
        MarkStudentPresentUseCase(
            repository = studentPackageRepository,
            transactionRunner = transactionRunner,
        )

    return AttendanceComponent(
        database = database,
        packageTypeRepository = packageTypeRepository,
        registerStudent = registerStudent,
        renewStudentPackage = renewStudentPackage,
        markStudentPresent = markStudentPresent,
        packageTypeSeeder = packageTypeSeeder,
    )
}
