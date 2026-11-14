package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object AttendanceMigration16To17 : Migration(16, 17) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE package_types ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
    }
}
