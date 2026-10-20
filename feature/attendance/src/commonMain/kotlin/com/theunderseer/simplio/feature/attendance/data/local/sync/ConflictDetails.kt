package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudDocument
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
    live.filter { it.type == SyncEntityType.STUDENT_GROUP }.forEach { result += "Group: ${it.string("name")}" }
    live.filter { it.type == SyncEntityType.PACKAGE_TYPE }.forEach {
        result += "Package: ${it.string("name")} · Classes: ${it.integer("totalClasses")}"
        result += "Price: ${it.integer("price")} · Validity: ${it.integer("validityWeeks")} weeks"
        result += "People per class: ${it.integer("maxVisitsPerAttendance")}"
    }
    if (result.isEmpty()) result += "Deleted records: ${rows.count { it.isDeleted }}"
    return result
}
