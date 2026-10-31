package com.hsib.sib.platform

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.hsib.sib.R
import com.hsib.sib.core.model.Importance
import com.hsib.sib.core.urgency.UrgencyState
import com.hsib.sib.data.LockScreenDetail
import com.hsib.sib.data.ThemeMode
import com.hsib.sib.domain.AppState
import com.hsib.sib.domain.Fmt
import java.time.temporal.ChronoUnit

private fun toneOf(u: UrgencyState) = when (u) {
    UrgencyState.OVERDUE, UrgencyState.DUE_TODAY, UrgencyState.DUE_SOON -> Tone.URGENT
    UrgencyState.APPROACHING, UrgencyState.START_SOON -> Tone.SOON
    else -> Tone.CALM
}

/** A critical deadline reads one step hotter than its date alone would say. */
private fun hotter(t: Tone) = when (t) { Tone.CALM -> Tone.SOON; else -> Tone.URGENT }

/**
 * The notification bar card: a ring with the days left, the next deadline, a row of dots counting down to its date, the next few
 * deadlines, and Open and Done. Its colour runs from violet through amber to coral as the date gets close or the deadline is
 * critical. Shown only while something needs attention. The picture itself is drawn by [DashboardArt].
 */
/** The two views of the card plus what a test or a lock screen version needs to know about them. */
internal class DashboardViews(val small: RemoteViews, val big: RemoteViews, val count: Int, val heroTitle: String, val subText: String, val hiddenKey: String)

internal fun Notifier.dashboardViews(st: AppState): DashboardViews? {
    val fmt = Fmt(context)
    val list = st.active.filter { it.dueInstant != null && it.urgency != UrgencyState.MOVED }
    val hero = list.firstOrNull() ?: return null
    val heroDay = hero.dueInstant!!.atZone(st.zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(st.today, heroDay)
    // Nothing is pinned for a deadline weeks away that is not yet worth thinking about.
    if (days > 14 && hero.urgency.rank < UrgencyState.START_SOON.rank) return null

    val mode = when (st.settings.theme) { ThemeMode.BLACK -> ThemeMode.BLACK; ThemeMode.NEON -> ThemeMode.NEON; ThemeMode.COSMOS -> ThemeMode.COSMOS; ThemeMode.FOREST -> ThemeMode.FOREST; ThemeMode.OCEAN -> ThemeMode.OCEAN; else -> ThemeMode.DARK }
    val tone = toneOf(hero.urgency).let { if (hero.o.importance == Importance.CRITICAL) hotter(it) else it }
    val progress = if (days < 0) 1f else (1f - days.coerceIn(0, 30) / 30f).coerceIn(0.04f, 1f)
    val countText = when { days < 0 -> "!"; days == 0L -> "Today"; else -> days.toString() }
    val subText = fmt.dayShort(heroDay) + "  ·  " + fmt.daysFrom(st.today, heroDay)
    val spoken = hero.o.title + ", " + hero.urgency.label + ", " + fmt.daysFrom(st.today, heroDay)

    val rest = list.drop(1).take(3)
    val data = CardData(
        tone, countText, hero.o.title, subText, progress, days.toInt(),
        rest.map { CardData.Other(toneOf(it.urgency), it.o.title, fmt.daysFrom(st.today, it.dueInstant!!.atZone(st.zone).toLocalDate())) }, mode,
    )

    val small = RemoteViews(context.packageName, R.layout.notif_dash_small).also { rv ->
        rv.setImageViewBitmap(R.id.card, DashboardArt.small(context, data))
        rv.setContentDescription(R.id.card, spoken)
        rv.setContentDescription(R.id.hit_open, "Open " + hero.o.title)
        rv.setContentDescription(R.id.hit_done, "Mark " + hero.o.title + " done")
        rv.setOnClickPendingIntent(R.id.hit_open, openIntent("item/${hero.o.id}", Notifier.ID_TODAY))
        rv.setOnClickPendingIntent(R.id.hit_done, actionIntent(Notifier.ACTION_DONE, hero.o.id))
    }

    val big = RemoteViews(context.packageName, R.layout.notif_dash_big).also { rv ->
        rv.setImageViewBitmap(R.id.card, DashboardArt.big(context, data))
        rv.setContentDescription(R.id.card, spoken)
        rv.setContentDescription(R.id.hit_hero, "Open " + hero.o.title)
        rv.setOnClickPendingIntent(R.id.hit_hero, openIntent("item/${hero.o.id}", Notifier.ID_TODAY))
        val rows = listOf(R.id.hit_row1, R.id.hit_row2, R.id.hit_row3)
        rows.forEachIndexed { i, id ->
            val item = rest.getOrNull(i)
            if (item != null) {
                rv.setContentDescription(id, "Open " + item.o.title)
                rv.setOnClickPendingIntent(id, openIntent("item/${item.o.id}", 4000 + i))
            }
        }
        rv.setContentDescription(R.id.hit_open, "Open " + hero.o.title)
        rv.setContentDescription(R.id.hit_done, "Mark " + hero.o.title + " done")
        rv.setOnClickPendingIntent(R.id.hit_open, openIntent("item/${hero.o.id}", 4010))
        rv.setOnClickPendingIntent(R.id.hit_done, actionIntent(Notifier.ACTION_DONE, hero.o.id))
    }

    // Swiping the card away hides it for this deadline at this level of urgency, until tomorrow or until it gets more urgent.
    return DashboardViews(small, big, list.size, hero.o.title, subText, hero.o.id + "|" + tone.name + "|" + st.today)
}

internal fun Notifier.dashboard(st: AppState): Notification? {
    val v = dashboardViews(st) ?: return null
    if (st.settings.dashboardHidden == v.hiddenKey) return null
    val titlesShown = st.settings.lockScreenDetail == LockScreenDetail.TITLES
    val delete = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ActionReceiver::class.java).setAction(Notifier.ACTION_TODAY_DISMISSED).setData(android.net.Uri.parse("sib://action/today")).putExtra("key", v.hiddenKey),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    return NotificationCompat.Builder(context, Notifier.CH_TODAY)
        .setSmallIcon(R.drawable.ic_stat_sib)
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setCustomContentView(v.small)
        .setCustomBigContentView(v.big)
        .setContentTitle(if (titlesShown) v.heroTitle else "H-sib Reminder")
        .setContentText(v.subText)
        // Only the person's own choice makes it stay through Clear all. Otherwise it goes like any other notification.
        .setOngoing(st.settings.todayPinned)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setShowWhen(false)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(openIntent(null, Notifier.ID_TODAY))
        .setDeleteIntent(delete)
        .setVisibility(if (titlesShown) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
        .setPublicVersion(
            NotificationCompat.Builder(context, Notifier.CH_TODAY).setSmallIcon(R.drawable.ic_stat_sib).setContentTitle("H-sib Reminder")
                .setContentText(if (v.count == 1) "1 deadline needs you" else "${v.count} deadlines need you").build(),
        )
        .build()
}
