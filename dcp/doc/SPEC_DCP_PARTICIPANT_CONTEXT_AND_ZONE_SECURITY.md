# [SPEC] DCP participant context and zone-separated endpoint security

Status: Accepted. All decisions are settled; the review findings of 2026-11-19 are folded in.
Date: 2026-11-20
Related: roadmap #397. It answers #423 and reshapes #424–#428 (M1–M5). It incorporates the ticked decisions of #404, #405, #407, #409, #410, #417, #420 and #428.

## Problem Statement

The DCP modules are meant to be extracted into their own repository. They should be usable by the TRUE Connector in a multi-tenant deployment, by a single-tenant deployment, and by an unrelated project that needs the Decentralized Claims Protocol for part of its functionality. Today none of these is supported cleanly:

- **One identity per process.** The DCP modules assume a single participant: one DID, one signing key, one set of service endpoints, one trusted-issuer list and one issuer metadata configuration, all loaded process-wide.
- **No partitioning.** Holder credentials, issuer requests, status lists, key metadata and audit events are stored and queried globally. A presentation query can return credentials of other tenants and holders. Request status, approval, rejection and key rotation have no ownership boundary.
- **No tenant-aware routing or resolution.** DCP routes are fixed (`/dcp`, `/issuer`, root `/.well-known/did.json`) while the connector's DSP routes are tenant-prefixed. The DID resolver drops the DID path, so tenant DIDs such as `did:web:host:tenant` cannot be resolved.
- **The obvious fix would break reuse.** Making DCP depend on the connector's tenant services would solve multitenancy for the connector and make the library unusable anywhere else.

The connector's endpoint groups also need different authentication and token structures, and in `DISABLED` mode no security at all. The groups are:

- the administration API used by the UI (`/api/**`)
- the connector's own loopback calls (`/internal/**`)
- DSP protocol endpoints called by other connectors
- DCP protocol endpoints called by other dataspace participants
- public discovery documents

Today:

- **DCP routes are unprotected.** DCP protocol and discovery routes are not matched by any zone and fall through to the default chain, which permits everything.
- **DCP admin endpoints are exposed.** Some DCP administration endpoints are reachable without authentication: the token generator and, in the standalone issuer, approve, reject and key rotation.
- **`DISABLED` mode is all-or-nothing.** It is a single switch that turns everything into `permitAll` with no principal and no tenant, so code paths differ between secured and unsecured runs.
- **The library has no security contract.** DCP has no defined way to tell a host which of its endpoints are protocol endpoints (spec-defined authentication) and which are administration endpoints (host-defined authentication).

## Solution

**Participant context.** DCP gets its own, protocol-native notion of whose identity a request is served under. A **participant context** is one participant's DID, signing keys, advertised service endpoints, trusted issuers and issuer metadata configuration. DCP code never sees a "tenant".

- **Single-tenant deployments and other projects** get exactly one participant context, built from today's `dcp.*` properties, with no extra configuration or code.
- **The connector** supplies an adapter that maps each enabled tenant to a participant context with its own DID (`did:web:<host>:<tenantId>`), routes (`/<tenantId>/dcp/...`, `/<tenantId>/issuer/...`) and keys.
- **Data and work are partitioned.** Every DCP record and every unit of work, including asynchronous work, carries the participant context ID. Single-tenant is simply "multi-tenant with one participant", so moving between the two needs no data migration and no second code path.
- **Contract tests.** A published contract test kit lets any host verify that its participant context implementation honors the isolation rules.

**Zone security.** Endpoint security is organized into URL zones. Each zone has its own security filter chain, its own token type and authority model, and its own decoder or verifier.

- **The DCP library owns only DCP protocol authentication.** Self-Issued ID Token validation, the access token carried in its `token` claim and binding to the caller DID are all defined by the specification.
- **The host decides how DCP administration endpoints are protected.** The library documents the authorities they require. Standalone DCP applications get a fail-closed default.
- **Disabling security is per zone.** A synthetic principal and participant context are installed, so downstream code never branches on whether security is on. Startup refuses to disable security outside dev, test and TCK profiles unless the operator explicitly acknowledges it.

## User Stories

### Single-tenant and reuse

1. As an operator of a single-tenant connector, I want DCP to work with only the existing `dcp.*` properties, so that I do not have to learn tenant concepts I do not use.
2. As an operator of a single-tenant connector, I want my DID document served at the URL my configured `did:web` DID implies (`/.well-known/did.json` for a DID without a path, `/<path>/did.json` for a DID with one), so that my existing DID keeps resolving after the resolver starts honoring DID paths.
3. As an operator of a single-tenant connector, I want my existing DCP data to keep working after the upgrade, so that I do not lose credentials, requests or keys.
4. As a developer of an unrelated project, I want to add the DCP library as a dependency and get a working Holder, Verifier or Issuer, so that I can use DCP without adopting connector concepts.
5. As a developer of an unrelated project, I want to plug in my own participant context provider, so that DCP identities come from my own registry or configuration store.
6. As a developer of an unrelated project, I want to choose how the participant is resolved from a request (fixed or path-based), so that DCP fits my routing.
7. As a developer of an unrelated project, I want to configure the DCP base paths, so that DCP endpoints do not collide with my own routes.
8. As a developer of an unrelated project, I want the DCP library not to depend on the connector, `tools`, Keycloak or the connector's tenant model, so that I can adopt it independently.
9. As a developer of an unrelated project, I want a contract test kit for the participant context SPI, so that I can prove my implementation isolates participants correctly before going live.
10. As a DCP library maintainer, I want the library's own default participant context to pass the same contract test kit, so that the defaults are held to the same bar as host adapters.

### Multi-tenant connector

11. As a tenant admin, I want my tenant to have its own DID, `did:web:<host>:<tenantId>`, so that other participants identify my organization, not the connector operator.
12. As a remote participant, I want to resolve a tenant DID to `https://<host>/<tenantId>/did.json`, as the `did:web` method defines, so that standard resolvers work.
13. As a remote participant, I want the tenant DID document to advertise tenant-prefixed `CredentialService` and `IssuerService` endpoints, so that I reach the right tenant.
14. As a connector operator, I want the root `/.well-known/did.json` not served when the connector runs multi-tenant, so that an unscoped request never falls back to some tenant's identity.
15. As a connector operator, I want requests for an unknown or disabled tenant to be rejected with 404 before any DCP processing, so that disabled tenants cannot be used for DCP.
16. As a tenant admin, I want presentation queries against my Credential Service to return only my tenant's credentials of the authenticated holder, so that no other tenant or holder can read them.
17. As a tenant admin, I want credential deliveries and status updates to be accepted only for my tenant's records, so that one tenant cannot write into another's store.
18. As a tenant admin acting as Issuer, I want credential requests, request status, approval and rejection scoped to my tenant, so that other tenants cannot see or act on my requests.
19. As a requesting client, I want request status visible only to the client that created the request within that tenant, and a 404 for anyone else, so that request details and their existence are not disclosed.
20. As a tenant admin, I want my tenant's status lists allocated, updated and served per tenant, so that revocation in one tenant does not affect another.
21. As a tenant admin, I want my own signing key and key rotation, so that compromise or rotation in one tenant does not affect others.
22. As a remote Verifier, I want the previous public key to stay in the tenant DID document for a grace period after rotation, so that tokens signed just before rotation still verify.
23. As a tenant admin, I want my trusted issuers and issuer metadata configured per tenant, so that each organization applies its own trust policy.
24. As a security auditor, I want every DCP audit event to record the participant context it belongs to, including events persisted asynchronously, so that I can attribute every event to a tenant.
25. As a security auditor, I want replay protection, rate limiting and consent keyed by participant context, and replay protection shared across replicas, so that one tenant's traffic cannot exhaust or bypass another's controls and a token cannot be replayed against a second replica.
26. As a remote participant, I want the tenant to be derived from the route and bound to the authenticated token, never from a tenant ID in DCP messages or from scopes, so that DCP messages stay specification-conformant.
27. As a security auditor, I want a request to be rejected with 401 when the routed participant does not match the audience of the Self-Issued ID Token, so that a token issued for tenant A cannot be used against tenant B.
28. As a super admin, I want creating a tenant to make its DCP identity available through the adapter, so that tenant onboarding and DCP onboarding are not separate manual steps.
29. As a remote Verifier, I want to fetch a tenant's status list credential without authentication, so that I can check revocation as the credential's `credentialStatus` URL promises.

### Zone-separated security

30. As a UI user, I want the `/api/**` zone to accept only my UI access token (Keycloak user token or INTERNAL self-issued JWT), so that machine or protocol tokens cannot act as me.
31. As a connector operator, I want the `/internal/**` zone to keep accepting only the in-memory internal-call token from a loopback connection, so that D-ARC-005 still holds.
32. As a remote connector, I want DSP protocol endpoints to accept only connector credentials: the platform realm token when DCP authentication is off, and only a DCP Self-Issued ID Token when it is on (DCP replaces the configured mode, #410), so that UI tokens cannot call protocol endpoints.
33. As a remote participant, I want DCP protocol endpoints to authenticate me with a Self-Issued ID Token as the DCP specification defines, so that I can interoperate without a Keycloak account at the connector.
34. As a security auditor, I want DCP protocol and discovery routes matched by explicit zones instead of falling through to the permit-all default chain, so that no DCP route is unprotected by accident.
35. As a security auditor, I want unmatched paths denied by default except an explicit public allow-list (health, DID documents, status lists, OpenAPI when enabled, the error dispatch), so that new endpoints are secure unless deliberately published.
36. As a security auditor, I want UI tokens, platform connector tokens and internal-call tokens each accepted in exactly one zone, and Self-Issued ID Tokens accepted only in the DCP-authenticated zones, so that a token leaked from one zone cannot be replayed in another.
37. As a connector operator, I want DCP administration endpoints (credential requests, key rotation, DID document management, the token generator) to live in the `/api/**` zone with authorities documented by the library, so that I protect them with my existing roles.
38. As a standalone Issuer operator, I want the Issuer administration API protected by HTTP Basic credentials from configuration and denying every request when none are configured, so that a default installation cannot be used to mint trusted credentials.
39. As a security auditor, I want the DCP token generator endpoint off by default and, when a dev-only property turns it on, reachable only by an authenticated admin, so that production deployments never expose a token-minting endpoint.
40. As a developer, I want to disable authentication per zone, for example the `/api/**` zone locally while DCP protocol authentication stays on, so that I can debug the UI against real protocol behavior.
41. As a developer running with authentication disabled, I want a synthetic principal with configurable roles and participant context injected, so that the code paths match secured runs and nothing sees a null principal or tenant.
42. As a connector operator, I want the connector to refuse to start with any zone's security disabled outside dev, test or TCK profiles unless I set an explicit acknowledgement property, so that security cannot be disabled in production by accident.
43. As a connector operator, I want a loud startup warning listing each disabled zone, so that a misconfiguration is visible in the logs.
44. As a CI maintainer, I want the TCK and Newman environments to keep working with their current disabled or relaxed settings via the acknowledgement property, so that compliance runs are not broken.
45. As a developer integrating DCP in another project, I want the library to ship default security chains that apply only when my application defines none, so that I get secure defaults but can take full control.

## Implementation Decisions

### Architecture decision records

- An ADR is required before implementation (AGENTS.md). Two new architecture ADRs:
  1. **DCP participant context**. This answers #423.
  2. **Endpoint zone security**. This extends the zone model of D-ARC-005 and the realm rules of D-ARC-004 and D-ARC-006 and supersedes neither. It also records the #410 rule (DCP replaces the configured mode on the DSP protocol chain).
- #423 is answered as follows:
  - **Q1 (tenant identification):** one DID per tenant, `did:web:<host>:<tenantId>`, with the document at `/<tenantId>/did.json`.
  - **Q2 (routing):** `/<tenantId>/dcp/...` and `/<tenantId>/issuer/...`, matching the DSP routes.
  - **Q3 (root `/.well-known/did.json`):** not served in multi-tenant mode. In single-tenant mode it is served only when the configured DID has no path.

### Participant context SPI (in `dcp-common`)

- **Participant context**: an immutable value with:
  - participant context ID
  - DID
  - a signing key reference
  - advertised service entries
  - trusted issuers
  - issuer metadata configuration (when acting as Issuer)
  - a state (enabled or disabled)

  The ID is a plain string with no meaning to DCP. The connector uses the tenant ID.
- **Participant context provider**: looks up a participant context by ID and returns an optional result. A disabled participant is reported as disabled, never returned as usable. No caching is assumed by the contract. The connector adapter performs a fresh enabled-tenant check per request, consistent with D-ARC-004.
- **Participant context resolver**: determines the participant context ID from an inbound request. It ships two strategies:
  - **fixed** (always the single configured participant)
  - **path-based** (first path segment before the DCP base path)

  Host-based resolution is a later extension point.
- **Participant key provider**: for a participant it returns the active signing key, the set of public verification keys to publish (active key plus keys retired within the grace period) and a rotation operation. Signing, DID document generation and key rotation always go through it.
  - DCP signing keys are separate from TLS. They are never issued by or chained to the TLS root or intermediate CA, and never share a keystore entry with a TLS key. A self-signed certificate used only as a keystore container for the DCP key pair is acceptable.
- **Participant admin authorization**: a small hook through which the library asks the host whether the authenticated administration principal may act on a given participant. The default allows any authenticated admin to act on the single participant. The connector implementation allows a tenant admin only on its own tenant and a super admin on every tenant.
- **Defaults**: each SPI has a default implementation registered only when the host defines none. The default provider is a single-participant provider built from the existing `dcp.*` properties, with participant context ID `default`. The default resolver is the fixed resolver. Existing single-tenant configuration stays valid without changes.
- **Explicit context passing**: service methods take the participant context (or its ID) as an explicit parameter. A request-scoped holder may exist only as a convenience at the controller edge. Asynchronous work (audit persistence, credential delivery, issuance) receives the context explicitly and never relies on thread-local inheritance.
- **No tenant on the wire**: no DCP wire message carries a tenant ID, and DCP scopes are never used as tenant selectors.

### Routing, DID documents and resolution

- **Base paths.** DCP base paths (`dcp`, `issuer`) are configurable. With the path-based resolver the routes are `/<participantId>/<dcpBase>/...` and `/<participantId>/<issuerBase>/...`. With the fixed resolver, today's unprefixed routes stay.
- **DID document serving.** Each participant's document is served at the URL its DID implies under the `did:web` method: `/.well-known/did.json` for a DID without a path, `/<p1>/<p2>/did.json` for `did:web:<host>:<p1>:<p2>`. In single-participant mode this keeps the CI DIDs working (`did:web:connector-a%3A8080:connectora` → `/connectora/did.json`). In multi-participant mode every participant DID has a path, and the root `/.well-known/did.json` returns 404 and never falls back to any participant.
- **Advertised endpoints.** DID documents advertise the participant's own prefixed `CredentialService` and `IssuerService` endpoints.
- **Scheme.** Tenant DIDs and advertised endpoints are derived from the configured public host and use HTTPS. The dev-only `dcp.allow-insecure-http=true` property from #420 is the only way to advertise or accept `http`.
- **Resolution.** The DID resolver keeps the DID path when converting `did:web` to a URL, per the `did:web` method specification.
- **Rejection and status codes.**
  - Unknown or disabled participant → **404**, before any DCP processing or token validation.
  - Missing or invalid Self-Issued ID Token, or `aud` not equal to the routed participant's DID → **401**.
  - Missing access token on a Credential Service query → **401** (#407 Q2).
  - An authenticated administration principal acting on a participant it is not authorized for → **403**.
  - Credential request status queried by anyone but the client that created the request → **404**, so existence is not revealed (#409).
- **Binding.** For every authenticated DCP protocol request, the routed participant's DID must equal the Self-Issued ID Token `aud`, and the caller identity is the verified `sub`.
- **Reserved IDs.** Participant and tenant IDs must not collide with zone prefixes. `internal` is already reserved (D-ARC-005). Add `api`, `actuator`, `.well-known`, the configured DCP base paths (`dcp`, `issuer` by default) and the DSP protocol prefixes to the reserved set.

### Persistence

- **Required field.** Every DCP persisted document carries a required, non-null `participantContextId`:
  - Holder verifiable credentials and credential status records
  - Issuer credential requests and status lists
  - key metadata
  - consent records
  - replay-protection entries
  - DCP audit events
- **Scoped repositories.** Every repository read and write takes the participant context ID. No repository method queries across participants, and there is no unscoped `findAll`. Lookups by credential ID, request ID or issuer PID are always combined with the participant context ID. Presentation lookups are also combined with the authenticated holder DID.
- **Indexes.** Compound indexes lead with `participantContextId` and are created programmatically at startup, consistent with D-TEC-005.
- **Migration.** A one-time startup migration sets `participantContextId` to `default` on existing records that lack it. A host adapter that maps a single existing deployment to a named tenant documents how to re-key records.
- **Storage.** MongoDB stays the persistence technology (D-TEC-001). No storage abstraction is introduced in this spec.
- **Status list identifiers.** Status list IDs and URLs stay globally unambiguous, because the URLs are public. In multi-participant mode the URL is `/<participantId>/<issuerBase>/status/{listId}`.

### Connector adapter (in `connector`, wiring only)

- **Mapping.** The connector provides a participant context provider backed by its tenant registry and enabled-tenant validation. Participant context ID equals tenant ID. The DID is derived from the configured public host and the tenant ID. Keys, trusted issuers and issuer metadata come from tenant-scoped configuration.
- **Resolver.** The connector uses the path-based resolver. The adapter ships behind `dcp.participant.resolver=path` (#444), and the connector default stays `fixed` until #448 switches it, moves CI to tenant DIDs and re-keys `default` records to a configured tenant. An operator of a single-tenant connector who wants to keep the configured DID sets `fixed`.
- **Tenant context.** The adapter sets the connector's tenant context from the resolved and authenticated participant, so connector-side code (audit, dashboard) attributes DCP work to the tenant.
- **Admin authorization.** The adapter implements the participant admin authorization hook with the connector's existing tenant binding.
- **Module boundaries.** DCP modules do not depend on `connector` or `tools`, and the dependency points from the connector to DCP only.

### In-memory controls

- Replay protection, presentation rate limiting and consent are keyed by participant context ID plus the relevant DID.
- Replay protection is shared across replicas (#428): the `jti` cache moves to MongoDB with a TTL index, keyed by participant context ID and `jti`. Rate limiting stays per replica.

### Zone security (connector)

The zones are summarized below. Each zone is a separate security filter chain with its own authentication manager.

| Zone | Paths | Accepted credential | Authorization |
|---|---|---|---|
| Internal | `/internal/**` | In-memory HMAC internal-call token, loopback only (unchanged, D-ARC-005) | `ROLE_INTERNAL` |
| Admin / UI | `/api/**`, `/actuator/**` (except health), `/env` | Keycloak user token (`aud` `dsp-true-connector`, realm rules of D-ARC-004 and D-ARC-006) or INTERNAL self-issued JWT | Roles (`ADMIN`, `SUPER_ADMIN`, and DCP admin authorities mapped to roles) |
| DSP protocol | `/*/connector/**`, `/*/catalog/**`, `/*/negotiations/**`, `/*/transfers/**`, `/*/consumer/**` (and the TCK-forwarded unprefixed forms) | DCP authentication off: platform realm token with `CONNECTOR`. DCP authentication on: a DCP Self-Issued ID Token only; it replaces the configured mode (#410). | `ROLE_CONNECTOR` |
| DCP protocol (new) | `/<dcpBase>/**`, `/<issuerBase>/**` and their `/*/`-prefixed forms, except the public paths below | DCP Self-Issued ID Token (signature verified against the `sub` DID document, `aud` equal to the routed participant DID, `jti` replay check), plus the access token in its `token` claim where the specification requires one | Authenticated DCP caller bound to the routed participant. Endpoint-level rules follow the specification. |
| Public (new, explicit) | `/actuator/health/**`; DID documents (`/.well-known/did.json` and `/**/did.json` at the paths participant DIDs imply); status lists (`/<issuerBase>/status/**`, `/*/<issuerBase>/status/**`, #417); OpenAPI paths when enabled (D-TEC-010); the `/error` dispatch; every other path the zone-security ADR's inventory of the current default chain keeps | None | `permitAll` |
| Default | anything else | n/a | Deny (changed from `permitAll`) |

- **Separate verifiers.** Each zone has its own JWT decoder or verifier and audience check. UI tokens, platform connector tokens and internal-call tokens are each accepted in exactly one zone and rejected (401) in all others. Self-Issued ID Tokens are accepted only in the DCP-authenticated zones: the DCP protocol zone always, and the DSP protocol zone when DCP authentication is on.
- **Deny-by-default prerequisite.** Before the default chain changes to deny, the zone-security ADR lists everything the current default chain lets through (for example Spring's `/error`, which error responses depend on, and static resources if any). Each item either joins the public allow-list or is deliberately denied.
- **Token generator (#404).** The DCP token generator endpoint is off by default. A dev-only property turns it on, and even then it lives in the `/api/**` zone and requires an authenticated admin. It is never `permitAll`. CI and the DCP e2e tests set the property and authenticate the call.
- **Spec-defined DCP authentication.** DCP protocol endpoints are never authenticated with Keycloak tokens. The DCP protocol zone uses the library's DCP authentication component (built by #410), which replaces today's pass-through `DcpAuthenticationFilter` stub.

### DCP library security contract

- **Endpoint groups.** The library publishes three endpoint groups as data the host can read:
  - protocol endpoints (DCP-authenticated)
  - administration endpoints, with the authority each requires (for example a DCP issuer admin authority and a DCP holder admin authority)
  - public discovery endpoints (DID documents, status lists)
- **Protocol authentication component.** The library ships a filter or authentication provider for protocol endpoints. On success it produces an authenticated DCP caller principal carrying the verified caller DID, the participant context and, when present, the validated access token claims. Hosts add it to their protocol chain.
- **Host-owned admin protection.** The library does not decide how administration endpoints are protected in a host that defines its own security. The connector maps the documented DCP admin authorities to its roles in the `/api/**` zone.
- **Standalone defaults.** For hosts without their own security configuration, the library ships default chains registered only when none exist:
  - protocol endpoints use DCP authentication
  - public endpoints are `permitAll`
  - administration endpoints use HTTP Basic with users, password hashes and authorities from configuration (#405)

  When no administration users are configured, administration endpoints deny every request (fail closed). This is the default for the standalone Issuer application.

### Disabled mode

- **Configuration.** The global `application.auth.provider=DISABLED` stays for compatibility and disables every zone. Per-zone overrides allow disabling individual zones; the internal and DCP protocol zones stay enabled unless overridden explicitly.
- **Synthetic principal.** A disabled zone installs a synthetic principal instead of anonymous access:
  - **Admin zone:** configurable roles and a configurable default tenant.
  - **DSP protocol zone:** a synthetic connector principal.
  - **DCP protocol zone:** a configurable caller DID and the default or routed participant.
- **Startup guard.** Startup fails when any zone is disabled and neither a dev, test or TCK profile is active nor the explicit acknowledgement property is set. Every disabled zone is logged as a warning at startup. The TCK and Newman environments set the acknowledgement property.

## Testing Decisions

### What makes a good test

- Tests assert external behavior only: HTTP status, response body, persisted state visible through the public API, published DID documents, and contract outcomes of SPI calls.
- Tests do not assert which internal service or repository method was called, filter ordering, or bean wiring.
- Tests that prove isolation do so with at least two participants and assert both the positive case (own data visible) and the negative case (other participant's data invisible, or the request rejected).

### Seam 1: connector HTTP seam

MockMvc and Testcontainers integration tests against the running connector application context.

- **Zone token matrix.** A parameterized test runs every zone against every token type (UI token, platform connector token, internal-call token, DCP Self-Issued ID Token, none), with DCP authentication on and off. It asserts:
  - UI, platform and internal-call tokens are each accepted in exactly one zone
  - Self-Issued ID Tokens are accepted only in the DCP protocol zone, plus the DSP protocol zone when DCP authentication is on
  - a platform token is rejected on the DSP protocol zone when DCP authentication is on
- **Public and default paths.** The public allow-list is reachable without a token, and an unmatched path is denied.
- **Disabled mode.**
  - A disabled zone exposes the synthetic principal and tenant.
  - Per-zone overrides leave the other zones enforcing.
  - Startup fails without a dev, test or TCK profile or the acknowledgement property.
- **Token generator.** The endpoint is absent by default, and with the dev property set it returns 401 without credentials, 403 for a non-admin and a token for an admin.
- **Tenant DCP routes and identity.**
  - Tenant DID documents are served at `/<tenantId>/did.json` and advertise tenant-prefixed endpoints.
  - The root DID document returns 404 in multi-tenant mode.
  - Unknown and disabled tenants get 404.
  - A Self-Issued ID Token whose `aud` is another tenant's DID gets 401.
- **Cross-tenant isolation** for:
  - presentation queries (type scopes and ID scopes)
  - credential delivery
  - request status, approval and rejection
  - status lists
  - key rotation
  - audit attribution after asynchronous persistence
- **Prior art:**
  - `AbstractTenantIsolationIT` and its subclasses (`ProtocolTenantIsolationIT`, `CrossTenantIsolationIT`, `AuditAndDashboardTenantIsolationIT`)
  - `KeycloakTenantIsolationIT`, `KeycloakTenantRealmClaimsIT`, `ConnectorSecurityConfigTest`
  - the Testcontainers setup in the test containers starting guide

### Seam 2: standalone DCP host seam

DCP applications that run without the connector, configured only with today's `dcp.*` properties and the library's default beans.

- **Single-participant flows.** Holder, Verifier and Issuer work end to end with a single participant: issuance, presentation query and verification.
- **Single-mode discovery.** The DID document is served at the URL the configured DID implies (root `/.well-known/did.json` for a DID without a path).
- **Standalone admin defaults.** The Issuer administration API denies every request when no administration users are configured. With users configured it returns 401 without credentials, 403 for the wrong authority and 2xx for an admin.
- **Migration.** Existing records without `participantContextId` are migrated to `default` and stay readable.
- **Prior art:**
  - `DcpCompleteFlowTestE2E`, `DcpCredentialFlowTestE2E` and `DcpVerifierFlowTestE2E` with their test applications
  - the Issuer application
  - the existing Issuer Testcontainers base test

### Seam 3: participant context SPI contract test kit

- **New module.** A new DCP module, `dcp-spi-testkit`, publishes abstract JUnit 5 contract test classes. Hosts consume it as a test-scoped dependency and subclass each contract with their own implementation.
- **Contracts:**
  - **Provider:**
    - an unknown ID returns empty
    - a disabled participant is reported as disabled and never returned as usable
    - each context has exactly one DID and a resolvable key reference
    - DIDs are unique across participants
  - **Resolver:**
    - an unscoped request never resolves to an arbitrary participant in multi-participant mode
    - reserved IDs are never resolved
    - resolution is deterministic for the same request
  - **Key provider:**
    - the active key signs
    - after rotation the previous public key stays published for the grace period and is then removed
    - keys of different participants are never shared
- **Implementations under test.** The kit runs against the library's default single-participant implementations (inside the DCP modules) and the connector's tenant adapter (inside `connector`).
- **Relation to the TCKs.** The kit complements the DSP and DCP TCKs: it verifies the host-to-library seam in-process, while the TCKs verify wire-protocol interoperability.

### Regression gates

- `mvn clean verify` passes.
- The DSP TCK profile stays at 100% (AGENTS.md).
- Newman suites pass with the seed files.
- Any change to a seeded model updates all `initial_data*.json` files together.

## Out of Scope

- **Repository extraction.** Moving the DCP modules into a separate repository. This spec only makes them extractable.
- **Storage abstraction.** Abstracting storage beyond MongoDB, or replacing Spring Boot as the library's integration framework.
- **Further resolver strategies.** Host- or subdomain-based participant resolution, beyond keeping the resolver an extension point.
- **Machine-client zone.** A separate zone for backend machine clients using OAuth2 client credentials.
- **Shared rate-limit state.** Rate limiting stays per replica. Replay protection is shared (#428).
- **Keycloak and frontend.** Changes to Keycloak realm design, role names or the UI frontend.
- **Upstream questions.** The DCP specification clarification notes in the gap inventory.

## Further Notes

**Current state that motivates the zone work:**

- DCP protocol routes, the root DID document and the Issuer's protocol routes are matched by no zone today, so the permit-all default chain serves them. DCP authentication is enforced only where controllers validate tokens themselves.
- `/api/dev/token/**` is `permitAll` in the KEYCLOAK and INTERNAL admin chains.
- The standalone Issuer application has no Spring Security at all.

**Delivery.** Roadmap #397 holds the tickets, their order and their blocking links:

1. ADRs: #423 (participant context, D-ARC-007) and #440 (endpoint zone security, D-ARC-008).
2. Groundwork: participant context SPI #441, record partitioning and migration #442, contract test kit #443, connector tenant adapter #444. Single-tenant behavior does not change.
3. Zone security: the DCP authentication component on the DSP chain #410, the library security contract with standalone defaults #445, the connector DCP protocol and public zones with deny-by-default #446, per-zone disabled mode #447.
4. Multitenancy: #424–#428 (M1–M5) built on the SPI, then #448 (M6) switches the connector to tenant DCP identities.

The remaining conformance fixes (#400, #402, #403, #406–#409, #412, #415, #417–#421) are tenant-independent in logic. They can proceed in parallel, but once the SPI exists they take the participant context and the participant key provider as their identity and key lookup, so they are not reworked.
