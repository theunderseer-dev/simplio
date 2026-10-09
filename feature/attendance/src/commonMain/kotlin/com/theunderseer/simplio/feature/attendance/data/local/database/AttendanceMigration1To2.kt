package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal object AttendanceMigration1To2 : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            ALTER TABLE attendance
            ADD COLUMN visitCount INTEGER NOT NULL DEFAULT 1
            """.trimIndent(),
        )
    }
}
