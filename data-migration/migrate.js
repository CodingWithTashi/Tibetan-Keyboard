// Firestore Data Migration Script
// Run this script in Node.js environment with Firebase Admin SDK

const admin = require("firebase-admin");

// Initialize Firebase Admin (replace with your service account key)
const serviceAccount = require("./tibetan-keyboard.json");

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const db = admin.firestore();

// Field mapping based on your actual Firestore data structure
// From the obfuscated data (a,b,c,d...) to proper field names
const FIELD_MAPPING = {
  // Main user fields (based on the obfuscated data you showed earlier)
  a: "uid",
  b: "displayName",
  c: "email",
  d: "photoUrl",
  e: "createdAt", // timestamp
  f: "lastLoginAt", // timestamp
  g: "subscribed", // boolean (maps to isSubscribed in your model)
  h: "subscriptionType",
  i: "subscriptionStartDate",
  j: "subscriptionEndDate",

  // DeviceInfo fields (nested under 'k')
  k: "deviceInfo",

  // Analytics fields (nested under 'l' if exists)
  l: "analytics",
};

// DeviceInfo field mapping (inside the 'k' object)
const DEVICE_INFO_MAPPING = {
  a: "deviceModel",
  b: "androidVersion",
  c: "appVersion",
  d: "deviceId",
  e: "fcmToken",
};

// Analytics field mapping (inside the 'l' object)
const ANALYTICS_MAPPING = {
  a: "totalLogins",
  b: "lastActiveDate",
  c: "sessionCount",
  d: "averageSessionDuration",
  e: "featuresUsed",
  f: "referralSource", // based on your data showing "direct"
};

async function migrateUserDocument(docRef, docData) {
  const migratedData = {};

  // Migrate main fields
  for (const [obfuscatedKey, originalKey] of Object.entries(FIELD_MAPPING)) {
    if (docData.hasOwnProperty(obfuscatedKey)) {
      const value = docData[obfuscatedKey];

      // Handle nested objects
      if (
        obfuscatedKey === "k" &&
        typeof value === "object" &&
        value !== null
      ) {
        // Migrate DeviceInfo
        const migratedDeviceInfo = {};
        for (const [obfKey, origKey] of Object.entries(DEVICE_INFO_MAPPING)) {
          if (value.hasOwnProperty(obfKey)) {
            migratedDeviceInfo[origKey] = value[obfKey];
          }
        }
        migratedData[originalKey] = migratedDeviceInfo;
      } else if (
        obfuscatedKey === "l" &&
        typeof value === "object" &&
        value !== null
      ) {
        // Migrate Analytics
        const migratedAnalytics = {};
        for (const [obfKey, origKey] of Object.entries(ANALYTICS_MAPPING)) {
          if (value.hasOwnProperty(obfKey)) {
            migratedAnalytics[origKey] = value[obfKey];
          }
        }
        migratedData[originalKey] = migratedAnalytics;
      } else {
        // Regular field
        migratedData[originalKey] = value;
      }
    }
  }

  // Add default values for missing fields (based on your actual data structure)
  if (!migratedData.createdAt) {
    migratedData.createdAt = admin.firestore.FieldValue.serverTimestamp();
  }

  if (!migratedData.lastLoginAt) {
    migratedData.lastLoginAt = admin.firestore.FieldValue.serverTimestamp();
  }

  if (!migratedData.hasOwnProperty("subscribed")) {
    migratedData.subscribed = false;
  }

  if (!migratedData.subscriptionType) {
    migratedData.subscriptionType = "FREE";
  }

  if (!migratedData.subscriptionEndDate) {
    migratedData.subscriptionEndDate = null;
  }

  if (!migratedData.subscriptionStartDate) {
    migratedData.subscriptionStartDate = null;
  }

  // Ensure analytics object exists with proper structure
  if (!migratedData.analytics) {
    migratedData.analytics = {
      totalLogins: 1,
      lastActiveDate: admin.firestore.FieldValue.serverTimestamp(),
      sessionCount: 0,
      averageSessionDuration: 0,
      featuresUsed: [],
      referralSource: "direct",
    };
  }

  // Ensure deviceInfo object exists
  if (!migratedData.deviceInfo) {
    migratedData.deviceInfo = {
      deviceModel: "Unknown",
      androidVersion: "Unknown",
      appVersion: "Unknown",
      deviceId: "Unknown",
      fcmToken: "",
    };
  }

  return migratedData;
}

async function migrateAllUsers() {
  try {
    console.log("Starting user data migration...");

    const usersRef = db.collection("users");
    const snapshot = await usersRef.get();

    if (snapshot.empty) {
      console.log("No users found to migrate.");
      return;
    }

    const batch = db.batch();
    let batchCount = 0;
    let totalProcessed = 0;

    for (const doc of snapshot.docs) {
      const docData = doc.data();

      // Check if document has obfuscated fields
      if (docData.hasOwnProperty("a") || docData.hasOwnProperty("b")) {
        console.log(`Migrating user: ${doc.id}`);

        const migratedData = await migrateUserDocument(doc.ref, docData);

        // Replace the document with migrated data
        batch.set(doc.ref, migratedData, { merge: false });
        batchCount++;

        // Firestore batch limit is 500 operations
        if (batchCount >= 450) {
          await batch.commit();
          console.log(`Committed batch of ${batchCount} documents`);
          batchCount = 0;
        }
      } else {
        console.log(
          `Skipping user ${doc.id} - already migrated or no obfuscated fields`
        );
      }

      totalProcessed++;
    }

    // Commit remaining batch
    if (batchCount > 0) {
      await batch.commit();
      console.log(`Committed final batch of ${batchCount} documents`);
    }

    console.log(`Migration completed! Processed ${totalProcessed} users.`);
  } catch (error) {
    console.error("Migration failed:", error);
  }
}

// Dry run function to preview changes without actually updating
async function dryRunMigration() {
  try {
    console.log("Starting DRY RUN - no data will be changed...");

    const usersRef = db.collection("users");
    const snapshot = await usersRef.limit(5).get(); // Just check first 5 users

    for (const doc of snapshot.docs) {
      const docData = doc.data();

      if (docData.hasOwnProperty("a") || docData.hasOwnProperty("b")) {
        console.log(`\n--- User ${doc.id} ---`);
        console.log("BEFORE:", JSON.stringify(docData, null, 2));

        const migratedData = await migrateUserDocument(doc.ref, docData);
        console.log("AFTER:", JSON.stringify(migratedData, null, 2));
      }
    }
  } catch (error) {
    console.error("Dry run failed:", error);
  }
}

// Migrate single user for testing
async function migrateSingleUser(userId) {
  try {
    const userRef = db.collection("users").doc(userId);
    const doc = await userRef.get();

    if (!doc.exists) {
      console.log(`User ${userId} not found`);
      return;
    }

    const docData = doc.data();
    console.log("BEFORE:", JSON.stringify(docData, null, 2));

    const migratedData = await migrateUserDocument(userRef, docData);
    console.log("AFTER:", JSON.stringify(migratedData, null, 2));

    // Uncomment to actually update:
    // await userRef.set(migratedData, { merge: false });
    // console.log('User migrated successfully!');
  } catch (error) {
    console.error("Single user migration failed:", error);
  }
}

// Main execution
async function main() {
  const args = process.argv.slice(2);

  if (args.includes("--test-user")) {
    const userId = args[args.indexOf("--test-user") + 1];
    if (!userId) {
      console.log("Usage: node migrate.js --test-user USER_ID");
      return;
    }
    await migrateSingleUser(userId);
  } else if (args.includes("--dry-run")) {
    await dryRunMigration();
  } else if (args.includes("--migrate")) {
    const confirm = args.includes("--confirm");
    if (!confirm) {
      console.log("⚠️  WARNING: This will modify your production data!");
      console.log("⚠️  Run with --dry-run first to preview changes.");
      console.log("⚠️  Add --confirm flag to proceed with migration.");
      return;
    }
    await migrateAllUsers();
  } else {
    console.log("Usage:");
    console.log("  node migrate.js --test-user USER_ID  # Test single user");
    console.log("  node migrate.js --dry-run     # Preview changes");
    console.log(
      "  node migrate.js --migrate --confirm  # Run actual migration"
    );
  }

  process.exit(0);
}

main();
