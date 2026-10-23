import { test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, createUserWithEmailAndPassword, signInWithEmailAndPassword, signOut } from 'firebase/auth';
import { getFirestore, connectFirestoreEmulator, doc, collection, getDoc, getDocs, setDoc, writeBatch,
  query, where, serverTimestamp, runTransaction, terminate } from 'firebase/firestore';

// Disposable identities on local emulators only: no production messages or accounts.
test('four real emulator accounts discover by code, exchange a group message and sign back in', async () => {
  assert.equal(process.env.GCLOUD_PROJECT, 'demo-overdex-comms');
  const clients = [];
  try {
    for (let i = 0; i < 4; i++) {
      const app = initializeApp({ projectId: 'demo-overdex-comms', apiKey: 'local-emulator-key', appId: `local:${i}` }, `comms-auth-${i}`);
      const auth = getAuth(app);
      connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
      const db = getFirestore(app);
      connectFirestoreEmulator(db, '127.0.0.1', 8085);
      const email = `trainer-${i}-${Date.now()}@example.invalid`;
      const user = (await createUserWithEmailAndPassword(auth, email, 'local-test-password')).user;
      const code = `99999999999${i}`;
      const displayName = `Local test trainer ${i}`;
      const batch = writeBatch(db);
      batch.set(doc(db, 'trainerCommsProfiles', user.uid), { accountId: user.uid, trainerCode: code, displayName, updatedAt: serverTimestamp() });
      batch.set(doc(db, 'trainerCommsCodes', code), { accountId: user.uid });
      await batch.commit();
      clients.push({ app, auth, db, user, code, email, displayName });
    }
    const [first, second, third, outsider] = clients;
    assert.equal((await getDoc(doc(first.db, 'trainerCommsCodes', second.code))).data().accountId, second.user.uid);
    const group = 'auth-group';
    const members = [first.user.uid, second.user.uid, third.user.uid];
    await setDoc(doc(first.db, 'trainerConversations', group), {
      title: 'Local coaching', kind: 'GROUP', memberIds: members, ownerId: first.user.uid, createdAt: serverTimestamp()
    });
    const found = await getDocs(query(collection(second.db, 'trainerConversations'), where('memberIds', 'array-contains', second.user.uid)));
    assert.ok(found.docs.some(d => d.id === group));
    await assert.rejects(getDoc(doc(outsider.db, 'trainerConversations', group)));
    const send = async () => runTransaction(first.db, async tx => {
      const ref = doc(first.db, 'trainerConversations', group, 'messages', 'same-retry-id');
      if (!(await tx.get(ref)).exists()) tx.set(ref, {
        senderId: first.user.uid, senderName: first.displayName, text: 'Local-only message', sentAt: Date.now(), serverSentAt: serverTimestamp()
      });
    });
    await send(); await send();
    const inbox = await getDocs(collection(second.db, 'trainerConversations', group, 'messages'));
    assert.equal(inbox.size, 1);
    assert.equal(inbox.docs[0].data().text, 'Local-only message');
    assert.equal((await getDocs(collection(third.db, 'trainerConversations', group, 'messages'))).size, 1);
    const accountId = second.user.uid;
    await signOut(second.auth);
    await assert.rejects(getDoc(doc(second.db, 'trainerConversations', group)));
    await signInWithEmailAndPassword(second.auth, second.email, 'local-test-password');
    assert.equal(second.auth.currentUser.uid, accountId);
    assert.equal((await getDoc(doc(second.db, 'trainerConversations', group, 'messages', 'same-retry-id'))).data().text, 'Local-only message');
  } finally {
    for (const client of clients) { await terminate(client.db); await deleteApp(client.app); }
  }
});
