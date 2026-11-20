# D-ARC-007 — DCP participant context: one identity, route, key set and data partition per participant

## Metadata
- Status: Accepted
- Date: 2026-11-20
- Owner: DCP / security
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: dcp, multi-tenant, did, security, persistence, module-boundaries
- Risk Level: High

## Context
The DCP modules (`dcp-common`, `dcp-holder`, `dcp-verifier`, `dcp-issuer`) assume a single participant
per process: one DID, one signing key, one set of service endpoints, one trusted-issuer list and one
issuer metadata configuration, all loaded from the `dcp.*` properties (`DcpProperties`,
`BaseDidDocumentConfiguration`). Holder credentials, issuer requests, status lists, key metadata and
DCP audit events are stored and queried globally, so a presentation query can return credentials of
other tenants and holders, and request status, approval, rejection and key rotation have no ownership
boundary. DCP routes are fixed (`/dcp`, `/issuer`, root `/.well-known/did.json`) while the connector's
DSP routes are tenant-prefixed, and `HttpDidResolverService` drops the DID path, so a tenant DID such
as `did:web:host:tenant` cannot be resolved. The gaps are listed in
[`DCP_SPEC_GAPS.md`](../../../dcp/doc/DCP_SPEC_GAPS.md#multitenancy-implementation-gaps).

The constraints:

- The DCP modules are meant to be extracted into their own repository. They must work for the
  multi-tenant TRUE Connector, for a single-tenant deployment and for an unrelated project, so they may
  not depend on `connector`, `tools`, Keycloak or the connector's tenant model.
- DCP v1.0.1 defines participant DIDs and protocol messages but no tenant IDs, tenant routing or tenant
  data partitions. DCP wire messages must stay specification-conformant.
- `did:web` resolution is defined by the `did:web` method: a DID with a path resolves to
  `https://<host>/<path>/did.json`, a DID without one to `https://<host>/.well-known/did.json`.
- Existing single-tenant configuration and data must keep working without changes or data loss.
- Tenant isolation must hold for synchronous and asynchronous work (`DcpAuditEventListener` persists
  on an `@Async` thread) and across replicas.
- The connector already validates enabled tenants per request without caching (D-ARC-004) and
  reserves `internal` as a tenant ID (D-ARC-005).

The accepted design is in the spec
[DCP participant context and zone-separated endpoint security](../../../dcp/doc/SPEC_DCP_PARTICIPANT_CONTEXT_AND_ZONE_SECURITY.md)
(#439). This ADR records its participant context part. The endpoint zone security part is
D-ARC-008 (#440).

## Decision
DCP gets its own, protocol-native **participant context**: the identity a request or a unit of work is
served under. DCP code never sees a tenant. Single-participant is the case of one participant, so both
modes share one code path and one data layout.

**Participant context SPI (in `dcp-common`).**

- A **participant context** is an immutable value with a participant context ID, a DID, a signing key
  reference, the advertised service entries, the trusted issuers, the issuer metadata configuration
  (when acting as Issuer) and a state (enabled or disabled). The ID is a plain string with no meaning
  to DCP.
- **Participant context provider**: looks up a participant context by ID and returns an optional
  result. A disabled participant is reported as disabled, never returned as usable. The contract
  assumes no caching.
- **Participant context resolver**: determines the participant context ID from an inbound request.
  Two strategies ship, selected with `dcp.participant.resolver`: `fixed` (always the single configured
  participant) and `path` (the first path segment before the DCP base path). Host-based resolution
  stays a later extension point.
- **Participant key provider**: returns a participant's active signing key, the public verification
  keys to publish (the active key plus keys retired within the grace period) and a rotation
  operation. Signing, DID document generation and key rotation always go through it.
- **Participant admin authorization**: a hook through which the library asks the host whether the
  authenticated administration principal may act on a given participant.
- **Single-participant defaults**: each SPI has a default registered only when the host defines none.
  The default provider is built from the existing `dcp.*` properties and has the participant context
  ID `default`; the default resolver is `fixed`; the default admin authorization allows any
  authenticated admin to act on the single participant. Existing single-tenant configuration stays
  valid unchanged.

**Identity, DID documents and routes.**

- In the connector each tenant is one participant with its own DID, `did:web:<host>:<tenantId>`,
  derived from the configured public host.
- Each participant's DID document is served at the URL its DID implies under the `did:web` method:
  `/.well-known/did.json` for a DID without a path, `/<p1>/<p2>/did.json` for
  `did:web:<host>:<p1>:<p2>`. In multi-participant mode every participant DID has a path and the root
  `/.well-known/did.json` returns 404; it never falls back to any participant.
- DID documents advertise the participant's own prefixed `CredentialService` and `IssuerService`
  endpoints, over HTTPS; only the dev-only `dcp.allow-insecure-http=true` (#420) allows `http`.
- The DID resolver keeps the DID path when it converts a `did:web` DID to a URL.
- The DCP base paths (`dcp`, `issuer` by default) are configurable. With the `path` resolver the
  routes are `/<participantId>/<dcpBase>/...` and `/<participantId>/<issuerBase>/...`, matching the
  connector's tenant-prefixed DSP routes. With the `fixed` resolver today's unprefixed routes stay.

**Binding and status codes.**

- For every authenticated DCP protocol request the routed participant's DID must equal the
  Self-Issued ID Token `aud`; the caller identity is the verified `sub`.
- Unknown or disabled participant: **404**, before any DCP processing or token validation.
- Missing or invalid Self-Issued ID Token, or `aud` not equal to the routed participant's DID:
  **401**. A missing access token on a Credential Service query is also 401 (#407).
- An authenticated administration principal acting on a participant it is not authorized for:
  **403**.
- Credential request status queried by anyone but the client that created the request: **404**, so
  existence is not revealed (#409).

**Reserved IDs.** Participant and tenant IDs must not collide with zone prefixes. Besides `internal`
(D-ARC-005), `api`, `actuator`, `.well-known`, the configured DCP base paths (`dcp`, `issuer` by
default) and the DSP protocol prefixes (`connector`, `catalog`, `negotiations`, `transfers`,
`consumer`) are reserved.

**Persistence partitioning.**

- Every DCP persisted document carries a required, non-null `participantContextId`: holder verifiable
  credentials and credential status records, issuer credential requests and status lists, key
  metadata, consent records, replay-protection entries and DCP audit events.
- Every repository read and write takes the participant context ID. No repository method queries
  across participants and there is no unscoped `findAll`. Lookups by credential ID, request ID or
  issuer PID are always combined with the participant context ID; presentation lookups are also
  combined with the authenticated holder DID.
- Compound indexes lead with `participantContextId` and are created programmatically at startup
  (D-TEC-005).
- A one-time startup migration sets `participantContextId` to `default` on existing records that
  lack it. A host adapter that maps an existing single deployment to a named tenant documents how to
  re-key those records.
- MongoDB stays the persistence technology (D-TEC-001); no storage abstraction is introduced.
- Status list IDs and URLs stay globally unambiguous because they are public; in multi-participant
  mode the URL is `/<participantId>/<issuerBase>/status/{listId}`.
- Replay protection, presentation rate limiting and consent are keyed by participant context ID plus
  the relevant DID. Replay protection is shared across replicas in MongoDB with a TTL index keyed by
  participant context ID and `jti` (#428); rate limiting stays per replica.

**Keys.** DCP signing keys are separate from TLS: they are never issued by or chained to the TLS root
or intermediate CA and never share a keystore entry with a TLS key. A self-signed certificate used
only as a keystore container for the DCP key pair is acceptable. After a rotation the previous public
key stays published in the participant's DID document for the grace period, then is removed.

**Explicit context passing.** Service methods take the participant context (or its ID) as an explicit
parameter. A request-scoped holder may exist only as a convenience at the controller edge.
Asynchronous work (audit persistence, credential delivery, issuance) receives the context explicitly
and never relies on thread-local inheritance.

**No tenant on the wire.** No DCP wire message carries a tenant or participant context ID, and DCP
scopes are never used as tenant selectors. The participant comes from the route and is bound to the
authenticated token.

**Connector adapter boundary.** DCP modules do not depend on `connector` or `tools`; the dependency
points from the connector to DCP only. The connector supplies, as wiring in `connector`:

- a participant context provider backed by its tenant registry and enabled-tenant validation, with a
  fresh enabled-tenant check per request (D-ARC-004). Participant context ID equals tenant ID; keys,
  trusted issuers and issuer metadata come from tenant-scoped configuration;
- the `path` resolver;
- a participant admin authorization hook built on the connector's existing tenant binding: a tenant
  admin acts only on its own tenant, a super admin on every tenant;
- the connector's tenant context set from the resolved and authenticated participant, so
  connector-side code (audit, dashboard) attributes DCP work to the tenant.

**Connector rollout.** The adapter ships behind `dcp.participant.resolver=path` (#444); the connector
default stays `fixed`. #448 makes `path` the connector default, moves CI to tenant DIDs and re-keys
`default` records to a configured tenant. An operator of a single-tenant connector who wants to keep
the configured DID sets `fixed`.

## Alternatives Considered
- **One DID per connector, tenants internal only** (#423 Q1) → rejected because remote participants
  would identify the connector operator, not the tenant's organization; per-tenant trust policy, keys,
  key rotation and status lists would have no identity to attach to; and the tenant would have to be
  carried in DCP messages or scopes, which the specification does not define.
- **One DID per tenant on its own host or subdomain** (#423 Q1) → rejected because it needs DNS and a
  TLS certificate per tenant and a host-based resolver, while the connector already routes tenants by
  path. Host-based resolution stays an extension point of the resolver SPI.
- **A fixed `/dcp` route with the tenant resolved from the token audience DID** (#423 Q2) → rejected
  because the participant must be known before the token is validated (unknown or disabled
  participants get 404 before any processing), unauthenticated discovery and status list routes carry
  no token, and the routes would differ from the tenant-prefixed DSP routes. With path routing the
  audience is still checked, as a binding, not as a selector.
- **Serve the root `/.well-known/did.json` for a configured default tenant** (#423 Q3) → rejected
  because an unscoped request would fall back to one tenant's identity, and the root document would
  not match the DID it serves, since every tenant DID has a path.
- **DCP depends directly on the `tools` tenant services (`TenantContextHolder`, `TenantService`)** →
  rejected because it solves multitenancy for the connector only and makes the library unusable in any
  other host. It would also tie DCP to a thread-local tenant context, which asynchronous DCP work does
  not inherit.

## Rationale
A participant context is the concept DCP itself uses: a DID with its keys and service endpoints.
Modelling multitenancy as "several participants" keeps the library free of tenant concepts, keeps DCP
messages specification-conformant and lets any host decide what a participant is. Making single-tenant
the one-participant case means one code path, one data layout and a migration that only fills in
`default`, so moving between the modes needs no second implementation.

One `did:web` DID per tenant with the document at the path the DID implies is what standard `did:web`
resolvers expect, so remote participants need nothing connector-specific. The CI DIDs already have a
path (`did:web:connector-a%3A8080:connectora` → `/connectora/did.json`), so serving documents where the
DID points keeps them resolving once the resolver honours the path. Path routing reuses the connector's
existing tenant-prefixed routing and lets the participant be resolved and rejected before token
validation; binding `aud` to the routed participant stops a token issued for tenant A from being used
against tenant B.

Required `participantContextId` fields, scoped repository methods without an unscoped `findAll`, and
explicit context parameters make isolation structural rather than a convention each caller must
remember, and they cover asynchronous work that a `ThreadLocal` would miss. Keeping DCP signing keys
apart from the TLS CA follows DCP's trust model: signatures are verified against the keys in the
resolved DID document, so CA-chaining adds no DCP trust and only couples DCP rotation to TLS
operations. The SPI defaults and the `fixed` resolver keep every existing deployment unchanged until a
host opts in, and the connector's staged rollout (#444, then #448) lets the adapter ship and be tested
before it becomes the default.

## Consequences

### Positive
- Each tenant has its own DCP identity, keys, rotation, trust policy, status lists and data partition.
- Remote participants resolve tenant DIDs with any standard `did:web` resolver.
- The DCP modules stay extractable and reusable by unrelated projects; hosts plug in their own
  provider, resolver, key provider and admin authorization.
- Single-tenant deployments keep their `dcp.*` configuration and data.
- A token for one participant cannot be used against another, and request status does not reveal
  other clients' requests.

### Negative
- Every DCP service and repository method signature gains a participant context parameter, a broad
  refactor across all DCP modules (#441, #442, #424–#428).
- Every DCP collection gains a field and compound indexes, and existing data needs a one-time
  migration.
- More reserved IDs: tenants cannot be named `api`, `actuator`, `.well-known`, `dcp`, `issuer` (or the
  configured base paths) or a DSP protocol prefix.
- Single-tenant connectors that switch to `path` after #448 get a new, tenant DID; remote participants
  that trusted the old DID must be updated. Operators who need the old DID keep `fixed`.
- Replay protection becomes a MongoDB round trip per token instead of an in-memory lookup.

### Risks
- A repository or service method is added without the participant context ID and leaks data across
  participants. Mitigation: no unscoped repository methods, a required non-null field, cross-tenant
  isolation tests with two participants, and the SPI contract test kit (`dcp-spi-testkit`, #443) run
  against both the library defaults and the connector adapter.
- Asynchronous work loses or mixes up the participant. Mitigation: the context is passed explicitly,
  never inherited from a thread-local, and audit attribution after asynchronous persistence is tested.
- An existing tenant ID collides with a newly reserved ID. Mitigation: tenant validation rejects
  reserved IDs; existing tenants must be checked before the connector switches to `path` (#448).
- An unscoped request resolves to an arbitrary participant. Mitigation: no root DID document in
  multi-participant mode, unknown or disabled participants get 404, and the resolver contract tests
  cover unscoped requests and reserved IDs.
- A rotated key is removed before tokens signed with it expire or cached DID documents refresh.
  Mitigation: the key provider publishes retired keys for a grace period, covered by the key provider
  contract tests.

## Related
- Decisions: [D-ARC-001](D-ARC-001-multi-module-maven-structure.md), [D-ARC-004](D-ARC-004-trusted-issuers-from-config-and-tenant-registry.md), [D-ARC-005](D-ARC-005-internal-call-zone-with-in-memory-key.md), [D-ARC-006](D-ARC-006-per-realm-role-allow-list-and-per-tenant-realm-credentials.md), [D-TEC-001](../technical/D-TEC-001-mongodb-persistence.md), [D-TEC-005](../technical/D-TEC-005-programmatic-startup-indexes.md). D-ARC-008 (endpoint zone security, #440) is the companion decision.
- Docs: [SPEC_DCP_PARTICIPANT_CONTEXT_AND_ZONE_SECURITY.md](../../../dcp/doc/SPEC_DCP_PARTICIPANT_CONTEXT_AND_ZONE_SECURITY.md), [DCP_SPEC_GAPS.md](../../../dcp/doc/DCP_SPEC_GAPS.md#multitenancy-implementation-gaps), [multi-tenant-technical.md](../../multi-tenant-technical.md), [glossary](../../glossary.md#participant-context)
- Tickets: #423 (this decision), #439 (spec), #397 (roadmap), #441, #442, #443, #444, #448, #424–#428
