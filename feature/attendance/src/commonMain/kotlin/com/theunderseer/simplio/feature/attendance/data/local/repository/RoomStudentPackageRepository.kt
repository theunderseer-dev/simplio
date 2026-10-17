package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.dao.AttendanceDao
import com.theunderseer.simplio.feature.attendance.data.local.dao.StudentPackageDao
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity
import com.theunderseer.simplio.feature.attendance.data.local.mapper.toDomain
import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import kotlinx.datetime.LocalDate

class RoomStudentPackageRepository(
    private val studentPackageDao: StudentPackageDao,
    private val attendanceDao: AttendanceDao,
) : StudentPackageRepository {
    override suspend fun create(
        studentId: Int,
        packageType: PackageType,
        purchasedOn: LocalDate,
        initialAttendance: InitialAttendance?,
    ): StudentPackage {
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

        return entity
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
        studentPackageDao.activateIfNeeded(
            packageId = packageId,
            startedOnEpochDay = attendedOn.toEpochDays(),
        )

        attendanceDao.insert(
            AttendanceEntity(
                packageId = packageId,
                attendedOnEpochDay = attendedOn.toEpochDays(),
                visitCount = visitCount,
            ),
        )
    }

    override suspend fun removeAttendance(packageId: Int, attendedOn: LocalDate) {
        attendanceDao.deleteForPackageOnDate(
            packageId = packageId,
            attendedOnEpochDay = attendedOn.toEpochDays(),
        )

        studentPackageDao.updateStartedOnFromAttendance(packageId)
    }
}
