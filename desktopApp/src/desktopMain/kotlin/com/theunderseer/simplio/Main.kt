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
import com.theunderseer.simplio.attendance.time.attendanceDates
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceScreen
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.datatransfer.StringSelection
import kotlin.time.Clock

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val component = createAttendanceComponent()

    component.use { component ->
        runBlocking {
            component.initialize()
        }

        val attendanceViewModel =
            AttendanceViewModel(
                getAttendanceUseCase = component.getAttendance,
                getPackageTypesUseCase = component.getPackageTypes,
                registerStudentUseCase = component.registerStudent,
                markStudentPresentUseCase = component.markStudentPresent,
                removeStudentAttendanceUseCase = component.removeStudentAttendance,
                deleteStudentUseCase = component.deleteStudent,
                currentDates = attendanceDates(clock = Clock.System),
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
                    onMarkStudentPresent = attendanceViewModel::markStudentPresent,
                    onRemoveStudentAttendance = attendanceViewModel::removeStudentAttendance,
                    onClearMarkStudentPresentState = attendanceViewModel::clearMarkStudentPresentState,
                )
            }
        }
    }
}
