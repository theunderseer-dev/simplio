package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Keep purchase snapshots and attendance intact while allowing a package without an expiry limit. */
object AttendanceMigration12To13 : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TEMP TABLE package_migration_sequence AS SELECT name, seq FROM sqlite_sequence")
        connection.execSQL("CREATE TEMP TABLE package_migration_package_types AS SELECT * FROM package_types")
        connection.execSQL("CREATE TEMP TABLE package_migration_student_packages AS SELECT * FROM student_packages")
        connection.execSQL("CREATE TEMP TABLE package_migration_attendance AS SELECT * FROM attendance")
        connection.execSQL("DROP TABLE attendance")
        connection.execSQL("DROP TABLE student_packages")
        connection.execSQL("DROP TABLE package_types")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `package_types` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL, `totalClasses` INTEGER NOT NULL, `validityWeeks` INTEGER, `price` INTEGER
            NOT NULL, `maxVisitsPerAttendance` INTEGER NOT NULL DEFAULT 1)
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO package_types (id, name, totalClasses, validityWeeks, price, maxVisitsPerAttendance)
            SELECT id, name, totalClasses, validityWeeks, price, maxVisitsPerAttendance FROM package_migration_package_types
            """.trimIndent(),
        )
        connection.execSQL("DROP TABLE package_migration_package_types")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `student_packages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `studentId` INTEGER NOT NULL, `packageTypeId` INTEGER NOT NULL, `packageName` TEXT NOT NULL,
            `totalClasses` INTEGER NOT NULL, `validityWeeks` INTEGER, `price` INTEGER NOT NULL,
            `purchasedOnEpochDay` INTEGER NOT NULL, `startedOnEpochDay` INTEGER, `maxVisitsPerAttendance`
            INTEGER NOT NULL DEFAULT 1, FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO
            ACTION ON DELETE CASCADE , FOREIGN KEY(`packageTypeId`) REFERENCES `package_types`(`id`) ON
            UPDATE NO ACTION ON DELETE NO ACTION )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO student_packages (id, studentId, packageTypeId, packageName, totalClasses, validityWeeks, price,
            purchasedOnEpochDay, startedOnEpochDay, maxVisitsPerAttendance)
            SELECT id, studentId, packageTypeId, packageName, totalClasses, validityWeeks, price,
            purchasedOnEpochDay, startedOnEpochDay, maxVisitsPerAttendance FROM package_migration_student_packages
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_student_packages_studentId` ON `student_packages`
            (`studentId`)
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_student_packages_packageTypeId` ON `student_packages`
            (`packageTypeId`)
            """.trimIndent(),
        )
        connection.execSQL("DROP TABLE package_migration_student_packages")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `attendance` (`packageId` INTEGER NOT NULL, `attendedOnEpochDay`
            INTEGER NOT NULL, `visitCount` INTEGER NOT NULL DEFAULT 1, `sessionId` TEXT, PRIMARY
            KEY(`packageId`, `attendedOnEpochDay`), FOREIGN KEY(`packageId`) REFERENCES
            `student_packages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO attendance (packageId, attendedOnEpochDay, visitCount, sessionId)
            SELECT packageId, attendedOnEpochDay, visitCount, sessionId FROM package_migration_attendance
            """.trimIndent(),
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_packageId` ON `attendance` (`packageId`)")
        connection.execSQL("DROP TABLE package_migration_attendance")
        connection.execSQL(
            """
            UPDATE sqlite_sequence SET seq = MAX(seq, COALESCE((SELECT seq FROM package_migration_sequence
            WHERE name = sqlite_sequence.name), 0))
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO sqlite_sequence(name, seq) SELECT name, seq FROM package_migration_sequence WHERE
            name IN ('package_types', 'student_packages') AND name NOT IN (SELECT name FROM sqlite_sequence)
            """.trimIndent(),
        )
        connection.execSQL("DROP TABLE package_migration_sequence")
    }
}
