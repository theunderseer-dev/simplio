package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object AttendanceMigration7To8 : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE sync_records ADD COLUMN aggregateKey TEXT NOT NULL DEFAULT ''")
        connection.execSQL(
            """
            UPDATE sync_records SET aggregateKey = CASE entityType
                WHEN 'STUDENT_GROUP' THEN 'STUDENT_GROUP:' || localId
                WHEN 'PACKAGE_TYPE' THEN 'PACKAGE_TYPE:' || localId
                WHEN 'STUDENT' THEN CASE WHEN isDeleted = 1 THEN 'legacy-deletions' ELSE 'STUDENT:' || localId END
                WHEN 'STUDENT_PACKAGE' THEN COALESCE((
                    SELECT 'STUDENT:' || studentId FROM student_packages WHERE CAST(id AS TEXT) = localId
                ), 'legacy-deletions')
                WHEN 'ATTENDANCE' THEN COALESCE((
                    SELECT 'STUDENT:' || studentId FROM student_packages
                    WHERE CAST(id AS TEXT) = substr(localId, 1, instr(localId, ':') - 1)
                ), 'legacy-deletions')
                ELSE 'legacy-deletions'
            END
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_upload_state (
                id INTEGER NOT NULL, projectId TEXT NOT NULL, userId TEXT NOT NULL,
                headUpdateTime TEXT, PRIMARY KEY(id)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_upload_batch (
                id INTEGER NOT NULL, batchId TEXT NOT NULL, snapshotJson TEXT NOT NULL,
                commitBody TEXT NOT NULL, attempts INTEGER NOT NULL, blockedReason TEXT, PRIMARY KEY(id)
            )
            """.trimIndent(),
        )
    }
}
