package com.theunderseer.simplio.feature.attendance.domain.usecase

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetAttendanceUseCaseIntegrationTest {
    private lateinit var database: AttendanceDatabase
    private lateinit var getAttendance: GetAttendanceUseCase

    @BeforeTest
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder<AttendanceDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()

        getAttendance =
            GetAttendanceUseCase(
                studentRepository =
                RoomStudentRepository(
                    studentDao = database.studentDao(),
                ),
                studentPackageRepository =
                RoomStudentPackageRepository(
                    studentPackageDao = database.studentPackageDao(),
                    attendanceDao = database.attendanceDao(),
                ),
            )
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `attendance returns student with latest package and its attendance history`() = runTest {
        // Given
        val studentId =
            database
                .studentDao()
                .insert(
                    StudentEntity(
                        fullName = "Anna Smith",
                    ),
                ).toInt()

        val packageTypeId =
            database
                .packageTypeDao()
                .insert(
                    PackageTypeEntity(
                        name = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                    ),
                ).toInt()

        val packageType =
            PackageType(
                id = packageTypeId,
                name = "5-class package",
                totalClasses = 5,
                validityWeeks = 6,
                price = 1700,
            )

        val latestStartedOn =
            LocalDate(
                2026,
                10,
                1,
            )

        val latestPackageId =
            database
                .studentPackageDao()
                .insert(
                    StudentPackageEntity(
                        studentId = studentId,
                        packageTypeId = packageTypeId,
                        packageName = packageType.name,
                        totalClasses = packageType.totalClasses,
                        validityWeeks = packageType.validityWeeks,
                        price = packageType.price,
                        startedOnEpochDay = latestStartedOn.toEpochDays(),
                    ),
                ).toInt()

        val secondAttendanceDate =
            LocalDate(
                2026,
                10,
                3,
            )

        database
            .attendanceDao()
            .insert(
                AttendanceEntity(
                    packageId = latestPackageId,
                    attendedOnEpochDay = latestStartedOn.toEpochDays(),
                ),
            )

        database
            .attendanceDao()
            .insert(
                AttendanceEntity(
                    packageId = latestPackageId,
                    attendedOnEpochDay = secondAttendanceDate.toEpochDays(),
                ),
            )

        val oldStartedOn =
            LocalDate(
                2026,
                8,
                1,
            )

        val oldPackageId =
            database
                .studentPackageDao()
                .insert(
                    StudentPackageEntity(
                        studentId = studentId,
                        packageTypeId = packageTypeId,
                        packageName = packageType.name,
                        totalClasses = packageType.totalClasses,
                        validityWeeks = packageType.validityWeeks,
                        price = packageType.price,
                        startedOnEpochDay = oldStartedOn.toEpochDays(),
                    ),
                ).toInt()

        assertTrue(
            oldPackageId > latestPackageId,
        )

        database
            .attendanceDao()
            .insert(
                AttendanceEntity(
                    packageId = oldPackageId,
                    attendedOnEpochDay = oldStartedOn.toEpochDays(),
                ),
            )

        // When
        val attendance =
            getAttendance()

        // Then
        assertEquals(
            Attendance(
                entries =
                listOf(
                    RegisteredStudent(
                        student =
                        Student(
                            id = studentId,
                            fullName = "Anna Smith",
                        ),
                        studentPackage =
                        StudentPackage(
                            id = latestPackageId,
                            studentId = studentId,
                            packageType = packageType,
                            startedOn = latestStartedOn,
                            attendanceDates =
                            setOf(
                                latestStartedOn,
                                secondAttendanceDate,
                            ),
                        ),
                    ),
                ),
            ),
            attendance,
        )
    }
}
