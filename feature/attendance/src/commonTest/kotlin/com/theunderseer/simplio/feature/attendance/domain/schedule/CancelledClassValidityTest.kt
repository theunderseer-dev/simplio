package com.theunderseer.simplio.feature.attendance.domain.schedule

import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentPackage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CancelledClassValidityTest {
    @Test
    fun `expiration advances Friday to Monday and Monday to Friday without increasing credits`() {
        val schedule = DefaultClassSchedule.schedule
        assertEquals(AttendanceTestData.class10Date, schedule.nextValidityDate(AttendanceTestData.class9Date))
        assertEquals(AttendanceTestData.class11Date, schedule.nextValidityDate(AttendanceTestData.class10Date))
        val pack = testStudentPackage(validityExtensions = mapOf(ClassAttendanceTestData.session.id to 3))
        assertEquals(AttendanceTestData.class10Date, pack.expiresOn)
        assertEquals(AttendanceTestData.fourClassPackage, pack.packageType)
        assertEquals(1, pack.usedClasses)
        assertEquals(3, pack.remainingClasses)
    }

    @Test
    fun `eligibility uses activation expiry and credits as of the cancelled class`() {
        val pack = testStudentPackage(
            visitsByDate = mapOf(
                AttendanceTestData.firstClassDate to 1,
                AttendanceTestData.thirdClassDate to 1,
                AttendanceTestData.fourthClassDate to 1,
                AttendanceTestData.fifthClassDate to 1,
            ),
        )
        assertTrue(pack.isCompleted)
        assertTrue(pack.isEligibleForCancellationOn(AttendanceTestData.secondClassDate))
        assertFalse(pack.isEligibleForCancellationOn(AttendanceTestData.sixthClassDate))
        assertFalse(pack.isEligibleForCancellationOn(AttendanceTestData.class10Date))
        assertFalse(
            testStudentPackage(startedOn = null).isEligibleForCancellationOn(
                AttendanceTestData.secondClassDate,
            ),
        )
        assertFalse(
            testStudentPackage(
                packageType = AttendanceTestData.privateEightClassPackage,
            ).isEligibleForCancellationOn(AttendanceTestData.secondClassDate),
        )
        assertTrue(testStudentPackage().isEligibleForCancellationOn(AttendanceTestData.class9Date))
    }
}
