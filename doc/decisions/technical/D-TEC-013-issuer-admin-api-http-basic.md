# D-TEC-013 — HTTP Basic with configured users for the Issuer administration API

## Metadata
- Status: Accepted
- Date: 2026-11-20
- Owner: TRUE Connector team
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: dcp, issuer, security, spring-security, authentication
- Risk Level: Medium

## Context

The standalone DCP Issuer (`dcp/dcp-issuer`) exposes a local administration API under `/api/issuer`:
approve and reject a credential request (`IssuerAPIController`) and rotate the signing key
(`IssuerKeyRotationAPIController`). The module had no Spring Security dependency and no filter chain,
so any caller could approve its own request with arbitrary `customClaims` and receive a trusted
credential, reject other holders' requests, or rotate the issuer key (`dcp/doc/DCP_SPEC_GAPS.md`, code
review finding P0 "Issuer administration API unauthenticated").

Constraints:

- The administration API is not a DCP endpoint; the DCP specification says nothing about it. The DCP
  protocol endpoints (`/issuer/metadata`, `/issuer/credentials`, `/issuer/requests/{id}`) authenticate the
  caller's Self-Issued ID Token in the controllers and must keep doing so.
- The Issuer runs standalone, often without an identity provider, and the DCP modules may not depend on
  Keycloak or on `connector`.
- A default installation must not be open, and the shipped configuration must not contain a default
  password.
- The endpoint zone security design (D-ARC-008, spec #439) assigns the protection of administration
  endpoints to the host. For a host without its own security it specifies HTTP Basic with users from
  `dcp.admin.users[n]` and fail-closed behaviour; #445 later moves these chains into the DCP library and
  keeps the property names.

## Decision

`dcp-issuer` adds `spring-boot-starter-security` and `IssuerSecurityConfig` with two stateless filter
chains:

1. `/api/issuer/**` (by pattern, so later admin endpoints are covered): HTTP Basic against an in-memory
   user store built from `dcp.admin.users[n].username`, `.password` (a `DelegatingPasswordEncoder` value
   such as `{bcrypt}…`) and `.authorities`; every request needs the authority `DCP_ISSUER_ADMIN`.
   Missing or wrong credentials get 401, a user without the authority gets 403.
2. Everything else: `/issuer/**`, `/.well-known/**`, `GET` of any `…/did.json`, `/actuator/health` and
   `/error` are permitted at the chain level, every other path is denied.

No user is configured by default; then every administration request gets 401 (fail closed). Invalid user
entries (blank username or password, duplicate username, unknown password encoding) stop the application
at startup.

## Alternatives Considered
- **OAuth2 resource server against Keycloak, as in the connector's `KEYCLOAK` mode** (#405 Q1) → rejected
  because the standalone Issuer and the DCP library may not depend on Keycloak, and a default installation
  would need an identity provider before it could be protected at all.
- **No in-app authentication; bind the administration endpoints to a separate internal-only port**
  (#405 Q1) → rejected because protection would depend entirely on network configuration that the
  application cannot check, so a misconfigured deployment would be open.
- **A static API key header** → rejected because it needs custom filter code for what Spring Security
  already provides with HTTP Basic, and a single shared key has no per-user identity or authorities.
- **A Spring Boot default user (`spring.security.user.*`)** → rejected because Boot generates and logs a
  random password when none is set, allows only one user, and its property names differ from the
  `dcp.admin.users[n]` contract that #445 keeps.

## Rationale

HTTP Basic with configured users is the smallest mechanism that works without an identity provider and is
still standard: every HTTP client and Newman support it, and Spring Security implements the challenge, the
password check (with timing protection for unknown users) and the 401/403 split. `DelegatingPasswordEncoder`
lets operators store bcrypt, argon2, pbkdf2 or scrypt hashes, so no plain password needs to be in a
property file. Defining the `UserDetailsService` bean stops Spring Boot from creating its default user with
a logged password. Fail closed when no user is configured follows D-ARC-008's deny-by-default principle
and means a forgotten configuration is visible (401 and a startup WARN) instead of an open endpoint.
Matching `/api/issuer/**` by pattern protects endpoints added later, such as the revocation endpoint of
#417, without editing the chain. The public chain leaves the DCP endpoints to their existing token checks,
so the DCP protocol behaviour does not change. Using the property names of D-ARC-008 lets #445 move the
chains into the library without a configuration change.

## Consequences

### Positive
- Approve, reject and key rotation require an authenticated administrator; the P0 finding is closed.
- A fresh installation is closed for administration until an operator configures a user.
- The DCP protocol endpoints, DID documents and health check behave as before.
- The configuration contract matches the standalone default chains planned in #445.

### Negative
- Users and authorities live in configuration: adding or removing an administrator needs a restart.
- HTTP Basic sends the credentials on every request, so it is only safe over TLS.
- CI, the DCP e2e tests and every script calling the administration API must send credentials.
- Paths that were reachable by accident (for example `/actuator/info`) are now denied.

### Risks
- **Weak or `{noop}` passwords in production.** Mitigation: `{noop}` logs a WARN at startup, and the
  deployment guide asks for a bcrypt (or stronger) hash from a secret store.
- **Brute force against HTTP Basic.** The Issuer has no lockout or rate limit. Mitigation: bcrypt makes
  each guess expensive; operators should put the administration API behind TLS and network restrictions.
- **Multitenancy.** One administrator may act on every request. Per-participant authorization is the
  participant admin authorization hook of spec #439 and is tracked in #426.

## Related
- Decisions: D-ARC-008 Endpoint zone security (PR #451, records the zone design and the library security
  contract this decision implements for the standalone Issuer)
- Docs: [doc/security.md](../../security.md#dcp-issuer-administration-api-standalone-issuer),
  [dcp/dcp-issuer/DEPLOYMENT.md](../../../dcp/dcp-issuer/DEPLOYMENT.md#administration-api-users),
  [dcp/dcp-issuer/API.md](../../../dcp/dcp-issuer/API.md#authentication),
  [dcp/doc/DCP_SPEC_GAPS.md](../../../dcp/doc/DCP_SPEC_GAPS.md),
  [dcp/doc/SPEC_DCP_PARTICIPANT_CONTEXT_AND_ZONE_SECURITY.md](../../../dcp/doc/SPEC_DCP_PARTICIPANT_CONTEXT_AND_ZONE_SECURITY.md)
- Tickets: [#405](https://github.com/Engineering-Research-and-Development/dsp-true-connector/issues/405),
  [#445](https://github.com/Engineering-Research-and-Development/dsp-true-connector/issues/445),
  [#426](https://github.com/Engineering-Research-and-Development/dsp-true-connector/issues/426),
  [#397](https://github.com/Engineering-Research-and-Development/dsp-true-connector/issues/397)
