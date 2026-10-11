package com.theunderseer.simplio.feature.attendance.domain.usecase

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testPackageTypeEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentGroupEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentPackageEntity
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
    fun setUp() = runTest {
        database =
            Room
                .inMemoryDatabaseBuilder<AttendanceDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()

        database.studentGroupDao().insert(testStudentGroupEntity())

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
                .insert(testStudentEntity()).toInt()

        val packageTemplate = testClassPackage()

        val packageTypeId = database.packageTypeDao().insert(
            testPackageTypeEntity(
                packageType = packageTemplate,
            ),
        ).toInt()

        val packageType = packageTemplate.copy(id = packageTypeId)

        val latestStartedOn =
            LocalDate(
                2026,
                10,
                1,
            )

        val latestPackageId = database.studentPackageDao().insert(
            testStudentPackageEntity(
                studentId = studentId,
                packageType = packageType,
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

        val oldPackageId = database.studentPackageDao().insert(
            testStudentPackageEntity(
                studentId = studentId,
                packageType = packageType,
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
        val attendance = getAttendance()

        // Then
        assertEquals(
            Attendance(
                entries =
                listOf(
                    RegisteredStudent(
                        student = testStudent(id = studentId),
                        studentPackage =
                        StudentPackage(
                            id = latestPackageId,
                            studentId = studentId,
                            packageType = packageType,
                            startedOn = latestStartedOn,
                            attendanceHistory =
                            PackageAttendanceHistory(
                                visitsByDate =
                                mapOf(
                                    latestStartedOn to 1,
                                    secondAttendanceDate to 1,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            attendance,
        )
    }
}
