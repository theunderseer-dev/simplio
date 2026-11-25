package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.ic_check
import com.theunderseer.simplio.feature.attendance.resources.ic_refresh
import com.theunderseer.simplio.feature.attendance.resources.ios_mark_student_present
import com.theunderseer.simplio.feature.attendance.resources.ios_remove_student_attendance
import com.theunderseer.simplio.feature.attendance.resources.ios_renew_student_package
import com.theunderseer.simplio.feature.attendance.resources.package_no_expiration
import com.theunderseer.simplio.feature.attendance.resources.roster_check
import com.theunderseer.simplio.feature.attendance.resources.roster_checked
import com.theunderseer.simplio.feature.attendance.resources.roster_expires_on
import com.theunderseer.simplio.feature.attendance.resources.roster_not_started
import com.theunderseer.simplio.feature.attendance.resources.roster_renew
import com.theunderseer.simplio.feature.attendance.resources.roster_visits_used
import com.theunderseer.simplio.shared.presentation.components.SimplioActionTone
import com.theunderseer.simplio.shared.presentation.components.SimplioIconButton
import com.theunderseer.simplio.shared.presentation.theme.positive
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Suppress("FunctionNaming")
@Composable
internal fun AttendanceRosterRow(
    row: StudentAttendanceUiState,
    reduceMotion: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
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
