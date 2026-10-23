import { readFileSync } from 'node:fs';
import { before, after, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, updateDoc, deleteDoc, collection, query, where, getDocs,
  writeBatch, runTransaction, serverTimestamp, Timestamp } from 'firebase/firestore';

let env;
const projectId = 'demo-overdex-comms';
const identity = id => env.authenticatedContext(id, { firebase: { sign_in_provider: 'password' }, email: `${id}@example.invalid` }).firestore();
const name = id => `Trainer ${id}`;
const trainerCode = n => String(n).padStart(12, '0');
const profile = (id, code = trainerCode(1)) => ({ accountId: id, trainerCode: code, displayName: name(id), updatedAt: serverTimestamp() });
const room = (members = ['alice','bob'], extra = {}) => ({ title: 'Practice coaches', kind: members.length === 2 ? 'DIRECT' : 'GROUP', memberIds: members, ownerId: 'alice', createdAt: serverTimestamp(), ...extra });
const message = (extra = {}) => ({ senderId: 'alice', senderName: name('alice'), text: 'Replay coaching', sentAt: Date.now(), serverSentAt: serverTimestamp(), ...extra });
const p = (db, id) => doc(db, 'trainerCommsProfiles', id);
const c = (db, code) => doc(db, 'trainerCommsCodes', code);
const r = (db, id = 'room') => doc(db, 'trainerConversations', id);
const m = (db, id = 'one', roomId = 'room') => doc(db, 'trainerConversations', roomId, 'messages', id);

before(async () => {
  env = await initializeTestEnvironment({ projectId, firestore: {
    host: '127.0.0.1', port: 8085, rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8')
  }});
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => {
    const db = ctx.firestore();
    for (let i = 0; i < 9; i++) {
      const id = ['alice','bob','charlie','dora','eve','frank','grace','hank','iris'][i];
      const code = trainerCode(i + 1);
      await setDoc(p(db, id), { ...profile(id, code), updatedAt: Timestamp.now() });
      await setDoc(c(db, code), { accountId: id });
    }
    await setDoc(r(db), { ...room(), createdAt: Timestamp.now() });
    await setDoc(m(db), { ...message(), serverSentAt: Timestamp.now() });
  });
});

test('unauthenticated and anonymous sessions cannot read any Comms data', async () => {
  for (const db of [env.unauthenticatedContext().firestore(), env.authenticatedContext('anon', { firebase: { sign_in_provider: 'anonymous' } }).firestore()]) {
    await assertFails(getDoc(p(db, 'alice')));
    await assertFails(getDoc(c(db, trainerCode(1))));
    await assertFails(getDoc(r(db)));
    await assertFails(getDoc(m(db)));
    await assertFails(setDoc(r(db, 'attack'), room()));
  }
});
test('known code and public profile can be looked up but cannot be enumerated', async () => {
  const db = identity('bob');
  await assertSucceeds(getDoc(c(db, trainerCode(1))));
  await assertSucceeds(getDoc(p(db, 'alice')));
  await assertFails(getDocs(collection(db, 'trainerCommsCodes')));
  await assertFails(getDocs(collection(db, 'trainerCommsProfiles')));
});
test('new account claims exactly one code atomically with profile', async () => {
  const db = identity('new-trainer');
  const batch = writeBatch(db);
  batch.set(p(db, 'new-trainer'), profile('new-trainer', trainerCode(22)));
  batch.set(c(db, trainerCode(22)), { accountId: 'new-trainer' });
  await assertSucceeds(batch.commit());
});
test('cannot claim another account code or profile', async () => {
  const db = identity('bob');
  await assertFails(setDoc(p(db, 'alice'), profile('alice')));
  await assertFails(setDoc(c(db, trainerCode(1)), { accountId: 'bob' }));
  const batch = writeBatch(db);
  batch.set(p(db, 'bob'), profile('bob', trainerCode(1)));
  batch.set(c(db, trainerCode(1)), { accountId: 'bob' });
  await assertFails(batch.commit());
});
test('code reroll releases old code and preserves rooms/messages under account ID', async () => {
  const db = identity('alice');
  const batch = writeBatch(db);
  batch.set(p(db, 'alice'), profile('alice', trainerCode(77)));
  batch.set(c(db, trainerCode(77)), { accountId: 'alice' });
  batch.delete(c(db, trainerCode(1)));
  await assertSucceeds(batch.commit());
  assert.equal((await getDoc(c(db, trainerCode(1)))).exists(), false);
  assert.equal((await getDoc(c(db, trainerCode(77)))).data().accountId, 'alice');
  await assertSucceeds(getDoc(m(db)));
});
test('cannot reserve extra codes or detach profile from its current code', async () => {
  const db = identity('alice');
  await assertFails(setDoc(c(db, trainerCode(99)), { accountId: 'alice' }));
  await assertFails(deleteDoc(c(db, trainerCode(1))));
  await assertFails(updateDoc(p(db, 'alice'), { trainerCode: trainerCode(99), updatedAt: serverTimestamp() }));
  const batch = writeBatch(db);
  batch.set(p(db, 'alice'), profile('alice', trainerCode(99)));
  batch.set(c(db, trainerCode(99)), { accountId: 'alice' });
  await assertFails(batch.commit()); // The old code must be released in the same transaction.
});
test('public profile has bounded fields and cannot leak private email/password fields', async () => {
  const db = identity('alice');
  for (const invalid of [
    { ...profile('alice'), email: 'private@example.invalid' },
    { ...profile('alice'), displayName: 'x'.repeat(41) },
    { ...profile('alice'), displayName: '' },
    { ...profile('alice'), accountId: 'bob' },
    { ...profile('alice'), updatedAt: 1 },
    { ...profile('alice'), trainerCode: 123456789012 },
  ]) await assertFails(setDoc(p(db, 'alice'), invalid));
  await assertFails(deleteDoc(p(db, 'alice')));
});
test('malformed, short and non-ASCII codes are rejected', async () => {
  const db = identity('new');
  for (const code of ['123', '12345678901a', '１２３４５６７８９０１２', '1234 5678 9012']) {
    const batch = writeBatch(db);
    batch.set(p(db, 'new'), profile('new', code));
    batch.set(c(db, code), { accountId: 'new' });
    await assertFails(batch.commit());
  }
});
test('authenticated owner creates a direct or eight-person group with registered members', async () => {
  const db = identity('alice');
  await assertSucceeds(setDoc(r(db, 'direct'), room()));
  await assertSucceeds(setDoc(r(db, 'group'), room(['alice','bob','charlie','dora','eve','frank','grace','hank'])));
});
test('room membership owner and schema cannot be forged', async () => {
  const db = identity('alice');
  for (const invalid of [room(['bob','charlie']), room(['alice','alice']), room(['alice','missing']),
    room(['alice']), room(['alice','bob','charlie'], { kind: 'DIRECT' }),
    room(['alice','bob'], { ownerId: 'bob' }), room(['alice','bob'], { admin: true }),
    room(['alice','bob'], { title: 'x'.repeat(81) }), room(['alice','bob'], { kind: 'LEGACY' }),
    room(['alice','bob'], { createdAt: 1 }),
    room(['alice','bob','charlie','dora','eve','frank','grace','hank','iris']),
    room(['alice', { accountId: 'bob' }])]) {
    await assertFails(setDoc(r(db, 'invalid'), invalid));
  }
});
test('outsider cannot read a room/messages or add itself; even owner cannot rewrite membership', async () => {
  const outsider = identity('charlie');
  await assertFails(getDoc(r(outsider)));
  await assertFails(getDoc(m(outsider)));
  await assertFails(updateDoc(r(outsider), { memberIds: ['alice','bob','charlie'] }));
  await assertFails(updateDoc(r(identity('alice')), { memberIds: ['alice','bob','charlie'] }));
  await assertFails(deleteDoc(r(identity('alice'))));
});
test('member-scoped room query succeeds; unscoped list and other-member query fail', async () => {
  const db = identity('alice');
  await assertSucceeds(getDocs(query(collection(db, 'trainerConversations'), where('memberIds', 'array-contains', 'alice'))));
  await assertFails(getDocs(collection(db, 'trainerConversations')));
  await assertFails(getDocs(query(collection(db, 'trainerConversations'), where('memberIds', 'array-contains', 'bob'))));
});
test('members read messages and create only their own bounded messages', async () => {
  const db = identity('alice');
  await assertSucceeds(getDocs(collection(db, 'trainerConversations', 'room', 'messages')));
  await assertSucceeds(setDoc(m(db, 'two'), message()));
  await assertSucceeds(setDoc(m(db, 'max-size'), message({ text: 'x'.repeat(4000) })));
});
test('cannot spoof message sender, profile name, timestamps or extra payload', async () => {
  const db = identity('alice');
  for (const invalid of [message({ senderId: 'bob' }), message({ senderName: 'Coach admin' }),
    message({ text: '' }), message({ text: 'x'.repeat(4001) }), message({ text: ['bad'] }),
    message({ sentAt: -1 }), message({ sentAt: Date.now() + 10000000 }),
    message({ serverSentAt: Timestamp.fromMillis(0) }), message({ attachment: 'unrestricted-url' })]) {
    await assertFails(setDoc(m(db, 'invalid'), invalid));
  }
});
test('messages cannot be edited deleted or written into an absent room', async () => {
  const db = identity('alice');
  await assertFails(updateDoc(m(db), { text: 'changed' }));
  await assertFails(deleteDoc(m(db)));
  await assertFails(setDoc(m(db, 'new', 'missing'), message()));
  await assertFails(setDoc(m(identity('charlie'), 'outsider'), message({ senderId: 'charlie', senderName: name('charlie') })));
});
test('retry of an acknowledged message reads original ID without creating a duplicate', async () => {
  const db = identity('alice');
  let writes = 0;
  for (let i = 0; i < 2; i++) {
    await assertSucceeds(runTransaction(db, async transaction => {
      const ref = m(db, 'retry-id');
      if (!(await transaction.get(ref)).exists()) { transaction.set(ref, message()); writes++; }
    }));
  }
  assert.equal(writes, 1);
  assert.equal((await getDocs(collection(db, 'trainerConversations', 'room', 'messages'))).size, 2);
});
test('legacy pair-ID chats and all unrelated collections stay denied', async () => {
  const db = identity('alice');
  for (const path of ['conversations/alice_bob/messages/one', 'private/alice', 'users/alice']) {
    await assertFails(getDoc(doc(db, path)));
    await assertFails(setDoc(doc(db, path), { public: true }));
  }
});
