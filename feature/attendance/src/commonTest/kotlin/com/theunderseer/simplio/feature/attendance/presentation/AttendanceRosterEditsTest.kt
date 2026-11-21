package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomAttendanceRoster
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassScheduleRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomPackageTypeRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.fake.FakeAttendanceCloud
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

class AttendanceRosterEditsTest {
    @Test
    fun `mobile check and renewal update Room and upload the same purchases and visits desktop reads`() = runTest {
        withSyncDatabases { desktop, mobile ->
            desktop.registerSyncStudent(
                recordFirstAttendance = false,
                packageType = AttendanceTestData.duoSpecialPackage,
            )
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(desktop)
            RoomPullStore(mobile, cloud.target).refreshFrom(cloud)
            val roster = RoomAttendanceRoster(mobile)
            var now = Instant.parse("2026-10-02T10:00:00Z")
            val edits = AttendanceRosterEdits(
                RoomTransactionRunner(mobile),
                RoomStudentPackageRepository(mobile),
                RoomPackageTypeRepository(mobile.packageTypeDao()),
                RoomClassScheduleRepository(mobile),
                RoomStudentRepository(mobile),
                object : Clock {
                    override fun now() = now
                },
            )

            val reviewed = roster.getAll().attendance.entries.single()
            assertNotNull(edits.apply(AttendanceRosterEdit.Mark(reviewed, LocalDate(2026, 10, 5), 2)))
            assertEquals(0, mobile.syncPullDao().pendingCount())
            assertNull(edits.apply(AttendanceRosterEdit.Mark(reviewed, AttendanceTestData.firstClassDate, 2)))
            val attended = roster.getAll().attendance.entries.single()
            assertEquals(2, attended.studentPackage.usedClasses)
            assertEquals(LocalDate(2026, 10, 30), attended.studentPackage.expiresOn)
            cloud.uploadPending(mobile)
            RoomPullStore(desktop, cloud.target).refreshFrom(cloud)
            val peerAfterCheck = RoomAttendanceRoster(desktop).getAll().attendance.entries.single()
            assertEquals(2, peerAfterCheck.studentPackage.usedClasses)

            now = Instant.parse("2026-11-02T10:00:00Z")
            val template = roster.getAll().packageTypes.single { it.id == attended.studentPackage.packageType.id }
            val purchaseDate = LocalDate(2026, 11, 2)
            val renewal = AttendanceRosterEdit.Renew(attended, purchaseDate, template, 1, true, "New note")
            assertNull(edits.apply(renewal))
            val renewed = roster.getAll().attendance.entries.single()
            assertNotEquals(attended.studentPackage.id, renewed.studentPackage.id)
            assertEquals(purchaseDate, renewed.studentPackage.startedOn)
            assertEquals(1, renewed.studentPackage.usedClasses)
            assertEquals("New note", renewed.student.note)
            assertFalse(roster.getAll().toRows(now).single().requiresRenewal)
            val pending = mobile.syncPullDao().pendingCount()
            assertNotNull(edits.apply(renewal))
            assertNotNull(edits.apply(AttendanceRosterEdit.Mark(attended, purchaseDate, 2)))
            assertEquals(pending, mobile.syncPullDao().pendingCount())

            cloud.uploadPending(mobile)
            RoomPullStore(desktop, cloud.target).refreshFrom(cloud)
            val peer = RoomAttendanceRoster(desktop).getAll().attendance.entries.single()
            assertEquals(renewed.studentPackage.attendanceHistory, peer.studentPackage.attendanceHistory)
            assertEquals(renewed.studentPackage.expiresOn, peer.studentPackage.expiresOn)
            assertEquals(renewed.student.note, peer.student.note)
        }
    }
}
