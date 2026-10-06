package com.theunderseer.simplio.attendance

import com.theunderseer.simplio.database.prepareSimplioDatabasePath
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.result.MarkStudentPresentResult
import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AttendanceComponentTest {
    @Test
    fun `attendance component wires registration to persistent database`() = runTest {
        withAttendanceComponent { component ->
            // Given
            val packageType = component.insertPackageType()

            val registeredOn =
                LocalDate(
                    2026,
                    10,
                    1,
                )

            // When
            val registeredStudent =
                component.registerStudent(
                    fullName = "Anna Smith",
                    packageType = packageType,
                    registeredOn = registeredOn,
                )

            // Then
            assertTrue(
                registeredStudent.student.id > 0,
            )

            val persistedStudent =
                assertNotNull(
                    component.database
                        .studentDao()
                        .getById(
                            registeredStudent.student.id,
                        ),
                )

            assertEquals(
                "Anna Smith",
                persistedStudent.fullName,
            )

            val persistedPackage =
                assertNotNull(
                    component.database
                        .studentPackageDao()
                        .getLatestForStudent(
                            registeredStudent.student.id,
                        ),
                )

            assertEquals(
                packageType.id,
                persistedPackage.packageTypeId,
            )

            assertEquals(
                registeredOn.toEpochDays(),
                persistedPackage.startedOnEpochDay,
            )

            val attendance =
                component.database
                    .attendanceDao()
                    .getForPackage(
                        persistedPackage.id,
                    )

            assertEquals(
                listOf(registeredOn.toEpochDays()),
                attendance.map { it.attendedOnEpochDay },
            )
        }
    }

    @Test
    fun `attendance component wires package renewal to persistent database`() = runTest {
        withAttendanceComponent { component ->
            // Given
            val packageType = component.insertPackageType()

            val registeredStudent =
                component.registerStudent(
                    fullName = "Anna Smith",
                    packageType = packageType,
                    registeredOn = LocalDate(2026, 8, 1),
                )

            val renewedOn =
                LocalDate(2026, 10, 1)

            // When
            val result =
                component.renewStudentPackage(
                    studentId = registeredStudent.student.id,
                    packageType = packageType,
                    renewedOn = renewedOn,
                )

            // Then
            val success =
                assertIs<RenewStudentPackageResult.Success>(
                    result,
                )

            val persistedPackage =
                assertNotNull(
                    component.database
                        .studentPackageDao()
                        .getLatestForStudent(
                            registeredStudent.student.id,
                        ),
                )

            assertEquals(
                success.studentPackage.id,
                persistedPackage.id,
            )

            assertEquals(
                renewedOn.toEpochDays(),
                persistedPackage.startedOnEpochDay,
            )

            val attendance =
                component.database
                    .attendanceDao()
                    .getForPackage(
                        persistedPackage.id,
                    )

            assertEquals(
                listOf(renewedOn.toEpochDays()),
                attendance.map { it.attendedOnEpochDay },
            )
        }
    }

    @Test
    fun `attendance component wires attendance marking to persistent database`() = runTest {
        withAttendanceComponent { component ->
            // Given
            val packageType = component.insertPackageType()

            val registeredOn =
                LocalDate(2026, 10, 1)

            val registeredStudent =
                component.registerStudent(
                    fullName = "Anna Smith",
                    packageType = packageType,
                    registeredOn = registeredOn,
                )

            val attendanceDate =
                LocalDate(2026, 10, 3)

            // When
            val result =
                component.markStudentPresent(
                    studentId = registeredStudent.student.id,
                    attendanceDate = attendanceDate,
                )

            // Then
            val success =
                assertIs<MarkStudentPresentResult.Success>(
                    result,
                )

            assertEquals(
                setOf(
                    registeredOn,
                    attendanceDate,
                ),
                success.studentPackage.attendanceDates,
            )

            val persistedAttendance =
                component.database
                    .attendanceDao()
                    .getForPackage(
                        registeredStudent.studentPackage.id,
                    )

            assertEquals(
                setOf(
                    registeredOn.toEpochDays(),
                    attendanceDate.toEpochDays(),
                ),
                persistedAttendance
                    .map { it.attendedOnEpochDay }
                    .toSet(),
            )
        }
    }

    @Test
    fun `attendance component exposes persisted package types`() = runTest {
        withAttendanceComponent { component ->
            // Given
            val firstId =
                component.database
                    .packageTypeDao()
                    .insert(
                        PackageTypeEntity(
                            name = "4-class package",
                            totalClasses = 4,
                            validityWeeks = 4,
                            price = 1400,
                        ),
                    ).toInt()

            val secondId =
                component.database
                    .packageTypeDao()
                    .insert(
                        PackageTypeEntity(
                            name = "5-class package",
                            totalClasses = 5,
                            validityWeeks = 5,
                            price = 1700,
                        ),
                    ).toInt()

            // When
            val packageTypes = component.getPackageTypes()

            // Then
            assertEquals(
                listOf(
                    PackageType(
                        id = firstId,
                        name = "4-class package",
                        totalClasses = 4,
                        validityWeeks = 4,
                        price = 1400,
                    ),
                    PackageType(
                        id = secondId,
                        name = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 5,
                        price = 1700,
                    ),
                ),
                packageTypes,
            )
        }
    }

    @Test
    fun `attendance component initializes default package types`() = runTest {
        withAttendanceComponent { component ->
            // Given
            // a fresh attendance component

            // When
            component.initialize()

            // Then
            val packageTypes = component.getPackageTypes()

            assertEquals(
                2,
                packageTypes.size,
            )

            assertEquals(
                PackageType(
                    id = packageTypes[0].id,
                    name = "4-class package",
                    totalClasses = 4,
                    validityWeeks = 4,
                    price = 1400,
                ),
                packageTypes[0],
            )

            assertEquals(
                PackageType(
                    id = packageTypes[1].id,
                    name = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 5,
                    price = 1700,
                ),
                packageTypes[1],
            )
        }
    }

    @Test
    fun `attendance component exposes persisted attendance state`() = runTest {
        withAttendanceComponent { component ->
            // Given
            val packageType = component.insertPackageType()

            val registeredStudent =
                component.registerStudent(
                    fullName = "Anna Smith",
                    packageType = packageType,
                    registeredOn = LocalDate(2026, 10, 2),
                )

            // When
            val attendance =
                component.getAttendance()

            // Then
            assertEquals(
                listOf(registeredStudent),
                attendance.entries,
            )
        }
    }

    private suspend fun withAttendanceComponent(block: suspend (AttendanceComponent) -> Unit) {
        val userHome =
            withContext(Dispatchers.IO) {
                Files.createTempDirectory(
                    "simplio-test-home",
                )
            }

        try {
            val databasePath =
                withContext(Dispatchers.IO) {
                    prepareSimplioDatabasePath(
                        userHome = userHome,
                    ).toString()
                }

            val component =
                createAttendanceComponent(
                    databasePath = databasePath,
                )

            component.use { component ->
                block(component)
            }
        } finally {
            withContext(Dispatchers.IO) {
                userHome
                    .toFile()
                    .deleteRecursively()
            }
        }
    }

    private suspend fun AttendanceComponent.insertPackageType(
        name: String = "5-class package",
        totalClasses: Int = 5,
        validityWeeks: Int = 5,
        price: Int = 1700,
    ): PackageType {
        val id =
            database
                .packageTypeDao()
                .insert(
                    PackageTypeEntity(
                        name = name,
                        totalClasses = totalClasses,
                        validityWeeks = validityWeeks,
                        price = price,
                    ),
                ).toInt()

        return PackageType(
            id = id,
            name = name,
            totalClasses = totalClasses,
            validityWeeks = validityWeeks,
            price = price,
        )
    }
}
