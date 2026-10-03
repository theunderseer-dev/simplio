package com.theunderseer.simplio.feature.attendance.data.local.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RoomStudentRepositoryTest {
    private lateinit var database: AttendanceDatabase
    private lateinit var repository: RoomStudentRepository

    @BeforeTest
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder<AttendanceDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()

        repository =
            RoomStudentRepository(
                studentDao = database.studentDao(),
            )
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `creating student persists student with generated identity`() = runTest {
        // Given
        val fullName = "Anna Smith"

        // When
        val student =
            repository.create(
                fullName = fullName,
            )

        // Then
        assertTrue(student.id > 0)
        assertEquals(fullName, student.fullName)

        // And
        // the student is persisted in Room
        val persistedStudent =
            assertNotNull(
                database.studentDao().getById(student.id),
            )

        assertEquals(
            student.id,
            persistedStudent.id,
        )

        assertEquals(
            fullName,
            persistedStudent.fullName,
        )
    }

    @Test
    fun `getAll returns persisted students`() = runTest {
        // Given
        val anna =
            repository.create(
                fullName = "Anna Smith",
            )

        val bob =
            repository.create(
                fullName = "Bob Lee",
            )

        // When
        val students = repository.getAll()

        // Then
        assertEquals(
            listOf(
                anna,
                bob,
            ),
            students,
        )
    }
}
