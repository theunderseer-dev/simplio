package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.database.createAttendanceDatabase
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentGroupEntity
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudentNotePersistenceTest {
    @Test
    fun `created updated and cleared notes survive reopening the database`() = runTest {
        val directory = Files.createTempDirectory("simplio-student-note-")
        val path = directory.resolve("attendance.db").toString()

        try {
            val studentId = withDatabase(path) { database ->
                val groupId = database.studentGroupDao().insert(testStudentGroupEntity()).toInt()
                RoomStudentRepository(database.studentDao()).create(
                    fullName = AttendanceTestData.student.fullName,
                    groupId = groupId,
                    note = AttendanceTestData.studentNote,
                ).id
            }

            withDatabase(path) { database ->
                val repository = RoomStudentRepository(database.studentDao())
                val student = repository.getAll().single()
                assertEquals(studentId, student.id)
                assertEquals(AttendanceTestData.studentNote, student.note)
                assertTrue(repository.updateNote(studentId, "Bring a receipt next time"))
            }

            withDatabase(path) { database ->
                val repository = RoomStudentRepository(database.studentDao())
                assertEquals("Bring a receipt next time", repository.getAll().single().note)
                assertTrue(repository.updateNote(studentId, ""))
            }

            withDatabase(path) { database ->
                val repository = RoomStudentRepository(database.studentDao())
                assertEquals("", repository.getAll().single().note)
                assertFalse(repository.updateNote(studentId + 1, "Unknown student"))
                assertEquals("", repository.getAll().single().note)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private suspend fun <T> withDatabase(path: String, block: suspend (AttendanceDatabase) -> T): T {
        val database = createAttendanceDatabase(path)
        return try {
            block(database)
        } finally {
            database.close()
        }
    }
}
