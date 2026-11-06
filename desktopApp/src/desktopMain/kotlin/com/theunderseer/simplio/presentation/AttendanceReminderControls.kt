package com.theunderseer.simplio.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.desktop.resources.Res
import com.theunderseer.simplio.desktop.resources.attendance_reminders
import com.theunderseer.simplio.desktop.resources.attendance_reminders_failed
import com.theunderseer.simplio.desktop.resources.attendance_reminders_permission
import com.theunderseer.simplio.desktop.resources.attendance_reminders_retry
import com.theunderseer.simplio.notifications.DesktopAttendanceReminders
import com.theunderseer.simplio.shared.presentation.components.SimplioTextButton
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AttendanceReminderControls(
    reminders: DesktopAttendanceReminders,
    account: String,
    updating: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val settings by reminders.settings.collectAsState()
    if (settings.account != account) return

    DropdownMenuItem(
        text = { Text(stringResource(Res.string.attendance_reminders)) },
        onClick = { onEnabledChange(!settings.enabled) },
        enabled = !updating,
        trailingIcon = {
            Switch(checked = settings.enabled, onCheckedChange = null, enabled = !updating)
        },
        modifier = Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(settings.enabled)
        },
    )
    if (settings.permissionDenied || settings.failed) {
        Column(Modifier.widthIn(max = 280.dp).padding(horizontal = 12.dp)) {
            if (settings.permissionDenied) {
                Text(
                    stringResource(Res.string.attendance_reminders_permission),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (settings.failed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.attendance_reminders_failed),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    SimplioTextButton(onClick = reminders::retry) {
                        Text(stringResource(Res.string.attendance_reminders_retry))
                    }
                }
            }
        }
    }
}
