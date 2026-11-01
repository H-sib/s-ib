package com.hsib.sib.platform

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.hsib.sib.SibApp
import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.model.Space
import com.hsib.sib.core.urgency.ReminderPlanner
import com.hsib.sib.data.AppSettings
import com.hsib.sib.data.ReminderEntity
import com.hsib.sib.data.SettingsStore
import com.hsib.sib.data.SibDatabase
import com.hsib.sib.data.Store
import com.hsib.sib.domain.AppState
import com.hsib.sib.domain.Engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Keeps scheduled reminders in step with the deadlines.
 *
 * Reminders are derived data. Every sync recomputes the wanted set, removes alarms that are no longer wanted
 * (so a moved deadline can never leave an old reminder behind) and registers alarms for the next ones. Each row is
 * keyed by deadline, kind and minute, so the same reminder can never be scheduled or shown twice.
 */
class ReminderSync(
    private val context: Context,
    private val db: SibDatabase,
    private val store: Store,
    private val settings: SettingsStore,
    private val engine: Engine,
    private val notifier: Notifier,
) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    companion object {
        const val ACTION_FIRE = "com.hsib.sib.action.FIRE"
        const val ACTION_DIGEST = "com.hsib.sib.action.DIGEST"
        const val MAX_ALARMS = 30
        const val SNOOZE_KIND = "SNOOZE"

        fun canUseExactAlarms(context: Context): Boolean =
            Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun pending(key: String): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE)
            .setData(Uri.parse("sib://reminder/$key")).putExtra("key", key)
        return PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // Exact alarms are only used where Android allows them without a special permission; see canUseExactAlarms.
    @SuppressLint("ScheduleExactAlarm")
    private fun setAlarm(at: Long, pi: PendingIntent, precise: Boolean) {
        if (precise && canUseExactAlarms(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun cancelAlarm(key: String) = alarms.cancel(pending(key))

    suspend fun sync(st: AppState) {
        val s = st.settings
        val rs = s.reminder()
        val specs = st.items.filter { it.o.lifecycle == Lifecycle.ACTIVE }.flatMap { v ->
            ReminderPlanner.specs(v.o, v.latestStart, rs, st.now, st.zone, fogTentative = v.tentative)
        }
        val budget = ReminderPlanner.applyBudget(specs, rs, st.zone)
        val wanted = (budget.scheduled.map { it to false } + budget.digestOnly.map { it to true }).associateBy { it.first.dedupeKey }
        val existing = db.reminders().scheduled()
        for (row in existing) {
            if (row.key !in wanted) { cancelAlarm(row.key); db.reminders().delete(row.key) }
        }
        val nowMs = st.now.toEpochMilli()
        // Rows whose time passed without firing (phone off, notifications blocked) become the "you may have missed" ledger.
        for (row in existing) if (row.key in wanted && row.fireAt < nowMs - 15 * 60_000L) db.reminders().upsert(row.copy(state = "MISSED"))
        for ((key, pair) in wanted) {
            if (db.reminders().get(key) != null) continue
            val (spec, digestOnly) = pair
            db.reminders().upsert(ReminderEntity(key, spec.obligationId, spec.kind.name, spec.fireAt.toEpochMilli(), "SCHEDULED", digestOnly))
        }
        db.reminders().pruneOld(nowMs - 14 * 86_400_000L)
        registerNext(s)
    }

    /** Registers alarms for the soonest rows. Safe to repeat. */
    suspend fun registerNext(s: AppSettings) {
        val nowMs = System.currentTimeMillis()
        val rows = db.reminders().scheduled().filter { !it.digestOnly && it.fireAt > nowMs }.take(MAX_ALARMS)
        for (row in rows) setAlarm(row.fireAt, pending(row.key), s.preciseReminders)
        // Snoozed rows live outside the regular set but still need their alarm.
        for (row in db.reminders().all().filter { it.state == "SNOOZED" && it.fireAt > nowMs }) setAlarm(row.fireAt, pending(row.key), s.preciseReminders)
    }

    /** After reboot, time or zone changes: alarms are gone or wrong, rebuild from the data. */
    suspend fun rebuildEverything() {
        val st = engine.snapshot()
        db.reminders().deleteScheduled()
        sync(st)
        scheduleDigest(st.settings)
        scheduleMotivation(st.settings)
    }

    /** Lets the user prove, on their own phone, that a reminder really arrives. */
    suspend fun scheduleTest(seconds: Int = 15) {
        val at = System.currentTimeMillis() + seconds * 1000L
        val key = "test|$at"
        db.reminders().upsert(ReminderEntity(key, "test", "TEST", at, "SNOOZED", false))
        setAlarm(at, pending(key), false)
    }

    /** Posts the notification for a due alarm, unless the deadline changed or was finished in the meantime. */
    suspend fun fire(key: String) {
        if (key.startsWith("test|")) {
            db.reminders().delete(key)
            notifier.post(Notifier.ID_TEST, notifier.test())
            return
        }
        val row = db.reminders().get(key) ?: return
        if (row.state != "SCHEDULED" && row.state != "SNOOZED") return
        val st = engine.snapshot()
        val view = st.items.firstOrNull { it.o.id == row.obligationId }
        if (view == null || view.o.lifecycle != Lifecycle.ACTIVE) { db.reminders().delete(key); return }
        if (!notifier.canPost()) { db.reminders().upsert(row.copy(state = "MISSED")); return }
        val notification = if (view.o.space == Space.CLOSE) notifier.closeReminder(view, row.kind, st) else notifier.reminder(view, row.kind, st)
        notifier.post(Notifier.notificationId(view.o.id), notification)
        db.reminders().upsert(row.copy(state = "FIRED"))
        registerNext(st.settings)
    }

    suspend fun snooze(obligationId: String, minutes: Int = 60) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val key = "$obligationId|$SNOOZE_KIND|${at / 60_000}"
        db.reminders().upsert(ReminderEntity(key, obligationId, SNOOZE_KIND, at, "SNOOZED", false))
        setAlarm(at, pending(key), settings.current().preciseReminders)
        notifier.cancel(Notifier.notificationId(obligationId))
    }

    // ---- daily digest -----------------------------------------------------------------------

    private fun digestIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0, Intent(context, DigestReceiver::class.java).setAction(ACTION_DIGEST).setData(Uri.parse("sib://digest")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    suspend fun scheduleDigest(s: AppSettings, hour: Int? = null) {
        alarms.cancel(digestIntent())
        if (!s.digestEnabled) return
        val zone = ZoneId.systemDefault()
        val h = hour ?: s.digestHour
        var next = ZonedDateTime.now(zone).withHour(h).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(ZonedDateTime.now(zone).plusMinutes(1))) next = next.plusDays(1)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), digestIntent())
    }

    // ---- one encouraging line a day, at a time that changes every day ---------------------------

    private fun motivationIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context, 1, Intent(context, MotivationReceiver::class.java).setAction("com.hsib.sib.action.MOTIVATION").setData(Uri.parse("sib://motivation")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Some minute between 11:00 and 17:59, different each day, so it arrives as a small surprise and never in quiet hours. */
    internal fun motivationTime(day: LocalDate, zone: ZoneId): ZonedDateTime =
        day.atStartOfDay(zone).plusMinutes(11 * 60L + Math.floorMod(day.toEpochDay() * 7919L, 7 * 60L))

    suspend fun scheduleMotivation(s: AppSettings) {
        alarms.cancel(motivationIntent())
        if (!s.motivationOn || !s.motivationNotify) return
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var next = motivationTime(now.toLocalDate(), zone)
        if (!next.isAfter(now.plusMinutes(1))) next = motivationTime(now.toLocalDate().plusDays(1), zone)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC, next.toInstant().toEpochMilli(), motivationIntent())
    }

    /** Picks one of the nearest deadlines (a different one on different days) and sends a line fitted to its kind. */
    suspend fun fireMotivation(force: Boolean = false) {
        val st = engine.snapshot()
        val s = st.settings
        if (force || (s.motivationOn && s.motivationNotify)) {
            val today = LocalDate.now(st.zone)
            val pool = (st.active + st.close)
                .filter { it.dueInstant != null && it.o.category.let { c -> com.hsib.sib.core.motivation.Motivation.groupFor(c).name !in s.motivationOff } }
                .sortedBy { it.dueInstant }
            val upcoming = pool.filter { !it.dueInstant!!.isBefore(st.now) }.ifEmpty { pool.takeLast(3) }.take(3)
            if (upcoming.isNotEmpty()) {
                val v = upcoming[Math.floorMod(today.toEpochDay(), upcoming.size.toLong()).toInt()]
                val group = com.hsib.sib.core.motivation.Motivation.groupFor(v.o.category)
                val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, v.dueInstant!!.atZone(st.zone).toLocalDate())
                val started = v.o.progressMin > 0 || v.o.parts.any { it.done }
                val line = com.hsib.sib.core.motivation.Motivation.line(daysLeft, v.o.id, today.toEpochDay() + if (force) System.nanoTime() % 1000 else 0, started, s.medicalMode && v.o.category.medical, group, s.callName)
                notifier.post(Notifier.ID_MOTIVATION, notifier.motivation(line, s.callName))
            }
        }
        scheduleMotivation(s)
    }

    suspend fun fireDigest(hourForNext: Int) {
        val st = engine.snapshot()
        val today = LocalDate.now()
        val digestOnly = db.reminders().all().count {
            it.digestOnly && Instant.ofEpochMilli(it.fireAt).atZone(st.zone).toLocalDate() == today && it.state == "SCHEDULED"
        }
        if (st.settings.digestEnabled) notifier.digest(st, digestOnly)?.let { notifier.post(Notifier.ID_DIGEST, it) }
        scheduleDigest(st.settings, hourForNext)
    }

    suspend fun missed(): List<ReminderEntity> = db.reminders().all().filter { it.state == "MISSED" }.sortedByDescending { it.fireAt }

    suspend fun clearMissed() {
        db.reminders().all().filter { it.state == "MISSED" }.forEach { db.reminders().delete(it.key) }
    }
}

private fun BroadcastReceiver.runAsync(context: Context, block: suspend (com.hsib.sib.Ready) -> Unit) {
    val pending = goAsync()
    val app = context.applicationContext as SibApp
    CoroutineScope(Dispatchers.Default).launch {
        try {
            app.container.awaitReady()?.let { block(it) }
        } finally {
            pending.finish()
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        runAsync(context) { it.reminders.fire(key) }
    }
}

class MotivationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync(context) { it.reminders.fireMotivation() }
    }
}

class DigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync(context) { it.reminders.fireDigest(it.digestHour()) }
    }
}

/** Boot, app update, clock and zone changes: alarms may be lost or shifted, so rebuild them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync(context) { it.reminders.rebuildEverything() }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Notifier.EXTRA_ID)
        val action = intent.action ?: return
        runAsync(context) { ready ->
            when (action) {
                Notifier.ACTION_DONE -> if (id != null) {
                    ready.store.complete(id, null, null)
                    ready.engine.refreshCalibration()
                    ready.notifier.cancel(Notifier.notificationId(id))
                }
                Notifier.ACTION_SNOOZE -> if (id != null) ready.reminders.snooze(id, 60)
                Notifier.ACTION_TODAY_DISMISSED -> intent.getStringExtra("key")?.let { key -> ready.settings.update { it.copy(dashboardHidden = key) } }
            }
        }
    }
}
