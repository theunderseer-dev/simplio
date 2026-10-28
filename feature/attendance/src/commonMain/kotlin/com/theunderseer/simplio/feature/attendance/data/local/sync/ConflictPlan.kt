package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.remote.FirestoreUploadProtocol
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudDocument
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudSnapshot
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictChoice
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictLocalSnapshot
import com.theunderseer.simplio.feature.attendance.data.sync.SyncConflictItem
import com.theunderseer.simplio.feature.attendance.domain.model.displayGroupName
import com.theunderseer.simplio.feature.attendance.domain.model.displayPackageName
import com.theunderseer.simplio.feature.attendance.domain.model.isValidGroupName
import com.theunderseer.simplio.feature.attendance.domain.model.isValidPackageName
import com.theunderseer.simplio.feature.attendance.domain.model.normalizedGroupName
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal typealias CloudKey = Pair<SyncEntityType, String>
internal val AttendanceCloudDocument.key: CloudKey get() = type to cloudId
internal val AttendanceCloudDocument.isDeleted: Boolean get() = fields.getValue("isDeleted")
    .jsonObject.getValue("booleanValue").jsonPrimitive.content == "true"
internal fun AttendanceCloudDocument.string(name: String): String = fields.getValue(name)
    .jsonObject.getValue("stringValue").jsonPrimitive.content
internal fun AttendanceCloudDocument.integer(name: String): Long = fields.getValue(name)
    .jsonObject.getValue("integerValue").jsonPrimitive.content.toLong()
internal fun AttendanceCloudDocument.content(): JsonObject = JsonObject(fields.filterKeys { it != "lastMutationId" })

/** Whole student aggregates prevent mixing attendance with a concurrently renewed package. */
internal class ConflictPlan(val local: ConflictLocalSnapshot, val cloud: AttendanceCloudSnapshot) {
    private val device = withImplicitDefaultSchedule(local.documents, cloud.documents).associateBy { it.key }
    private val server = withImplicitDefaultSchedule(cloud.documents, local.documents).associateBy { it.key }
    private val known = local.records.associateBy { it.entityType to it.cloudId }
    private val sessionGroups = sessionConflictGroups(device.values.toList(), server.values.toList(), local)
    private val deviceGroups = owners(device).mapValues { (_, group) -> sessionGroups[group] ?: group }
    private val serverGroups = owners(server).mapValues { (_, group) -> sessionGroups[group] ?: group }
    private val originalGroups = local.pending.map { checkNotNull(deviceGroups[it.entityType to it.cloudId]) }.toSet()

    // A new local student/package may depend on a catalog entry removed by another device.
    private val removedParents = device.values.filter { !it.isDeleted && deviceGroups[it.key] in originalGroups }
        .flatMap { document ->
            val parents = when (document.type) {
                SyncEntityType.STUDENT -> listOf(SyncEntityType.STUDENT_GROUP to document.string("groupId"))
                SyncEntityType.STUDENT_PACKAGE -> listOf(
                    SyncEntityType.PACKAGE_TYPE to document.string("packageTypeId"),
                )
                SyncEntityType.CLASS_SESSION -> listOf(SyncEntityType.CLASS_SCHEDULE to document.string("scheduleId"))
                SyncEntityType.STUDENT_GROUP -> document.scheduleIds().orEmpty().map {
                    SyncEntityType.CLASS_SCHEDULE to
                        it
                }
                else -> emptyList()
            }
            parents.filter { server[it]?.isDeleted == true }.map { document.key to it }
        }
    private val forcedGroups = removedParents.flatMap { (child, parent) ->
        listOf(checkNotNull(deviceGroups[child]), checkNotNull(deviceGroups[parent]))
    }.toSet() + device.values.filter { document ->
        document.isDeleted &&
            deviceGroups[document.key] in originalGroups &&
            server.values.any { remote ->
                !remote.isDeleted &&
                    when (document.type) {
                        SyncEntityType.STUDENT_GROUP ->
                            remote.type == SyncEntityType.STUDENT &&
                                remote.string("groupId") == document.cloudId
                        SyncEntityType.PACKAGE_TYPE ->
                            remote.type == SyncEntityType.STUDENT_PACKAGE &&
                                remote.string("packageTypeId") == document.cloudId
                        SyncEntityType.CLASS_SCHEDULE ->
                            remote.type == SyncEntityType.STUDENT_GROUP &&
                                document.cloudId in remote.scheduleIds().orEmpty()
                        else -> false
                    }
            }
    }.map { checkNotNull(deviceGroups[it.key]) }
    val pendingGroups = originalGroups + forcedGroups
    private val changedGroups = cloud.documents.filter { known[it.key]?.serverUpdateTime != it.updateTime }
        .map { checkNotNull(serverGroups[it.key]) }.toSet()
    private val nameCollisions = device.values.filter {
        it.type in setOf(SyncEntityType.STUDENT_GROUP, SyncEntityType.PACKAGE_TYPE) &&
            !it.isDeleted &&
            deviceGroups[it.key] in originalGroups
    }.mapNotNull { localGroup ->
        val remote = server.values.firstOrNull {
            it.type == localGroup.type &&
                !it.isDeleted &&
                it.key != localGroup.key &&
                normalizedGroupName(it.string("name")) == normalizedGroupName(localGroup.string("name"))
        }
        remote?.let { checkNotNull(deviceGroups[localGroup.key]) to it }
    }.toMap()
    val conflictingKeys = pendingGroups.filter { group ->
        group in nameCollisions ||
            group in forcedGroups ||
            group in changedGroups &&
            contentFor(device, deviceGroups, group) != contentFor(server, serverGroups, group)
    }.toSet()

    fun groupFor(key: CloudKey): String {
        val deviceGroup = deviceGroups[key]
        val serverGroup = serverGroups[key]
        return when {
            deviceGroup?.startsWith("student:") == true -> deviceGroup
            serverGroup?.startsWith("student:") == true -> serverGroup
            else -> deviceGroup ?: checkNotNull(serverGroup)
        }
    }

    fun items(): List<SyncConflictItem> = conflictingKeys.sorted().map { group ->
        val deviceRows = device.values.filter { deviceGroups[it.key] == group }
        val cloudRows = nameCollisions[group]?.let { listOf(it) }
            ?: server.values.filter { serverGroups[it.key] == group }
        SyncConflictItem(
            group,
            label(deviceRows, cloudRows),
            conflictDetails(deviceRows, device.values.toList()),
            conflictDetails(cloudRows, server.values.toList()),
            deviceChoiceAllowed = canChoose(group, ConflictChoice.DEVICE),
            cloudChoiceAllowed = canChoose(group, ConflictChoice.CLOUD),
            deviceClasses = classSessionSummaries(deviceRows, device.values.toList()),
            cloudClasses = classSessionSummaries(cloudRows, server.values.toList()),
            requiresGroupRename = group in nameCollisions && renameDocument(group).type == SyncEntityType.STUDENT_GROUP,
            requiresPackageRename = group in nameCollisions &&
                renameDocument(
                    group,
                ).type == SyncEntityType.PACKAGE_TYPE,
            unavailablePackageNames = if (group in nameCollisions) {
                (device.values + server.values)
                    .filter { it.type == SyncEntityType.PACKAGE_TYPE && !it.isDeleted }.map { it.string("name") }
            } else {
                emptyList()
            },
            unavailableGroupNames = if (group in nameCollisions) {
                (device.values + server.values)
                    .filter { it.type == SyncEntityType.STUDENT_GROUP && !it.isDeleted }.map { it.string("name") }
            } else {
                emptyList()
            },
        )
    }

    fun merge(
        choices: Map<String, ConflictChoice>,
        catalogRenames: Map<String, String> = emptyMap(),
        retirementAt: kotlin.time.Instant,
    ): AttendanceCloudSnapshot {
        require(catalogRenames.keys == nameCollisions.keys)
        catalogRenames.forEach { (group, name) ->
            val type = renameDocument(group).type
            require(choices[group] == ConflictChoice.DEVICE)
            require(if (type == SyncEntityType.PACKAGE_TYPE) isValidPackageName(name) else isValidGroupName(name))
        }
        require(choices.keys == conflictingKeys)
        require(choices.all { (group, choice) -> canChoose(group, choice) })
        val merged = server.toMutableMap()
        pendingGroups.forEach { group ->
            if (choices[group] == ConflictChoice.CLOUD) return@forEach
            // Choosing a student version includes every purchased package and attendance in that version.
            // Remote-only children become tombstones, rather than surviving an obsolete package selection.
            server.values.filter { serverGroups[it.key] == group && deviceGroups[it.key] != group }.forEach {
                merged[it.key] = it.copy(fields = tombstoneFields())
            }
            device.values.filter { deviceGroups[it.key] == group }.forEach { document ->
                val renamed = catalogRenames[group]?.takeIf { document.key == renameDocument(group).key }
                merged[document.key] = if (renamed == null) {
                    document
                } else {
                    document.copy(
                        fields = buildJsonObject {
                            document.fields.forEach { (key, value) -> put(key, value) }
                            val name = if (document.type ==
                                SyncEntityType.PACKAGE_TYPE
                            ) {
                                displayPackageName(renamed)
                            } else {
                                displayGroupName(renamed)
                            }
                            put("name", FirestoreUploadProtocol.string(name))
                        },
                    )
                }
            }
        }
        catalogRenames.keys.forEach { group ->
            val localGroup = renameDocument(group)
            val desired = checkNotNull(merged[localGroup.key])
            require(
                merged.values.none {
                    it.key != desired.key &&
                        it.type == desired.type &&
                        !it.isDeleted &&
                        normalizedGroupName(it.string("name")) == normalizedGroupName(desired.string("name"))
                },
            )
        }
        // Independent offline deletions can jointly remove the last groups although neither device saw itself as last.
        // The series stops when the complete account graph loses its final group during reconciliation.
        val removed = merged.values.filter {
            it.type == SyncEntityType.STUDENT_GROUP && it.isDeleted
        }.map { it.cloudId }.toSet()
        val before = (device.values + server.values).filter { it.type == SyncEntityType.STUDENT_GROUP && !it.isDeleted }
        val removedScheduleIds = before.filter { it.cloudId in removed }
            .flatMap { it.scheduleIds() ?: listOf(DefaultClassSchedule.ID) }.toSet()
        val activeScheduleIds = merged.values.filter { it.type == SyncEntityType.STUDENT_GROUP && !it.isDeleted }
            .flatMap { it.scheduleIds() ?: listOf(DefaultClassSchedule.ID) }.toSet()
        merged.values.toList().filter {
            it.type == SyncEntityType.CLASS_SCHEDULE &&
                !it.isDeleted &&
                it.cloudId in removedScheduleIds &&
                it.cloudId !in activeScheduleIds &&
                "archivedAtEpochMillis" !in it.fields
        }.forEach { schedule ->
            merged[schedule.key] = schedule.copy(
                fields = buildJsonObject {
                    schedule.fields.forEach { (key, value) -> put(key, value) }
                    put("archivedAtEpochMillis", FirestoreUploadProtocol.integer(retirementAt.toEpochMilliseconds()))
                },
            )
        }
        return cloud.copy(documents = merged.values.toList())
    }

    private fun canChoose(group: String, choice: ConflictChoice): Boolean {
        val chosen = if (choice == ConflictChoice.DEVICE) device else server
        val other = if (choice == ConflictChoice.DEVICE) server else device
        val owners = if (choice == ConflictChoice.DEVICE) deviceGroups else serverGroups
        val otherOwners = if (choice == ConflictChoice.DEVICE) serverGroups else deviceGroups
        val rows = chosen.values.filter { owners[it.key] == group }
        val removesGroups = rows.any {
            it.type == SyncEntityType.STUDENT_GROUP &&
                it.isDeleted ||
                it.type == SyncEntityType.CLASS_SCHEDULE &&
                !it.isDeleted &&
                "archivedAtEpochMillis" in it.fields
        }
        if (!removesGroups) return true
        // Explicit student tombstones remain valid; group deletion alone must not delete newly added students.
        return other.values.none {
            otherOwners[it.key] == group && it.type == SyncEntityType.STUDENT && !it.isDeleted && it.key !in chosen
        }
    }

    private fun renameDocument(group: String): AttendanceCloudDocument {
        val collision = checkNotNull(nameCollisions[group])
        return device.values.single {
            deviceGroups[it.key] == group &&
                it.type == collision.type &&
                !it.isDeleted &&
                normalizedGroupName(it.string("name")) == normalizedGroupName(collision.string("name"))
        }
    }

    private fun owners(documents: Map<CloudKey, AttendanceCloudDocument>): Map<CloudKey, String> {
        val students = local.records.filter { it.entityType == SyncEntityType.STUDENT }
            .associate { it.localId to it.cloudId }
        fun fallback(key: CloudKey): String? {
            val aggregate = known[key]?.aggregateKey ?: return null
            return if (aggregate.startsWith("STUDENT:")) {
                students[aggregate.substringAfter(':')]?.let { "student:$it" }
            } else {
                aggregate.takeIf { it == "legacy-deletions" }
            }
        }
        val packages = documents.values.filter { it.type == SyncEntityType.STUDENT_PACKAGE }.associate {
            it.cloudId to if (!it.isDeleted) "student:${it.string("studentId")}" else fallback(it.key)
        }
        return documents.values.associate { document ->
            val group = when (document.type) {
                SyncEntityType.CLASS_SCHEDULE -> "schedule:${document.cloudId}"
                SyncEntityType.CLASS_SESSION -> "class:${document.cloudId}"
                SyncEntityType.STUDENT_GROUP -> "group:${document.cloudId}"
                SyncEntityType.PACKAGE_TYPE -> "plan:${document.cloudId}"
                SyncEntityType.STUDENT -> "student:${document.cloudId}"
                SyncEntityType.STUDENT_PACKAGE -> packages[document.cloudId]
                SyncEntityType.ATTENDANCE -> {
                    val parent = if (document.isDeleted) {
                        document.cloudId.substringBeforeLast('_')
                    } else {
                        document.string("packageId")
                    }
                    packages[parent] ?: fallback(document.key)
                }
            } ?: fallback(document.key) ?: "unassigned:${document.type}:${document.cloudId}"
            document.key to group
        }
    }

    private fun contentFor(
        rows: Map<CloudKey, AttendanceCloudDocument>,
        owners: Map<CloudKey, String>,
        group: String,
    ) = rows.filterKeys { owners[it] == group }.mapValues { it.value.content() }

    private fun label(deviceRows: List<AttendanceCloudDocument>, cloudRows: List<AttendanceCloudDocument>): String {
        val rows = deviceRows + cloudRows
        if (rows.any {
                it.type == SyncEntityType.STUDENT_GROUP &&
                    it.isDeleted ||
                    it.type == SyncEntityType.CLASS_SCHEDULE &&
                    !it.isDeleted &&
                    "archivedAtEpochMillis" in it.fields
            }
        ) {
            rows.firstOrNull {
                it.type == SyncEntityType.STUDENT_GROUP && !it.isDeleted
            }?.let { return it.string("name") }
            rows.firstOrNull {
                it.type == SyncEntityType.CLASS_SCHEDULE && !it.isDeleted
            }?.let { return it.string("name") }
        }
        rows.firstOrNull { it.type == SyncEntityType.CLASS_SESSION && !it.isDeleted }?.let {
            return "${kotlinx.datetime.LocalDate.fromEpochDays(it.integer("dateEpochDay"))}"
        }
        rows.firstOrNull { it.type == SyncEntityType.STUDENT && !it.isDeleted }?.let { return it.string("fullName") }
        rows.firstOrNull {
            !it.isDeleted &&
                it.type in
                listOf(SyncEntityType.STUDENT_GROUP, SyncEntityType.PACKAGE_TYPE, SyncEntityType.CLASS_SCHEDULE)
        }
            ?.let { return it.string("name") }
        return "Deleted records"
    }
}

internal fun tombstoneFields(): JsonObject = buildJsonObject {
    put("schemaVersion", FirestoreUploadProtocol.integer(1))
    put("isDeleted", FirestoreUploadProtocol.boolean(true))
    put("lastMutationId", FirestoreUploadProtocol.string("review-selection"))
}

/** Legacy group documents imply the default series without an explicit cloud schedule document. */
private fun withImplicitDefaultSchedule(
    rows: List<AttendanceCloudDocument>,
    other: List<AttendanceCloudDocument>,
): List<AttendanceCloudDocument> {
    val id = DefaultClassSchedule.ID
    if (rows.any { it.type == SyncEntityType.CLASS_SCHEDULE && it.cloudId == id }) return rows
    val hasLiveGroup = rows.any {
        it.type == SyncEntityType.STUDENT_GROUP &&
            !it.isDeleted &&
            (it.scheduleIds() == null || id in it.scheduleIds().orEmpty())
    }
    if (!hasLiveGroup) return rows
    val template = other.firstOrNull { it.type == SyncEntityType.CLASS_SCHEDULE && it.cloudId == id && !it.isDeleted }
        ?: return rows
    return rows + template.copy(fields = JsonObject(template.fields.filterKeys { it != "archivedAtEpochMillis" }))
}
