package com.hsib.sib.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hsib.sib.MainActivity
import com.hsib.sib.R
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.urgency.ReminderKind
import com.hsib.sib.core.urgency.UrgencyState
import com.hsib.sib.data.LockScreenDetail
import com.hsib.sib.domain.AppState
import com.hsib.sib.domain.Fmt
import com.hsib.sib.domain.ObligationView

/**
 * Builds every notification. Rules that hold everywhere: calm factual wording, no guilt, lock-screen details
 * hidden unless the user chose otherwise, and each reminder offers Done and Snooze without opening the app.
 */
class Notifier(internal val context: Context) {
    companion object {
        const val CH_REMINDERS = "reminders"
        const val CH_URGENT = "urgent"
        const val CH_DIGEST = "digest"
        const val CH_TODAY = "today"
        const val CH_REVIEW = "review"
        const val CH_CLOSE = "close_ones"
        const val CH_MOTIVATION = "motivation"
        const val ID_MOTIVATION = 1008
        const val ID_DIGEST = 1001
        const val ID_TODAY = 1002
        const val ID_REVIEW = 1003
        const val ID_TEST = 1004
        const val ACTION_DONE = "com.hsib.sib.action.DONE"
        const val ACTION_SNOOZE = "com.hsib.sib.action.SNOOZE"
        const val ACTION_TODAY_DISMISSED = "com.hsib.sib.action.TODAY_DISMISSED"
        const val EXTRA_ID = "obligationId"
        const val EXTRA_ROUTE = "route"
        private val SAFE_ROUTE = Regex("^(review|item/[A-Za-z0-9-]{1,64}|close/item/[A-Za-z0-9-]{1,64})$")

        /** Any app can send MainActivity an intent, so a requested screen is used only if it is one of ours. */
        fun safeRoute(route: String?): String? = route?.takeIf { SAFE_ROUTE.matches(it) }

        fun notificationId(obligationId: String) = 2000 + (obligationId.hashCode() and 0x3FFFFFFF) % 900_000
    }

    private val fmt get() = Fmt(context)
    private val manager get() = NotificationManagerCompat.from(context)

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        fun ch(id: String, name: String, desc: String, importance: Int) =
            NotificationChannel(id, name, importance).apply { description = desc; setShowBadge(id != CH_TODAY) }
        nm.createNotificationChannels(
            listOf(
                ch(CH_REMINDERS, "Reminders", "Start nudges and approaching deadlines.", NotificationManager.IMPORTANCE_DEFAULT),
                ch(CH_URGENT, "Due today and past due", "Deadlines that need attention now.", NotificationManager.IMPORTANCE_HIGH),
                ch(CH_DIGEST, "Daily summary", "One quiet summary of today.", NotificationManager.IMPORTANCE_LOW),
                ch(CH_TODAY, "Dashboard", "Your next deadlines as a card in the notification bar, shown only when something needs you.", NotificationManager.IMPORTANCE_LOW),
                ch(CH_MOTIVATION, "A little push", "One quiet line a day to get you going. Never makes a sound. Turn it off in Settings, Motivation.", NotificationManager.IMPORTANCE_LOW),
                ch(CH_REVIEW, "New suggestions", "Possible deadlines found in what you shared.", NotificationManager.IMPORTANCE_LOW),
                ch(CH_CLOSE, "Close ones", "Gentle reminders for anniversaries, birthdays and other occasions. Never shows details on the lock screen.", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    fun canPost(): Boolean = manager.areNotificationsEnabled()

    @Suppress("MissingPermission")
    fun post(id: Int, n: Notification) {
        if (canPost()) runCatching { manager.notify(id, n) }
    }

    fun cancel(id: Int) = manager.cancel(id)

    internal fun openIntent(route: String?, requestCode: Int): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            route?.let { putExtra(EXTRA_ROUTE, it) }
        }
        return PendingIntent.getActivity(context, requestCode, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    internal fun actionIntent(action: String, obligationId: String): PendingIntent {
        val i = Intent(context, ActionReceiver::class.java).setAction(action).putExtra(EXTRA_ID, obligationId)
            .setData(android.net.Uri.parse("sib://action/$action/$obligationId"))
        return PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun publicVersion(channel: String, text: String): Notification =
        NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_stat_sib).setContentTitle("H-sib Reminder").setContentText(text).build()

    fun reminder(v: ObligationView, kindName: String, st: AppState): Notification {
        val s = st.settings
        val o = v.o
        val due = v.dueInstant
        val dueText = due?.let { fmt.countdown(st.now, it, st.zone) } ?: ""
        val remaining = (o.effort?.expectedMin ?: 0).minus(o.progressMin).coerceAtLeast(0)
        val work = if (remaining > 0) " About ${fmt.minutes(remaining)} of work left." else ""
        val (title, body, channel) = when (kindName) {
            ReminderKind.START_NUDGE.name -> Triple("Time to start: ${o.title}", "This is the latest safe day to begin.$work", CH_REMINDERS)
            ReminderKind.LEAD.name -> Triple("${o.title} is due $dueText", "A calm early reminder.$work", CH_REMINDERS)
            ReminderKind.APPROACHING.name -> Triple("${o.title} is due $dueText", "No rush yet.$work", CH_REMINDERS)
            ReminderKind.DUE_SOON.name -> Triple("Due soon: ${o.title}", "Due $dueText.$work", CH_URGENT)
            ReminderKind.DUE_MORNING.name -> Triple("Due today: ${o.title}", (due?.let { "Due at " + fmt.time(it.atZone(st.zone).toLocalTime()) + "." } ?: "") + work, CH_URGENT)
            ReminderKind.DUE_FINAL.name -> Triple("${o.title} is due $dueText", "Last reminder before the deadline.$work", CH_URGENT)
            ReminderKind.OVERDUE_CHECK.name -> Triple("${o.title} is past due", "Mark it done, or move the date if it changed.", CH_URGENT)
            else -> Triple("Reminder: ${o.title}", "Due $dueText.$work", CH_REMINDERS)
        }
        val showTitles = s.lockScreenDetail == LockScreenDetail.TITLES
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_sib)
            .setContentTitle(title)
            .setContentText(body.trim())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.trim()))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openIntent("item/${o.id}", notificationId(o.id)))
            .addAction(0, "Done", actionIntent(ACTION_DONE, o.id))
            .addAction(0, "Snooze 1 h", actionIntent(ACTION_SNOOZE, o.id))
            .setVisibility(if (showTitles) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(channel, "A deadline reminder"))
            .build()
    }

    /**
     * Reminders for the Close ones space. Wording is warm and brief, and nothing about it is shown on the lock screen:
     * the public version says only "A reminder", whatever the lock screen setting is, so a surprise stays a surprise.
     */
    fun closeReminder(v: ObligationView, kindName: String, st: AppState): Notification {
        val o = v.o
        val day = v.dueInstant?.atZone(st.zone)?.toLocalDate() ?: st.today
        val whenText = fmt.daysFrom(st.today, day)
        val time = v.dueInstant?.takeIf { o.due?.precision == Precision.DATE_TIME }?.let { fmt.time(it.atZone(st.zone).toLocalTime()) }
        val forWhom = o.person.trim().takeIf { it.isNotEmpty() }
        val (title, body) = when (kindName) {
            ReminderKind.LEAD.name -> "${o.title}, $whenText" to (forWhom?.let { "Time to plan something for $it." } ?: "A good moment to plan ahead.")
            ReminderKind.DUE_MORNING.name -> "Today: ${o.title}" to (time?.let { "At $it." } ?: (forWhom?.let { "Make it a good one for $it." } ?: "Make it a good one."))
            ReminderKind.DUE_FINAL.name -> "${o.title} is $whenText" to (time?.let { "At $it." } ?: "")
            else -> o.title to whenText
        }
        return NotificationCompat.Builder(context, CH_CLOSE)
            .setSmallIcon(R.drawable.ic_stat_sib)
            .setContentTitle(title)
            .setContentText(body.trim())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.trim()))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(0xFF9C3D5A.toInt())
            .setAutoCancel(true)
            .setContentIntent(openIntent("close/item/${o.id}", notificationId(o.id)))
            .addAction(0, "Done", actionIntent(ACTION_DONE, o.id))
            .addAction(0, "Snooze 1 h", actionIntent(ACTION_SNOOZE, o.id))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(CH_CLOSE, "A reminder"))
            .build()
    }

    /** The daily encouraging line. Silent, gone by evening, and never shown on the lock screen. */
    fun motivation(line: String, name: String? = null): Notification =
        NotificationCompat.Builder(context, CH_MOTIVATION)
            .setSmallIcon(R.drawable.ic_stat_sib)
            .setContentTitle(if (name != null) "A little push, $name" else "A little push")
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line))
            .setAutoCancel(true)
            .setSilent(true)
            .setTimeoutAfter(8 * 3_600_000L)
            .setContentIntent(openIntent(null, ID_MOTIVATION))
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

    fun digest(st: AppState, extraDigestOnly: Int): Notification? {
        val active = st.active
        val overdue = active.count { it.urgency == UrgencyState.OVERDUE }
        val today = active.count { it.urgency == UrgencyState.DUE_TODAY }
        val soon = active.count { it.urgency == UrgencyState.DUE_SOON || it.urgency == UrgencyState.APPROACHING }
        val start = active.count { it.urgency == UrgencyState.START_SOON }
        val review = st.pending.size
        val parts = buildList {
            if (overdue > 0) add("$overdue past due")
            if (today > 0) add("$today due today")
            if (soon > 0) add("$soon coming up")
            if (start > 0) add("$start to start")
            if (review > 0) add("$review to review")
            if (extraDigestOnly > 0 && isEmpty()) add("$extraDigestOnly gentle reminders")
        }
        if (parts.isEmpty()) return null
        val text = parts.joinToString(", ")
        val next = active.firstOrNull { it.urgency.rank >= UrgencyState.START_SOON.rank }
        val showTitles = st.settings.lockScreenDetail == LockScreenDetail.TITLES
        return NotificationCompat.Builder(context, CH_DIGEST)
            .setSmallIcon(R.drawable.ic_stat_sib)
            .setContentTitle(st.settings.callName?.let { "Today, $it" } ?: "Today")
            .setContentText(text.replaceFirstChar { it.uppercase() })
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.replaceFirstChar { it.uppercase() } + (next?.let { "\nNext: ${it.o.title}" } ?: "")))
            .setAutoCancel(true)
            .setContentIntent(openIntent(null, ID_DIGEST))
            .setVisibility(if (showTitles) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(CH_DIGEST, "Your daily summary"))
            .build()
    }

    /** The notification bar dashboard. Null when nothing needs attention, so the card disappears by itself. */
    fun today(st: AppState): Notification? = dashboard(st)

    fun test(): Notification =
        NotificationCompat.Builder(context, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_sib)
            .setContentTitle("Test reminder")
            .setContentText("It works. Real reminders will arrive the same way.")
            .setAutoCancel(true)
            .setContentIntent(openIntent(null, ID_TEST))
            .build()

    fun review(count: Int): Notification =
        NotificationCompat.Builder(context, CH_REVIEW)
            .setSmallIcon(R.drawable.ic_stat_sib)
            .setContentTitle(if (count == 1) "1 possible deadline found" else "$count possible deadlines found")
            .setContentText("Nothing is added until you confirm it.")
            .setAutoCancel(true)
            .setContentIntent(openIntent("review", ID_REVIEW))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(CH_REVIEW, "New suggestions to review"))
            .build()
}
