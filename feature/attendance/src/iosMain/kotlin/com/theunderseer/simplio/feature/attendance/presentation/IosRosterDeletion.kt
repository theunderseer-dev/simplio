package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class IosRosterDeletion(
    private val scope: CoroutineScope,
    private val deleteStudent: suspend (Int) -> String?,
) {
    var revealedStudentId by mutableStateOf<Int?>(null)
        private set
    var selectedStudent by mutableStateOf<Student?>(null)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun setRevealed(studentId: Int, revealed: Boolean): Boolean {
        if (revealed && selectedStudent != null) return false
        if (revealed) revealedStudentId = studentId else closeReveal(studentId)
        return true
    }

    fun closeReveal(studentId: Int? = revealedStudentId) {
        if (revealedStudentId == studentId) revealedStudentId = null
    }

    fun request(student: Student) {
        if (selectedStudent != null) return
        closeReveal()
        selectedStudent = student
        errorMessage = null
    }

    fun confirm() {
        val student = selectedStudent ?: return
        if (isSaving) return
        isSaving = true
        errorMessage = null
        scope.launch {
            try {
                errorMessage = deleteStudent(student.id)
                if (errorMessage == null) selectedStudent = null
            } finally {
                isSaving = false
            }
        }
    }

    fun dismiss() {
        if (!isSaving) selectedStudent = null
    }
}

@Suppress("FunctionNaming") // Compose UI functions use PascalCase.
@Composable
internal fun IosRosterDeletionDialog(deletion: IosRosterDeletion) {
    val student = deletion.selectedStudent ?: return
    StudentDeletionDialog(
        studentName = student.fullName,
        isSaving = deletion.isSaving,
        errorMessage = deletion.errorMessage,
        onConfirm = deletion::confirm,
        onDismiss = deletion::dismiss,
    )
}

internal fun Modifier.closeRosterRevealOnTap(deletion: IosRosterDeletion?): Modifier {
    if (deletion == null) return this
    return pointerInput(deletion) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            do {
                // Inspect the final pass so a child button receives its tap before the row closes.
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.singleOrNull()?.takeIf { it.id == down.id } ?: return@awaitEachGesture
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    return@awaitEachGesture
                }
                if (change.changedToUpIgnoreConsumed()) deletion.closeReveal()
            } while (change.pressed)
        }
    }
}
