package com.theunderseer.simplio.feature.attendance.domain.model

import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentPackage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnlimitedStudentPackageTest {
    @Test
    fun `unlimited private packages finish by actual visits rather than elapsed schedule dates`() {
        AttendanceTestData.privatePackages.forEach { type ->
            val dates = AttendanceTestData.packageClassDates.take(type.totalClasses)
            val unfinished = testStudentPackage(
                packageType = type,
                visitsByDate = dates.dropLast(1).associateWith { 1 },
            )
            assertNull(unfinished.expiresOn)
            assertFalse(unfinished.isCompleted)
            assertEquals(1, unfinished.remainingClasses)
            assertTrue(unfinished.isActiveOn(LocalDate(2030, 1, 1)))
            val completed = unfinished.copy(attendanceHistory = PackageAttendanceHistory(dates.associateWith { 1 }))
            assertTrue(completed.isCompleted)
            assertEquals(0, completed.remainingClasses)
            assertFalse(completed.isActiveOn(dates.last()))
        }
    }

    @Test
    fun `skipping scheduled dates does not consume credits and private pair still costs one`() {
        val pack = testStudentPackage(
            packageType = AttendanceTestData.privateEightClassPackage,
            visitsByDate = mapOf(AttendanceTestData.firstClassDate to 1, AttendanceTestData.fourthClassDate to 1),
        )
        assertEquals(2, pack.usedClasses)
        assertEquals(6, pack.remainingClasses)
        assertFalse(pack.allowsVisitCount(2))
        assertNull(pack.expiresOn)
    }

    @Test
    fun `finite custom package expires from activation independently of capacity`() {
        val pack = testStudentPackage(
            packageType = AttendanceTestData.privateEightClassPackage.copy(validityWeeks = 6),
        )
        assertEquals(AttendanceTestData.class13Date, pack.expiresOn)
        assertFalse(pack.isExpiredOn(AttendanceTestData.class13Date))
        assertTrue(pack.isExpiredOn(AttendanceTestData.class14Date))
    }
}
