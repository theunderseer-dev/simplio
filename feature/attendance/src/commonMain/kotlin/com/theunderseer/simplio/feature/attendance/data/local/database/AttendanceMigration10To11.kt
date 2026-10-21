package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object AttendanceMigration10To11 : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS class_schedules (
                id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, timeZoneId TEXT NOT NULL,
                startsOnEpochDay INTEGER, endsOnEpochDay INTEGER
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS class_schedule_slots (
                scheduleId TEXT NOT NULL, id TEXT NOT NULL, dayOfWeek INTEGER NOT NULL,
                startMinute INTEGER NOT NULL, endMinute INTEGER NOT NULL,
                PRIMARY KEY(scheduleId, id),
                FOREIGN KEY(scheduleId) REFERENCES class_schedules(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS student_group_schedules (
                groupId INTEGER NOT NULL, scheduleId TEXT NOT NULL, PRIMARY KEY(groupId, scheduleId),
                FOREIGN KEY(groupId) REFERENCES student_groups(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(scheduleId) REFERENCES class_schedules(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_student_group_schedules_scheduleId ON student_group_schedules(scheduleId)",
        )
        // Historical schema constants must not follow future changes to the application's default template.
        connection.execSQL(
            """
            INSERT INTO class_schedules VALUES ('legacy-mon-fri', 'Monday and Friday', 'Asia/Bangkok', NULL, NULL)
            """.trimIndent(),
        )
        connection.execSQL("INSERT INTO class_schedule_slots VALUES ('legacy-mon-fri', 'monday', 1, 540, 615)")
        connection.execSQL("INSERT INTO class_schedule_slots VALUES ('legacy-mon-fri', 'friday', 5, 540, 615)")
        connection.execSQL("INSERT INTO student_group_schedules SELECT id, 'legacy-mon-fri' FROM student_groups")
    }
}
