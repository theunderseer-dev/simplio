package com.theunderseer.simplio.shared.presentation.input

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.theunderseer.simplio.shared.validation.InputPolicy
import kotlinx.coroutines.channels.Channel

@Stable
class ValidatedInputState(val textFieldState: TextFieldState, val policy: InputPolicy) {
    constructor(initialText: String, policy: InputPolicy) : this(TextFieldState(initialText), policy)
    val text: String get() = textFieldState.text.toString()
    var rejectedEdit by mutableStateOf(false)
        private set
    private val attempts = Channel<Boolean>(Channel.CONFLATED)

    // Transform only the buffer here. A collector updates UI feedback after the edit is finished.
    val transformation = InputTransformation {
        val rejected = !policy.accepts(asCharSequence())
        if (rejected) {
            revertAllChanges()
            // A bounded clock edit must not invalidate an unchanged, valid time.
            if (policy != InputPolicy.ClockTime) attempts.trySend(true)
        } else {
            attempts.trySend(false)
        }
    }

    fun replaceText(text: String) {
        require(policy.accepts(text))
        textFieldState.setTextAndPlaceCursorAtEnd(text)
        rejectedEdit = false
    }

    internal suspend fun collectAttempts(onAccepted: () -> Unit) {
        for (rejected in attempts) {
            rejectedEdit = rejected
            if (!rejected) onAccepted()
        }
    }
}

@Composable
fun rememberValidatedInput(
    initialText: String,
    policy: InputPolicy,
    onAccepted: () -> Unit = {},
): ValidatedInputState {
    val field = rememberTextFieldState(initialText)
    val input = remember(field, policy) { ValidatedInputState(field, policy) }
    ObserveInputFeedback(input, onAccepted)
    return input
}

/** For a keyed collection of fields whose state also controls a dialog-level confirmation button. */
@Composable
fun ObserveInputFeedback(input: ValidatedInputState, onAccepted: () -> Unit = {}) {
    val currentOnAccepted by rememberUpdatedState(onAccepted)
    LaunchedEffect(input) { input.collectAttempts { currentOnAccepted() } }
}
