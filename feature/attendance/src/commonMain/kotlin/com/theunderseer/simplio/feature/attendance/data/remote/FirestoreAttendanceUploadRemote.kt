package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceUploadRemote
import com.theunderseer.simplio.feature.attendance.data.sync.UploadAcknowledgement
import com.theunderseer.simplio.feature.attendance.data.sync.UploadSnapshot
import com.theunderseer.simplio.shared.firebase.FirestoreFailure
import com.theunderseer.simplio.shared.firebase.FirestoreFailureKind
import com.theunderseer.simplio.shared.firebase.FirestoreRestClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class FirestoreAttendanceUploadRemote(private val client: FirestoreRestClient) : AttendanceUploadRemote {
    override suspend fun sendOrRecover(snapshot: UploadSnapshot, commitBody: String): UploadAcknowledgement {
        require(snapshot.target.projectId == client.projectId && snapshot.target.userId == client.userId)
        require(
            Json.parseToJsonElement(commitBody) ==
                Json.parseToJsonElement(FirestoreUploadProtocol.commitBody(snapshot)),
        )
        recover(snapshot)?.let { return it }
        try {
            val versions = client.commit(commitBody)
            return UploadAcknowledgement(versions.take(snapshot.documents.size), versions[snapshot.documents.size])
        } catch (error: FirestoreFailure) {
            if (error.kind == FirestoreFailureKind.CONFLICT) recover(snapshot)?.let { return it }
            throw error
        }
    }

    private suspend fun recover(snapshot: UploadSnapshot): UploadAcknowledgement? {
        val receipt = client.getDocument(FirestoreUploadProtocol.RECEIPT_COLLECTION, snapshot.batchId) ?: return null
        if (receipt.fields != FirestoreUploadProtocol.receiptFields(snapshot)) {
            throw FirestoreFailure(FirestoreFailureKind.INVALID_RESPONSE)
        }
        val head = client.getDocument(FirestoreUploadProtocol.HEAD_COLLECTION, FirestoreUploadProtocol.HEAD_ID)
        if (head == null ||
            head.fields["batchId"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content != snapshot.batchId
        ) {
            throw FirestoreFailure(FirestoreFailureKind.CONFLICT)
        }
        val versions = snapshot.documents.map { document ->
            val remote = client.getDocument(
                FirestoreUploadProtocol.collection(document.change.entityType),
                document.change.cloudId,
            )
            if (remote == null || remote.fields != document.fields) {
                throw FirestoreFailure(FirestoreFailureKind.CONFLICT)
            }
            remote.updateTime
        }
        return UploadAcknowledgement(versions, head.updateTime)
    }
}
