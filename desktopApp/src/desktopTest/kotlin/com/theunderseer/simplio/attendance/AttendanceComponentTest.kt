package com.theunderseer.simplio.attendance

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
    fun `attendance component wires registration to persistent database`() =
        runTest {
            withAttendanceComponent { component ->
                // Given
                val packageTypeId =
                    component.database
                        .packageTypeDao()
                        .insert(
                            PackageTypeEntity(
                                name = "5-class package",
                                totalClasses = 5,
                                validityWeeks = 6,
                                price = 1700,
                            ),
                        ).toInt()

                val packageType =
                    PackageType(
                        id = packageTypeId,
                        name = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                    )

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
                    packageTypeId,
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
    fun `attendance component wires package renewal to persistent database`() =
        runTest {
            withAttendanceComponent { component ->
                // Given
                val packageTypeId =
                    component.database
                        .packageTypeDao()
                        .insert(
                            PackageTypeEntity(
                                name = "5-class package",
                                totalClasses = 5,
                                validityWeeks = 6,
                                price = 1700,
                            ),
                        ).toInt()

                val packageType =
                    PackageType(
                        id = packageTypeId,
                        name = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                    )

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
    fun `attendance component wires attendance marking to persistent database`() =
        runTest {
            withAttendanceComponent { component ->
                // Given
                val packageTypeId =
                    component.database
                        .packageTypeDao()
                        .insert(
                            PackageTypeEntity(
                                name = "5-class package",
                                totalClasses = 5,
                                validityWeeks = 6,
                                price = 1700,
                            ),
                        ).toInt()

                val packageType =
                    PackageType(
                        id = packageTypeId,
                        name = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                    )

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
    fun `attendance component exposes persisted package types through repository`() =
        runTest {
            withAttendanceComponent { component ->
                // Given
                val firstId =
                    component.database
                        .packageTypeDao()
                        .insert(
                            PackageTypeEntity(
                                name = "4-class package",
                                totalClasses = 4,
                                validityWeeks = 5,
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
                                validityWeeks = 6,
                                price = 1700,
                            ),
                        ).toInt()

                // When
                val packageTypes =
                    component.packageTypeRepository.getAll()

                // Then
                assertEquals(
                    listOf(
                        PackageType(
                            id = firstId,
                            name = "4-class package",
                            totalClasses = 4,
                            validityWeeks = 5,
                            price = 1400,
                        ),
                        PackageType(
                            id = secondId,
                            name = "5-class package",
                            totalClasses = 5,
                            validityWeeks = 6,
                            price = 1700,
                        ),
                    ),
                    packageTypes,
                )
            }
        }

    @Test
    fun `attendance component initializes default package types`() =
        runTest {
            withAttendanceComponent { component ->
                // Given
                // a fresh attendance component

                // When
                component.initialize()

                // Then
                val packageTypes =
                    component.packageTypeRepository.getAll()

                assertEquals(
                    2,
                    packageTypes.size,
                )

                assertEquals(
                    PackageType(
                        id = packageTypes[0].id,
                        name = "4-class package",
                        totalClasses = 4,
                        validityWeeks = 5,
                        price = 1400,
                    ),
                    packageTypes[0],
                )

                assertEquals(
                    PackageType(
                        id = packageTypes[1].id,
                        name = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                    ),
                    packageTypes[1],
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
            val component =
                createAttendanceComponent(
                    userHome = userHome,
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
}
