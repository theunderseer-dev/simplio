package com.theunderseer.simplio.feature.attendance.data.local.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetClassHistoryUseCase
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testPackageTypeEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentGroupEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentPackageEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassHistorySchedulePersistenceTest {
    private lateinit var database: AttendanceDatabase
    private lateinit var repository: RoomClassHistoryRepository
    private var packageId = 0

    @BeforeTest
    fun setUp() = runTest {
        database = Room
            .inMemoryDatabaseBuilder<AttendanceDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        repository = RoomClassHistoryRepository(attendanceDao = database.attendanceDao())

        val groupId = database.studentGroupDao().insert(testStudentGroupEntity()).toInt()
        val studentId = database.studentDao().insert(testStudentEntity(groupId = groupId)).toInt()
        val template = AttendanceTestData.duoSpecialPackage
        val typeId = database.packageTypeDao().insert(testPackageTypeEntity(packageType = template)).toInt()

        packageId = database.studentPackageDao().insert(
            testStudentPackageEntity(
                studentId = studentId,
                packageType = template.copy(id = typeId),
                startedOnEpochDay = AttendanceTestData.firstClassDate.toEpochDays(),
            ),
        ).toInt()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `scheduled pages fill gaps without creating attendance records and preserve duo visit counts`() = runTest {
        record(date = AttendanceTestData.firstClassDate, visitCount = 2)
        record(date = AttendanceTestData.thirdClassDate, visitCount = 1)
        val storedAttendance = database.attendanceDao().getForPackage(packageId)
        val getHistory = GetClassHistoryUseCase(repository)

        val firstPage = getHistory(currentDate = AttendanceTestData.eighthClassDate)

        assertEquals(
            listOf(
                AttendanceTestData.eighthClassDate,
                AttendanceTestData.seventhClassDate,
                AttendanceTestData.sixthClassDate,
                AttendanceTestData.fifthClassDate,
                AttendanceTestData.fourthClassDate,
            ),
            firstPage.entries.map { it.date },
        )
        assertTrue(firstPage.entries.all { it.students.isEmpty() })
        assertEquals(AttendanceTestData.fourthClassDate, firstPage.nextBeforeDate)

        val lastPage = getHistory(
            currentDate = AttendanceTestData.eighthClassDate,
            beforeDate = firstPage.nextBeforeDate,
        )

        assertEquals(
            listOf(
                AttendanceTestData.thirdClassDate,
                AttendanceTestData.secondClassDate,
                AttendanceTestData.firstClassDate,
            ),
            lastPage.entries.map { it.date },
        )
        assertEquals(1, lastPage.entries.first().students.single().visitCount)
        assertTrue(lastPage.entries[1].students.isEmpty())
        assertEquals(2, lastPage.entries.last().students.single().visitCount)
        assertNull(lastPage.nextBeforeDate)
        assertEquals(storedAttendance, database.attendanceDao().getForPackage(packageId))
    }

    @Test
    fun `first recorded date and history boundary update after attendance is removed`() = runTest {
        record(date = AttendanceTestData.firstClassDate, visitCount = 2)
        record(date = AttendanceTestData.thirdClassDate, visitCount = 1)
        val getHistory = GetClassHistoryUseCase(repository)

        assertEquals(AttendanceTestData.firstClassDate, repository.getFirstAttendanceDate())

        database.attendanceDao().deleteForPackageOnDate(
            packageId = packageId,
            attendedOnEpochDay = AttendanceTestData.firstClassDate.toEpochDays(),
        )

        assertEquals(AttendanceTestData.thirdClassDate, repository.getFirstAttendanceDate())
        assertEquals(
            listOf(AttendanceTestData.thirdClassDate),
            getHistory(currentDate = AttendanceTestData.thirdClassDate).entries.map { it.date },
        )

        database.attendanceDao().deleteForPackageOnDate(
            packageId = packageId,
            attendedOnEpochDay = AttendanceTestData.thirdClassDate.toEpochDays(),
        )

        assertNull(repository.getFirstAttendanceDate())
        val emptyPage = getHistory(currentDate = AttendanceTestData.eighthClassDate)
        assertTrue(emptyPage.entries.isEmpty())
        assertNull(emptyPage.nextBeforeDate)
    }

    private suspend fun record(date: LocalDate, visitCount: Int) {
        database.attendanceDao().insert(
            AttendanceEntity(
                packageId = packageId,
                attendedOnEpochDay = date.toEpochDays(),
                visitCount = visitCount,
            ),
        )
    }
}
