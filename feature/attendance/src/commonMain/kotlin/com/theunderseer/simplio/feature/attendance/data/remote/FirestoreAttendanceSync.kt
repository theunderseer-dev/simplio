package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomConflictStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceSyncCoordinator
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceUploadCoordinator
import com.theunderseer.simplio.feature.attendance.data.sync.UploadTarget
import com.theunderseer.simplio.shared.firebase.FirestoreRestClient

fun createFirestoreAttendanceSync(
    database: AttendanceDatabase,
    client: FirestoreRestClient,
): AttendanceSyncCoordinator {
    val target = UploadTarget(client.projectId, client.userId)
    return AttendanceSyncCoordinator(
        store = RoomPullStore(database, target),
        conflicts = RoomConflictStore(database, target),
        remote = FirestoreAttendancePullRemote(client),
        uploader = AttendanceUploadCoordinator(
            RoomUploadStore(database),
            FirestoreAttendanceUploadRemote(client),
            target,
        ),
    )
}
