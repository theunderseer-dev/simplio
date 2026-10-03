package com.theunderseer.simplio

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.theunderseer.simplio.attendance.createAttendanceComponent
import kotlinx.coroutines.runBlocking

fun main() {
    val component =
        createAttendanceComponent()

    component.use { component ->
        runBlocking {
            component.initialize()
        }

        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "Simplio",
            ) {
                // Features will be composed here.
            }
        }
    }
}
