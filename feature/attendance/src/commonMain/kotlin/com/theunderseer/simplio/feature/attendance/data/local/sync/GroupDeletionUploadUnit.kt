package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase

/** A retired series, group tombstones and pending student deletions must arrive as a valid graph. */
internal suspend fun groupDeletionUploadUnit(
    database: AttendanceDatabase,
    pending: List<PendingSyncChange>,
): List<PendingSyncChange> {
    val retired = database.classScheduleDao().schedules().filter {
        it.archivedAtEpochMillis != null
    }.map { it.id }.toSet()
    val deletedStudents = pending.filter {
        it.entityType == SyncEntityType.STUDENT &&
            it.operation == SyncOperation.DELETE
    }
        .map { it.aggregateKey }.toSet()
    val initial = pending.filter {
        it.entityType == SyncEntityType.STUDENT_GROUP &&
            it.operation == SyncOperation.DELETE ||
            it.entityType == SyncEntityType.CLASS_SCHEDULE &&
            it.localId in retired ||
            it.aggregateKey in deletedStudents
    }.map { it.entityType to it.localId }.toSet()
    return classUploadUnit(database, pending, initial)
}
