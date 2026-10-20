package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase

internal suspend fun withSyncDatabases(block: suspend (AttendanceDatabase, AttendanceDatabase) -> Unit) {
    val source = testSyncDatabase()
    val target = testSyncDatabase()
    try {
        block(source, target)
    } finally {
        source.close()
        target.close()
    }
}
