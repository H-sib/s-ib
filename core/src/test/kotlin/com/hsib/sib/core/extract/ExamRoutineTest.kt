package com.hsib.sib.core.extract

import com.hsib.sib.core.model.Candidate
import com.hsib.sib.core.model.Category
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Kind
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.model.Relation
import com.hsib.sib.core.model.SourceType
import com.hsib.sib.core.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ExamRoutineTest {
    private fun exam(i: Int, title: String = "Subject $i exam", category: Category = Category.EXAM) = Candidate(
        "c$i", title, category, Kind.DEADLINE,
        DueSpec(LocalDate.of(2026, 11, 20 - i).atStartOfDay(), ZoneId.of("UTC"), Precision.DATE_ONLY), null,
        0.9, Tier.SUGGEST, Relation.NEW, null, emptyList(), emptySet(), SourceType.SCAN, "",
    )

    @Test fun sevenExamsFormARoutineInDateOrder() {
        val found = ExamRoutine.find((1..7).map { exam(it) })
        assertEquals(7, found.size)
        assertTrue(found.zipWithNext().all { (a, b) -> !a.due!!.local.isAfter(b.due!!.local) })
    }

    @Test fun twoExamsAreNotARoutine() {
        assertTrue(ExamRoutine.find((1..2).map { exam(it) }).isEmpty())
    }

    @Test fun otherKindsOfDeadlineAreIgnored() {
        val mixed = (1..3).map { exam(it) } + exam(9, "Pay the fee", Category.BILL)
        assertEquals(3, ExamRoutine.find(mixed).size)
    }
}
