package com.hsib.sib.core.extract

import com.hsib.sib.core.model.Category
import com.hsib.sib.core.model.Candidate
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Flag
import com.hsib.sib.core.model.Kind
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.model.Relation
import com.hsib.sib.core.model.SourceType
import com.hsib.sib.core.model.Tier
import com.hsib.sib.core.time.DateOrder
import com.hsib.sib.core.time.TemporalContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ExtractorTest {
    private val zone = ZoneId.of("UTC")
    private val ref = ZonedDateTime.of(2026, 10, 14, 10, 0, 0, 0, zone)
    private val ctx = TemporalContext(ref, dateOrder = DateOrder.DMY)
    private val extractor = Extractor()

    private fun extract(text: String, existing: List<Obligation> = emptyList(), dismissed: Set<String> = emptySet(), c: TemporalContext = ctx) =
        extractor.extract(text, c, SourceType.SHARE_TEXT, existing, dismissed)

    private fun one(text: String, existing: List<Obligation> = emptyList()): Candidate {
        val r = extract(text, existing)
        assertEquals("candidates for '$text': ${r.candidates.map { it.title + " " + it.due }}; outcomes: ${r.outcomes.map { it.explanation }}", 1, r.candidates.size)
        return r.candidates[0]
    }

    @Test fun theModelShipsWithTheLibrary() {
        assertNotNull("role_model.json must be bundled", RoleModel.loadDefault())
    }

    @Test fun clearDeadlineBecomesAHighConfidenceSuggestion() {
        val c = one("Please submit the DSP assignment by November 18 at 11:59 PM.")
        assertEquals(Tier.SUGGEST, c.tier)
        assertEquals(Kind.DEADLINE, c.kind)
        assertEquals(Category.ASSIGNMENT, c.category)
        assertEquals(LocalDateTime.of(2026, 11, 18, 23, 59), c.due!!.local)
        assertEquals(Precision.DATE_TIME, c.due.precision)
        assertTrue(c.title.contains("DSP assignment"))
        assertTrue(c.confidence >= 0.8)
        assertTrue("the reasons explain the decision", c.reasons.isNotEmpty())
        assertEquals(Relation.NEW, c.relation)
    }

    @Test fun publicationDatesAreIgnoredWithAReason() {
        val r = extract("Posted on 10 Oct 2026. Submit the report by 5 Nov.")
        assertEquals(1, r.candidates.size)
        assertEquals(LocalDate.of(2026, 11, 5), r.candidates[0].due!!.local.toLocalDate())
        assertEquals(2, r.outcomes.size)
        val ignored = r.outcomes.first { !it.accepted }
        assertTrue(ignored.explanation, ignored.explanation.startsWith("Ignored"))
    }

    @Test fun historyAndOrdinaryDatesDoNotInterrupt() {
        assertTrue(extract("The meeting happened last Monday and went well.").candidates.isEmpty())
        assertTrue(extract("We submitted the proposal on 30 September.").candidates.isEmpty())
        assertTrue(extract("Flash sale ends 27 November, up to 70% off!").candidates.isEmpty())
        assertTrue(extract("The cafeteria is closed on 31 October for the holiday.").candidates.isEmpty())
    }

    @Test fun tentativeWordingStaysAMaybe() {
        val c = one("Maybe we should meet on 21 October?")
        assertEquals(Tier.MAYBE, c.tier)
        assertTrue(Flag.TENTATIVE_WORDING in c.flags)
    }

    @Test fun datesAlreadyInThePastAreNotSuggested() {
        val r = extract("The deadline was 3 October.")
        assertTrue(r.candidates.isEmpty())
        assertTrue(r.outcomes.single().explanation.contains("past"))
    }

    @Test fun ambiguousDayMonthOrderIsFlaggedAndOffersTheOtherReading() {
        val c = one("Fee payment is due by 12/11/2026.")
        assertTrue(Flag.AMBIGUOUS_ORDER in c.flags)
        assertEquals(1, c.alternatives.size)
        assertEquals(LocalDate.of(2026, 11, 12), c.due!!.local.toLocalDate())
        assertEquals(LocalDate.of(2026, 12, 11), c.alternatives[0].local.toLocalDate())
        val mdy = extract("Fee payment is due by 12/11/2026.", c = TemporalContext(ref, dateOrder = DateOrder.MDY)).candidates.single()
        assertEquals(LocalDate.of(2026, 12, 11), mdy.due!!.local.toLocalDate())
    }

    @Test fun midnightIsFlaggedBecauseItBelongsToTwoDays() {
        val c = one("Applications close at midnight on 15 November.")
        assertTrue(Flag.MIDNIGHT in c.flags)
        assertEquals(LocalDateTime.of(2026, 11, 16, 0, 0), c.due!!.local)
        assertEquals(LocalDateTime.of(2026, 11, 15, 0, 0), c.alternatives.single().local)
    }

    @Test fun windowsBecomeTentativeFogWindows() {
        val c = one("The final report must be submitted sometime in late November.")
        assertNotNull(c.fog)
        assertEquals(LocalDate.of(2026, 11, 21), c.fog!!.earliest)
        assertEquals(LocalDate.of(2026, 11, 30), c.fog.latest)
    }

    @Test fun quotedReplyTextNeverProducesSuggestions() {
        assertTrue(extract("Thanks, noted.\n> Submit the report by 18 November\n> regards").candidates.isEmpty())
        assertTrue(extract("Sounds good\nOn Tue, Oct 6, 2026 at 9:00 AM Sam wrote:\nPlease submit the report by 18 November").candidates.isEmpty())
    }

    @Test fun digitsFromOtherNumeralSystemsAreUnderstood() {
        val c = one("Submit the report by 18 November ٢٠٢٦")
        assertEquals(LocalDate.of(2026, 11, 18), c.due!!.local.toLocalDate())
    }

    @Test fun titlesReadWell() {
        assertEquals("Submit DSP assignment", one("Submit the DSP assignment by Nov 18").title)
        assertEquals("Final report deadline", one("The deadline for the final report is Nov 18.").title)
        assertEquals("Registration closes", one("Registration closes on 10 November").title)
        assertEquals("Chemistry midterm", one("The chemistry midterm is on 5 November in Hall B.").title)
    }

    @Test fun aLabelledDateTakesItsTitleFromTheLineAbove() {
        val c = one("Library fine payment\nDeadline: 20 October")
        assertTrue(c.title, c.title.lowercase().contains("library fine"))
    }

    // ---- changes, cancellations and duplicates -------------------------------------------------

    private fun tracked(id: String, title: String, y: Int, m: Int, d: Int, cat: Category = Category.ASSIGNMENT) = Obligation(
        id = id, title = title, category = cat, kind = Kind.DEADLINE,
        due = DueSpec(LocalDateTime.of(y, m, d, 0, 0), zone, Precision.DATE_ONLY),
    )

    @Test fun anExtensionIsLinkedToTheExistingItemInsteadOfCreatingADuplicate() {
        val existing = listOf(tracked("dsp", "DSP assignment", 2026, 11, 18))
        val c = one("The DSP assignment deadline has been extended to November 25.", existing)
        assertEquals(Relation.UPDATE, c.relation)
        assertEquals("dsp", c.relatedId)
        assertEquals(LocalDate.of(2026, 11, 25), c.due!!.local.toLocalDate())
    }

    @Test fun cancellationLinksToTheItemAndNeverCreatesANewOne() {
        val existing = listOf(tracked("sem", "Seminar", 2026, 10, 24, Category.MEETING))
        val c = one("The seminar on 24 October has been cancelled.", existing)
        assertEquals(Relation.CANCEL, c.relation)
        assertEquals("sem", c.relatedId)
        assertTrue(extract("The seminar on 24 October has been cancelled.").candidates.isEmpty())
    }

    @Test fun somethingAlreadyTrackedIsNotSuggestedAgain() {
        val existing = listOf(tracked("dsp", "DSP assignment", 2026, 11, 18))
        val r = extract("Reminder: submit the DSP assignment by November 18.", existing)
        assertTrue(r.candidates.isEmpty())
        assertTrue(r.outcomes.single().explanation.contains("already track"))
    }

    @Test fun differentItemsWithSimilarWordsAreNotMerged() {
        val existing = listOf(tracked("a1", "Assignment 1", 2026, 11, 18))
        val c = one("Please submit Assignment 2 by 25 November.", existing)
        assertEquals(Relation.NEW, c.relation)
    }

    @Test fun dismissedSuggestionsStayDismissed() {
        val c = one("Please submit the DSP assignment by November 18.")
        val again = extract("Please submit the DSP assignment by November 18.", dismissed = setOf(c.fingerprint))
        assertTrue(again.candidates.isEmpty())
        assertTrue(again.outcomes.single().explanation.contains("dismissed"))
        val hmacA = HmacFingerprinter(ByteArray(32) { 1 }).of("x")
        val hmacB = HmacFingerprinter(ByteArray(32) { 2 }).of("x")
        assertTrue("keyed fingerprints must differ per key", hmacA != hmacB)
    }

    @Test fun twoMentionsOfTheSameThingGiveOneSuggestion() {
        val r = extract("Submit the report by 18 November. Again, the report is due 18 November.")
        assertEquals(1, r.candidates.size)
        assertEquals(1, r.outcomes.count { it.accepted })
    }

    @Test fun severalDifferentItemsInOneMessageAreAllFound() {
        val r = extract("Final exam schedule: Mathematics on 14 December, Physics on 16 December.")
        assertEquals(2, r.candidates.size)
        assertEquals(setOf(LocalDate.of(2026, 12, 14), LocalDate.of(2026, 12, 16)), r.candidates.map { it.due!!.local.toLocalDate() }.toSet())
    }

    // ---- robustness -----------------------------------------------------------------------------

    @Test fun neverThrowsAndStaysFastOnLargeInput() {
        val sentence = "Please submit the report by 18 November 2026 at 5 pm. We met on 3 October. Maybe Friday? "
        val big = sentence.repeat(3000)
        val started = System.nanoTime()
        val r = extract(big)
        val ms = (System.nanoTime() - started) / 1_000_000
        println("extracted from ${big.length} chars in $ms ms")
        assertTrue("took $ms ms", ms < 20_000)
        assertNull(r.candidates.firstOrNull { it.due == null && it.fog == null })
    }

    @Test fun garbageInputIsHarmless() {
        val rnd = java.util.Random(5)
        val chars = "abcdefghij 0123456789:/.-,\n٢​>OnNovMonFri"
        repeat(1500) {
            val s = buildString { repeat(rnd.nextInt(120)) { append(chars[rnd.nextInt(chars.length)]) } }
            val r = extract(s)
            for (c in r.candidates) assertTrue(c.confidence in 0.0..1.0)
        }
    }
}
