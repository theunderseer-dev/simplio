package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal object AttendanceMigration2To3 : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            ALTER TABLE package_types
            ADD COLUMN maxVisitsPerAttendance INTEGER NOT NULL DEFAULT 1
            """.trimIndent(),
        )

        connection.execSQL(
            """
            ALTER TABLE student_packages
            ADD COLUMN maxVisitsPerAttendance INTEGER NOT NULL DEFAULT 1
            """.trimIndent(),
        )
    }
}
