package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

internal fun formatAttendanceForSharing(attendance: Attendance, currentDate: LocalDate): String =
    attendance.entries.joinToString(separator = "\n") { registeredStudent ->
        val studentPackage = registeredStudent.studentPackage

        val renewalMarker =
            if (
                studentPackage.isCompleted ||
                studentPackage.isExpiredOn(currentDate)
            ) {
                "♻️"
            } else {
                ""
            }

        val expirationDate =
            "(until " +
                studentPackage.expiresOn.day
                    .toString()
                    .padStart(2, '0') +
                "/" +
                studentPackage.expiresOn.month.number
                    .toString()
                    .padStart(2, '0') +
                ")"

        "$renewalMarker${registeredStudent.student.fullName} " +
            "${studentPackage.usedClasses}/${studentPackage.packageType.totalClasses} " +
            expirationDate
    }
