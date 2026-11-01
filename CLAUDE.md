# H-sib Reminder: start here

Android app (package com.hsib.sib), owner H-sib (Chowdhury Hasibur Rahaman), live in Google Play closed testing. The owner has very little AI budget and is not an
Android expert: be short, plain, and careful.

1. Read `docs/HANDOFF.txt` first. It holds the whole context, his rules and taste, the current state, the release procedure and the pitfalls.
2. Then read `docs/FEEDBACK_LOG.txt` (open tester items) and the last 60 lines of `docs/RESUME.txt`.
3. Machine setup in a fresh cloud session: `bash tools/cloud-setup.sh`, then `./gradlew --version`.
4. Test narrowly first: `./gradlew :app:testDebugUnitTest --tests "<class>" -q`. Run `:core:test :app:testDebugUnitTest` once before a pull request.
5. Before every commit: `python3 tools/hygiene.py`, `python3 tools/words-check.py`.

Personal knowledge base for every app (taste, workflow, bugs, patterns): read `dev-knowledge/README.txt`.

Hard rules: no em dashes or en dashes anywhere; no new .md files (use .txt); commit as `Chowdhury Hasibur Rahaman <chr.siam7@gmail.com>` with no Co-Authored-By line;
never commit secrets (google-services.json, release/, keystores, local.properties); never change Firebase, Google Cloud or Play settings (tell the owner the exact clicks);
send nothing to anyone; keep his loved Home screen and week chart unchanged. Work on a branch and open a pull request. The owner builds and uploads the release on his own PC.
