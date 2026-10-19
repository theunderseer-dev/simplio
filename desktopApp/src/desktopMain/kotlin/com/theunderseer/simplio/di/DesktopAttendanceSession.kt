package com.theunderseer.simplio.di

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.theunderseer.simplio.feature.attendance.data.local.seed.PackageTypeSeeder
import com.theunderseer.simplio.feature.attendance.data.local.seed.StudentGroupSeeder
import com.theunderseer.simplio.feature.attendance.di.attendanceModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication

internal class DesktopAttendanceSession(databasePath: String) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    val application: KoinApplication = koinApplication {
        modules(attendanceModule(), desktopAttendanceModule(databasePath))
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
