package com.theunderseer.simplio.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.theunderseer.simplio.database.prepareSimplioDatabasePath
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.seed.PackageTypeSeeder
import com.theunderseer.simplio.feature.attendance.di.attendanceModule
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.result.MarkStudentPresentResult
import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetPackageTypesUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.MarkStudentPresentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RenewStudentPackageUseCase
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class AttendanceKoinIntegrationTest {
    @Test
    fun `attendance graph wires registration to persistent database`() = runTest {
        withAttendanceKoin { koin ->
            // Given
            val database = koin.get<AttendanceDatabase>()
            val registerStudent = koin.get<RegisterStudentUseCase>()
            val packageType = koin.insertPackageType()
            val registeredOn = LocalDate(2026, 10, 1)

            // When
            val registeredStudent =
                registerStudent(
                    fullName = "Anna Smith",
                    packageType = packageType,
                    registeredOn = registeredOn,
                )

            // Then
            assertTrue(registeredStudent.student.id > 0)

            val persistedStudent =
                assertNotNull(
                    database.studentDao().getById(
                        registeredStudent.student.id,
                    ),
                )

            assertEquals(
                "Anna Smith",
                persistedStudent.fullName,
            )

            val persistedPackage =
                assertNotNull(
                    database.studentPackageDao().getLatestForStudent(
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
                database.attendanceDao().getForPackage(
                    persistedPackage.id,
                )

            assertEquals(
                listOf(registeredOn.toEpochDays()),
                attendance.map { it.attendedOnEpochDay },
            )
        }
    }

    @Test
    fun `attendance graph wires package renewal to persistent database`() = runTest {
        withAttendanceKoin { koin ->
            // Given
            val database = koin.get<AttendanceDatabase>()
            val registerStudent = koin.get<RegisterStudentUseCase>()
            val renewStudentPackage = koin.get<RenewStudentPackageUseCase>()
            val packageType = koin.insertPackageType()

            val registeredStudent =
                registerStudent(
                    fullName = "Anna Smith",
                    packageType = packageType,
                    registeredOn = LocalDate(2026, 8, 1),
                )

            val renewedOn = LocalDate(2026, 10, 1)

            // When
            val result =
                renewStudentPackage(
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
                    database.studentPackageDao().getLatestForStudent(
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
                database.attendanceDao().getForPackage(
                    persistedPackage.id,
                )

            assertEquals(
                listOf(renewedOn.toEpochDays()),
                attendance.map { it.attendedOnEpochDay },
            )
        }
    }

    @Test
    fun `attendance graph wires attendance marking to persistent database`() = runTest {
        withAttendanceKoin { koin ->
            // Given
            val database = koin.get<AttendanceDatabase>()
            val registerStudent = koin.get<RegisterStudentUseCase>()
            val markStudentPresent = koin.get<MarkStudentPresentUseCase>()
            val packageType = koin.insertPackageType()
            val registeredOn = LocalDate(2026, 10, 1)

            val registeredStudent =
                registerStudent(
                    fullName = "Anna Smith",
                    packageType = packageType,
                    registeredOn = registeredOn,
                )

            val attendanceDate = LocalDate(2026, 10, 3)

            // When
            val result =
                markStudentPresent(
                    studentId = registeredStudent.student.id,
                    attendanceDate = attendanceDate,
                )

            // Then
            val success =
                assertIs<MarkStudentPresentResult.Success>(
                    result,
                )

            assertEquals(
                setOf(registeredOn, attendanceDate),
                success.studentPackage.attendanceDates,
            )

            val persistedAttendance =
                database.attendanceDao().getForPackage(
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
    fun `attendance graph exposes persisted package types`() = runTest {
        withAttendanceKoin { koin ->
            // Given
            val firstPackage =
                koin.insertPackageType(
                    name = "4-class package",
                    totalClasses = 4,
                    validityWeeks = 4,
                    price = 1400,
                )

            val secondPackage = koin.insertPackageType()
            val getPackageTypes = koin.get<GetPackageTypesUseCase>()

            // When
            val packageTypes = getPackageTypes()

            // Then
            assertEquals(
                listOf(firstPackage, secondPackage),
                packageTypes,
            )
        }
    }

    @Test
    fun `attendance graph initializes default package types`() = runTest {
        withAttendanceKoin { koin ->
            // Given
            val seeder = koin.get<PackageTypeSeeder>()
            val getPackageTypes = koin.get<GetPackageTypesUseCase>()

            // When
            seeder.seedDefaults()

            // Then
            val packageTypes = getPackageTypes()

            assertEquals(3, packageTypes.size)

            assertEquals(
                listOf(
                    PackageType(
                        id = packageTypes[0].id,
                        name = "4-class package",
                        totalClasses = 4,
                        validityWeeks = 4,
                        price = 1400,
                    ),
                    PackageType(
                        id = packageTypes[1].id,
                        name = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 5,
                        price = 1700,
                    ),
                    PackageType(
                        id = packageTypes[2].id,
                        name = "Duo 8 · Special rate",
                        totalClasses = 8,
                        validityWeeks = 4,
                        price = 2400,
                        maxVisitsPerAttendance = 2,
                    ),
                ),
                packageTypes,
            )
        }
    }

    @Test
    fun `attendance graph exposes persisted attendance state`() = runTest {
        withAttendanceKoin { koin ->
            // Given
            val registerStudent = koin.get<RegisterStudentUseCase>()
            val getAttendance = koin.get<GetAttendanceUseCase>()
            val packageType = koin.insertPackageType()

            val registeredStudent =
                registerStudent(
                    fullName = "Anna Smith",
                    packageType = packageType,
                    registeredOn = LocalDate(2026, 10, 2),
                )

            // When
            val attendance = getAttendance()

            // Then
            assertEquals(
                listOf(registeredStudent),
                attendance.entries,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `attendance graph creates view model and loads persisted attendance`() = runTest {
        Dispatchers.setMain(
            StandardTestDispatcher(testScheduler),
        )

        try {
            // Given
            val today = LocalDate(2026, 10, 9)
            val clock =
                object : Clock {
                    override fun now(): Instant = Instant.parse("2026-10-09T05:00:00Z")
                }

            withAttendanceKoin(clock = clock) { koin ->
                koin.get<PackageTypeSeeder>().seedDefaults()

                val packageTypes =
                    koin.get<GetPackageTypesUseCase>()()

                val packageType =
                    packageTypes.first {
                        it.totalClasses == 4
                    }

                val registeredStudent =
                    koin.get<RegisterStudentUseCase>()(
                        fullName = "Anna Smith",
                        packageType = packageType,
                        registeredOn = LocalDate(2026, 10, 2),
                    )

                val viewModelStore = ViewModelStore()

                try {
                    val provider =
                        ViewModelProvider.create(
                            viewModelStore,
                            viewModelFactory {
                                initializer {
                                    koin.get<AttendanceViewModel>()
                                }
                            },
                        )

                    // When
                    val viewModel =
                        provider[AttendanceViewModel::class]

                    val state =
                        viewModel.uiState.first {
                            it !is AttendanceUiState.Loading
                        }

                    // Then
                    val content =
                        assertIs<AttendanceUiState.Content>(
                            state,
                        )

                    assertEquals(
                        today,
                        content.currentDate,
                    )

                    assertEquals(
                        packageTypes,
                        content.packageTypes,
                    )

                    assertEquals(
                        listOf(registeredStudent),
                        content.attendance.entries,
                    )

                    assertSame(
                        viewModel,
                        provider[AttendanceViewModel::class],
                    )
                } finally {
                    viewModelStore.clear()
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun withAttendanceKoin(clock: Clock = Clock.System, block: suspend (Koin) -> Unit) {
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

            val application =
                koinApplication {
                    modules(
                        attendanceModule,
                        desktopAttendanceModule(
                            databasePath = databasePath,
                            clock = clock,
                        ),
                    )
                }

            try {
                block(application.koin)
            } finally {
                application.close()
            }
        } finally {
            withContext(Dispatchers.IO) {
                userHome.toFile().deleteRecursively()
            }
        }
    }

    private suspend fun Koin.insertPackageType(
        name: String = "5-class package",
        totalClasses: Int = 5,
        validityWeeks: Int = 5,
        price: Int = 1700,
    ): PackageType {
        val id =
            get<AttendanceDatabase>()
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
