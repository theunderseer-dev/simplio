package com.theunderseer.simplio.feature.attendance.data.local.database

import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupEntity
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.student
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AttendanceDatabaseFactoryTest {
    @Test
    fun `database persists data after close and reopen`() = runTest {
        // Given
        val tempDirectory =
            withContext(Dispatchers.IO) {
                Files.createTempDirectory(
                    "simplio-attendance-",
                )
            }

        val databasePath =
            tempDirectory.resolve(
                "attendance.db",
            )

        try {
            val database =
                createAttendanceDatabase(
                    path = databasePath.toString(),
                )

            val studentId =
                try {
                    val groupId =
                        database.studentGroupDao().insert(
                            StudentGroupEntity(
                                id = DefaultStudentGroups.GROUP_A.id,
                                name = DefaultStudentGroups.GROUP_A.name,
                            ),
                        ).toInt()

                    database.studentDao().insert(
                        StudentEntity(
                            fullName = student.fullName,
                            groupId = groupId,
                        ),
                    ).toInt()
                } finally {
                    database.close()
                }

            // When
            val reopenedDatabase =
                createAttendanceDatabase(
                    path = databasePath.toString(),
                )

            try {
                // Then
                val persistedStudent =
                    assertNotNull(
                        reopenedDatabase
                            .studentDao()
                            .getById(studentId),
                    )

                assertEquals(
                    student.fullName,
                    persistedStudent.fullName,
                )

                assertEquals(1, persistedStudent.groupId)
            } finally {
                reopenedDatabase.close()
            }
        } finally {
            withContext(Dispatchers.IO) {
                tempDirectory
                    .toFile()
                    .deleteRecursively()
            }
        }
    }
}
