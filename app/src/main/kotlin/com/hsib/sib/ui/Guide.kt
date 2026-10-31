package com.hsib.sib.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One thing the app can do, explained in a few short steps. Every feature has one, so nothing has to be found by accident. */
/** Words in [square brackets] inside steps are shown highlighted: they are the buttons to press. */
data class GuideTopic(val title: String, val line: String, val steps: List<String>, val tips: List<String> = emptyList(), val example: String? = null)

object Guide {
    fun topics(online: Boolean, medical: Boolean = false): List<GuideTopic> = buildList {
        add(GuideTopic("Add a deadline", "Four ways in, all read on this phone.", listOf(
            "Tap Add and type or paste a sentence such as: Report due 18 Nov, 11:59 PM, about 6 hours.",
            "Share a message, screenshot, photo or PDF to the app from any other app.",
            "Use Photo or Scan for a notice on paper.",
            "Check what was understood, change anything, and save.",
        )))
        if (medical) add(GuideTopic("Medical student mode", "Made for MBBS students.", listOf(
            "Tap [Add]. At the top, pick your prof, then the kind of exam, then the subject.",
            "The title, the time needed and the study steps are filled in. A prof or term exam gets a checklist with one item for each subject.",
            "Open [More], [Settings], [Medical student], [Class routine] and fill your week table: the subject and classroom of each lecture, tutorial and ward class, with its time.",
            "Use [Class diary] to write each class: subject, topic, faculty.",
        ), listOf(
            "Item is the weekly class test, then card, then term, then prof. Practical, ward final and supplementary are kept apart.",
            "1st, 2nd and 3rd prof are in November, the 4th prof in May. Supplementary runs the other way round. Set the exact day yourself.",
            "Choose the days and how many lectures, tutorials and ward classes each day has. Phases differ, so every number is yours to set.",
            "Turn the mode off in Settings and your deadlines stay.",
        ), "Anatomy Card, 1st Prof\nNeeds 8 h  ·  Read, MCQ practice, Viva"))
        add(GuideTopic("Make it yours", "Set the app up for your life.", listOf(
            "Open [More], then [Make it yours] at the top.",
            "Choose [University and college], [Work and profession] or [Daily life], then pick the one that fits you.",
            "In [Add] you now see your own kinds of deadline, with the usual hours filled in.",
            "Something does not match? Tap [Edit] and change the areas, subjects, kinds of deadline and hours. Tap a subject to move it to another phase.",
            "Tap [Share] to send your setup to friends or colleagues as a short piece of text. They paste it under [Take a setup from a friend].",
        ), listOf(
            "A setup holds only subjects or areas, kinds of deadline, hours and months. Nothing personal is shared.",
            "You can make one for any college or job by answering a few questions.",
            "Switch it off any time and your deadlines stay.",
        ), "Engineering university: Year 1, Mathematics, Physics, Chemistry"))
        add(GuideTopic("Steps you can edit", "Cut a job into pieces that suit you.", listOf(
            "Open a deadline, then the [Steps] tab.",
            "Tap [Use these steps] to take the suggestions, or [Write my own step] to start from nothing.",
            "Tap a step, or its pencil, to change what it says, how long it takes and which day.",
            "Tap [Add a step] for more, the box to tick one off, and the cross to remove it.",
        ), listOf("A step you change belongs to you from then on. [Go back to suggested steps] starts again."), "Draft the introduction, 45 min, Thu 12 Nov"))
        add(GuideTopic("Focus sessions", "Add the time you really worked.", listOf(
            "Open a deadline and find the [Focus session] card.",
            "Tap [Start a focus session] when you sit down to work.",
            "When you finish, tap [Stop and add] and the real minutes go into the progress bar.",
        ), listOf("You can close the app in between. Turn the card off in Settings, Appearance, Show or hide.")))
        add(GuideTopic("Find a friend and chat", "Chat first, share a deadline later.", listOf(
            "Open [Messages]. Type a friend's exact username, or their exact email if they allowed it, and tap [Search].",
            "Tap [Message]. They see \"wants to chat\" and choose to accept, decline or block.",
            "Set your own name in [Account] under [Username], so friends can find you.",
            "[Join with a code] is still there for shared deadlines.",
        ), listOf(
            "Nobody can browse names. A person is found only by typing the exact username.",
            "Chats are sealed on your phone, so the service that stores them cannot read them.",
        ), "@rafi.k"))
        add(GuideTopic("Language and easy mode", "Read it your way, with only what you need.", listOf(
            "The first screen after the opening asks for your language: English, Bengali or Hindi.",
            "Change it later under [More], [Language].",
            "Later in the first setup you can choose [Easy: only the basics]. Easy mode shows Today, Plan, Review and the basic settings.",
            "Switch [Easy mode] off at the top of [More] to see everything.",
        ), listOf("Bengali and Hindi cover the main screens, buttons and settings. Anything not yet translated stays in English.")))
        add(GuideTopic("Hide what you do not need", "Everything can be turned off.", listOf(
            "Open [More], [Settings], [Appearance], [Show or hide].",
            "Switch off any part of Today you do not want: the other deadlines, today's classes, the day counts and more.",
            "Under [Motivation], turn off the daily line for any kind of deadline, or all of it.",
            "Under [Your name and hints], stop being greeted by name or stop the help hints.",
        ), listOf("Nothing you hide is deleted. Switch it back on any time.")))
        add(GuideTopic("Review suggestions", "Nothing becomes a deadline until you say yes.", listOf(
            "Dates found in what you shared wait in Review.",
            "Each one shows the sentence it came from and why it was chosen. Tap Check this for doubts.",
            "Confirm, change, snooze for three days, or dismiss.",
        )))
        add(GuideTopic("The plan", "It tells you when to begin and when it does not fit.", listOf(
            "Latest safe start is the last day you can begin and still finish comfortably, even if the job runs long.",
            "The load chart spreads the work over the days you have. Each week is separated.",
            "If it does not fit, Options shows what would help: more time, a smaller job, or a later date.",
            "Set your time in Settings, Reminders.",
        )))
        add(GuideTopic("Own plan and hours", "You decide how long, and on which days.", listOf(
            "Open a deadline, then [Hours and importance]. Type any number of hours, even 48.",
            "Open [My own plan] and choose the minutes for each day with the plus and minus buttons.",
            "Other deadlines then plan around your days. Tap [Go back to the automatic plan] any time.",
        ), listOf(
            "Tap the small eye next to a title to read what it means.",
            "[Log time] buttons add the time you really worked. Hours done count toward the estimate.",
        ), "Nov 3: 4 h   Nov 4: 2 h   Nov 5: none"))
        add(GuideTopic("Checklist and progress bar", "One deadline, several pieces.", listOf(
            "Open a deadline and find the [Checklist] card. Choose how many pieces, up to 50, and tap [Make a checklist].",
            "Tap a name to give it your own, for example Renal Carcinoma. Tap the circle when a piece is done.",
            "The progress bar, drawn in the shape of a battery, fills with each piece you finish.",
        ), listOf("Exam timetables can be kept as one deadline with a checklist item for each exam. The app asks after you scan one."), "3 of 7 done  ·  43%"))
        add(GuideTopic("Month calendar", "See every deadline on a month.", listOf(
            "Open [Plan], then [Month].",
            "Days with a deadline have a dot: red when close or late, violet otherwise.",
            "Tap a day to see what is due and planned. Use the arrows to change month.",
        )))
        add(GuideTopic("Say it out loud", "Speak a deadline instead of typing.", listOf(
            "Tap [Add], then [Speak]. Your phone's own speech window opens.",
            "Say it in one go, for example: assignment due 19 November, remind me 5 days before, about 3 to 5 hours.",
            "The words appear in the box. Check what was understood and tap [Save].",
        ), listOf("The phone's speech service turns your voice into text, so it may use the internet. H-sib Reminder only receives the text."), "Assignment due 19 November, remind me 5 days before, about 3 to 5 hours."))
        add(GuideTopic("Attach files", "A PDF or photo, one tap away.", listOf(
            "Open a deadline and find the [Files] card. Tap [Attach a file].",
            "Choose the file with the phone's file picker.",
            "Tap its name any time to open it in your usual app.",
        ), listOf("H-sib Reminder never copies, stores or reads the file. It only remembers where it is, so if you move or delete the file the link stops working.")))
        add(GuideTopic("Reminders", "A few, at sensible times, never in quiet hours.", listOf(
            "Choose a daily budget and quiet hours in Settings, Reminders.",
            "Every deadline gets a calm early reminder 7, 6 and 5 days ahead. Change the days in the deadline, [Reminder days].",
            "Open Reminder health check if a reminder ever comes late. Some phones hold back apps to save battery.",
            "Done on a deadline can keep a note or a photo as proof.",
        )))
        add(GuideTopic("Close ones", "A private space for the dates that matter most.", listOf(
            "Anniversaries, birthdays and date nights live apart, with their own colours.",
            "Their reminders never show details on the lock screen, and they are never shared or put in a calendar file.",
            "Turn the space off in Settings, Close ones.",
        )))
        add(GuideTopic("Chats and notifications", "Find deadlines in your messages, only if you ask.", listOf(
            "Open Settings, Chats and notifications and read what it does.",
            "Allow access in Android's list, then tick the apps to read.",
            "Notification text is read on the phone and not kept. Suggestions still wait in Review.",
        )))
        add(GuideTopic("Card, widget and tile", "Your next deadline without opening the app.", listOf(
            "Notification bar card: a colourful card with a ring and the next deadlines, and Open and Done. It is on by default and changes colour as the date nears. Settings lets you turn it off or keep it after Clear all.",
            "Widget: long-press the home screen, choose Widgets, add Next up.",
            "Tile: pull the shade down twice, tap the pencil, drag the tile into your panel.",
        )))
        add(GuideTopic("Back up and restore", "Encrypted before it leaves the phone.", listOf(
            "Settings, Backup and restore: choose a folder, for example in Google Drive.",
            "Write down the recovery key. Without it a backup cannot be opened.",
            "On a new phone choose Restore, pick the folder and type the key.",
        )))
        if (online) {
            add(GuideTopic("Share with friends", "Reminders and a chat for everyone you add.", listOf(
                "Open Friends and sign in with Google. It is optional.",
                "Open a deadline, tap People, and share it. Send the invite code to friends.",
                "Friends tap Join with a code. Each person keeps their own plan and reminders.",
                "Only the owner changes the title and date. Everyone can chat, block and report.",
            )))
        } else {
            add(GuideTopic("Send to a friend", "A calendar file anyone can open.", listOf(
                "Open a deadline and tap Send a calendar file.",
                "Choose any app. The file holds the title and date only.",
            )))
        }
        add(GuideTopic("Today style", "Choose how Today shows your deadlines.", listOf(
            "Open [Settings], [Appearance], [Today style].",
            "Focus: one big card with the others listed under it. Swipe: every deadline gets a big card. Stack: the nearest in front, the rest behind. Collage: tiles.",
        ), listOf("The nearest deadline always comes first.")))
        add(GuideTopic("Looks and motion", "Four themes, and calm if you want it.", listOf(
            "Settings, Appearance: Light, Twilight, Black and Neon.",
            "Reduce motion skips animations. Intro speed sets how long the opening plays.",
        )))
    }
}

/** One icon for each topic, so nothing in the Guide is a bare line of words. */
fun guideIcon(title: String): SibIcon = when (title) {
    "Add a deadline" -> SibIcon.PLUS
    "Make it yours" -> SibIcon.PALETTE
    "Medical student mode" -> SibIcon.BOOK
    "Review suggestions" -> SibIcon.DOC_CHECK
    "The plan", "Own plan and hours" -> SibIcon.GAUGE
    "Steps you can edit" -> SibIcon.ROUTE
    "Focus sessions" -> SibIcon.CLOCK
    "Checklist and progress bar" -> SibIcon.CHECK
    "Month calendar" -> SibIcon.CALENDAR
    "Say it out loud" -> SibIcon.CHAT
    "Attach files" -> SibIcon.FILE
    "Reminders", "Class reminders" -> SibIcon.BELL
    "Close ones" -> SibIcon.HEART
    "Chats and notifications", "Find a friend and chat" -> SibIcon.CHAT
    "Card, widget and tile", "Today style" -> SibIcon.GRID
    "Back up and restore" -> SibIcon.CLOUD
    "Share with friends" -> SibIcon.PEOPLE
    "Send to a friend" -> SibIcon.SEND
    "Hide what you do not need" -> SibIcon.EYE
    "Language and easy mode" -> SibIcon.GLOBE
    else -> SibIcon.PALETTE
}

/** The Guide: a list of topics, then one topic at a time. Reached from Settings. */
@Composable
fun GuideScreen(online: Boolean, onBack: () -> Unit, medical: Boolean = false, startTopic: String? = null) {
    val c = SibTheme.colors
    val topics = Guide.topics(online, medical)
    var open by rememberSaveable { mutableIntStateOf(topics.indexOfFirst { it.title == startTopic }) }
    val topic = topics.getOrNull(open)
    if (topic == null) {
        Page("Guide", onBack) {
            ScreenHeader("Guide", "Everything the app can do, in a few steps each", SibIcon.BOOK)
            Gap(20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                topics.forEachIndexed { i, t -> NavRow(t.title, t.line, { open = i }, icon = guideIcon(t.title)) }
            }
            Gap(40.dp)
        }
    } else {
        Page(topic.title, { open = -1 }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(guideIcon(topic.title), size = 48.dp)
                Spacer(Modifier.size(14.dp))
                Text(topic.line, style = SibType.headline, color = c.ink, modifier = Modifier.weight(1f))
            }
            Gap(20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                topic.steps.forEachIndexed { i, step ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(18.dp), verticalAlignment = Alignment.Top) {
                        Text("${i + 1}", style = SibType.number.copy(fontSize = 22.sp), color = c.violet, modifier = Modifier.padding(end = 16.dp))
                        Text(highlighted(step, c.violet), style = SibType.body, color = c.ink, modifier = Modifier.weight(1f))
                    }
                }
            }
            topic.example?.let { ex ->
                Gap(16.dp)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.violetWash).padding(18.dp)) {
                    Text("Example", style = SibType.label, color = c.violet)
                    Gap(6.dp)
                    Text(ex, style = SibType.bodyStrong, color = c.ink)
                }
            }
            if (topic.tips.isNotEmpty()) {
                Gap(16.dp)
                Text("Good to know", style = SibType.title, color = c.ink)
                Gap(8.dp)
                topic.tips.forEach { tip ->
                    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.Top) {
                        Text("•", style = SibType.body, color = c.violet, modifier = Modifier.padding(end = 10.dp))
                        Text(tip, style = SibType.body, color = c.inkSoft)
                    }
                }
            }
            Spacer(Modifier.size(40.dp))
        }
    }
}

private fun highlighted(text: String, color: androidx.compose.ui.graphics.Color) = androidx.compose.ui.text.buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val open = text.indexOf('[', i)
        val close = if (open >= 0) text.indexOf(']', open) else -1
        if (open < 0 || close < 0) { append(text.substring(i)); break }
        append(text.substring(i, open))
        pushStyle(androidx.compose.ui.text.SpanStyle(color = color, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        append(text.substring(open + 1, close))
        pop()
        i = close + 1
    }
}
