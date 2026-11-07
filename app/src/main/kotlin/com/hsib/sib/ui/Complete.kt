package com.hsib.sib.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hsib.sib.Ready
import com.hsib.sib.core.model.Completion
import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.UndoRecord
import com.hsib.sib.domain.Fmt
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Asks before a deadline is marked complete, and says plainly what will happen. A repeating deadline is not closed, it jumps to its
 * next date, which can be a year away, so that date is named here. [proof] offers a note and a photo, only for a deadline that closes.
 */
@Composable
fun CompleteConfirm(o: Obligation, today: LocalDate, fmt: Fmt, proof: Boolean, onCancel: () -> Unit, onConfirm: (String?, Uri?) -> Unit) {
    val c = SibTheme.colors
    val next = Completion.nextDate(o)
    var note by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { photo = it }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = c.raised,
        title = { Text("Mark this complete?", style = SibType.title, color = c.ink) },
        text = {
            Column {
                Text(o.title, style = SibType.bodyStrong, color = c.ink)
                Spacer(Modifier.height(8.dp))
                if (next != null) {
                    Text("This one repeats, so it is not closed. Marking it complete moves it to ${fmt.dayYear(next)}, ${fmt.daysFrom(today, next)}.", style = SibType.body, color = c.inkSoft)
                    Spacer(Modifier.height(6.dp))
                    Text("You can undo it on this page for ${Completion.UNDO_DAYS} days.", style = SibType.small, color = c.inkSoft)
                } else {
                    Text("It leaves your list and goes to History. You can bring it back with Undo.", style = SibType.body, color = c.inkSoft)
                    if (proof) {
                        Spacer(Modifier.height(12.dp))
                        Text("Optionally keep proof, such as a confirmation number or a screenshot of the receipt. It stays encrypted on this phone.", style = SibType.small, color = c.inkSoft)
                        Spacer(Modifier.height(10.dp))
                        Field("Note (optional)", note, { note = it }, singleLine = false, minLines = 2)
                        Spacer(Modifier.height(10.dp))
                        SibButton(if (photo == null) "Add a photo" else "Photo added, change", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, kind = ButtonKind.QUIET, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton({ onConfirm(note.trim().ifEmpty { null }, photo) }) { Text("Mark complete", style = SibType.bodyStrong, color = c.ink) } },
        dismissButton = { TextButton(onCancel) { Text("Not yet", style = SibType.body, color = c.inkSoft) } },
    )
}

/**
 * Shown on a deadline's page for 30 days after it was marked complete, with one button to put it back. It disappears if the person
 * has since changed the date themselves, because undoing then would overwrite their change.
 */
@Composable
fun UndoBanner(o: Obligation, ready: Ready, fmt: Fmt, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    val scope = rememberCoroutineScope()
    val record by produceState<UndoRecord?>(null, o.id, o.lifecycle, o.updatedAt) { value = ready.store.undoRecord(o.id) }
    val r = record ?: return
    val fits = if (r.advanced) o.lifecycle == Lifecycle.ACTIVE && o.due?.local == r.newDue else o.lifecycle == Lifecycle.COMPLETED
    if (!fits || !r.isFresh(System.currentTimeMillis())) return
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.verdigris.copy(alpha = 0.14f)).padding(16.dp)) {
        val moved = r.newDue?.toLocalDate()
        Text(
            if (r.advanced && moved != null) "Marked complete. It moved to ${fmt.dayYear(moved)}." else "Marked complete. It is now in History.",
            style = SibType.bodyStrong, color = c.ink,
        )
        Spacer(Modifier.height(10.dp))
        SibButton("Undo", {
            scope.launch {
                if (ready.store.undoComplete(o.id) && !r.advanced) ready.social?.markDone(o.id, false)
            }
        }, kind = ButtonKind.QUIET)
    }
}

/** The green Complete pill in a page's top bar. It never asks for more room than the title can spare, so big phone fonts shrink its letters instead. */
@Composable
fun CompleteButton(onClick: () -> Unit) {
    val c = SibTheme.colors
    val big = LocalDensity.current.fontScale > 1.15f
    FitText(
        "Complete", SibType.bodyStrong, c.paper,
        modifier = Modifier.then(if (big) Modifier.widthIn(max = 120.dp) else Modifier).clip(RoundedCornerShape(50)).background(c.verdigris)
            .bounceClick(onClick = onClick).padding(horizontal = if (big) 12.dp else 22.dp, vertical = 11.dp)
            .semantics { contentDescription = "Mark this complete. You will be asked to confirm." },
    )
}
