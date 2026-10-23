package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentGroupCreationRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictChoice
import com.theunderseer.simplio.feature.attendance.data.sync.InvalidConflictChoice
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import com.theunderseer.simplio.feature.attendance.fake.FakeAttendanceCloud
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GroupNameConflictTest {
    @Test
    fun `offline duplicate names require rename and keep both groups students and attendance`() = runTest {
        withSyncDatabases { source, device ->
            source.seedSyncDefaults()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            RoomPullStore(device, testUploadTarget).apply(cloud.snapshot())
            val remoteGroup = assertIs<CreateStudentGroupResult.Success>(
                RoomStudentGroupCreationRepository(source).create(
                    CreateStudentGroupRequest("Private yoga", existingScheduleId = DefaultClassSchedule.ID),
                ),
            ).group
            val deviceGroup = assertIs<CreateStudentGroupResult.Success>(
                RoomStudentGroupCreationRepository(device).create(
                    CreateStudentGroupRequest("PRIVATE YOGA", existingScheduleId = DefaultClassSchedule.ID),
                ),
            ).group
            listOf(Triple(source, remoteGroup, "Remote student"), Triple(device, deviceGroup, "Device student"))
                .forEach { (database, group, name) ->
                    val student = RoomStudentRepository(database).create(name, group.id, "Keep note")
                    val type = database.packageTypeDao().getAll().first()
                    RoomStudentPackageRepository(database).create(
                        student.id,
                        com.theunderseer.simplio.feature.attendance.domain.model.PackageType(
                            type.id,
                            type.name,
                            type.totalClasses,
                            type.validityWeeks,
                            type.price,
                            type.maxVisitsPerAttendance,
                        ),
                        LocalDate(2026, 10, 2),
                        InitialAttendance(LocalDate(2026, 10, 2), 1),
                    )
                }
            cloud.uploadPending(source)
            val store = RoomConflictStore(device, testUploadTarget)
            val review = store.create(cloud.snapshot())
            val item = review.items.single()
            assertTrue(item.requiresGroupRename)
            val choice = mapOf(item.key to ConflictChoice.DEVICE)
            assertFailsWith<InvalidConflictChoice> { store.apply(review.id, choice, cloud.readHead()) }
            assertFailsWith<InvalidConflictChoice> {
                store.apply(review.id, choice, cloud.readHead(), mapOf(item.key to " private   yoga "))
            }
            store.apply(review.id, choice, cloud.readHead(), mapOf(item.key to "Private yoga 2"))
            cloud.uploadPending(device)
            RoomPullStore(source, testUploadTarget).apply(cloud.snapshot())
            val groups = source.studentGroupDao().getAll().associateBy { it.name }
            assertNotEquals(groups.getValue("Private yoga").id, groups.getValue("Private yoga 2").id)
            val students = source.studentDao().getAll().associateBy { it.fullName }
            assertEquals(groups.getValue("Private yoga").id, students.getValue("Remote student").groupId)
            assertEquals(groups.getValue("Private yoga 2").id, students.getValue("Device student").groupId)
            students.values.forEach {
                assertEquals("Keep note", it.note)
                assertEquals(1, RoomStudentPackageRepository(source).getLatestPackageForStudent(it.id)?.usedClasses)
            }
            assertTrue(checkNotNull(device.syncConflictDao().get(review.id)?.choicesJson).contains("Private yoga 2"))
        }
    }
}
