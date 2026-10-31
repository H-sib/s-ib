package com.hsib.sib.core.medical

/**
 * The ready made setups a person can pick: a kind of university, a profession or a way of life. None of them claims to match one
 * college or one employer exactly, so every one can be edited, saved and shared.
 */
data class CourseTemplate(val group: String, val course: Course, val blurb: String)

object CourseTemplates {
    const val STUDY = "University and college"
    const val WORK = "Work and profession"
    const val LIFE = "Daily life"

    private val plainKinds = listOf("ITEM", "TERM", "PRACTICAL", "SUPPLEMENTARY")

    private fun study(name: String, field: String, unit: String, subjects: List<List<String>>, practicals: List<String>, blurb: String, kinds: List<String> = plainKinds, examMonth: Int = 12, suppMonth: Int = 6, duration: String = ""): CourseTemplate =
        CourseTemplate(
            CourseTemplates.STUDY,
            Course(name, field, subjects.mapIndexed { i, s -> CoursePhase("$unit ${i + 1}", "$unit ${i + 1} Final Exam", duration, s, practicals, "", kinds, examMonth, suppMonth) }),
            blurb,
        )

    private fun t(n: String, c: String, h: Int) = WorkType(n, c, h)

    private fun work(name: String, blurb: String, areas: List<Triple<String, List<String>, List<WorkType>>>): CourseTemplate =
        CourseTemplate(WORK, Course(name, "Work: $name", areas.map { (a, items, types) -> CoursePhase(a, subjects = items, types = types) }), blurb)

    private fun life(name: String, blurb: String, areas: List<Triple<String, List<String>, List<WorkType>>>): CourseTemplate =
        CourseTemplate(LIFE, Course(name, "Life: $name", areas.map { (a, items, types) -> CoursePhase(a, subjects = items, types = types) }), blurb)

    private val empty = List(4) { emptyList<String>() }

    private val study_and_work: List<CourseTemplate> = listOf(
        CourseTemplate(STUDY, Courses.mbbs, "Set up for a medical college: four profs with their subjects, items, cards, terms, practicals and exams."),
        CourseTemplate(
            STUDY,
            Course(
                "BDS", "Dental (BDS)", listOf(
                    CoursePhase("1st Prof", "1st Professional Exam", "", listOf("Anatomy", "Physiology", "Biochemistry", "Dental Anatomy"), listOf("Written practical", "Assignment"), "", listOf("ITEM", "TERM", "CARD", "PROF", "SUPPLEMENTARY", "PRACTICAL"), 11, 5),
                    CoursePhase("2nd Prof", "2nd Professional Exam", "", listOf("Pharmacology", "Pathology", "Microbiology", "Dental Materials"), listOf("Written practical", "Assignment"), "", listOf("ITEM", "TERM", "PROF", "SUPPLEMENTARY", "PRACTICAL"), 11, 5),
                    CoursePhase("3rd Prof", "3rd Professional Exam", "", listOf("Oral Pathology", "Periodontology", "Community Dentistry"), listOf("Written practical", "Case report"), "", listOf("ITEM", "TERM", "PROF", "SUPPLEMENTARY", "PRACTICAL"), 11, 5),
                    CoursePhase("4th Prof", "4th Professional Exam", "", listOf("Oral Surgery", "Prosthodontics", "Orthodontics", "Conservative Dentistry"), listOf("Short case", "Long case", "Case report"), "", listOf("TERM", "PROF", "SUPPLEMENTARY", "PRACTICAL", "WARD_FINAL"), 5, 11),
                ),
            ),
            "Set up for a dental college: four profs with their subjects, items, terms and exams. Dental colleges differ a lot, so check the subjects and edit what does not match.",
        ),
        study("Engineering university", "Engineering", "Year", listOf(listOf("Mathematics", "Physics", "Chemistry", "Programming", "English"), emptyList(), emptyList(), emptyList()), listOf("Lab report", "Assignment", "Project"), "Set up for an engineering university: four years with class tests, terms, labs and retakes. The first year has the common subjects. Add the rest."),
        study("University (general)", "Other", "Year", empty, listOf("Assignment", "Presentation", "Project"), "Set up for any bachelor degree: four years with class tests, term exams and retakes. Add your subjects."),
        study("Business school (BBA or MBA)", "Business", "Semester", List(8) { emptyList<String>() }, listOf("Case study", "Presentation", "Group project"), "Set up for business school: eight semesters with quizzes, mid terms, finals and group work."),
        study("Law (LLB)", "Law", "Year", empty, listOf("Moot court", "Assignment", "Viva"), "Set up for law school: four years with assignments, moots and viva."),
        study("Nursing", "Nursing", "Year", listOf(listOf("Anatomy", "Physiology", "Fundamentals of Nursing"), emptyList(), emptyList(), emptyList()), listOf("Clinical posting", "Care plan", "Assignment"), "Set up for nursing: four years with theory, clinical postings and care plans."),
        study("Pharmacy", "Other", "Year", listOf(listOf("Pharmaceutics", "Pharmaceutical Chemistry", "Pharmacology"), emptyList(), emptyList(), emptyList()), listOf("Lab report", "Assignment", "Project"), "Set up for pharmacy: four years with labs and projects."),
        study("College (HSC or A levels)", "School or college", "Year", List(2) { emptyList<String>() }, listOf("Practical", "Assignment"), "Set up for college: two years with class tests, terms and a board exam.", kinds = listOf("ITEM", "TERM", "PROF", "PRACTICAL"), examMonth = 4, suppMonth = 10),
        study("School", "School or college", "Term", List(3) { emptyList<String>() }, listOf("Homework", "Project"), "Set up for school: three terms a year with class tests and term exams."),

        work(
            "Corporate office", "Set up for office work in any company: reports, meetings, approvals and routine tasks.", listOf(
                Triple("Reports", listOf("Weekly report", "Monthly report", "Quarterly review"), listOf(t("Draft", "ASSIGNMENT", 3), t("Review", "ASSIGNMENT", 1), t("Submission", "APPLICATION", 1))),
                Triple("Meetings", listOf("Team meeting", "Client meeting", "Management review"), listOf(t("Preparation", "MEETING", 1), t("Presentation", "ASSIGNMENT", 4), t("Follow up", "OTHER", 1))),
                Triple("Admin", listOf("Expense claim", "Leave request", "Performance review"), listOf(t("Form", "APPLICATION", 1), t("Approval", "OTHER", 1), t("Payment", "BILL", 1))),
            ),
        ),
        work(
            "Manager or team lead", "Set up for people who lead a team: one to ones, planning, hiring and reviews.", listOf(
                Triple("Team", listOf("One to one", "Team planning", "Performance review"), listOf(t("Preparation", "MEETING", 1), t("Feedback", "OTHER", 1), t("Review", "ASSIGNMENT", 3))),
                Triple("Delivery", listOf("Project milestone", "Budget", "Roadmap"), listOf(t("Plan", "ASSIGNMENT", 4), t("Status report", "ASSIGNMENT", 2), t("Sign off", "APPLICATION", 1))),
                Triple("Hiring", listOf("Interview round", "Offer", "Onboarding"), listOf(t("Interview", "MEETING", 2), t("Decision", "OTHER", 1))),
            ),
        ),
        work(
            "Software developer", "Set up for developers: releases, reviews, bugs and learning.", listOf(
                Triple("Delivery", listOf("Sprint", "Release", "Hotfix"), listOf(t("Feature", "ASSIGNMENT", 8), t("Bug fix", "ASSIGNMENT", 3), t("Release", "OTHER", 2))),
                Triple("Quality", listOf("Code review", "Test plan", "Documentation"), listOf(t("Review", "ASSIGNMENT", 1), t("Tests", "ASSIGNMENT", 4), t("Write up", "ASSIGNMENT", 2))),
                Triple("Growth", listOf("Course", "Certification", "Side project"), listOf(t("Study", "EXAM", 6), t("Exam", "EXAM", 8))),
            ),
        ),
        work(
            "Teacher or lecturer", "Set up for teachers: lessons, marking, exams and meetings.", listOf(
                Triple("Teaching", listOf("Lesson plan", "Class test", "Lab session"), listOf(t("Prepare", "ASSIGNMENT", 2), t("Slides", "ASSIGNMENT", 3), t("Class", "MEETING", 1))),
                Triple("Marking", listOf("Assignments", "Exam scripts", "Project reports"), listOf(t("Mark", "ASSIGNMENT", 6), t("Enter results", "APPLICATION", 1), t("Feedback", "OTHER", 2))),
                Triple("School duties", listOf("Staff meeting", "Parent meeting", "Invigilation"), listOf(t("Meeting", "MEETING", 1), t("Report", "ASSIGNMENT", 2))),
            ),
        ),
        work(
            "Doctor or clinician", "Set up for practising doctors: duty rosters, cases, audits and exams.", listOf(
                Triple("Clinic and ward", listOf("Duty", "Ward round", "Case presentation"), listOf(t("Preparation", "MEETING", 1), t("Case write up", "ASSIGNMENT", 2), t("Follow up", "APPOINTMENT", 1))),
                Triple("Learning", listOf("Journal club", "Audit", "Postgraduate exam"), listOf(t("Reading", "EXAM", 4), t("Presentation", "ASSIGNMENT", 4), t("Exam", "EXAM", 20))),
                Triple("Admin", listOf("Licence renewal", "Insurance", "Rota"), listOf(t("Form", "APPLICATION", 1), t("Renewal", "BILL", 1))),
            ),
        ),
        work(
            "Nurse or healthcare worker", "Set up for nurses and health staff: shifts, training and records.", listOf(
                Triple("Shifts", listOf("Morning shift", "Evening shift", "Night shift"), listOf(t("Handover notes", "OTHER", 1), t("Care plan", "ASSIGNMENT", 2))),
                Triple("Training", listOf("Mandatory training", "Skills check", "Certification"), listOf(t("Course", "EXAM", 4), t("Test", "EXAM", 3))),
                Triple("Records", listOf("Patient records", "Incident report", "Leave"), listOf(t("Report", "ASSIGNMENT", 1), t("Request", "APPLICATION", 1))),
            ),
        ),
        work(
            "Lawyer", "Set up for lawyers: hearings, filings, client matters and billing.", listOf(
                Triple("Cases", listOf("Hearing", "Filing", "Appeal"), listOf(t("Research", "ASSIGNMENT", 6), t("Drafting", "ASSIGNMENT", 5), t("Court", "APPOINTMENT", 3))),
                Triple("Clients", listOf("Consultation", "Contract", "Advice letter"), listOf(t("Meeting", "MEETING", 1), t("Draft", "ASSIGNMENT", 3), t("Review", "ASSIGNMENT", 1))),
                Triple("Practice", listOf("Invoice", "Bar council renewal", "Continuing education"), listOf(t("Invoice", "BILL", 1), t("Renewal", "BILL", 1))),
            ),
        ),
        work(
            "Banker or finance", "Set up for banking and finance: closes, audits, reports and compliance.", listOf(
                Triple("Reporting", listOf("Month end close", "Quarter end", "Audit"), listOf(t("Reconcile", "ASSIGNMENT", 4), t("Report", "ASSIGNMENT", 4), t("Submit", "APPLICATION", 1))),
                Triple("Clients", listOf("Loan file", "Review meeting", "Onboarding"), listOf(t("Meeting", "MEETING", 1), t("Documents", "APPLICATION", 2))),
                Triple("Compliance", listOf("Training", "Policy attestation", "Regulatory filing"), listOf(t("Training", "EXAM", 2), t("Filing", "APPLICATION", 2))),
            ),
        ),
        work(
            "Accountant", "Set up for accountants: returns, payroll, audits and client deadlines.", listOf(
                Triple("Tax", listOf("Income tax return", "VAT return", "Withholding"), listOf(t("Prepare", "ASSIGNMENT", 5), t("File", "APPLICATION", 1), t("Pay", "BILL", 1))),
                Triple("Books", listOf("Monthly accounts", "Payroll", "Bank reconciliation"), listOf(t("Close", "ASSIGNMENT", 4), t("Review", "ASSIGNMENT", 2))),
                Triple("Clients", listOf("Audit", "Budget review", "Advisory meeting"), listOf(t("Meeting", "MEETING", 1), t("Report", "ASSIGNMENT", 4))),
            ),
        ),
        work(
            "Sales and marketing", "Set up for sales and marketing: targets, campaigns, pitches and follow ups.", listOf(
                Triple("Sales", listOf("Monthly target", "Client pitch", "Proposal"), listOf(t("Pitch", "MEETING", 3), t("Proposal", "ASSIGNMENT", 4), t("Follow up", "OTHER", 1))),
                Triple("Campaigns", listOf("Launch", "Newsletter", "Social calendar"), listOf(t("Plan", "ASSIGNMENT", 3), t("Content", "ASSIGNMENT", 5), t("Publish", "OTHER", 1))),
                Triple("Reports", listOf("Weekly numbers", "Monthly review"), listOf(t("Report", "ASSIGNMENT", 2))),
            ),
        ),
        work(
            "Government employee", "Set up for public service: files, approvals, audits and leave.", listOf(
                Triple("Files", listOf("Approval", "Circular", "Budget proposal"), listOf(t("Draft", "ASSIGNMENT", 3), t("Submit", "APPLICATION", 1), t("Follow up", "OTHER", 1))),
                Triple("Meetings", listOf("Departmental meeting", "Inspection", "Audit"), listOf(t("Preparation", "MEETING", 2), t("Report", "ASSIGNMENT", 3))),
                Triple("Personal service", listOf("Leave", "Annual report", "Training"), listOf(t("Application", "APPLICATION", 1), t("Course", "EXAM", 4))),
            ),
        ),
        work(
            "Freelancer or creator", "Set up for freelancers: client work, invoices, pitches and content.", listOf(
                Triple("Client work", listOf("Project", "Revision", "Delivery"), listOf(t("Draft", "ASSIGNMENT", 6), t("Revision", "ASSIGNMENT", 3), t("Delivery", "OTHER", 1))),
                Triple("Business", listOf("Invoice", "Tax", "Pitch"), listOf(t("Invoice", "BILL", 1), t("Pitch", "ASSIGNMENT", 3), t("Follow up", "OTHER", 1))),
                Triple("Content", listOf("Video", "Article", "Post"), listOf(t("Plan", "ASSIGNMENT", 2), t("Create", "ASSIGNMENT", 6), t("Publish", "OTHER", 1))),
            ),
        ),
        work(
            "Researcher or PhD student", "Set up for research: papers, grants, experiments and supervisor meetings.", listOf(
                Triple("Papers", listOf("Paper", "Thesis chapter", "Conference abstract"), listOf(t("Write", "ASSIGNMENT", 10), t("Revise", "ASSIGNMENT", 5), t("Submit", "APPLICATION", 2))),
                Triple("Experiments", listOf("Experiment", "Data analysis", "Lab booking"), listOf(t("Run", "ASSIGNMENT", 8), t("Analyse", "ASSIGNMENT", 6))),
                Triple("Supervision", listOf("Supervisor meeting", "Progress report", "Grant"), listOf(t("Meeting", "MEETING", 1), t("Report", "ASSIGNMENT", 4), t("Application", "APPLICATION", 6))),
            ),
        ),
        work(
            "Small business owner", "Set up for shop and business owners: stock, bills, staff and taxes.", listOf(
                Triple("Money", listOf("Supplier bill", "Rent", "Tax"), listOf(t("Pay", "BILL", 1), t("Record", "OTHER", 1))),
                Triple("Operations", listOf("Stock order", "Staff rota", "Licence renewal"), listOf(t("Order", "OTHER", 2), t("Renewal", "APPLICATION", 2))),
                Triple("Growth", listOf("Promotion", "New product", "Customer follow up"), listOf(t("Plan", "ASSIGNMENT", 3), t("Launch", "OTHER", 3))),
            ),
        ),
        work(
            "Engineer (site or plant)", "Set up for engineers on site: inspections, permits, maintenance and reports.", listOf(
                Triple("Site", listOf("Inspection", "Maintenance", "Commissioning"), listOf(t("Visit", "APPOINTMENT", 4), t("Report", "ASSIGNMENT", 3))),
                Triple("Permits", listOf("Permit", "Safety audit", "Certificate renewal"), listOf(t("Application", "APPLICATION", 2), t("Renewal", "BILL", 1))),
                Triple("Projects", listOf("Design review", "Milestone", "Handover"), listOf(t("Review", "ASSIGNMENT", 3), t("Drawing", "ASSIGNMENT", 8), t("Handover", "OTHER", 2))),
            ),
        ),
        work(
            "Designer", "Set up for designers: briefs, drafts, reviews and handover.", listOf(
                Triple("Projects", listOf("Brief", "Concept", "Final files"), listOf(t("Research", "ASSIGNMENT", 3), t("Draft", "ASSIGNMENT", 8), t("Final", "ASSIGNMENT", 4))),
                Triple("Reviews", listOf("Client review", "Design critique"), listOf(t("Presentation", "MEETING", 3), t("Revision", "ASSIGNMENT", 3))),
                Triple("Business", listOf("Invoice", "Portfolio", "Pitch"), listOf(t("Invoice", "BILL", 1), t("Update", "ASSIGNMENT", 3))),
            ),
        ),
    )

    private val lifeAll: List<CourseTemplate> = listOf(
        life(
            "Home and family", "Set up for running a home: bills, repairs, errands and family plans.", listOf(
                Triple("Household", listOf("Electricity bill", "Water bill", "Rent", "Repairs"), listOf(t("Pay", "BILL", 1), t("Fix", "OTHER", 3), t("Order", "OTHER", 1))),
                Triple("Family", listOf("Family visit", "Doctor visit", "School event"), listOf(t("Prepare", "PERSONAL", 2), t("Appointment", "APPOINTMENT", 2))),
                Triple("Shopping", listOf("Groceries", "Gifts", "Festival shopping"), listOf(t("List", "PERSONAL", 1), t("Shop", "PERSONAL", 2))),
            ),
        ),
        life(
            "Parent", "Set up for parents: homework help, school fees, health visits and activities.", listOf(
                Triple("School", listOf("Homework", "Exam week", "Parent meeting", "School fees"), listOf(t("Help", "PERSONAL", 2), t("Pay", "BILL", 1), t("Meeting", "MEETING", 1))),
                Triple("Health", listOf("Vaccination", "Check up", "Medicine refill"), listOf(t("Appointment", "APPOINTMENT", 2), t("Reminder", "PERSONAL", 1))),
                Triple("Activities", listOf("Sports day", "Birthday party", "Holiday plan"), listOf(t("Prepare", "PERSONAL", 3), t("Event", "PERSONAL", 3))),
            ),
        ),
        life(
            "Fitness and health", "Set up for health goals: training plans, check ups and healthy habits.", listOf(
                Triple("Training", listOf("Workout plan", "Race", "Weigh in"), listOf(t("Session", "PERSONAL", 1), t("Event", "PERSONAL", 3), t("Review", "PERSONAL", 1))),
                Triple("Health", listOf("Check up", "Blood test", "Medicine refill"), listOf(t("Appointment", "APPOINTMENT", 2), t("Refill", "PERSONAL", 1))),
                Triple("Habits", listOf("Sleep", "Water", "Meal plan"), listOf(t("Plan", "PERSONAL", 1))),
            ),
        ),
        life(
            "Hobby and creative", "Set up for makers and artists: projects, shows and learning.", listOf(
                Triple("Projects", listOf("Painting", "Song", "Writing", "Craft"), listOf(t("Sketch", "PERSONAL", 2), t("Create", "PERSONAL", 8), t("Finish", "PERSONAL", 3))),
                Triple("Shows", listOf("Exhibition", "Competition", "Open mic"), listOf(t("Prepare", "PERSONAL", 5), t("Entry form", "APPLICATION", 1))),
                Triple("Learning", listOf("Workshop", "Online class"), listOf(t("Class", "PERSONAL", 3), t("Practice", "PERSONAL", 2))),
            ),
        ),
        life(
            "Job hunting", "Set up for finding work: applications, interviews and skills.", listOf(
                Triple("Applications", listOf("CV", "Cover letter", "Application form", "Portfolio"), listOf(t("Write", "APPLICATION", 3), t("Submit", "APPLICATION", 1), t("Follow up", "OTHER", 1))),
                Triple("Interviews", listOf("Phone screen", "Interview", "Test task"), listOf(t("Prepare", "MEETING", 3), t("Interview", "MEETING", 2), t("Thank you note", "OTHER", 1))),
                Triple("Skills", listOf("Course", "Certificate", "Practice test"), listOf(t("Study", "EXAM", 6), t("Test", "EXAM", 3))),
            ),
        ),
        life(
            "Travel and events", "Set up for trips and big days: visas, tickets, bookings and parties.", listOf(
                Triple("Trip", listOf("Visa", "Tickets", "Hotel", "Packing"), listOf(t("Apply", "APPLICATION", 3), t("Book", "OTHER", 1), t("Pack", "PERSONAL", 2))),
                Triple("Event", listOf("Wedding", "Party", "Reunion"), listOf(t("Plan", "PERSONAL", 4), t("Invite", "PERSONAL", 2), t("Event", "PERSONAL", 4))),
                Triple("Budget", listOf("Deposit", "Final payment"), listOf(t("Pay", "BILL", 1))),
            ),
        ),
        life(
            "Volunteer and community", "Set up for community work: events, meetings and fundraising.", listOf(
                Triple("Events", listOf("Clean up day", "Food drive", "Awareness event"), listOf(t("Plan", "PERSONAL", 3), t("Event", "PERSONAL", 4))),
                Triple("Meetings", listOf("Committee", "Planning meeting"), listOf(t("Meeting", "MEETING", 1), t("Minutes", "ASSIGNMENT", 1))),
                Triple("Fundraising", listOf("Campaign", "Report to donors"), listOf(t("Draft", "ASSIGNMENT", 3), t("Report", "ASSIGNMENT", 3))),
            ),
        ),
        life(
            "Farming", "Set up for farmers: sowing, harvest, inputs and market days.", listOf(
                Triple("Crops", listOf("Sowing", "Irrigation", "Spraying", "Harvest"), listOf(t("Work", "OTHER", 6), t("Check", "OTHER", 1))),
                Triple("Inputs", listOf("Seed", "Fertiliser", "Loan instalment"), listOf(t("Order", "OTHER", 2), t("Pay", "BILL", 1))),
                Triple("Market", listOf("Market day", "Delivery", "Price check"), listOf(t("Prepare", "OTHER", 3), t("Sell", "OTHER", 4))),
            ),
        ),
        life(
            "Sports and coaching", "Set up for players and coaches: matches, training and registration.", listOf(
                Triple("Matches", listOf("League match", "Tournament", "Trial"), listOf(t("Prepare", "PERSONAL", 3), t("Match", "PERSONAL", 3))),
                Triple("Training", listOf("Team practice", "Fitness test", "Skills session"), listOf(t("Session", "PERSONAL", 2), t("Plan", "PERSONAL", 1))),
                Triple("Admin", listOf("Registration", "Kit order", "Fees"), listOf(t("Form", "APPLICATION", 1), t("Pay", "BILL", 1))),
            ),
        ),
        life(
            "Learning on my own", "Set up for self learners: courses, languages and certificates.", listOf(
                Triple("Courses", listOf("Online course", "Book", "Tutorial series"), listOf(t("Study", "PERSONAL", 3), t("Assignment", "ASSIGNMENT", 3), t("Exam", "EXAM", 6))),
                Triple("Languages", listOf("Vocabulary", "Speaking practice", "Language test"), listOf(t("Practice", "PERSONAL", 1), t("Test", "EXAM", 3))),
                Triple("Certificates", listOf("Application", "Renewal"), listOf(t("Apply", "APPLICATION", 2), t("Renew", "BILL", 1))),
            ),
        ),
        life(
            "Retired and relaxed", "Set up for a calmer life: health, family, interests and paperwork.", listOf(
                Triple("Health", listOf("Check up", "Medicine refill", "Eye test"), listOf(t("Appointment", "APPOINTMENT", 2), t("Refill", "PERSONAL", 1))),
                Triple("Family", listOf("Grandchildren visit", "Family gathering"), listOf(t("Prepare", "PERSONAL", 2), t("Visit", "PERSONAL", 3))),
                Triple("Paperwork", listOf("Pension", "Insurance", "Tax"), listOf(t("Form", "APPLICATION", 1), t("Pay", "BILL", 1))),
            ),
        ),
        life(
            "Festivals and gatherings", "Set up for festival seasons: shopping, cooking, gifts and travel home.", listOf(
                Triple("Preparation", listOf("Shopping", "Cleaning", "Cooking"), listOf(t("Plan", "PERSONAL", 2), t("Do", "PERSONAL", 4))),
                Triple("Gifts", listOf("Family gifts", "Friends gifts", "Charity"), listOf(t("Choose", "PERSONAL", 2), t("Buy", "PERSONAL", 2))),
                Triple("Travel", listOf("Tickets", "Packing", "Journey"), listOf(t("Book", "OTHER", 1), t("Pack", "PERSONAL", 2))),
            ),
        ),
    )

    val all: List<CourseTemplate> get() = study_and_work + lifeAll

    fun group(name: String): List<CourseTemplate> = all.filter { it.group == name }
}
