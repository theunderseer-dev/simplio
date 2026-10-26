package com.theunderseer.simplio.shared.validation

import com.theunderseer.simplio.shared.fixtures.InputTestData
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputPolicyTest {
    @Test
    fun `numbers allow empty editing zero leading zeros and ten ASCII digits`() {
        listOf("", "0", "0008", InputTestData.number, Int.MAX_VALUE.toString()).forEach {
            assertTrue(InputPolicy.UnsignedInteger.accepts(it), it)
        }
        listOf("-8", "+8", "1.5", "1e3", "8 classes", " 8", "8\n", "１２", "١٢", "12345678901").forEach {
            assertFalse(InputPolicy.UnsignedInteger.accepts(it), it)
        }
    }

    @Test
    fun `single line names permit international text digits and punctuation but reject controls and excess length`() {
        val rule = InputPolicy.SingleLine(80)
        assertTrue(rule.accepts(InputTestData.singleLineText))
        assertTrue(rule.accepts(""))
        assertTrue(rule.accepts("x".repeat(80)))
        listOf("x".repeat(81), "a\nb", "a\tb", "a\rb", "a\u0000b", "a\u2028b", "a\u2029b").forEach {
            assertFalse(rule.accepts(it))
        }
    }

    @Test
    fun `clock editing is bounded without making partial edits impossible`() {
        listOf("", "0", "09", "09:", "09:0", "09:00", "23:59", "24:00").forEach {
            assertTrue(InputPolicy.ClockTime.accepts(it), it)
        }
        listOf("09:000", "09:00 111", "09::0", "09a00", "０９:００").forEach {
            assertFalse(InputPolicy.ClockTime.accepts(it), it)
        }
    }

    @Test
    fun `zone syntax allows IANA and fixed offset editing but rejects spaces and excess input`() {
        listOf("", "UTC", "Asia/Bangkok", "Etc/GMT+5", "UTC+07:00").forEach {
            assertTrue(InputPolicy.TimeZone.accepts(it), it)
        }
        listOf("Asia /Bangkok", "Asia/Bangkok\n", "x".repeat(129)).forEach {
            assertFalse(InputPolicy.TimeZone.accepts(it), it)
        }
    }

    @Test
    fun `email validation is basic syntax with no destructive normalization`() {
        assertTrue(isValidSignInEmail(InputTestData.email))
        assertTrue(isValidSignInEmail("teacher@localhost"))
        listOf("", "teacher", "@example.com", "teacher@", "a@@example.com", " teacher@example.com", "a@b\n").forEach {
            assertFalse(isValidSignInEmail(it), it)
        }
        assertFalse(InputPolicy.Email.accepts("x".repeat(MAX_EMAIL_LENGTH + 1)))
    }
}
