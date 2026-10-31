package com.hsib.sib.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hsib.sib.core.model.FileLink

/**
 * Files and links on a deadline. The app only remembers where the file is. It never copies it, never reads it and never sends it
 * anywhere; opening it hands it to whichever app the phone uses for that kind of file.
 */
@Composable
fun FilesTile(links: List<FileLink>, onChange: (List<FileLink>) -> Unit) {
    val c = SibTheme.colors
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onChange(links + FileLink(displayName(context, uri), uri.toString()))
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(18.dp)) {
        InfoTitle("Files", "Point to a PDF, a photo or any file already on your phone. H-sib Reminder does not store it or look inside it. It only remembers where it is, so one tap opens it in your usual app.")
        if (links.isEmpty()) Text("Nothing attached.", style = SibType.small, color = c.inkSoft)
        links.forEachIndexed { i, f ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                SibIconView(SibIcon.FILE, size = 22.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    f.name, style = SibType.body, color = c.ink,
                    modifier = Modifier.weight(1f).clickable(role = Role.Button) { open(context, f) }.padding(vertical = 10.dp),
                )
                SibButton("Remove", { onChange(links.filterIndexed { j, _ -> j != i }) }, kind = ButtonKind.TEXT, compact = true)
            }
        }
        Spacer(Modifier.size(8.dp))
        SibButton("Attach a file", { picker.launch(arrayOf("*/*")) }, kind = ButtonKind.QUIET)
    }
}

private fun displayName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment ?: "File"

private fun open(context: Context, f: FileLink) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setData(Uri.parse(f.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
