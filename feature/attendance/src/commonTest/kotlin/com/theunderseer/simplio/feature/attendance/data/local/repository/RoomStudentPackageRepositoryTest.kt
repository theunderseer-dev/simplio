package com.theunderseer.simplio.feature.attendance.data.local.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomStudentPackageRepositoryTest {
    private lateinit var database: AttendanceDatabase
    private lateinit var repository: RoomStudentPackageRepository

    @BeforeTest
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder<AttendanceDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()

        repository =
            RoomStudentPackageRepository(
                studentPackageDao = database.studentPackageDao(),
                attendanceDao = database.attendanceDao(),
            )
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `creating package persists package and first attendance`() = runTest {
        // Given
        // an existing student
        val studentId =
            database
                .studentDao()
                .insert(
                    StudentEntity(
                        fullName = "Anna Smith",
                    ),
                ).toInt()

        // And
        // an existing package type
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

        val startedOn = LocalDate(2026, 9, 30)

        // When
        val studentPackage =
            repository.create(
                studentId = studentId,
                packageType = packageType,
                startedOn = startedOn,
            )

        // Then
        // a generated package identity is returned
        assertTrue(studentPackage.id > 0)

        // And
        // the package belongs to the student
        assertEquals(
            studentId,
            studentPackage.studentId,
        )

        // And
        // the purchased package terms are preserved
        assertEquals(
            packageType,
            studentPackage.packageType,
        )

        // And
        // the package starts on the selected date
        assertEquals(
            startedOn,
            studentPackage.startedOn,
        )

        // And
        // the first attendance is recorded automatically
        assertEquals(
            setOf(startedOn),
            studentPackage.attendanceDates,
        )

        // And
        // the package row is persisted in Room
        val persistedPackage =
            assertNotNull(
                database
                    .studentPackageDao()
                    .getLatestForStudent(studentId),
            )

        assertEquals(
            studentPackage.id,
            persistedPackage.id,
        )

        assertEquals(
            packageType.id,
            persistedPackage.packageTypeId,
        )

        assertEquals(
            packageType.name,
            persistedPackage.packageName,
        )

        assertEquals(
            packageType.totalClasses,
            persistedPackage.totalClasses,
        )

        assertEquals(
            packageType.validityWeeks,
            persistedPackage.validityWeeks,
        )

        assertEquals(
            packageType.price,
            persistedPackage.price,
        )

        assertEquals(
            startedOn.toEpochDays(),
            persistedPackage.startedOnEpochDay,
        )

        // And
        // the first attendance row is persisted separately
        val persistedAttendance =
            database
                .attendanceDao()
                .getForPackage(studentPackage.id)

        assertEquals(
            1,
            persistedAttendance.size,
        )

        val attendance = persistedAttendance.single()

        assertEquals(
            studentPackage.id,
            attendance.packageId,
        )

        assertEquals(
            startedOn.toEpochDays(),
            attendance.attendedOnEpochDay,
        )
    }

    @Test
    fun `getting latest package reconstructs package with attendance history`() = runTest {
        // Given
        // an existing student
        val studentId =
            database
                .studentDao()
                .insert(
                    StudentEntity(
                        fullName = "Anna Smith",
                    ),
                ).toInt()

        // And
        // the current package type has updated terms
        val packageTypeId =
            database
                .packageTypeDao()
                .insert(
                    PackageTypeEntity(
                        name = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 5,
                        price = 2000,
                    ),
                ).toInt()

        // And
        // the student has a newer package with historical purchase terms
        val latestStartedOn = LocalDate(2026, 9, 1)

        val latestPackageId =
            database
                .studentPackageDao()
                .insert(
                    StudentPackageEntity(
                        studentId = studentId,
                        packageTypeId = packageTypeId,
                        packageName = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                        startedOnEpochDay = latestStartedOn.toEpochDays(),
                    ),
                ).toInt()

        // And
        // an older package was persisted later
        database
            .studentPackageDao()
            .insert(
                StudentPackageEntity(
                    studentId = studentId,
                    packageTypeId = packageTypeId,
                    packageName = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 6,
                    price = 1700,
                    startedOnEpochDay =
                    LocalDate(2026, 7, 1).toEpochDays(),
                ),
            )

        // And
        // the latest package has attendance history
        val secondAttendance = LocalDate(2026, 9, 8)
        val thirdAttendance = LocalDate(2026, 9, 15)

        database.attendanceDao().insert(
            AttendanceEntity(
                packageId = latestPackageId,
                attendedOnEpochDay = latestStartedOn.toEpochDays(),
            ),
        )

        database.attendanceDao().insert(
            AttendanceEntity(
                packageId = latestPackageId,
                attendedOnEpochDay = secondAttendance.toEpochDays(),
            ),
        )

        database.attendanceDao().insert(
            AttendanceEntity(
                packageId = latestPackageId,
                attendedOnEpochDay = thirdAttendance.toEpochDays(),
            ),
        )

        // When
        val studentPackage =
            repository.getLatestPackageForStudent(
                studentId = studentId,
            )

        // Then
        // the latest package is returned
        val latestPackage =
            assertNotNull(studentPackage)

        assertEquals(
            latestPackageId,
            latestPackage.id,
        )

        assertEquals(
            studentId,
            latestPackage.studentId,
        )

        // And
        // the historical package snapshot is restored
        assertEquals(
            PackageType(
                id = packageTypeId,
                name = "5-class package",
                totalClasses = 5,
                validityWeeks = 6,
                price = 1700,
            ),
            latestPackage.packageType,
        )

        // And
        // the package start date is restored
        assertEquals(
            latestStartedOn,
            latestPackage.startedOn,
        )

        // And
        // all attendance dates are reconstructed
        assertEquals(
            setOf(
                latestStartedOn,
                secondAttendance,
                thirdAttendance,
            ),
            latestPackage.attendanceDates,
        )

        // And
        // used classes reflect the reconstructed attendance history
        assertEquals(
            3,
            latestPackage.usedClasses,
        )
    }

    @Test
    fun `recording attendance persists attendance for package`() = runTest {
        // Given
        // an existing student
        val studentId =
            database
                .studentDao()
                .insert(
                    StudentEntity(
                        fullName = "Anna Smith",
                    ),
                ).toInt()

        // And
        // an existing package type
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

        // And
        // an existing student package
        val startedOn = LocalDate(2026, 9, 1)

        val packageId =
            database
                .studentPackageDao()
                .insert(
                    StudentPackageEntity(
                        studentId = studentId,
                        packageTypeId = packageTypeId,
                        packageName = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                        startedOnEpochDay = startedOn.toEpochDays(),
                    ),
                ).toInt()

        // And
        // its initial attendance already exists
        database.attendanceDao().insert(
            AttendanceEntity(
                packageId = packageId,
                attendedOnEpochDay = startedOn.toEpochDays(),
            ),
        )

        val attendedOn = LocalDate(2026, 9, 8)

        // When
        repository.recordAttendance(
            packageId = packageId,
            attendedOn = attendedOn,
        )

        // Then
        // the new attendance is persisted
        val persistedAttendance =
            database
                .attendanceDao()
                .getForPackage(packageId)

        assertEquals(
            2,
            persistedAttendance.size,
        )

        assertEquals(
            setOf(
                startedOn.toEpochDays(),
                attendedOn.toEpochDays(),
            ),
            persistedAttendance
                .map { it.attendedOnEpochDay }
                .toSet(),
        )

        // And
        // all attendance rows belong to the same package
        assertEquals(
            setOf(packageId),
            persistedAttendance
                .map { it.packageId }
                .toSet(),
        )
    }

    @Test
    fun `recording duplicate attendance does not create another attendance row`() = runTest {
        // Given
        // an existing student
        val studentId =
            database
                .studentDao()
                .insert(
                    StudentEntity(
                        fullName = "Anna Smith",
                    ),
                ).toInt()

        // And
        // an existing package type
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

        // And
        // an existing student package
        val startedOn = LocalDate(2026, 9, 1)

        val packageId =
            database
                .studentPackageDao()
                .insert(
                    StudentPackageEntity(
                        studentId = studentId,
                        packageTypeId = packageTypeId,
                        packageName = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                        startedOnEpochDay = startedOn.toEpochDays(),
                    ),
                ).toInt()

        val attendedOn = LocalDate(2026, 9, 8)

        // And
        // the attendance already exists
        database.attendanceDao().insert(
            AttendanceEntity(
                packageId = packageId,
                attendedOnEpochDay = attendedOn.toEpochDays(),
            ),
        )

        // When
        repository.recordAttendance(
            packageId = packageId,
            attendedOn = attendedOn,
        )

        // Then
        // no duplicate attendance row is created
        val persistedAttendance =
            database
                .attendanceDao()
                .getForPackage(packageId)

        assertEquals(
            1,
            persistedAttendance.size,
        )

        val attendance = persistedAttendance.single()

        assertEquals(
            packageId,
            attendance.packageId,
        )

        assertEquals(
            attendedOn.toEpochDays(),
            attendance.attendedOnEpochDay,
        )
    }

    @Test
    fun `getting latest package returns null when student has no packages`() = runTest {
        // Given
        // an existing student with no packages
        val studentId =
            database
                .studentDao()
                .insert(
                    StudentEntity(
                        fullName = "Anna Smith",
                    ),
                ).toInt()

        // When
        val studentPackage =
            repository.getLatestPackageForStudent(
                studentId = studentId,
            )

        // Then
        assertNull(studentPackage)
    }

    @Test
    fun `recorded attendance is returned when package is read again`() = runTest {
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

        val startedOn = LocalDate(2026, 9, 25)

        val packageId =
            database
                .studentPackageDao()
                .insert(
                    StudentPackageEntity(
                        studentId = studentId,
                        packageTypeId = packageTypeId,
                        packageName = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                        startedOnEpochDay = startedOn.toEpochDays(),
                    ),
                ).toInt()

        database.attendanceDao().insert(
            AttendanceEntity(
                packageId = packageId,
                attendedOnEpochDay = startedOn.toEpochDays(),
            ),
        )

        val attendanceDate = LocalDate(2026, 9, 27)

        // When
        repository.recordAttendance(
            packageId = packageId,
            attendedOn = attendanceDate,
        )

        val reloadedPackage =
            assertNotNull(
                repository.getLatestPackageForStudent(studentId),
            )

        // Then
        assertEquals(
            packageId,
            reloadedPackage.id,
        )

        assertEquals(
            setOf(
                startedOn,
                attendanceDate,
            ),
            reloadedPackage.attendanceDates,
        )

        assertEquals(
            2,
            reloadedPackage.usedClasses,
        )
    }

    @Test
    fun `removing attendance deletes only requested date from requested package`() = runTest {
        // Given
        val studentId =
            database.studentDao().insert(
                StudentEntity(fullName = "Anna Smith"),
            ).toInt()
        val otherStudentId =
            database.studentDao().insert(
                StudentEntity(fullName = "John Brown"),
            ).toInt()
        val packageTypeId =
            database.packageTypeDao().insert(
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
        val firstAttendance = LocalDate(2026, 10, 2)
        val attendanceToRemove = LocalDate(2026, 10, 5)
        val lastAttendance = LocalDate(2026, 10, 9)
        val studentPackage =
            repository.create(
                studentId = studentId,
                packageType = packageType,
                startedOn = firstAttendance,
            )
        val otherStudentPackage =
            repository.create(
                studentId = otherStudentId,
                packageType = packageType,
                startedOn = firstAttendance,
            )

        repository.recordAttendance(studentPackage.id, attendanceToRemove)
        repository.recordAttendance(studentPackage.id, lastAttendance)
        repository.recordAttendance(otherStudentPackage.id, attendanceToRemove)

        // When
        repository.removeAttendance(
            packageId = studentPackage.id,
            attendedOn = attendanceToRemove,
        )

        // Then: the remaining history and package details are preserved
        val expectedPackage =
            studentPackage.copy(
                attendanceHistory =
                AttendanceHistory(
                    visitsByDate =
                    mapOf(
                        firstAttendance to 1,
                        lastAttendance to 1,
                    ),
                ),
            )
        val reloadedPackage =
            assertNotNull(repository.getLatestPackageForStudent(studentId))

        assertEquals(expectedPackage, reloadedPackage)
        assertEquals(2, reloadedPackage.usedClasses)
        assertEquals(
            listOf(firstAttendance.toEpochDays(), lastAttendance.toEpochDays()),
            database.attendanceDao().getForPackage(studentPackage.id)
                .map { it.attendedOnEpochDay },
        )

        // And: the same date remains recorded for another package
        assertEquals(
            otherStudentPackage.copy(
                attendanceHistory =
                AttendanceHistory(
                    visitsByDate =
                    mapOf(
                        firstAttendance to 1,
                        attendanceToRemove to 1,
                    ),
                ),
            ),
            repository.getLatestPackageForStudent(otherStudentId),
        )

        // When: removal is repeated
        repository.removeAttendance(
            packageId = studentPackage.id,
            attendedOn = attendanceToRemove,
        )

        // Then: the remaining attendance is unchanged
        assertEquals(
            expectedPackage,
            repository.getLatestPackageForStudent(studentId),
        )
    }
}
