package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object AttendanceMigration13To14 : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `class_session_states` (`sessionId` TEXT NOT NULL,
            `scheduleId` TEXT NOT NULL, `dateEpochDay` INTEGER NOT NULL, `status` TEXT NOT NULL,
            PRIMARY KEY(`sessionId`))
            """.trimIndent(),
        )
    }
}
