# Dashboard Seed Fixtures Design

## Goal

Populate both runtime seed profiles with realistic dashboard data for local UI and API testing:

- `connector/src/main/resources/initial_data-consumer.json`
- `connector/src/main/resources/initial_data-provider.json`

Each profile will contain 100 contract negotiations, 100 transfer processes, and
100 audit events. Runtime JVM metrics remain live-derived and are not represented
in seed data.

## Data shape

### Contract negotiations

Add 100 valid `contract_negotiations` documents per profile. Records will use the
profile's existing tenant ID and valid negotiation states and roles. Each record
will have unique IDs and unique consumer/provider process IDs. Each negotiation
will reference a corresponding seeded agreement.

### Agreements and policy enforcement

Add one supporting agreement and one policy-enforcement document for each seeded
negotiation. Agreement identifiers, technical MongoDB IDs, negotiation references,
and tenant IDs will be unique and internally consistent. Policy-enforcement
counts will be valid integer values.

These supporting documents are not separate dashboard categories, but are
required to keep the seeded negotiation relationships valid.

### Transfer processes

Add 100 valid `transfer_process` documents per profile. Records will use valid
transfer states, roles, formats, download flags, and the profile's existing
tenant ID. Each transfer will reference one of the seeded agreements and the
existing dataset ID. Creation and modification timestamps will be included.

### Audit events

Add 100 valid `audit_events` documents per profile. Events will use supported
audit event types, realistic descriptions, sources, usernames, roles/details,
and the profile's existing tenant ID. Every event will have a timestamp.

## Timestamp distribution

Timestamps will fall between `2025-08-31T00:00:00Z` and
`2026-08-31T23:59:59Z`, inclusive.

They will be random but intentionally non-uniform: records will be weighted
toward recent activity, with additional irregular clusters and gaps across the
year. This produces a more realistic dashboard timeline than evenly spaced or
uniformly distributed samples. Related records will receive nearby but not
identical timestamps so event and process activity appears naturally grouped.

## Profile parity

Both files will receive the same record counts and equivalent data shapes. The
existing profile-specific tenant IDs, callback URLs, and participant settings
will be preserved. Generated IDs will include a profile-specific prefix to avoid
confusing records when comparing consumer and provider exports.

## Validation

After generation:

1. Parse both files as JSON.
2. Confirm each profile has exactly 100 records in each dashboard category.
3. Confirm there are 100 supporting agreements and policy-enforcement records.
4. Confirm every negotiation agreement reference and transfer agreement ID resolves.
5. Confirm all generated timestamps are within the requested one-year window.
6. Confirm no generated IDs are duplicated within a profile.
