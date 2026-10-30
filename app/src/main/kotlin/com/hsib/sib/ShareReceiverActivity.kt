package com.hsib.sib

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import com.hsib.sib.core.model.SourceType
import com.hsib.sib.core.model.Space
import com.hsib.sib.data.ThemeMode
import com.hsib.sib.platform.Notifier
import com.hsib.sib.ui.SibTheme
import com.hsib.sib.ui.SibType

/**
 * The small window that opens when something is shared to H-sib Reminder. It reads what it was given on the phone, saves
 * suggestions only, and says how many it found. Titles are shown only when the app lock is off.
 */
class ShareReceiverActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // What arrives here comes from another app, so anything malformed simply closes the window.
        if (savedInstanceState == null) runCatching { handle(intent) }.onFailure { finish() }
        setContent {
            val state by vm.state.collectAsState()
            val ui by vm.importUi.collectAsState()
            SibTheme(if (state.loaded) state.settings.theme else ThemeMode.SYSTEM, Space.WORK) {
                ShareDialog(ui, showTitles = state.loaded && !state.settings.appLock, onReview = ::openReview, onClose = ::close)
            }
        }
    }

    private fun handle(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                when {
                    stream != null -> vm.importUri(stream, intent.type)
                    !text.isNullOrBlank() -> vm.importText(text.take(MAX_TEXT), SourceType.SHARE_TEXT, "Shared text")
                    else -> finish()
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                if (uris.isEmpty()) finish() else vm.importUris(uris, SourceType.SHARE_IMAGE)
            }
            Intent.ACTION_PROCESS_TEXT -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                if (text.isNullOrBlank()) finish() else vm.importText(text.take(MAX_TEXT), SourceType.SELECTION, "Selected text")
            }
            Intent.ACTION_VIEW -> intent.data?.let { vm.importUri(it, intent.type) } ?: finish()
            else -> finish()
        }
    }

    private fun openReview() {
        vm.dismissImport()
        startActivity(Intent(this, MainActivity::class.java).putExtra(Notifier.EXTRA_ROUTE, "review").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun close() {
        vm.dismissImport()
        finish()
    }

    private companion object {
        const val MAX_TEXT = 200_000
    }
}

@Composable
private fun ShareDialog(ui: ImportUi, showTitles: Boolean, onReview: () -> Unit, onClose: () -> Unit) {
    val c = SibTheme.colors
    when (ui) {
        ImportUi.Idle -> Unit
        is ImportUi.Working -> AlertDialog(
            onDismissRequest = {}, containerColor = c.raised,
            title = { Text("Reading...", style = SibType.title, color = c.ink) },
            text = { Text("This happens on your phone.", style = SibType.body, color = c.inkSoft) },
            confirmButton = {},
        )
        is ImportUi.Done -> {
            val r = ui.result
            val found = r.saved.size
            AlertDialog(
                onDismissRequest = onClose, containerColor = c.raised,
                title = {
                    Text(
                        when {
                            r.error != null -> "That could not be read"
                            found == 0 -> "No dates found"
                            found == 1 -> "1 possible deadline found"
                            else -> "$found possible deadlines found"
                        },
                        style = SibType.title, color = c.ink,
                    )
                },
                text = {
                    Text(
                        when {
                            r.error != null -> r.error
                            found == 0 -> "Nothing in it looked like a deadline."
                            showTitles -> r.saved.take(3).joinToString("\n") { it.title } + "\n\nNothing is added until you confirm it."
                            else -> "Nothing is added until you confirm it in Review."
                        },
                        style = SibType.body, color = c.inkSoft,
                    )
                },
                confirmButton = {
                    if (found > 0 && r.error == null) TextButton(onReview) { Text("Review now", style = SibType.bodyStrong, color = c.ink) }
                    else TextButton(onClose) { Text("Close", style = SibType.bodyStrong, color = c.ink) }
                },
                dismissButton = { if (found > 0 && r.error == null) TextButton(onClose) { Text("Later", style = SibType.body, color = c.inkSoft) } },
            )
        }
    }
}
