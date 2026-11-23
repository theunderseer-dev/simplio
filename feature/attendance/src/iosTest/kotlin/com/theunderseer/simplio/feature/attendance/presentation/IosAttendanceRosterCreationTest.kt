package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomAttendanceRoster
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
import com.theunderseer.simplio.feature.attendance.di.attendanceModule
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.fake.FakeAttendanceCloud
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IosAttendanceRosterCreationTest {
    @Test
    fun `creates a schedule package and first student and syncs them to another installation`() = runTest {
        withSyncDatabases { mobile, peer ->
            val application = koinApplication { modules(module { single { mobile } }, attendanceModule()) }
            try {
                val dependencies = application.koin
                val creation = iosAttendanceCreation(dependencies.get(), dependencies.get(), dependencies.get())
                val roster = RoomAttendanceRoster(mobile)
                val schedule = testClassSchedule()
                val group = CreateStudentGroupRequest("Friday group", newSchedule = schedule)
                assertEquals(CreateStudentGroupUiState.Success(group.name), creation.addSchedule(group))

                val packageRequest = CreatePackageTypeRequest("Friday pass", 4, 1200, 4, 1)
                assertEquals(CreatePackageTypeUiState.Success(packageRequest.name), creation.addPackage(packageRequest))
                assertEquals(CreatePackageTypeUiState.DuplicateName, creation.addPackage(packageRequest))

                val emptyRoster = roster.getAll()
                assertTrue(emptyRoster.attendance.entries.isEmpty())
                val groupId = emptyRoster.studentGroups.single().id
                assertEquals(listOf(schedule), emptyRoster.groupSchedules[groupId])
                val template = emptyRoster.packageTypes.single()
                val registration = IosStudentRegistration(
                    name = "Friday student",
                    groupId = groupId,
                    packageType = template,
                    registeredOn = AttendanceTestData.firstClassDate,
                    visitCount = 1,
                    recordFirstAttendance = true,
                    note = "First class",
                )
                assertIs<RegisterStudentUiState.Success>(creation.addStudent(registration))
                val localRoster = roster.getAll()
                val student = localRoster.attendance.entries.single()
                assertEquals(1, student.studentPackage.usedClasses)
                assertEquals(registration.note, student.student.note)

                val cloud = FakeAttendanceCloud()
                cloud.uploadPending(mobile)
                RoomPullStore(peer, cloud.target).refreshFrom(cloud)
                val peerRoster = RoomAttendanceRoster(peer).getAll()
                assertEquals(localRoster.attendance, peerRoster.attendance)
                assertEquals(localRoster.studentGroups, peerRoster.studentGroups)
                assertEquals(localRoster.packageTypes, peerRoster.packageTypes)
                assertEquals(localRoster.groupSchedules, peerRoster.groupSchedules)
                assertEquals(schedule, peerRoster.classSchedules.single { it.id == schedule.id })
            } finally {
                application.close()
            }
        }
    }
}
