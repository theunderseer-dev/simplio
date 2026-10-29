package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object AttendanceMigration15To16 : Migration(15, 16) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE student_packages ADD COLUMN validityExtensions TEXT NOT NULL DEFAULT '{}'")
    }
}
