package com.theunderseer.simplio

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.theunderseer.simplio.attendance.createAttendanceComponent
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceScreen
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.awt.datatransfer.StringSelection
import kotlin.time.Clock

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val component =
        createAttendanceComponent()

    component.use { component ->
        runBlocking {
            component.initialize()
        }

        val attendanceViewModel =
            AttendanceViewModel(
                getAttendanceUseCase = component.getAttendance,
                getPackageTypesUseCase = component.getPackageTypes,
                registerStudentUseCase = component.registerStudent,
                deleteStudentUseCase = component.deleteStudent,
            )

        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "Simplio",
            ) {
                val state by attendanceViewModel.uiState.collectAsState()

                val clipboard = LocalClipboard.current
                val coroutineScope = rememberCoroutineScope()

                AttendanceScreen(
                    state = state,
                    currentDate =
                    Clock.System
                        .now()
                        .toLocalDateTime(
                            TimeZone.currentSystemDefault(),
                        ).date,
                    onRegisterStudent = attendanceViewModel::registerStudent,
                    onDeleteStudent = attendanceViewModel::deleteStudent,
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
}
