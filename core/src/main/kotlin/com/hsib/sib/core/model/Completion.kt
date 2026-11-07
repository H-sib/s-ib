package com.hsib.sib.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Marking a deadline complete, kept free of Android so it can be tested.
 *
 * A repeating deadline is never closed: it moves on to its next date. That is a big jump (a birthday 21 days away becomes one a year
 * and 21 days away), so the person is told what will happen before they confirm, and a small [UndoRecord] is kept so it can be put back.
 */
object Completion {
    /** How long a completion can be undone from the deadline page. */
    const val UNDO_DAYS = 30L

    /** The date a repeating deadline moves to when it is marked complete, or null when it does not repeat. */
    fun nextDate(o: Obligation): LocalDate? {
        val anchor = o.repeatAnchor ?: return null
        val due = o.due ?: return null
        if (o.repeat == Repeat.NONE) return null
        return Recurrence.nextOnOrAfter(anchor, o.repeat, due.local.toLocalDate().plusDays(1))
    }

    /** What is needed to put the deadline back the way it was just before it was marked complete. */
    fun recordFor(o: Obligation, nowMs: Long): UndoRecord {
        val next = nextDate(o)
        return UndoRecord(nowMs, next != null, o.due, o.postponedUntil, next?.atTime(o.due?.local?.toLocalTime() ?: java.time.LocalTime.MIDNIGHT))
    }
}

/**
 * Kept on the phone for [Completion.UNDO_DAYS] days after a deadline is marked complete.
 * For a repeating deadline it holds the date it had before, and the date it moved to, so undo does not overwrite a date the person set later.
 */
data class UndoRecord(
    val atMs: Long,
    /** True when a repeating deadline moved on; false when the deadline was closed. */
    val advanced: Boolean,
    val previousDue: DueSpec?,
    val previousPostponed: LocalDate?,
    val newDue: LocalDateTime?,
) {
    fun isFresh(nowMs: Long): Boolean = nowMs - atMs in 0..Completion.UNDO_DAYS * 86_400_000L

    fun encode(): String = listOf(
        "1", atMs.toString(), if (advanced) "1" else "0",
        previousDue?.local?.toString().orEmpty(), previousDue?.zone?.id.orEmpty(), previousDue?.precision?.name.orEmpty(), if (previousDue?.floating == true) "1" else "0",
        previousPostponed?.toString().orEmpty(), newDue?.toString().orEmpty(),
    ).joinToString("|")

    companion object {
        /** Reads a stored record; anything that does not read cleanly gives null, so a damaged record can never change a deadline. */
        fun decode(text: String?): UndoRecord? = runCatching {
            val p = (text ?: return null).split("|")
            if (p.size != 9 || p[0] != "1") return null
            val due = if (p[3].isEmpty()) null else DueSpec(LocalDateTime.parse(p[3]), ZoneId.of(p[4]), Precision.valueOf(p[5]), p[6] == "1")
            UndoRecord(
                p[1].toLong(), p[2] == "1", due,
                p[7].takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                p[8].takeIf { it.isNotEmpty() }?.let(LocalDateTime::parse),
            )
        }.getOrNull()
    }
}
