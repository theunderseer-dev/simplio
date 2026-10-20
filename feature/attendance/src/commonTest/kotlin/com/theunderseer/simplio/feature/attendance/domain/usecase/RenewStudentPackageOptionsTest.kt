package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeTransactionRunner
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testCompletedPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentPackage
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenewStudentPackageOptionsTest {
    private val student = AttendanceTestData.student.copy(note = "Return change")
    private val previous = testCompletedPackage(student)
    private val date = AttendanceTestData.sixthClassDate

    @Test
    fun `renewal without first attendance keeps activation and expiration unset`() = runTest {
        val fixture = Fixture()
        val result = fixture.renew(
            student.id,
            previous.id,
            AttendanceTestData.fiveClassPackage,
            date,
            note = " Keep this note ",
            recordFirstAttendance = false,
        )
        val pack = assertIs<RenewStudentPackageResult.Success>(result).studentPackage
        assertEquals(date, pack.purchasedOn)
        assertNull(pack.startedOn)
        assertNull(pack.expiresOn)
        assertEquals(0, pack.usedClasses)
        assertEquals(5, pack.remainingClasses)
        assertEquals("Keep this note", fixture.students.students.single().note)
        assertEquals(student.fullName, fixture.students.students.single().fullName)
        assertEquals(student.groupId, fixture.students.students.single().groupId)
        assertEquals(previous, fixture.packages.packages.first())
        assertEquals(pack, fixture.packages.getLatestPackageForStudent(student.id))
    }

    @Test
    fun `duo renewal records the selected two visits`() = runTest {
        val fixture = Fixture()
        val result = fixture.renew(
            student.id,
            previous.id,
            AttendanceTestData.duoSpecialPackage,
            date,
            student.note,
            visitCount = 2,
        )
        val pack = assertIs<RenewStudentPackageResult.Success>(result).studentPackage
        assertEquals(mapOf(date to 2), pack.attendanceHistory.visitsByDate)
        assertEquals(2, pack.usedClasses)
        assertEquals(6, pack.remainingClasses)
        assertEquals(date, pack.startedOn)
    }

    @Test
    fun `reviewed note can be explicitly cleared during renewal`() = runTest {
        val fixture = Fixture()
        assertIs<RenewStudentPackageResult.Success>(
            fixture.renew(
                student.id,
                previous.id,
                AttendanceTestData.fourClassPackage,
                date,
                note = "   ",
            ),
        )
        assertEquals("", fixture.students.students.single().note)
    }

    @Test
    fun `invalid first visit count changes neither note nor packages`() = runTest {
        for (count in listOf(0, 3)) {
            val fixture = Fixture()
            assertIs<RenewStudentPackageResult.InvalidVisitCount>(
                fixture.renew(
                    student.id,
                    previous.id,
                    AttendanceTestData.duoSpecialPackage,
                    date,
                    "Changed",
                    visitCount = count,
                ),
            )
            assertEquals(listOf(previous), fixture.packages.packages)
            assertEquals(student, fixture.students.students.single())
        }
    }

    @Test
    fun `visit count is ignored when first attendance is unchecked`() = runTest {
        val fixture = Fixture()
        val result = fixture.renew(
            student.id,
            previous.id,
            AttendanceTestData.fourClassPackage,
            date,
            student.note,
            visitCount = 0,
            recordFirstAttendance = false,
        )
        assertEquals(0, assertIs<RenewStudentPackageResult.Success>(result).studentPackage.usedClasses)
    }

    @Test
    fun `stale dialog cannot renew a different latest package even when it is completed`() = runTest {
        val latest = testCompletedPackage(student, id = 2).copy(purchasedOn = AttendanceTestData.fifthClassDate)
        val fixture = Fixture(listOf(previous, latest))
        assertIs<RenewStudentPackageResult.PackageChanged>(
            fixture.renew(
                student.id,
                previous.id,
                AttendanceTestData.fourClassPackage,
                date,
                "Changed",
            ),
        )
        assertEquals(listOf(previous, latest), fixture.packages.packages)
        assertEquals(student, fixture.students.students.single())
    }

    @Test
    fun `retrying the same renewal cannot create another new package`() = runTest {
        val fixture = Fixture()
        assertIs<RenewStudentPackageResult.Success>(
            fixture.renew(
                student.id,
                previous.id,
                AttendanceTestData.fourClassPackage,
                date,
                student.note,
                recordFirstAttendance = false,
            ),
        )
        assertIs<RenewStudentPackageResult.PackageChanged>(
            fixture.renew(
                student.id,
                previous.id,
                AttendanceTestData.fourClassPackage,
                date,
                student.note,
                recordFirstAttendance = false,
            ),
        )
        assertEquals(2, fixture.packages.packages.size)
    }

    @Test
    fun `renewal before the previous last attendance is rejected`() = runTest {
        val fixture = Fixture()
        val result = fixture.renew(
            student.id,
            previous.id,
            AttendanceTestData.fourClassPackage,
            AttendanceTestData.thirdClassDate,
            "Changed",
        )
        assertEquals(
            previous.attendanceDates.max(),
            assertIs<RenewStudentPackageResult.RenewalDateTooEarly>(result).earliestAllowedDate,
        )
        assertEquals(listOf(previous), fixture.packages.packages)
        assertEquals(student, fixture.students.students.single())
    }

    @Test
    fun `purchase date ordering cannot make renewed package older than previous purchase`() = runTest {
        val purchasedLater = previous.copy(purchasedOn = date)
        val fixture = Fixture(listOf(purchasedLater))
        assertIs<RenewStudentPackageResult.RenewalDateTooEarly>(
            fixture.renew(
                student.id,
                previous.id,
                AttendanceTestData.fourClassPackage,
                AttendanceTestData.fifthClassDate,
                student.note,
            ),
        )
        assertEquals(listOf(purchasedLater), fixture.packages.packages)
    }

    @Test
    fun `missing package does not create a purchase or change note`() = runTest {
        val fixture = Fixture(emptyList())
        assertIs<RenewStudentPackageResult.PackageNotFound>(
            fixture.renew(
                student.id,
                previous.id,
                AttendanceTestData.fourClassPackage,
                date,
                "Changed",
            ),
        )
        assertTrue(fixture.packages.packages.isEmpty())
        assertEquals(student, fixture.students.students.single())
    }

    @Test
    fun `deleted student cannot receive a new purchase`() = runTest {
        val fixture = Fixture()
        fixture.students.delete(student.id)
        assertIs<RenewStudentPackageResult.StudentNotFound>(
            fixture.renew(
                student.id,
                previous.id,
                AttendanceTestData.fourClassPackage,
                date,
                student.note,
            ),
        )
        assertEquals(listOf(previous), fixture.packages.packages)
    }

    @Test
    fun `expired incomplete package can renew only after its inclusive expiration date`() = runTest {
        val old = testStudentPackage(studentId = student.id, startedOn = LocalDate(2026, 8, 1))
        val fixture = Fixture(listOf(old))
        val expiry = checkNotNull(old.expiresOn)
        assertIs<RenewStudentPackageResult.ActivePackageExists>(
            fixture.renew(
                student.id,
                old.id,
                AttendanceTestData.fourClassPackage,
                expiry,
                student.note,
            ),
        )
        assertEquals(LocalDate(2026, 8, 30), old.earliestRenewalDate)
        assertIs<RenewStudentPackageResult.Success>(
            fixture.renew(
                student.id,
                old.id,
                AttendanceTestData.fourClassPackage,
                old.earliestRenewalDate,
                student.note,
            ),
        )
    }

    private inner class Fixture(
        initialPackages: List<StudentPackage> =
            listOf(previous),
    ) {
        val students = FakeStudentRepository().also { it.seed(student) }
        val packages = FakeStudentPackageRepository(initialPackages)
        val renew = RenewStudentPackageUseCase(packages, students, FakeTransactionRunner())
    }
}
