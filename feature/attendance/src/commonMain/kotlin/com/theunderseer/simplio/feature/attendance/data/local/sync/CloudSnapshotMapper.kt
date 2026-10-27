package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.ClassSessionStateEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupScheduleEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncRecordEntity
import com.theunderseer.simplio.feature.attendance.data.local.mapper.slotEntities
import com.theunderseer.simplio.feature.attendance.data.local.mapper.toEntity
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudDocument
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudSnapshot
import com.theunderseer.simplio.feature.attendance.domain.model.normalizedGroupName
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.schedule.sessionsBetween
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

/** Validate the entire graph before the first write to the database. */
internal fun mapCloudSnapshot(
    snapshot: AttendanceCloudSnapshot,
    existing: List<SyncRecordEntity>,
): ImportedAttendanceRows {
    val readTime = Instant.parse(snapshot.head.readTime)
    require(snapshot.head.updateTime != null)
    require(Instant.parse(checkNotNull(snapshot.head.updateTime)) <= readTime)
    val documents = snapshot.documents
    require(documents.isNotEmpty())
    require(documents.size <= 50_000)
    val byKey = documents.associateBy { it.type to it.cloudId }
    require(byKey.size == documents.size)
    documents.forEach { document ->
        requireCloudId(document.cloudId)
        require(Instant.parse(document.updateTime) <= readTime)
        val version = document.number("schemaVersion")
        require(version in 1L..3L)
        require(version != 3L || document.type in setOf(SyncEntityType.PACKAGE_TYPE, SyncEntityType.STUDENT_PACKAGE))
        require(document.type != SyncEntityType.CLASS_SCHEDULE || version == 2L)
        require("scheduleIds" !in document.fields || document.type == SyncEntityType.STUDENT_GROUP && version == 2L)
        require(
            "archivedAtEpochMillis" !in document.fields ||
                document.type == SyncEntityType.CLASS_SCHEDULE &&
                version == 2L,
        )
        requireCloudId(document.text("lastMutationId"))
        document.deleted()
    }
    // The upload protocol retains tombstones. Missing acknowledged documents mean an incomplete or reset cloud.
    existing.filter { it.acknowledgedRevision > 0 }.forEach {
        require(byKey.containsKey(it.entityType to it.cloudId))
    }
    val old = existing.associateBy { it.entityType to it.cloudId }
    val ids = mutableMapOf<Pair<SyncEntityType, String>, Int>()
    SyncEntityType.entries.filter {
        it !in setOf(SyncEntityType.ATTENDANCE, SyncEntityType.CLASS_SCHEDULE, SyncEntityType.CLASS_SESSION)
    }.forEach { type ->
        var next = (
            existing.filter { it.entityType == type }.maxOfOrNull {
                checkNotNull(it.localId.toIntOrNull()).also { id -> require(id > 0) }
            } ?: 0
            ).toLong() + 1
        documents.filter { it.type == type }.forEach { document ->
            val key = type to document.cloudId
            val id = old[key]?.localId?.toInt() ?: next++.also { require(it <= Int.MAX_VALUE) }.toInt()
            ids[key] = id
        }
    }
    fun id(type: SyncEntityType, cloudId: String): Int = checkNotNull(ids[type to cloudId])
    fun parent(type: SyncEntityType, cloudId: String): Int {
        require(!checkNotNull(byKey[type to cloudId]).deleted())
        return id(type, cloudId)
    }
    val groupKeys = mutableSetOf<String>()
    val groups = documents.live(SyncEntityType.STUDENT_GROUP).map {
        val groupId = id(SyncEntityType.STUDENT_GROUP, it.cloudId)
        val name = it.nonBlank("name")
        val normalized = normalizedGroupName(name)
        val key = if (groupKeys.add(normalized)) normalized else "$normalized\u0000legacy-$groupId"
        StudentGroupEntity(groupId, name, key)
    }
    val schedulesById = documents.live(SyncEntityType.CLASS_SCHEDULE).map { it.toSchedule() }.associateBy { it.id }
        .toMutableMap()
    val groupSchedules = documents.live(SyncEntityType.STUDENT_GROUP).flatMap { group ->
        val references = group.scheduleIds() ?: listOf(DefaultClassSchedule.ID)
        references.map { scheduleId ->
            if (scheduleId == DefaultClassSchedule.ID && scheduleId !in schedulesById) {
                require(byKey[SyncEntityType.CLASS_SCHEDULE to scheduleId]?.deleted() != true)
                schedulesById[scheduleId] = DefaultClassSchedule.schedule
            }
            require(scheduleId in schedulesById) { "Missing schedule parent" }
            require(schedulesById.getValue(scheduleId).archivedAt == null) {
                "A live group cannot use an archived schedule"
            }
            StudentGroupScheduleEntity(id(SyncEntityType.STUDENT_GROUP, group.cloudId), scheduleId)
        }
    }
    val types = documents.live(SyncEntityType.PACKAGE_TYPE).map {
        it.requireTerms()
        PackageTypeEntity(
            id(SyncEntityType.PACKAGE_TYPE, it.cloudId),
            it.nonBlank("name"),
            it.int("totalClasses"),
            it.validityWeeks(),
            it.int("price"),
            it.int("maxVisitsPerAttendance"),
        )
    }
    val students = documents.live(SyncEntityType.STUDENT).map {
        StudentEntity(
            id(SyncEntityType.STUDENT, it.cloudId),
            it.nonBlank("fullName"),
            parent(SyncEntityType.STUDENT_GROUP, it.text("groupId")),
            it.text("note"),
        )
    }
    val packages = documents.live(SyncEntityType.STUDENT_PACKAGE).map {
        it.requireTerms()
        val purchased = it.number("purchasedOnEpochDay").also { day -> LocalDate.fromEpochDays(day) }
        StudentPackageEntity(
            id = id(SyncEntityType.STUDENT_PACKAGE, it.cloudId),
            studentId = parent(SyncEntityType.STUDENT, it.text("studentId")),
            packageTypeId = parent(SyncEntityType.PACKAGE_TYPE, it.text("packageTypeId")),
            packageName = it.nonBlank("packageName"), totalClasses = it.int("totalClasses"),
            validityWeeks = it.validityWeeks(), price = it.int("price"),
            purchasedOnEpochDay = purchased, maxVisitsPerAttendance = it.int("maxVisitsPerAttendance"),
        )
    }
    val attendance = documents.live(SyncEntityType.ATTENDANCE).map {
        val packageCloudId = it.text("packageId")
        val day = it.number("attendedOnEpochDay").also { value -> LocalDate.fromEpochDays(value) }
        require(it.cloudId == "${packageCloudId}_$day")
        val packageId = parent(SyncEntityType.STUDENT_PACKAGE, packageCloudId)
        val groupId = students.single { student ->
            student.id ==
                packages.single { pack -> pack.id == packageId }.studentId
        }
            .groupId
        val groupScheduleIds = groupSchedules.filter { link -> link.groupId == groupId }.map { link -> link.scheduleId }
        val sessionId = if ("sessionId" in it.fields) it.text("sessionId") else null
        if (sessionId != null) {
            require(it.number("schemaVersion") == 2L)
            val sessions = groupScheduleIds.flatMap { scheduleId ->
                schedulesById.getValue(
                    scheduleId,
                ).sessionsBetween(LocalDate.fromEpochDays(day), LocalDate.fromEpochDays(day))
            }
            require(sessions.any { session -> session.id == sessionId })
        }
        AttendanceEntity(packageId, day, it.int("visitCount"), sessionId)
    }
    val classSessions = documents.live(SyncEntityType.CLASS_SESSION).map { document ->
        require(document.number("schemaVersion") == 1L)
        val scheduleId = document.text("scheduleId")
        val day = document.number("dateEpochDay")
        val date = LocalDate.fromEpochDays(day)
        val schedule = checkNotNull(schedulesById[scheduleId])
        require(schedule.sessionsBetween(date, date).any { it.id == document.cloudId })
        val status = document.text("status")
        require(status in setOf("HELD", "CANCELLED"))
        if (status == "CANCELLED") {
            val groupIds = groupSchedules.filter { it.scheduleId == scheduleId }.map { it.groupId }.toSet()
            val studentIds = students.filter { it.groupId in groupIds }.map { it.id }.toSet()
            val packageIds = packages.filter { it.studentId in studentIds }.map { it.id }.toSet()
            require(
                attendance.none {
                    it.sessionId == document.cloudId ||
                        it.sessionId == null &&
                        it.attendedOnEpochDay == day &&
                        it.packageId in packageIds
                },
            )
        }
        ClassSessionStateEntity(document.cloudId, scheduleId, day, status)
    }
    val attendanceKeys = attendance.map { it.packageId to it.attendedOnEpochDay }
    require(attendanceKeys.distinct().size == attendance.size)
    val restoredPackages = packages.map { pack ->
        val visits = attendance.filter { it.packageId == pack.id }
        require(
            visits.all {
                it.attendedOnEpochDay >= pack.purchasedOnEpochDay && it.visitCount in 1..pack.maxVisitsPerAttendance
            },
        )
        require(visits.sumOf { it.visitCount.toLong() } <= pack.totalClasses)
        val start = visits.minOfOrNull { it.attendedOnEpochDay }
        pack.validityWeeks?.let { weeks ->
            start?.let { LocalDate.fromEpochDays(it).plus(DatePeriod(days = weeks * 7)) }
        }
        pack.copy(startedOnEpochDay = start)
    }
    require(students.all { student -> restoredPackages.any { it.studentId == student.id } })
    val packageOwner = restoredPackages.associate { it.id to it.studentId }
    val records = documents.map { document ->
        val key = document.type to document.cloudId
        val localId = if (document.type in setOf(SyncEntityType.CLASS_SCHEDULE, SyncEntityType.CLASS_SESSION)) {
            document.cloudId
        } else if (document.type == SyncEntityType.ATTENDANCE) {
            val separator = document.cloudId.lastIndexOf('_')
            require(separator > 0)
            val packageCloudId = document.cloudId.substring(0, separator)
            val day = document.cloudId.substring(separator + 1).toLong()
            LocalDate.fromEpochDays(day)
            "${id(SyncEntityType.STUDENT_PACKAGE, packageCloudId)}:$day"
        } else {
            id(document.type, document.cloudId).toString()
        }
        val aggregate = when (document.type) {
            SyncEntityType.CLASS_SCHEDULE -> "CLASS_SCHEDULE:$localId"
            SyncEntityType.CLASS_SESSION -> "CLASS_SESSION:$localId"
            SyncEntityType.STUDENT_GROUP -> "STUDENT_GROUP:$localId"
            SyncEntityType.PACKAGE_TYPE -> "PACKAGE_TYPE:$localId"
            SyncEntityType.STUDENT -> if (document.deleted()) "legacy-deletions" else "STUDENT:$localId"
            SyncEntityType.STUDENT_PACKAGE -> packageOwner[localId.toInt()]?.let { "STUDENT:$it" } ?: "legacy-deletions"
            SyncEntityType.ATTENDANCE -> packageOwner[localId.substringBefore(':').toInt()]
                ?.let { "STUDENT:$it" } ?: "legacy-deletions"
        }
        val revision = old[key]?.revision ?: 1L
        SyncRecordEntity(
            document.type,
            localId,
            document.cloudId,
            revision,
            acknowledgedRevision = revision,
            isDeleted = document.deleted(),
            serverUpdateTime = document.updateTime,
            aggregateKey = aggregate,
        )
    }
    return ImportedAttendanceRows(
        groups, types, students, restoredPackages, attendance, records,
        schedulesById.values.map {
            it.toEntity()
        },
        schedulesById.values.flatMap { it.slotEntities() }, groupSchedules, classSessions,
    )
}

private fun List<AttendanceCloudDocument>.live(type: SyncEntityType) = filter { it.type == type && !it.deleted() }
private fun AttendanceCloudDocument.text(key: String): String = fields.getValue(key).jsonObject
    .getValue("stringValue").jsonPrimitive.also { require(it.isString) }.content
private fun AttendanceCloudDocument.nonBlank(key: String): String = text(key).also { require(it.isNotBlank()) }
private fun AttendanceCloudDocument.number(key: String): Long = fields.getValue(key).jsonObject
    .getValue("integerValue").jsonPrimitive.content.toLong()
private fun AttendanceCloudDocument.int(key: String): Int = number(key).also {
    require(it in Int.MIN_VALUE..Int.MAX_VALUE)
}.toInt()
private fun AttendanceCloudDocument.deleted(): Boolean = fields.getValue("isDeleted").jsonObject
    .getValue("booleanValue").jsonPrimitive.also { require(!it.isString) }.boolean
private fun AttendanceCloudDocument.requireTerms() {
    require(int("totalClasses") > 0)
    validityWeeks()
    require(int("price") >= 0 && int("maxVisitsPerAttendance") in 1..int("totalClasses"))
}
private fun requireCloudId(value: String) {
    require(value.isNotBlank() && '/' !in value && value != "." && value != "..")
}
