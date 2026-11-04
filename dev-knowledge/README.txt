H-SIB APP DEVELOPMENT KNOWLEDGE BASE (plain text on purpose, no .md files)
Owner: Chowdhury Hasibur Rahaman ("H-sib"). Built 2026-11-01 from real project records. Read this file first, then only what the task needs.

WHAT THIS IS
One place that holds how the owner likes to work, his design taste, the workflow that worked, how real-phone testing was done,
the mistakes already made, and the parts worth reusing. It is NOT an app. Every new app session should start here.

EVIDENCE RULE
Every statement carries a tag: SEEN (looked at an image or a phone), MEASURED (a number from a run), READ (written in a repo file or
the owner's own words), ASSUMED (a guess, treat as open). Source of each fact: dev-knowledge/10_SOURCES_AND_GAPS.txt. Do not invent.

FILES
 01_WORKING_WITH_THE_OWNER.txt   who he is, how to talk to him, hard rules, what he never wants
 02_UI_UX_TASTE.txt              hierarchy, type, space, colour, cards, nav, buttons, motion, states, accessibility
 03_DEVELOPMENT_WORKFLOW.txt     how a task goes from idea to release; branches, checks, handoff, release
 04_DEVICE_TESTING_AND_USB.txt   real phone testing, what is evidenced, what is not
 05_FAILURE_DATABASE.txt         what went wrong, cause, how found, fix, prevention
 06_REUSABLE_PATTERNS.txt        reusable parts and techniques, kept apart from app-specific ones
 07_ITERATIVE_IMPROVEMENT.txt    the examine / fix / test / regress / refine loop
 08_NEW_APP_KICKOFF.txt          the steps for starting a brand new app
 09_LEARNING_LOOP.txt            how to add lessons after each milestone without losing old ones
 10_SOURCES_AND_GAPS.txt         what was read, what could not be recovered
 projects/                       app-specific records (kept apart on purpose)
 templates/                      files to copy into a new project or into the user level Claude folder

HOW TO USE IN A NEW PROJECT (short)
 1. Keep this folder in its own private GitHub repo (suggested name: hsib-dev-knowledge). Copy the folder into each new app repo as
    dev-knowledge/ (or add it as a git submodule).
 2. Copy templates/CLAUDE_TEMPLATE.txt to the new repo root and save it as CLAUDE.md.
 3. Once, copy templates/GLOBAL_USER_CLAUDE_TEMPLATE.txt to ~/.claude/CLAUDE.md on the PC (Windows: C:\Users\<name>\.claude\CLAUDE.md).
    Then Claude reads the rules in every project, even before the folder is copied.
 4. In the new session say: "New app: <idea>. Read dev-knowledge/README.txt first."

RULE FOR CLAUDE FILES: keep a project's own CLAUDE.md short (under about 60 lines). Details live in these files, not there.
