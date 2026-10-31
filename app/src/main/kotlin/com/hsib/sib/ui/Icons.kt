package com.hsib.sib.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's own icons: thin outlines on a 24 point grid with round ends, and, on some, one small accent dot, the same dot that
 * sits between the bars of the H-sib mark. They are drawn from path data, so they stay sharp at any size and follow the theme.
 * An icon says what a row is about so the row can say less.
 */
enum class SibIcon(private val d: String, val dotX: Float = -1f, val dotY: Float = -1f) {
    CALENDAR("M6 5.5h12a2.5 2.5 0 0 1 2.5 2.5v10.5a2.5 2.5 0 0 1-2.5 2.5H6a2.5 2.5 0 0 1-2.5-2.5V8A2.5 2.5 0 0 1 6 5.5z M3.5 10.5h17 M8 3v3.5 M16 3v3.5", 12f, 15.2f),
    CALENDAR_PLUS("M6 5.5h12a2.5 2.5 0 0 1 2.5 2.5v10.5a2.5 2.5 0 0 1-2.5 2.5H6a2.5 2.5 0 0 1-2.5-2.5V8A2.5 2.5 0 0 1 6 5.5z M3.5 10.5h17 M8 3v3.5 M16 3v3.5 M12 13v5 M9.5 15.5h5"),
    MOVE("M6 5.5h12a2.5 2.5 0 0 1 2.5 2.5v10.5a2.5 2.5 0 0 1-2.5 2.5H6a2.5 2.5 0 0 1-2.5-2.5V8A2.5 2.5 0 0 1 6 5.5z M3.5 10.5h17 M8 3v3.5 M16 3v3.5 M8.5 15.5h7 M13.5 13l2.5 2.5-2.5 2.5"),
    SEND("M20.5 3.5L10 14 M20.5 3.5l-6.5 17-3.5-6.5-6.5-3.5z"),
    PEOPLE("M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z M2.5 20c0-3.3 2.9-5.5 6.5-5.5s6.5 2.2 6.5 5.5 M16.2 4.4a3.5 3.5 0 0 1 0 6.4 M18.2 14.8c2 .7 3.3 2.3 3.3 5.2"),
    USER("M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4.5 20c0-3.6 3.4-6 7.5-6s7.5 2.4 7.5 6"),
    CHAT("M20.5 11.5a8 8 0 0 1-11.7 7.1L3.5 20l1.2-4.4A8 8 0 1 1 20.5 11.5z", 12.3f, 11.7f),
    ROUTE("M6 16.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z M18 2.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z M8.5 19h6a3.5 3.5 0 0 0 0-7h-5a3.5 3.5 0 0 1 0-7H15.5"),
    SLIDERS("M4 7h9 M17 7h3 M4 17h3 M11 17h9 M15 4.5v5 M9 14.5v5"),
    PAUSE("M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18z M10 9v6 M14 9v6"),
    TRASH("M4 7h16 M9 7V4.5h6V7 M6.5 7l1 13h9l1-13 M10 11v5 M14 11v5"),
    CHECK("M5 12.5l4.5 4.5L19 7.5"),
    PLUS("M12 5v14 M5 12h14"),
    CLOSE("M6 6l12 12 M18 6L6 18"),
    MORE("M5 12h.01 M12 12h.01 M19 12h.01"),
    BELL("M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15z M10 21h4", 17.8f, 5.2f),
    MOON("M20 14.5A8.5 8.5 0 1 1 9.5 4a7 7 0 0 0 10.5 10.5z"),
    PALETTE("M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18z M12 3v18", 7.6f, 12f),
    SHIELD("M12 3l8 3v5.5c0 4.5-3.2 8.2-8 9.5-4.8-1.3-8-5-8-9.5V6z", 12f, 11.5f),
    LOCK("M6 11h12v9H6z M8.5 11V8a3.5 3.5 0 0 1 7 0v3", 12f, 15.5f),
    CLOUD("M7 18a4.5 4.5 0 0 1-.5-9 6 6 0 0 1 11.3 1.8A3.9 3.9 0 0 1 17 18z M12 16v-5 M9.8 13l2.2-2.2 2.2 2.2"),
    INFO("M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18z M12 11v5.5", 12f, 7.8f),
    HEART("M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7.2 4.3 4.3 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10z"),
    BOOK("M12 6.5C10 5 7 4.5 4 5v13c3-.5 6 0 8 1.5 2-1.5 5-2 8-1.5V5c-3-.5-6 0-8 1.5z M12 6.5v13"),
    SCAN("M4 8V5.5A1.5 1.5 0 0 1 5.5 4H8 M16 4h2.5A1.5 1.5 0 0 1 20 5.5V8 M20 16v2.5a1.5 1.5 0 0 1-1.5 1.5H16 M8 20H5.5A1.5 1.5 0 0 1 4 18.5V16 M4 12h16"),
    PHOTO("M4 7.5A1.5 1.5 0 0 1 5.5 6h2L9 4h6l1.5 2h2A1.5 1.5 0 0 1 20 7.5v10a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z M12 16.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z"),
    FILE("M7 3h7l5 5v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z M14 3v5h5 M9 13h6 M9 16.5h6"),
    DOC_CHECK("M7 3h7l5 5v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z M14 3v5h5 M9 15l2 2 3.5-3.5"),
    PASTE("M9 4h6v3H9z M7 5.5H6a1 1 0 0 0-1 1V20a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V6.5a1 1 0 0 0-1-1h-1"),
    KEY("M8 19a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M11 12l9-9 M16.5 6.5l2.5 2.5 M14 9l2 2"),
    EYE("M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z", 12f, 12f),
    PULSE("M3 12h4l2.5-6 4 12 2.5-6H21"),
    GRID("M4 4h6.5v6.5H4z M13.5 4H20v6.5h-6.5z M4 13.5h6.5V20H4z M13.5 13.5H20V20h-6.5z", 7.25f, 7.25f),
    PENCIL("M4 20l1-4.5L16.5 4a2.1 2.1 0 0 1 3 3L8 18.5z M14.5 6l3.5 3.5"),
    CLOCK("M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18z M12 7v5l3.5 2"),
    HOURGLASS("M7 3h10 M7 21h10 M8 3v3.5l4 5.5-4 5.5V21 M16 3v3.5L12 12l4 5.5V21"),
    STEPS("M9 6h11 M9 12h11 M9 18h11 M4 6h.01 M4 12h.01 M4 18h.01"),
    SPARK("M12 3.5l1.9 5.6 5.6 1.9-5.6 1.9L12 18.5l-1.9-5.6L4.5 11l5.6-1.9z", 19f, 18f),
    GLOBE("M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18z M3 12h18 M12 3c2.5 2.5 3.8 5.5 3.8 9s-1.3 6.5-3.8 9c-2.5-2.5-3.8-5.5-3.8-9S9.5 5.5 12 3z"),
    FLAG("M6 21V4 M6 5h11l-2 4 2 4H6"),
    GAUGE("M4.6 17.2A8.5 8.5 0 1 1 19.4 17.2 M12 13.5l3.2-4.2", 12f, 13.5f),
    LOGOUT("M9 4H5.5A1.5 1.5 0 0 0 4 5.5v13A1.5 1.5 0 0 0 5.5 20H9 M15 8l4 4-4 4 M19 12H9"),
    BLOCK("M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18z M5.6 5.6l12.8 12.8");

    /** The outline, parsed once. */
    val path: Path by lazy { PathParser().parsePathString(d).toPath() }
}

/** An icon drawn in [tint], with its accent dot (if it has one) in [accent]. Decorative: a screen reader skips it. */
@Composable
fun SibIconView(
    icon: SibIcon, modifier: Modifier = Modifier, size: Dp = 22.dp,
    tint: Color = SibTheme.colors.ink, accent: Color = SibTheme.colors.violet, stroke: Float = 1.8f,
) {
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val u = this.size.width / 24f
        scale(u, u, pivot = Offset.Zero) {
            drawPath(icon.path, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (icon.dotX >= 0f) drawCircle(accent, 1.5f, Offset(icon.dotX, icon.dotY))
        }
    }
}

/** An icon on a soft tinted square, the way rows carry them. */
@Composable
fun IconBadge(icon: SibIcon, modifier: Modifier = Modifier, size: Dp = 40.dp, tint: Color? = null) {
    val c = SibTheme.colors
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(c.violet.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        SibIconView(icon, size = size * 0.55f, tint = tint ?: c.ink)
    }
}

/** The icon that goes with a settings or information row, chosen by its title, so a row can show it without being told. */
fun iconFor(title: String): SibIcon? = when (title) {
    "Guide", "How to use" -> SibIcon.BOOK
    "Account" -> SibIcon.USER
    "Friends", "People" -> SibIcon.PEOPLE
    "Reminders" -> SibIcon.BELL
    "Chats and notifications" -> SibIcon.CHAT
    "Notification dashboard" -> SibIcon.GRID
    "Close ones" -> SibIcon.HEART
    "Motivation" -> SibIcon.CHECK
    "Calendar" -> SibIcon.CALENDAR
    "Medical student", "Study course", "Study tools", "My course" -> SibIcon.BOOK
    "Appearance" -> SibIcon.PALETTE
    "Backup and restore", "In backups" -> SibIcon.CLOUD
    "Security", "Lock screen", "On this phone" -> SibIcon.LOCK
    "Privacy and data", "Privacy policy" -> SibIcon.SHIELD
    "Sources", "Finding dates" -> SibIcon.EYE
    "Terms and security", "Terms of use", "Community rules" -> SibIcon.DOC_CHECK
    "Reminder health check" -> SibIcon.PULSE
    "Send feedback" -> SibIcon.PENCIL
    "Open source notices" -> SibIcon.FILE
    "What's new" -> SibIcon.SPARK
    "Planning" -> SibIcon.ROUTE
    "Reminding" -> SibIcon.BELL
    "The name" -> SibIcon.SPARK
    "Over the network" -> SibIcon.GLOBE
    "History" -> SibIcon.CLOCK
    "The honest limits", "About" -> SibIcon.INFO
    "Everywhere else" -> SibIcon.EYE
    "Encouraging lines" -> SibIcon.SPARK
    "On a deadline's page" -> SibIcon.DOC_CHECK
    "One quiet notification a day" -> SibIcon.BELL
    "Follow my phone" -> SibIcon.MOON
    "Block screenshots" -> SibIcon.LOCK
    "Reduce motion" -> SibIcon.PAUSE
    "Show Close ones on Today" -> SibIcon.HEART
    "Use my setup" -> SibIcon.BOOK
    "Count my setup anonymously" -> SibIcon.GLOBE
    "Let people find me by my email" -> SibIcon.USER
    "Assignments" -> SibIcon.BOOK
    "Exams" -> SibIcon.DOC_CHECK
    "Applications" -> SibIcon.FILE
    "Meetings and appointments" -> SibIcon.PEOPLE
    "Bills and renewals" -> SibIcon.BELL
    "Personal and other" -> SibIcon.USER
    "Also coming up", "Today's classes", "Needs more time", "Day counts", "Friends row", "To review row", "Make it yours card" -> SibIcon.GRID
    "Blocked people" -> SibIcon.BLOCK
    "Sign out" -> SibIcon.LOGOUT
    else -> null
}
