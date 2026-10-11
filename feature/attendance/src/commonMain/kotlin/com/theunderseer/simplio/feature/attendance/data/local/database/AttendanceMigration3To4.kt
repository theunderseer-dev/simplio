package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object AttendanceMigration3To4 : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        // Prevent cascading deletion while replacing the parent table.
        connection.prepare("PRAGMA foreign_keys").use { statement ->
            check(statement.step() && statement.getLong(0) == 0L) {
                "Foreign keys must be disabled while rebuilding students"
            }
        }

        connection.execSQL(
            """
            CREATE TABLE student_groups (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL
            )
            """.trimIndent(),
        )

        connection.execSQL(
            """
            INSERT INTO student_groups (id, name)
            VALUES
                (1, 'Group A'),
                (2, 'Group B')
            """.trimIndent(),
        )

        connection.execSQL(
            """
            CREATE TABLE students_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                fullName TEXT NOT NULL,
                groupId INTEGER NOT NULL,
                FOREIGN KEY (groupId)
                    REFERENCES student_groups (id)
                    ON UPDATE NO ACTION
                    ON DELETE NO ACTION
            )
            """.trimIndent(),
        )

        connection.execSQL(
            """
            INSERT INTO students_new (id, fullName, groupId)
            SELECT id, fullName, 1
            FROM students
            """.trimIndent(),
        )

        // Preserve generated ID history, including deleted students.
        connection.execSQL(
            """
            UPDATE sqlite_sequence
            SET seq = MAX(
                seq,
                COALESCE(
                    (
                        SELECT seq
                        FROM sqlite_sequence
                        WHERE name = 'students'
                    ),
                    0
                )
            )
            WHERE name = 'students_new'
            """.trimIndent(),
        )

        connection.execSQL("DROP TABLE students")

        connection.execSQL(
            "ALTER TABLE students_new RENAME TO students",
        )

        connection.execSQL(
            """
            CREATE INDEX index_students_groupId
            ON students (groupId)
            """.trimIndent(),
        )
    }
}
