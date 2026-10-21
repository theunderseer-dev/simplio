package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudDocument
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Product details only; transport IDs and mutation metadata do not belong in the review UI. */
internal fun conflictDetails(rows: List<AttendanceCloudDocument>, all: List<AttendanceCloudDocument>): List<String> {
    val live = rows.filter { !it.isDeleted }
    if (live.isEmpty()) return listOf("Deleted")
    val result = mutableListOf<String>()
    live.firstOrNull { it.type == SyncEntityType.STUDENT }?.let { student ->
        result += "Name: ${student.string("fullName")}"
        val group = all.firstOrNull {
            it.type == SyncEntityType.STUDENT_GROUP && it.cloudId == student.string("groupId")
        }
        result += "Group: ${group?.takeIf { !it.isDeleted }?.string("name") ?: "Deleted group"}"
        result += "Note: ${student.string("note").ifBlank { "—" }}"
    }
    live.filter { it.type == SyncEntityType.STUDENT_PACKAGE }.sortedWith(
        compareBy<AttendanceCloudDocument> { it.integer("purchasedOnEpochDay") }.thenBy { it.cloudId },
    ).forEach { pack ->
        val visits = live.filter { it.type == SyncEntityType.ATTENDANCE && it.string("packageId") == pack.cloudId }
            .sortedBy { it.integer("attendedOnEpochDay") }
        result += "Package: ${pack.string("packageName")}"
        result += "Purchased: ${LocalDate.fromEpochDays(pack.integer("purchasedOnEpochDay"))}"
        result += "Visits: ${visits.sumOf { it.integer("visitCount") }} / ${pack.integer("totalClasses")}"
        result += "Price: ${pack.integer("price")} · Validity: ${pack.integer("validityWeeks")} weeks"
        result += "People per class: ${pack.integer("maxVisitsPerAttendance")}"
        val activation = visits.firstOrNull()?.integer("attendedOnEpochDay")?.let { LocalDate.fromEpochDays(it) }
        result += "Activation: ${activation ?: "—"}"
        result +=
            "Valid until: ${activation?.plus(DatePeriod(days = pack.integer("validityWeeks").toInt() * 7)) ?: "—"}"
        result += "Attendance: " + visits.joinToString { visit ->
            "${LocalDate.fromEpochDays(visit.integer("attendedOnEpochDay"))} × ${visit.integer("visitCount")}"
        }.ifBlank { "None" }
    }
    live.filter { it.type == SyncEntityType.STUDENT_GROUP }.forEach { group ->
        result += "Group: ${group.string("name")}"
        (group.scheduleIds() ?: listOf(DefaultClassSchedule.ID)).forEach { id ->
            val schedule = all.firstOrNull { it.type == SyncEntityType.CLASS_SCHEDULE && it.cloudId == id }
                ?.takeUnless { it.isDeleted }?.toSchedule()
                ?: DefaultClassSchedule.schedule.takeIf { it.id == id }
            result +=
                if (schedule == null) "Schedule: Deleted" else "Schedule: ${schedule.name} · ${schedule.timeZoneId}"
        }
    }
    live.filter { it.type == SyncEntityType.PACKAGE_TYPE }.forEach {
        result += "Package: ${it.string("name")} · Classes: ${it.integer("totalClasses")}"
        result += "Price: ${it.integer("price")} · Validity: ${it.integer("validityWeeks")} weeks"
        result += "People per class: ${it.integer("maxVisitsPerAttendance")}"
    }
    live.filter { it.type == SyncEntityType.CLASS_SCHEDULE }.forEach { document ->
        val schedule = document.toSchedule()
        result += "Schedule: ${schedule.name}"
        result += "Time zone: ${schedule.timeZoneId}"
        result += "Dates: ${schedule.startsOn ?: "—"} – ${schedule.endsOn ?: "—"}"
        result += schedule.slots.joinToString { "${it.dayOfWeek}: ${it.startsAt}–${it.endsAt}" }
    }
    if (result.isEmpty()) result += "Deleted records: ${rows.count { it.isDeleted }}"
    return result
}
