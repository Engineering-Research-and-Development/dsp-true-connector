# D-ARC-008 — Endpoint zone security: one chain, one token type and one verifier per URL zone, deny by default

## Metadata
- Status: Accepted
- Date: 2026-11-20
- Owner: Security / authentication
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: security, dcp, keycloak, multi-tenant, jwt, spring-security
- Risk Level: High

## Context
`ConnectorSecurityConfig` builds four ordered filter chains: `/internal/**` (Order 0,
[D-ARC-005](D-ARC-005-internal-call-zone-with-in-memory-key.md)), the admin chain over `/api/**`,
`/actuator/**` and `/env` (Order 1), the DSP protocol chain over `/*/connector/**`, `/*/catalog/**`,
`/*/negotiations/**`, `/*/transfers/**`, `/*/consumer/**` and their unprefixed TCK forms (Order 2), and
`defaultFilterChain` (Order 3), which permits every request the other three do not match. The connector now
embeds the DCP Holder and Verifier, and DCP adds endpoint groups that no chain was designed for:

- DCP protocol routes (`/dcp/**` today, `/issuer/**` in the Issuer) and the DID documents fall through to
  the permit-all default chain. DCP authentication happens only where a controller validates the token
  itself, and `POST /dcp/offers` does not.
- `/api/dev/token/**`, which signs Self-Issued ID Tokens with the connector's DID key, is `permitAll` in the
  `KEYCLOAK` and `INTERNAL` admin chains.
- The standalone Issuer application has no Spring Security, so approve, reject and key rotation accept any
  caller.
- With `application.auth.dcp.enabled=true` the DSP chain installs `DcpAuthenticationFilter`, a pass-through
  stub, so every protocol request gets 401.
- `application.auth.provider=DISABLED` turns every chain into `permitAll` with no principal and no tenant,
  so code paths differ between secured and unsecured runs.
- The DCP library has no way to tell a host which of its endpoints are protocol endpoints, authenticated as
  the specification defines, and which are administration endpoints, authenticated as the host decides.

The gaps are listed in
[`DCP_SPEC_GAPS.md`](../../../dcp/doc/DCP_SPEC_GAPS.md#code-review-findings-dcp-v101-2026-11-17).

The constraints:

- DCP v1.0.1 §4.3 defines how DCP protocol calls are authenticated (Self-Issued ID Token, access token in
  its `token` claim). How a host separates its own endpoints into zones is connector-specific (§3.3 only
  requires transport security).
- The DCP modules must stay usable without the connector, `tools` or Keycloak (DCP participant context
  decision, D-ARC-007, #423).
- The trust rules of [D-ARC-004](D-ARC-004-trusted-issuers-from-config-and-tenant-registry.md) and
  [D-ARC-006](D-ARC-006-per-realm-role-allow-list-and-per-tenant-realm-credentials.md) and the internal-call
  zone of D-ARC-005 must hold unchanged.
- Changing the default chain to deny must not break a path that works today by accident: Spring's `/error`
  dispatch, discovery documents, the HTTP-PULL download and the OpenAPI documentation all reach the
  connector through it.
- The DSP TCK result must stay at 100%, and the TCK and Newman environments, which run with `DISABLED` or
  relaxed authentication, must keep working.

The accepted design is in the spec
[DCP participant context and zone-separated endpoint security](../../../dcp/doc/SPEC_DCP_PARTICIPANT_CONTEXT_AND_ZONE_SECURITY.md)
(#439). This ADR records its zone security part. The participant context part is D-ARC-007 (#423).

## Decision
Endpoint security is organised into URL zones. Each zone is its own `SecurityFilterChain` with its own
authentication manager, its own token type and its own decoder or verifier with its own audience check.
A path no zone matches is denied.

**Zones.**

| Zone | Paths | Accepted credential | Authorization |
|---|---|---|---|
| Internal | `/internal/**` | In-memory HMAC internal-call token, loopback only (unchanged, D-ARC-005) | `ROLE_INTERNAL` |
| Admin / UI | `/api/**`, `/actuator/**` (except health), `/env` | Keycloak user token (`aud` `dsp-true-connector`, realm rules of D-ARC-004 and D-ARC-006) or the `INTERNAL`-mode UI JWT from `/api/v1/auth/login` | Roles: `ADMIN`, `SUPER_ADMIN`, and the DCP admin authorities mapped to them |
| DSP protocol | `/*/connector/**`, `/*/catalog/**`, `/*/negotiations/**`, `/*/transfers/**`, `/*/consumer/**` and the unprefixed forms the TCK forwarding filter rewrites | DCP authentication off: the platform connector token (`platform` realm token in `KEYCLOAK` mode, the shared-secret protocol token in `INTERNAL` mode). DCP authentication on: a DCP Self-Issued ID Token only. | `ROLE_CONNECTOR` |
| DCP protocol (new) | `/<dcpBase>/**`, `/<issuerBase>/**` and their `/*/`-prefixed forms, except the public paths below | DCP Self-Issued ID Token: signature verified against the `sub` DID document, `aud` equal to the routed participant's DID, `jti` replay check; plus the access token in its `token` claim where the specification requires one | An authenticated DCP caller bound to the routed participant. Endpoint-level rules follow the specification. |
| Public (new, explicit) | The allow-list of the inventory below: `/actuator/health/**`, DID documents, status lists, OpenAPI when enabled, `/error`, `/.well-known/dspace-version`, `/public/tenant`, `/artifacts/*` | None | `permitAll` |
| Default | Anything else | n/a | Deny (changed from `permitAll`) |

`<dcpBase>` and `<issuerBase>` are the configurable DCP base paths, `dcp` and `issuer` by default (D-ARC-007).
The internal zone keeps Order 0. The public zone comes next, before the admin, DSP and DCP protocol zones,
so its exact entries win over their broader matchers: `/actuator/health/**` over `/actuator/**`, and the
status list path and a DID document such as the standalone Issuer's `/issuer/did.json` over `/issuer/**`.
The default chain stays last.

**Token acceptance.** UI tokens, platform connector tokens and internal-call tokens are each accepted in
exactly one zone and rejected with 401 in every other zone. Self-Issued ID Tokens are accepted only in the
DCP-authenticated zones: the DCP protocol zone always, and the DSP protocol zone when
`application.auth.dcp.enabled=true`. The public zone evaluates no credential.

| Token | Internal | Admin / UI | DSP, DCP auth off | DSP, DCP auth on | DCP protocol |
|---|---|---|---|---|---|
| Internal-call token (D-ARC-005) | accepted | 401 | 401 | 401 | 401 |
| UI token (Keycloak tenant or `connector-admin` user token, `INTERNAL` UI JWT) | 401 | accepted | 401 | 401 | 401 |
| Platform connector token (`platform` realm token, `INTERNAL` protocol token) | 401 | 401 | accepted | 401 | 401 |
| DCP Self-Issued ID Token | 401 | 401 | 401 | accepted | accepted |

Accepted means the token authenticates; the zone's authorization rules still apply (403 for a missing role).
The DCP protocol zone always uses DCP authentication. `application.auth.dcp.enabled` only selects the
credential of the DSP protocol zone.

**DCP replaces the configured mode on the DSP protocol chain (#410).** When `application.auth.dcp.enabled=true`,
the DSP protocol chain authenticates with the DCP authentication component only, and a platform connector
token gets 401 there. The `aud` of the Self-Issued ID Token must equal the DID of the routed participant.
The admin zone always uses the configured provider, never DCP. `DISABLED` together with
`application.auth.dcp.enabled=true` stays a startup error.

**Token generator (#404).** The DCP token generator (`/api/dev/token/**`) is off by default: the endpoint is
registered only when the dev-only property `dcp.dev.token-generator.enabled=true` is set. When it is on, it
lives in the `/api/**` zone and requires an authenticated admin (401 without credentials, 403 for a
non-admin). It is never `permitAll`. CI and the DCP e2e tests set the property and authenticate the call.

**DCP library security contract.** The library owns DCP protocol authentication only; the host decides how
DCP administration endpoints are protected.

- **Endpoint groups as data.** The library publishes three groups the host can read: protocol endpoints
  (DCP-authenticated); administration endpoints, each with the authority it requires (`DCP_ISSUER_ADMIN`,
  `DCP_HOLDER_ADMIN`); and public discovery endpoints (DID documents, status lists). The groups are computed
  from the configured base paths.
- **Protocol authentication component (#410).** The library ships a filter and authentication provider for
  protocol endpoints, in `dcp-common`, with Spring Security as an optional dependency. On success it
  produces an authenticated DCP caller principal carrying the verified caller DID, the participant context
  and, when present, the validated access token claims. The connector adds it to its DSP protocol chain
  (when DCP authentication is on) and to its DCP protocol chain. It replaces the pass-through
  `DcpAuthenticationFilter`. DCP protocol endpoints are never authenticated with Keycloak tokens.
- **Host-owned admin protection.** The library does not decide how a host with its own security protects
  the administration endpoints. The connector keeps them in the `/api/**` zone and maps the documented DCP
  admin authorities to `ADMIN` and `SUPER_ADMIN`. Whether a given admin may act on a given participant is
  answered by the participant admin authorization hook of D-ARC-007 (403 otherwise).
- **Standalone default chains (#405, #445).** For a host that defines no `SecurityFilterChain`, the library
  registers default chains: protocol endpoints use DCP authentication, public endpoints are `permitAll`,
  and administration endpoints use HTTP Basic with users, password hashes (`DelegatingPasswordEncoder`
  format) and authorities from configuration (`dcp.admin.users[n].username`, `.password`, `.authorities`).
  When no admin user is configured, every administration request is denied (fail closed). This is the
  default of the standalone Issuer application. A host with its own chain, such as the connector, gets no
  library chain.

**Disabled mode.** Security can be disabled per zone, and a disabled zone installs a synthetic principal
instead of anonymous access, so downstream code never sees a null principal or tenant.

| Property | Default | Meaning |
|---|---|---|
| `application.auth.zone.admin.enabled` | `true` | Admin / UI zone security on or off |
| `application.auth.zone.dsp.enabled` | `true` | DSP protocol zone security on or off |
| `application.auth.zone.dcp.enabled` | `true` | DCP protocol zone security on or off |
| `application.auth.zone.internal.enabled` | `true` | Internal zone security on or off |
| `application.auth.disabled-zones-acknowledged` | `false` | The operator's acknowledgement that lets a zone be disabled outside the dev, test and TCK profiles |
| `application.auth.disabled.roles` | — | Roles of the synthetic admin principal |
| `application.auth.disabled.tenant-id` | — | Tenant of the synthetic admin principal |
| `application.auth.disabled.dcp-caller-did` | — | Caller DID of the synthetic DCP principal |

- `application.auth.provider=DISABLED` stays for compatibility and turns every zone switch off. A zone
  switch that is set explicitly wins over that default. Disabling one zone never disables another: the
  internal and DCP protocol zones stay enabled unless their own switch is set to `false`.
- The synthetic principals: in the admin zone, the configured roles and default tenant; in the DSP
  protocol zone, a synthetic connector principal with `ROLE_CONNECTOR`; in the DCP protocol zone, the
  configured caller DID and the default or routed participant. The internal zone gets no synthetic
  principal; when disabled it permits all requests, as under `DISABLED` today.
- Startup fails when any zone is disabled and neither a dev, test or TCK profile is active nor
  `application.auth.disabled-zones-acknowledged=true` is set. Every disabled zone is logged as a warning at
  startup. The TCK and Newman environments set the acknowledgement property.
- `application.auth.zone.dcp.enabled` is unrelated to the existing `application.auth.dcp.enabled`: the
  first switches the security of the DCP protocol zone, the second selects the credential of the DSP
  protocol zone.

**Default-chain inventory.** Before the default chain denies (#446), everything it serves today gets a
disposition. Each path either joins the public allow-list, moves into a named zone, or is deliberately
denied.

| Path | Served by (today) | Disposition | Reason |
|---|---|---|---|
| `/error` | Spring Boot error dispatch (`BasicErrorController`) | Public | Spring Security also runs on the `ERROR` dispatch. Denying `/error` would turn every 400, 404 and 500 of every zone into 401. |
| `/.well-known/dspace-version` | `DSpaceVersionController` | Public | DSP version discovery: peers and the TCK read it before they hold a token. |
| `/public/tenant` | `PublicTenantInfoController` | Public | The UI sign-in page lists the enabled tenants before a user has a token. |
| `/artifacts/{transactionId}` | `RestArtifactController`, guarded by `EndpointAvailableFilter` | Public | HTTP-PULL data address handed to the consumer. Access is gated by a started transfer process and a valid agreement, not by a token. Behaviour unchanged. |
| `/.well-known/did.json` | `GenericDidDocumentController` (`dcp-common`) | Public | DID document of a DID without a path. Served only in single-participant mode for such a DID; 404 in multi-participant mode (D-ARC-007). |
| `/*/did.json` (legacy convenience endpoint, `dcp.enable-legacy-endpoints`) | `GenericDidDocumentController`, registered at startup | Public, as `/**/did.json` | For the one-segment DIDs in use (`did:web:<host>:connectora` → `/connectora/did.json`) this is the `did:web` location. Once DID documents are served at the path their DID implies (#424), the public matcher `/**/did.json` covers every participant DID; the controller answers 404 for any path that is not an enabled participant's DID path. |
| `/<path>/.well-known/did.json` (`dcp.auto-register-path-endpoints`) | `GenericDidDocumentController`, registered at startup | Deliberately denied | Not a `did:web` location. Nothing fetches it: the resolver fetches the root document today and `/<path>/did.json` after #424. |
| `/dcp/presentations/query`, `/dcp/credentials`, `/dcp/offers` | `DcpController` (`dcp-holder`) | DCP protocol zone | Credential Service endpoints, authenticated with a Self-Issued ID Token. `/<participantId>/dcp/**` with the `path` resolver. |
| `/issuer/metadata`, `/issuer/credentials`, `/issuer/requests/{id}` | `IssuerController` (`dcp-issuer`; not on the connector's classpath today, served by the standalone Issuer without security) | DCP protocol zone | Issuer Service endpoints. In the standalone Issuer, the library's default protocol chain. `/<participantId>/issuer/**` with the `path` resolver. |
| `/<issuerBase>/status/{listId}`, `/*/<issuerBase>/status/{listId}` | Status list endpoint of #417 (not yet implemented) | Public | A Verifier fetches a credential's `credentialStatus` URL without authentication. |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | springdoc, only when `springdoc.api-docs.enabled` and `springdoc.swagger-ui.enabled` are `true` | Public when enabled; denied otherwise | Documentation only; the operations they describe keep their zone's authentication (D-TEC-010). |
| Static resources (`classpath:/static/`, `/public/`, `/resources/`, `/META-INF/resources/`) | Spring Boot's default resource handler | Deliberately denied | The connector ships no static content (the UI is a separate application). The Swagger UI webjar is covered by the OpenAPI entry. New static content needs an explicit allow-list entry. |
| Any other path | No handler today (404 through `/error`) | Denied | The point of deny-by-default: a new endpoint is secured unless it is deliberately published. |

Paths that other chains permit without a token today:

| Path | Today | Disposition |
|---|---|---|
| `/actuator/health`, `/actuator/health/**` | `permitAll` inside the admin chain | Public zone |
| `/api/v1/auth/**` (`INTERNAL` mode login, refresh, logout) | `permitAll` inside the admin chain | Stays in the admin / UI zone as its only unauthenticated entry: these calls obtain the UI token |
| `/api/dev/token/**` | `permitAll` inside the `KEYCLOAK` and `INTERNAL` admin chains | Admin / UI zone, authenticated admin, off by default (#404) |
| Every path, `DISABLED` mode | `permitAll` on every chain | Per-zone switches and synthetic principals (disabled mode above) |

## Alternatives Considered
- **One chain that sniffs the token type and dispatches to the matching verifier** → rejected because every
  token type would then be accepted on every route, and a token leaked from one zone could be replayed
  in another. Authorization would rest on role checks alone, which is the generic role mapping that
  D-ARC-006 removed. One chain per zone keeps an unknown token type at 401 by construction.
- **Accept either credential on the DSP protocol chain when DCP authentication is on** (#410 Q1, option
  "accept either") → rejected because the chain would trust two credential types at once, an operator who
  enables DCP would still accept platform tokens, and which verifier applies would depend on what the caller
  chose to send.
- **Enable DCP authentication per tenant** (#410 Q1, option "per tenant") → rejected because the DSP
  protocol chain would need the tenant before authentication, and the credential type of a route would
  depend on mutable tenant data. One process-wide switch keeps one verifier per chain.
- **OAuth2 resource server against Keycloak for the Issuer administration API** (#405 Q1) → rejected
  because the DCP library and the standalone Issuer may not depend on Keycloak, and a default installation
  needs a fail-closed protection that works without an identity provider. Hosts that already have one,
  such as the connector, protect the administration endpoints with it (host-owned admin protection).
- **Keep the permit-all default chain and add matchers for the DCP routes only** → rejected because every
  new endpoint would be public until someone remembers to add a matcher, which is how the DCP routes and the
  token generator ended up open.

## Rationale
A zone is the unit at which the caller, the credential and the authority model change: UI users, the
connector's own loopback calls, peer connectors and DCP participants never share a credential. Giving each
zone its own chain, authentication manager and verifier makes a token's reach a property of the
configuration rather than of the token: a decoder that does not know a token type cannot accept it. This
is the reasoning D-ARC-005 applied to the internal zone, extended to every zone. It keeps the issuer rules
of D-ARC-004 and the per-realm role rules of D-ARC-006 inside the admin and DSP zones where they already
apply.

DCP protocol authentication is defined by the specification, so it belongs to the library and is the same in
every host. Administration endpoints are local APIs whose protection depends on the host's identity
infrastructure, so the library only states the authorities they need. Publishing the endpoint groups as data
lets a host build its chains from the library instead of copying path lists. The standalone defaults give a
host without its own security a fail-closed configuration that needs no identity provider.

Deny-by-default turns a forgotten matcher into a visible failure instead of an open endpoint. The inventory
makes the switch safe: every path the default chain serves today is listed with a reason, so #446 can deny
the rest without breaking the error dispatch, discovery, HTTP-PULL or the documentation.

Per-zone disabled mode with synthetic principals keeps one code path: controllers and services always see a
principal and a tenant or participant, whether security is on or off. The startup guard and the warnings
make a disabled zone a deliberate, visible choice outside development, and the acknowledgement property
keeps the TCK and Newman runs working.

## Consequences

### Positive
- No DCP route, DID document or future endpoint is reachable without a deliberate decision.
- A token leaked from one zone gets 401 everywhere else.
- The token generator and the Issuer administration API stop being usable by anonymous callers.
- The DCP library is usable by any host: protocol authentication ships with it, and the host keeps control
  of administration security.
- Developers can disable one zone, for example the admin zone, while protocol authentication keeps running.

### Negative
- More chains to build and test; the zone token matrix (every zone × every token type, DCP authentication on
  and off) becomes a required integration test (#446).
- A token on the wrong zone now gets 401 where some got 403 before: the platform token and the `INTERNAL`
  protocol token on `/api/**`, and an `INTERNAL` UI JWT on the protocol routes. In `INTERNAL` mode the UI
  JWT and the protocol token are signed with the same secret, so their verifiers must tell them apart by
  their claims.
- With DCP authentication on, peers that call the DSP protocol routes with a platform token get 401 and
  must switch to DCP.
- Unmatched paths answer 401 or 403 instead of 404, so a client probing for an endpoint no longer learns
  that it does not exist, and diagnosing a wrong path is harder.
- New static content, discovery documents or callback endpoints need an explicit allow-list entry.
- Operators who run with `DISABLED` outside the dev, test and TCK profiles must set
  `application.auth.disabled-zones-acknowledged=true`, or startup fails.

### Risks
- Denying the default chain breaks a path the inventory missed. Mitigation: the inventory covers every
  controller mapping in `connector`, `tools`, `catalog`, `negotiation`, `data-transfer` and `dcp/*`, the
  error dispatch, springdoc and static resources; #446 tests every public path without a token and an
  unmatched path for denial, and the TCK and Newman suites run against the change.
- The public zone's broad DID matcher (`/**/did.json`) exposes more than DID documents. Mitigation: only
  `GenericDidDocumentController` serves such paths, and it answers 404 for anything but an enabled
  participant's DID path; reserved participant IDs (D-ARC-007) keep zone prefixes out of tenant paths.
- `/artifacts/{transactionId}` stays public and is guarded by transfer state, not by a token. Mitigation:
  behaviour is unchanged by this decision; access needs a started transfer and a valid agreement.
- A disabled zone reaches production. Mitigation: startup fails without a dev, test or TCK profile or the
  acknowledgement property, and every disabled zone is logged as a warning.
- Zone order changes and a broader matcher captures a public path. Mitigation: the public zone is placed
  before the admin and protocol zones, and the zone token matrix and public-path tests fail on such a change.

## Related
- Decisions: [D-ARC-005](D-ARC-005-internal-call-zone-with-in-memory-key.md) (internal zone, extended), [D-ARC-004](D-ARC-004-trusted-issuers-from-config-and-tenant-registry.md) (trusted issuers, extended), [D-ARC-006](D-ARC-006-per-realm-role-allow-list-and-per-tenant-realm-credentials.md) (per-realm role rules, extended), [D-ARC-003](D-ARC-003-no-login-proxy-in-keycloak-mode.md), [D-TEC-010](../technical/D-TEC-010-springdoc-openapi-documentation.md) (OpenAPI paths). D-ARC-007 (DCP participant context, #423) is the companion decision. None is superseded.
- Docs: [SPEC_DCP_PARTICIPANT_CONTEXT_AND_ZONE_SECURITY.md](../../../dcp/doc/SPEC_DCP_PARTICIPANT_CONTEXT_AND_ZONE_SECURITY.md), [doc/security.md](../../security.md), [DCP_SPEC_GAPS.md](../../../dcp/doc/DCP_SPEC_GAPS.md#code-review-findings-dcp-v101-2026-11-17)
- Tickets: #440 (this decision), #439 (spec), #397 (roadmap), #404, #405, #410, #417, #424, #445, #446, #447
