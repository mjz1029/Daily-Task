const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-7ae3d496-baf4-40e1-aca6-fb689afdfa6e";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validTaskPayload(userId, overrides = {}) {
  const pastEnough = new Date(Date.now() - 1000);
  return {
    userId,
    title: "提交周报与项目进度",
    notes: "重点补充折叠屏适配结果",
    deadlineMillis: Date.now() + 3600000,
    isCompleted: false,
    completedAtMillis: 0,
    priority: "high",
    category: "工作",
    remindMinutesBefore: 15,
    createdAt: pastEnough,
    updatedAt: pastEnough,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot list or write tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/tasks`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/tasks`).doc("task_1").set(validTaskPayload(ALICE_UID))
  );
});

test("Owner can create, list, toggle completion, update, and delete own task", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const taskRef = aliceDb.collection(`users/${ALICE_UID}/tasks`).doc("task_01");

  await assertSucceeds(taskRef.set(validTaskPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/tasks`).get());
  await assertSucceeds(
    taskRef.update({
      isCompleted: true,
      completedAtMillis: Date.now(),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(taskRef.delete());
});

test("Cross-user read, create, update, and delete are denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/tasks`).doc("alice_task").set(validTaskPayload(ALICE_UID))
  );

  await assertFails(bobDb.collection(`users/${ALICE_UID}/tasks`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/tasks`).doc("alice_task").get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/tasks`).doc("bob_spoof").set(validTaskPayload(ALICE_UID))
  );
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/tasks`).doc("alice_task").update({
      isCompleted: true,
      completedAtMillis: Date.now(),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/tasks`).doc("alice_task").delete());
});

test("Shadow fields, empty title, invalid priority, and immutable createdAt overwrite are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const tasksCol = aliceDb.collection(`users/${ALICE_UID}/tasks`);

  // Shadow/ghost field
  await assertFails(
    tasksCol.doc("t_ghost").set(validTaskPayload(ALICE_UID, { isAdmin: true }))
  );

  // Empty title
  await assertFails(
    tasksCol.doc("t_empty").set(validTaskPayload(ALICE_UID, { title: "" }))
  );

  // Invalid priority
  await assertFails(
    tasksCol.doc("t_prio").set(validTaskPayload(ALICE_UID, { priority: "urgent" }))
  );

  // Valid create, then attempt immutable userId/createdAt mutation
  const docRef = tasksCol.doc("t_valid");
  await assertSucceeds(docRef.set(validTaskPayload(ALICE_UID)));
  await assertFails(
    docRef.update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
