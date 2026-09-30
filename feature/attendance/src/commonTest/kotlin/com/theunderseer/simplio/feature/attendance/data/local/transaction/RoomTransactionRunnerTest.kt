package com.theunderseer.simplio.feature.attendance.data.local.transaction

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomTransactionRunnerTest {
    private lateinit var database: AttendanceDatabase
    private lateinit var transactionRunner: RoomTransactionRunner

    @BeforeTest
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder<AttendanceDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()

        transactionRunner =
            RoomTransactionRunner(
                database = database,
            )
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `successful transaction commits changes`() =
        runTest {
            // When
            val studentId =
                transactionRunner.run {
                    database
                        .studentDao()
                        .insert(
                            StudentEntity(
                                fullName = "Anna Smith",
                            ),
                        ).toInt()
                }

            // Then
            val persistedStudent =
                assertNotNull(
                    database
                        .studentDao()
                        .getById(studentId),
                )

            assertEquals(
                "Anna Smith",
                persistedStudent.fullName,
            )
        }

    @Test
    fun `failed transaction rethrows exception and rolls back changes`() =
        runTest {
            // Given
            var insertedStudentId: Int? = null

            // When
            val exception =
                assertFailsWith<IllegalStateException> {
                    transactionRunner.run {
                        insertedStudentId =
                            database
                                .studentDao()
                                .insert(
                                    StudentEntity(
                                        fullName = "Anna Smith",
                                    ),
                                ).toInt()

                        error("Transaction failed")
                    }
                }

            // Then
            assertEquals(
                "Transaction failed",
                exception.message,
            )

            val studentId =
                assertNotNull(insertedStudentId)

            assertNull(
                database
                    .studentDao()
                    .getById(studentId),
            )
        }

    @Test
    fun `failed transaction rolls back changes across multiple DAOs`() =
        runTest {
            // Given
            // an existing package type outside the transaction
            val packageTypeId =
                database
                    .packageTypeDao()
                    .insert(
                        PackageTypeEntity(
                            name = "5-class package",
                            totalClasses = 5,
                            validityWeeks = 6,
                            price = 1700,
                        ),
                    ).toInt()

            val startedOn = LocalDate(2026, 9, 30)

            var insertedStudentId: Int? = null
            var insertedPackageId: Int? = null

            // When
            val exception =
                assertFailsWith<IllegalStateException> {
                    transactionRunner.run {
                        val studentId =
                            database
                                .studentDao()
                                .insert(
                                    StudentEntity(
                                        fullName = "Anna Smith",
                                    ),
                                ).toInt()

                        insertedStudentId = studentId

                        val packageId =
                            database
                                .studentPackageDao()
                                .insert(
                                    StudentPackageEntity(
                                        studentId = studentId,
                                        packageTypeId = packageTypeId,
                                        packageName = "5-class package",
                                        totalClasses = 5,
                                        validityWeeks = 6,
                                        price = 1700,
                                        startedOnEpochDay = startedOn.toEpochDays(),
                                    ),
                                ).toInt()

                        insertedPackageId = packageId

                        database
                            .attendanceDao()
                            .insert(
                                AttendanceEntity(
                                    packageId = packageId,
                                    attendedOnEpochDay = startedOn.toEpochDays(),
                                ),
                            )

                        error("Transaction failed")
                    }
                }

            // Then
            assertEquals(
                "Transaction failed",
                exception.message,
            )

            val studentId =
                assertNotNull(insertedStudentId)

            val packageId =
                assertNotNull(insertedPackageId)

            assertNull(
                database
                    .studentDao()
                    .getById(studentId),
            )

            assertNull(
                database
                    .studentPackageDao()
                    .getLatestForStudent(studentId),
            )

            assertTrue(
                database
                    .attendanceDao()
                    .getForPackage(packageId)
                    .isEmpty(),
            )

            // And
            // data that existed before the transaction remains
            assertNotNull(
                database
                    .packageTypeDao()
                    .getById(packageTypeId),
            )
        }
}
