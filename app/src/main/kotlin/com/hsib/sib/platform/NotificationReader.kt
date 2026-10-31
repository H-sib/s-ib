package com.hsib.sib.platform

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.BundleCompat
import com.hsib.sib.SibApp
import com.hsib.sib.core.model.SourceType
import com.hsib.sib.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Chat and mail apps H-sib Reminder offers to read. Only the ones installed on the phone are shown, and none is read until chosen. */
object KnownApps {
    val all: List<Pair<String, String>> = listOf(
        "com.google.android.gm" to "Gmail",
        "com.google.android.calendar" to "Google Calendar",
        "com.samsung.android.calendar" to "Samsung Calendar",
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.facebook.orca" to "Messenger",
        "com.google.android.apps.messaging" to "Messages",
        "com.samsung.android.messaging" to "Samsung Messages",
        "org.telegram.messenger" to "Telegram",
        "com.microsoft.office.outlook" to "Outlook",
        "com.Slack" to "Slack",
        "com.discord" to "Discord",
        "com.microsoft.teams" to "Teams",
        "com.google.android.apps.classroom" to "Classroom",
        "org.thoughtcrime.securesms" to "Signal",
        "com.viber.voip" to "Viber",
        "jp.naver.line.android" to "LINE",
        "com.tencent.mm" to "WeChat",
    )
}

/**
 * The words worth reading from a notification, and whether it is worth reading at all. Pure, so it can be tested.
 * Nothing here is stored: the text lives only long enough to be read for dates.
 */
object NotificationText {
    const val MAX_CHARS = 4000
    private const val MIN_CHARS = 8

    private val hint = Regex(
        """\d|\b(today|tonight|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|next|week|deadline|due|submit|submission|exam|test|quiz|meeting|class|lecture)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** The title, the body and any conversation lines, in order, without repeating a line. */
    fun assemble(title: CharSequence?, text: CharSequence?, bigText: CharSequence?, lines: List<CharSequence>): String {
        val parts = LinkedHashSet<String>()
        for (p in listOf(title, bigText?.takeIf { it.isNotBlank() } ?: text) + lines) {
            val t = p?.toString()?.trim().orEmpty()
            if (t.isNotEmpty()) parts += t
        }
        return parts.joinToString("\n").take(MAX_CHARS)
    }

    /** A cheap first look so ordinary chatter is skipped without running the full reader. */
    fun worthReading(text: String): Boolean = text.length >= MIN_CHARS && hint.containsMatchIn(text)
}

/**
 * Reads notifications from the apps the owner chose, finds deadlines in them on the phone, and files them as
 * suggestions in Review. It keeps no notification text; a suggestion keeps only the sentence it came from, until accepted
 * or expired.
 */
class NotificationReader(private val context: Context, private val settings: SettingsStore, private val importer: Importer, private val notifier: Notifier) {
    private val recent = ArrayDeque<Int>()
    private var windowStart = 0L
    private var windowCount = 0
    private var lastPrompt = 0L

    suspend fun handle(pkg: String, text: String) {
        val chosen = settings.current().notificationReaderApps
        if (pkg !in chosen || !NotificationText.worthReading(text)) return
        // An updated notification repeats text it already showed; read each distinct text once.
        val key = text.hashCode()
        if (key in recent) return
        recent.addLast(key)
        if (recent.size > 200) recent.removeFirst()
        // A chatty group chat must not keep the phone busy: at most 40 reads a minute.
        val now = System.currentTimeMillis()
        if (now - windowStart > 60_000) { windowStart = now; windowCount = 0 }
        if (++windowCount > 40) return

        val label = KnownApps.all.firstOrNull { it.first == pkg }?.second ?: "a chat"
        val result = importer.fromText(text, SourceType.NOTIFICATION, "Notification from $label")
        settings.putRaw("stat.notif.reads", ((settings.raw("stat.notif.reads")?.toLongOrNull() ?: 0) + 1).toString())
        settings.putRaw("stat.notif.last", now.toString())
        if (result.saved.isNotEmpty()) {
            settings.putRaw("stat.notif.found", ((settings.raw("stat.notif.found")?.toLongOrNull() ?: 0) + result.saved.size).toString())
            // One quiet nudge at most every half hour, however many arrive.
            if (now - lastPrompt > 30 * 60_000L) {
                lastPrompt = now
                notifier.post(Notifier.ID_REVIEW, notifier.review(result.saved.size))
            }
        }
    }

    companion object {
        /** Whether the owner has switched H-sib Reminder on in Android's notification access list. */
        fun isEnabled(context: Context): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }
}

/** Android calls this for every notification once access is granted; it passes on only what the owner chose. */
class NotificationReaderService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (sbn.packageName == packageName || sbn.isOngoing || (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val extras = n.extras ?: return
        val lines = BundleCompat.getParcelableArray(extras, Notification.EXTRA_MESSAGES, Bundle::class.java)
            ?.mapNotNull { (it as? Bundle)?.getCharSequence("text") }.orEmpty()
        val text = NotificationText.assemble(
            extras.getCharSequence(Notification.EXTRA_TITLE), extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT), lines,
        )
        val pkg = sbn.packageName
        scope.launch {
            val ready = (applicationContext as SibApp).container.awaitReady() ?: return@launch
            runCatching { ready.reader.handle(pkg, text) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
