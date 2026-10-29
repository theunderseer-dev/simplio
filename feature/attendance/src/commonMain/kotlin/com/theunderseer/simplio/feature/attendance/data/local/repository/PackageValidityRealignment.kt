package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.mapper.toDomain
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncJournal
import com.theunderseer.simplio.feature.attendance.domain.schedule.nextValidityDate

/** Keep awarded class shifts aligned when correction/reactivation moves the first attendance. */
internal suspend fun AttendanceDatabase.realignPackageValidity(
    packageId: Int,
    previousStartedOn: Long?,
    journal: RoomSyncJournal,
) {
    val entity = checkNotNull(studentPackageDao().getById(packageId))
    if (entity.startedOnEpochDay == previousStartedOn || entity.validityExtensions.isEmpty()) return
    var expiration = entity.copy(validityExtensions = emptyMap())
        .toDomain(attendanceDao().getForPackage(packageId)).expiresOn ?: return
    val schedules = RoomClassScheduleRepository(this).getAll().associateBy { it.id }
    val grants = linkedMapOf<String, Int>()
    entity.validityExtensions.keys.sortedWith(
        compareBy<String> { it.substringAfterLast(':').toLong() }.thenBy { it },
    ).forEach { id ->
        val next = schedules.getValue(id.substringBefore(':')).nextValidityDate(expiration)
        grants[id] = (next.toEpochDays() - expiration.toEpochDays()).toInt()
        expiration = next
    }
    if (grants != entity.validityExtensions) {
        studentPackageDao().update(entity.copy(validityExtensions = grants))
        journal.upsertPackage(packageId)
    }
}
