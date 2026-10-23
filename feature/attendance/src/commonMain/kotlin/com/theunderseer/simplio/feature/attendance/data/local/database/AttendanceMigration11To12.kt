package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object AttendanceMigration11To12 : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE student_groups ADD COLUMN nameKey TEXT NOT NULL DEFAULT ''")
        val groups = mutableListOf<Pair<Long, String>>()
        connection.prepare("SELECT id, name FROM student_groups ORDER BY id").use { statement ->
            while (statement.step()) groups += statement.getLong(0) to statement.getText(1)
        }
        val used = mutableSetOf<String>()
        groups.forEach { (id, name) ->
            // Old duplicate display names are preserved, while new writes cannot add another duplicate.
            val normalized = name.trim().split(Regex("\\s+")).joinToString(" ").lowercase()
            val key = if (used.add(normalized)) normalized else "$normalized\u0000legacy-$id"
            connection.prepare("UPDATE student_groups SET nameKey = ? WHERE id = ?").use { statement ->
                statement.bindText(1, key)
                statement.bindLong(2, id)
                statement.step()
            }
        }
        connection.execSQL("ALTER TABLE attendance ADD COLUMN sessionId TEXT")
        connection.execSQL("CREATE UNIQUE INDEX index_student_groups_nameKey ON student_groups(nameKey)")
    }
}
