package com.theunderseer.simplio.shared.presentation.fixtures

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.shared.presentation.components.SimplioFilterChip
import com.theunderseer.simplio.shared.presentation.components.SimplioIconButton
import com.theunderseer.simplio.shared.presentation.components.SimplioOutlinedButton
import com.theunderseer.simplio.shared.presentation.components.SimplioToggleIconButton
import com.theunderseer.simplio.shared.presentation.components.simplioClickable

internal object SimplioFocusTestData {
    const val ACTION_LABEL = "Action"
    const val NEXT_LABEL = "Next"
    const val DIALOG_TITLE = "Create item"
    const val CLOSE_LABEL = "Close"
}

internal enum class FocusTestControl { Button, Icon, Note, Toggle, SelectedChip }

@Composable
internal fun FocusTestAction(kind: FocusTestControl, label: String, modifier: Modifier, onClick: () -> Unit) {
    when (kind) {
        FocusTestControl.Button -> SimplioOutlinedButton(onClick, modifier) { Text(label) }
        FocusTestControl.Icon -> SimplioIconButton(ColorPainter(Color.Black), label, onClick, modifier)
        FocusTestControl.Note -> Box(modifier.size(120.dp, 48.dp).simplioClickable(onClick = onClick)) {
            Text(label)
        }
        FocusTestControl.Toggle -> SimplioToggleIconButton(
            ColorPainter(Color.Black),
            label,
            true,
            { onClick() },
            modifier,
        )
        FocusTestControl.SelectedChip -> SimplioFilterChip(true, onClick, { Text(label) }, modifier)
    }
}
