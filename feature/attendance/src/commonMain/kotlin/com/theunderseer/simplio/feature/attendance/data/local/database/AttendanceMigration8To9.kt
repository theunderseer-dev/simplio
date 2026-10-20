package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object AttendanceMigration8To9 : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE sync_upload_state ADD COLUMN lastPullReadTime TEXT")
    }
}
