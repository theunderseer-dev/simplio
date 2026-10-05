package com.theunderseer.simplio

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.theunderseer.simplio.attendance.createAttendanceComponent
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceScreen
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

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
                currentDate = {
                    Clock.System
                        .now()
                        .toLocalDateTime(
                            TimeZone.currentSystemDefault(),
                        ).date
                },
            )

        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "Simplio",
            ) {
                val state by attendanceViewModel.uiState.collectAsState()

                AttendanceScreen(
                    state = state,
                    onRegisterStudent = attendanceViewModel::registerStudent,
                    onDeleteStudent = {},
                )
            }
        }
    }
}
