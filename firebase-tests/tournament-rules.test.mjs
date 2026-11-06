import { readFileSync } from 'node:fs';
import { before, after, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, updateDoc, deleteDoc, collection, query, where, getDocs,
  writeBatch, serverTimestamp, Timestamp } from 'firebase/firestore';

// Only the disposable local demo project is used. Never production accounts or events.
let env;
const projectId = 'demo-overdex-comms';
const identity = id => env.authenticatedContext(id, {
  firebase: { sign_in_provider: 'password' }, email: `${id}@example.invalid`
}).firestore();
const room = (extra = {}) => ({ schemaVersion: 1, ownerId: 'alice',
  memberIds: ['alice', 'bob', 'charlie', 'dora'], phase: 'LOBBY', activeBattles: {},
  revision: 0, settingsRevision: 0, payload: '{"localTest":true}', createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(), ...extra });
const attendance = (extra = {}) => ({ accepted: true, checkedIn: true, declined: false, settingsRevision: 0, updatedAt: serverTimestamp(), ...extra });
const preparation = (extra = {}) => ({ playerId: 'alice', battleKey: 'r1s1b1', ready: true,
  result: 'NONE', note: '', updatedAt: serverTimestamp(), ...extra });
const r = (db, id = 'lobby') => doc(db, 'tournaments', id);
const a = (db, player = 'alice', id = 'lobby') => doc(db, 'tournaments', id, 'entrants', player);
const p = (db, key = 'r1s1b1_alice', id = 'active') => doc(db, 'tournaments', id, 'preparations', key);
const change = (extra = {}) => ({ revision: 1, updatedAt: serverTimestamp(), ...extra });

before(async () => {
  env = await initializeTestEnvironment({ projectId, firestore: {
    host: '127.0.0.1', port: 8085,
    rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8')
  }});
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    const createdAt = Timestamp.fromMillis(1000);
    const updatedAt = Timestamp.fromMillis(2000);
    await setDoc(r(db), room({ createdAt, updatedAt }));
    await setDoc(r(db, 'active'), room({ phase: 'ACTIVE', activeBattles: {
      r1s1b1: ['alice', 'bob'], r1s2b1: ['charlie', 'dora']
    }, createdAt, updatedAt }));
    await setDoc(r(db, 'ended'), room({ phase: 'ENDED_EARLY', createdAt, updatedAt }));
    await setDoc(r(db, 'completed'), room({ phase: 'COMPLETED', createdAt, updatedAt }));
    await setDoc(a(db), attendance({ updatedAt }));
    await setDoc(p(db), preparation({ updatedAt }));
  });
});

test('unauthenticated and anonymous sessions cannot read or create tournament data', async () => {
  const contexts = [env.unauthenticatedContext(),
    env.authenticatedContext('alice', { firebase: { sign_in_provider: 'anonymous' } })];
  for (const context of contexts) {
    const db = context.firestore();
    await assertFails(getDoc(r(db)));
    await assertFails(getDoc(a(db)));
    await assertFails(getDoc(p(db)));
    await assertFails(getDocs(collection(db, 'tournaments')));
    await assertFails(setDoc(r(db, 'new'), room()));
  }
});

test('only invited members read rooms, attendance, reports and missing response defaults', async () => {
  const member = identity('bob');
  await assertSucceeds(getDoc(r(member)));
  await assertSucceeds(getDoc(a(member)));
  await assertSucceeds(getDoc(p(member)));
  assert.equal((await assertSucceeds(getDoc(a(member, 'bob')))).exists(), false);
  assert.equal((await assertSucceeds(getDoc(p(member, 'r1s1b1_bob')))).exists(), false);
  await assertSucceeds(getDocs(collection(member, 'tournaments', 'lobby', 'entrants')));
  await assertSucceeds(getDocs(collection(member, 'tournaments', 'active', 'preparations')));
  const outsider = identity('outsider');
  for (const ref of [r(outsider), a(outsider), p(outsider), a(outsider, 'outsider')]) {
    await assertFails(getDoc(ref));
  }
  await assertFails(getDocs(collection(outsider, 'tournaments', 'active', 'preparations')));
  assert.equal((await assertSucceeds(getDoc(r(outsider, 'not-created')))).exists(), false);
});

test('member-scoped event query succeeds but enumeration and another member query fail', async () => {
  const db = identity('bob');
  const results = await assertSucceeds(getDocs(query(collection(db, 'tournaments'), where('memberIds', 'array-contains', 'bob'))));
  assert.equal(results.size, 4);
  await assertFails(getDocs(collection(db, 'tournaments')));
  await assertFails(getDocs(query(collection(db, 'tournaments'), where('memberIds', 'array-contains', 'alice'))));
});

test('host creates room and its own checked-in response atomically', async () => {
  const db = identity('alice');
  const batch = writeBatch(db);
  batch.set(r(db, 'new'), room({ memberIds: ['alice'] }));
  batch.set(a(db, 'alice', 'new'), attendance());
  await assertSucceeds(batch.commit());
  const forged = writeBatch(db);
  forged.set(r(db, 'forged'), room());
  forged.set(a(db, 'bob', 'forged'), attendance());
  await assertFails(forged.commit());
});

test('creator cannot impersonate another host or create an already started event', async () => {
  const db = identity('alice');
  for (const invalid of [room({ ownerId: 'bob' }), room({ phase: 'ACTIVE' }),
    room({ phase: 'COMPLETED' }), room({ revision: 1 }), room({ createdAt: Timestamp.fromMillis(0) }),
    room({ updatedAt: Timestamp.fromMillis(0) })]) {
    await assertFails(setDoc(r(db, 'bad'), invalid));
  }
});

test('root schema and bounded routing data are validated on create', async () => {
  const db = identity('alice');
  const noPayload = room(); delete noPayload.payload;
  for (const invalid of [noPayload, room({ extra: true }), room({ schemaVersion: 2 }),
    room({ memberIds: [] }), room({ memberIds: ['alice', 'alice'] }),
    room({ memberIds: ['bob'] }), room({ memberIds: ['alice', { accountId: 'bob' }] }),
    room({ memberIds: ['alice', ...Array.from({ length: 16 }, (_, i) => `u${i}`)] }),
    room({ memberIds: ['alice', 'bad/uid'] }), room({ memberIds: ['alice', 'x'.repeat(129)] }),
    room({ memberIds: ['alice', 'bob|charlie'] }), room({ memberIds: ['alice', 42] }),
    room({ memberIds: ['alice', true] }), room({ memberIds: ['alice', null] }),
    room({ settingsRevision: 1 }),
    room({ phase: 'ADMIN' }), room({ revision: -1 }), room({ revision: 0.5 }),
    room({ payload: '' }), room({ payload: 'x'.repeat(120001) }), room({ payload: {} }),
    room({ activeBattles: { r1: ['alice', 'bob'] } }), room({ createdAt: 1 })]) {
    await assertFails(setDoc(r(db, 'bad'), invalid));
  }
});

test('host can append invitations and update settings only with a new revision', async () => {
  const db = identity('alice');
  await assertSucceeds(updateDoc(r(db), change({ memberIds: ['alice', 'bob', 'charlie', 'dora', 'eve'], payload: '{"updated":true}' })));
  await assertFails(updateDoc(r(db), { payload: 'stale overwrite', updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(r(db), { revision: 3, updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(r(db), { revision: 2, memberIds: ['alice', 'bob'], updatedAt: serverTimestamp() }));
});

test('nonhosts cannot change settings, invitations, phase, bracket, owner or revisions', async () => {
  for (const actor of ['bob', 'outsider']) {
    const db = identity(actor);
    for (const patch of [{ payload: 'forged' }, { memberIds: ['alice', actor] },
      { ownerId: actor }, { phase: 'ACTIVE' }, { activeBattles: { invented: [actor, 'alice'] } }]) {
      await assertFails(updateDoc(r(db), change(patch)));
    }
    await assertFails(deleteDoc(r(db)));
  }
});

test('root update cannot bypass schema, immutable fields, timestamp or size bounds', async () => {
  const db = identity('alice');
  for (const invalid of [{ ownerId: 'bob' }, { schemaVersion: 2 }, { createdAt: Timestamp.now() },
    { payload: 'x'.repeat(120001) }, { payload: 7 }, { extra: true },
    { updatedAt: Timestamp.fromMillis(0) }, { revision: 1000001 }]) {
    await assertFails(updateDoc(r(db), change(invalid)));
  }
  const original = (await getDoc(r(db))).data();
  delete original.payload;
  await assertFails(setDoc(r(db), { ...original, ...change() }));
  await assertFails(deleteDoc(r(db)));
});

test('only valid phase transitions are possible and ending freezes host edits', async () => {
  const db = identity('alice');
  await assertFails(updateDoc(r(db), change({ phase: 'COMPLETED' })));
  await assertSucceeds(updateDoc(r(db), change({ phase: 'ACTIVE', activeBattles: { r1s1b1: ['alice', 'bob'] } })));
  await assertFails(updateDoc(r(db), { ...change({ revision: 2 }), phase: 'LOBBY', activeBattles: {} }));
  await assertSucceeds(updateDoc(r(db), { ...change({ revision: 2 }), phase: 'ENDED_EARLY', activeBattles: {} }));
  await assertFails(updateDoc(r(db), { ...change({ revision: 3 }), payload: 'rewrite ended event' }));
  await assertFails(updateDoc(r(db, 'completed'), change({ phase: 'ACTIVE' })));
});

test('host can close a one-person draft early but cannot start it', async () => {
  const db = identity('alice');
  await assertSucceeds(setDoc(r(db, 'solo'), room({ memberIds: ['alice'] })));
  await assertFails(updateDoc(r(db, 'solo'), change({ phase: 'ACTIVE' })));
  await assertSucceeds(updateDoc(r(db, 'solo'), change({ phase: 'ENDED_EARLY' })));
});

test('active roster is frozen and completed or ended events cannot publish pairings', async () => {
  const db = identity('alice');
  for (const patch of [{ memberIds: ['alice', 'bob', 'charlie', 'dora', 'eve'] },
    { memberIds: ['alice', 'bob'] }, { phase: 'COMPLETED' }, { phase: 'ENDED_EARLY' }]) {
    await assertFails(updateDoc(r(db, 'active'), change(patch)));
  }
  await assertSucceeds(updateDoc(r(db, 'active'), change({ phase: 'COMPLETED', activeBattles: {} })));
  await assertFails(updateDoc(r(db, 'ended'), change({ activeBattles: { r1: ['alice', 'bob'] } })));
});

test('every published pair is bounded, distinct and belongs to the invited roster', async () => {
  const db = identity('alice');
  for (const activeBattles of [[], { r1: ['alice'] }, { r1: ['alice', 'bob', 'charlie'] },
    { r1: ['alice', 'alice'] }, { r1: ['alice', 'outsider'] }, { r1: ['alice', 9] },
    { 'bad/key': ['alice', 'bob'] }, { ['x'.repeat(81)]: ['alice', 'bob'] },
    Object.fromEntries(Array.from({ length: 9 }, (_, i) => [`r${i}`, ['alice', 'bob']]))]) {
    await assertFails(updateDoc(r(db), change({ phase: 'ACTIVE', activeBattles })));
  }
});

test('maximum sixteen-trainer roster and eight simultaneous pairings fit the rule limits', async () => {
  const db = identity('alice');
  const members = ['alice', ...Array.from({ length: 15 }, (_, i) => `trainer${i}`)];
  await assertSucceeds(setDoc(r(db, 'maximum'), room({ memberIds: members, payload: 'x'.repeat(120000) })));
  const activeBattles = Object.fromEntries(Array.from({ length: 8 }, (_, i) => [`r1s${i + 1}b1`, members.slice(i * 2, i * 2 + 2)]));
  await assertSucceeds(updateDoc(r(db, 'maximum'), change({ phase: 'ACTIVE', activeBattles })));
});

test('invited trainer alone accepts, checks in, declines and changes its own lobby response', async () => {
  const db = identity('bob');
  await assertSucceeds(setDoc(a(db, 'bob'), attendance({ checkedIn: false })));
  await assertSucceeds(updateDoc(a(db, 'bob'), attendance()));
  await assertSucceeds(updateDoc(a(db, 'bob'), attendance({ accepted: false, checkedIn: false, declined: true })));
  await assertFails(updateDoc(a(db, 'alice'), attendance()));
  await assertFails(setDoc(a(identity('alice'), 'bob'), attendance()));
  await assertFails(setDoc(a(identity('outsider'), 'outsider'), attendance()));
});

test('attendance validates every write and cannot be rewritten or joined after start', async () => {
  const db = identity('alice');
  for (const invalid of [attendance({ accepted: false }), attendance({ checkedIn: 1 }),
    attendance({ declined: true }), attendance({ declined: 'yes' }),
    attendance({ role: 'host' }), attendance({ updatedAt: Timestamp.fromMillis(0) }),
    { accepted: true, updatedAt: serverTimestamp() }]) {
    await assertFails(setDoc(a(db), invalid));
  }
  for (const id of ['active', 'ended', 'completed', 'missing']) {
    await assertFails(setDoc(a(db, 'alice', id), attendance()));
  }
  await assertFails(deleteDoc(a(db)));
});

test('changed settings invalidate old check-ins and require acknowledgement of the current version', async () => {
  const host = identity('alice');
  await assertSucceeds(updateDoc(r(host), change({ settingsRevision: 1, payload: '{"newRules":true}' })));
  const bob = identity('bob');
  await assertFails(setDoc(a(bob, 'bob'), attendance()));
  await assertSucceeds(setDoc(a(bob, 'bob'), attendance({ settingsRevision: 1 })));
  await assertFails(updateDoc(r(host), { ...change({ revision: 2 }), settingsRevision: 3 }));
  await assertFails(updateDoc(r(host), { ...change({ revision: 2 }), settingsRevision: 0 }));
  await assertFails(updateDoc(r(host, 'active'), change({ settingsRevision: 1 })));
});

test('paired trainers ready and report independently while members can read those reports', async () => {
  const alice = identity('alice');
  const bob = identity('bob');
  await assertSucceeds(updateDoc(p(alice), preparation({ result: 'WIN', note: 'Local test' })));
  await assertSucceeds(setDoc(p(bob, 'r1s1b1_bob'), preparation({ playerId: 'bob', result: 'LOSS' })));
  assert.equal((await getDoc(p(bob))).data().result, 'WIN');
  await assertSucceeds(updateDoc(p(alice), preparation({ result: 'DISPUTE', ready: false, note: 'Please review this battle.' })));
  await assertSucceeds(updateDoc(p(alice), preparation({ ready: false, result: 'NONE' })));
});

test('host cannot forge another player report and a trainer cannot report another pairing', async () => {
  const db = identity('alice');
  await assertFails(setDoc(p(db, 'r1s1b1_bob'), preparation({ playerId: 'bob', result: 'LOSS' })));
  await assertFails(setDoc(p(db, 'r1s2b1_alice'), preparation({ battleKey: 'r1s2b1', result: 'WIN' })));
  await assertFails(setDoc(p(identity('outsider'), 'r1s1b1_outsider'), preparation({ playerId: 'outsider' })));
  await assertFails(setDoc(p(identity('charlie'), 'r1s1b1_charlie'), preparation({ playerId: 'charlie' })));
});

test('preparation key, identity, required fields, bounds and result are validated on every write', async () => {
  const db = identity('alice');
  for (const invalid of [preparation({ playerId: 'bob' }), preparation({ battleKey: 'r1s2b1' }),
    preparation({ result: 'CHAMPION' }), preparation({ result: 'WIN', ready: false }),
    preparation({ ready: 1 }), preparation({ note: 'x'.repeat(241) }), preparation({ note: [] }),
    preparation({ extra: 'unrestricted' }), preparation({ updatedAt: Timestamp.fromMillis(0) }),
    { playerId: 'alice', battleKey: 'r1s1b1', ready: true, result: 'WIN', updatedAt: serverTimestamp() }]) {
    await assertFails(setDoc(p(db), invalid));
  }
  await assertFails(setDoc(p(db, 'different-document'), preparation()));
  await assertFails(deleteDoc(p(db)));
});

test('old reports remain readable but cannot change after a battle advances or tournament ends', async () => {
  const db = identity('alice');
  await assertSucceeds(updateDoc(r(db, 'active'), change({ activeBattles: { r1s1b2: ['alice', 'bob'] } })));
  await assertSucceeds(getDoc(p(db)));
  await assertFails(updateDoc(p(db), preparation({ result: 'WIN' })));
  await assertSucceeds(setDoc(p(db, 'r1s1b2_alice'), preparation({ battleKey: 'r1s1b2' })));
  await assertSucceeds(updateDoc(r(db, 'active'), { ...change({ revision: 2 }), phase: 'ENDED_EARLY', activeBattles: {} }));
  await assertFails(updateDoc(p(db, 'r1s1b2_alice'), preparation({ battleKey: 'r1s1b2', result: 'WIN' })));
  await assertFails(setDoc(p(db, 'r1s1b1_alice', 'lobby'), preparation()));
});

test('orphaned and arbitrary tournament subcollections stay denied', async () => {
  const db = identity('alice');
  await assertFails(getDoc(p(db, 'r1s1b1_alice', 'missing')));
  await assertFails(setDoc(p(db, 'r1s1b1_alice', 'missing'), preparation()));
  await assertFails(setDoc(doc(db, 'tournaments', 'lobby', 'admins', 'alice'), { role: 'host' }));
  await assertFails(getDoc(doc(db, 'tournaments', 'lobby', 'admins', 'alice')));
});
