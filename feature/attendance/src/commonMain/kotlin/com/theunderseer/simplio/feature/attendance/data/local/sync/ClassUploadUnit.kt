package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase

/** Keep a date's decisions and attendance atomic, including deletions and new purchase parents. */
internal suspend fun classUploadUnit(
    database: AttendanceDatabase,
    pending: List<PendingSyncChange>,
    initial: Set<Pair<SyncEntityType, String>>? = null,
): List<PendingSyncChange> {
    val selected = initial?.toMutableSet() ?: pending.first { it.entityType == SyncEntityType.CLASS_SESSION }
        .let { mutableSetOf(it.entityType to it.localId) }
    val decisions = database.classSessionStateDao().getAll().associateBy { it.sessionId }
    val packages = database.syncPullDao().packages().associateBy { it.id }
    val links = database.classScheduleDao().groupSchedules()
    val students = database.studentDao().getAll().associateBy { it.id }
    var changed: Boolean
    do {
        val previous = selected.size
        val dates = pending.filter { it.entityType to it.localId in selected }.mapNotNull {
            when (it.entityType) {
                SyncEntityType.CLASS_SESSION -> decisions[it.localId]?.dateEpochDay
                    ?: it.localId.substringAfterLast(':').toLong()
                SyncEntityType.ATTENDANCE -> it.localId.substringAfter(':').toLong()
                else -> null
            }
        }.toSet()
        pending.forEach { change ->
            val sameDate = when (change.entityType) {
                SyncEntityType.CLASS_SESSION -> (
                    decisions[change.localId]?.dateEpochDay
                        ?: change.localId.substringAfterLast(':').toLong()
                    ) in
                    dates
                SyncEntityType.ATTENDANCE -> change.localId.substringAfter(':').toLong() in dates
                else -> false
            }
            if (sameDate) selected += change.entityType to change.localId
        }
        pending.filter { it.entityType to it.localId in selected }.forEach { change ->
            when (change.entityType) {
                SyncEntityType.ATTENDANCE -> {
                    val id = change.localId.substringBefore(':').toInt()
                    val pack = packages[id]
                    if (pack == null) {
                        pending.filter { it.aggregateKey == change.aggregateKey }.forEach {
                            selected += it.entityType to it.localId
                        }
                    } else {
                        selected += SyncEntityType.STUDENT_PACKAGE to id.toString()
                        selected += SyncEntityType.STUDENT to pack.studentId.toString()
                    }
                }
                SyncEntityType.STUDENT_PACKAGE -> packages[change.localId.toInt()]?.let {
                    selected += SyncEntityType.STUDENT to it.studentId.toString()
                    selected += SyncEntityType.PACKAGE_TYPE to it.packageTypeId.toString()
                }
                SyncEntityType.STUDENT -> {
                    val id = change.localId.toInt()
                    students[id]?.let { selected += SyncEntityType.STUDENT_GROUP to it.groupId.toString() }
                    if (id !in students) {
                        pending.filter { it.aggregateKey == change.aggregateKey }.forEach {
                            selected += it.entityType to it.localId
                        }
                    }
                    // A newly uploaded student must arrive with at least one purchased package.
                    packages.values.filter { it.studentId == id }.forEach {
                        selected += SyncEntityType.STUDENT_PACKAGE to it.id.toString()
                    }
                }
                SyncEntityType.STUDENT_GROUP ->
                    links
                        .filter { it.groupId.toString() == change.localId }.forEach {
                            selected += SyncEntityType.CLASS_SCHEDULE to it.scheduleId
                        }
                SyncEntityType.CLASS_SESSION -> decisions[change.localId]?.let {
                    selected += SyncEntityType.CLASS_SCHEDULE to it.scheduleId
                }
                else -> Unit
            }
        }
        changed = previous != selected.size
    } while (changed)
    return pending.filter { it.entityType to it.localId in selected }
}
