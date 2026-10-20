package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object AttendanceMigration9To10 : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_conflict_reviews (
                reviewId TEXT NOT NULL PRIMARY KEY,
                projectId TEXT NOT NULL,
                userId TEXT NOT NULL,
                localJson TEXT NOT NULL,
                cloudJson TEXT NOT NULL,
                status TEXT NOT NULL,
                choicesJson TEXT
            )
            """.trimIndent(),
        )
    }
}
