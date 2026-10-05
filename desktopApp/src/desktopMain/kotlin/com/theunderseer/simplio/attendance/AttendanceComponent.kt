package com.theunderseer.simplio.attendance

import com.theunderseer.simplio.database.prepareSimplioDatabasePath
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.database.createAttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomPackageTypeRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.seed.PackageTypeSeeder
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.usecase.DeleteStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetPackageTypesUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.MarkStudentPresentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RenewStudentPackageUseCase

class AttendanceComponent(
    internal val database: AttendanceDatabase,
    val registerStudent: RegisterStudentUseCase,
    val renewStudentPackage: RenewStudentPackageUseCase,
    val markStudentPresent: MarkStudentPresentUseCase,
    val getAttendance: GetAttendanceUseCase,
    val getPackageTypes: GetPackageTypesUseCase,
    val deleteStudent: DeleteStudentUseCase,
    private val packageTypeSeeder: PackageTypeSeeder,
) : AutoCloseable {
    suspend fun initialize() {
        packageTypeSeeder.seedDefaults()
    }

    override fun close() {
        database.close()
    }
}

fun createAttendanceComponent(): AttendanceComponent = createAttendanceComponent(
    databasePath = prepareSimplioDatabasePath(),
)

internal fun createAttendanceComponent(databasePath: String): AttendanceComponent {
    val database =
        createAttendanceDatabase(
            path = databasePath,
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

    val getAttendance =
        GetAttendanceUseCase(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
        )

    val getPackageTypes =
        GetPackageTypesUseCase(
            packageTypeRepository = packageTypeRepository,
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

    val deleteStudent =
        DeleteStudentUseCase(
            studentRepository = studentRepository,
        )

    return AttendanceComponent(
        database = database,
        registerStudent = registerStudent,
        renewStudentPackage = renewStudentPackage,
        markStudentPresent = markStudentPresent,
        getAttendance = getAttendance,
        getPackageTypes = getPackageTypes,
        deleteStudent = deleteStudent,
        packageTypeSeeder = packageTypeSeeder,
    )
}
