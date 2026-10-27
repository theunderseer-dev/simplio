package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudDocument
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictLocalSnapshot
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule

/** A decision and all affected student versions are selected together, never cancelled plus attended. */
internal fun sessionConflictGroups(
    device: List<AttendanceCloudDocument>,
    server: List<AttendanceCloudDocument>,
    local: ConflictLocalSnapshot,
): Map<String, String> {
    val known = local.records.associateBy { it.entityType to it.cloudId }
    val pending = local.pending.filter { it.entityType == SyncEntityType.CLASS_SESSION }.map { it.cloudId }.toSet()
    val changed = server.filter {
        it.type == SyncEntityType.CLASS_SESSION &&
            known[it.key]?.serverUpdateTime != it.updateTime
    }.map { it.cloudId }.toSet()
    val decisions = (device + server).filter {
        it.type == SyncEntityType.CLASS_SESSION &&
            !it.isDeleted &&
            it.cloudId in pending + changed
    }
    val parents = mutableMapOf<String, String>()
    fun root(value: String): String {
        val parent = parents[value] ?: return value
        return root(parent).also { parents[value] = it }
    }
    fun join(a: String, b: String) {
        val x = root(a)
        val y = root(b)
        if (x != y) parents[maxOf(x, y)] = minOf(x, y)
    }
    listOf(device, server).forEach { documents ->
        val packages = documents.filter { it.type == SyncEntityType.STUDENT_PACKAGE && !it.isDeleted }
            .associateBy { it.cloudId }
        val students = documents.filter { it.type == SyncEntityType.STUDENT && !it.isDeleted }
            .associateBy { it.cloudId }
        val groups = documents.filter { it.type == SyncEntityType.STUDENT_GROUP && !it.isDeleted }
            .associateBy { it.cloudId }
        documents.filter { it.type == SyncEntityType.ATTENDANCE && !it.isDeleted }.forEach { attendance ->
            val student = packages[attendance.string("packageId")]?.string("studentId") ?: return@forEach
            decisions.forEach { decision ->
                val explicit = attendance.fields["sessionId"] != null
                val belongs = if (explicit) {
                    attendance.string("sessionId") == decision.cloudId
                } else {
                    val group = students[student]?.string("groupId")?.let(groups::get)
                    val ids = group?.scheduleIds() ?: listOf(DefaultClassSchedule.ID)
                    attendance.integer("attendedOnEpochDay") == decision.integer("dateEpochDay") &&
                        decision.string("scheduleId") in ids
                }
                if (belongs) join("class:${decision.cloudId}", "student:$student")
            }
        }
    }
    return parents.keys.toList().associateWith(::root)
}
