package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceRoster
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.ic_more_vert
import com.theunderseer.simplio.feature.attendance.resources.ios_roster_options
import com.theunderseer.simplio.feature.attendance.resources.ios_roster_refreshing
import com.theunderseer.simplio.feature.attendance.resources.ios_view_student_details
import com.theunderseer.simplio.feature.attendance.resources.roster_empty
import com.theunderseer.simplio.feature.attendance.resources.roster_loading
import com.theunderseer.simplio.feature.attendance.resources.roster_refresh
import com.theunderseer.simplio.feature.attendance.resources.roster_retry
import com.theunderseer.simplio.feature.attendance.resources.roster_sign_out
import com.theunderseer.simplio.feature.attendance.resources.roster_title
import com.theunderseer.simplio.shared.presentation.components.SimplioIconButton
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

private const val HORIZONTAL_ELLIPSIS_ROTATION = 90f

@Suppress("FunctionNaming") // Compose UI functions use PascalCase.
@Composable
internal fun AttendanceRosterScreen(
    state: AttendanceRosterState,
    now: Instant,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onEdit: suspend (AttendanceRosterEdit) -> String?,
    creation: IosAttendanceCreation? = null,
    onDeleteStudent: (suspend (Int) -> String?)? = null,
    syncNotice: @Composable () -> Unit = {},
    isRefreshing: Boolean = false,
    onUpdateStudentNote: (suspend (Int, String) -> String?)? = null,
) {
    var selectedStudentId by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val studentId = selectedStudentId
    if (studentId == null) {
        AttendanceRosterList(
            state = state,
            now = now,
            listState = listState,
            onRefresh = onRefresh,
            onSignOut = onSignOut,
            onEdit = onEdit,
            onStudentClick = { selectedStudentId = it },
            creation = creation,
            onDeleteStudent = onDeleteStudent,
            syncNotice = syncNotice,
            isRefreshing = isRefreshing,
        )
    } else {
        val roster = (state as? AttendanceRosterState.Content)?.roster
        val entry = roster?.attendance?.entries?.firstOrNull { it.student.id == studentId }
        IosStudentDetailsScreen(
            entry = entry,
            onUpdateNote = onUpdateStudentNote?.let { update -> { note -> update(studentId, note) } },
            onBack = { selectedStudentId = null },
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AttendanceRosterList(
    state: AttendanceRosterState,
    now: Instant,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onEdit: suspend (AttendanceRosterEdit) -> String?,
    onStudentClick: (Int) -> Unit,
    creation: IosAttendanceCreation?,
    onDeleteStudent: (suspend (Int) -> String?)?,
    syncNotice: @Composable () -> Unit,
    isRefreshing: Boolean,
) {
    var dialog by remember { mutableStateOf<StudentAttendanceUiState?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletion = remember(onDeleteStudent, scope) { onDeleteStudent?.let { IosRosterDeletion(scope, it) } }
    val roster = (state as? AttendanceRosterState.Content)?.roster
    var showQuickActions by remember(roster != null, creation) { mutableStateOf(false) }
    val reduceMotion = rememberIosReduceMotion()
    val systemBars = WindowInsets.systemBars
    val refresh = {
        deletion?.closeReveal()
        onRefresh()
    }
    Box(Modifier.fillMaxSize().closeRosterRevealOnTap(deletion)) {
        AttendanceRosterFrame(
            hideSemantics = showQuickActions,
            onRefresh = refresh.takeIf { state != AttendanceRosterState.Loading && !isRefreshing },
            onSignOut = onSignOut,
        ) {
            syncNotice()
            AttendanceRosterContent(
                state = state,
                now = now,
                listState = listState,
                reduceMotion = reduceMotion,
                isRefreshing = isRefreshing,
                onRefresh = refresh,
                deletion = deletion,
                onStudentClick = onStudentClick,
                onAction = { dialog = it },
            )
        }
        SnackbarHost(
            snackbarHost,
            Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(bottom = quickActionListClearance),
        )
        if (roster != null && creation != null) {
            RosterCreationOverlay(
                roster = roster,
                now = now,
                creation = creation,
                snackbarHost = snackbarHost,
                expanded = showQuickActions,
                reduceMotion = reduceMotion,
                onExpandedChange = { showQuickActions = it },
                modifier = Modifier.matchParentSize().windowInsetsPadding(systemBars),
            )
        }
    }
    if (roster != null) {
        if (deletion != null) IosRosterDeletionDialog(deletion)
        dialog?.let { selected ->
            AttendanceRosterActionDialog(selected, roster, now, onEdit) { dialog = null }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun RosterCreationOverlay(
    roster: AttendanceRoster,
    now: Instant,
    creation: IosAttendanceCreation,
    snackbarHost: SnackbarHostState,
    expanded: Boolean,
    reduceMotion: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    var action by remember(creation) { mutableStateOf<IosAttendanceQuickAction?>(null) }
    val scope = rememberCoroutineScope()
    IosAttendanceQuickActions(expanded, reduceMotion, onExpandedChange, { action = it }, modifier)
    action?.let { selected ->
        IosAttendanceCreationDialog(
            action = selected,
            roster = roster,
            now = now,
            creation = creation,
            onCreated = { resource, name ->
                action = null
                scope.launch { snackbarHost.showSnackbar(getString(resource, name)) }
            },
            onDismiss = { action = null },
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AttendanceRosterFrame(
    hideSemantics: Boolean,
    onRefresh: (() -> Unit)?,
    onSignOut: () -> Unit,
    content: @Composable () -> Unit,
) {
    val contentSemantics = if (hideSemantics) Modifier.clearAndSetSemantics {} else Modifier
    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .then(contentSemantics),
    ) {
        AttendanceRosterHeader(onRefresh, onSignOut)
        HorizontalDivider()
        content()
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AttendanceRosterHeader(onRefresh: (() -> Unit)?, onSignOut: () -> Unit) {
    val refreshLabel = stringResource(Res.string.roster_refresh)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.roster_title),
            modifier = Modifier.weight(1f).semantics {
                heading()
                if (onRefresh != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(refreshLabel) {
                            onRefresh()
                            true
                        },
                    )
                }
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        AttendanceRosterOptions(onSignOut)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AttendanceRosterOptions(onSignOut: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SimplioIconButton(
            painter = painterResource(Res.drawable.ic_more_vert),
            contentDescription = stringResource(Res.string.ios_roster_options),
            onClick = { expanded = true },
            modifier = Modifier.rotate(HORIZONTAL_ELLIPSIS_ROTATION),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.roster_sign_out)) },
                onClick = {
                    expanded = false
                    onSignOut()
                },
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AttendanceRosterContent(
    state: AttendanceRosterState,
    now: Instant,
    listState: LazyListState,
    reduceMotion: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    deletion: IosRosterDeletion?,
    onStudentClick: (Int) -> Unit,
    onAction: (StudentAttendanceUiState) -> Unit,
) {
    val roster = (state as? AttendanceRosterState.Content)?.roster
    val rows = remember(roster, now) { roster?.toRows(now).orEmpty() }
    val pullState = rememberPullToRefreshState()
    val refreshLabel = stringResource(Res.string.ios_roster_refreshing)
    val bottomInset = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().clipToBounds(),
        state = pullState,
        enabled = state != AttendanceRosterState.Loading,
        indicator = {
            if (isRefreshing || pullState.distanceFraction > 0f) {
                val progress = if (isRefreshing) Modifier.semantics { contentDescription = refreshLabel } else Modifier
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter).then(progress),
                )
            }
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("ios-attendance-list"),
            state = listState,
            contentPadding = PaddingValues(
                top = PullToRefreshDefaults.IndicatorMaxDistance * pullState.distanceFraction.coerceIn(0f, 1f),
                bottom = quickActionListClearance + bottomInset,
            ),
        ) {
            if (roster != null && rows.isNotEmpty()) {
                groupedRosterItems(roster, rows, reduceMotion, deletion, onStudentClick, onAction)
            } else {
                item(key = "roster-status") { RosterStatus(state, onRefresh, Modifier.fillParentMaxSize()) }
            }
        }
    }
}

private fun LazyListScope.groupedRosterItems(
    roster: AttendanceRoster,
    rows: List<StudentAttendanceUiState>,
    reduceMotion: Boolean,
    deletion: IosRosterDeletion?,
    onStudentClick: (Int) -> Unit,
    onAction: (StudentAttendanceUiState) -> Unit,
) {
    val rowsByGroup = rows.groupBy { it.registeredStudent.student.groupId }
    val namedGroupIds = roster.studentGroups.mapTo(mutableSetOf()) { it.id }
    roster.studentGroups.forEach { group ->
        val groupRows = rowsByGroup[group.id].orEmpty()
        if (groupRows.isNotEmpty()) {
            item(key = "student-group-${group.id}", contentType = "student-group-header") {
                RosterGroupHeader(group.name, group.id)
            }
            attendanceRosterItems(groupRows, reduceMotion, deletion, onStudentClick, onAction)
        }
    }
    val ungroupedRows = rows.filterNot { it.registeredStudent.student.groupId in namedGroupIds }
    attendanceRosterItems(ungroupedRows, reduceMotion, deletion, onStudentClick, onAction)
}

@Suppress("FunctionNaming")
@Composable
private fun RosterGroupHeader(name: String, groupId: Int) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 18.dp, end = 16.dp)) {
        Text(
            text = name,
            modifier = Modifier.padding(bottom = 6.dp).semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.testTag("ios-group-divider-$groupId"))
    }
}

private fun LazyListScope.attendanceRosterItems(
    rows: List<StudentAttendanceUiState>,
    reduceMotion: Boolean,
    deletion: IosRosterDeletion?,
    onStudentClick: (Int) -> Unit,
    onAction: (StudentAttendanceUiState) -> Unit,
) {
    items(rows, key = { it.registeredStudent.student.id }, contentType = { "student-row" }) { row ->
        val student = row.registeredStudent.student
        val details = Modifier.clickable(
            onClickLabel = stringResource(Res.string.ios_view_student_details),
            role = Role.Button,
        ) {
            if (deletion?.revealedStudentId != null) {
                deletion.closeReveal()
            } else {
                onStudentClick(student.id)
            }
        }
        val action = {
            deletion?.closeReveal()
            onAction(row)
        }
        if (deletion == null) {
            AttendanceRosterRow(row, reduceMotion, action, modifier = details)
        } else {
            IosStudentSwipeRow(
                studentName = student.fullName,
                expanded = deletion.revealedStudentId == student.id,
                onExpandedChange = { deletion.setRevealed(student.id, it) },
                onRequestDelete = { deletion.request(student) },
                modifier = Modifier.testTag("ios-student-row-${student.id}"),
                reduceMotion = reduceMotion,
                foregroundModifier = details,
            ) { AttendanceRosterRow(row, reduceMotion, action) }
        }
        HorizontalDivider()
    }
}

@Suppress("FunctionNaming")
@Composable
private fun RosterStatus(state: AttendanceRosterState, onRefresh: () -> Unit, modifier: Modifier) {
    Box(modifier.padding(20.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (state) {
                AttendanceRosterState.Loading -> {
                    val loading = stringResource(Res.string.roster_loading)
                    CircularProgressIndicator(Modifier.semantics { contentDescription = loading })
                }
                is AttendanceRosterState.Error -> {
                    Text(stringResource(state.message))
                    TextButton(onClick = onRefresh) { Text(stringResource(Res.string.roster_retry)) }
                }
                is AttendanceRosterState.Content -> Text(stringResource(Res.string.roster_empty))
            }
        }
    }
}
