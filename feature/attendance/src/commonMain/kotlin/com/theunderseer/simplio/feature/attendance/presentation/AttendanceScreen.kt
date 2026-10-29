package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.domain.schedule.latestClassDate
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.add_package
import com.theunderseer.simplio.feature.attendance.resources.attendance_add_group
import com.theunderseer.simplio.feature.attendance.resources.attendance_add_student
import com.theunderseer.simplio.feature.attendance.resources.ic_check
import com.theunderseer.simplio.feature.attendance.resources.ic_close
import com.theunderseer.simplio.feature.attendance.resources.ic_content_copy
import com.theunderseer.simplio.feature.attendance.resources.ic_refresh
import com.theunderseer.simplio.feature.attendance.resources.package_added
import com.theunderseer.simplio.feature.attendance.resources.package_no_expiration
import com.theunderseer.simplio.shared.presentation.components.SimplioActionTone
import com.theunderseer.simplio.shared.presentation.components.SimplioButton
import com.theunderseer.simplio.shared.presentation.components.SimplioIconButton
import com.theunderseer.simplio.shared.presentation.components.SimplioOutlinedButton
import com.theunderseer.simplio.shared.presentation.components.SimplioTextButton
import com.theunderseer.simplio.shared.presentation.components.collectIsKeyboardFocusedAsState
import com.theunderseer.simplio.shared.presentation.components.simplioClickable
import com.theunderseer.simplio.shared.presentation.components.simplioHoverSurface
import com.theunderseer.simplio.shared.presentation.theme.SimplioSpacing
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AttendanceScreen(
    state: AttendanceUiState,
    onRegisterStudent: (String, Int, PackageType, LocalDate, Int, Boolean, String) -> Unit,
    onDeleteStudent: (Int) -> Unit,
    onCopyAttendance: (String) -> Unit,
    onMarkStudentPresent: (Int, LocalDate, Int) -> Unit,
    onRemoveStudentAttendance: (Int, LocalDate) -> Unit,
    onClearMarkStudentPresentState: () -> Unit,
    onClearRemoveStudentAttendanceState: () -> Unit,
    onLoadNextClassHistoryPage: () -> Unit,
    onRetryClassHistory: () -> Unit,
    onUpdateStudentNote: (Int, String) -> Unit,
    onClearUpdateStudentNoteState: () -> Unit,
    onRenewStudentPackage: (Int, Int, PackageType, LocalDate, Int, Boolean, String) -> Unit,
    onClearRenewStudentPackageState: () -> Unit,
    onCreatePackageType: (CreatePackageTypeRequest) -> Unit,
    onClearCreatePackageTypeState: () -> Unit,
    classAttendanceCallbacks: ClassAttendanceCallbacks,
    onDeleteStudentGroup: (Int) -> Unit,
    onClearDeleteStudentGroupState: () -> Unit,
    onCreateStudentGroup: (CreateStudentGroupRequest) -> Unit = {},
    onClearCreateStudentGroupState: () -> Unit = {},
) {
    var showPackageDialog by remember { mutableStateOf(false) }
    var showManageGroups by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }

    var showRegistrationDialog by remember {
        mutableStateOf(false)
    }

    var deleteStudentDialog by remember {
        mutableStateOf<Int?>(null)
    }

    var attendanceDatePickerStudentId by remember {
        mutableStateOf<Int?>(null)
    }

    var attendanceRemovalRequest by remember {
        mutableStateOf<AttendanceRemovalRequest?>(null)
    }

    var renewalStudent by remember { mutableStateOf<RegisteredStudent?>(null) }

    var noteStudentId by remember { mutableStateOf<Int?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    when (state) {
        is AttendanceUiState.Content -> {
            ClassAttendanceWorkflow(
                state,
                classAttendanceCallbacks,
                onCreatePackageType,
                onClearCreatePackageTypeState,
            )
            if (showManageGroups) {
                StudentGroupManagementDialog(
                    state,
                    onDeleteStudentGroup,
                    onClearDeleteStudentGroupState,
                    {
                        onClearDeleteStudentGroupState()
                        showManageGroups = false
                    },
                )
            }
            val markState = state.markStudentPresentState
            val removeState = state.removeStudentAttendanceState
            val noteState = state.updateStudentNoteState
            val renewState = state.renewStudentPackageState
            val packageState = state.createPackageTypeState
            LaunchedEffect(packageState) {
                if (packageState is CreatePackageTypeUiState.Success) {
                    showPackageDialog = false
                    onClearCreatePackageTypeState()
                    snackbarHostState.showSnackbar(getString(Res.string.package_added, packageState.name))
                }
            }
            val groupState = state.createStudentGroupState
            LaunchedEffect(groupState) {
                if (groupState is CreateStudentGroupUiState.Success) {
                    showGroupDialog = false
                    snackbarHostState.showSnackbar("Group added: ${groupState.name}")
                    onClearCreateStudentGroupState()
                }
            }

            LaunchedEffect(renewState) {
                if (renewState is RenewStudentPackageUiState.Success &&
                    renewState.studentId == renewalStudent?.student?.id
                ) {
                    renewalStudent = null
                    onClearRenewStudentPackageState()
                }
            }

            LaunchedEffect(noteState) {
                if (noteState is UpdateStudentNoteUiState.Success && noteState.studentId == noteStudentId) {
                    noteStudentId = null
                    onClearUpdateStudentNoteState()
                }
            }

            LaunchedEffect(markState) {
                if (markState == MarkStudentPresentUiState.Success) {
                    attendanceDatePickerStudentId = null
                    onClearMarkStudentPresentState()
                }
            }

            LaunchedEffect(removeState) {
                if (removeState == RemoveStudentAttendanceUiState.Success) {
                    attendanceRemovalRequest = null
                    onClearRemoveStudentAttendanceState()
                }
            }

            Scaffold(
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                    )
                },
            ) { contentPadding ->
                AttendanceContent(
                    state = state,
                    onAddPackageClick = {
                        onClearCreatePackageTypeState()
                        showPackageDialog = true
                    },
                    onManageGroupsClick = {
                        onClearDeleteStudentGroupState()
                        showManageGroups = true
                    },
                    onAddGroupClick = {
                        onClearCreateStudentGroupState()
                        showGroupDialog = true
                    },
                    onAddStudentClick = {
                        showRegistrationDialog = true
                    },
                    onDeleteStudentClick = { studentId ->
                        deleteStudentDialog = studentId
                    },
                    onCopyAttendanceClick = { groupId ->
                        val groupAttendance = state.attendance.copy(
                            entries = state.attendance.entries.filter { entry ->
                                entry.student.groupId == groupId
                            },
                        )

                        onCopyAttendance(
                            formatAttendanceForSharing(
                                attendance = groupAttendance,
                                currentDate = state.groupCurrentDates[groupId] ?: state.currentDate,
                            ),
                        )

                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Copied to clipboard",
                            )
                        }
                    },
                    onCheckStudentClick = { row ->
                        if (row.isCheckEnabled) {
                            val student = row.registeredStudent.student

                            if (row.requiresRenewal) {
                                onClearRenewStudentPackageState()
                                renewalStudent = row.registeredStudent
                            } else {
                                when (row.checkState) {
                                    AttendanceCheckState.Pending -> {
                                        onClearMarkStudentPresentState()
                                        attendanceDatePickerStudentId = student.id
                                    }

                                    AttendanceCheckState.Checked -> {
                                        onClearRemoveStudentAttendanceState()

                                        attendanceRemovalRequest =
                                            AttendanceRemovalRequest(
                                                studentId = student.id,
                                                studentName = student.fullName,
                                                attendanceDate = row.effectiveClassDate ?: state.latestClassDate,
                                            )
                                    }
                                }
                            }
                        }
                    },
                    onRemoveStudentAttendanceClick = { row, date ->
                        val student = row.registeredStudent.student

                        onClearRemoveStudentAttendanceState()

                        attendanceRemovalRequest =
                            AttendanceRemovalRequest(
                                studentId = student.id,
                                studentName = student.fullName,
                                attendanceDate = date,
                            )
                    },
                    onLoadNextClassHistoryPage = onLoadNextClassHistoryPage,
                    onRetryClassHistory = onRetryClassHistory,
                    onTrackClassAttendance = classAttendanceCallbacks.open,
                    onEditNoteClick = { studentId ->
                        onClearUpdateStudentNoteState()
                        noteStudentId = studentId
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                )
            }

            if (showPackageDialog) {
                PackageTypeCreationDialog(
                    packageTypes = state.packageTypes,
                    state = packageState,
                    onConfirm = onCreatePackageType,
                    onClearError = onClearCreatePackageTypeState,
                    onDismiss = {
                        showPackageDialog = false
                        onClearCreatePackageTypeState()
                    },
                )
            }
            if (showGroupDialog) {
                StudentGroupCreationDialog(
                    groups = state.studentGroups,
                    schedules = state.classSchedules,
                    today = state.currentDate,
                    state = groupState,
                    onCreate = onCreateStudentGroup,
                    onDismiss = {
                        showGroupDialog = false
                        onClearCreateStudentGroupState()
                    },
                )
            }
            if (showRegistrationDialog) {
                StudentRegistrationDialog(
                    studentGroups = state.studentGroups,
                    groupSchedules = state.groupSchedules,
                    groupCurrentDates = state.groupCurrentDates,
                    packageTypes = state.packageTypes,
                    initialDate = state.currentDate,
                    onRegister = {
                            fullName,
                            groupId,
                            packageType,
                            registeredOn,
                            visitCount,
                            recordFirstAttendance,
                            note,
                        ->
                        onRegisterStudent(
                            fullName,
                            groupId,
                            packageType,
                            registeredOn,
                            visitCount,
                            recordFirstAttendance,
                            note,
                        )
                        showRegistrationDialog = false
                    },
                    onDismiss = {
                        showRegistrationDialog = false
                    },
                )
            }

            renewalStudent?.let { registeredStudent ->
                val studentId = registeredStudent.student.id
                StudentPackageRenewalDialog(
                    registeredStudent = registeredStudent,
                    studentGroups = state.studentGroups,
                    groupSchedules = state.groupSchedules,
                    packageTypes = state.packageTypes,
                    currentDate = state.groupCurrentDates[registeredStudent.student.groupId] ?: state.currentDate,
                    isSaving = renewState == RenewStudentPackageUiState.Saving(studentId),
                    errorMessage = (renewState as? RenewStudentPackageUiState.Error)
                        ?.takeIf { it.studentId == studentId }?.message,
                    onRenew = { packageType, date, visits, recordAttendance, note ->
                        onRenewStudentPackage(
                            studentId,
                            registeredStudent.studentPackage.id,
                            packageType,
                            date,
                            visits,
                            recordAttendance,
                            note,
                        )
                    },
                    onDismiss = {
                        renewalStudent = null
                        onClearRenewStudentPackageState()
                    },
                )
            }

            noteStudentId?.let { studentId ->
                state.attendance.entries.firstOrNull { it.student.id == studentId }?.student?.let { student ->
                    StudentNoteDialog(
                        student = student,
                        isSaving = noteState == UpdateStudentNoteUiState.Saving(studentId),
                        errorMessage = (noteState as? UpdateStudentNoteUiState.Error)
                            ?.takeIf { it.studentId == studentId }
                            ?.message,
                        onSave = { note -> onUpdateStudentNote(studentId, note) },
                        onDismiss = {
                            noteStudentId = null
                            onClearUpdateStudentNoteState()
                        },
                    )
                }
            }

            attendanceDatePickerStudentId?.let { studentId ->
                val registeredStudent = state.attendance.entries.first { it.student.id == studentId }
                val studentPackage = registeredStudent.studentPackage

                AttendanceDatePickerDialog(
                    initialDate = state.rows.first { it.registeredStudent.student.id == studentId }.effectiveClassDate
                        ?: state.latestClassDate,
                    currentDate =
                    state.groupCurrentDates[
                        state.attendance.entries.first {
                            it.student.id == studentId
                        }.student.groupId,
                    ]
                        ?: state.currentDate,
                    schedules = state.groupSchedules[
                        state.attendance.entries.first { it.student.id == studentId }.student.groupId,
                    ],
                    maxVisitsPerAttendance =
                    studentPackage.packageType.maxVisitsPerAttendance,
                    remainingClasses = studentPackage.remainingClasses,
                    purchasedOn = studentPackage.purchasedOn,
                    startedOn = studentPackage.startedOn,
                    expiresOn = studentPackage.expiresOn,
                    recordedAttendanceDates = registeredStudent.recordedAttendanceDates,
                    isSaving = markState == MarkStudentPresentUiState.Saving,
                    errorMessage =
                    (markState as? MarkStudentPresentUiState.Error)?.message,
                    onConfirm = { selectedDate, visitCount ->
                        onMarkStudentPresent(
                            studentId,
                            selectedDate,
                            visitCount,
                        )
                    },
                    onDismiss = {
                        attendanceDatePickerStudentId = null
                        onClearMarkStudentPresentState()
                    },
                )
            }

            attendanceRemovalRequest?.let { request ->
                AttendanceRemovalDialog(
                    studentName = request.studentName,
                    attendanceDate = request.attendanceDate,
                    isSaving = removeState == RemoveStudentAttendanceUiState.Saving,
                    errorMessage =
                    (removeState as? RemoveStudentAttendanceUiState.Error)?.message,
                    onConfirm = {
                        onRemoveStudentAttendance(
                            request.studentId,
                            request.attendanceDate,
                        )
                    },
                    onDismiss = {
                        attendanceRemovalRequest = null
                        onClearRemoveStudentAttendanceState()
                    },
                )
            }

            deleteStudentDialog?.let { studentId ->
                val student =
                    state.attendance.entries
                        .first { it.student.id == studentId }
                        .student

                AlertDialog(
                    onDismissRequest = {
                        deleteStudentDialog = null
                    },
                    title = {
                        Text(
                            text = "Delete student?",
                        )
                    },
                    text = {
                        Text(
                            text = "Delete \"${student.fullName}\"?",
                        )
                    },
                    confirmButton = {
                        SimplioButton(
                            onClick = {
                                onDeleteStudent(studentId)
                                deleteStudentDialog = null
                            },
                        ) {
                            Text(
                                text = "Delete",
                            )
                        }
                    },
                    dismissButton = {
                        SimplioTextButton(
                            onClick = {
                                deleteStudentDialog = null
                            },
                        ) {
                            Text(
                                text = "Cancel",
                            )
                        }
                    },
                )
            }
        }

        AttendanceUiState.Loading -> Unit

        is AttendanceUiState.Error -> {
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun AttendanceContent(
    state: AttendanceUiState.Content,
    onTrackClassAttendance: (String) -> Unit,
    onAddStudentClick: () -> Unit,
    onAddGroupClick: () -> Unit,
    onManageGroupsClick: () -> Unit,
    onAddPackageClick: () -> Unit,
    onDeleteStudentClick: (Int) -> Unit,
    onCopyAttendanceClick: (Int) -> Unit,
    onCheckStudentClick: (StudentAttendanceUiState) -> Unit,
    onRemoveStudentAttendanceClick: (StudentAttendanceUiState, LocalDate) -> Unit,
    onLoadNextClassHistoryPage: () -> Unit,
    onRetryClassHistory: () -> Unit,
    onEditNoteClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowsByGroup = state.rows.groupBy { row ->
        row.registeredStudent.student.groupId
    }

    LazyColumn(
        modifier = modifier.testTag("attendance-list"),
        contentPadding = PaddingValues(
            start = SimplioSpacing.Page,
            end = SimplioSpacing.Page,
            top = SimplioSpacing.Medium,
            bottom = SimplioSpacing.Page,
        ),
    ) {
        if (state.scheduleOverlaps.isNotEmpty()) {
            item(key = "schedule-overlaps", contentType = "schedule-overlaps") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("schedule-overlap-notice"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Overlapping schedules need review. Both schedules are saved.",
                        color = MaterialTheme.colorScheme.warning,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.scheduleOverlaps.forEach { overlap ->
                        Text(
                            "${overlap.first.name} / ${overlap.second.name}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        if (state.rows.isEmpty()) {
            item(key = "students-empty", contentType = "students-empty") {
                Text("No students yet")
            }
        }

        state.studentGroups.forEach { group ->
            val groupRows = rowsByGroup[group.id].orEmpty()

            if (groupRows.isNotEmpty()) {
                item(
                    key = "student-group-${group.id}",
                    contentType = "student-group-header",
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("student-group-${group.id}"),
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                top = 8.dp,
                                bottom = 10.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )

                            SimplioIconButton(
                                painter = painterResource(Res.drawable.ic_content_copy),
                                contentDescription = "Copy attendance for ${group.name}",
                                onClick = { onCopyAttendanceClick(group.id) },
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = groupRows,
                    key = { _, row ->
                        "student-${row.registeredStudent.student.id}"
                    },
                    contentType = { _, _ -> "student" },
                ) { index, row ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .simplioHoverSurface()
                            .testTag("student-row-${row.registeredStudent.student.id}")
                            .padding(horizontal = 12.dp),
                    ) {
                        StudentAttendanceRow(
                            row = row,
                            onCheckClick = {
                                onCheckStudentClick(row)
                            },
                            onDeleteClick = {
                                onDeleteStudentClick(row.registeredStudent.student.id)
                            },
                            onRemoveAttendanceClick = { date ->
                                onRemoveStudentAttendanceClick(row, date)
                            },
                        )

                        val student = row.registeredStudent.student

                        Text(
                            text = student.note.ifBlank { "Add note" },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (student.note.isBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .testTag("student-note-${student.id}")
                                .simplioClickable { onEditNoteClick(student.id) }
                                .semantics { contentDescription = "Edit note for ${student.fullName}" }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        )

                        if (index < groupRows.lastIndex) {
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        item(key = "add-student", contentType = "add-student") {
            FlowRow(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SimplioButton(onClick = onAddStudentClick) {
                    Text(stringResource(Res.string.attendance_add_student))
                }
                SimplioOutlinedButton(onClick = onAddGroupClick) {
                    Text(stringResource(Res.string.attendance_add_group))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SimplioOutlinedButton(onClick = onAddPackageClick) { Text(stringResource(Res.string.add_package)) }
                    StudentGroupOptions(onManageGroupsClick)
                }
            }
        }

        classHistoryItems(
            state = state.classHistoryState,
            studentGroups = state.studentGroups,
            onLoadNextPage = onLoadNextClassHistoryPage,
            onRetry = onRetryClassHistory,
            onTrackAttendance = onTrackClassAttendance,
        )
    }
}

@Composable
private fun StudentAttendanceRow(
    row: StudentAttendanceUiState,
    onCheckClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRemoveAttendanceClick: (LocalDate) -> Unit,
) {
    val registeredStudent = row.registeredStudent
    val studentPackage =
        registeredStudent.studentPackage

    var showPackageHistory by remember {
        mutableStateOf(false)
    }

    val historySource = remember { MutableInteractionSource() }
    val historyHovered by historySource.collectIsHoveredAsState()
    val historyFocused by historySource.collectIsKeyboardFocusedAsState()

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = registeredStudent.student.fullName,
            modifier = Modifier.weight(AttendanceDimensions.NAME_COLUMN_WEIGHT),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Box(
            modifier = Modifier.weight(AttendanceDimensions.CLASSES_COLUMN_WEIGHT),
        ) {
            Text(
                text =
                "${studentPackage.usedClasses} / " +
                    "${studentPackage.packageType.totalClasses}",
                modifier =
                Modifier
                    .testTag("student-package-history-${registeredStudent.student.id}")
                    .clickable(interactionSource = historySource, indication = null, role = Role.Button) {
                        showPackageHistory = true
                    }.padding(horizontal = 8.dp, vertical = 4.dp).semantics {
                        contentDescription =
                            "View package history for ${registeredStudent.student.fullName}"
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = if (historyHovered ||
                    historyFocused
                ) {
                    TextDecoration.Underline
                } else {
                    TextDecoration.None
                },
            )

            DropdownMenu(
                expanded = showPackageHistory,
                onDismissRequest = {
                    showPackageHistory = false
                },
                modifier = Modifier.testTag("package-history-menu"),
            ) {
                Text(
                    text = "Package history",
                    modifier =
                    Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                )

                studentPackage.attendanceDates
                    .sorted()
                    .forEachIndexed { index, date ->
                        Row(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 4.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}. ${date.toDisplayDate()}",
                                modifier = Modifier.weight(1f),
                            )

                            if (studentPackage.packageType.maxVisitsPerAttendance > 1) {
                                val visitCount =
                                    studentPackage.attendanceHistory.visitsByDate.getValue(date)

                                Text(
                                    text = visitCount.toString(),
                                    modifier = Modifier
                                        .padding(
                                            start = 12.dp,
                                            end = 8.dp,
                                        )
                                        .width(24.dp)
                                        .semantics {
                                            contentDescription =
                                                "Visits on ${date.toDisplayDate()} " +
                                                "for ${registeredStudent.student.fullName}"
                                            stateDescription = visitCount.toString()
                                        },
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            SimplioIconButton(
                                painter = painterResource(Res.drawable.ic_close),
                                contentDescription = "Remove attendance on ${date.toDisplayDate()} " +
                                    "for ${registeredStudent.student.fullName}",
                                onClick = { onRemoveAttendanceClick(date) },
                                tone = SimplioActionTone.Destructive,
                            )
                        }
                    }
            }
        }

        Text(
            text = studentPackage.startedOn?.toDisplayDate().orEmpty(),
            modifier = Modifier.weight(AttendanceDimensions.DATE_COLUMN_WEIGHT),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = if (studentPackage.packageType.validityWeeks == null) {
                stringResource(Res.string.package_no_expiration)
            } else {
                studentPackage.expiresOn?.toDisplayDate().orEmpty()
            },
            modifier = Modifier.weight(AttendanceDimensions.DATE_COLUMN_WEIGHT),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = studentPackage.packageType.name,
            modifier = Modifier.weight(AttendanceDimensions.PACKAGE_COLUMN_WEIGHT),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        SimplioIconButton(
            painter = painterResource(if (row.requiresRenewal) Res.drawable.ic_refresh else Res.drawable.ic_check),
            contentDescription = if (row.requiresRenewal) {
                "Renew package for ${registeredStudent.student.fullName}"
            } else {
                "Mark ${registeredStudent.student.fullName} present"
            },
            onClick = onCheckClick,
            enabled = row.isCheckEnabled,
            selected = !row.requiresRenewal && row.checkState == AttendanceCheckState.Checked,
            tone = when {
                row.requiresRenewal -> SimplioActionTone.Accent
                row.checkState == AttendanceCheckState.Checked -> SimplioActionTone.Positive
                else -> SimplioActionTone.Neutral
            },
            modifier = Modifier.semantics {
                stateDescription = if (row.requiresRenewal) "Renewal required" else row.checkState.name
            },
        )
        SimplioIconButton(
            painter = painterResource(Res.drawable.ic_close),
            contentDescription = "Delete ${registeredStudent.student.fullName}",
            onClick = onDeleteClick,
            tone = SimplioActionTone.Destructive,
        )
    }
}

internal fun LocalDate.toDisplayDate(): String {
    val month =
        month.name
            .take(3)
            .lowercase()
            .replaceFirstChar { it.uppercase() }

    return "$month $day"
}

private data class AttendanceRemovalRequest(val studentId: Int, val studentName: String, val attendanceDate: LocalDate)
