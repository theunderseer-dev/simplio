package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.ios_attendance_entry
import com.theunderseer.simplio.feature.attendance.resources.ios_attendance_entry_with_people
import com.theunderseer.simplio.feature.attendance.resources.ios_back_to_attendance
import com.theunderseer.simplio.feature.attendance.resources.ios_chevron_back
import com.theunderseer.simplio.feature.attendance.resources.ios_expiration_date
import com.theunderseer.simplio.feature.attendance.resources.ios_first_attendance
import com.theunderseer.simplio.feature.attendance.resources.ios_no_attendance_recorded
import com.theunderseer.simplio.feature.attendance.resources.ios_note
import com.theunderseer.simplio.feature.attendance.resources.ios_note_placeholder
import com.theunderseer.simplio.feature.attendance.resources.ios_note_saving
import com.theunderseer.simplio.feature.attendance.resources.ios_purchase_date
import com.theunderseer.simplio.feature.attendance.resources.ios_student_details
import com.theunderseer.simplio.feature.attendance.resources.ios_student_unavailable
import com.theunderseer.simplio.feature.attendance.resources.package_name
import com.theunderseer.simplio.feature.attendance.resources.package_no_expiration
import com.theunderseer.simplio.feature.attendance.resources.roster_not_started
import com.theunderseer.simplio.feature.attendance.resources.roster_retry
import com.theunderseer.simplio.feature.attendance.resources.roster_visits_used
import com.theunderseer.simplio.shared.presentation.components.SimplioIconButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Suppress("FunctionNaming")
@Composable
internal fun IosStudentDetailsScreen(
    entry: RegisteredStudent?,
    onUpdateNote: (suspend (String) -> String?)?,
    onBack: () -> Unit,
) {
    val student = entry?.student
    val editor = student?.let { remember(it.id) { StudentNoteEditor(it.note) } }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    LaunchedEffect(editor, student?.note) {
        if (student != null) editor?.updateSavedNote(student.note)
    }

    fun saveNote(afterSave: (() -> Unit)? = null) {
        if (editor != null && onUpdateNote != null) {
            editor.save(scope, onUpdateNote, afterSave)
        } else {
            afterSave?.invoke()
        }
    }

    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .pointerInput(focus) { detectTapGestures { focus.clearFocus() } },
    ) {
        StudentDetailsHeader { saveNote(onBack) }
        HorizontalDivider()
        if (entry != null && editor != null) {
            StudentDetailsContent(entry, editor, editable = onUpdateNote != null, onSaveNote = { saveNote() })
        } else {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.ios_student_unavailable))
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun StudentDetailsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SimplioIconButton(
            painter = painterResource(Res.drawable.ios_chevron_back),
            contentDescription = stringResource(Res.string.ios_back_to_attendance),
            onClick = onBack,
        )
        Text(
            text = stringResource(Res.string.ios_student_details),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun StudentDetailsContent(
    entry: RegisteredStudent,
    editor: StudentNoteEditor,
    editable: Boolean,
    onSaveNote: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .imePadding()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(entry.student.fullName, style = MaterialTheme.typography.headlineSmall)
        CurrentPackageAttendance(entry.studentPackage)
        StudentPackageDetails(entry.studentPackage)
        StudentNoteField(editor, editable, onSaveNote)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CurrentPackageAttendance(purchase: StudentPackage) {
    val history = purchase.attendanceHistory
    val rows = remember(history) { history.visitsByDate.entries.sortedBy { it.key } }
    val used = history.usedClasses
    val total = purchase.packageType.totalClasses
    val counterDescription = stringResource(Res.string.roster_visits_used, used, total)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "$used/$total",
            modifier = Modifier.align(Alignment.End).semantics { contentDescription = counterDescription },
            style = MaterialTheme.typography.titleMedium,
        )
        Column(
            Modifier.fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
        ) {
            if (rows.isEmpty()) {
                Text(
                    text = stringResource(Res.string.ios_no_attendance_recorded),
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            rows.forEachIndexed { index, (date, count) ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                val people = count.takeIf { purchase.packageType.maxVisitsPerAttendance > 1 }
                StudentAttendanceTableRow(index + 1, date, people)
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun StudentAttendanceTableRow(number: Int, date: LocalDate, people: Int?) {
    val day = date.toDisplayDate()
    val description = if (people == null) {
        stringResource(Res.string.ios_attendance_entry, number, day)
    } else {
        stringResource(Res.string.ios_attendance_entry_with_people, number, day, people)
    }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number.", color = secondary, style = MaterialTheme.typography.bodyMedium)
        Text(day, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (people != null) Text(people.toString(), color = secondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun StudentPackageDetails(purchase: StudentPackage) {
    val expiration = if (purchase.packageType.validityWeeks == null) {
        stringResource(Res.string.package_no_expiration)
    } else {
        purchase.expiresOn?.toDetailsDate() ?: "—"
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StudentDetailValue(stringResource(Res.string.ios_purchase_date), purchase.purchasedOn.toDetailsDate())
        StudentDetailValue(
            stringResource(Res.string.ios_first_attendance),
            purchase.startedOn?.toDetailsDate() ?: stringResource(Res.string.roster_not_started),
        )
        StudentDetailValue(stringResource(Res.string.ios_expiration_date), expiration)
        StudentDetailValue(stringResource(Res.string.package_name), purchase.packageType.name)
    }
}

private fun LocalDate.toDetailsDate(): String = "${toDisplayDate()}, $year"

@Suppress("FunctionNaming")
@Composable
private fun StudentDetailValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun StudentNoteField(editor: StudentNoteEditor, editable: Boolean, onSave: () -> Unit) {
    val focus = LocalFocusManager.current
    var wasFocused by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(
            value = editor.draft,
            onValueChange = editor::edit,
            modifier = Modifier.fillMaxWidth().testTag("ios-student-details-note").onFocusChanged { state ->
                val lostFocus = wasFocused && !state.hasFocus
                wasFocused = state.hasFocus
                if (lostFocus) onSave()
            },
            label = { Text(stringResource(Res.string.ios_note)) },
            placeholder = { Text(stringResource(Res.string.ios_note_placeholder)) },
            isError = editor.errorMessage != null,
            readOnly = !editable || editor.isSaving,
            minLines = 3,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        )
        if (editor.isSaving) {
            Text(
                text = stringResource(Res.string.ios_note_saving),
                modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        editor.errorMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onSave) { Text(stringResource(Res.string.roster_retry)) }
        }
    }
}

private class StudentNoteEditor(initialNote: String) {
    var draft by mutableStateOf(initialNote)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var savedNote = initialNote
    private var navigateAfterSave: (() -> Unit)? = null

    fun edit(text: String) {
        draft = text
        errorMessage = null
    }

    fun updateSavedNote(note: String) {
        if (!isSaving && draft == savedNote) draft = note
        savedNote = note
    }

    fun save(scope: CoroutineScope, update: suspend (String) -> String?, onSaved: (() -> Unit)?) {
        if (onSaved != null) navigateAfterSave = onSaved
        if (isSaving) return
        val note = draft.trim()
        if (note == savedNote.trim()) {
            finish()
            return
        }
        isSaving = true
        errorMessage = null
        scope.launch {
            try {
                errorMessage = update(note)
                if (errorMessage == null) {
                    savedNote = note
                    draft = note
                    finish()
                }
            } finally {
                navigateAfterSave = null
                isSaving = false
            }
        }
    }

    private fun finish() {
        val navigate = navigateAfterSave
        navigateAfterSave = null
        navigate?.invoke()
    }
}
