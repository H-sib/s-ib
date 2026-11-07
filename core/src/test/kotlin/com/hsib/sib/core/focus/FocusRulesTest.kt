package com.hsib.sib.core.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusRulesTest {
    @Test fun elapsedIsWholeMinutesAndNeverNegative() {
        assertEquals(0, FocusRules.elapsedMin(1_000_000, 1_000_000 + 59_999))
        assertEquals(1, FocusRules.elapsedMin(1_000_000, 1_000_000 + 60_000))
        assertEquals(0, FocusRules.elapsedMin(5_000_000, 1_000_000))
    }

    @Test fun twoForgottenDaysCountAsLongAndAreCappedInTheBox() {
        val twoDays = FocusRules.elapsedMin(0, 2 * 24 * 3_600_000L)
        assertEquals(2880, twoDays)
        assertTrue(FocusRules.isLong(twoDays))
        assertEquals(FocusRules.LONG_MIN, FocusRules.suggestedMin(twoDays))
    }

    @Test fun anOrdinarySessionIsAddedAsItRan() {
        assertFalse(FocusRules.isLong(45))
        assertFalse(FocusRules.isLong(FocusRules.LONG_MIN - 1))
        assertTrue(FocusRules.isLong(FocusRules.LONG_MIN))
        assertEquals(45, FocusRules.suggestedMin(45))
    }

    @Test fun lengthsReadPlainly() {
        assertEquals("0 min", FocusRules.describe(0))
        assertEquals("45 min", FocusRules.describe(45))
        assertEquals("3 h", FocusRules.describe(180))
        assertEquals("3 h 20 min", FocusRules.describe(200))
        assertEquals("1 day", FocusRules.describe(1440))
        assertEquals("2 days 3 h", FocusRules.describe(2 * 1440 + 185))
    }

    @Test fun theBoxIsReadSafely() {
        assertEquals(90, FocusRules.parseMinutes(" 90 "))
        assertEquals(0, FocusRules.parseMinutes(""))
        assertEquals(0, FocusRules.parseMinutes("abc"))
        assertEquals(0, FocusRules.parseMinutes("-5"))
        assertEquals(7 * 1440, FocusRules.parseMinutes("999999"))
    }
}
