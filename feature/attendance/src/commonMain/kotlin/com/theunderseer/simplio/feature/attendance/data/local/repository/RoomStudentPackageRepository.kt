package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity
import com.theunderseer.simplio.feature.attendance.data.local.mapper.toDomain
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncJournal
import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import kotlinx.datetime.LocalDate

class RoomStudentPackageRepository(private val database: AttendanceDatabase) : StudentPackageRepository {
    private val studentPackageDao = database.studentPackageDao()
    private val attendanceDao = database.attendanceDao()

    override suspend fun create(
        studentId: Int,
        packageType: PackageType,
        purchasedOn: LocalDate,
        initialAttendance: InitialAttendance?,
    ): StudentPackage = RoomSyncJournal.write(database) { journal ->
        val entity = StudentPackageEntity(
            studentId = studentId,
            packageTypeId = packageType.id,
            packageName = packageType.name,
            totalClasses = packageType.totalClasses,
            validityWeeks = packageType.validityWeeks,
            price = packageType.price,
            purchasedOnEpochDay = purchasedOn.toEpochDays(),
            startedOnEpochDay = initialAttendance?.date?.toEpochDays(),
            maxVisitsPerAttendance = packageType.maxVisitsPerAttendance,
        )

        val id = studentPackageDao.insert(entity).toInt()

        val attendance = initialAttendance?.let {
            AttendanceEntity(
                packageId = id,
                attendedOnEpochDay = it.date.toEpochDays(),
                visitCount = it.visitCount,
            )
        }

        if (attendance != null) {
            attendanceDao.insert(attendance)
        }

        journal.ensurePackage(id)
        if (attendance != null) {
            journal.ensureAttendance(id, attendance.attendedOnEpochDay)
        }

        entity
            .copy(id = id)
            .toDomain(attendance = listOfNotNull(attendance))
    }

    override suspend fun getLatestPackageForStudent(studentId: Int): StudentPackage? {
        val entity =
            studentPackageDao.getLatestForStudent(studentId)
                ?: return null

        val attendance = attendanceDao.getForPackage(entity.id)

        return entity.toDomain(attendance = attendance)
    }

    override suspend fun recordAttendance(packageId: Int, attendedOn: LocalDate, visitCount: Int) {
        RoomSyncJournal.write(database) { journal ->
            val epochDay = attendedOn.toEpochDays()
            if (attendanceDao.getForPackage(packageId).any { it.attendedOnEpochDay == epochDay }) {
                return@write
            }
            attendanceDao.insert(
                AttendanceEntity(
                    packageId = packageId,
                    attendedOnEpochDay = epochDay,
                    visitCount = visitCount,
                ),
            )
            studentPackageDao.activateIfNeeded(packageId, epochDay)
            journal.upsertAttendance(packageId, epochDay)
        }
    }

    override suspend fun removeAttendance(packageId: Int, attendedOn: LocalDate) {
        RoomSyncJournal.write(database) { journal ->
            val epochDay = attendedOn.toEpochDays()
            if (attendanceDao.getForPackage(packageId).none { it.attendedOnEpochDay == epochDay }) {
                return@write
            }
            journal.deleteAttendance(packageId, epochDay)
            attendanceDao.deleteForPackageOnDate(packageId, epochDay)
            studentPackageDao.updateStartedOnFromAttendance(packageId)
        }
    }
}
