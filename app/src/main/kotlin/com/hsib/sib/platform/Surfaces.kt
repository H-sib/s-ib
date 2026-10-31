package com.hsib.sib.platform

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hsib.sib.MainActivity
import com.hsib.sib.SibApp
import com.hsib.sib.core.urgency.UrgencyState
import com.hsib.sib.data.LockScreenDetail
import com.hsib.sib.domain.AppState
import com.hsib.sib.domain.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Pushes the current picture to the surfaces the user chose to enable. All of them are optional and off the critical path. */
class Surfaces(private val context: Context, private val notifier: Notifier) {
    suspend fun refresh(st: AppState) {
        if (st.settings.todayNotification) {
            val n = notifier.today(st)
            if (n != null) notifier.post(Notifier.ID_TODAY, n) else notifier.cancel(Notifier.ID_TODAY)
        } else {
            notifier.cancel(Notifier.ID_TODAY)
        }
        runCatching { NextUpWidget().updateAll(context) }
        runCatching { TileService.requestListeningState(context, ComponentName(context, NextUpTileService::class.java)) }
    }
}

/** Quick Settings tile: the closest thing to a glance surface inside the notification shade. */
class NextUpTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        scope.launch {
            val app = applicationContext as SibApp
            val st = app.container.awaitReady()?.engine?.snapshot()
            val tile = qsTile ?: return@launch
            val next = st?.active?.firstOrNull { it.urgency.rank >= UrgencyState.PLANNED.rank && it.dueInstant != null && it.urgency != UrgencyState.MOVED }
            tile.label = "H-sib"
            val showTitles = st?.settings?.lockScreenDetail == LockScreenDetail.TITLES
            if (Build.VERSION.SDK_INT >= 29) {
                tile.subtitle = when {
                    st == null -> "Open H-sib Reminder"
                    next == null -> "Nothing pending"
                    showTitles -> "${next.o.title}, " + Fmt(applicationContext).daysFrom(st.today, next.dueInstant!!.atZone(st.zone).toLocalDate())
                    else -> next.urgency.label
                }
            }
            tile.state = if (next != null && next.urgency.rank >= UrgencyState.APPROACHING.rank) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // the Intent overload is only used below Android 14
    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

class NextUpWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as SibApp
        val st = app.container.awaitReady()?.engine?.snapshot()
        provideContent { WidgetContent(context, st) }
    }
}

class NextUpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextUpWidget()
}

private val paper = ColorProvider(day = Color(0xFFF3F0FF), night = Color(0xFF171433))
private val ink = ColorProvider(day = Color(0xFF14112B), night = Color(0xFFF7F4FF))
private val quiet = ColorProvider(day = Color(0xFF3E3869), night = Color(0xFFCFC8F0))

@Composable
private fun WidgetContent(context: Context, st: AppState?) {
    val fmt = Fmt(context)
    Column(
        modifier = GlanceModifier.fillMaxSize().background(paper).padding(14.dp).clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        if (st == null) {
            Text("H-sib", style = TextStyle(color = ink, fontWeight = FontWeight.Bold, fontSize = 16.sp))
            Text("Open the app to set up.", style = TextStyle(color = quiet, fontSize = 12.sp))
            return@Column
        }
        val ranked = st.active.filter { it.dueInstant != null && it.urgency != UrgencyState.MOVED }.take(3)
        val titles = st.settings.lockScreenDetail == LockScreenDetail.TITLES
        Text("Next up", style = TextStyle(color = quiet, fontSize = 11.sp))
        Spacer(GlanceModifier.height(4.dp))
        if (ranked.isEmpty()) {
            Text("Nothing pending", style = TextStyle(color = ink, fontWeight = FontWeight.Bold, fontSize = 15.sp))
        } else if (!titles) {
            val due = ranked.size
            Text("$due on your list", style = TextStyle(color = ink, fontWeight = FontWeight.Bold, fontSize = 15.sp))
            Text(ranked.first().urgency.label + " first", style = TextStyle(color = quiet, fontSize = 12.sp))
        } else {
            ranked.forEach { v ->
                Text(v.o.title, style = TextStyle(color = ink, fontWeight = FontWeight.Medium, fontSize = 14.sp), maxLines = 1)
                Text(
                    v.urgency.label + ", " + fmt.daysFrom(st.today, v.dueInstant!!.atZone(st.zone).toLocalDate()),
                    style = TextStyle(color = quiet, fontSize = 11.sp), maxLines = 1,
                )
                Spacer(GlanceModifier.height(5.dp))
            }
        }
    }
}

/** Runs a few times a day: expires old suggestions, rebuilds reminders, refreshes surfaces, takes the scheduled backup. */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SibApp
        val ready = app.container.awaitReady() ?: return Result.success()
        ready.store.expireCandidates()
        ready.store.unsnoozeDue()
        ready.store.rollRepeats(java.time.LocalDate.now(), java.time.ZoneId.systemDefault())
        ready.engine.refreshCalibration()
        val st = ready.engine.snapshot()
        ready.reminders.sync(st)
        ready.reminders.scheduleDigest(st.settings, ready.digestHour())
        ready.reminders.scheduleMotivation(st.settings)
        ready.surfaces.refresh(st)
        val s = st.settings
        if (s.backupAuto && s.backupFolder != null && System.currentTimeMillis() - s.backupLastAt > 20 * 3_600_000L) {
            runCatching { ready.backup.backupNow() }
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<MaintenanceWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("maintenance", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
