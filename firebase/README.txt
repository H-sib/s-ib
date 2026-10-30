S-IB: THE FIREBASE SIDE OF SHARING AND CHAT
Written 2026-10-14. Plain text on purpose. Read docs\FRIENDS_AND_CHAT_DESIGN.txt first.

STATE OF THINGS (honest)
  - The app side is built and tested against an in memory server (FakeBackend), with real sealing and key wrapping.
  - firestore.rules is written to say the same thing as FakeBackend. It has NOT been run against the Firebase emulator.
  - tests\rules.test.js is written for the emulator and has NOT been run. Running it needs downloads (Node packages,
    firebase-tools, and the Firestore emulator, which firebase-tools fetches itself). That needs the owner's approval.
  - No Firebase project exists. No FirestoreBackend and no Google sign in code exists yet. Release builds contain no sharing
    code path that can reach the network, and the release manifest still has no INTERNET permission.

WHAT THE OWNER DOES (nobody else can)
  1. Create the Firebase project (suggested id s-ib-app) in the owner's Google account. Stay on the free Spark plan.
  2. Add an Android app, package com.hsib.sib. Add the SHA-1 and SHA-256 fingerprints of: the debug key, the upload key
     (./gradlew signingReport prints them), and later the Play App Signing key from Play Console. A missing one makes sign in fail.
  3. Authentication, Sign-in method: turn on Google. Note the Web client ID Firebase creates; the app needs that one.
  4. Firestore Database: create it in production mode. CHOOSE THE REGION CAREFULLY, it cannot be changed. Pick the one nearest
     most of your friends.
  5. App Check: register the Android app with Play Integrity, then switch enforcement on for Firestore once the app sends tokens.
  6. Deploy the rules: firebase deploy --only firestore:rules (needs firebase-tools and a login, which only the owner can do).
  7. Download google-services.json into app\. It is project configuration, not a password, but it stays out of git.
  8. Optional later: the Blaze plan with a budget alert, only if instant chat notifications are wanted.

THE DOCUMENTS THE APP WRITES (so the real backend matches the rules)
  Times are Firestore timestamps and are mapped to and from the milliseconds the app uses. Where the rules say
  "== request.time" the app writes serverTimestamp().
  users/{uid}                       displayName, publicKey (base64url, 43 characters)
  users/{uid}/rooms/{roomId}        joinedAt            (a private list of my rooms; written with the member record)
  rooms/{roomId}                    ownerUid, keyVersion, rev, sealedPayload, memberCount, createdAt
  rooms/{roomId}/members/{uid}      role OWNER or MEMBER, displayName, joinedAt, doneAt (absent until done), wrappedKey,
                                    wrappedKeyVersion, lastMessageAt, inviteId (joining only)
  rooms/{roomId}/messages/{id}      senderUid, createdAt, kind TEXT or SYSTEM, sealedBody, keyVersion
  invites/{inviteId}                roomId, createdByUid, expiresAt, revoked, sealedKey, keyVersion
  reports/{id}                      roomId, reportedUid, reporterUid, messageId, reason, detail, quote, createdAt

  Writes that must be ONE batch (the rules check the "after" state):
    make a room        room + owner member record + users/{me}/rooms/{room}
    join               my member record (with inviteId) + room.memberCount + 1 + users/{me}/rooms/{room}
    leave              delete my member record + room.memberCount - 1 + delete users/{me}/rooms/{room}
    send a message     the message + my member lastMessageAt = serverTimestamp()
    remove a person    room (keyVersion + 1, memberCount - 1, sealedPayload sealed again) + delete that member
                       + every remaining member's wrappedKey and wrappedKeyVersion + delete users/{them}/rooms/{room}
    delete a room      delete every message, every member record, then the room, in batches of up to 500 writes
  Firestore limits rule lookups (exists, get, getAfter) to 20 per batch. A removal in a room of 20 people may come close to
  that; the emulator tests must include it.

WHAT THE RULES CANNOT DO (said plainly)
  - They cannot count a person's rooms, so the limit of 50 rooms is enforced by the app only. Someone with a modified app
    could make many rooms. App Check and the free quota limit the harm; a Cloud Function (Blaze plan) would close it.
  - They cannot send a push notification or clean up after someone who deletes their account from the web page. Deleting a
    room's documents, a person's messages and their profile is done by the app (deleteMyData). A request made from the web
    page after uninstalling is handled by the owner by hand in the Firebase console, unless a Cloud Function is added.
  - They cannot read sealed text, which is the point. Moderation therefore works from reports that include the text the
    reporter chose to send.

COST (free Spark plan; figures from third party summaries, to be checked against Firebase's own page before relying on them)
  Firestore reads about 50,000 a day, writes about 20,000 a day. The app keeps reads low: profiles are fetched again only when
  the people change or every six hours, a full sync runs when the app opens and then every ten minutes while it is open, and an
  open chat asks for new messages every six seconds (one small query when nothing is new). When the free quota is used up the
  service stops until the next day instead of charging. Snapshot listeners instead of polling will cut this further when the
  real backend is written.

FILES HERE
  firestore.rules        the rules
  firebase.json          points the Firebase tools at the rules
  firestore.indexes.json no extra indexes are needed by the queries the app makes
  tests\                 emulator tests for the rules (not yet run)
