package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.database.createAttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.domain.schedule.sessionsBetween
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RoomClassSchedulePersistenceTest {
    @Test
    fun `restart preserves shared schedule identity links and frozen pending upload`() = runTest {
        val directory = Files.createTempDirectory("simplio-schedule-restart-")
        val path = directory.resolve("attendance.db").toString()
        try {
            val first = createAttendanceDatabase(path)
            val schedule = testClassSchedule()
            val date = LocalDate(2026, 10, 2)
            val batch = try {
                first.seedSyncDefaults()
                val repository = RoomClassScheduleRepository(first)
                repository.save(schedule)
                repository.assignToGroup(1, setOf(schedule.id))
                repository.assignToGroup(2, setOf(schedule.id))
                assertNotNull(RoomUploadStore(first).prepare(testUploadTarget))
            } finally {
                first.close()
            }
            val reopened = createAttendanceDatabase(path)
            try {
                val repository = RoomClassScheduleRepository(reopened)
                assertEquals(schedule, repository.getForGroup(1).single())
                assertEquals(schedule, repository.getForGroup(2).single())
                assertEquals(
                    schedule.sessionsBetween(date, date),
                    repository.getForGroup(1).single()
                        .sessionsBetween(date, date),
                )
                assertEquals(batch, RoomUploadStore(reopened).prepare(testUploadTarget))
            } finally {
                reopened.close()
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
