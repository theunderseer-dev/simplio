package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.CloudAttendanceRoster
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.fake.FakeAttendanceCloud
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CloudAttendanceRosterTest {
    @Test
    fun `reads a duo purchase and uses the schedule date for renewal`() = runTest {
        withSyncDatabases { desktop, mobile ->
            val registered = desktop.registerSyncStudent(packageType = AttendanceTestData.duoSpecialPackage)
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(desktop)
            val roster = CloudAttendanceRoster(mobile, cloud.target, cloud)

            val active = roster.getAll().toRows(Instant.parse("2026-10-02T10:00:00Z")).single()
            assertEquals(registered.student.fullName, active.registeredStudent.student.fullName)
            assertEquals(2, active.registeredStudent.studentPackage.usedClasses)
            assertEquals(8, active.registeredStudent.studentPackage.packageType.totalClasses)
            assertEquals(registered.studentPackage.expiresOn, active.registeredStudent.studentPackage.expiresOn)
            assertEquals(AttendanceCheckState.Checked, active.checkState)
            assertFalse(active.requiresRenewal)

            val expired = roster.getAll().toRows(Instant.parse("2026-10-30T17:00:00Z")).single()
            assertTrue(expired.requiresRenewal)
            assertEquals(1, cloud.downloads)

            RoomStudentPackageRepository(desktop).create(
                studentId = registered.student.id,
                packageType = registered.studentPackage.packageType,
                purchasedOn = LocalDate(2026, 11, 2),
                initialAttendance = null,
            )
            cloud.uploadPending(desktop)
            val renewed = roster.getAll().toRows(Instant.parse("2026-11-02T10:00:00Z")).single()
            assertEquals(0, renewed.registeredStudent.studentPackage.usedClasses)
            assertNull(renewed.registeredStudent.studentPackage.expiresOn)
            assertFalse(renewed.requiresRenewal)
            assertEquals(2, cloud.downloads)
            assertEquals(0, mobile.syncPullDao().pendingCount())
        }
    }

    @Test
    fun `empty cloud account has no roster rows`() = runTest {
        withSyncDatabases { _, mobile ->
            val cloud = FakeAttendanceCloud()
            val roster = CloudAttendanceRoster(mobile, cloud.target, cloud)
            assertTrue(roster.getAll().attendance.entries.isEmpty())
        }
    }
}
