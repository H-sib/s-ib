package com.hsib.sib.core.motivation

/**
 * A line of encouragement for a deadline, light and a little cheeky, never harsh. Each line is two sentences chosen from a pool
 * for how close the date is: a remark, then a nudge. With 16 of each in seven pools, plus pools for a deadline already started
 * and for medical students, thousands of different lines come out of a few hundred sentences, so one person rarely sees a repeat.
 * The choice depends on the deadline and the day, so it stays the same all day and changes tomorrow.
 */
object Motivation {
    /** What the deadline is about, so the nudge can talk about the right kind of work. */
    enum class Group(val label: String) {
        ASSIGNMENT("Assignments"), EXAM("Exams"), APPLICATION("Applications"), MEETING("Meetings and appointments"),
        BILL("Bills and renewals"), PERSONAL("Personal and other"), CLOSE("Close ones"),
    }

    fun groupFor(c: com.hsib.sib.core.model.Category): Group = when (c) {
        com.hsib.sib.core.model.Category.ASSIGNMENT, com.hsib.sib.core.model.Category.REPORT -> Group.ASSIGNMENT
        com.hsib.sib.core.model.Category.EXAM, com.hsib.sib.core.model.Category.ITEM, com.hsib.sib.core.model.Category.CARD, com.hsib.sib.core.model.Category.TERM,
        com.hsib.sib.core.model.Category.PROF, com.hsib.sib.core.model.Category.SUPPLEMENTARY, com.hsib.sib.core.model.Category.PRACTICAL, com.hsib.sib.core.model.Category.WARD_FINAL -> Group.EXAM
        com.hsib.sib.core.model.Category.APPLICATION -> Group.APPLICATION
        com.hsib.sib.core.model.Category.MEETING, com.hsib.sib.core.model.Category.APPOINTMENT -> Group.MEETING
        com.hsib.sib.core.model.Category.BILL -> Group.BILL
        else -> if (c.close) Group.CLOSE else Group.PERSONAL
    }

    enum class Bucket { OVERDUE, TODAY, TOMORROW, FEW_DAYS, THIS_WEEK, TWO_WEEKS, FAR }

    fun bucket(daysLeft: Long): Bucket = when {
        daysLeft < 0 -> Bucket.OVERDUE
        daysLeft == 0L -> Bucket.TODAY
        daysLeft == 1L -> Bucket.TOMORROW
        daysLeft <= 3 -> Bucket.FEW_DAYS
        daysLeft <= 7 -> Bucket.THIS_WEEK
        daysLeft <= 14 -> Bucket.TWO_WEEKS
        else -> Bucket.FAR
    }

    private val remarks: Map<Bucket, List<String>> = mapOf(
        Bucket.OVERDUE to listOf(
            "This one slipped past, and that happens to the best of us.",
            "The date has gone by, but the work has not gone anywhere.",
            "Yes, it is late. Panic will not make it earlier.",
            "The deadline left without you, so let us go after it.",
            "Late is a word, not a verdict.",
            "Past due is a polite way of saying not done yet.",
            "Time travel is not available, so we work with now.",
            "The clock won this round. Round two starts now.",
            "It is overdue, which also means it is very familiar by now.",
            "Somewhere a calendar is judging you quietly. Ignore it.",
            "This is awkward for both of us, but it is fixable.",
            "The good news is that the bad news is already out.",
            "Overdue things get lighter once they start moving.",
            "You have been avoiding this one, and it has noticed.",
            "Missed the date, not the point.",
            "Rough day for the calendar, fine day to catch up.",
        ),
        Bucket.TODAY to listOf(
            "It is due today, and you are still here, which is a good sign.",
            "Plot twist: the deadline is today.",
            "Good news: the waiting is over. Bad news: so is the day.",
            "Today the calendar finally stopped bluffing.",
            "It is due today, and you clearly work well under pressure.",
            "The last day is the busiest day, so let us be efficient.",
            "Today you become the hero of your own last minute story.",
            "Time is short, but short is still workable.",
            "Everyone finds surprising focus on deadline day.",
            "This is the part of the film with the dramatic music.",
            "You have done harder things than this, probably before breakfast.",
            "Today is the final boss level of this task.",
            "Take a deep breath, because the day is long enough for a good effort.",
            "It is due today, so your excuses are fully booked.",
            "The sun is up, the clock is ticking, the work is waiting.",
            "Nobody ever regretted starting right now.",
        ),
        Bucket.TOMORROW to listOf(
            "The deadline is tomorrow, and the evening is still yours.",
            "One sleep left, so use the awake hours well.",
            "Tomorrow is close enough to smell the stress.",
            "Tomorrow arrives on schedule, unlike most buses.",
            "A deadline tomorrow is a deadline today wearing a disguise.",
            "You have a whole evening, which is a lot of minutes.",
            "Tomorrow-you is begging tonight-you for a head start.",
            "The calendar says tomorrow, and the calendar does not joke.",
            "Tonight is the quiet hero of tomorrow's deadline.",
            "There is still time, and time is the one thing you cannot print more of.",
            "Tomorrow is near, but nearly is not the same as late.",
            "The deadline is a sleep away, so let it find you prepared.",
            "This is the last evening where it is easy to be calm.",
            "Imagine how smug you will feel at breakfast.",
            "The tick of the clock is getting friendly.",
            "You are one good evening away from relief.",
        ),
        Bucket.FEW_DAYS to listOf(
            "A few days left: enough to do it well, not enough to dream about it.",
            "The deadline is close enough to see and far enough to beat.",
            "Two or three days is a perfectly respectable runway.",
            "The calendar is leaning in, but you still lead.",
            "This is the sweet spot between plenty of time and none at all.",
            "The deadline has started to look back at you.",
            "Days left are few, but they are all yours.",
            "Procrastination had a good run, and it ends here.",
            "Your future self is writing you a thank you note already.",
            "It is the point where plans turn into pages.",
            "The finish line is within shouting distance.",
            "A little effort each day beats a large panic on the last one.",
            "The pressure is not here yet, but it has booked a ticket.",
            "You can still choose calm, and calm is cheaper than rushing.",
            "A few days is a lot when you stop wasting them.",
            "This is where good students quietly pull ahead.",
        ),
        Bucket.THIS_WEEK to listOf(
            "A week or less, which is the perfect size for a decent plan.",
            "The deadline is this week, so the fun excuses expire soon.",
            "There is time to do this properly and still have a life.",
            "The week is long enough for a good job and short enough to matter.",
            "You are at the stage where small steps look big on paper.",
            "The deadline is not breathing down your neck yet, only warming up.",
            "Seven days is a lot of sunrises to waste.",
            "Think of today as the first of a few easy wins.",
            "Starting now means you get to enjoy the weekend, maybe.",
            "A gentle start this week saves a loud finish next.",
            "The clock is awake, but it is still polite.",
            "This is the last stretch where everything is still optional.",
            "A week feels long right up until it does not.",
            "Good news: nothing is on fire. Better news: you can keep it that way.",
            "The date is close enough to take seriously and far enough to enjoy it.",
            "Every great finish started with a boring Tuesday.",
        ),
        Bucket.TWO_WEEKS to listOf(
            "Two weeks is plenty, which is exactly what makes it risky.",
            "You have time, and time loves to be wasted early.",
            "The deadline is far enough to ignore and near enough to plan.",
            "A fortnight is the comfortable zone where good habits start.",
            "Nothing is urgent, which is the best moment to begin.",
            "The calendar is still smiling, so keep it that way.",
            "You can finish this early and be insufferably relaxed.",
            "It is not scary yet, and starting small keeps it that way.",
            "Two weeks out, the best plan is a boring one.",
            "A little today is worth a lot of tomorrows.",
            "The deadline is a distant relative who is coming to visit.",
            "Get ahead now and you will be the calm one later.",
            "You are in the quiet part of the story, so make a good outline.",
            "Early effort is the cheapest kind.",
            "Plenty of time is a gift, so unwrap it.",
            "The best day to start was yesterday, and the second best is a sensible today.",
        ),
        Bucket.FAR to listOf(
            "The deadline is far away, which is a lovely place to begin.",
            "You have loads of time, and loads of time is how late things are born.",
            "Nothing to worry about yet, so start before there is.",
            "Distance makes everything look easy, so test it with a first step.",
            "The date is a speck on the horizon, but specks grow.",
            "There is so much room that a small start looks silly, and works anyway.",
            "Plenty of time: the dangerous kind.",
            "A far deadline is a free head start waiting to be claimed.",
            "A calm month beats a frantic night.",
            "You could ignore this for now, and the future version of you will not thank you.",
            "The good plans are made when nothing is on fire.",
            "Long deadlines grow longer in the mind and shorter on the calendar.",
            "This is the cheapest time to be organised.",
            "Today's tiny step is tomorrow's invisible relief.",
            "Starting early is a superpower that looks like boredom.",
            "A little progress now makes the deadline much less dramatic.",
        ),
    )

    private val nudges: Map<Bucket, List<String>> = mapOf(
        Bucket.OVERDUE to listOf(
            "Open it and write one ugly sentence.",
            "Set a timer for twenty minutes and see what happens.",
            "Do the smallest piece first and let momentum help.",
            "Tell whoever needs to know, then get to it.",
            "Start with the easiest part, no ceremony.",
            "Put the phone face down and begin.",
            "Ten minutes now beats an hour of dread later.",
            "Draft it badly first and fix it after.",
            "Make tea, sit down, begin.",
            "Finish one piece before anything else.",
            "Ask about an extension if you need one, and work while you wait.",
            "Tick one small box and the rest gets easier.",
            "Close the extra tabs and keep only this one.",
            "Your future self will sleep better for it.",
            "Begin in the middle if the start feels heavy.",
            "Done is the goal and polish is optional.",
        ),
        Bucket.TODAY to listOf(
            "Cancel the optional things and start now.",
            "Work in short bursts and keep moving.",
            "Do the part that counts the most first.",
            "Skip perfection and deliver something good.",
            "Silence the notifications and begin.",
            "Aim for finished, then improve what time allows.",
            "Set a timer and do not negotiate with it.",
            "Eat something, drink water, then go.",
            "Submit early if you can and breathe out.",
            "Check the requirements once, then stop checking.",
            "Write the answer before you write the introduction.",
            "One focused hour will move this a long way.",
            "Start with the section you fear most.",
            "Keep a list of what remains and cross it off loudly.",
            "Tell people you are busy until it is done.",
            "Begin before you feel ready, because nobody ever does.",
        ),
        Bucket.TOMORROW to listOf(
            "Do the hardest part tonight and the rest tomorrow will feel kind.",
            "Plan the evening in three blocks and stick to them.",
            "Gather everything you need now, so tomorrow has no surprises.",
            "Work until the main part is done, then sleep properly.",
            "Write down what is left and attack it in order.",
            "Leave the screen distractions for after.",
            "Finish a draft tonight, polish it in the morning.",
            "Pack, print or prepare whatever you need before bed.",
            "Take a short break every hour and keep the rhythm.",
            "Set an alarm for tomorrow and a timer for tonight.",
            "Choose the one thing that must be finished, and finish it.",
            "Be kind to yourself and be busy anyway.",
            "Start now, stop at a sensible hour, and rest.",
            "Check the details once, then trust your work.",
            "Begin with ten minutes and see how far the momentum carries you.",
            "Do not wait for motivation, it arrives after you start.",
        ),
        Bucket.FEW_DAYS to listOf(
            "Split it into three days and give each day one job.",
            "Do a first pass today so nothing is left blank.",
            "Block out a clear hour each day and protect it.",
            "Start with the outline and the rest will follow.",
            "Plan the last day as a buffer, not as a plan.",
            "Do the awkward part first while you have energy.",
            "Write the rough version, then improve it a little every day.",
            "Tell a friend your plan so someone is watching, gently.",
            "Take the first step before you reach for your phone.",
            "A short session today will make tomorrow lighter.",
            "Keep your notes in one place and use them.",
            "Mark the middle day as check-in day.",
            "Begin with what you already know.",
            "Make a short list and cross out the first line today.",
            "Be early to your own work and sit down on time.",
            "One steady hour a day does more than a long panic.",
        ),
        Bucket.THIS_WEEK to listOf(
            "Pick a daily time slot and stay loyal to it.",
            "Start with research or reading while it is still easy.",
            "Break the job into pieces and give each a day.",
            "Make the first hour today and reward yourself after.",
            "Do a little each day and enjoy the quiet.",
            "Keep your workspace tidy, because it helps more than it sounds.",
            "Write the headings now and fill them in later.",
            "Set the first small goal for today and meet it.",
            "Schedule the boring parts before the fun parts.",
            "Leave room for surprises by starting early.",
            "Start today so you are not the person who starts tomorrow.",
            "A calm outline now is a finished draft later.",
            "Treat today as a warm up, and warm up properly.",
            "Share the plan with someone and let them nag politely.",
            "Aim for steady, not heroic.",
            "Start small and let the habit do the heavy lifting.",
        ),
        Bucket.TWO_WEEKS to listOf(
            "Spend today on a plan, then follow it lazily and consistently.",
            "Set three milestones and write them on paper.",
            "Do one small thing today just to break the seal.",
            "Collect the materials now and thank yourself later.",
            "Start with the part you are curious about.",
            "Put one session in your week and keep it.",
            "Begin with a rough outline and call it progress.",
            "Reserve the last two days as a safety net.",
            "Work for twenty minutes and call it a win.",
            "Choose the order of the work now and relax.",
            "Tell yourself you are only starting, then keep going.",
            "Make a folder, name it properly, and begin.",
            "Make progress boring and regular.",
            "Start gently and keep a steady pace.",
            "Draw up a plan and let the plan work.",
            "Pick a start day and make it today.",
        ),
        Bucket.FAR to listOf(
            "Write down what done looks like and stop there for today.",
            "Spend ten minutes on a first idea and leave it to grow.",
            "Put a small session on the calendar this week.",
            "Gather what you will need, quietly and early.",
            "Make a rough plan and let it ripen.",
            "Start a notes page and put one line in it.",
            "Tell yourself it is only a look around, then look around.",
            "Choose a first small step and take it this week.",
            "Break it into pieces now and keep them for later.",
            "Set a reminder to begin well before you need to.",
            "Take five minutes today to understand the task properly.",
            "Pick the first topic and read about it for a bit.",
            "Make friends with the task early, and it becomes less scary.",
            "A tiny start today keeps the panic away for weeks.",
            "Look at it once a week and the date will never surprise you.",
            "Begin lightly, finish gracefully.",
        ),
    )

    /** Extra nudges that only make sense for one kind of deadline. They are mixed in with the time based ones. */
    private val byGroup: Map<Group, List<String>> = mapOf(
        Group.ASSIGNMENT to listOf(
            "Open the brief again and underline what is actually asked.",
            "Write the headings first, the paragraphs will queue up behind them.",
            "Write the part you know best and the rest will feel smaller.",
            "Check the word count early so it holds no surprises.",
            "Keep the sources in one tab and the writing in another.",
            "Draft the introduction last, it is easier when you know what you said.",
            "Read the marking guide once, then write for it.",
            "Save the file now, name it properly and back it up.",
            "Start with the conclusion if you are stuck, then work backwards.",
            "Ask your teacher one clear question rather than worry about ten.",
        ),
        Group.EXAM to listOf(
            "Do a past paper under time and mark it honestly.",
            "Teach the hardest topic to an imaginary friend.",
            "Write one page of key points from memory, then check it.",
            "Sleep is revision too, so protect it.",
            "Mix the topics instead of reading one for hours.",
            "Test yourself first, read after, and you will remember more.",
            "List the three topics that scare you and give each a short slot.",
            "Pack the bag, pen, ID and water the night before.",
            "Study in blocks of forty minutes with short breaks.",
            "Skip the new material the night before and revise what you know.",
        ),
        Group.APPLICATION to listOf(
            "Gather the documents in one folder before you fill anything in.",
            "Read the instructions once and tick each requirement.",
            "Write the personal statement in a plain document first.",
            "Ask for the reference letter today and not tomorrow.",
            "Fill in the easy fields now and save the rest for a calm hour.",
            "Check the submission portal opens before the last hour.",
            "Keep a copy of everything you send.",
            "Proofread out loud once and then let it go.",
        ),
        Group.MEETING to listOf(
            "Write the three things you want from the meeting.",
            "Check the place and time once, then stop worrying.",
            "Prepare one good question and bring it.",
            "Plan how you will get there and add a spare ten minutes.",
            "Have your papers ready and your phone charged.",
            "Send a short note to confirm if it helps you relax.",
            "Take a pen, because notes beat memory.",
            "Arrive a little early, it is the cheapest advantage there is.",
        ),
        Group.BILL to listOf(
            "Pay it now and enjoy the quiet.",
            "Check the amount, pay, and save the receipt.",
            "Set the automatic payment if the app allows it.",
            "Do the dull thing first so the day feels lighter.",
            "Late fees love procrastinators, so deny them.",
            "Open the payment page and finish it in two minutes.",
            "Note the next due date so you never have to remember again.",
            "Take a screenshot of the receipt for later.",
        ),
        Group.PERSONAL to listOf(
            "Decide the first small step and take it today.",
            "Tell one person what you plan, and it becomes real.",
            "Put it in the morning, when your will is strongest.",
            "Make it small enough that you cannot say no.",
            "Reward yourself the moment you finish.",
            "Do it once and see how it feels.",
        ),
    )

    private val closeRemarks = listOf(
        "A date that matters is coming, and you are the kind of person who remembers.",
        "Someone will be happy you planned ahead.",
        "Small effort, big smile: that is the whole formula.",
        "The best gifts are thought of early.",
        "People remember who remembered.",
        "Thoughtfulness is a habit, and you keep it well.",
        "A little planning makes the day feel unhurried.",
        "Love likes a head start.",
        "A kind gesture does not need a big budget, only a bit of time.",
        "The day will go better for being prepared.",
        "Good memories rarely happen at the last minute.",
        "Someone is lucky to have you thinking of them.",
    )

    private val closeNudges = listOf(
        "Think of one thing they would love and write it down.",
        "A short message today can warm the whole week.",
        "Book the table or the ticket before it fills.",
        "Pick the card or the gift and ask yourself if it sounds like them.",
        "Add a small surprise to the plan.",
        "Call them today, just to hear their voice.",
        "Choose a time to remind yourself again.",
        "Do one thoughtful thing, however small.",
        "Take a photo you can print for them.",
        "Keep the plan simple and make it sincere.",
        "Write down a memory you share and tell it on the day.",
        "Make sure the day is free and the phone is off.",
    )

    /** For a deadline that has already been worked on: the nudge changes to praise and keeps the push gentle. */
    private val started = listOf(
        "You have already started, which puts you ahead of most people.",
        "The hardest part was starting, and you did it. Keep that going.",
        "Progress is on the board, and it looks good on you.",
        "You are not at zero any more, and that is a real difference.",
        "Every minute you logged is still working for you.",
        "A good start is half a finish, so go for the other half.",
        "You are moving, and moving things tend to keep moving.",
        "The bar is filling. Give it a little more.",
        "Nice work so far. A little more today and you can relax.",
        "What you have done counts, so add a bit to it.",
        "Momentum is yours already, so use it.",
        "You started, which is the heroic bit. The rest is only steps.",
        "Keep the streak alive with one more small push.",
        "The version of you from earlier would be proud.",
        "You are building something, one session at a time.",
        "Steady beats fast, and you are steady.",
    )

    /** Extra nudges for medical students, mixed into the usual ones. */
    private val medical = listOf(
        "Do a round of past MCQs and see what the topic really wants.",
        "Explain the topic out loud as if you were in a viva.",
        "Draw the diagram once from memory, then check it.",
        "A quick look at the high yield points will do you good.",
        "Revise with a friend, because teaching is the best test.",
        "Make a one page summary and keep it near your bed.",
        "Read the topic once, then close the book and recall it.",
        "Sleep counts as revision, so do not skip it.",
        "A short break and some water will help the facts stay.",
        "Group the topic into three headings and learn those.",
        "Practise the case history out loud, slowly.",
        "Do ten questions now and mark them honestly.",
        "Colour the key points and look at them again tonight.",
        "Review yesterday's topic for five minutes before the new one.",
        "A cup of tea and one chapter is a perfectly good start.",
        "Treat the viva as a conversation, and practise the conversation.",
    )

    private val doneLines = listOf(
        "That is one thing off your mind, and onto the finished pile.",
        "Finished. Somewhere a calendar is quietly impressed.",
        "Done is a lovely word, and you earned it.",
        "You started it, you finished it. That is the whole trick.",
        "One less thing waiting for you. Go and enjoy the quiet.",
        "Well done. Your future self already sent a thank you note.",
        "Another one down, and it did not even see you coming.",
        "That took effort, and it shows. Take a proper break.",
        "The deadline never stood a chance.",
        "Finished early or finished late, finished is finished.",
        "Tick. That sound is you getting better at this.",
        "A small victory, and the good kind: the real kind.",
        "You made it look easy. It was not, and you did it anyway.",
        "Drink some water, stretch a little, and be proud for a minute.",
        "That is how it is done: a bit at a time, then all at once.",
        "Not everyone finishes things. You do.",
    )

    /** A line to say when something is finished. With a [name] some of them speak to the person. */
    fun doneLine(seedKey: String, epochDay: Long, name: String? = null): String {
        val h = Math.floorMod((seedKey.hashCode().toLong() * 31 + epochDay) * 2654435761L, doneLines.size.toLong()).toInt()
        val line = doneLines[h]
        val n = name?.trim()?.takeIf { it.isNotEmpty() } ?: return line
        return if (h % 2 == 0) "$n, " + line.replaceFirstChar { it.lowercase() } else line
    }

    /** How many different lines can come out, counting the extra pools. */
    fun possibleLines(): Int = Bucket.values().sumOf { b -> remarks.getValue(b).size * (nudges.getValue(b).size + started.size + medical.size + byGroup.values.maxOf { it.size }) } + closeRemarks.size * closeNudges.size

    /**
     * One line for a deadline [seedKey] on day [epochDay]. [daysLeft] picks the pool, [started] swaps in praise when work has begun,
     * and [medical] mixes in study nudges. The same inputs always give the same line, and tomorrow gives a different one.
     */
    fun line(daysLeft: Long, seedKey: String, epochDay: Long, started: Boolean = false, medical: Boolean = false, group: Group = Group.PERSONAL, name: String? = null): String {
        val plain = plainLine(daysLeft, seedKey, epochDay, started, medical, group)
        // About one line in three speaks to the person by name, so it feels personal without becoming a habit.
        val n = name?.trim()?.takeIf { it.isNotEmpty() } ?: return plain
        return if (Math.floorMod((seedKey.hashCode().toLong() + epochDay) * 31, 3L) == 0L) n + ", " + plain.replaceFirstChar { it.lowercase() } else plain
    }

    private fun plainLine(daysLeft: Long, seedKey: String, epochDay: Long, started: Boolean, medical: Boolean, group: Group): String {
        val b = bucket(daysLeft)
        var h = (seedKey.hashCode().toLong() * 31 + epochDay) * 2654435761L
        h = h xor (h ushr 17)
        if (group == Group.CLOSE) {
            return closeRemarks[Math.floorMod(h, closeRemarks.size.toLong()).toInt()] + " " + closeNudges[Math.floorMod(h ushr 8, closeNudges.size.toLong()).toInt()]
        }
        val r = remarks.getValue(b)
        val n = buildList {
            addAll(nudges.getValue(b))
            // The kind of deadline is mixed in twice so it is heard more often than the general nudges.
            byGroup[group]?.let { addAll(it); addAll(it) }
            if (started) addAll(Motivation.started)
            if (medical) addAll(Motivation.medical)
        }
        val a = Math.floorMod(h, r.size.toLong()).toInt()
        val c = Math.floorMod(h ushr 8, n.size.toLong()).toInt()
        return r[a] + " " + n[c]
    }
}
