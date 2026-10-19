package com.theunderseer.simplio.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.theunderseer.simplio.database.AccountDatabaseSelection
import com.theunderseer.simplio.database.DesktopDatabaseManager
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentRepository
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetAttendanceUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetPackageTypesUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopAttendanceSessionTest {
    private lateinit var userHome: Path
    private lateinit var databases: DesktopDatabaseManager

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        userHome = Files.createTempDirectory("simplio-account-sessions-")
        databases = DesktopDatabaseManager.open(userHome)
    }

    @AfterTest
    fun tearDown() {
        databases.close()
        userHome.toFile().deleteRecursively()
        Dispatchers.resetMain()
    }

    @Test
    fun `account sessions use independent repositories and preserve their own students after reopening`() = runTest {
        val firstPath = accountPath("account-a")
        val secondPath = accountPath("account-b")

        withSession(firstPath) { first ->
            withSession(secondPath) { second ->
                val firstRepository = first.application.koin.get<StudentRepository>()
                val secondRepository = second.application.koin.get<StudentRepository>()

                assertNotSame(firstRepository, secondRepository)
                assertNotSame(
                    first.application.koin.get<AttendanceDatabase>(),
                    second.application.koin.get<AttendanceDatabase>(),
                )

                firstRepository.create("First account student", DefaultStudentGroups.GROUP_A.id, "First note")
                assertEquals(emptyList(), secondRepository.getAll())

                secondRepository.create("Second account student", DefaultStudentGroups.GROUP_B.id, "Second note")
                assertEquals(listOf("First account student"), firstRepository.getAll().map { it.fullName })
            }

            // Closing the second container must not close the first account's dependencies.
            assertEquals(1, first.application.koin.get<StudentRepository>().getAll().size)
        }

        withSession(firstPath) { session ->
            val student = session.application.koin.get<StudentRepository>().getAll().single()
            assertEquals("First account student", student.fullName)
            assertEquals("First note", student.note)
        }

        withSession(secondPath) { session ->
            val student = session.application.koin.get<StudentRepository>().getAll().single()
            assertEquals("Second account student", student.fullName)
            assertEquals("Second note", student.note)
        }
    }

    @Test
    fun `assigning existing database preserves student notes package terms and attendance`() = runTest {
        val legacyPath = databases.localDatabase()

        val registeredStudent = withSession(legacyPath) { session ->
            val packageType = session.application.koin.get<GetPackageTypesUseCase>()().first()
            session.application.koin.get<RegisterStudentUseCase>()(
                fullName = "Existing student",
                groupId = DefaultStudentGroups.GROUP_A.id,
                packageType = packageType,
                registeredOn = LocalDate(2026, 10, 2),
                note = "Return change next time",
            )
        }

        assertEquals(AccountDatabaseSelection.LegacyDataAvailable, databases.selectAccount("account-a"))
        assertEquals(legacyPath, databases.useExistingData("account-a"))

        withSession(accountPath("account-a")) { session ->
            val attendance = session.application.koin.get<GetAttendanceUseCase>()()
            assertEquals(listOf(registeredStudent), attendance.entries)
        }

        withSession(accountPath("account-b")) { session ->
            assertEquals(emptyList(), session.application.koin.get<GetAttendanceUseCase>()().entries)
        }

        withSession(databases.localDatabase()) { session ->
            assertEquals(emptyList(), session.application.koin.get<GetAttendanceUseCase>()().entries)
        }
    }

    @Test
    fun `session clears view models while their dependencies are still available`() = runTest {
        var cleared = false
        var dependenciesAvailable = false

        withSession(accountPath("account-a")) { session ->
            val repository = session.application.koin.get<StudentRepository>()
            val provider = ViewModelProvider.create(
                session.viewModelStore,
                viewModelFactory {
                    initializer {
                        ClearingViewModel {
                            cleared = true
                            dependenciesAvailable = session.application.koin.get<StudentRepository>() === repository
                        }
                    }
                },
            )

            provider[ClearingViewModel::class]
        }

        assertTrue(cleared)
        assertTrue(dependenciesAvailable)
    }

    private fun accountPath(userId: String): Path =
        assertIs<AccountDatabaseSelection.Ready>(databases.selectAccount(userId)).path

    private suspend fun <T> withSession(path: Path, block: suspend (DesktopAttendanceSession) -> T): T {
        val session = DesktopAttendanceSession(path.toString())

        try {
            session.initialize()
            return block(session)
        } finally {
            session.close()
        }
    }

    private class ClearingViewModel(private val onClear: () -> Unit) : ViewModel() {
        override fun onCleared() {
            onClear()
        }
    }
}
