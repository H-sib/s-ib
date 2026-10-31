package com.hsib.sib.data

import com.hsib.sib.core.time.DateOrder
import com.hsib.sib.core.urgency.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * SYSTEM follows the phone between Light and Twilight. DARK is Twilight (the violet night theme). BLACK is true black with
 * white. NEON is near black with vivid colour. The names are kept so stored settings never break.
 */
enum class ThemeMode(val label: String) { SYSTEM("Follow my phone"), LIGHT("Light"), DARK("Twilight"), BLACK("Black"), NEON("Neon") }

enum class LockScreenDetail { HIDDEN, TITLES }

/** How long the opening plays. AUTO is long the first time and short after. */
enum class IntroSpeed(val label: String) { AUTO("Long once, then short"), LONG("Always long"), SHORT("Always short"), OFF("Skip it") }

/** How the next deadlines are shown on Today. FOCUS is the original look and the default. */
enum class HomeStyle(val label: String, val hint: String) {
    FOCUS("Focus", "One big card, the others listed under it"),
    SWIPE("Swipe", "Every deadline gets the same big card. Swipe to see the next"),
    STACK("Stack", "The nearest in front, the others behind it, faded"),
    COLLAGE("Collage", "All deadlines as colourful tiles"),
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dateOrder: DateOrder = DateOrder.AUTO,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val weekend: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
    val weekdayMinutes: Int = 120,
    val weekendMinutes: Int = 60,
    val morning: LocalTime = LocalTime.of(8, 30),
    val quietStart: LocalTime = LocalTime.of(22, 0),
    val quietEnd: LocalTime = LocalTime.of(7, 30),
    val dailyBudget: Int = 2,
    val finalLeadMinutes: Int = 120,
    val remindOverdue: Boolean = true,
    val lockScreenDetail: LockScreenDetail = LockScreenDetail.HIDDEN,
    val todayNotification: Boolean = true,
    val todayPinned: Boolean = false,
    val dashboardHidden: String = "",
    val digestEnabled: Boolean = true,
    val digestHour: Int = 8,
    val learnDigestTime: Boolean = true,
    val preciseReminders: Boolean = false,
    val appLock: Boolean = false,
    /** Off by default: blocking screenshots is a choice for the owner to make, not a surprise. */
    val hideInRecents: Boolean = false,
    val reduceMotion: Boolean = false,
    val onboardingDone: Boolean = false,
    val candidateTtlDays: Int = 30,
    val keepExcerptsByDefault: Boolean = false,
    val notificationPermissionAsked: Boolean = false,
    val closeOnesEnabled: Boolean = true,
    /** Medical student mode: MBBS exam types, subjects and a class diary. Off until the person asks for it. */
    val medicalMode: Boolean = false,
    val medicalAsked: Boolean = false,
    /** Encouraging lines. [motivationOn] is the master switch; the others turn off one place or one kind of deadline. */
    /** The class routine: which weekdays it covers (1 Monday to 7 Sunday, Saturday to Thursday by default) and how many classes a day has. */
    val routineDays: Set<Int> = setOf(6, 7, 1, 2, 3, 4),
    val routineLectures: Int = 4,
    val routineTutorials: Int = 1,
    val routineWards: Int = 1,
    val motivationOn: Boolean = true,
    val motivationShow: Boolean = true,
    val motivationNotify: Boolean = true,
    val motivationOff: Set<String> = emptySet(),
    val homeStyle: HomeStyle = HomeStyle.FOCUS,
    /** Packages whose notifications H-sib Reminder may read for deadlines. Empty means it reads none. Never restored from a backup. */
    val notificationReaderApps: Set<String> = emptySet(),
    val readerPromptDismissed: Boolean = false,
    val dashboardPromptDismissed: Boolean = false,
    val introSpeed: IntroSpeed = IntroSpeed.AUTO,
    /** The version of the terms this phone accepted, 0 if none. Per device, never restored from a backup. */
    val termsVersion: Int = 0,
    /** The newest release whose What's new has been seen. Per device. */
    val lastSeenVersion: Int = 0,
    // Backup bookkeeping. These never leave the device inside a backup.
    val backupFolder: String? = null,
    val backupAuto: Boolean = false,
    val backupLastAt: Long = 0,
    val backupSeq: Long = -1,
    val backupLineage: String? = null,
    val backupHead: String? = null,
) {
    fun reminder() = ReminderSettings(
        morning = morning, quietStart = quietStart, quietEnd = quietEnd, dailyBudget = dailyBudget,
        finalLeadMinutes = finalLeadMinutes, remindOverdue = remindOverdue,
    )
}

object SettingsCodec {
    /** Keys that describe this device's backup setup and must not be restored onto another device. */
    private val deviceOnly = setOf("backupFolder", "backupAuto", "backupLastAt", "backupSeq", "backupLineage", "backupHead", "notificationPermissionAsked", "notificationReaderApps", "termsVersion", "lastSeenVersion", "dashboardHidden")

    fun encode(s: AppSettings): Map<String, String> = buildMap {
        put("theme", s.theme.name); put("dateOrder", s.dateOrder.name); put("weekStart", s.weekStart.name)
        put("weekend", s.weekend.joinToString(",") { it.name })
        put("weekdayMinutes", s.weekdayMinutes.toString()); put("weekendMinutes", s.weekendMinutes.toString())
        put("morning", s.morning.toString()); put("quietStart", s.quietStart.toString()); put("quietEnd", s.quietEnd.toString())
        put("dailyBudget", s.dailyBudget.toString()); put("finalLeadMinutes", s.finalLeadMinutes.toString())
        put("remindOverdue", s.remindOverdue.toString()); put("lockScreenDetail", s.lockScreenDetail.name)
        put("todayNotification", s.todayNotification.toString()); put("todayPinned", s.todayPinned.toString()); put("dashboardHidden", s.dashboardHidden); put("digestEnabled", s.digestEnabled.toString())
        put("digestHour", s.digestHour.toString()); put("learnDigestTime", s.learnDigestTime.toString())
        put("preciseReminders", s.preciseReminders.toString()); put("appLock", s.appLock.toString())
        put("hideFromScreenshots", s.hideInRecents.toString()); put("reduceMotion", s.reduceMotion.toString())
        put("onboardingDone", s.onboardingDone.toString()); put("candidateTtlDays", s.candidateTtlDays.toString())
        put("keepExcerptsByDefault", s.keepExcerptsByDefault.toString())
        put("notificationPermissionAsked", s.notificationPermissionAsked.toString())
        put("closeOnesEnabled", s.closeOnesEnabled.toString())
        put("medicalMode", s.medicalMode.toString()); put("medicalAsked", s.medicalAsked.toString()); put("homeStyle", s.homeStyle.name)
        put("routineDays", s.routineDays.sorted().joinToString(",")); put("routineLectures", s.routineLectures.toString()); put("routineTutorials", s.routineTutorials.toString()); put("routineWards", s.routineWards.toString())
        put("motivationOn", s.motivationOn.toString()); put("motivationShow", s.motivationShow.toString()); put("motivationNotify", s.motivationNotify.toString()); put("motivationOff", s.motivationOff.sorted().joinToString(","))
        put("notificationReaderApps", s.notificationReaderApps.sorted().joinToString(","))
        put("readerPromptDismissed", s.readerPromptDismissed.toString())
        put("dashboardPromptDismissed", s.dashboardPromptDismissed.toString())
        put("introSpeed", s.introSpeed.name); put("termsVersion", s.termsVersion.toString()); put("lastSeenVersion", s.lastSeenVersion.toString())
        s.backupFolder?.let { put("backupFolder", it) }
        put("backupAuto", s.backupAuto.toString()); put("backupLastAt", s.backupLastAt.toString()); put("backupSeq", s.backupSeq.toString())
        s.backupLineage?.let { put("backupLineage", it) }
        s.backupHead?.let { put("backupHead", it) }
    }

    fun portable(s: AppSettings): Map<String, String> = encode(s).filterKeys { it !in deviceOnly }

    private inline fun <reified E : Enum<E>> Map<String, String>.enum(key: String, default: E): E =
        this[key]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    private fun Map<String, String>.int(key: String, default: Int, range: IntRange) = (this[key]?.toIntOrNull() ?: default).coerceIn(range)
    private fun Map<String, String>.bool(key: String, default: Boolean) = this[key]?.toBooleanStrictOrNull() ?: default
    private fun Map<String, String>.time(key: String, default: LocalTime) = this[key]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: default

    fun decode(m: Map<String, String>): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = m.enum("theme", d.theme),
            dateOrder = m.enum("dateOrder", d.dateOrder),
            weekStart = m.enum("weekStart", d.weekStart),
            weekend = m["weekend"]?.split(",")?.mapNotNull { n -> DayOfWeek.values().firstOrNull { it.name == n } }?.toSet() ?: d.weekend,
            weekdayMinutes = m.int("weekdayMinutes", d.weekdayMinutes, 0..960),
            weekendMinutes = m.int("weekendMinutes", d.weekendMinutes, 0..960),
            morning = m.time("morning", d.morning), quietStart = m.time("quietStart", d.quietStart), quietEnd = m.time("quietEnd", d.quietEnd),
            dailyBudget = m.int("dailyBudget", d.dailyBudget, 0..10),
            finalLeadMinutes = m.int("finalLeadMinutes", d.finalLeadMinutes, 15..1440),
            remindOverdue = m.bool("remindOverdue", d.remindOverdue),
            lockScreenDetail = m.enum("lockScreenDetail", d.lockScreenDetail),
            todayNotification = m.bool("todayNotification", d.todayNotification),
            todayPinned = m.bool("todayPinned", d.todayPinned),
            dashboardHidden = m["dashboardHidden"] ?: d.dashboardHidden,
            digestEnabled = m.bool("digestEnabled", d.digestEnabled),
            digestHour = m.int("digestHour", d.digestHour, 0..23),
            learnDigestTime = m.bool("learnDigestTime", d.learnDigestTime),
            preciseReminders = m.bool("preciseReminders", d.preciseReminders),
            appLock = m.bool("appLock", d.appLock),
            hideInRecents = m.bool("hideFromScreenshots", d.hideInRecents),
            reduceMotion = m.bool("reduceMotion", d.reduceMotion),
            onboardingDone = m.bool("onboardingDone", d.onboardingDone),
            candidateTtlDays = m.int("candidateTtlDays", d.candidateTtlDays, 1..365),
            keepExcerptsByDefault = m.bool("keepExcerptsByDefault", d.keepExcerptsByDefault),
            notificationPermissionAsked = m.bool("notificationPermissionAsked", d.notificationPermissionAsked),
            closeOnesEnabled = m.bool("closeOnesEnabled", d.closeOnesEnabled),
            medicalMode = m.bool("medicalMode", d.medicalMode),
            medicalAsked = m.bool("medicalAsked", d.medicalAsked),
            homeStyle = m.enum("homeStyle", d.homeStyle),
            routineDays = m["routineDays"]?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 1..7 }?.toSet()?.takeIf { it.isNotEmpty() } ?: d.routineDays,
            routineLectures = m.int("routineLectures", d.routineLectures, 0..8),
            routineTutorials = m.int("routineTutorials", d.routineTutorials, 0..2),
            routineWards = m.int("routineWards", d.routineWards, 0..2),
            motivationOn = m.bool("motivationOn", d.motivationOn),
            motivationShow = m.bool("motivationShow", d.motivationShow),
            motivationNotify = m.bool("motivationNotify", d.motivationNotify),
            motivationOff = m["motivationOff"]?.split(",")?.map { it.trim() }?.filter { it.length in 3..20 }?.take(10)?.toSet() ?: d.motivationOff,
            notificationReaderApps = m["notificationReaderApps"]?.split(",")?.map { it.trim() }?.filter { it.length in 3..160 }?.take(60)?.toSet() ?: d.notificationReaderApps,
            readerPromptDismissed = m.bool("readerPromptDismissed", d.readerPromptDismissed),
            dashboardPromptDismissed = m.bool("dashboardPromptDismissed", d.dashboardPromptDismissed),
            introSpeed = m.enum("introSpeed", d.introSpeed),
            termsVersion = m.int("termsVersion", d.termsVersion, 0..1000),
            lastSeenVersion = m.int("lastSeenVersion", d.lastSeenVersion, 0..100000),
            backupFolder = m["backupFolder"],
            backupAuto = m.bool("backupAuto", false),
            backupLastAt = m["backupLastAt"]?.toLongOrNull() ?: 0,
            backupSeq = m["backupSeq"]?.toLongOrNull() ?: -1,
            backupLineage = m["backupLineage"],
            backupHead = m["backupHead"],
        )
    }
}

class SettingsStore(private val dao: SettingDao) {
    val flow: Flow<AppSettings> = dao.observeAll().map { rows -> SettingsCodec.decode(rows.associate { it.key to it.value }) }

    suspend fun current(): AppSettings = SettingsCodec.decode(dao.all().associate { it.key to it.value })

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val before = SettingsCodec.encode(current())
        val after = SettingsCodec.encode(transform(SettingsCodec.decode(before)))
        for ((k, v) in after) if (before[k] != v) dao.upsert(SettingEntity(k, v))
        for (k in before.keys - after.keys) dao.delete(k)
    }

    suspend fun raw(key: String): String? = dao.all().firstOrNull { it.key == key }?.value

    suspend fun putRaw(key: String, value: String) = dao.upsert(SettingEntity(key, value))

    suspend fun removeRaw(key: String) = dao.delete(key)
}
