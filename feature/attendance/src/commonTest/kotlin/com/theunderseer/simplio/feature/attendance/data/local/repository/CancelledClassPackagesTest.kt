package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupScheduleEntity
import com.theunderseer.simplio.feature.attendance.data.local.mapper.toDomain
import com.theunderseer.simplio.feature.attendance.data.local.sync.SyncEntityType
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceFailure
import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.schedule.sessionsBetween
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestClock
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.seedClassAttendanceStudents
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CancelledClassPackagesTest {
    @Test
    fun `cancellation extends both shared groups once without changing visits or terms`() = runTest {
        withSyncDatabases { database, _ ->
            val students = database.seedClassAttendanceStudents(activated = true)
            val before = database.syncPullDao().packages()
            val attendance = database.attendanceDao().getAll()
            val repository = RoomClassAttendanceRepository(database, ClassAttendanceTestClock())
            val sheet = repository.load(ClassAttendanceTestData.session.id)
            repository.save(sheet, emptyMap(), true)
            students.forEach { student ->
                val entity = database.studentPackageDao().getById(student.studentPackage.id)!!
                assertEquals(mapOf(sheet.session.id to 3), entity.validityExtensions)
                assertEquals(before.single { it.id == entity.id }, entity.copy(validityExtensions = emptyMap()))
                assertEquals(
                    AttendanceTestData.class10Date,
                    entity.toDomain(
                        database.attendanceDao().getForPackage(entity.id),
                    ).expiresOn,
                )
                assertTrue(
                    database.syncDao().getPending(100).any {
                        it.entityType == SyncEntityType.STUDENT_PACKAGE && it.localId == entity.id.toString()
                    },
                )
            }
            assertEquals(attendance, database.attendanceDao().getAll())
            val after = database.syncPullDao().packages()
            val pending = database.syncDao().getPending(100)
            assertFailsWith<ClassAttendanceFailure> { repository.save(sheet, emptyMap(), true) }
            assertEquals(after, database.syncPullDao().packages())
            assertEquals(pending, database.syncDao().getPending(100))
        }
    }

    @Test
    fun `successive cancellations shift by three then four days regardless of their entry order`() = runTest {
        for (sessions in listOf(
            listOf(ClassAttendanceTestData.session, ClassAttendanceTestData.nextSession),
            listOf(ClassAttendanceTestData.nextSession, ClassAttendanceTestData.session),
        )) {
            withSyncDatabases { database, _ ->
                database.seedClassAttendanceStudents(activated = true)
                val repository = RoomClassAttendanceRepository(
                    database,
                    ClassAttendanceTestClock(ClassAttendanceTestData.historicalReviewInstant),
                )
                sessions.forEach { session -> repository.save(repository.load(session.id), emptyMap(), true) }
                database.syncPullDao().packages().forEach { pack ->
                    assertEquals(setOf(3, 4), pack.validityExtensions.values.toSet())
                    assertEquals(2, pack.validityExtensions.size)
                    assertEquals(
                        AttendanceTestData.class11Date,
                        pack.toDomain(
                            database.attendanceDao().getForPackage(pack.id),
                        ).expiresOn,
                    )
                }
            }
        }
    }

    @Test
    fun `only the package applicable to the historical class is extended after a later renewal`() = runTest {
        withSyncDatabases { database, _ ->
            val student = database.seedClassAttendanceStudents(activated = true).first()
            val packages = RoomStudentPackageRepository(database)
            val newer = packages.create(
                student.student.id,
                student.studentPackage.packageType,
                AttendanceTestData.class11Date,
                InitialAttendance(AttendanceTestData.class11Date, 1),
            )
            val repository = RoomClassAttendanceRepository(
                database,
                ClassAttendanceTestClock(ClassAttendanceTestData.historicalReviewInstant),
            )
            repository.save(repository.load(ClassAttendanceTestData.session.id), emptyMap(), true)
            val previous = database.studentPackageDao().getById(student.studentPackage.id)!!
            assertEquals(mapOf(ClassAttendanceTestData.session.id to 3), previous.validityExtensions)
            assertEquals(newer, packages.getLatestPackageForStudent(student.student.id))
        }
    }

    @Test
    fun `other schedules unactivated unlimited completed and already expired packages get no extension`() = runTest {
        withSyncDatabases { database, _ ->
            val students = database.seedClassAttendanceStudents(activated = true)
            val other = testClassSchedule(id = "other")
            RoomClassScheduleRepository(database).save(other)
            database.classScheduleDao().detachGroup(students.last().student.groupId)
            database.classScheduleDao().attach(StudentGroupScheduleEntity(students.last().student.groupId, other.id))
            val repository = RoomClassAttendanceRepository(database, ClassAttendanceTestClock())
            repository.save(repository.load(ClassAttendanceTestData.session.id), emptyMap(), true)
            assertTrue(
                database.studentPackageDao().getById(
                    students.last().studentPackage.id,
                )!!.validityExtensions.isEmpty(),
            )
        }
        for (kind in listOf("unactivated", "unlimited", "completed", "expired")) {
            withSyncDatabases { database, _ ->
                val student = database.seedClassAttendanceStudents(activated = kind != "unactivated").first()
                val pack = database.studentPackageDao().getById(student.studentPackage.id)!!
                if (kind == "unlimited") database.studentPackageDao().update(pack.copy(validityWeeks = null))
                if (kind == "completed") database.studentPackageDao().update(pack.copy(totalClasses = 1))
                val target = if (kind == "expired") {
                    DefaultClassSchedule.schedule.sessionsBetween(
                        AttendanceTestData.class10Date,
                        AttendanceTestData.class10Date,
                    ).single()
                } else {
                    ClassAttendanceTestData.session
                }
                val repository = RoomClassAttendanceRepository(
                    database,
                    ClassAttendanceTestClock(ClassAttendanceTestData.historicalReviewInstant),
                )
                repository.save(repository.load(target.id), emptyMap(), true)
                assertTrue(database.studentPackageDao().getById(pack.id)!!.validityExtensions.isEmpty(), kind)
            }
        }
    }

    @Test
    fun `held with zero visits leaves finite packages unchanged`() = runTest {
        withSyncDatabases { database, _ ->
            database.seedClassAttendanceStudents(activated = true)
            val before = database.syncPullDao().packages()
            val repository = RoomClassAttendanceRepository(database, ClassAttendanceTestClock())
            repository.save(repository.load(ClassAttendanceTestData.session.id), emptyMap(), false)
            assertEquals(before, database.syncPullDao().packages())
        }
    }

    @Test
    fun `correcting activation and reactivating retain one class shift on scheduled dates`() = runTest {
        withSyncDatabases { database, _ ->
            val student = database.seedClassAttendanceStudents(activated = true).first()
            val id = student.studentPackage.id
            val classes = RoomClassAttendanceRepository(database, ClassAttendanceTestClock())
            classes.save(classes.load(ClassAttendanceTestData.session.id), emptyMap(), true)
            val packages = RoomStudentPackageRepository(database)
            packages.recordAttendance(id, AttendanceTestData.fourthClassDate, 1)
            packages.removeAttendance(id, AttendanceTestData.firstClassDate)
            val corrected = packages.getLatestPackageForStudent(student.student.id)!!
            assertEquals(AttendanceTestData.fourthClassDate, corrected.startedOn)
            assertEquals(AttendanceTestData.class13Date, corrected.expiresOn)
            assertEquals(mapOf(ClassAttendanceTestData.session.id to 4), corrected.validityExtensions)
            packages.removeAttendance(id, AttendanceTestData.fourthClassDate)
            assertEquals(null, packages.getLatestPackageForStudent(student.student.id)?.expiresOn)
            packages.recordAttendance(id, AttendanceTestData.thirdClassDate, 1)
            val reactivated = packages.getLatestPackageForStudent(student.student.id)!!
            assertEquals(AttendanceTestData.class12Date, reactivated.expiresOn)
            assertEquals(mapOf(ClassAttendanceTestData.session.id to 3), reactivated.validityExtensions)
            assertEquals(1, reactivated.usedClasses)
        }
    }
}
