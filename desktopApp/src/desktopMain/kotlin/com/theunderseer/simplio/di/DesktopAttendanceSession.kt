package com.theunderseer.simplio.di

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.seed.PackageTypeSeeder
import com.theunderseer.simplio.feature.attendance.data.local.seed.StudentGroupSeeder
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomConflictStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.data.remote.FirestoreAttendancePullRemote
import com.theunderseer.simplio.feature.attendance.data.remote.FirestoreAttendanceUploadRemote
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceSyncCoordinator
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceUploadCoordinator
import com.theunderseer.simplio.feature.attendance.data.sync.UploadTarget
import com.theunderseer.simplio.feature.attendance.di.attendanceModule
import com.theunderseer.simplio.shared.firebase.FirestoreRestClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication

internal class DesktopAttendanceSession(databasePath: String, uploadClient: FirestoreRestClient? = null) :
    ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    val application: KoinApplication = koinApplication {
        modules(attendanceModule(), desktopAttendanceModule(databasePath))
    }

    val sync = uploadClient?.let { client ->
        val database = application.koin.get<AttendanceDatabase>()
        val target = UploadTarget(client.projectId, client.userId)
        AttendanceSyncCoordinator(
            store = RoomPullStore(database, target),
            conflicts = RoomConflictStore(database, target),
            remote = FirestoreAttendancePullRemote(client),
            uploader = AttendanceUploadCoordinator(
                store = RoomUploadStore(application.koin.get<AttendanceDatabase>()),
                remote = FirestoreAttendanceUploadRemote(client),
                target = target,
            ),
        )
    }

    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            application.koin.get<PackageTypeSeeder>().seedDefaults()
            application.koin.get<StudentGroupSeeder>().seedDefaults()
        }
    }

    suspend fun close() {
        withContext(NonCancellable) {
            try {
                withContext(Dispatchers.Main.immediate) {
                    viewModelStore.clear()
                }
            } finally {
                withContext(Dispatchers.IO) {
                    application.close()
                }
            }
        }
    }
}
