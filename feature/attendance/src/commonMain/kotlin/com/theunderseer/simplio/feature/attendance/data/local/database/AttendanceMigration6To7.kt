package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Version 6 data becomes pending local state; historical schemas remain unchanged. */
object AttendanceMigration6To7 : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_records (
                entityType TEXT NOT NULL,
                localId TEXT NOT NULL,
                cloudId TEXT NOT NULL,
                revision INTEGER NOT NULL,
                acknowledgedRevision INTEGER NOT NULL,
                isDeleted INTEGER NOT NULL,
                serverUpdateTime TEXT,
                PRIMARY KEY(entityType, localId)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_sync_records_entityType_cloudId " +
                "ON sync_records (entityType, cloudId)",
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_outbox (
                queueId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                entityType TEXT NOT NULL,
                localId TEXT NOT NULL,
                revision INTEGER NOT NULL,
                operation TEXT NOT NULL,
                mutationId TEXT NOT NULL,
                FOREIGN KEY(entityType, localId) REFERENCES sync_records(entityType, localId)
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_sync_outbox_entityType_localId " +
                "ON sync_outbox (entityType, localId)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_sync_outbox_mutationId ON sync_outbox (mutationId)",
        )
        // Freeze the version 6 template values here, independently of future PackagePlan changes.
        connection.execSQL(
            """
            INSERT INTO sync_records
            SELECT 'STUDENT_GROUP', CAST(id AS TEXT),
                CASE
                    WHEN id = 1 AND name = 'Group A' THEN 'default-group-a'
                    WHEN id = 2 AND name = 'Group B' THEN 'default-group-b'
                    ELSE lower(hex(randomblob(16)))
                END,
                1, 0, 0, NULL
            FROM student_groups
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO sync_records
            SELECT 'PACKAGE_TYPE', CAST(p.id AS TEXT),
                CASE
                    WHEN p.id != (
                        SELECT MIN(other.id) FROM package_types AS other
                        WHERE other.name = p.name AND other.totalClasses = p.totalClasses
                            AND other.validityWeeks = p.validityWeeks AND other.price = p.price
                            AND other.maxVisitsPerAttendance = p.maxVisitsPerAttendance
                    ) THEN lower(hex(randomblob(16)))
                    WHEN name = '4-class package' AND totalClasses = 4 AND validityWeeks = 4
                        AND price = 1400 AND maxVisitsPerAttendance = 1 THEN 'default-plan-four_classes'
                    WHEN name = '5-class package' AND totalClasses = 5 AND validityWeeks = 5
                        AND price = 1700 AND maxVisitsPerAttendance = 1 THEN 'default-plan-five_classes'
                    WHEN name = 'Duo 8 · Special rate' AND totalClasses = 8 AND validityWeeks = 4
                        AND price = 2400 AND maxVisitsPerAttendance = 2 THEN 'default-plan-duo_special'
                    ELSE lower(hex(randomblob(16)))
                END,
                1, 0, 0, NULL
            FROM package_types AS p
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO sync_records
            SELECT 'STUDENT', CAST(id AS TEXT), lower(hex(randomblob(16))), 1, 0, 0, NULL FROM students
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO sync_records
            SELECT 'STUDENT_PACKAGE', CAST(id AS TEXT), lower(hex(randomblob(16))), 1, 0, 0, NULL
            FROM student_packages
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO sync_records
            SELECT 'ATTENDANCE', a.packageId || ':' || a.attendedOnEpochDay,
                parent.cloudId || '_' || a.attendedOnEpochDay, 1, 0, 0, NULL
            FROM attendance AS a
            INNER JOIN sync_records AS parent
                ON parent.entityType = 'STUDENT_PACKAGE' AND parent.localId = CAST(a.packageId AS TEXT)
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO sync_outbox (entityType, localId, revision, operation, mutationId)
            SELECT entityType, localId, revision, 'UPSERT', lower(hex(randomblob(16)))
            FROM sync_records ORDER BY entityType, localId
            """.trimIndent(),
        )
    }
}
