package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.data.sync.UploadAcknowledgement
import com.theunderseer.simplio.feature.attendance.data.sync.UploadSnapshot
import com.theunderseer.simplio.feature.attendance.data.sync.UploadTarget
import kotlin.test.assertNotNull

internal val testUploadTarget = UploadTarget("test-project", "owner")
internal val testServerVersion = "2026-10-19T12:00:00.123456Z"

// Expected transport contract, deliberately independent of the production version constant.
internal const val TEST_UPLOAD_PROTOCOL_VERSION = 7

internal fun testUploadAcknowledgement(snapshot: UploadSnapshot) = UploadAcknowledgement(
    documentUpdateTimes = List(snapshot.documents.size) { testServerVersion },
    headUpdateTime = testServerVersion,
)

internal suspend fun RoomUploadStore.acknowledgeCatalog() {
    val batch = assertNotNull(prepare(testUploadTarget))
    acknowledge(batch, testUploadAcknowledgement(snapshot(batch)))
}
