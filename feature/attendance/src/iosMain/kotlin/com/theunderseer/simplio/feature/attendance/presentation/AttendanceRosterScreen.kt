package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceRoster
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.ic_check
import com.theunderseer.simplio.feature.attendance.resources.ic_more_vert
import com.theunderseer.simplio.feature.attendance.resources.ic_refresh
import com.theunderseer.simplio.feature.attendance.resources.ios_mark_student_present
import com.theunderseer.simplio.feature.attendance.resources.ios_remove_student_attendance
import com.theunderseer.simplio.feature.attendance.resources.ios_renew_student_package
import com.theunderseer.simplio.feature.attendance.resources.ios_roster_options
import com.theunderseer.simplio.feature.attendance.resources.ios_roster_refreshing
import com.theunderseer.simplio.feature.attendance.resources.package_no_expiration
import com.theunderseer.simplio.feature.attendance.resources.roster_check
import com.theunderseer.simplio.feature.attendance.resources.roster_checked
import com.theunderseer.simplio.feature.attendance.resources.roster_empty
import com.theunderseer.simplio.feature.attendance.resources.roster_expires_on
import com.theunderseer.simplio.feature.attendance.resources.roster_loading
import com.theunderseer.simplio.feature.attendance.resources.roster_not_started
import com.theunderseer.simplio.feature.attendance.resources.roster_refresh
import com.theunderseer.simplio.feature.attendance.resources.roster_renew
import com.theunderseer.simplio.feature.attendance.resources.roster_retry
import com.theunderseer.simplio.feature.attendance.resources.roster_sign_out
import com.theunderseer.simplio.feature.attendance.resources.roster_title
import com.theunderseer.simplio.feature.attendance.resources.roster_visits_used
import com.theunderseer.simplio.shared.presentation.components.SimplioActionTone
import com.theunderseer.simplio.shared.presentation.components.SimplioIconButton
import com.theunderseer.simplio.shared.presentation.theme.positive
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
) {
    var dialog by remember { mutableStateOf<StudentAttendanceUiState?>(null) }
    var creationDialog by remember(creation) { mutableStateOf<IosAttendanceQuickAction?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletion = remember(onDeleteStudent, scope) { onDeleteStudent?.let { IosRosterDeletion(scope, it) } }
    val showTrigger = state is AttendanceRosterState.Content && creation != null
    var showQuickActions by remember(showTrigger) { mutableStateOf(false) }
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
            AttendanceRosterContent(state, now, reduceMotion, isRefreshing, refresh, deletion) { row -> dialog = row }
        }
        SnackbarHost(
            snackbarHost,
            Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(bottom = quickActionListClearance),
        )
        if (showTrigger) {
            IosAttendanceQuickActions(
                expanded = showQuickActions,
                reduceMotion = reduceMotion,
                onExpandedChange = { showQuickActions = it },
                onAction = { creationDialog = it },
                modifier = Modifier.matchParentSize().windowInsetsPadding(systemBars),
            )
        }
    }
    if (state is AttendanceRosterState.Content) {
        if (deletion != null) IosRosterDeletionDialog(deletion)
        dialog?.let { selected ->
            AttendanceRosterActionDialog(selected, state.roster, now, onEdit) { dialog = null }
        }
        val action = creationDialog
        if (creation != null && action != null) {
            IosAttendanceCreationDialog(
                action = action,
                roster = state.roster,
                now = now,
                creation = creation,
                onCreated = { resource, name ->
                    creationDialog = null
                    scope.launch { snackbarHost.showSnackbar(getString(resource, name)) }
                },
                onDismiss = { creationDialog = null },
            )
        }
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
    reduceMotion: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    deletion: IosRosterDeletion?,
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
            contentPadding = PaddingValues(
                top = PullToRefreshDefaults.IndicatorMaxDistance * pullState.distanceFraction.coerceIn(0f, 1f),
                bottom = quickActionListClearance + bottomInset,
            ),
        ) {
            if (roster != null && rows.isNotEmpty()) {
                groupedRosterItems(roster, rows, reduceMotion, deletion, onAction)
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
            attendanceRosterItems(groupRows, reduceMotion, deletion, onAction)
        }
    }
    val ungroupedRows = rows.filterNot { it.registeredStudent.student.groupId in namedGroupIds }
    attendanceRosterItems(ungroupedRows, reduceMotion, deletion, onAction)
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
    onAction: (StudentAttendanceUiState) -> Unit,
) {
    items(rows, key = { it.registeredStudent.student.id }, contentType = { "student-row" }) { row ->
        val student = row.registeredStudent.student
        val content: @Composable () -> Unit = {
            AttendanceRosterRow(row, reduceMotion) {
                deletion?.closeReveal()
                onAction(row)
            }
        }
        if (deletion == null) {
            content()
        } else {
            IosStudentSwipeRow(
                studentName = student.fullName,
                expanded = deletion.revealedStudentId == student.id,
                onExpandedChange = { deletion.setRevealed(student.id, it) },
                onRequestDelete = { deletion.request(student) },
                modifier = Modifier.testTag("ios-student-row-${student.id}"),
                reduceMotion = reduceMotion,
                content = content,
            )
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

@Suppress("FunctionNaming")
@Composable
private fun AttendanceRosterRow(row: StudentAttendanceUiState, reduceMotion: Boolean, onAction: () -> Unit) {
    val student = row.registeredStudent.student
    val purchase = row.registeredStudent.studentPackage
    val total = purchase.packageType.totalClasses
    val used = purchase.usedClasses
    val visits = stringResource(Res.string.roster_visits_used, used, total)
    val expiryDate = purchase.expiresOn
    val expiration = when {
        purchase.packageType.validityWeeks == null -> stringResource(Res.string.package_no_expiration)
        expiryDate == null -> stringResource(Res.string.roster_not_started)
        else -> stringResource(Res.string.roster_expires_on, expiryDate.toDisplayDate())
    }
    val textColor = if (row.requiresRenewal) MaterialTheme.colorScheme.positive else Color.Unspecified
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(student.fullName, color = textColor, style = MaterialTheme.typography.titleMedium)
                Text(expiration, color = textColor, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "$used/$total",
                modifier = Modifier.clearAndSetSemantics { contentDescription = visits },
                color = textColor,
                style = MaterialTheme.typography.titleMedium,
            )
            AttendanceRosterAction(row, reduceMotion, onAction)
        }
        if (student.note.isNotBlank()) {
            Text(
                text = student.note,
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AttendanceRosterAction(row: StudentAttendanceUiState, reduceMotion: Boolean, onAction: () -> Unit) {
    val checked = !row.requiresRenewal && row.checkState == AttendanceCheckState.Checked
    val (description, status) = when {
        row.requiresRenewal -> Res.string.ios_renew_student_package to Res.string.roster_renew
        checked -> Res.string.ios_remove_student_attendance to Res.string.roster_checked
        else -> Res.string.ios_mark_student_present to Res.string.roster_check
    }
    val rotation by animateFloatAsState(
        targetValue = if (row.requiresRenewal) 360f else 0f,
        animationSpec = if (reduceMotion || !row.requiresRenewal) snap() else tween(260),
    )
    val state = stringResource(status)
    SimplioIconButton(
        painter = painterResource(if (row.requiresRenewal) Res.drawable.ic_refresh else Res.drawable.ic_check),
        contentDescription = stringResource(description, row.registeredStudent.student.fullName),
        onClick = onAction,
        modifier = Modifier.rotate(rotation).semantics { stateDescription = state },
        enabled = row.isCheckEnabled,
        tone = if (row.requiresRenewal || checked) SimplioActionTone.Positive else SimplioActionTone.Neutral,
        selected = checked,
    )
}
