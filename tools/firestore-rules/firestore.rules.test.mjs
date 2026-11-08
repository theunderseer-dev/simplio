import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { after, before, beforeEach, test } from 'node:test';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, collectionGroup, deleteDoc, doc, getDoc, getDocs, setDoc, updateDoc, writeBatch } from 'firebase/firestore';

const projectId = 'demo-simplio-rules';
const attendanceCollections = [
  'studentGroups', 'packageTypes', 'students', 'studentPackages',
  'attendance', 'classSchedules', 'classSessions',
];
let environment;

before(async () => {
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, '127.0.0.1:8189');
  environment = await initializeTestEnvironment({
    projectId,
    firestore: {
      host: '127.0.0.1',
      port: 8189,
      rules: await readFile(new URL('../../firestore.rules', import.meta.url), 'utf8'),
    },
  });
});

beforeEach(async () => environment.clearFirestore());
after(async () => environment?.cleanup());

test('each teacher can create, list, and update records in their own workspace', async () => {
  for (const uid of ['teacher-a', 'teacher-b']) {
    const db = environment.authenticatedContext(uid).firestore();
    for (const collectionId of attendanceCollections) {
      const records = collection(db, 'users', uid, collectionId);
      assert.equal((await assertSucceeds(getDocs(records))).size, 0);
      const record = doc(records, 'record-1');
      await assertSucceeds(setDoc(record, { isDeleted: false }));
      await assertSucceeds(updateDoc(record, { isDeleted: true }));
      assert.equal((await assertSucceeds(getDoc(record))).data().isDeleted, true);
      assert.equal((await assertSucceeds(getDocs(records))).size, 1);
      await assertFails(deleteDoc(record));
    }
  }
});

test('another teacher and a signed-out client cannot read, query, create, or change records', async () => {
  const owner = environment.authenticatedContext('teacher-a').firestore();
  const other = environment.authenticatedContext('teacher-b').firestore();
  const signedOut = environment.unauthenticatedContext().firestore();
  for (const collectionId of attendanceCollections) {
    const path = `users/teacher-a/${collectionId}`;
    await assertSucceeds(setDoc(doc(owner, path, 'record-1'), { isDeleted: false }));
    for (const db of [other, signedOut]) {
      await assertFails(getDoc(doc(db, path, 'record-1')));
      await assertFails(getDocs(collection(db, path)));
      await assertFails(setDoc(doc(db, path, 'record-2'), { isDeleted: false }));
      await assertFails(updateDoc(doc(db, path, 'record-1'), { isDeleted: true }));
      await assertFails(deleteDoc(doc(db, path, 'record-1')));
    }
  }
  await assertFails(getDocs(collectionGroup(other, 'students')));
});

test('a teacher can atomically commit a record, sync head, and immutable receipt', async () => {
  const db = environment.authenticatedContext('teacher-a').firestore();
  const student = doc(db, 'users/teacher-a/students/student-1');
  const head = doc(db, 'users/teacher-a/syncState/attendance');
  const receipt = doc(db, 'users/teacher-a/syncReceipts/batch-1');
  const upload = writeBatch(db);
  upload.set(student, { isDeleted: false });
  upload.set(head, { batchId: 'batch-1', protocolVersion: 7 });
  upload.set(receipt, { batchId: 'batch-1', protocolVersion: 7, mutationIds: ['mutation-1'] });
  await assertSucceeds(upload.commit());
  assert.equal((await assertSucceeds(getDoc(receipt))).data().batchId, 'batch-1');
  assert.equal((await assertSucceeds(getDoc(head))).data().batchId, 'batch-1');
  await assertSucceeds(updateDoc(head, { batchId: 'batch-2' }));
  await assertFails(deleteDoc(head));
  await assertFails(updateDoc(receipt, { batchId: 'changed' }));
  await assertFails(deleteDoc(receipt));
});

test('a write to another workspace rejects the entire batch and protects sync metadata', async () => {
  const owner = environment.authenticatedContext('teacher-a').firestore();
  const other = environment.authenticatedContext('teacher-b').firestore();
  const signedOut = environment.unauthenticatedContext().firestore();
  for (const path of ['syncState/attendance', 'syncReceipts/batch-1']) {
    const absolutePath = `users/teacher-a/${path}`;
    await assertSucceeds(setDoc(doc(owner, absolutePath), { batchId: 'batch-1' }));
    for (const db of [other, signedOut]) {
      await assertFails(getDoc(doc(db, absolutePath)));
      await assertFails(updateDoc(doc(db, absolutePath), { batchId: 'changed' }));
    }
  }
  await assertFails(setDoc(doc(other, 'users/teacher-a/syncReceipts/batch-2'), { batchId: 'batch-2' }));

  const ownStudent = doc(other, 'users/teacher-b/students/student-1');
  const batch = writeBatch(other);
  batch.set(ownStudent, { isDeleted: false });
  batch.update(doc(other, 'users/teacher-a/syncState/attendance'), { batchId: 'changed' });
  await assertFails(batch.commit());
  assert.equal((await assertSucceeds(getDoc(ownStudent))).exists(), false);
});

test('access to unspecified paths is denied even inside the caller’s workspace', async () => {
  const db = environment.authenticatedContext('teacher-a').firestore();
  await assertFails(setDoc(doc(db, 'users/teacher-a'), { admin: true }));
  await assertFails(setDoc(doc(db, 'users/teacher-a/other/record-1'), { value: true }));
  await assertFails(setDoc(doc(db, 'users/teacher-a/syncState/other'), { value: true }));
});
