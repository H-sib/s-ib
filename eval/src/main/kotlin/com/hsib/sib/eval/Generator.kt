package com.hsib.sib.eval

import com.hsib.sib.core.extract.Role
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.Random

data class Sample(val text: String, val roles: List<Role>, val reference: ZonedDateTime)

/**
 * Synthetic text generator. It writes sentences from templates for each role, with many date formats,
 * optional times, composite multi-date texts and light noise. It is the only training data the shipped
 * model has seen, so the evaluation uses a separately hand written gold set.
 */
class Generator(seed: Long) {
    private val rnd = Random(seed)
    private val zone = ZoneId.of("UTC")
    private val en = Locale.ENGLISH

    private val tasks = listOf(
        "assignment", "lab report", "final project report", "essay draft", "scholarship application", "application form",
        "visa renewal form", "tax return", "rent payment", "invoice", "quarterly report", "design proposal", "problem set 3",
        "literature review", "thesis chapter", "insurance renewal", "library books", "permission slip", "registration form",
        "expense report", "grant proposal", "portfolio", "survey responses", "timesheet", "course feedback form", "budget plan",
        "reading summary", "passport renewal", "club membership fee", "research paper",
    )
    private val events = listOf(
        "midterm exam", "final exam", "entrance test", "interview", "team meeting", "dentist appointment", "project presentation",
        "orientation session", "webinar", "viva voce", "admission test", "quiz", "parent-teacher meeting", "demo day",
        "lab practical", "flight", "doctor appointment", "client call", "workshop", "campus tour", "seminar", "board meeting",
    )
    private val openers = listOf("", "", "", "", "Reminder: ", "Dear students, ", "Hi all, ", "Notice: ", "Hello, ", "FYI: ", "Important: ", "Update: ", "Hey, ", "Dear applicants, ", "Team, ", "Hi, ")
    private val closers = listOf("", "", "", ".", " Thanks!", " Please confirm.", " Do not miss it.", " Regards.", " Let me know if you have questions.", " Thank you.")

    private fun <T> pick(l: List<T>): T = l[rnd.nextInt(l.size)]
    private fun chance(p: Double) = rnd.nextDouble() < p

    private fun ordinal(d: Int) = when {
        d in 11..13 -> "${d}th"
        d % 10 == 1 -> "${d}st"
        d % 10 == 2 -> "${d}nd"
        d % 10 == 3 -> "${d}rd"
        else -> "${d}th"
    }

    private fun fmt(p: String, d: LocalDate) = d.format(DateTimeFormatter.ofPattern(p, en))

    private fun dateString(date: LocalDate, ref: LocalDate, withTimeAllowed: Boolean = true): String {
        val delta = ChronoUnit.DAYS.between(ref, date).toInt()
        var s = when (rnd.nextInt(14)) {
            0 -> fmt("MMMM d, yyyy", date)
            1 -> fmt("MMM d", date)
            2 -> fmt("d MMMM yyyy", date)
            3 -> fmt("d MMM", date)
            4 -> fmt("MMMM d", date)
            5 -> fmt("yyyy-MM-dd", date)
            6 -> if (date.dayOfMonth > 12) fmt("d/M/yyyy", date) else fmt("MMMM d, yyyy", date)
            7 -> fmt("EEE, MMM d", date)
            8 -> fmt("EEEE d MMMM", date)
            9 -> "${ordinal(date.dayOfMonth)} ${fmt("MMMM", date)}"
            10 -> "${fmt("MMM", date)} ${ordinal(date.dayOfMonth)}"
            11 -> if (delta == 1) "tomorrow" else if (delta == 0) "today" else if (delta in 2..14) "in $delta days" else fmt("MMMM d", date)
            12 -> if (delta in 1..6) fmt("EEEE", date) else fmt("d MMM yyyy", date)
            else -> if (date.dayOfMonth > 12) fmt("dd.MM.yyyy", date) else fmt("d MMMM", date)
        }
        if (withTimeAllowed && chance(0.35)) {
            s += when (rnd.nextInt(6)) {
                0 -> " at 11:59 PM"
                1 -> " at 5 pm"
                2 -> ", 09:00"
                3 -> " 23:59"
                4 -> " at 10:30 AM"
                else -> " at noon"
            }
        }
        return s
    }

    private fun fill(t: String, d: String): String {
        val task = pick(tasks)
        val event = pick(events)
        return t.replace("{d}", d).replace("{task}", task).replace("{Task}", task.replaceFirstChar { it.uppercase() })
            .replace("{event}", event).replace("{Event}", event.replaceFirstChar { it.uppercase() })
    }

    private val deadlineT = listOf(
        "Submit the {task} by {d}", "The deadline for the {task} is {d}", "{Task} is due {d}", "Applications close on {d}",
        "Last date to apply for the scholarship: {d}", "Please send your {task} no later than {d}", "You must complete the {task} before {d}",
        "Registration closes {d}", "Payment of the {task} must be made by {d}", "Hand in your {task} on or before {d}",
        "Your {task} expires on {d}", "Final date for {task} submission: {d}", "The submission deadline has been extended to {d}",
        "Reminder that the {task} is due by {d}", "I need the {task} from you by {d}", "Don't forget to renew the {task} before {d}",
        "{d} is the last day to submit the {task}", "Cut-off for the {task}: {d}", "We must file the {task} before {d}",
        "Please upload the {task} until {d}", "Due: {d}", "Deadline: {d}", "The {task} needs to be finished by {d}",
        "All {task}s are due on {d}", "Applicants should submit documents by {d}", "Last day to withdraw from the course is {d}",
        "Make sure the {task} reaches us before {d}", "Closing date for entries: {d}", "The {task} must reach the office by {d}",
    )
    private val eventT = listOf(
        "The {event} will take place on {d}", "Your {event} is scheduled for {d}", "{Event}: {d}", "We have the {event} on {d}",
        "The {event} has been rescheduled to {d}", "Join us for the {event} on {d}", "Your {event} is confirmed for {d}",
        "The {event} is on {d}", "Don't forget the {event} on {d}", "The {event} has been postponed to {d}",
        "Reporting time for the {event}: {d}", "Our {event} takes place {d}", "{d}: {event}", "The {event} will be held on {d}",
        "You are invited to the {event} on {d}", "Your {event} slot is {d}", "Please attend the {event} on {d}",
        "The {event} on {d} has been cancelled", "The {event} originally set for {d} is called off",
    )
    private val startT = listOf(
        "Classes begin on {d}", "Registration opens on {d}", "The new semester starts {d}", "Applications open {d}",
        "The course commences on {d}", "Doors open at the venue on {d}", "Enrolment begins {d}", "The program starts on {d}",
        "Online portal opens {d}", "Term starts {d}", "The portal will open for submissions on {d}", "Lectures start from {d}",
        "Office hours resume from {d}", "Services resume on {d}", "Applications will open on {d}", "The season kicks off on {d}",
        "The course registration starts {d}", "Bookings open {d}", "Winter break ends and classes restart on {d}",
    )
    private val publishedT = listOf(
        "Posted on {d}", "Notice dated {d}", "Last updated: {d}", "Published {d} by the registrar's office", "Issued on {d}",
        "Circular no. 45 dated {d}", "Sent on {d}", "Announced on {d}", "Date of issue: {d}", "This notice was published on {d}",
        "Updated {d}", "As of {d}, the list is final", "Created on {d}", "Uploaded {d}", "Document revision dated {d}",
        "Document revision: {d}", "Version 2.1, {d}", "Sent on {d} from my phone", "Posted {d} by Admin",
        "Notice No. 87/2026, issued on {d}", "Ref: 2026/44, dated {d}", "Printed on {d}",
    )
    private val historyT = listOf(
        "The meeting took place on {d}", "I submitted the {task} on {d}", "We met on {d}", "Results were announced on {d}",
        "It was due on {d} but is already done", "The exam was held on {d}", "I went to the clinic on {d}", "That happened on {d}",
        "The {task} was completed on {d}", "Last time we spoke was {d}", "She joined the team on {d}", "The package arrived on {d}",
        "I paid the {task} on {d}", "Our trip ended on {d}", "He sent the {task} on {d}", "We finished the {task} on {d}",
        "I attended the {event} on {d}", "We visited the campus on {d}", "The {event} was on {d} and went well",
        "I handed in the {task} on {d}", "They signed the {task} on {d}", "The deadline passed on {d} and nobody noticed",
        "I emailed the {task} on {d}", "She received the {task} on {d}", "The {event} ended on {d}", "We completed the {task} on {d}",
        "It was submitted on {d}", "I filed the {task} on {d}", "Yesterday I submitted the form, {d} was the last chance",
    )
    private val tentativeT = listOf(
        "Maybe we should meet {d}", "The exam might be on {d}", "Tentatively scheduled for {d}", "Possibly {d}, TBC",
        "I think the deadline could be {d}", "Perhaps we can submit the {task} by {d}", "The {event} is expected around {d}, not confirmed",
        "If all goes well the results come out {d}", "How about {d} for the call?", "Should we do the {event} on {d}?",
        "Probably {d} for the demo", "The date is provisional: {d}", "We might visit on {d}", "Not sure yet, maybe {d}",
        "The {event} is likely on {d} but to be confirmed",
        "If approved, we start on {d}", "Dinner on {d}? Let me know", "Lunch on {d}?", "Possibly on {d}, depending on the weather",
        "If the grant comes through the project begins {d}", "Hopefully the visa will be ready by {d}", "Could we meet on {d}?",
        "I might be free on {d}", "Unsure, but the {event} could be on {d}",
    )
    private val otherT = listOf(
        "The museum is closed on {d}", "{d} is a public holiday", "Sale ends {d}! Save up to 50%", "Limited time offer valid until {d}",
        "The office is closed for the holiday on {d}", "Free shipping until {d}", "Weather forecast for {d}: rain", "The library is open until 8 pm on {d}",
        "Prices valid {d}", "Offer expires {d}, use code SAVE", "On {d} the town celebrated its founding", "Big discount weekend, ends {d}",
        "Flash sale until {d}", "Our newsletter for {d}", "Special price valid through {d}", "Bank holiday on {d}",
        "Order before {d} for free delivery", "Book by {d} and save 15%", "Register before {d} to get a free gift",
        "Early bird pricing ends {d}", "Use code SPRING by {d} for 10% off", "Join before {d} and get 20% off",
        "Subscribe by {d} to unlock 30% off", "Parking is free after 5 pm on {d}", "The shop is open late on {d}",
        "Sign up before {d} for a chance to win a prize", "Deal of the week, ends {d}", "Members get double points until {d}",
        "Trending on {d}: top ten recipes", "Sunrise on {d} is at 6:12 am", "Tickets on sale until {d}, while stocks last",
    )

    private fun templates(role: Role) = when (role) {
        Role.DEADLINE -> deadlineT
        Role.EVENT -> eventT
        Role.START -> startT
        Role.PUBLISHED -> publishedT
        Role.HISTORY -> historyT
        Role.TENTATIVE -> tentativeT
        Role.OTHER -> otherT
    }

    private fun dateFor(role: Role, ref: LocalDate): LocalDate = when (role) {
        Role.HISTORY -> ref.minusDays(1L + rnd.nextInt(60))
        Role.PUBLISHED -> if (chance(0.7)) ref.minusDays(rnd.nextInt(45).toLong()) else ref
        Role.OTHER -> ref.plusDays((rnd.nextInt(80) - 20).toLong())
        else -> if (chance(0.1)) ref.plusDays(100L + rnd.nextInt(250)) else ref.plusDays(1L + rnd.nextInt(60))
    }

    private fun weightedRole(): Role {
        val r = rnd.nextInt(100)
        return when {
            r < 24 -> Role.DEADLINE
            r < 44 -> Role.EVENT
            r < 52 -> Role.START
            r < 62 -> Role.PUBLISHED
            r < 76 -> Role.HISTORY
            r < 86 -> Role.TENTATIVE
            else -> Role.OTHER
        }
    }

    private fun sentence(role: Role, ref: LocalDate): String {
        val date = dateFor(role, ref)
        val allowTime = role != Role.PUBLISHED && role != Role.HISTORY
        val d = dateString(date, ref, allowTime)
        var s = fill(pick(templates(role)), d)
        if (chance(0.4)) {
            val opener = pick(openers)
            if (opener.isNotEmpty()) s = opener + s.replaceFirstChar { it.lowercase() }
        }
        s += pick(closers)
        if (chance(0.08)) s = s.lowercase()
        if (chance(0.05)) s = "- $s"
        if (chance(0.04)) s += " 🙂"
        return s.trim()
    }

    fun sample(): Sample {
        val ref = ZonedDateTime.of(
            LocalDate.of(2026, 1, 1).plusDays(rnd.nextInt(700).toLong()),
            LocalTime.of(8 + rnd.nextInt(10), 0), zone,
        )
        val refDay = ref.toLocalDate()
        val first = weightedRole()
        if (!chance(0.3)) {
            return Sample(sentence(first, refDay), listOf(first), ref)
        }
        val second = weightedRole()
        val sep = pick(listOf(" ", "\n", " ", ". ", "\n\n"))
        val a = sentence(first, refDay)
        val b = sentence(second, refDay)
        val glue = if (a.endsWith(".") || a.endsWith("!") || a.endsWith("?") || sep.contains('\n')) sep else ". "
        return Sample(a + glue + b, listOf(first, second), ref)
    }
}
