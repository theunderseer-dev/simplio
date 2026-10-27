package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceRow
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceSheet
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.schedule.sessionsBetween
import kotlin.time.Clock
import kotlin.time.Instant

internal object ClassAttendanceTestData {
    val date = AttendanceTestData.secondClassDate
    val session = DefaultClassSchedule.schedule.sessionsBetween(date, date).single()
    val nextSession = DefaultClassSchedule.schedule.sessionsBetween(
        AttendanceTestData.thirdClassDate,
        AttendanceTestData.thirdClassDate,
    ).single()
    val timeLabel = "09:00–10:15 · Asia/Bangkok"
    val companionName = "Bob + 1"
}

internal class ClassAttendanceTestClock(var instant: Instant = ClassAttendanceTestData.session.endsAt) : Clock {
    override fun now(): Instant = instant
}

internal fun testClassAttendanceSheet(recordedVisitCount: Int = 0, duo: Boolean = false): ClassAttendanceSheet {
    val pack = testStudentPackage(
        packageType = if (duo) AttendanceTestData.duoSpecialPackage else AttendanceTestData.fourClassPackage,
        purchasedOn = AttendanceTestData.firstClassDate,
        startedOn = if (recordedVisitCount > 0) ClassAttendanceTestData.date else null,
        visitsByDate = if (recordedVisitCount == 0) {
            emptyMap()
        } else {
            mapOf(
                ClassAttendanceTestData.date to recordedVisitCount,
            )
        },
    )
    return ClassAttendanceSheet(
        ClassAttendanceTestData.session,
        ClassAttendanceTestData.timeLabel,
        listOf(ClassAttendanceRow(AttendanceTestData.student, pack, recordedVisitCount, true)),
    )
}

internal suspend fun AttendanceDatabase.seedClassAttendanceStudents(
    standardCapacity: Int = AttendanceTestData.fourClassPackage.totalClasses,
    duoCapacity: Int = AttendanceTestData.duoSpecialPackage.totalClasses,
): List<RegisteredStudent> {
    seedSyncDefaults()
    val repository = RoomStudentRepository(this)
    val packages = RoomStudentPackageRepository(this)
    return listOf(AttendanceTestData.fourClassPackage, AttendanceTestData.duoSpecialPackage).mapIndexed { index, type ->
        val student = repository.create(
            if (index == 0) AttendanceTestData.student.fullName else ClassAttendanceTestData.companionName,
            index + 1,
            "",
        )
        val stored = type.copy(
            id = packageTypeDao().getAll().single { it.name == type.name }.id,
            totalClasses = if (index == 0) standardCapacity else duoCapacity,
        )
        RegisteredStudent(student, packages.create(student.id, stored, AttendanceTestData.firstClassDate, null))
    }
}
