package com.hsib.sib.core.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceHintsTest {
    @Test fun readsTheExampleFromTheOwner() {
        val h = VoiceHints.parse("Hey app, remind me I have an assignment due on 19 November. It is very important. Remind me 5 days before, and I might need only 3 to 5 hours to do it")
        assertEquals(listOf(5), h.leadDays)
        assertEquals(240, h.effortMin)
    }

    @Test fun wordsAndWeeksWork() {
        assertEquals(listOf(7, 3), VoiceHints.parse("remind me a week before and three days before").leadDays)
    }

    @Test fun nothingSaidMeansNothingFound() {
        val h = VoiceHints.parse("Report due 18 Nov")
        assertEquals(emptyList<Int>(), h.leadDays)
        assertNull(h.effortMin)
    }
}
