package com.theunderseer.simplio.feature.attendance.data.local.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.model.ClassAttendanceRow
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.domain.model.PackagePlan
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
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

class ClassHistoryPersistenceTest {
    private val firstClassDate = LocalDate(2026, 10, 2)
    private val secondClassDate = LocalDate(2026, 10, 5)
    private val latestClassDate = LocalDate(2026, 10, 9)

    private lateinit var database: AttendanceDatabase
    private lateinit var packageTypes: Map<PackagePlan, PackageType>

    @BeforeTest
    fun setUp() = runTest {
        database = Room
            .inMemoryDatabaseBuilder<AttendanceDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()

        DefaultStudentGroups.ALL.forEach { group ->
            database.studentGroupDao().insert(
                testStudentGroupEntity(
                    id = group.id,
                    name = group.name,
                ),
            )
        }

        packageTypes = PackagePlan.entries.associateWith { plan ->
            val template = testClassPackage(plan = plan)

            val id = database.packageTypeDao().insert(
                testPackageTypeEntity(packageType = template),
            ).toInt()

            template.copy(id = id)
        }
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `returns empty history when no attendance is recorded`() = runTest {
        assertEquals(emptyList(), historyPage())
    }

    @Test
    fun `returns students from both groups ordered by class date and name`() = runTest {
        // Given
        val anna = createStudent(
            fullName = "Anna Smith",
            groupId = DefaultStudentGroups.GROUP_A_ID,
        )
        val bob = createStudent(
            fullName = "Bob Lee",
            groupId = DefaultStudentGroups.GROUP_B_ID,
        )

        val annaPackageId = createPackage(student = anna)
        val bobPackageId = createPackage(
            student = bob,
            plan = PackagePlan.DUO_SPECIAL,
        )

        record(annaPackageId, firstClassDate)
        record(annaPackageId, latestClassDate)
        record(bobPackageId, firstClassDate, visitCount = 2)
        record(bobPackageId, secondClassDate)

        // When
        val rows = historyPage()

        // Then
        assertEquals(
            listOf(latestClassDate, secondClassDate, firstClassDate),
            rows.map {
                LocalDate.fromEpochDays(it.attendedOnEpochDay)
            }.distinct(),
        )

        assertEquals(
            listOf(anna.id, bob.id, anna.id, bob.id),
            rows.map { it.studentId },
        )

        val firstClassRows = rows.filter {
            it.attendedOnEpochDay == firstClassDate.toEpochDays()
        }

        assertEquals(
            listOf(anna.fullName, bob.fullName),
            firstClassRows.map { it.fullName },
        )
        assertEquals(
            listOf(anna.groupId, bob.groupId),
            firstClassRows.map { it.groupId },
        )
        assertEquals(
            listOf(1, 2),
            firstClassRows.map { it.visitCount },
        )
    }

    @Test
    fun `includes attendance from previous student packages`() = runTest {
        // Given
        val student = createStudent(fullName = "Anna Smith")
        val oldClassDate = LocalDate(2026, 8, 3)

        val oldPackageId = createPackage(
            student = student,
            startedOn = oldClassDate,
        )
        val latestPackageId = createPackage(student = student)

        record(oldPackageId, oldClassDate)
        record(latestPackageId, firstClassDate)

        // When
        val rows = historyPage()

        // Then
        assertEquals(
            listOf(firstClassDate, oldClassDate),
            rows.map {
                LocalDate.fromEpochDays(it.attendedOnEpochDay)
            },
        )
        assertEquals(
            listOf(student.id, student.id),
            rows.map { it.studentId },
        )
    }

    @Test
    fun `pagination limits class dates and keeps all students of each class`() = runTest {
        // Given
        val students = listOf(
            createStudent(fullName = "Anna Smith"),
            createStudent(
                fullName = "Bob Lee",
                groupId = DefaultStudentGroups.GROUP_B_ID,
            ),
            createStudent(fullName = "Charlie Brown"),
        )

        val packageIds = students.map { student ->
            createPackage(student = student)
        }

        packageIds.forEach { packageId ->
            record(packageId, firstClassDate)
            record(packageId, latestClassDate)
        }
        record(packageIds.first(), secondClassDate)

        // When
        val firstPage = historyPage(limit = 2)

        // Then
        assertEquals(
            listOf(
                latestClassDate.toEpochDays(),
                secondClassDate.toEpochDays(),
            ),
            firstPage.map { it.attendedOnEpochDay }.distinct(),
        )
        assertEquals(
            students.map { it.id },
            firstPage
                .filter {
                    it.attendedOnEpochDay == latestClassDate.toEpochDays()
                }
                .map { it.studentId },
        )

        // When
        val secondPage = historyPage(
            beforeDate = LocalDate.fromEpochDays(
                firstPage.last().attendedOnEpochDay,
            ),
            limit = 2,
        )

        // Then
        assertEquals(
            listOf(firstClassDate.toEpochDays()),
            secondPage.map { it.attendedOnEpochDay }.distinct(),
        )
        assertEquals(
            students.map { it.id },
            secondPage.map { it.studentId },
        )
        assertEquals(
            emptyList(),
            historyPage(
                beforeDate = firstClassDate,
                limit = 2,
            ),
        )
    }

    @Test
    fun `combines records for same student and date across packages`() = runTest {
        // Given
        val student = createStudent(fullName = "Anna Smith")
        val firstPackageId = createPackage(student = student)
        val secondPackageId = createPackage(student = student)

        record(firstPackageId, latestClassDate)
        record(secondPackageId, latestClassDate)

        // When
        val row = historyPage().single()

        // Then
        assertEquals(student.id, row.studentId)
        assertEquals(student.fullName, row.fullName)
        assertEquals(latestClassDate.toEpochDays(), row.attendedOnEpochDay)
        assertEquals(2, row.visitCount)
    }

    @Test
    fun `repository maps class dates students and visit counts`() = runTest {
        // Given
        val student = createStudent(
            fullName = "Anna & John",
            groupId = DefaultStudentGroups.GROUP_B_ID,
        )
        val packageId = createPackage(
            student = student,
            plan = PackagePlan.DUO_SPECIAL,
        )

        record(packageId, firstClassDate, visitCount = 2)
        record(packageId, latestClassDate, visitCount = 1)

        val repository = RoomClassHistoryRepository(
            attendanceDao = database.attendanceDao(),
        )

        // When
        val entries = repository.getPage(
            beforeDate = null,
            limit = 2,
        )

        // Then
        assertEquals(
            listOf(latestClassDate, firstClassDate),
            entries.map { it.date },
        )

        val expectedStudent = testStudent(
            id = student.id,
            fullName = student.fullName,
            groupId = student.groupId,
        )

        assertEquals(
            listOf(expectedStudent, expectedStudent),
            entries.map { it.students.single().student },
        )
        assertEquals(
            listOf(1, 2),
            entries.map { it.students.single().visitCount },
        )
    }

    private suspend fun createStudent(
        fullName: String,
        groupId: Int = DefaultStudentGroups.GROUP_A_ID,
    ): StudentEntity {
        val entity = testStudentEntity(
            fullName = fullName,
            groupId = groupId,
        )
        val id = database.studentDao().insert(entity).toInt()

        return entity.copy(id = id)
    }

    private suspend fun createPackage(
        student: StudentEntity,
        startedOn: LocalDate = firstClassDate,
        plan: PackagePlan = PackagePlan.FOUR_CLASSES,
    ): Int = database.studentPackageDao().insert(
        testStudentPackageEntity(
            studentId = student.id,
            packageType = packageTypes.getValue(plan),
            startedOnEpochDay = startedOn.toEpochDays(),
        ),
    ).toInt()

    private suspend fun record(packageId: Int, date: LocalDate, visitCount: Int = 1) {
        database.attendanceDao().insert(
            AttendanceEntity(
                packageId = packageId,
                attendedOnEpochDay = date.toEpochDays(),
                visitCount = visitCount,
            ),
        )
    }

    private suspend fun historyPage(beforeDate: LocalDate? = null, limit: Int = 20): List<ClassAttendanceRow> =
        database.attendanceDao().getClassHistoryPage(
            beforeEpochDay = beforeDate?.toEpochDays(),
            limit = limit,
        )
}
