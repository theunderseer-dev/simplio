package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.local.sync.SyncEntityType
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudDocument
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudHead
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudSnapshot
import com.theunderseer.simplio.feature.attendance.data.sync.AttendancePullRemote
import com.theunderseer.simplio.feature.attendance.data.sync.UploadPreparationFailure
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.data.sync.UploadTarget
import com.theunderseer.simplio.shared.firebase.FirestoreFailure
import com.theunderseer.simplio.shared.firebase.FirestoreFailureKind
import com.theunderseer.simplio.shared.firebase.FirestoreRestClient
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class FirestoreAttendancePullRemote(private val client: FirestoreRestClient) : AttendancePullRemote {
    private val target = UploadTarget(client.projectId, client.userId)

    override suspend fun readHead(): AttendanceCloudHead {
        val snapshot = client.readDocumentSnapshot(
            FirestoreUploadProtocol.HEAD_COLLECTION,
            FirestoreUploadProtocol.HEAD_ID,
        )
        snapshot.document?.let { head ->
            val protocol = head.fields["protocolVersion"]?.jsonObject?.get(
                "integerValue",
            )?.jsonPrimitive?.content?.toIntOrNull()
            if (protocol == null ||
                protocol !in 1..FirestoreUploadProtocol.CURRENT_VERSION ||
                head.fields["batchId"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content.isNullOrBlank()
            ) {
                throw FirestoreFailure(FirestoreFailureKind.INVALID_RESPONSE)
            }
        }
        return AttendanceCloudHead(target, snapshot.document?.updateTime, snapshot.readTime)
    }

    override suspend fun readSnapshot(head: AttendanceCloudHead): AttendanceCloudSnapshot {
        require(head.target == target)
        val documents = mutableListOf<AttendanceCloudDocument>()
        var bytes = 0L
        SyncEntityType.entries.forEach { type ->
            var token: String? = null
            val tokens = mutableSetOf<String>()
            val ids = mutableSetOf<String>()
            do {
                val page = client.queryDocumentsAt(FirestoreUploadProtocol.collection(type), head.readTime, token)
                page.documents.forEach { document ->
                    if (!ids.add(document.id)) throw FirestoreFailure(FirestoreFailureKind.INVALID_RESPONSE)
                    bytes += document.fields.toString().encodeToByteArray().size
                    if (documents.size >= 50_000 || bytes > 32L * 1024 * 1024) {
                        throw UploadPreparationFailure(UploadProblem.TOO_LARGE)
                    }
                    documents += AttendanceCloudDocument(type, document.id, document.fields, document.updateTime)
                }
                token = page.nextAfterId
                if (token != null && !tokens.add(checkNotNull(token))) {
                    throw FirestoreFailure(FirestoreFailureKind.INVALID_RESPONSE)
                }
            } while (token != null)
        }
        if (head.updateTime == null && documents.isNotEmpty()) {
            throw FirestoreFailure(FirestoreFailureKind.INVALID_RESPONSE)
        }
        return AttendanceCloudSnapshot(head, documents)
    }
}
