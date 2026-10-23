package com.theunderseer.simplio.feature.attendance.domain.usecase

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testPackageTypeEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentGroupEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentPackage
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
                RoomStudentRepository(database),
                studentPackageRepository =
                RoomStudentPackageRepository(database),
            )
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `attendance returns latest package and dates from earlier packages`() = runTest {
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

        val latestPackageId = database.studentPackageDao().insert(
            testStudentPackageEntity(
                studentId = studentId,
                packageType = packageType,
                startedOnEpochDay = firstClassDate.toEpochDays(),
            ),
        ).toInt()

        database
            .attendanceDao()
            .insert(
                AttendanceEntity(
                    packageId = latestPackageId,
                    attendedOnEpochDay = firstClassDate.toEpochDays(),
                ),
            )

        database
            .attendanceDao()
            .insert(
                AttendanceEntity(
                    packageId = latestPackageId,
                    attendedOnEpochDay = secondClassDate.toEpochDays(),
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
                        studentPackage = testStudentPackage(
                            id = latestPackageId,
                            studentId = studentId,
                            packageType = packageType,
                            purchasedOn = firstClassDate,
                            startedOn = firstClassDate,
                            visitsByDate = mapOf(
                                firstClassDate to 1,
                                secondClassDate to 1,
                            ),
                        ),
                        previousAttendanceDates = setOf(oldStartedOn),
                    ),
                ),
            ),
            attendance,
        )
    }
}
