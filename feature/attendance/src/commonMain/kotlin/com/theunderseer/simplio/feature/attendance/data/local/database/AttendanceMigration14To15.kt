package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object AttendanceMigration14To15 : Migration(14, 15) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE class_schedules ADD COLUMN archivedAtEpochMillis INTEGER")
    }
}
