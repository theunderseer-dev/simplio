package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceFailure
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceProblem
import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.schedule.latestStartedClass
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetClassHistoryUseCase
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestClock
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.seedClassAttendanceStudents
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ClassAttendanceRepositoryTest {
    @Test
    fun `individual attendance hides history tracking until the last visit is removed`() = runTest {
        withSyncDatabases { database, _ ->
            val students = database.seedClassAttendanceStudents()
            val clock = ClassAttendanceTestClock()
            val packages = RoomStudentPackageRepository(database)
            val history = GetClassHistoryUseCase(RoomClassHistoryRepository(database.attendanceDao(), database))
            val session = ClassAttendanceTestData.session

            suspend fun currentClass() = history(session.date, currentInstant = clock.now())
                .entries.single { it.sessionId == session.id }

            assertTrue(currentClass().canTrackAttendance)

            students.zip(listOf(1, 2)).forEach { (registered, visitCount) ->
                packages.recordAttendance(registered.studentPackage.id, session.date, visitCount)

                val recorded = currentClass()
                assertEquals(
                    visitCount,
                    recorded.students.single { it.student.id == registered.student.id }.visitCount,
                )
                assertFalse(recorded.canTrackAttendance)
                assertNull(database.classSessionStateDao().get(session.id))
            }

            packages.removeAttendance(students.first().studentPackage.id, session.date)
            assertEquals(students.last().student.id, currentClass().students.single().student.id)
            assertFalse(currentClass().canTrackAttendance)

            packages.removeAttendance(students.last().studentPackage.id, session.date)
            assertTrue(currentClass().students.isEmpty())
            assertTrue(currentClass().canTrackAttendance)
        }
    }

    @Test
    fun `shared groups save standard and duo visits once with a persisted held decision`() = runTest {
        withSyncDatabases { database, _ ->
            val students = database.seedClassAttendanceStudents()
            val clock = ClassAttendanceTestClock()
            val repository = RoomClassAttendanceRepository(database, clock)
            val sheet = repository.load(ClassAttendanceTestData.session.id)
            assertEquals(students.map { it.student.id }, sheet.rows.map { it.student.id })
            assertTrue(sheet.canCancel)
            assertTrue(students.all { database.attendanceDao().getForPackage(it.studentPackage.id).isEmpty() })
            repository.save(sheet, mapOf(students[0].student.id to 1, students[1].student.id to 2), false)
            assertEquals(1, database.attendanceDao().getForPackage(students[0].studentPackage.id).single().visitCount)
            assertEquals(2, database.attendanceDao().getForPackage(students[1].studentPackage.id).single().visitCount)
            assertEquals("HELD", database.classSessionStateDao().get(sheet.session.id)?.status)
            assertEquals(
                ClassAttendanceTestData.date.toEpochDays(),
                database.studentPackageDao().getById(students[1].studentPackage.id)?.startedOnEpochDay,
            )
            assertFailsWith<ClassAttendanceFailure> { repository.save(sheet, emptyMap(), false) }
            val history = GetClassHistoryUseCase(RoomClassHistoryRepository(database.attendanceDao(), database))(
                ClassAttendanceTestData.date,
                currentInstant = clock.now(),
            ).entries.first()
            assertEquals(2, history.students.size)
            assertFalse(history.canTrackAttendance)
        }
    }

    @Test
    fun `cancel changes no packages and blocks individual check and renewal attendance`() = runTest {
        withSyncDatabases { database, _ ->
            val students = database.seedClassAttendanceStudents()
            val repository = RoomClassAttendanceRepository(database, ClassAttendanceTestClock())
            val sheet = repository.load(ClassAttendanceTestData.session.id)
            val before = database.syncPullDao().packages()
            repository.save(sheet, emptyMap(), true)
            assertEquals("CANCELLED", database.classSessionStateDao().get(sheet.session.id)?.status)
            assertEquals(before, database.syncPullDao().packages())
            val student = students.first()
            val packs = RoomStudentPackageRepository(database)
            assertEquals(
                ClassAttendanceProblem.CANCELLED,
                assertFailsWith<ClassAttendanceFailure> {
                    packs.recordAttendance(student.studentPackage.id, sheet.session.date, 1)
                }.problem,
            )
            assertFailsWith<ClassAttendanceFailure> {
                packs.create(
                    student.student.id,
                    student.studentPackage.packageType,
                    sheet.session.date,
                    InitialAttendance(sheet.session.date, 1),
                )
            }
            assertEquals(before, database.syncPullDao().packages())
            assertTrue(database.attendanceDao().getForPackage(student.studentPackage.id).isEmpty())
        }
    }

    @Test
    fun `zero attendance confirmation stays distinct from cancellation`() = runTest {
        withSyncDatabases { database, _ ->
            database.seedClassAttendanceStudents()
            val repository = RoomClassAttendanceRepository(database, ClassAttendanceTestClock())
            val sheet = repository.load(ClassAttendanceTestData.session.id)
            repository.save(sheet, emptyMap(), false)
            assertEquals("HELD", database.classSessionStateDao().get(sheet.session.id)?.status)
            assertTrue(database.syncPullDao().packages().all { it.startedOnEpochDay == null })
        }
    }

    @Test
    fun `stale sheet never partially saves or cancels newly recorded attendance`() = runTest {
        withSyncDatabases { database, _ ->
            val students = database.seedClassAttendanceStudents()
            val repository = RoomClassAttendanceRepository(database, ClassAttendanceTestClock())
            val sheet = repository.load(ClassAttendanceTestData.session.id)
            RoomStudentPackageRepository(database).recordAttendance(
                students[0].studentPackage.id,
                sheet.session.date,
                1,
            )
            val pending = database.syncDao().getPending(100)
            assertEquals(
                ClassAttendanceProblem.STALE,
                assertFailsWith<ClassAttendanceFailure> {
                    repository.save(sheet, mapOf(students[1].student.id to 2), false)
                }.problem,
            )
            assertNull(database.classSessionStateDao().get(sheet.session.id))
            assertTrue(database.attendanceDao().getForPackage(students[1].studentPackage.id).isEmpty())
            assertEquals(pending, database.syncDao().getPending(100))
            val current = repository.load(sheet.session.id)
            assertFalse(current.canCancel)
            assertEquals(
                ClassAttendanceProblem.HAS_ATTENDANCE,
                assertFailsWith<ClassAttendanceFailure> {
                    repository.save(current, emptyMap(), true)
                }.problem,
            )
        }
    }

    @Test
    fun `last visit remains checked and removable and duo is limited to remaining credits`() = runTest {
        withSyncDatabases { database, _ ->
            val students = database.seedClassAttendanceStudents(standardCapacity = 2, duoCapacity = 2)
            val standard = students[0].studentPackage
            database.attendanceDao().save(
                AttendanceEntity(standard.id, AttendanceTestData.firstClassDate.toEpochDays(), 1),
            )
            database.attendanceDao().save(AttendanceEntity(standard.id, ClassAttendanceTestData.date.toEpochDays(), 1))
            val duo = students[1].studentPackage
            database.attendanceDao().save(AttendanceEntity(duo.id, AttendanceTestData.firstClassDate.toEpochDays(), 1))
            val repository = RoomClassAttendanceRepository(database, ClassAttendanceTestClock())
            val sheet = repository.load(ClassAttendanceTestData.session.id)
            assertTrue(sheet.rows[0].canRecord)
            assertEquals(1, sheet.rows[0].recordedVisitCount)
            assertEquals(1, sheet.rows[1].maximumVisitCount)
            assertFailsWith<ClassAttendanceFailure> {
                repository.save(sheet, mapOf(students[1].student.id to 2), false)
            }
            assertNull(database.classSessionStateDao().get(sheet.session.id))
            repository.save(sheet, mapOf(students[1].student.id to 1), false)
            assertEquals(
                listOf(AttendanceTestData.firstClassDate.toEpochDays()),
                database.attendanceDao().getForPackage(standard.id).map { it.attendedOnEpochDay },
            )
            assertEquals(2, database.attendanceDao().getForPackage(duo.id).size)
        }
    }

    @Test
    fun `historical tracking survives later classes including an open draft`() = runTest {
        withSyncDatabases { database, _ ->
            val students = database.seedClassAttendanceStudents()
            val clock = ClassAttendanceTestClock(ClassAttendanceTestData.nextSession.startsAt - 1.seconds)
            val repository = RoomClassAttendanceRepository(database, clock)
            val sheet = repository.load(ClassAttendanceTestData.session.id)
            clock.instant = ClassAttendanceTestData.nextSession.startsAt
            assertEquals(sheet, repository.load(sheet.session.id))
            assertNotNull(repository.load(ClassAttendanceTestData.nextSession.id))
            assertEquals(
                ClassAttendanceTestData.nextSession,
                DefaultClassSchedule.schedule.latestStartedClass(clock.now()),
            )
            clock.instant = ClassAttendanceTestData.historicalReviewInstant
            assertEquals(sheet, repository.load(sheet.session.id))
            repository.save(sheet, mapOf(students[0].student.id to 1, students[1].student.id to 2), false)
            assertEquals("HELD", database.classSessionStateDao().get(sheet.session.id)?.status)
            assertEquals(1, database.attendanceDao().getForPackage(students[0].studentPackage.id).single().visitCount)
            assertEquals(2, database.attendanceDao().getForPackage(students[1].studentPackage.id).single().visitCount)
        }
    }

    @Test
    fun `all unrecorded history pages remain trackable and historical decisions preserve packages`() = runTest {
        withSyncDatabases { database, _ ->
            database.seedClassAttendanceStudents()
            val clock = ClassAttendanceTestClock(ClassAttendanceTestData.historicalReviewInstant)
            val repository = RoomClassAttendanceRepository(database, clock)
            val history = GetClassHistoryUseCase(RoomClassHistoryRepository(database.attendanceDao(), database))
            val before = database.syncPullDao().packages()
            var beforeDate: kotlinx.datetime.LocalDate? = null
            var foundOldClass = false
            var pages = 0
            do {
                val page = history(ClassAttendanceTestData.historicalReviewDate, beforeDate, clock.now())
                assertTrue(page.entries.isNotEmpty())
                assertTrue(page.entries.all { it.students.isEmpty() && it.canTrackAttendance })
                foundOldClass = foundOldClass || page.entries.any { it.sessionId == ClassAttendanceTestData.session.id }
                pages++
                beforeDate = page.nextBeforeDate
            } while (beforeDate != null)
            assertTrue(foundOldClass)
            assertTrue(pages > 1)
            assertTrue(database.classSessionStateDao().getAll().isEmpty())
            assertEquals(before, database.syncPullDao().packages())

            val sheet = repository.load(ClassAttendanceTestData.session.id)
            repository.save(sheet, emptyMap(), true)
            val previous = repository.load(ClassAttendanceTestData.previousSession.id)
            repository.save(previous, emptyMap(), false)
            assertEquals(before, database.syncPullDao().packages())
            assertTrue(database.attendanceDao().getAll().isEmpty())
            val decided = history(
                ClassAttendanceTestData.historicalReviewDate,
                ClassAttendanceTestData.nextSession.date,
                clock.now(),
            ).entries
            assertFalse(decided.single { it.sessionId == sheet.session.id }.canTrackAttendance)
            assertFalse(decided.single { it.sessionId == previous.session.id }.canTrackAttendance)
            assertEquals("CANCELLED", database.classSessionStateDao().get(sheet.session.id)?.status)
            assertEquals("HELD", database.classSessionStateDao().get(previous.session.id)?.status)
            assertFailsWith<ClassAttendanceFailure> { repository.load(sheet.session.id) }
        }
    }

    @Test
    fun `historical attendance uses the package from that date even after a newer purchase`() = runTest {
        withSyncDatabases { database, _ ->
            val student = database.seedClassAttendanceStudents().first()
            val packages = RoomStudentPackageRepository(database)
            packages.recordAttendance(student.studentPackage.id, AttendanceTestData.firstClassDate, 1)
            val newer = packages.create(
                student.student.id,
                student.studentPackage.packageType,
                ClassAttendanceTestData.historicalReviewDate,
                null,
            )
            val repository = RoomClassAttendanceRepository(
                database,
                ClassAttendanceTestClock(ClassAttendanceTestData.historicalReviewInstant),
            )
            val sheet = repository.load(ClassAttendanceTestData.session.id)
            val row = sheet.rows.single { it.student.id == student.student.id }
            assertEquals(student.studentPackage.id, row.studentPackage?.id)
            assertTrue(row.canRecord)
            assertFalse(row.canRenew)
            repository.save(sheet, mapOf(student.student.id to 1), false)
            assertEquals(2, database.attendanceDao().getForPackage(student.studentPackage.id).size)
            assertTrue(database.attendanceDao().getForPackage(newer.id).isEmpty())
            assertEquals(newer, packages.getLatestPackageForStudent(student.student.id))
        }
    }

    @Test
    fun `future class cannot be loaded and packages without visits still appear in history`() = runTest {
        withSyncDatabases { database, _ ->
            database.seedClassAttendanceStudents()
            val clock = ClassAttendanceTestClock(ClassAttendanceTestData.session.startsAt - 1.seconds)
            assertFailsWith<ClassAttendanceFailure> {
                RoomClassAttendanceRepository(database, clock).load(ClassAttendanceTestData.session.id)
            }
            val history = GetClassHistoryUseCase(RoomClassHistoryRepository(database.attendanceDao(), database))(
                ClassAttendanceTestData.date,
                currentInstant = clock.now(),
            )
            assertTrue(history.entries.none { it.sessionId == ClassAttendanceTestData.session.id })
            assertTrue(history.entries.any { it.canTrackAttendance })
        }
    }
}
