package com.hsib.sib.core.time

import com.hsib.sib.core.model.Flag
import com.hsib.sib.core.model.FogWindow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

enum class DateOrder { DMY, MDY, YMD, AUTO }

/**
 * Everything the tagger needs to turn words into dates. The reference time is when the text was
 * written (the message timestamp for imports), not necessarily "now".
 */
data class TemporalContext(
    val reference: ZonedDateTime,
    val dateOrder: DateOrder = DateOrder.AUTO,
    val autoOrderDefault: DateOrder = DateOrder.DMY,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val weekend: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
) {
    val zone: ZoneId get() = reference.zone
    val today: LocalDate get() = reference.toLocalDate()
    val order: DateOrder get() = if (dateOrder == DateOrder.AUTO) autoOrderDefault else dateOrder
}

enum class MentionKind { POINT, WINDOW, TIME_ONLY }

/** A date or time found in text, resolved to calendar values, with every doubt flagged. */
data class TemporalMention(
    val start: Int,
    val end: Int,
    val text: String,
    val kind: MentionKind,
    val date: LocalDate?,
    val time: LocalTime?,
    val zone: ZoneId?,
    val window: FogWindow?,
    val flags: Set<Flag>,
    val alternatives: List<LocalDateTime> = emptyList(),
    val isPast: Boolean = false,
) {
    val hasTime: Boolean get() = time != null
    val localDateTime: LocalDateTime? get() = if (date != null && time != null) LocalDateTime.of(date, time) else null
}

/**
 * Rule based English temporal tagger.
 *
 * Deliberately deterministic: the same text and context always give the same answer, every
 * ambiguity is flagged rather than guessed silently, and locale behaviour comes from the context.
 * Other languages plug in as additional tagger implementations behind the same output type.
 */
object TemporalTagger {

    // ---- vocabulary ---------------------------------------------------------------------------

    private const val MONTH_WORDS =
        "january|february|march|april|may|june|july|august|september|october|november|december|" +
            "jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec"
    private const val MONTH = "($MONTH_WORDS)\\.?"
    private const val MONTH_BARE = "($MONTH_WORDS)"
    private const val ORD = "(?:st|nd|rd|th)"
    private const val YEAR = "((?:19|20)\\d{2})"
    private const val DASH = "(?:-|\\u2013|\\u2014|to|through|thru|until|till)"
    private const val NUMWORD = "\\d+|a|an|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fourteen|fifteen|twenty|thirty"
    private const val UNIT = "business days?|working days?|weekdays?|days?|weeks?|months?|hours?|hrs?"
    private val I = RegexOption.IGNORE_CASE

    private val rxIso = Regex("\\b((?:19|20)\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})\\b")
    private val rxMonthDay = Regex("\\b$MONTH\\s+(\\d{1,2})$ORD?\\b(?:,?\\s*$YEAR\\b)?", I)
    private val rxDayMonth = Regex("\\b(\\d{1,2})$ORD?(?:\\s+of)?\\s+$MONTH(?:,?\\s*$YEAR\\b)?", I)
    private val rxRangeMonthFirst = Regex("\\b$MONTH\\s+(\\d{1,2})$ORD?\\s*$DASH\\s*(\\d{1,2})$ORD?\\b(?:,?\\s*$YEAR\\b)?", I)
    private val rxRangeDayFirst = Regex("\\b(\\d{1,2})$ORD?\\s*$DASH\\s*(\\d{1,2})$ORD?\\s+$MONTH(?:,?\\s*$YEAR\\b)?", I)
    private val rxNum3 = Regex("\\b(\\d{1,2})([/.\\-])(\\d{1,2})\\2(\\d{4}|\\d{2})\\b")
    private val rxNum2 = Regex("\\b(\\d{1,2})/(\\d{1,2})\\b(?![/\\d:])")
    private val rxNum2Cue = Regex("(?:\\bon|\\bby|\\bbefore|\\buntil|\\btill|\\bdue|\\bdate|\\bdeadline|\\bfrom|\\bto)\\s*:?\\s*$", I)
    // "Black Friday", "Good Friday" and friends are names, not days to resolve.
    private val rxWeekday = Regex(
        "(?<!black\\s)(?<!good\\s)(?<!cyber\\s)(?<!super\\s)(?<!maundy\\s)(?<!holy\\s)(?<!ash\\s)" +
            "\\b(?:(this|next|last|coming|upcoming)\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday|tues|tue|wed|thurs|thur|thu|fri)\\b", I,
    )
    private val rxWeekdayCue = Regex("\\b(?:on|by|this|next|before|until|till)\\s+(mon|sat|sun)\\b", I)
    private val rxWeekdayAbbrDate = Regex("\\b(mon|sat|sun)\\b\\.?,?\\s+(?=(?:$MONTH_WORDS)\\b|\\d{1,2}\\b)", I)
    private val rxDayOfMonth = Regex("\\b(?:on|by|before|until|till|due|of)\\s+(the\\s+(\\d{1,2})$ORD)\\b", I)
    private val rxRelDay = Regex(
        "\\b(the day after tomorrow|day after tomorrow|the day before yesterday|day before yesterday|tomorrow|tmrw|tmr|tonight|today|yesterday)\\b", I,
    )
    private val rxOffsetPrefix = Regex("\\b(in|within|after)\\s+($NUMWORD)\\s+($UNIT)\\b", I)
    private val rxOffsetSuffix = Regex("\\b($NUMWORD)\\s+($UNIT)\\s+(from now|later|ago)\\b", I)
    private val rxPeriodEnd = Regex("\\b(end of (?:the )?(day|week|month|year)|eod|eow|eom|cob|close of business)\\b", I)
    private val rxPeriod = Regex("\\b(this|next|last)\\s+(week|month|year)\\b", I)
    private val rxMonthPart = Regex("\\b(early|mid|late|end of|beginning of|start of)[\\s-]+$MONTH_BARE(?:\\s+$YEAR\\b)?", I)
    private val rxMonthYear = Regex("\\b$MONTH_BARE\\s+$YEAR\\b", I)
    private val rxMonthOnly = Regex("\\b(?:in|during|within|throughout|by|before|until|till)\\s+(?:the\\s+month\\s+of\\s+)?($MONTH_WORDS)(?:\\s+$YEAR)?\\b(?!\\s*\\d)", I)

    private val rxTime12 = Regex("\\b(\\d{1,2})(?:[:.](\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)(?![a-z])", I)
    private val rxTime24 = Regex("(?<![\\d:.])([01]?\\d|2[0-3]):([0-5]\\d)(?::[0-5]\\d)?(?!\\s*(?:a\\.?m|p\\.?m))(?![\\d:])", I)
    private val rxTimeHrs = Regex("\\b([01]\\d|2[0-3])([0-5]\\d)\\s*(?:hrs|hours|h)\\b", I)
    private val rxTimeWord = Regex("\\b(noon|midday|midnight)\\b", I)
    private val rxOclock = Regex("\\b(\\d{1,2})\\s*o'?clock\\b", I)
    private val rxZoneOffset = Regex("\\b(?:UTC|GMT)(?:\\s*([+\\-\\u2212])\\s*(\\d{1,2})(?::?(\\d{2}))?)?(?![A-Za-z])")
    private val rxZoneAbbr = Regex("\\b(EST|EDT|CST|CDT|MST|MDT|PST|PDT|CET|CEST|BST|EET|EEST|JST|AEST|AEDT|NZST|NZDT|HST|AKST)\\b")

    private val dateTimeGap = Regex("^(?:[\\s,;:@()\\-]|\\u2013|\\u2014|\\bat\\b|\\bon\\b|\\bby\\b|\\bbefore\\b|\\buntil\\b|\\btill\\b|\\bthe\\b|\\bof\\b|\\bdue\\b|\\bcutoff\\b)*$", I)
    private val zoneGap = Regex("^[\\s(,]*$")
    private val labelGap = Regex("^[\\s,;|\\n]*(?:time|timing|starts?|starting|at|by|cutoff|deadline)?[\\s:\\-]*$", I)
    private val rangeGap = Regex("^\\s*(?:-|\\u2013|\\u2014|to|through|thru|until|till)\\s*$", I)
    private val betweenGap = Regex("^\\s*and\\s*$", I)
    private val weekOfBefore = Regex("week\\s+of\\s*$", I)
    private val betweenBefore = Regex("\\b(?:between|from)\\s*$", I)
    private val weekdayGap = Regex("^[\\s,.]*$")

    // ---- atoms --------------------------------------------------------------------------------

    private class Res(
        val date: LocalDate? = null,
        val time: LocalTime? = null,
        val window: FogWindow? = null,
        val flags: Set<Flag> = emptySet(),
        val altDates: List<LocalDate> = emptyList(),
    )

    private sealed class Atom(val start: Int, val end: Int)

    private class DateAtom(
        start: Int,
        end: Int,
        val tag: Tag,
        val dow: DayOfWeek? = null,
        val resolve: (TemporalContext) -> Res?,
    ) : Atom(start, end) {
        enum class Tag { ABS, WEEKDAY, OTHER }
    }

    private class TimeAtom(start: Int, end: Int, val time: LocalTime, val flags: Set<Flag>, val midnightWord: Boolean) : Atom(start, end)

    private class ZoneAtom(start: Int, end: Int, val zone: ZoneId) : Atom(start, end)

    // ---- public API ---------------------------------------------------------------------------

    fun tag(text: String, ctx: TemporalContext): List<TemporalMention> {
        val atoms = scan(text)
        val used = BooleanArray(atoms.size)
        val mentions = mutableListOf<TemporalMention>()

        // A weekday immediately before an absolute date is part of that date ("Fri, Nov 20").
        val weekdayFor = HashMap<Int, Int>()
        for (i in 0 until atoms.size - 1) {
            val a = atoms[i]
            val b = atoms[i + 1]
            if (a is DateAtom && a.tag == DateAtom.Tag.WEEKDAY && b is DateAtom && b.tag == DateAtom.Tag.ABS &&
                weekdayGap.matches(text.substring(a.end, b.start))
            ) {
                weekdayFor[i + 1] = i
                used[i] = true
            }
        }

        for (i in atoms.indices) {
            val a = atoms[i]
            if (a !is DateAtom || (used[i] && !weekdayFor.containsKey(i))) continue
            used[i] = true
            val r = a.resolve(ctx) ?: continue
            var start = a.start
            var end = a.end
            val flags = r.flags.toMutableSet()
            val date = r.date
            var timeAtom: TimeAtom? = null
            var zoneAtom: ZoneAtom? = null

            weekdayFor[i]?.let { wi ->
                val w = atoms[wi] as DateAtom
                start = minOf(start, w.start)
                if (date != null && w.dow != null && date.dayOfWeek != w.dow) flags += Flag.WEEKDAY_MISMATCH
            }

            if (r.window == null && r.time == null) {
                // Time after the date: "Nov 18 at 11:59 PM EST"
                val n1 = atoms.getOrNull(i + 1)
                if (n1 is TimeAtom && !used[i + 1] && dateTimeGap.matches(text.substring(a.end, n1.start)) && text.substring(a.end, n1.start).length <= 14) {
                    timeAtom = n1
                    used[i + 1] = true
                    end = n1.end
                    val n2 = atoms.getOrNull(i + 2)
                    if (n2 is ZoneAtom && !used[i + 2] && zoneGap.matches(text.substring(n1.end, n2.start))) {
                        zoneAtom = n2
                        used[i + 2] = true
                        end = n2.end
                    }
                } else {
                    // Time before the date: "11:59 PM EST on Nov 18"
                    val p1 = atoms.getOrNull(i - 1)
                    val p2 = atoms.getOrNull(i - 2)
                    if (p1 is TimeAtom && !used[i - 1] && dateTimeGap.matches(text.substring(p1.end, start)) && text.substring(p1.end, start).length <= 14) {
                        timeAtom = p1
                        used[i - 1] = true
                        start = p1.start
                    } else if (p1 is ZoneAtom && p2 is TimeAtom && !used[i - 1] && !used[i - 2] &&
                        zoneGap.matches(text.substring(p2.end, p1.start)) &&
                        dateTimeGap.matches(text.substring(p1.end, start)) && text.substring(p1.end, start).length <= 14
                    ) {
                        timeAtom = p2
                        zoneAtom = p1
                        used[i - 1] = true
                        used[i - 2] = true
                        start = p2.start
                    }
                }
            }
            mentions += build(text, start, end, r, date, timeAtom, zoneAtom, flags, ctx)
        }

        // Remaining times: attach to a preceding date with a label ("Date: Nov 18 / Time: 11:59 PM"), else stand alone.
        for (i in atoms.indices) {
            val a = atoms[i]
            if (a !is TimeAtom || used[i]) continue
            used[i] = true
            var zone: ZoneAtom? = null
            var end = a.end
            val nxt = atoms.getOrNull(i + 1)
            if (nxt is ZoneAtom && !used[i + 1] && zoneGap.matches(text.substring(a.end, nxt.start))) {
                zone = nxt
                used[i + 1] = true
                end = nxt.end
            }
            val prevIdx = mentions.indexOfLast { it.kind == MentionKind.POINT && it.time == null && it.end <= a.start }
            val prev = mentions.getOrNull(prevIdx)
            if (prev != null && a.start - prev.end <= 40 && labelGap.matches(text.substring(prev.end, a.start))) {
                mentions[prevIdx] = rebuildWithTime(text, prev, a, zone, end, ctx)
            } else {
                mentions += TemporalMention(
                    start = a.start, end = end, text = text.substring(a.start, end), kind = MentionKind.TIME_ONLY,
                    date = null, time = a.time, zone = zone?.zone, window = null, flags = a.flags,
                )
            }
        }

        // Stray zones attach to the nearest preceding timed mention without one.
        for (i in atoms.indices) {
            val a = atoms[i]
            if (a !is ZoneAtom || used[i]) continue
            val idx = mentions.indexOfLast { it.end <= a.start && it.time != null && it.zone == null }
            val m = mentions.getOrNull(idx) ?: continue
            if (zoneGap.matches(text.substring(m.end, a.start))) {
                used[i] = true
                mentions[idx] = m.copy(end = a.end, text = text.substring(m.start, a.end), zone = a.zone)
            }
        }

        val sorted = mentions.sortedBy { it.start }
        return mergeRanges(text, sorted, ctx)
    }

    // ---- building mentions --------------------------------------------------------------------

    private fun build(
        text: String,
        start: Int,
        end: Int,
        r: Res,
        dateIn: LocalDate?,
        timeAtom: TimeAtom?,
        zoneAtom: ZoneAtom?,
        flagsIn: Set<Flag>,
        ctx: TemporalContext,
    ): TemporalMention {
        val flags = flagsIn.toMutableSet()
        if (r.window != null) {
            val past = r.window.latest.isBefore(ctx.today)
            if (past) flags += Flag.PAST_DATE
            return TemporalMention(start, end, text.substring(start, end), MentionKind.WINDOW, null, null, null, r.window, flags, isPast = past)
        }
        var date = dateIn
        var time: LocalTime? = r.time ?: timeAtom?.time
        timeAtom?.let { flags += it.flags }
        val alts = mutableListOf<LocalDateTime>()
        if (date != null && timeAtom != null && Flag.MIDNIGHT in timeAtom.flags && r.time == null) {
            // Midnight belongs to two days. Spoken "midnight" usually ends the stated day; "12 AM" or "00:00" starts it.
            val startOfDay = LocalDateTime.of(date, LocalTime.MIDNIGHT)
            val endOfDay = LocalDateTime.of(date.plusDays(1), LocalTime.MIDNIGHT)
            if (timeAtom.midnightWord) {
                alts += startOfDay
                date = date.plusDays(1)
            } else {
                alts += endOfDay
            }
        }
        if (r.altDates.isNotEmpty() && date != null) {
            for (d in r.altDates) alts += LocalDateTime.of(d, time ?: LocalTime.of(23, 59))
        }
        if (date != null && time == null) flags += Flag.NO_TIME
        if (time != null && zoneAtom == null) flags += Flag.ZONE_ASSUMED
        val zone = zoneAtom?.zone
        val past = when {
            date == null -> false
            time != null -> ZonedDateTime.of(LocalDateTime.of(date, time), zone ?: ctx.zone).toInstant().isBefore(ctx.reference.toInstant())
            else -> date.isBefore(ctx.today)
        }
        if (past) flags += Flag.PAST_DATE
        return TemporalMention(start, end, text.substring(start, end), MentionKind.POINT, date, time, zone, null, flags, alts, past)
    }

    private fun rebuildWithTime(text: String, m: TemporalMention, t: TimeAtom, z: ZoneAtom?, end: Int, ctx: TemporalContext): TemporalMention {
        val flags = (m.flags - Flag.NO_TIME + t.flags).toMutableSet()
        var date = m.date
        val alts = m.alternatives.toMutableList()
        if (date != null && Flag.MIDNIGHT in t.flags) {
            val startOfDay = LocalDateTime.of(date, LocalTime.MIDNIGHT)
            val endOfDay = LocalDateTime.of(date.plusDays(1), LocalTime.MIDNIGHT)
            if (t.midnightWord) {
                alts += startOfDay
                date = date.plusDays(1)
            } else {
                alts += endOfDay
            }
        }
        if (z == null) flags += Flag.ZONE_ASSUMED else flags -= Flag.ZONE_ASSUMED
        val zone = z?.zone
        val past = date != null && ZonedDateTime.of(LocalDateTime.of(date, t.time), zone ?: ctx.zone).toInstant().isBefore(ctx.reference.toInstant())
        if (past) flags += Flag.PAST_DATE else flags -= Flag.PAST_DATE
        return m.copy(end = end, text = text.substring(m.start, end), date = date, time = t.time, zone = zone, flags = flags, alternatives = alts, isPast = past)
    }

    /** Joins "week of X" into a window and "X to Y" into a window. */
    private fun mergeRanges(text: String, list: List<TemporalMention>, ctx: TemporalContext): List<TemporalMention> {
        val out = mutableListOf<TemporalMention>()
        var i = 0
        while (i < list.size) {
            var m = list[i]
            val md = m.date
            if (m.kind == MentionKind.POINT && md != null) {
                val before = text.substring(maxOf(0, m.start - 10), m.start)
                val wk = weekOfBefore.find(before)
                if (wk != null) {
                    val ns = m.start - (before.length - wk.range.first)
                    m = m.copy(
                        start = ns, text = text.substring(ns, m.end), kind = MentionKind.WINDOW,
                        window = FogWindow(md, md.plusDays(6)), date = null, time = null, flags = m.flags + Flag.VAGUE - Flag.NO_TIME,
                    )
                    out += m
                    i++
                    continue
                }
                val next = list.getOrNull(i + 1)
                val nd = next?.date
                if (next != null && next.kind == MentionKind.POINT && nd != null && !nd.isBefore(md)) {
                    val gap = text.substring(m.end, next.start)
                    val beforeText = text.substring(maxOf(0, m.start - 12), m.start)
                    val isRange = rangeGap.matches(gap) || (betweenGap.matches(gap) && betweenBefore.containsMatchIn(beforeText))
                    if (isRange && m.time == null && next.time == null) {
                        out += TemporalMention(
                            start = m.start, end = next.end, text = text.substring(m.start, next.end), kind = MentionKind.WINDOW,
                            date = null, time = null, zone = null, window = FogWindow(md, nd),
                            flags = (m.flags + next.flags - Flag.NO_TIME), isPast = nd.isBefore(ctx.today),
                        )
                        i += 2
                        continue
                    }
                }
            }
            out += m
            i++
        }
        return out
    }

    // ---- scanning -----------------------------------------------------------------------------

    private data class Raw(val start: Int, val end: Int, val priority: Int, val atom: Atom)

    private fun scan(text: String): List<Atom> {
        val raws = mutableListOf<Raw>()
        fun add(a: Atom, priority: Int = 0) { raws += Raw(a.start, a.end, priority, a) }

        rxIso.findAll(text).forEach { m ->
            val (y, mo, d) = m.destructured
            add(DateAtom(m.range.first, m.range.last + 1, DateAtom.Tag.ABS) { _ -> absolute(y.toInt(), mo.toInt(), d.toInt()) })
        }
        rxRangeMonthFirst.findAll(text).forEach { m ->
            val month = monthNumber(m.groupValues[1]); val d1 = m.groupValues[2].toInt(); val d2 = m.groupValues[3].toInt()
            val year = m.groupValues[4].takeIf { it.isNotEmpty() }?.toInt()
            add(DateAtom(m.range.first, m.range.last + 1, DateAtom.Tag.OTHER) { ctx -> rangeSameMonth(month, d1, d2, year, ctx) }, 3)
        }
        rxRangeDayFirst.findAll(text).forEach { m ->
            val d1 = m.groupValues[1].toInt(); val d2 = m.groupValues[2].toInt(); val month = monthNumber(m.groupValues[3])
            val year = m.groupValues[4].takeIf { it.isNotEmpty() }?.toInt()
            add(DateAtom(m.range.first, m.range.last + 1, DateAtom.Tag.OTHER) { ctx -> rangeSameMonth(month, d1, d2, year, ctx) }, 3)
        }
        rxMonthDay.findAll(text).forEach { m ->
            val month = monthNumber(m.groupValues[1]); val day = m.groupValues[2].toInt()
            val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            add(DateAtom(m.range.first, m.range.last + 1, DateAtom.Tag.ABS) { ctx -> monthDay(month, day, year, ctx) }, 2)
        }
        rxDayMonth.findAll(text).forEach { m ->
            val day = m.groupValues[1].toInt(); val month = monthNumber(m.groupValues[2])
            val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            add(DateAtom(m.range.first, m.range.last + 1, DateAtom.Tag.ABS) { ctx -> monthDay(month, day, year, ctx) }, 2)
        }
        rxNum3.findAll(text).forEach { m ->
            val a = m.groupValues[1].toInt(); val b = m.groupValues[3].toInt(); val cs = m.groupValues[4]
            val year = if (cs.length == 2) 2000 + cs.toInt() else cs.toInt()
            add(DateAtom(m.range.first, m.range.last + 1, DateAtom.Tag.ABS) { ctx -> numeric(a, b, year, ctx) }, 1)
        }
        rxNum2.findAll(text).forEach { m ->
            val before = text.substring(maxOf(0, m.range.first - 16), m.range.first)
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
            if (a > 12 || b > 12 || rxNum2Cue.containsMatchIn(before)) {
                add(DateAtom(m.range.first, m.range.last + 1, DateAtom.Tag.ABS) { ctx -> numeric(a, b, null, ctx) }, 0)
            }
        }
        rxWeekday.findAll(text).forEach { m ->
            val qual = m.groupValues[1].lowercase().ifEmpty { null }
            val dow = weekdayOf(m.groupValues[2])
            add(DateAtom(m.range.first, m.range.last + 1, DateAtom.Tag.WEEKDAY, dow) { ctx -> weekday(dow, qual, ctx) })
        }
        rxWeekdayCue.findAll(text).forEach { m ->
            val g = m.groups[1]!!
            val dow = weekdayOf(g.value)
            add(DateAtom(g.range.first, g.range.last + 1, DateAtom.Tag.WEEKDAY, dow) { ctx -> weekday(dow, null, ctx) })
        }
        rxWeekdayAbbrDate.findAll(text).forEach { m ->
            val g = m.groups[1]!!
            val dow = weekdayOf(g.value)
            add(DateAtom(g.range.first, g.range.last + 1, DateAtom.Tag.WEEKDAY, dow) { ctx -> weekday(dow, null, ctx) })
        }
        rxDayOfMonth.findAll(text).forEach { m ->
            val g = m.groups[1]!!
            val day = m.groupValues[2].toInt()
            add(DateAtom(g.range.first, g.range.last + 1, DateAtom.Tag.OTHER) { ctx -> dayOfMonth(day, ctx) })
        }
        rxRelDay.findAll(text).forEach { m ->
            val w = m.groupValues[1].lowercase().removePrefix("the ")
            add(DateAtom(m.range.first, m.range.last + 1, DateAtom.Tag.OTHER) { ctx -> relativeDay(w, ctx) })
        }
        rxOffsetPrefix.findAll(text).forEach { m ->
            val kind = m.groupValues[1].lowercase(); val n = numberOf(m.groupValues[2]); val unit = m.groupValues[3].lowercase()
            add(DateAtom(m.range.first, m.range.last + 1, DateAtom.Tag.OTHER) { ctx -> offset(n, unit, kind == "within", false, ctx) }, 1)
        }
        rxOffsetSuffix.findAll(text).forEach { m ->
            val n = numberOf(m.groupValues[1]); val unit = m.groupValues[2].lowercase(); val dir = m.groupValues[3].lowercase()
            add(DateAtom(m.range.first, m.range.last + 1, DateAtom.Tag.OTHER) { ctx -> offset(n, unit, false, dir == "ago", ctx) }, 1)
        }
        rxPeriodEnd.findAll(text).forEach { m ->
            val w = m.groupValues[1].lowercase()
            add(DateAtom(m.range.first, m.range.last + 1, DateAtom.Tag.OTHER) { ctx -> periodEnd(w, ctx) })
        }
        rxPeriod.findAll(text).forEach { m ->
            val which = m.groupValues[1].lowercase(); val unit = m.groupValues[2].lowercase()
            add(DateAtom(m.range.first, m.range.last + 1, DateAtom.Tag.OTHER) { ctx -> period(which, unit, ctx) })
        }
        rxMonthPart.findAll(text).forEach { m ->
            val part = m.groupValues[1].lowercase(); val month = monthNumber(m.groupValues[2])
            val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            add(DateAtom(m.range.first, m.range.last + 1, DateAtom.Tag.OTHER) { ctx -> monthPart(part, month, year, ctx) }, 2)
        }
        rxMonthYear.findAll(text).forEach { m ->
            val month = monthNumber(m.groupValues[1]); val year = m.groupValues[2].toInt()
            add(DateAtom(m.range.first, m.range.last + 1, DateAtom.Tag.OTHER) { _ -> wholeMonth(month, year) })
        }
        rxMonthOnly.findAll(text).forEach { m ->
            val g = m.groups[1]!!
            val month = monthNumber(g.value)
            val year = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt()
            val endPos = (m.groups[2]?.range?.last ?: g.range.last) + 1
            add(DateAtom(g.range.first, endPos, DateAtom.Tag.OTHER) { ctx -> monthOnly(month, year, ctx) })
        }

        rxTime12.findAll(text).forEach { m ->
            val h = m.groupValues[1].toInt(); val min = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            val pm = m.groupValues[3].lowercase().startsWith("p")
            if (h in 1..12 && min in 0..59) {
                val hour = when { h == 12 && !pm -> 0; h == 12 -> 12; pm -> h + 12; else -> h }
                val flags = if (h == 12 && !pm && min == 0) setOf(Flag.MIDNIGHT) else emptySet()
                add(TimeAtom(m.range.first, m.range.last + 1, LocalTime.of(hour, min), flags, false), 1)
            }
        }
        rxTime24.findAll(text).forEach { m ->
            val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
            val flags = mutableSetOf<Flag>()
            if (h == 0 && min == 0) flags += Flag.MIDNIGHT
            if (h in 1..12 && !m.groupValues[1].startsWith("0")) flags += Flag.AMPM_UNCERTAIN
            add(TimeAtom(m.range.first, m.range.last + 1, LocalTime.of(h, min), flags, false))
        }
        rxTimeHrs.findAll(text).forEach { m ->
            add(TimeAtom(m.range.first, m.range.last + 1, LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()), emptySet(), false), 1)
        }
        rxTimeWord.findAll(text).forEach { m ->
            when (m.groupValues[1].lowercase()) {
                "midnight" -> add(TimeAtom(m.range.first, m.range.last + 1, LocalTime.MIDNIGHT, setOf(Flag.MIDNIGHT), true))
                else -> add(TimeAtom(m.range.first, m.range.last + 1, LocalTime.NOON, emptySet(), false))
            }
        }
        rxOclock.findAll(text).forEach { m ->
            val h = m.groupValues[1].toInt()
            if (h in 1..12) add(TimeAtom(m.range.first, m.range.last + 1, LocalTime.of(h, 0), setOf(Flag.AMPM_UNCERTAIN), false))
        }
        rxZoneOffset.findAll(text).forEach { m ->
            val sign = m.groupValues[1]
            val zone: ZoneId = if (sign.isEmpty()) ZoneOffset.UTC else {
                val hh = m.groupValues[2].toInt(); val mm = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 0
                if (hh > 18 || mm > 59) return@forEach
                val total = hh * 3600 + mm * 60
                ZoneOffset.ofTotalSeconds(if (sign == "-" || sign == "\u2212") -total else total)
            }
            add(ZoneAtom(m.range.first, m.range.last + 1, zone))
        }
        rxZoneAbbr.findAll(text).forEach { m ->
            zoneOfAbbr(m.groupValues[1])?.let { add(ZoneAtom(m.range.first, m.range.last + 1, it)) }
        }

        // Keep the earliest, then longest, then highest priority match; drop anything overlapping a kept one.
        val sorted = raws.sortedWith(compareBy<Raw> { it.start }.thenByDescending { it.end - it.start }.thenByDescending { it.priority })
        val kept = mutableListOf<Raw>()
        var lastEnd = -1
        for (r in sorted) {
            if (r.start >= lastEnd) {
                kept += r
                lastEnd = r.end
            }
        }
        return kept.map { it.atom }
    }

    // ---- resolution helpers -------------------------------------------------------------------

    private fun absolute(y: Int, m: Int, d: Int): Res? {
        val date = runCatching { LocalDate.of(y, m, d) }.getOrNull() ?: return null
        return Res(date = date)
    }

    /** Picks the year for a date written without one: the nearest sensible occurrence. */
    private fun inferYear(m: Int, d: Int, ctx: TemporalContext): LocalDate? {
        val y = ctx.today.year
        val thisYear = runCatching { LocalDate.of(y, m, d) }.getOrNull()
        val nextYear = runCatching { LocalDate.of(y + 1, m, d) }.getOrNull()
        val base = thisYear ?: nextYear ?: return null
        return if (base.isBefore(ctx.today.minusDays(90)) && nextYear != null) nextYear else base
    }

    private fun monthDay(m: Int, d: Int, year: Int?, ctx: TemporalContext): Res? {
        if (year != null) return absolute(year, m, d)
        val date = inferYear(m, d, ctx) ?: return null
        return Res(date = date, flags = setOf(Flag.YEAR_INFERRED))
    }

    private fun numeric(a: Int, b: Int, year: Int?, ctx: TemporalContext): Res? {
        fun make(day: Int, month: Int): LocalDate? =
            if (year != null) runCatching { LocalDate.of(year, month, day) }.getOrNull() else inferYear(month, day, ctx)
        val dmy = if (a in 1..31 && b in 1..12) make(a, b) else null
        val mdy = if (b in 1..31 && a in 1..12) make(b, a) else null
        val inferred = if (year == null) setOf(Flag.YEAR_INFERRED) else emptySet()
        return when {
            dmy != null && mdy != null && dmy != mdy -> {
                val prefersMdy = ctx.order == DateOrder.MDY
                val primary = if (prefersMdy) mdy else dmy
                val other = if (prefersMdy) dmy else mdy
                Res(date = primary, flags = inferred + Flag.AMBIGUOUS_ORDER, altDates = listOf(other))
            }
            dmy != null -> Res(date = dmy, flags = inferred)
            mdy != null -> Res(date = mdy, flags = inferred)
            else -> null
        }
    }

    private fun rangeSameMonth(month: Int, d1: Int, d2: Int, year: Int?, ctx: TemporalContext): Res? {
        if (d2 < d1) return null
        val first = monthDay(month, d1, year, ctx) ?: return null
        val start = first.date ?: return null
        val end = runCatching { start.withDayOfMonth(d2) }.getOrNull() ?: return null
        return Res(window = FogWindow(start, end), flags = first.flags)
    }

    private fun weekdayOf(s: String): DayOfWeek = when (s.lowercase().take(3)) {
        "mon" -> DayOfWeek.MONDAY
        "tue" -> DayOfWeek.TUESDAY
        "wed" -> DayOfWeek.WEDNESDAY
        "thu" -> DayOfWeek.THURSDAY
        "fri" -> DayOfWeek.FRIDAY
        "sat" -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    private fun weekday(dow: DayOfWeek, qual: String?, ctx: TemporalContext): Res {
        val today = ctx.today
        val date = when (qual) {
            "last" -> today.with(TemporalAdjusters.previous(dow))
            "next" -> {
                val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(ctx.weekStart))
                thisWeekStart.plusWeeks(1).with(TemporalAdjusters.nextOrSame(dow))
            }
            else -> today.with(TemporalAdjusters.nextOrSame(dow))
        }
        return Res(date = date, flags = setOf(Flag.RELATIVE))
    }

    private fun dayOfMonth(day: Int, ctx: TemporalContext): Res? {
        if (day !in 1..31) return null
        var ym = YearMonth.from(ctx.today)
        repeat(13) {
            if (day <= ym.lengthOfMonth()) {
                val d = ym.atDay(day)
                if (!d.isBefore(ctx.today)) return Res(date = d, flags = setOf(Flag.MONTH_INFERRED))
            }
            ym = ym.plusMonths(1)
        }
        return null
    }

    private fun relativeDay(word: String, ctx: TemporalContext): Res {
        val off = when (word) {
            "tomorrow", "tmrw", "tmr" -> 1L
            "day after tomorrow" -> 2L
            "yesterday" -> -1L
            "day before yesterday" -> -2L
            else -> 0L
        }
        return Res(date = ctx.today.plusDays(off), flags = setOf(Flag.RELATIVE))
    }

    private fun numberOf(s: String): Int = when (s.lowercase()) {
        "a", "an", "one" -> 1
        "two" -> 2; "three" -> 3; "four" -> 4; "five" -> 5; "six" -> 6; "seven" -> 7; "eight" -> 8
        "nine" -> 9; "ten" -> 10; "eleven" -> 11; "twelve" -> 12; "fourteen" -> 14; "fifteen" -> 15
        "twenty" -> 20; "thirty" -> 30
        else -> s.toIntOrNull() ?: 0
    }

    private fun offset(n: Int, unit: String, within: Boolean, ago: Boolean, ctx: TemporalContext): Res? {
        if (n <= 0) return null
        val sign = if (ago) -1 else 1
        val today = ctx.today
        val flags = mutableSetOf(Flag.RELATIVE)
        return when {
            unit.startsWith("hour") || unit.startsWith("hr") -> {
                val target = ctx.reference.plusHours((sign * n).toLong())
                Res(date = target.toLocalDate(), time = target.toLocalTime().withSecond(0).withNano(0), flags = flags)
            }
            unit.startsWith("business") || unit.startsWith("working") || unit.startsWith("weekday") -> {
                var d = today
                var left = n
                val step = if (ago) -1L else 1L
                while (left > 0) {
                    d = d.plusDays(step)
                    if (d.dayOfWeek !in ctx.weekend) left--
                }
                Res(date = d, flags = flags)
            }
            unit.startsWith("day") -> Res(date = today.plusDays((sign * n).toLong()), flags = flags)
            unit.startsWith("week") -> Res(date = today.plusWeeks((sign * n).toLong()), flags = flags)
            unit.startsWith("month") -> Res(date = today.plusMonths((sign * n).toLong()), flags = flags + Flag.VAGUE)
            else -> null
        }
    }

    private fun lastWorkingDayOfWeek(ctx: TemporalContext): LocalDate {
        val start = ctx.today.with(TemporalAdjusters.previousOrSame(ctx.weekStart))
        var d = start.plusDays(6)
        while (d.dayOfWeek in ctx.weekend && d.isAfter(start)) d = d.minusDays(1)
        if (d.isBefore(ctx.today)) {
            d = d.plusDays(7)
        }
        return d
    }

    private fun periodEnd(w: String, ctx: TemporalContext): Res {
        val flags = setOf(Flag.RELATIVE)
        return when {
            w == "eod" || w == "cob" || w.startsWith("close") || w.contains("day") -> Res(date = ctx.today, flags = flags)
            w == "eow" || w.contains("week") -> Res(date = lastWorkingDayOfWeek(ctx), flags = flags)
            w == "eom" || w.contains("month") -> Res(date = YearMonth.from(ctx.today).atEndOfMonth(), flags = flags)
            else -> Res(date = LocalDate.of(ctx.today.year, 12, 31), flags = flags)
        }
    }

    private fun period(which: String, unit: String, ctx: TemporalContext): Res {
        val today = ctx.today
        val flags = setOf(Flag.RELATIVE, Flag.VAGUE)
        return when (unit) {
            "week" -> {
                val ws = today.with(TemporalAdjusters.previousOrSame(ctx.weekStart))
                when (which) {
                    "next" -> Res(window = FogWindow(ws.plusWeeks(1), ws.plusWeeks(1).plusDays(6)), flags = flags)
                    "last" -> Res(window = FogWindow(ws.minusWeeks(1), ws.minusWeeks(1).plusDays(6)), flags = flags)
                    else -> Res(window = FogWindow(today, ws.plusDays(6)), flags = flags)
                }
            }
            "month" -> {
                val ym = YearMonth.from(today)
                when (which) {
                    "next" -> Res(window = FogWindow(ym.plusMonths(1).atDay(1), ym.plusMonths(1).atEndOfMonth()), flags = flags)
                    "last" -> Res(window = FogWindow(ym.minusMonths(1).atDay(1), ym.minusMonths(1).atEndOfMonth()), flags = flags)
                    else -> Res(window = FogWindow(today, ym.atEndOfMonth()), flags = flags)
                }
            }
            else -> {
                val y = today.year
                when (which) {
                    "next" -> Res(window = FogWindow(LocalDate.of(y + 1, 1, 1), LocalDate.of(y + 1, 12, 31)), flags = flags)
                    "last" -> Res(window = FogWindow(LocalDate.of(y - 1, 1, 1), LocalDate.of(y - 1, 12, 31)), flags = flags)
                    else -> Res(window = FogWindow(today, LocalDate.of(y, 12, 31)), flags = flags)
                }
            }
        }
    }

    private fun monthYearFor(month: Int, year: Int?, ctx: TemporalContext): YearMonth {
        if (year != null) return YearMonth.of(year, month)
        val thisYear = YearMonth.of(ctx.today.year, month)
        return if (thisYear.atEndOfMonth().isBefore(ctx.today.minusDays(60))) thisYear.plusYears(1) else thisYear
    }

    private fun wholeMonth(month: Int, year: Int): Res {
        val ym = YearMonth.of(year, month)
        return Res(window = FogWindow(ym.atDay(1), ym.atEndOfMonth()), flags = setOf(Flag.VAGUE))
    }

    private fun monthOnly(month: Int, year: Int?, ctx: TemporalContext): Res {
        val ym = monthYearFor(month, year, ctx)
        val flags = if (year == null) setOf(Flag.VAGUE, Flag.YEAR_INFERRED) else setOf(Flag.VAGUE)
        return Res(window = FogWindow(ym.atDay(1), ym.atEndOfMonth()), flags = flags)
    }

    private fun monthPart(part: String, month: Int, year: Int?, ctx: TemporalContext): Res {
        val ym = monthYearFor(month, year, ctx)
        val inferred = if (year == null) setOf(Flag.YEAR_INFERRED) else emptySet()
        return when (part) {
            "end of" -> Res(date = ym.atEndOfMonth(), flags = inferred)
            "early" -> Res(window = FogWindow(ym.atDay(1), ym.atDay(10)), flags = inferred + Flag.VAGUE)
            "mid" -> Res(window = FogWindow(ym.atDay(11), ym.atDay(minOf(20, ym.lengthOfMonth()))), flags = inferred + Flag.VAGUE)
            "late" -> Res(window = FogWindow(ym.atDay(21), ym.atEndOfMonth()), flags = inferred + Flag.VAGUE)
            else -> Res(window = FogWindow(ym.atDay(1), ym.atDay(7)), flags = inferred + Flag.VAGUE)
        }
    }

    private fun monthNumber(name: String): Int = when (name.lowercase().removeSuffix(".").take(3)) {
        "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4; "may" -> 5; "jun" -> 6
        "jul" -> 7; "aug" -> 8; "sep" -> 9; "oct" -> 10; "nov" -> 11; else -> 12
    }

    private fun zoneOfAbbr(a: String): ZoneId? {
        val hours = when (a) {
            "EST" -> -5; "EDT" -> -4; "CST" -> -6; "CDT" -> -5; "MST" -> -7; "MDT" -> -6
            "PST" -> -8; "PDT" -> -7; "AKST" -> -9; "HST" -> -10
            "CET" -> 1; "CEST" -> 2; "BST" -> 1; "EET" -> 2; "EEST" -> 3
            "JST" -> 9; "AEST" -> 10; "AEDT" -> 11; "NZST" -> 12; "NZDT" -> 13
            else -> return null
        }
        return ZoneOffset.ofHours(hours)
    }
}
