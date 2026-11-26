const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write settings or entries", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("entries").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("entries").doc("e1").set({
      userId: ALICE_UID,
      dateEpochDay: 20782,
      inputUnit: "BAGS",
      inputValue: 2,
      bagCapacityKgAtEntry: 15,
      weightKg: 30,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create, read, and update UserSettings with valid bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      bagCapacityKg: 15,
      calculationMethod: "FULL_REFILL",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bagCapacityKg: 20,
      calculationMethod: "DIRECT_SUM",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject out-of-bounds bag capacity, invalid method, ghost field, and createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bagCapacityKg: 0,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      calculationMethod: "INVALID",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Owner can create, list, update, and delete PelletEntry documents", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // List empty subcollection succeeds for owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("entries").get());

  // Create without optional note
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("entries").doc("entry_1").set({
      userId: ALICE_UID,
      dateEpochDay: 20782,
      inputUnit: "BAGS",
      inputValue: 3,
      bagCapacityKgAtEntry: 15,
      weightKg: 45,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create with optional note
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("entries").doc("entry_2").set({
      userId: ALICE_UID,
      dateEpochDay: 20785,
      inputUnit: "KG",
      inputValue: 30,
      bagCapacityKgAtEntry: 15,
      weightKg: 30,
      note: "Pellet sosnowy A1",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("entries").doc("entry_1").update({
      inputValue: 4,
      weightKg: 60,
      note: "Dopalenie",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject @DocumentId 'id' field in payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("entries").doc("entry_bad").set({
      id: "entry_bad",
      userId: ALICE_UID,
      dateEpochDay: 20782,
      inputUnit: "KG",
      inputValue: 15,
      bagCapacityKgAtEntry: 15,
      weightKg: 15,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("entries").doc("entry_future").set({
      userId: ALICE_UID,
      dateEpochDay: 20782,
      inputUnit: "KG",
      inputValue: 15,
      bagCapacityKgAtEntry: 15,
      weightKg: 15,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Delete succeeds for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("entries").doc("entry_2").delete()
  );
});

test("Cross-user access to settings and entries is strictly denied", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      bagCapacityKg: 15,
      calculationMethod: "FULL_REFILL",
      createdAt: created,
      updatedAt: created,
    });
    await db.collection("users").doc(BOB_UID).collection("entries").doc("bob_e1").set({
      userId: BOB_UID,
      dateEpochDay: 20780,
      inputUnit: "BAGS",
      inputValue: 2,
      bagCapacityKgAtEntry: 15,
      weightKg: 30,
      createdAt: created,
      updatedAt: created,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("entries").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("entries").doc("bob_e1").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("entries").doc("bob_e1").delete()
  );
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("stock_batches").get());
});

test("Owner can create, list, update, and delete stock_batches with valid bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("stock_batches").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("stock_batches").doc("batch_1").set({
      userId: ALICE_UID,
      bagsCount: 60,
      bagCapacityKg: 15,
      totalKg: 900,
      pricePerBagPln: 22.5,
      totalCostPln: 1350,
      purchaseDateEpochDay: 20782,
      purchasePlace: "Skład opału Kowalski",
      pelletType: "Sosnowy A1",
      note: "Pellet sosnowy",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("stock_batches").doc("batch_1").update({
      bagsCount: 50,
      totalKg: 750,
      pricePerBagPln: 24,
      totalCostPln: 1200,
      purchasePlace: "Castorama",
      pelletType: "Dębowy",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject negative price, invalid bagsCount, or missing purchaseDateEpochDay
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("stock_batches").doc("batch_bad").set({
      userId: ALICE_UID,
      bagsCount: 0,
      bagCapacityKg: 15,
      totalKg: 15,
      pricePerBagPln: 20,
      totalCostPln: 20,
      purchaseDateEpochDay: 20782,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("stock_batches").doc("batch_missing_date").set({
      userId: ALICE_UID,
      bagsCount: 10,
      bagCapacityKg: 15,
      totalKg: 150,
      pricePerBagPln: 20,
      totalCostPln: 200,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("stock_batches").doc("batch_1").update({
      pricePerBagPln: -5,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("stock_batches").doc("batch_1").delete()
  );
});

