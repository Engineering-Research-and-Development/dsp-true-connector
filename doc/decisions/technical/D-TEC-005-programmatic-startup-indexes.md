# D-TEC-005 — Programmatic startup index creation via MongoTemplate

## Metadata
- Status: Accepted
- Date: 2026-06-30
- Owner: TRUE Connector team
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: mongodb, indexing, multi-tenancy, startup, performance
- Risk Level: Low

## Context

Multi-tenancy (MT3) introduces tenantId as a mandatory partition key on every collection.
All major query paths — catalog lookup, transfer process resolution, audit event retrieval,
agreement validation — now filter by `tenantId`. Without compound indexes on
`(tenantId, <primary-query-field>)`, these queries degrade to full collection scans as
tenant counts and document volumes grow.

Spring Data MongoDB's `@CompoundIndex` annotations on domain model classes are an option,
but the TRUE Connector domain model intentionally avoids persistence concerns in model
classes (see model-class-guidelines). Annotation-based indexes also require Spring Data to
derive and create them from field names that may diverge from MongoDB field names after
serialization.

An alternative is to let MongoDB auto-create indexes from queries at runtime, but this is
disabled in production environments and unreliable across connector restarts.

## Decision

Compound indexes are created programmatically in `InitialDataLoader` using a
`@EventListener(ApplicationReadyEvent.class)` method that calls
`MongoTemplate.getCollection(collectionName).createIndex(keys, options)` for each target
collection. Index creation is idempotent: MongoDB silently skips any index that already
exists with the same key specification and options.

Collections and their compound indexes (as of MT3):

| Collection | Index key fields |
|---|---|
| `catalogs` | `tenantId`, `_id` |
| `datasets` | `tenantId`, `_id` |
| `contract_negotiations` | `tenantId`, `_id` |
| `transfer_process` | `tenantId`, `_id` |
| `agreements` | `tenantId`, `_id` |
| `audit_events` | `tenantId`, `_id` |
| `application_properties` | `tenantId`, `_id` |

## Alternatives Considered

- **`@CompoundIndex` annotations on model classes** → rejected; introduces persistence concerns
  into domain model classes, which the project guidelines forbid. Field name divergence after
  serialization also makes this brittle.
- **Spring Data schema-based auto-index creation** (`spring.data.mongodb.auto-index-creation=true`)
  → rejected; relies on `@Indexed`/`@CompoundIndex` on model classes (same problem as above)
  and is not recommended for production.
- **Flyway / Mongock migration scripts** → rejected; adds a migration framework dependency for
  a simple, idempotent operation that the native MongoDB driver handles natively.

## Rationale

`MongoTemplate.getCollection(...).createIndex(...)` is the lowest-friction approach: it is
idempotent, runs against the live Testcontainers container in integration tests
(`MongoCompoundIndexIT`), requires no additional framework, and keeps index definitions
close to the startup logic that owns them. `ApplicationReadyEvent` fires after the full
application context (including bean wiring and `CommandLineRunner` data loading) is ready,
ensuring indexes exist before the first live query.

## Consequences

### Positive
- Indexes exist before the first production query after any startup — no warm-up window.
- Idempotent: safe to execute on every restart; connectors with pre-existing indexes
  incur only one fast `listIndexes` call per collection.
- No model-class coupling; index definitions live in infrastructure code.
- Verified by `MongoCompoundIndexIT`, which asserts the correct index name on all 7 collections.

### Negative
- Index definitions are in Java code, not in a migration script, so they cannot be versioned
  as a schema changelog. New collections require a code change to add their index.
- `ApplicationReadyEvent` fires before the HTTP server is fully ready; a very slow index
  creation on a large collection could marginally delay first request availability.

### Risks
- A future collection rename that is not reflected here would leave that collection unindexed.
  Mitigated by `MongoCompoundIndexIT`, which would fail if the collection name drifts.

## Related
- Decisions: [D-TEC-001](D-TEC-001-mongodb-persistence.md), [D-TEC-002](D-TEC-002-testcontainers-integration-testing.md)
- Docs: [architecture.md](../../doc/architecture.md) — Multi-tenancy section
- Tickets: #265 (T265), #250 (MT3 slice)
