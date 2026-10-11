package com.theunderseer.simplio

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.theunderseer.simplio.database.prepareSimplioDatabasePath
import com.theunderseer.simplio.di.desktopAttendanceModule
import com.theunderseer.simplio.feature.attendance.data.local.seed.PackageTypeSeeder
import com.theunderseer.simplio.feature.attendance.data.local.seed.StudentGroupSeeder
import com.theunderseer.simplio.feature.attendance.di.attendanceModule
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val koinApplication =
        startKoin {
            modules(
                attendanceModule,
                desktopAttendanceModule(
                    databasePath = prepareSimplioDatabasePath(),
                ),
            )
        }

    val viewModelStoreOwner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }

    try {
        runBlocking {
            koinApplication.koin
                .get<PackageTypeSeeder>()
                .seedDefaults()

            koinApplication.koin
                .get<StudentGroupSeeder>()
                .seedDefaults()
        }

        application(
            exitProcessOnExit = false,
        ) {
            Window(
                onCloseRequest = ::exitApplication,
                title = "Simplio",
            ) {
                val clipboard = LocalClipboard.current
                val coroutineScope = rememberCoroutineScope()

                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides viewModelStoreOwner,
                ) {
                    AttendanceRoute(
                        onCopyAttendance = { text ->
                            coroutineScope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(
                                        StringSelection(text),
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }
    } finally {
        try {
            runBlocking(Dispatchers.Main.immediate) {
                viewModelStoreOwner.viewModelStore.clear()
            }
        } finally {
            stopKoin()
        }
    }
}
