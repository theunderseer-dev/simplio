package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.local.sync.SyncEntityType
import com.theunderseer.simplio.feature.attendance.data.sync.UploadSnapshot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object FirestoreUploadProtocol {
    const val HEAD_COLLECTION = "syncState"
    const val HEAD_ID = "attendance"
    const val RECEIPT_COLLECTION = "syncReceipts"

    fun collection(type: SyncEntityType): String = when (type) {
        SyncEntityType.STUDENT_GROUP -> "studentGroups"
        SyncEntityType.PACKAGE_TYPE -> "packageTypes"
        SyncEntityType.STUDENT -> "students"
        SyncEntityType.STUDENT_PACKAGE -> "studentPackages"
        SyncEntityType.ATTENDANCE -> "attendance"
    }

    fun string(value: String): JsonObject = buildJsonObject { put("stringValue", value) }
    fun integer(value: Number): JsonObject = buildJsonObject { put("integerValue", value.toString()) }
    fun boolean(value: Boolean): JsonObject = buildJsonObject { put("booleanValue", value) }

    fun receiptFields(snapshot: UploadSnapshot): JsonObject = buildJsonObject {
        put("protocolVersion", integer(1))
        put("batchId", string(snapshot.batchId))
        put(
            "mutationIds",
            buildJsonObject {
                put(
                    "arrayValue",
                    buildJsonObject {
                        put("values", JsonArray(snapshot.documents.map { string(it.change.mutationId) }))
                    },
                )
            },
        )
    }

    fun commitBody(snapshot: UploadSnapshot): String {
        val target = snapshot.target
        val prefix = "projects/${target.projectId}/databases/(default)/documents/users/${target.userId}"
        fun write(path: String, fields: JsonObject, version: String?): JsonObject = buildJsonObject {
            put(
                "update",
                buildJsonObject {
                    put("name", "$prefix/$path")
                    put("fields", fields)
                },
            )
            put(
                "currentDocument",
                buildJsonObject {
                    if (version == null) put("exists", false) else put("updateTime", version)
                },
            )
        }
        val writes = snapshot.documents.map { document ->
            write(
                "${collection(document.change.entityType)}/${document.change.cloudId}",
                document.fields,
                document.change.serverUpdateTime,
            )
        } + write(
            "$HEAD_COLLECTION/$HEAD_ID",
            buildJsonObject {
                put("protocolVersion", integer(1))
                put("batchId", string(snapshot.batchId))
            },
            snapshot.headUpdateTime,
        ) + write("$RECEIPT_COLLECTION/${snapshot.batchId}", receiptFields(snapshot), null)
        return buildJsonObject { put("writes", JsonArray(writes)) }.toString()
    }
}
