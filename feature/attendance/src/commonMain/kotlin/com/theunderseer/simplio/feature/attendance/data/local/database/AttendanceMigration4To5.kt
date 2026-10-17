package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal object AttendanceMigration4To5 : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            ALTER TABLE student_packages
            RENAME COLUMN startedOnEpochDay TO purchasedOnEpochDay
            """.trimIndent(),
        )

        connection.execSQL(
            """
            ALTER TABLE student_packages
            ADD COLUMN startedOnEpochDay INTEGER
            """.trimIndent(),
        )

        connection.execSQL(
            """
            UPDATE student_packages
            SET startedOnEpochDay = purchasedOnEpochDay
            """.trimIndent(),
        )
    }
}
