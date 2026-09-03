/**
 * MongoDB migration script: add tenantId to existing documents.
 *
 * Run with mongosh:
 *   mongosh <connection-uri>/<database> \
 *     --eval "var TENANT_ID='engineering'; var DRY_RUN=false;" \
 *     add-tenant-id.js
 *
 * Or via the shell wrapper:
 *   ./migrate-tenant-id.sh --tenant engineering --database true_connector_provider
 *
 * The script updates every document that has no tenantId (null or missing) in
 * the following collections, setting it to the value of TENANT_ID:
 *
 *   catalogs, datasets, distributions, dataservices,
 *   contract_negotiations, agreements, policy_enforcements,
 *   transfer_process, users
 *
 * When DRY_RUN is true the script prints what it *would* change but makes no
 * writes to the database.
 *
 * NOTE — ROLE_SUPER_ADMIN users intentionally have no tenantId (they operate
 * across all tenants).  The script explicitly excludes them from the update
 * and inserts the default super-admin account if one does not already exist.
 */

// ---------------------------------------------------------------------------
// Configuration — override via --eval before loading this file
// ---------------------------------------------------------------------------
if (typeof TENANT_ID === "undefined" || TENANT_ID === "") {
  throw new Error(
    "TENANT_ID must be set via --eval before loading this script.\n" +
    "Example: mongosh <uri>/<db> --eval \"var TENANT_ID='engineering'; var DRY_RUN=false;\" add-tenant-id.js"
  );
}

if (typeof DRY_RUN === "undefined") {
  DRY_RUN = true; // safe default: never mutate without an explicit opt-in
}

// ---------------------------------------------------------------------------
// Collections that carry a tenantId field
// ---------------------------------------------------------------------------
const COLLECTIONS = [
  "catalogs",
  "datasets",
  "distributions",
  "dataservices",
  "contract_negotiations",
  "agreements",
  "policy_enforcements",
  "transfer_process",
];

// Users are handled separately — ROLE_SUPER_ADMIN must never receive a tenantId.
const USERS_COLLECTION = "users";

// Default super-admin user inserted on pre-tenant installs (mirrors initial_data.json).
// The password hash is BCrypt for the default connector password.
const SUPER_ADMIN_USER = {
  _class: "it.eng.connector.model.User",
  _id: "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  firstName: "Super",
  lastName: "Admin",
  email: "superadmin@mail.com",
  password: "$2a$10$wQgl7stAxkVI1oxaynYU2uj.1IxzQ/ETygs32RoveH.rkgAfXAk5q",
  enabled: true,
  expired: false,
  locked: false,
  role: "ROLE_SUPER_ADMIN",
};

// ---------------------------------------------------------------------------
// Migration
// ---------------------------------------------------------------------------
const mode = DRY_RUN ? "DRY RUN" : "LIVE";
print(`\n========================================================`);
print(`  TRUE Connector — tenantId migration  [${mode}]`);
print(`  Target tenant  : ${TENANT_ID}`);
print(`  Database       : ${db.getName()}`);
print(`  Timestamp      : ${new Date().toISOString()}`);
print(`========================================================\n`);

const filter = {
  $or: [{ tenantId: { $exists: false } }, { tenantId: null }],
};

const update = { $set: { tenantId: TENANT_ID } };

let totalMatched = 0;
let totalModified = 0;

// ---------------------------------------------------------------------------
// Migrate non-user collections
// ---------------------------------------------------------------------------
COLLECTIONS.forEach((collectionName) => {
  const coll = db.getCollection(collectionName);

  const matched = coll.countDocuments(filter);
  print(`Collection: ${collectionName}`);
  print(`  Documents without tenantId : ${matched}`);

  if (matched === 0) {
    print(`  -> Nothing to do.\n`);
    return;
  }

  if (DRY_RUN) {
    print(`  -> [DRY RUN] Would set tenantId='${TENANT_ID}' on ${matched} document(s).\n`);
  } else {
    const result = coll.updateMany(filter, update);
    print(`  -> Modified: ${result.modifiedCount} document(s).\n`);
    totalModified += result.modifiedCount;
  }

  totalMatched += matched;
});

// ---------------------------------------------------------------------------
// Migrate users — exclude ROLE_SUPER_ADMIN (intentionally has no tenantId)
// ---------------------------------------------------------------------------
print(`Collection: ${USERS_COLLECTION} (excluding ROLE_SUPER_ADMIN)`);
const usersFilter = {
  $and: [
    { $or: [{ tenantId: { $exists: false } }, { tenantId: null }] },
    { role: { $ne: "ROLE_SUPER_ADMIN" } },
  ],
};
const usersColl = db.getCollection(USERS_COLLECTION);
const usersMatched = usersColl.countDocuments(usersFilter);
print(`  Documents without tenantId (non-super-admin): ${usersMatched}`);

if (usersMatched === 0) {
  print(`  -> Nothing to do.\n`);
} else if (DRY_RUN) {
  print(`  -> [DRY RUN] Would set tenantId='${TENANT_ID}' on ${usersMatched} user(s).\n`);
  totalMatched += usersMatched;
} else {
  const usersResult = usersColl.updateMany(usersFilter, update);
  print(`  -> Modified: ${usersResult.modifiedCount} user(s).\n`);
  totalMatched += usersMatched;
  totalModified += usersResult.modifiedCount;
}

// ---------------------------------------------------------------------------
// Ensure the ROLE_SUPER_ADMIN user exists (new in tenant-aware version)
// ---------------------------------------------------------------------------
print(`Collection: ${USERS_COLLECTION} — super-admin upsert`);
const existingSuperAdmin = usersColl.findOne({ role: "ROLE_SUPER_ADMIN" });
if (existingSuperAdmin) {
  print(`  -> ROLE_SUPER_ADMIN user already exists (${existingSuperAdmin.email}). Skipping.\n`);
} else if (DRY_RUN) {
  print(`  -> [DRY RUN] Would insert ROLE_SUPER_ADMIN user: ${SUPER_ADMIN_USER.email}\n`);
} else {
  usersColl.insertOne(SUPER_ADMIN_USER);
  print(`  -> Inserted ROLE_SUPER_ADMIN user: ${SUPER_ADMIN_USER.email}\n`);
}

print(`========================================================`);
if (DRY_RUN) {
  print(`  DRY RUN complete.`);
  print(`  Total documents that WOULD be updated: ${totalMatched}`);
  print(`  Re-run with DRY_RUN=false to apply changes.`);
} else {
  print(`  Migration complete.`);
  print(`  Total documents matched  : ${totalMatched}`);
  print(`  Total documents modified : ${totalModified}`);
}
print(`========================================================\n`);
