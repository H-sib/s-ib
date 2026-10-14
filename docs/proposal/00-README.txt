# H-sib Deadline Intelligence: Product Proposal v0.1

NAME UPDATE (2026-10-15): the product is now called S-IB (pronounced 'sib'; applicationId com.hsib.sib). The shortlist below records how
the name was reached and is kept for reference. Every later document and the code use S-IB.


Author: Claude (as technical cofounder), for Chowdhury Hasibur Rahaman (H-sib)
Date: 2026-10-14
Status: Proposal only. No application code has been written.

**Product stance (updated 2026-10-14 on your instruction):** this is a global, locale-neutral product. It is not a Bangladesh app and does not assume any country, language, weekend, calendar, or institution. Your own story (forgetting a university admission test while preparing for several) is the founder origin story, and it is told without a region. Where a regional or language choice has to be made, the design uses a **locale setting plus add-on language packs**, never a hard-coded default.

## How this proposal is organised

| File | Covers (your numbering from the master prompt) |
|---|---|
| `00-README.txt` (this file) | Name shortlist, executive summary, verified-facts register |
| `01-product-and-discovery.txt` | 1 Identity, 2 Innovation, 3 Features, 4 Discovery feasibility, 5 ML, 6 Reminder intelligence |
| `02-experience-design.txt` | 7 Visual design, 8 Notification shade |
| `03-security-and-architecture.txt` | 9 Cryptography, 10 Security limits, 11 Stack, 12 Data model |
| `04-build-and-launch.txt` | 13 Journeys, 14 Roadmap, 15 Competitors, 16 Play readiness and money, 17 CV value, 18 Extra ideas, 19 Critical review, 20 Questions |

## Evidence tags used throughout

Every claim about Android, Google Play, or a third-party platform carries a tag so you can see how much weight it can bear.

- **[V]** Verified on 2026-10-14 by reading the official page itself.
- **[S]** Seen only in a search summary or secondary source. Probably right, confirm before relying on it.
- **[K]** From my background knowledge. Not re-verified in this session. Treat as a lead, not a fact.
- **[?]** Unresolved. Needs a decision, a device test, or a written answer from the platform owner.

## Executive summary (read this if nothing else)

1. **Build native: Kotlin + Jetpack Compose, local-first, no backend in v1.** Use the user's own Google Drive hidden app folder as an untrusted blob store for encrypted backups. Your running cost per user is close to zero, and there is no server for anyone to breach or subpoena.
2. **Your two strongest differentiators are the preparation planner and the privacy architecture, not message mining.** Message mining is the most constrained feature on Android. I recommend building it as a plug-in layer (`SourceAdapter`) that is added last, so the product never depends on it.
3. **Four honest verdicts that change your original vision:**
   - A true interactive dashboard in the notification shade is not possible. Android's "Live Updates" style explicitly lists upcoming calendar events as a prohibited use [V]. The closest compliant set is an ongoing "Today" notification, a Quick Settings tile, and a home-screen widget.
   - Gmail access is a Google "restricted scope" with its own verification process [V]. It is doable but slow, so it should not be on the critical path.
   - WhatsApp and Telegram expose no ordinary API to a personal-chat reader. Realistic options are share-sheet, copy-paste, and chat-export import.
   - On-device OCR from ML Kit covers only five script families (Chinese, Devanagari, Japanese, Korean, Latin) [V]. A global product therefore ships **English and Latin-script languages first**, with other languages added as locale packs as data and demand justify them. Multilingual support is a staged capability, not a launch promise.
4. **"Developer cannot read your data" can be made architecturally true for server-side access** (there is no server), but not against a malicious app update. Section 10 of the security file spells out exactly what you can and cannot claim.
5. **Challenge to your scope:** "restore on a new device" is a much smaller problem than "continuous multi-device sync." I recommend end-to-end encrypted backup and restore first, and treating true sync as a later phase.
6. **Pick a launch wedge, even for a global product.** A global app still needs one first audience. I recommend students and applicants (exams, assignments, applications), because their deadlines are the most numerous, the most announced-in-advance, and the best served by structured sources (learning-management systems, calendar feeds). Professionals and households come after.

---

## Name shortlist

You are Chowdhury Hasibur Rahaman. Two threads in that name give honest, non-regional material:

- **Hasib** comes from the Arabic root h-s-b, "to reckon." Hasib is "one who reckons or counts." A deadline app is a reckoner of dates, so the meaning fits the product. (Note: "Al-Hasib" is also one of the names of God, so I avoid bare "Hasib" as a commercial product name.)
- **Your initials and "H-sib"** give sound material for a coined word: the "sib" of Hasib and the "ra" of Rahaman.

I searched the Google Play store for each candidate on 2026-10-14. These are first-page results only, and this is not trademark clearance.

| # | Name | Where it comes from | Why it fits | Risk | Play search result |
|---|---|---|---|---|---|
| 1 | **Sibra** | Coined: "sib" from Hasib, "ra" from Rahaman. Say it SIB-ra. | Short, global, ownable, a personal monogram. Reads like a real product name in any language. | Means nothing to strangers, so it needs a tagline. "SIB" is also a bank acronym, so search results sit next to bank apps. | No app named Sibra. Unrelated "Sibra Résa" (transit) and "SIBRA SPOTTERON" exist. |
| 2 | **Computus** | Latin for the medieval science of calculating calendar dates (famously Easter). | The meaning is the product: a reckoner of dates, which is what Hasib means. Serious tone suits your research profile. Gives a rich visual world (almanac). | Heavy and academic for a consumer app. Some people will not know how to say it. | No app named Computus in first results. |
| 3 | **Sibyl** | The oracle of antiquity. Contains "sib". | Foresight metaphor: "sees collisions before they happen." | Common word. One dream-interpreter app named Sibyl exists. Sounds like "Sybil attack," a security term, which could amuse or confuse security readers. | One unrelated app (dream interpreter). |
| 4 | **Sibori** | Coined, softer cousin of Sibra. | Melodic, easy in many languages, calm tone. | Pure invention, even less meaning than Sibra. | No app named Sibori. Near names: Sibro, Sibr, Sibo. |
| 5 | **Reckoner** | The English word for what "Hasib" means. | Plain-English meaning, instantly understood. | Generic, hard to own. "Reckon" is an accounting-software name [K]. | No exact match. Two titles start with "Reckoner" (a game, a watch face). |

Dropped after checking:
- **Hisab, Meyad, Prohori:** regional-language names. Dropped because the product is global. They also collided: at least 12 finance apps use "Hisab," and "Meyad: Expiry & Warranty" is live next door.
- **Ephemeris:** several exactly named apps exist (astronomy and astrology).
- **Nasib:** a dating app named Nasib is live.
- **Wada:** "WADA" (World Anti-Doping Agency) dominates search.
- **Sibwise:** nothing literal on Play, but generic and forgettable.

**My recommendation:**

- **Product name: Sibra.** It is the most ownable, the easiest to say worldwide, and the only one literally built from your name.
- **Engine name: Computus.** Name the pure-Kotlin core library (temporal parser, extractor, planner, urgency model) "Computus." It gives you a second, serious name for the open-source or research side, and it is accurate.
- If you would rather the product name carry meaning, use Computus as the product. Everything else in this proposal works unchanged.

Before committing to any name, do these (I have done none of them): domain check, WIPO Global Brand Database, USPTO and your local trademark office, and a fresh Play search. "H-sib" stays the publisher identity either way.

---

## Verified-facts register

| Claim | Tag | Source |
|---|---|---|
| Gmail read scopes (`gmail.readonly`, `gmail.metadata`, `gmail.modify`, `mail.google.com`) are **restricted** and need restricted-scope verification. A security assessment is required if you store restricted data on servers or transmit it. The page says nothing about device-only apps. | [V] | https://developers.google.com/workspace/gmail/api/auth/scopes |
| A 2018 Google post says apps that keep data only on the device skip the full assessment but are still verified as non-malicious. Old, so ask Google in writing. | [S] | https://developers.googleblog.com/en/elevating-user-trust-in-our-api-ecosystem/ |
| Live Updates (promoted ongoing notifications) forbid upcoming calendar events, ambient info about upcoming events, alerts, chat messages, and `RemoteViews`. They must be user-initiated, ongoing, time-sensitive activities. | [V] | https://developer.android.com/develop/ui/views/notifications/live-update |
| Promotion needs `POST_PROMOTED_NOTIFICATIONS`, `requestPromotedOngoing`, `setOngoing(true)`, a title, no custom view, not colorized, not `IMPORTANCE_MIN`. | [V] | same page |
| `SCHEDULE_EXACT_ALARM` is denied by default for new installs targeting Android 13+, and is denied after a backup restore onto Android 14. `USE_EXACT_ALARM` is auto-granted but limited by Play policy. Alarms are cleared on reboot; reschedule on `BOOT_COMPLETED`, which only arrives after the user has launched the app once. | [V] | https://developer.android.com/develop/background-work/services/alarms |
| Play policy allows `USE_EXACT_ALARM` only where precise timing is core (alarm, timer, calendar apps showing event notifications). The wording I found is a 2022 handout. | [S] | https://services.google.cn/fh/files/misc/play_policy_handout_202208.pdf |
| New apps and updates must target Android 16 (API 36) from 2026-08-31, with an extension request available to 2026-11-01. | [S] | https://developer.android.com/google/play/requirements/target-sdk |
| ML Kit on-device text recognition supports Chinese, Devanagari, Japanese, Korean, and Latin scripts. Other scripts (for example Bengali, Arabic, Cyrillic, Thai) are not listed. | [V] | https://developers.google.com/ml-kit/vision/text-recognition/v2 |
| ML Kit Entity Extraction returns date-time entities, is **beta**, and has no SLA or deprecation policy. The page I read has no language list, so per-language support is unconfirmed. | [V] | https://developers.google.com/ml-kit/language/entity-extraction/android |
| ML Kit GenAI (Gemini Nano) covers summarising, proofreading, rewriting, image description, runs via AICore, and started on Pixel hardware. It is not a date extractor. | [S] | https://android-developers.googleblog.com/2025/08/the-latest-gemini-nano-with-on-device-ml-kit-genai-apis.html |
| Android 15 stops untrusted notification listeners from reading unredacted content of notifications where an OTP is detected. | [S] | https://developer.android.com/about/versions/15/behavior-changes-all |
| Jetpack Security (`EncryptedSharedPreferences`) is deprecated; the reference page also warns not to back it up with Auto Backup because the key will be absent after restore. | [S] | https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences |
| `dataExtractionRules` has separate `cloud-backup` and `device-transfer` sections; old devices use `fullBackupContent`. | [S] | https://developer.android.com/identity/data/autobackup |
| `drive.appdata` is a non-sensitive Drive scope; the app-data folder is readable only by the app that created it and is hidden from the Drive UI. | [S] | https://developers.google.com/workspace/drive/api/guides/api-specific-auth |
| Block Store holds small restorable tokens and is end-to-end encrypted "when possible," so it must not be assumed always E2EE. | [S] | https://developer.android.com/identity/block-store |
| Play account deletion: apps that let users create an account must offer in-app deletion and a web deletion link, and declare it in Data safety. Accounts created and operated entirely offline are exempt. | [V] | https://support.google.com/googleplay/android-developer/answer/13327111 |
| New personal developer accounts (created after 2023-11-13) need a closed test with at least 12 testers opted in for 14 consecutive days before production access. | [S] | https://support.google.com/googleplay/android-developer/answer/14151465 |
| Telegram API Terms require a developer-owned `api_id`, prominent disclosure of API use, privacy care, and ban interference such as "ghost mode." I could not read the later sections. | [S] | https://core.telegram.org/api/terms |
| WhatsApp Android can export one chat at a time as a text file through the share sheet. I found no official way to share a single message to another app. | [S] | https://whatstk.readthedocs.io/en/latest/source/getting_started/export_chat.html |
| Gemini in Gmail can show an "Add to Calendar" button; at launch it was English-only and web-only. Pixel 10 Magic Cue surfaces Gmail/Calendar/Messages info on-device but I found no evidence it creates events from messages. | [S] | https://9to5google.com/2025/08/20/pixel-10-magic-cue-launch/ |
| Shovel and DormWay parse syllabi and sync LMS data (vendor-written descriptions). | [S] | https://dormway.app/blog/best-college-assignment-tracker-apps-2026 |
| Developer-side payouts: Google opened Play merchant accounts to developers in your country in 2017 (payouts in USD at that time). Current status not confirmed, and this concerns where **you** are paid, not where users are. | [S] | https://old.thefinancialexpress.com.bd/sci-tech/google-opens-merchant-account-in-bangladesh-1510055630 |
| Android 17: stricter memory checks for notifications with custom views (targeting API 37). Reports of a hidden-content App Lock come from a leaked build, not a shipped feature. | [S] | https://developer.android.com/about/versions/17/summary |
