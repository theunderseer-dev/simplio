package com.theunderseer.simplio.shared.validation

/** Editing constraints only. Required values and business ranges are checked separately. */
sealed interface InputPolicy {
    fun accepts(text: CharSequence): Boolean

    data class SingleLine(val maxLength: Int) : InputPolicy {
        override fun accepts(text: CharSequence): Boolean = text.length <= maxLength && text.none(::isLineControl)
    }

    data object UnsignedInteger : InputPolicy {
        override fun accepts(text: CharSequence): Boolean = text.length <= 10 && text.all { it in '0'..'9' }
    }

    data object ClockTime : InputPolicy {
        override fun accepts(text: CharSequence): Boolean =
            text.length <= 5 && text.all { it in '0'..'9' || it == ':' } && text.count { it == ':' } <= 1
    }

    data object TimeZone : InputPolicy {
        override fun accepts(text: CharSequence): Boolean = text.length <= 128 &&
            text.all { it.isLetterOrDigit() && it.code < 128 || it in "/_+-:." }
    }

    data object Email : InputPolicy {
        override fun accepts(text: CharSequence): Boolean =
            text.length <= MAX_EMAIL_LENGTH && text.none { it.isWhitespace() || isLineControl(it) }
    }
}

const val MAX_EMAIL_LENGTH = 254

fun isLineControl(character: Char): Boolean = character.isISOControl() || character == '\u2028' || character == '\u2029'

/** Deliberately basic syntax check; Firebase remains authoritative for account/email validity. */
fun isValidSignInEmail(email: String): Boolean = InputPolicy.Email.accepts(email) &&
    email.count { it == '@' } == 1 &&
    email.substringBefore('@').isNotEmpty() &&
    email.substringAfter('@').isNotEmpty()
