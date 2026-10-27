package com.theunderseer.simplio.feature.attendance.fixtures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.theunderseer.simplio.feature.attendance.presentation.ClassAttendanceCallbacks
import com.theunderseer.simplio.feature.attendance.presentation.ClassAttendanceDialog
import com.theunderseer.simplio.feature.attendance.presentation.ClassAttendanceUiState

internal fun testClassAttendanceCallbacks(
    open: (String) -> Unit = {},
    close: () -> Unit = {},
    save: (Map<Int, Int>, Boolean) -> Unit = { _, _ -> },
    select: (Int, Int) -> Unit = { _, _ -> },
    addStudent: () -> Unit = {},
    renewStudent: (Int) -> Unit = {},
): ClassAttendanceCallbacks = ClassAttendanceCallbacks(
    open, close, save, select, addStudent, renewStudent, {}, {}, { _, _, _, _, _, _ -> }, { _, _, _, _ -> },
)

@Composable
internal fun ClassAttendanceDialogForTest(
    initial: ClassAttendanceUiState.Content,
    onClose: () -> Unit,
    onRetry: (String) -> Unit,
    onSave: (Map<Int, Int>, Boolean) -> Unit,
) {
    var content by remember { mutableStateOf(initial) }
    ClassAttendanceDialog(
        content,
        onClose,
        onRetry,
        onSave,
        { id, count -> content = content.withVisit(id, count) },
        {},
        {},
    )
}
