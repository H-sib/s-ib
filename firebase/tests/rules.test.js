// Tests for firebase/firestore.rules, written for the Firebase Firestore emulator.
// STATUS: written but NOT YET RUN. The emulator and the test libraries have not been downloaded (see firebase/README.txt).
// Run with:  npm install  then  npm test   inside firebase/tests.
//
// Each test names the permission it checks. They mirror the tests in SocialRepositoryTest, which run the same rules against
// FakeBackend, so a difference between the two is a bug in one of them.

const { test, before, after, beforeEach } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const {
  initializeTestEnvironment, assertSucceeds, assertFails,
} = require('@firebase/rules-unit-testing');
const {
  doc, getDoc, setDoc, updateDoc, deleteDoc, writeBatch, getDocs, collection, serverTimestamp, Timestamp, increment,
} = require('firebase/firestore');

let env;
const PUBLIC_KEY = 'A'.repeat(43);
const SEALED = 'c2VhbGVk'.repeat(20);
const ROOM = 'room1';

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'sib-rules-test',
    firestore: { rules: fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8') },
  });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });

const as = (uid, verified = true) => env.authenticatedContext(uid, { email_verified: verified, email: `${uid}@example.test` }).firestore();
const ts = (ms) => Timestamp.fromMillis(ms);
const hoursFromNow = (h) => ts(Date.now() + h * 3600 * 1000);

/** A room owned by "ana" with the listed extra members, written with the rules switched off. */
async function seed({ members = ['ben'], keyVersion = 1, memberCount, invite } = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'rooms', ROOM), {
      ownerUid: 'ana', keyVersion, rev: 1, sealedPayload: SEALED, memberCount: memberCount ?? 1 + members.length, createdAt: ts(1000),
    });
    const person = (role, name, v = keyVersion) => ({
      role, displayName: name, joinedAt: ts(1000), wrappedKey: 'k', wrappedKeyVersion: v, lastMessageAt: ts(1000),
    });
    await setDoc(doc(db, 'rooms', ROOM, 'members', 'ana'), person('OWNER', 'Ana'));
    for (const m of members) await setDoc(doc(db, 'rooms', ROOM, 'members', m), person('MEMBER', m));
    if (invite) await setDoc(doc(db, 'invites', 'ABCDEFGHJK'), {
      roomId: ROOM, createdByUid: 'ana', expiresAt: hoursFromNow(24), revoked: false, sealedKey: 'k', keyVersion, ...invite,
    });
  });
}

const newRoomBatch = (db, uid, over = {}) => {
  const b = writeBatch(db);
  b.set(doc(db, 'rooms', ROOM), {
    ownerUid: uid, keyVersion: 1, rev: 1, sealedPayload: SEALED, memberCount: 1, createdAt: serverTimestamp(), ...over,
  });
  b.set(doc(db, 'rooms', ROOM, 'members', uid), {
    role: 'OWNER', displayName: 'Ana', joinedAt: serverTimestamp(), wrappedKey: 'k', wrappedKeyVersion: 1, lastMessageAt: serverTimestamp(),
  });
  b.set(doc(db, 'users', uid, 'rooms', ROOM), { joinedAt: serverTimestamp() });
  return b;
};

const joinBatch = (db, uid, memberCount) => {
  const b = writeBatch(db);
  b.set(doc(db, 'rooms', ROOM, 'members', uid), {
    role: 'MEMBER', displayName: uid, joinedAt: serverTimestamp(), wrappedKey: 'k', wrappedKeyVersion: 1,
    lastMessageAt: serverTimestamp(), inviteId: 'ABCDEFGHJK',
  });
  b.update(doc(db, 'rooms', ROOM), { memberCount });
  b.set(doc(db, 'users', uid, 'rooms', ROOM), { joinedAt: serverTimestamp() });
  return b;
};

// ---- who is allowed in at all -------------------------------------------------------------------------

test('nobody signed out, or with an unverified email, can read anything', async () => {
  await seed();
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'rooms', ROOM)));
  await assertFails(getDoc(doc(as('ben', false), 'rooms', ROOM)));
  await assertSucceeds(getDoc(doc(as('ben'), 'rooms', ROOM)));
});

test('rooms and invites and profiles cannot be listed', async () => {
  await seed({ invite: {} });
  await assertFails(getDocs(collection(as('ben'), 'rooms')));
  await assertFails(getDocs(collection(as('ben'), 'invites')));
  await assertFails(getDocs(collection(as('ben'), 'users')));
});

test('only members read a room, its people and its messages', async () => {
  await seed();
  await assertFails(getDoc(doc(as('eve'), 'rooms', ROOM)));
  await assertFails(getDoc(doc(as('eve'), 'rooms', ROOM, 'members', 'ana')));
  await assertFails(getDocs(collection(as('eve'), 'rooms', ROOM, 'messages')));
  await assertSucceeds(getDocs(collection(as('ben'), 'rooms', ROOM, 'members')));
  await assertSucceeds(getDocs(collection(as('ben'), 'rooms', ROOM, 'messages')));
});

// ---- profiles --------------------------------------------------------------------------------------------

test('a person writes only their own profile, with a valid key', async () => {
  const ana = as('ana');
  await assertSucceeds(setDoc(doc(ana, 'users', 'ana'), { displayName: 'Ana', publicKey: PUBLIC_KEY }));
  await assertFails(setDoc(doc(ana, 'users', 'ben'), { displayName: 'Ben', publicKey: PUBLIC_KEY }));
  await assertFails(setDoc(doc(ana, 'users', 'ana'), { displayName: 'Ana', publicKey: 'short' }));
  await assertFails(setDoc(doc(ana, 'users', 'ana'), { displayName: 'A'.repeat(41), publicKey: PUBLIC_KEY }));
  await assertFails(setDoc(doc(ana, 'users', 'ana'), { displayName: 'Ana', publicKey: PUBLIC_KEY, email: 'a@b.c' }));
  await assertSucceeds(getDoc(doc(as('ben'), 'users', 'ana')));
});

// ---- making and changing a room ----------------------------------------------------------------------------------

test('an owner makes a room as themselves, at version one, with sealed details', async () => {
  await assertSucceeds(newRoomBatch(as('ana'), 'ana').commit());
  await env.clearFirestore();
  await assertFails(newRoomBatch(as('ana'), 'ana', { ownerUid: 'ben' }).commit());
  await assertFails(newRoomBatch(as('ana'), 'ana', { keyVersion: 2 }).commit());
  await assertFails(newRoomBatch(as('ana'), 'ana', { memberCount: 5 }).commit());
  await assertFails(newRoomBatch(as('ana'), 'ana', { sealedPayload: 'x'.repeat(40001) }).commit());
  await assertFails(newRoomBatch(as('ana'), 'ana', { extra: 1 }).commit());
});

test('only the owner changes the details, one revision at a time', async () => {
  await seed();
  await assertSucceeds(updateDoc(doc(as('ana'), 'rooms', ROOM), { rev: 2, sealedPayload: SEALED }));
  await assertFails(updateDoc(doc(as('ana'), 'rooms', ROOM), { rev: 5, sealedPayload: SEALED }));
  await assertFails(updateDoc(doc(as('ben'), 'rooms', ROOM), { rev: 2, sealedPayload: SEALED }));
  await assertFails(updateDoc(doc(as('ana'), 'rooms', ROOM), { ownerUid: 'ben' }));
});

test('the owner can delete a whole room, a member cannot', async () => {
  await seed();
  await assertFails(deleteDoc(doc(as('ben'), 'rooms', ROOM)));
  const db = as('ana');
  const b = writeBatch(db);
  b.delete(doc(db, 'rooms', ROOM, 'members', 'ben'));
  b.delete(doc(db, 'rooms', ROOM, 'members', 'ana'));
  b.delete(doc(db, 'rooms', ROOM));
  await assertSucceeds(b.commit());
});

// ---- invites and joining ------------------------------------------------------------------------------------------

test('only the owner makes or cancels invites, and an invite lasts at most a week', async () => {
  await seed({ members: [] });
  const make = (db, over = {}) => setDoc(doc(db, 'invites', 'ABCDEFGHJK'), {
    roomId: ROOM, createdByUid: 'ana', expiresAt: hoursFromNow(24), revoked: false, sealedKey: 'k', keyVersion: 1, ...over,
  });
  await assertFails(make(as('ben')));
  await assertFails(make(as('ana'), { expiresAt: hoursFromNow(24 * 8) }));
  await assertFails(make(as('ana'), { keyVersion: 2 }));
  await assertFails(make(as('ana'), { revoked: true }));
  await assertSucceeds(make(as('ana')));
  await assertSucceeds(getDoc(doc(as('eve'), 'invites', 'ABCDEFGHJK')));
  await assertFails(updateDoc(doc(as('eve'), 'invites', 'ABCDEFGHJK'), { revoked: true }));
  await assertSucceeds(updateDoc(doc(as('ana'), 'invites', 'ABCDEFGHJK'), { revoked: true }));
  await assertFails(updateDoc(doc(as('ana'), 'invites', 'ABCDEFGHJK'), { revoked: false }));
});

test('a person joins with a valid invite and the count goes up by one', async () => {
  await seed({ members: [], invite: {} });
  await assertSucceeds(joinBatch(as('cy'), 'cy', 2).commit());
});

test('a joining person is refused for a cancelled, expired or outdated invite, or a full room', async () => {
  await seed({ members: [], invite: { revoked: true } });
  await assertFails(joinBatch(as('cy'), 'cy', 2).commit());
  await env.clearFirestore();
  await seed({ members: [], invite: { expiresAt: ts(Date.now() - 1000) } });
  await assertFails(joinBatch(as('cy'), 'cy', 2).commit());
  await env.clearFirestore();
  await seed({ members: [], keyVersion: 2, invite: { keyVersion: 1 } });
  await assertFails(joinBatch(as('cy'), 'cy', 2).commit());
  await env.clearFirestore();
  await seed({ members: [], memberCount: 20, invite: {} });
  await assertFails(joinBatch(as('cy'), 'cy', 21).commit());
});

test('a joining person cannot take the owner role, skip the count, or join twice', async () => {
  await seed({ members: [], invite: {} });
  const db = as('cy');
  const b = writeBatch(db);
  b.set(doc(db, 'rooms', ROOM, 'members', 'cy'), {
    role: 'OWNER', displayName: 'cy', joinedAt: serverTimestamp(), wrappedKey: 'k', wrappedKeyVersion: 1, lastMessageAt: serverTimestamp(), inviteId: 'ABCDEFGHJK',
  });
  b.update(doc(db, 'rooms', ROOM), { memberCount: 2 });
  await assertFails(b.commit());
  await assertFails(joinBatch(db, 'cy', 5).commit());
  await assertSucceeds(joinBatch(db, 'cy', 2).commit());
  await assertFails(joinBatch(db, 'cy', 3).commit());
});

// ---- chat ----------------------------------------------------------------------------------------------------------------

const sendBatch = (db, uid, id, over = {}) => {
  const b = writeBatch(db);
  b.set(doc(db, 'rooms', ROOM, 'messages', id), {
    senderUid: uid, createdAt: serverTimestamp(), kind: 'TEXT', sealedBody: SEALED, keyVersion: 1, ...over,
  });
  b.update(doc(db, 'rooms', ROOM, 'members', uid), { lastMessageAt: serverTimestamp() });
  return b;
};

test('a member sends a sealed message as themselves for the current key', async () => {
  await seed();
  const ben = as('ben');
  await assertSucceeds(sendBatch(ben, 'ben', 'm1').commit());
  await assertFails(sendBatch(as('ben'), 'ben', 'm2', { senderUid: 'ana' }).commit());
  await assertFails(sendBatch(as('ben'), 'ben', 'm3', { keyVersion: 2 }).commit());
  await assertFails(sendBatch(as('ben'), 'ben', 'm4', { kind: 'SHOUT' }).commit());
  await assertFails(sendBatch(as('ben'), 'ben', 'm5', { sealedBody: '' }).commit());
  await assertFails(sendBatch(as('eve'), 'eve', 'm6').commit());
});

test('messages come no faster than one a second, and are never edited', async () => {
  await seed();
  const ben = as('ben');
  await assertSucceeds(sendBatch(ben, 'ben', 'm1').commit());
  await assertFails(sendBatch(ben, 'ben', 'm2').commit());
  await assertFails(updateDoc(doc(ben, 'rooms', ROOM, 'messages', 'm1'), { sealedBody: SEALED }));
});

test('a person deletes their own messages, the owner can delete any, other members cannot', async () => {
  await seed({ members: ['ben', 'cy'] });
  await assertSucceeds(sendBatch(as('ben'), 'ben', 'm1').commit());
  await assertFails(deleteDoc(doc(as('cy'), 'rooms', ROOM, 'messages', 'm1')));
  await assertSucceeds(deleteDoc(doc(as('ana'), 'rooms', ROOM, 'messages', 'm1')));
  await assertSucceeds(sendBatch(as('ben'), 'ben', 'm2').commit().catch(() => {}));
  await assertSucceeds(deleteDoc(doc(as('ben'), 'rooms', ROOM, 'messages', 'm1')).catch(() => {}));
});

// ---- people ------------------------------------------------------------------------------------------------------------

test('a member changes only their own done mark', async () => {
  await seed();
  await assertSucceeds(updateDoc(doc(as('ben'), 'rooms', ROOM, 'members', 'ben'), { doneAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(as('ben'), 'rooms', ROOM, 'members', 'ana'), { doneAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(as('ben'), 'rooms', ROOM, 'members', 'ben'), { role: 'OWNER' }));
  await assertFails(updateDoc(doc(as('ben'), 'rooms', ROOM, 'members', 'ben'), { displayName: 'Boss' }));
});

test('a member can leave, the owner cannot leave without deleting the room', async () => {
  await seed();
  const db = as('ben');
  const b = writeBatch(db);
  b.delete(doc(db, 'rooms', ROOM, 'members', 'ben'));
  b.update(doc(db, 'rooms', ROOM), { memberCount: 1 });
  b.delete(doc(db, 'users', 'ben', 'rooms', ROOM));
  await assertSucceeds(b.commit());
  await assertFails(deleteDoc(doc(as('ana'), 'rooms', ROOM, 'members', 'ana')));
});

test('the owner removes a person and seals a new key for everyone who stays, in one step', async () => {
  await seed({ members: ['ben', 'cy'] });
  const rekey = (version) => {
    const db = as('ana');
    const b = writeBatch(db);
    b.update(doc(db, 'rooms', ROOM), { keyVersion: 2, memberCount: 2, sealedPayload: SEALED });
    b.delete(doc(db, 'rooms', ROOM, 'members', 'ben'));
    b.update(doc(db, 'rooms', ROOM, 'members', 'ana'), { wrappedKey: 'new', wrappedKeyVersion: version });
    b.update(doc(db, 'rooms', ROOM, 'members', 'cy'), { wrappedKey: 'new', wrappedKeyVersion: version });
    b.delete(doc(db, 'users', 'ben', 'rooms', ROOM));
    return b;
  };
  await assertFails(rekey(3).commit());
  await assertSucceeds(rekey(2).commit());
});

test('a member cannot remove another member or seal keys', async () => {
  await seed({ members: ['ben', 'cy'] });
  await assertFails(deleteDoc(doc(as('ben'), 'rooms', ROOM, 'members', 'cy')));
  await assertFails(updateDoc(doc(as('ben'), 'rooms', ROOM, 'members', 'cy'), { wrappedKey: 'x', wrappedKeyVersion: 2 }));
});

// ---- reports ---------------------------------------------------------------------------------------------------------------

test('a member can report, and nobody can read reports from the app', async () => {
  await seed();
  const report = (uid, over = {}) => setDoc(doc(as(uid), 'reports', `r-${uid}`), {
    roomId: ROOM, reportedUid: 'ana', reporterUid: uid, messageId: 'm1', reason: 'HARASSMENT', detail: 'text', quote: 'quoted', createdAt: serverTimestamp(), ...over,
  });
  await assertSucceeds(report('ben'));
  await assertFails(report('eve'));
  await assertFails(report('ben', { reporterUid: 'ana' }));
  await assertFails(report('ben', { detail: 'x'.repeat(1001) }));
  await assertFails(getDoc(doc(as('ben'), 'reports', 'r-ben')));
  await assertFails(deleteDoc(doc(as('ben'), 'reports', 'r-ben')));
});
