package com.hsib.sib.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.hsib.sib.core.ingest.IcsItem
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Precision
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Reads the next weeks of entries from the calendar apps on the phone (Samsung Calendar, Google Calendar and any other that
 * keeps its entries in the phone's calendar store). Read only: nothing is ever written back. Needs the person's permission.
 */
object PhoneCalendar {
    const val DAYS = 60
    private const val MAX_ITEMS = 300

    fun allowed(context: Context): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun read(context: Context, days: Int = DAYS, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): List<IcsItem> {
        if (!allowed(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DESCRIPTION,
        )
        val begin = now.toEpochMilli()
        val end = now.plusSeconds(days * 86_400L).toEpochMilli()
        val out = ArrayList<IcsItem>()
        runCatching {
            CalendarContract.Instances.query(context.contentResolver, projection, begin, end)?.use { c ->
                while (c.moveToNext() && out.size < MAX_ITEMS) {
                    val title = c.getString(1)?.trim().orEmpty()
                    if (title.isEmpty()) continue
                    val start = c.getLong(2)
                    val allDay = c.getInt(3) == 1
                    // All day entries are stored as midnight UTC of the day itself, so the day is read in UTC.
                    val due = if (allDay) {
                        DueSpec(Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(), zone, Precision.DATE_ONLY)
                    } else {
                        DueSpec(Instant.ofEpochMilli(start).atZone(zone).toLocalDateTime(), zone, Precision.DATE_TIME)
                    }
                    out += IcsItem(title.take(120), (c.getString(4) ?: "").take(200), due, isTodo = false, uid = "cal-${c.getLong(0)}-$start")
                }
            }
        }
        return out
    }
}
