package com.hsib.sib.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

enum class Importance(val weight: Int) { LOW(1), NORMAL(2), HIGH(3), CRITICAL(4) }

enum class Kind { DEADLINE, EVENT, OPEN_ENDED }

enum class Lifecycle { ACTIVE, COMPLETED, CANCELLED }

enum class Category(val label: String, val close: Boolean = false, val medical: Boolean = false) {
    ASSIGNMENT("Assignment"),
    EXAM("Exam"),
    APPLICATION("Application"),
    MEETING("Meeting"),
    BILL("Bill or renewal"),
    APPOINTMENT("Appointment"),
    PERSONAL("Personal"),
    OTHER("Other"),
    ANNIVERSARY("Anniversary", true),
    BIRTHDAY("Birthday", true),
    DATE_NIGHT("Date night", true),
    CHECK_IN("Check in", true),
    GIFT("Gift", true),
    TRIP("Trip", true),
    SPECIAL("Something special", true),
    ITEM("Item (class test)", medical = true),
    CARD("Card exam", medical = true),
    TERM("Term exam", medical = true),
    PROF("Prof exam", medical = true),
    PRACTICAL("Practical", medical = true),
    WARD_FINAL("Ward final", medical = true),
    SUPPLEMENTARY("Supplementary exam", medical = true),
    REPORT("Report or log book", medical = true),
    ;

    companion object {
        val work: List<Category> get() = values().filter { !it.close && !it.medical }
        val medical: List<Category> get() = values().filter { it.medical }
        val closeOnes: List<Category> get() = values().filter { it.close }
    }
}

/** WORK is the main app. CLOSE is the private space for partners and the people who matter most. */
enum class Space { WORK, CLOSE }

/** How an occasion comes round again. Dates are computed from the original day so they never drift. */
enum class Repeat(val label: String) { NONE("Once"), WEEKLY("Every week"), FORTNIGHTLY("Every two weeks"), MONTHLY("Every month"), YEARLY("Every year") }

enum class Precision { DATE_ONLY, DATE_TIME }

/**
 * A due moment exactly as it was stated: a local date-time plus the zone it was stated in.
 * The device zone can change; the stated fact ("11:59 PM in the institution's zone") does not.
 */
data class DueSpec(
    val local: LocalDateTime,
    val zone: ZoneId,
    val precision: Precision,
    val floating: Boolean = false,
) {
    /** Real instant of the deadline. Date-only deadlines end at the last second of that day. */
    fun instant(deviceZone: ZoneId): Instant {
        val z = if (floating) deviceZone else zone
        val ldt = if (precision == Precision.DATE_ONLY) local.toLocalDate().atTime(23, 59, 59) else local
        return ldt.atZone(z).toInstant()
    }

    /** The calendar day, in the device zone, on which the deadline falls. */
    fun dayIn(deviceZone: ZoneId): LocalDate = instant(deviceZone).atZone(deviceZone).toLocalDate()
}

/** A tentative window ("the exam is somewhere in the week of 16 November"). Plans anchor to [earliest]. */
data class FogWindow(val earliest: LocalDate, val latest: LocalDate) {
    init { require(!latest.isBefore(earliest)) { "window ends before it starts" } }
    val days: Long get() = java.time.temporal.ChronoUnit.DAYS.between(earliest, latest) + 1
}

/** Three point effort estimate in minutes. */
data class Effort(val optimisticMin: Int, val likelyMin: Int, val pessimisticMin: Int) {
    init {
        require(optimisticMin >= 0 && optimisticMin <= likelyMin && likelyMin <= pessimisticMin) {
            "effort must satisfy 0 <= optimistic <= likely <= pessimistic"
        }
    }

    /** PERT expected value. */
    val expectedMin: Int get() = ((optimisticMin + 4.0 * likelyMin + pessimisticMin) / 6.0).roundToInt()

    /** Conservative figure used for "latest safe start". */
    val safeMin: Int get() = pessimisticMin

    fun scaled(factor: Double) = Effort(
        (optimisticMin * factor).roundToInt(),
        (likelyMin * factor).roundToInt(),
        (pessimisticMin * factor).roundToInt(),
    )

    companion object {
        fun fromLikely(minutes: Int): Effort =
            Effort((minutes * 0.7).roundToInt(), minutes, (minutes * 1.6).roundToInt())
    }
}

data class Obligation(
    val id: String,
    val title: String,
    val notes: String = "",
    val category: Category = Category.OTHER,
    val kind: Kind = Kind.DEADLINE,
    val due: DueSpec? = null,
    val fog: FogWindow? = null,
    val importance: Importance = Importance.NORMAL,
    val effort: Effort? = null,
    val lifecycle: Lifecycle = Lifecycle.ACTIVE,
    val postponedUntil: LocalDate? = null,
    val progressMin: Int = 0,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val completedAt: Instant? = null,
    val space: Space = Space.WORK,
    val repeat: Repeat = Repeat.NONE,
    /** First occurrence, so that repeats are counted from it and a 31st or a 29 February never drifts. */
    val repeatAnchor: LocalDate? = null,
    /** Days before the date on which to be reminded to prepare, for example 21, 7 and 1. */
    val leadDays: List<Int> = emptyList(),
    val person: String = "",
    val relation: String = "",
    /** For an anniversary: the day it all began. */
    val since: LocalDate? = null,
    /** Smaller pieces of one deadline, for example seven assignments due together. Empty when the deadline is a single piece. */
    val parts: List<Part> = emptyList(),
    /** Minutes the person chose to give on given days. When not empty the planner leaves this deadline alone and uses these. */
    val manualPlan: Map<LocalDate, Int> = emptyMap(),
    /** Files and links the person pointed to. Only the address and a name are kept; the file itself stays where it is. */
    val links: List<FileLink> = emptyList(),
) {
    val partsDone: Int get() = parts.count { it.done }

    /** Remaining expected work in minutes (never negative). */
    fun remainingExpectedMin(): Int = ((effort?.expectedMin ?: 0) - progressMin).coerceAtLeast(0)

    fun remainingSafeMin(): Int = ((effort?.safeMin ?: 0) - progressMin).coerceAtLeast(0)

    /** Day the plan must be done by: the stated due day, or the earliest day of a tentative window. */
    fun planDay(deviceZone: ZoneId): LocalDate? = when {
        due != null && fog == null -> due.dayIn(deviceZone)
        fog != null -> fog.earliest
        due != null -> due.dayIn(deviceZone)
        else -> null
    }
}

/** A file the person chose with the system file picker. H-sib Reminder keeps the address, never a copy. */
data class FileLink(val name: String, val uri: String) {
    companion object {
        fun encode(links: List<FileLink>): String = links.joinToString("\n") { it.name.replace('\t', ' ').replace('\n', ' ') + "\t" + it.uri }

        fun decode(text: String): List<FileLink> =
            if (text.isEmpty()) emptyList() else text.split("\n").filter { '\t' in it }.map { FileLink(it.substringBefore('\t'), it.substringAfter('\t')) }
    }
}

object ManualPlan {
    fun encode(plan: Map<LocalDate, Int>): String = plan.toSortedMap().entries.filter { it.value > 0 }.joinToString(",") { "${it.key}:${it.value}" }

    fun decode(text: String): Map<LocalDate, Int> =
        if (text.isEmpty()) emptyMap() else text.split(",").mapNotNull { e ->
            val d = runCatching { LocalDate.parse(e.substringBefore(':')) }.getOrNull() ?: return@mapNotNull null
            val m = e.substringAfter(':').toIntOrNull() ?: return@mapNotNull null
            d to m
        }.toMap()
}

/** One piece of a deadline, with a name the person can change and a done mark. */
data class Part(val name: String, val done: Boolean = false) {
    companion object {
        /** Stored as one line per part: 0 or 1 for done, a bar, then the name. */
        fun encode(parts: List<Part>): String = parts.joinToString("\n") { (if (it.done) "1|" else "0|") + it.name.replace('\n', ' ') }

        fun decode(text: String): List<Part> =
            if (text.isEmpty()) emptyList() else text.split("\n").map { Part(it.substringAfter('|'), it.startsWith("1")) }
    }
}

enum class SourceType { MANUAL, SHARE_TEXT, SHARE_IMAGE, SHARE_PDF, FILE, SCAN, PASTE, CHAT_EXPORT, ICS, SELECTION, DEMO, NOTIFICATION }

enum class Tier { SUGGEST, MAYBE }

enum class Relation { NEW, UPDATE, CANCEL, DUPLICATE }

/** Things the user should double check. Each maps to a plain language warning in the interface. */
enum class Flag {
    AMBIGUOUS_ORDER,
    MIDNIGHT,
    YEAR_INFERRED,
    MONTH_INFERRED,
    RELATIVE,
    VAGUE,
    TENTATIVE_WORDING,
    NO_TIME,
    AMPM_UNCERTAIN,
    ZONE_ASSUMED,
    WEEKDAY_MISMATCH,
    PAST_DATE,
}

data class Reason(val text: String, val weight: Double)

/** A suggested deadline waiting for the user. It is never a deadline until confirmed. */
data class Candidate(
    val id: String,
    val title: String,
    val category: Category,
    val kind: Kind,
    val due: DueSpec?,
    val fog: FogWindow?,
    val confidence: Double,
    val tier: Tier,
    val relation: Relation,
    val relatedId: String?,
    val reasons: List<Reason>,
    val flags: Set<Flag>,
    val sourceType: SourceType,
    val excerpt: String,
    val alternatives: List<DueSpec> = emptyList(),
    val fingerprint: String = "",
)
