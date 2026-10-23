package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupEntity
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class AttendanceMigration11To12Test {
    @Test
    fun `migration preserves duplicate legacy names and builds normalized guard for new groups`() = runTest {
        val directory = Files.createTempDirectory("simplio-group-migration-")
        val path = directory.resolve("attendance.db")
        try {
            val helper = MigrationTestHelper(
                schemaDirectoryPath = Paths.get(checkNotNull(System.getProperty("simplio.room.schemaDirectory"))),
                databasePath = path,
                driver = BundledSQLiteDriver(),
                databaseClass = AttendanceDatabase::class,
            )
            helper.createDatabase(version = 11).use {
                it.execSQL("INSERT INTO student_groups VALUES (7, 'Private  yoga'), (19, ' PRIVATE yoga ')")
                it.execSQL("INSERT INTO students (id, fullName, groupId, note) VALUES (12, 'Anna', 7, 'Keep note')")
            }
            helper.runMigrationsAndValidate(12, listOf(AttendanceMigration11To12)).close()
            val database = createAttendanceDatabase(path.toString())
            try {
                val groups = database.studentGroupDao().getAll()
                assertEquals(listOf("Private  yoga", " PRIVATE yoga "), groups.map { it.name })
                assertNotEquals(groups[0].nameKey, groups[1].nameKey)
                assertEquals("Keep note", database.studentDao().getById(12)?.note)
                assertFailsWith<Exception> {
                    database.studentGroupDao().insert(StudentGroupEntity(name = "private yoga"))
                }
            } finally {
                database.close()
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
