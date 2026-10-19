package com.theunderseer.simplio.feature.attendance.data.local.seed

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentGroupEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudentGroupSeederTest {
    private lateinit var database: AttendanceDatabase
    private lateinit var seeder: StudentGroupSeeder

    private val expectedGroups =
        DefaultStudentGroups.ALL.map { group ->
            testStudentGroupEntity(
                id = group.id,
                name = group.name,
            )
        }

    @BeforeTest
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder<AttendanceDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()

        seeder =
            StudentGroupSeeder(database)
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `seeding fresh database creates default student groups`() = runTest {
        // Given
        assertTrue(database.studentGroupDao().getAll().isEmpty())

        // When
        seeder.seedDefaults()

        // Then
        assertEquals(
            expectedGroups,
            database.studentGroupDao().getAll(),
        )
    }

    @Test
    fun `seeding default student groups twice does not create duplicates`() = runTest {
        // Given
        seeder.seedDefaults()

        // When
        seeder.seedDefaults()

        // Then
        assertEquals(
            expectedGroups,
            database.studentGroupDao().getAll(),
        )
    }

    @Test
    fun `seeding preserves existing groups and creates missing defaults`() = runTest {
        // Given
        val existingGroup =
            testStudentGroupEntity(
                name = "Morning group",
            )

        database.studentGroupDao().insert(existingGroup)

        // When
        seeder.seedDefaults()

        // Then
        val missingGroup =
            expectedGroups.single {
                it.id == DefaultStudentGroups.GROUP_B.id
            }

        assertEquals(
            listOf(existingGroup, missingGroup),
            database.studentGroupDao().getAll(),
        )
    }
}
