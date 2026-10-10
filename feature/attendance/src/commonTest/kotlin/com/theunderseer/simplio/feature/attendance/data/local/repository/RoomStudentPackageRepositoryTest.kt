package com.theunderseer.simplio.feature.attendance.data.local.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
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
    fun `recording duplicate attendance preserves original visit count`() = runTest {
        // Given
        val studentId =
            database.studentDao().insert(
                StudentEntity(fullName = "Anna & John"),
            ).toInt()

        val packageTypeId =
            database.packageTypeDao().insert(
                PackageTypeEntity(
                    name = "Duo 8 · Special rate",
                    totalClasses = 8,
                    validityWeeks = 4,
                    price = 2400,
                ),
            ).toInt()

        val packageType =
            PackageType(
                id = packageTypeId,
                name = "Duo 8 · Special rate",
                totalClasses = 8,
                validityWeeks = 4,
                price = 2400,
                maxVisitsPerAttendance = 2,
            )

        val startedOn = LocalDate(2026, 10, 5)
        val attendedOn = LocalDate(2026, 10, 9)

        val studentPackage =
            repository.create(
                studentId = studentId,
                packageType = packageType,
                startedOn = startedOn,
            )

        repository.recordAttendance(
            packageId = studentPackage.id,
            attendedOn = attendedOn,
            visitCount = 2,
        )

        // When
        repository.recordAttendance(
            packageId = studentPackage.id,
            attendedOn = attendedOn,
            visitCount = 1,
        )

        // Then
        assertEquals(
            listOf(
                AttendanceEntity(
                    packageId = studentPackage.id,
                    attendedOnEpochDay = startedOn.toEpochDays(),
                    visitCount = 1,
                ),
                AttendanceEntity(
                    packageId = studentPackage.id,
                    attendedOnEpochDay = attendedOn.toEpochDays(),
                    visitCount = 2,
                ),
            ),
            database.attendanceDao().getForPackage(studentPackage.id),
        )

        val reloadedPackage =
            assertNotNull(
                repository.getLatestPackageForStudent(studentId),
            )

        assertEquals(
            mapOf(
                startedOn to 1,
                attendedOn to 2,
            ),
            reloadedPackage.attendanceHistory.visitsByDate,
        )

        assertEquals(
            3,
            reloadedPackage.usedClasses,
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
    fun `removing attendance restores all visits for requested date and preserves other history`() = runTest {
        // Given
        val studentId =
            database.studentDao().insert(
                StudentEntity(fullName = "Anna & John"),
            ).toInt()

        val otherStudentId =
            database.studentDao().insert(
                StudentEntity(fullName = "Other couple"),
            ).toInt()

        val packageTypeId =
            database.packageTypeDao().insert(
                PackageTypeEntity(
                    name = "Duo 8 · Special rate",
                    totalClasses = 8,
                    validityWeeks = 4,
                    price = 2400,
                ),
            ).toInt()

        val packageType =
            PackageType(
                id = packageTypeId,
                name = "Duo 8 · Special rate",
                totalClasses = 8,
                validityWeeks = 4,
                price = 2400,
                maxVisitsPerAttendance = 2,
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

        repository.recordAttendance(
            packageId = studentPackage.id,
            attendedOn = attendanceToRemove,
            visitCount = 2,
        )

        repository.recordAttendance(
            packageId = studentPackage.id,
            attendedOn = lastAttendance,
            visitCount = 1,
        )

        repository.recordAttendance(
            packageId = otherStudentPackage.id,
            attendedOn = attendanceToRemove,
            visitCount = 2,
        )

        val packageBeforeRemoval =
            assertNotNull(
                repository.getLatestPackageForStudent(studentId),
            )

        assertEquals(4, packageBeforeRemoval.usedClasses)
        assertEquals(4, packageBeforeRemoval.remainingClasses)

        // When
        repository.removeAttendance(
            packageId = studentPackage.id,
            attendedOn = attendanceToRemove,
        )

        // Then
        val expectedPackage =
            studentPackage.copy(
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate =
                    mapOf(
                        firstAttendance to 1,
                        lastAttendance to 1,
                    ),
                ),
            )

        val reloadedPackage =
            assertNotNull(
                repository.getLatestPackageForStudent(studentId),
            )

        assertEquals(expectedPackage, reloadedPackage)
        assertEquals(2, reloadedPackage.usedClasses)
        assertEquals(6, reloadedPackage.remainingClasses)

        assertEquals(
            listOf(
                AttendanceEntity(
                    packageId = studentPackage.id,
                    attendedOnEpochDay = firstAttendance.toEpochDays(),
                    visitCount = 1,
                ),
                AttendanceEntity(
                    packageId = studentPackage.id,
                    attendedOnEpochDay = lastAttendance.toEpochDays(),
                    visitCount = 1,
                ),
            ),
            database.attendanceDao().getForPackage(studentPackage.id),
        )

        // And: another package retains both visits for the same date
        val expectedOtherPackage =
            otherStudentPackage.copy(
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate =
                    mapOf(
                        firstAttendance to 1,
                        attendanceToRemove to 2,
                    ),
                ),
            )

        assertEquals(
            expectedOtherPackage,
            repository.getLatestPackageForStudent(otherStudentId),
        )

        // When: removal is repeated
        repository.removeAttendance(
            packageId = studentPackage.id,
            attendedOn = attendanceToRemove,
        )

        // Then: no additional visits are restored
        assertEquals(
            expectedPackage,
            repository.getLatestPackageForStudent(studentId),
        )
    }

    @Test
    fun `reading package preserves visit counts for each attendance date`() = runTest {
        // Given
        val studentId =
            database.studentDao().insert(
                StudentEntity(fullName = "Anna & John"),
            ).toInt()

        val packageTypeId =
            database.packageTypeDao().insert(
                PackageTypeEntity(
                    name = "Duo 8 · Special rate",
                    totalClasses = 8,
                    validityWeeks = 4,
                    price = 2400,
                ),
            ).toInt()

        val packageType =
            PackageType(
                id = packageTypeId,
                name = "Duo 8 · Special rate",
                totalClasses = 8,
                validityWeeks = 4,
                price = 2400,
            )

        val startedOn = LocalDate(2026, 10, 5)
        val secondAttendance = LocalDate(2026, 10, 9)

        val studentPackage =
            repository.create(
                studentId = studentId,
                packageType = packageType,
                startedOn = startedOn,
            )

        database.attendanceDao().insert(
            AttendanceEntity(
                packageId = studentPackage.id,
                attendedOnEpochDay = secondAttendance.toEpochDays(),
                visitCount = 2,
            ),
        )

        // When
        val reloadedPackage =
            assertNotNull(
                repository.getLatestPackageForStudent(studentId),
            )

        // Then
        assertEquals(
            mapOf(
                startedOn to 1,
                secondAttendance to 2,
            ),
            reloadedPackage.attendanceHistory.visitsByDate,
        )

        assertEquals(
            3,
            reloadedPackage.usedClasses,
        )
    }

    @Test
    fun `recording attendance persists requested visit count`() = runTest {
        // Given
        val studentId =
            database.studentDao().insert(
                StudentEntity(fullName = "Anna & John"),
            ).toInt()

        val packageTypeId =
            database.packageTypeDao().insert(
                PackageTypeEntity(
                    name = "Duo 8 · Special rate",
                    totalClasses = 8,
                    validityWeeks = 4,
                    price = 2400,
                ),
            ).toInt()

        val packageType =
            PackageType(
                id = packageTypeId,
                name = "Duo 8 · Special rate",
                totalClasses = 8,
                validityWeeks = 4,
                price = 2400,
                maxVisitsPerAttendance = 2,
            )

        val startedOn = LocalDate(2026, 10, 5)
        val attendedOn = LocalDate(2026, 10, 9)

        val studentPackage =
            repository.create(
                studentId = studentId,
                packageType = packageType,
                startedOn = startedOn,
            )

        // When
        repository.recordAttendance(
            packageId = studentPackage.id,
            attendedOn = attendedOn,
            visitCount = 2,
        )

        // Then
        val reloadedPackage =
            assertNotNull(
                repository.getLatestPackageForStudent(studentId),
            )

        assertEquals(
            mapOf(
                startedOn to 1,
                attendedOn to 2,
            ),
            reloadedPackage.attendanceHistory.visitsByDate,
        )

        assertEquals(3, reloadedPackage.usedClasses)
    }

    @Test
    fun `reading purchased package preserves maximum visits per attendance`() = runTest {
        // Given
        val studentId =
            database.studentDao().insert(
                StudentEntity(fullName = "Anna & John"),
            ).toInt()

        val packageTypeId =
            database.packageTypeDao().insert(
                PackageTypeEntity(
                    name = "Duo 8 · Special rate",
                    totalClasses = 8,
                    validityWeeks = 4,
                    price = 2400,
                    maxVisitsPerAttendance = 2,
                ),
            ).toInt()

        val packageType =
            PackageType(
                id = packageTypeId,
                name = "Duo 8 · Special rate",
                totalClasses = 8,
                validityWeeks = 4,
                price = 2400,
                maxVisitsPerAttendance = 2,
            )

        repository.create(
            studentId = studentId,
            packageType = packageType,
            startedOn = LocalDate(2026, 10, 5),
        )

        // When
        val reloadedPackage =
            assertNotNull(
                repository.getLatestPackageForStudent(studentId),
            )

        // Then
        assertEquals(
            packageType,
            reloadedPackage.packageType,
        )
    }
}
