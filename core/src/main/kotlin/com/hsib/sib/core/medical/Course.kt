package com.hsib.sib.core.medical

import com.hsib.sib.core.model.Category
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** A kind of deadline typical for a profession, with the hours it usually takes. [category] is the name of a Category. */
@Serializable
data class WorkType(val name: String, val category: String = "OTHER", val hours: Int = 2)

/** One phase of a course: a year, a semester or a prof. [kinds] holds the names of the exam kinds it uses. */
@Serializable
data class CoursePhase(
    val name: String,
    val examName: String = "",
    val duration: String = "",
    val subjects: List<String> = emptyList(),
    val practicals: List<String> = emptyList(),
    val week: String = "",
    val kinds: List<String> = emptyList(),
    val examMonth: Int = 11,
    val supplementaryMonth: Int = 5,
    /** For a profession: the kinds of deadline in this area. Used instead of the exam kinds. */
    val types: List<WorkType> = emptyList(),
)

/** A whole course. It holds no personal data, so it can be shared as text. */
@Serializable
data class Course(val name: String, val field: String = "Medicine", val phases: List<CoursePhase> = emptyList())

object Courses {
    val FIELDS = listOf("Medicine (MBBS)", "Dental (BDS)", "Nursing", "Engineering", "Law", "Business", "School or college", "Other")

    /** Fields that get the medical colours. */
    /** A job or a way of life rather than a course of study: it uses kinds of deadline with usual hours, not exam kinds. */
    fun isWork(c: Course): Boolean = c.field.startsWith("Work") || c.field.startsWith("Life")

    fun isMedical(c: Course): Boolean = c.field.startsWith("Medicine") || c.field.startsWith("Dental") || c.field.startsWith("Nursing")

    private const val MAX_PHASES = 10
    private const val MAX_LIST = 40
    private const val MAX_TEXT = 80
    const val MAX_CODE_CHARS = 30_000
    const val CODE_PREFIX = "SIB-COURSE-1:"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun clean(s: String, max: Int = MAX_TEXT): String = s.filter { !it.isISOControl() }.trim().take(max)

    /** The kinds a phase is allowed to use, so a shared course can never carry anything unexpected. */
    private val allowedKinds = Category.medical.map { it.name }.toSet()
    private val allowedCategories = Category.values().filter { !it.close }.map { it.name }.toSet()

    /** Limits every length and count, removes control characters, and drops what is not understood. Null when nothing usable is left. */
    fun sanitize(c: Course): Course? {
        val name = clean(c.name, 60)
        if (name.isEmpty()) return null
        val phases = c.phases.take(MAX_PHASES).mapNotNull { p ->
            val pn = clean(p.name, 40)
            if (pn.isEmpty()) return@mapNotNull null
            CoursePhase(
                name = pn, examName = clean(p.examName), duration = clean(p.duration, 40),
                subjects = p.subjects.map { clean(it) }.filter { it.isNotEmpty() }.distinct().take(MAX_LIST),
                practicals = p.practicals.map { clean(it) }.filter { it.isNotEmpty() }.distinct().take(MAX_LIST),
                week = clean(p.week, 160), kinds = p.kinds.filter { it in allowedKinds }.distinct(),
                types = p.types.map { WorkType(clean(it.name, 40), if (it.category in allowedCategories) it.category else "OTHER", it.hours.coerceIn(1, 200)) }.filter { it.name.isNotEmpty() }.distinctBy { it.name }.take(MAX_LIST),
                examMonth = p.examMonth.coerceIn(1, 12), supplementaryMonth = p.supplementaryMonth.coerceIn(1, 12),
            )
        }
        if (phases.isEmpty()) return null
        return Course(name, clean(c.field, 40).ifEmpty { "Other" }, phases)
    }

    fun listToJson(list: List<Course>): String = json.encodeToString(ListSerializer(Course.serializer()), list)

    fun listFromJson(text: String): List<Course> =
        if (text.isBlank() || text.length > 200_000) emptyList()
        else runCatching { json.decodeFromString(ListSerializer(Course.serializer()), text) }.getOrDefault(emptyList()).mapNotNull(::sanitize).take(20)

    /** A short piece of text a friend can paste into the app. Small enough for a chat message. */
    fun toCode(c: Course): String {
        val raw = json.encodeToString(Course.serializer(), sanitize(c) ?: c).toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(raw) }
        return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    /** Reads a code, or null if it is not one, is too large, or holds nothing usable. Never throws. */
    fun fromCode(text: String): Course? {
        val t = text.trim().replace(Regex("\\s+"), "")
        if (!t.startsWith(CODE_PREFIX) || t.length > MAX_CODE_CHARS) return null
        return runCatching {
            val bytes = Base64.getUrlDecoder().decode(t.removePrefix(CODE_PREFIX))
            val raw = GZIPInputStream(ByteArrayInputStream(bytes)).use { gz ->
                val buf = ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                var total = 0
                while (true) {
                    val n = gz.read(chunk)
                    if (n < 0) break
                    total += n
                    require(total <= 200_000) { "too large" }
                    buf.write(chunk, 0, n)
                }
                buf.toByteArray()
            }
            sanitize(json.decodeFromString(Course.serializer(), raw.toString(Charsets.UTF_8)))
        }.getOrNull()
    }

    private val allKinds = listOf(Category.ITEM, Category.TERM, Category.CARD, Category.PROF, Category.SUPPLEMENTARY, Category.PRACTICAL, Category.WARD_FINAL)

    /** The built in MBBS course, as told by Dr. Muser. */
    val mbbs: Course = Course(
        "MBBS", "Medicine (MBBS)", listOf(
            CoursePhase(
                "1st Prof", "1st Professional Exam", "Phase I, 1.5 years", listOf("Anatomy", "Physiology", "Biochemistry"),
                listOf("Written practical", "Assignment"), "usually 2 lectures and 2 tutorials a day",
                listOf("ITEM", "TERM", "CARD", "PROF", "SUPPLEMENTARY", "PRACTICAL"), 11, 5,
            ),
            CoursePhase(
                "2nd Prof", "2nd Professional Exam", "Phase II, 1 year", listOf("Pharmacology & Therapeutics", "Forensic Medicine & Toxicology"),
                listOf("Written practical", "Assignment", "Postmortem report", "Certificates", "Case report (Pharmacology & Therapeutics)"),
                "usually 2 lectures, 1 tutorial and 1 ward or block class of 3 hours a day", listOf("ITEM", "TERM", "PROF", "SUPPLEMENTARY", "PRACTICAL"), 11, 5,
            ),
            CoursePhase(
                "3rd Prof", "3rd Professional Exam", "Phase III, 1 year", listOf("Pathology", "Microbiology", "Community Medicine & Public Health"),
                listOf("Written practical", "Assignment", "Day visit report", "RFST report", "Study tour report", "Case report (Pathology)", "Case report (Microbiology)"),
                "usually 2 lectures, 1 tutorial and 1 ward or block class a day", listOf("ITEM", "TERM", "PROF", "SUPPLEMENTARY", "PRACTICAL"), 11, 5,
            ),
            CoursePhase(
                "4th Prof", "4th Professional Exam", "Phase IV, final phase, 1.5 years", listOf("Medicine & Allied Subjects", "Surgery & Allied Subjects", "Obstetrics & Gynaecology"),
                listOf("Short case", "Long case", "Case report"),
                "usually 5 lectures and 2 ward or block classes (morning and evening) a day, no tutorials", listOf("TERM", "PROF", "SUPPLEMENTARY", "PRACTICAL", "WARD_FINAL"), 5, 11,
            ),
        ),
    )

    /** A starting point for a new course made from answers: [phaseCount] phases named after [unit], with the chosen exam kinds. */
    fun fromAnswers(name: String, field: String, unit: String, phaseCount: Int, kinds: List<Category>, subjects: List<List<String>>, examMonth: Int, suppMonth: Int): Course? {
        val n = phaseCount.coerceIn(1, MAX_PHASES)
        val phases = (1 until n + 1).map { i ->
            val label = "$unit $i"
            CoursePhase(label, "$label Exam", "", subjects.getOrNull(i - 1).orEmpty(), emptyList(), "", kinds.map { it.name }, examMonth, suppMonth)
        }
        return sanitize(Course(name, field, phases))
    }

    /** All kinds a student can switch on or off for a phase. */
    fun selectableKinds(): List<Category> = allKinds
}
