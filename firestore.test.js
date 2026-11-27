const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_editor_123";
const BOB_UID = "bob_editor_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid, ts) {
  return {
    userId: uid,
    displayName: "Alex Rivera",
    themeMode: "OLED_DARK",
    batterySaverEnabled: true,
    defaultResolution: "4K",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validProjectPayload(uid, ts) {
  return {
    userId: uid,
    title: "Neon Rain Reel 4K",
    aspectRatio: "16:9",
    resolution: "4K",
    frameRate: 60,
    totalDurationMs: 18000,
    colorPreset: "KODAK_2383",
    exposure: 0.15,
    contrast: 1.15,
    saturation: 1.1,
    temperature: 0.08,
    tint: 0.0,
    vignette: 0.25,
    captionStyle: "KINETIC_BOLD",
    clips: [
      {
        clipId: "clip_1",
        label: "Opening Street Shot",
        sourceUri: "",
        startMs: 0,
        endMs: 9000,
        trimStartMs: 0,
        trimEndMs: 9000,
        speed: 1.0,
        volume: 0.85,
        muted: false,
        transition: "CROSS_DISSOLVE",
        sampleScene: "NEON_CITY",
      },
    ],
    audioTracks: [
      {
        trackId: "track_orig",
        name: "Original Audio",
        role: "ORIGINAL",
        sourceUri: "",
        volume: 0.9,
        pan: 0.0,
        muted: false,
        solo: false,
        fadeInMs: 0,
        fadeOutMs: 400,
        offsetMs: 0,
        durationMs: 18000,
      },
    ],
    captions: [
      {
        captionId: "cap_1",
        startMs: 500,
        endMs: 3200,
        text: "Midnight rain transforms the city skyline.",
        emphasisWord: "Midnight",
        speaker: "Narrator",
      },
    ],
    aiSummary: "AI Quick-Cut applied 2 dynamic pacing trims.",
    cloudBackupStatus: "SYNCED",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validExportPayload(uid, projectId, ts) {
  return {
    userId: uid,
    projectId: projectId,
    projectTitle: "Neon Rain Reel 4K",
    resolution: "4K",
    codec: "HEVC_H265",
    frameRate: 60,
    durationMs: 18000,
    estimatedSizeMb: 124.5,
    destination: "YOUTUBE_SHORTS",
    localOutputUri: "content://media/external/video/media/42",
    status: "COMPLETED",
    createdAt: ts,
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

test("Unauthenticated user: denied all reads and writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, ts))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .set(validProjectPayload(ALICE_UID, ts))
  );
});

test("UserProfile: owner can create and partially update preferences; ghost fields & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, created))
  );

  // Partial update matching Kotlin UserProfileRepository
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      themeMode: "DAYLIGHT",
      batterySaverEnabled: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Shadow update with ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      themeMode: "STUDIO_DARK",
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Cross-user read & write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("VideoProject: owner create, list, partial update, delete succeed; invalid payloads & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Empty subcollection list must succeed for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );

  // Valid Kotlin-mirrored create payload (no "id" key)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_neon_01")
      .set(validProjectPayload(ALICE_UID, created))
  );

  // Reject extra @DocumentId "id" key on create
  const withId = { ...validProjectPayload(ALICE_UID, created), id: "proj_bad_id" };
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_bad_id")
      .set(withId)
  );

  // Partial update of grading + clips + captions succeeds while preserving createdAt
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_neon_01")
      .update({
        colorPreset: "TEAL_ORANGE",
        exposure: 0.4,
        contrast: 1.3,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_neon_01")
      .update({
        createdAt: new Date(Date.now() - 500),
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Reject out-of-bounds value poisoning
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_neon_01")
      .update({
        exposure: 9.5,
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Cross-user isolation on list/get/update/delete
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_neon_01")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_neon_01")
      .delete()
  );

  // Owner delete succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_neon_01")
      .delete()
  );
});

test("ExportRecord: requires existing parent project, enforces immutability, isolates users", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // Creating export for non-existent project must fail (orphan prevention)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("exports")
      .doc("exp_orphan")
      .set(validExportPayload(ALICE_UID, "missing_proj", ts))
  );

  // Seed parent project
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_4k_master")
      .set(validProjectPayload(ALICE_UID, ts))
  );

  // Creating export for existing project succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("exports")
      .doc("exp_001")
      .set(validExportPayload(ALICE_UID, "proj_4k_master", ts))
  );

  // Owner list exports succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("exports").get()
  );

  // Export records are terminal & immutable (updates always fail)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("exports")
      .doc("exp_001")
      .update({ status: "FAILED" })
  );

  // Cross-user read denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("exports").get()
  );
});
