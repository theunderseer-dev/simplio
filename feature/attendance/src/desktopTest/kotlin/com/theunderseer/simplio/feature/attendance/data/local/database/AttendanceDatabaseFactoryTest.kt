package com.theunderseer.simplio.feature.attendance.data.local.database

import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AttendanceDatabaseFactoryTest {
    @Test
    fun `database persists data after close and reopen`() =
        runTest {
            // Given
            val databasePath =
                Files
                    .createTempFile(
                        "simplio-attendance-",
                        ".db",
                    )

            databasePath.deleteIfExists()

            try {
                val database =
                    createAttendanceDatabase(
                        path = databasePath.toString(),
                    )

                val studentId =
                    database
                        .studentDao()
                        .insert(
                            StudentEntity(
                                fullName = "Anna Smith",
                            ),
                        ).toInt()

                database.close()

                // When
                val reopenedDatabase =
                    createAttendanceDatabase(
                        path = databasePath.toString(),
                    )

                // Then
                val persistedStudent =
                    assertNotNull(
                        reopenedDatabase
                            .studentDao()
                            .getById(studentId),
                    )

                assertEquals(
                    "Anna Smith",
                    persistedStudent.fullName,
                )

                reopenedDatabase.close()
            } finally {
                databasePath.deleteIfExists()
            }
        }
}
