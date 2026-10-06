# DCP v1.0 Implementation Specification (DRAFT for verification)

Source: `dcp/doc/DCP_SPEC_GAPS.md`, `.github/skills/dcp-protocol/SKILL.md`, and a read-only analysis of the `dcp` modules and connector security config.
Status: **Draft – no code changed.** This document is to be verified, then turned into an implementation plan.

## 0. Decisions already taken

| # | Decision |
|---|---|
| D1 | Phasing: **Phase A** = protocol P0/P1 gaps; **Phase B** = multitenancy. |
| D2 | Presentation Definition: **implement Presentation Exchange 2.1.1 execution** (not 501). |
| D3 | Transport follows the existing `server.ssl.enabled` property (no new property): if the connector is deployed over http, DCP (DID resolution, endpoint discovery, delivery) also uses http; if https, DCP uses https. Production runs with `server.ssl.enabled=true`, dev may use either. Endpoint validation (A11) therefore enforces scheme == local scheme policy: when `server.ssl.enabled=true`, any non-https remote endpoint/DID URL is rejected; when false, http and https are both accepted. |
| D4 | Tenant DID = `Tenant.participantId` (`did:web:host:tenantId`). |
| D5 | Unqualified scopes (e.g. `MembershipCredential`, a test credential): accepted **only** when `dcp.scope.legacy-unqualified=true` (ON in dev/test profiles, OFF by default/prod). Spec-conformant form is `<alias>:<discriminator>`. Confirmed. |
| D7 | Gap-document clarification items 3–7 are resolved with the JSON schema as the leading source; the 2 open questions are Q1 (issuer discovery direction) and Q2 (revocation), see §4. Confirmed. |
| D8 | Status lookup by non-owner returns **404**. `GET /issuer/metadata` is **public**. Presentation query with no matching/authorized scope returns an **empty** result in all cases (mandatory scope not authorized, or no scope requested). |
| D6 | The JWK `d` leak is already fixed (`KeyService.convertPublicKeyToJWK` emits only kty/use/crv/kid/x/y). No rotation runbook needed; add a regression test only. |

## 1. Corrections and new findings versus the gap document

| ID | Finding | Evidence |
|---|---|---|
| N1 | Gap doc multitenancy row says "authenticated Holder DID". In `POST /presentations/query` the token subject is the **Verifier (requester)**; the Holder is the connector's own DID (`DidDocumentConfig.getDid()`). | `DcpController.queryPresentations`, `HolderService.createPresentation(query, holderDid, claims)` is misnamed. |
| N2 | **Authorization hole:** `PresentationService` reads `scope` from the outer Self-Issued ID Token; the nested `token` claim is never parsed. The outer token has no scope, so `authorizedScopes` is empty = "no restriction", and `credentialRepository.findAll()` is used if the query scope is empty. Any authenticated requester can read all credentials. | `PresentationService.extractScopesFromClaims`. |
| N3 | `POST /dcp/offers` is unauthenticated (authorization commented out) and takes a raw `Map`. | `DcpController.receiveOffer`. |
| N4 | `POST /dcp/credentials` doesn't check token `aud` = this holder, nor correlation of `holderPid`/`issuerPid` with a pending request. | `DcpController.receiveCredentials`. |
| N5 | `PresentationValidationServiceImpl` has no production callers and cannot handle JWT VPs. The real path is `VerifierService`, which lacks: revocation/status check, VP `exp`/`nbf`, `authentication` relationship for VP proof, scope/definition satisfaction, strict holder binding, skipping non-JWT items silently. | grep of callers; `VerifierService.validatePresentationResponse`. |
| N6 | Rejection message uses `holderPid = holderDid` and a placeholder credential (`"No type"`, `"JWT"`) to satisfy model constraints. | `CredentialDeliveryService.rejectCredentialRequest`. |
| N7 | `CredentialMessage` requires `holderPid` and non-empty `credentials`; DCP makes them optional (and `credentials` empty for rejection). | `CredentialMessage` builder. |
| N8 | Issuer metadata credential ids are random UUIDs if not configured → unstable across restarts; per-credential build errors swallowed; issuer DID defaults to `did:web:issuer-url`. | `CredentialMetadataService.buildIssuerMetadata`. |
| N9 | `GET /issuer/metadata` requires a Bearer token though commented as public discovery. | `IssuerController.getMetadata`. |
| N10 | No scheme validation on discovered `CredentialService` endpoints except in `DcpVerifierClient`. | `CredentialDeliveryService.resolveCredentialServiceEndpoint`, `VerifierService.resolveHolderCredentialService`. |
| N11 | `DidDocument` drops relationship arrays on deserialization (`ignoreUnknown=true`) and does not emit them. `HttpDidResolverService` DID path is dropped (always `/.well-known/did.json`), so `did:web:host:tenantId` cannot resolve to `/tenantId/did.json`. `convertDidToUrl` special-cases a path segment named `holder`. | resolver, converter. |
| N12 | Error bodies are plain text, not JSON. `Location` header is relative. | `IssuerController`. |
| N13 | See A14 (S1–S9): the connector DCP wiring is incomplete. `DcpAuthenticationFilter` is a stub, `DcpVerifierAuthenticationProvider` is not wired. Also: fall back silently to the next provider on any failure; the filter chain only protects `/connector/**`, `/catalog/**`, `/negotiations/**`, `/transfers/**`. `/dcp/**` and `/issuer/**` are in the default chain (`permitAll`), so each controller must authenticate itself. | `ConnectorSecurityConfig`. |
| N14 | The `dcp-*` modules do not depend on `tools` tenant classes; no `tenantId` on `VerifiableCredential`, `CredentialRequest`, `CredentialStatusRecord`, `StatusList`, `KeyMetadata`, `DcpAuditEvent`. | model review. |

## 2. Phase A – Protocol gaps (ordered by dependency)

Every item lists: current behaviour → requirement → target design → acceptance criteria. Common to all: Javadoc on public methods, no FQNs, builder/validation pattern for models, JUnit 5 + Mockito, CHANGELOG entry, TCK profile unaffected (DCP is separate from DSP, but run `mvn -pl connector -Ptck verify` as regression).

### A1 (P0) Self-Issued ID Token signature verification
- **Current:** `SelfIssuedIdTokenService.validateToken` resolves the JWK but the verify block is commented out. Forged tokens are accepted.
- **Requirement (DCP §5.x):** verify signature with key resolved from the `iss` DID document, selected by `kid`; `iss == sub`; `aud` = own DID; `exp`/`nbf`/`iat` with skew; `jti` replay; `token` claim present where required.
- **Design:**
  1. Resolve key via A2 (`resolveVerificationKey(did, kid, "authentication")`).
  2. Verify with Nimbus `ECDSAVerifier` (ES256 only; reject `alg=none`/others; reject if `kid` DID ≠ `iss`).
  3. Verify **before** consuming `jti` (don't poison the replay cache with forged tokens).
  4. Throw typed `TokenValidationException` (reason enum) mapped to 401; audit event with reason code.
- **Acceptance:** valid token passes; wrong key, tampered payload, alg none, kid of other DID, expired, replayed, wrong aud, iss≠sub all rejected; test matrix in `SelfIssuedIdTokenServiceTest`. Connector `DcpVerifierAuthenticationProvider` integration test proves a forged token no longer authenticates.
- **Risk:** every flow that currently works with unsigned/foreign-key tokens breaks; fix test fixtures and CI Newman e2e (`ci/docker`) together.

### A2 (P0) DID resolution, key selection, relationships
- **Current:** first verification method returned; `kid` null → null; relationship checks commented out; only did:web; DID path dropped.
- **Design:**
  - Extend `DidDocument` with `authentication`, `assertionMethod`, `capabilityInvocation`, `keyAgreement` (list of string refs or embedded methods, via a small `VerificationRelationshipRef` type) – keep `ignoreUnknown`.
  - `DidDocumentService` emits `authentication` and `assertionMethod` referencing the single key `did#kid` (kid = JWK thumbprint-based, already stable).
  - `HttpDidResolverService.resolveVerificationKey(did, kid, relationship)`:
    - `kid` present → must match a method id (`did#frag`), method must be listed in the relationship, else fail.
    - `kid` absent → allowed **only** if exactly one verification method exists and it is in the relationship.
    - Return typed result (JWK + method id); never "first JWK".
  - Relationship usage: ID token and VP proof → `authentication`; VC proof → `assertionMethod` (issuer DID).
  - DID URL: honour the DID path: `did:web:host:a:b` → `https://host/a/b/did.json`; no path → `/.well-known/did.json`. Remove `holder` special case or gate it behind legacy property (needs check of existing e2e DIDs).
  - Scheme: driven by `server.ssl.enabled` (D3). `true` → remote DID URLs must be https (http rejected); `false` → http and https both resolved (https tried per DID, http allowed). No new property.
  - Cache keyed by resolved URL, 300s TTL kept; do not cache failures.
- **Acceptance:** tests for each kid/relationship permutation; path DID resolves; http rejected unless flag; multi-method doc without kid rejected.

### A3 (P0) Access-token binding on issuance (`token` claim)
- **Current:** `IssuerService.authorizeRequest` validates the outer token only; delivery tokens are created with `token=null`; `CredentialRequest` stores no access token.
- **Requirement:** issuer-to-holder `CredentialMessage` delivery must carry the holder's access token (outer `token` claim) as proof of association with the original request; the issuer MUST verify the holder presented a valid access token on request creation.
- **Design:**
  1. On `POST /issuer/credentials`: require outer `token` claim (non-blank); store it encrypted in `CredentialRequest.accessToken` (`@Encrypted`, same mechanism as S3 secrets, see `FieldEncryptionService`) plus `accessTokenExpiresAt` if parseable.
  2. On delivery/reject: `createAndSignToken(holderDid, storedAccessToken, config)`; if stored token absent → delivery fails with explicit state `FAILED_NO_TOKEN`.
  3. Async approval may happen after the token expires: documented decision – the stored token is **opaque to the issuer** (it is the credential-service access token issued by the holder's STS for the issuer to call the holder's CredentialService), so expiry is evaluated by the holder, and a holder rejection (401) moves the request to `DELIVERY_FAILED` for admin retry. No refresh mechanism in Phase A.
  4. Purge stored token after terminal state (ISSUED/REJECTED/FAILED).
- **Assumption (to verify against dcp-spec.txt):** the stored token is the holder-supplied `token` claim.
- **Seed files:** `CredentialRequest` is not seeded in `initial_data*.json` (verify with the grep from AGENTS.md before closing; if present update all 12).
- **Acceptance:** request without `token` → 400/401; delivery message token contains stored token; token never logged or returned in API; encrypted at rest (repository test).

### A4 (P0) Status endpoint ownership
- **Current:** `GET /issuer/requests/{id}` validates token but not that `sub` owns the request.
- **Design:** after loading, compare token `sub` with `CredentialRequest.holderDid`; mismatch → **404** (don't leak existence; recommended) (decided, D8). Response is the DCP `CredentialStatus` JSON (`@context`, `type`, `issuerPid`, `holderPid`, `status`, `rejectionReason?`). Add JSON error bodies (see A12).
- **Acceptance:** owner 200; other holder 404; unauthenticated 401; unknown id 404.

### A5 (P0) Presentation query authorization and scope enforcement (N1, N2)
- **Design:**
  1. In `queryPresentations`: authenticated principal = **requester** DID; holder = own DID (`DidDocumentConfig.getDid()`; Phase B: tenant DID).
  2. Parse the nested `token` claim: validate it (signature via A1 machinery, `aud` = this holder, `iss` = this holder's STS or configured trust, expiry) and extract `scope` from **it**. Outer token scope is ignored.
  3. Effective scopes = intersection(requested scopes, authorized scopes). **Empty authorized scope ⇒ empty result**, never "all". Remove `findAll()` fallback. Empty requested scopes with non-empty authorized ⇒ return nothing (DCP requires explicit query) (decided, D8: empty both when the mandatory scope is not authorized and when no scope is requested).
  4. Scope grammar: `<alias>:<discriminator>` where alias ∈ `{org.eclipse.dspace.dcp.vc.type, org.eclipse.dspace.dcp.vc.id}`; type and id intersections handled separately (no normalising to bare discriminator, fixes confusion). Unqualified accepted only per D5. Strip of `:read` etc. suffixes removed in strict mode.
  5. Requested-but-not-authorized scope ⇒ 200 with fewer entries (not error), consistent with spec.
  6. Credential lookup constrained by `holderDid == own DID` (and tenant in Phase B).
  7. Rate limiter stays keyed by requester DID.
- **Acceptance:** each combination covered; test proving a requester without scope receives empty `presentation` list; credentials of other holders never returned.

### A6 (P0/P1) Holder `/offers` and `/credentials` hardening (N3, N4)
- `/offers`: authenticate with the same issuer-token validation as `/credentials` (signature, `aud` = own DID); bind to `CredentialOfferMessage` model (not `Map`); verify issuer is the configured/trusted issuer; return 200 (accepted) per spec instead of `{accepted}` custom if spec says empty body (verify against `dcp-spec.txt`).
- `/credentials`: verify `aud` = own DID; correlate `holderPid` to a stored outgoing request (`CredentialRequest` on holder side or `CredentialStatusRecord`); reject unknown `holderPid` with 400/404; keep per-credential issuer trust check.
- **Acceptance:** unauthenticated offer → 401; unknown holderPid → rejected; replay of same message idempotent (same `issuerPid` + status).

### A7 (P1) Presentation verification pipeline (N5)
Consolidate on `VerifierService` (the real path); delete or repurpose `PresentationValidationServiceImpl` after confirming no external use (separate cleanup task).
Add, in this order, inside `validatePresentationResponse`:
1. VP proof: key via A2 with `authentication`; VP `iss`/`sub` = holder; `exp`/`nbf` if present; `aud` where used.
2. For each VC: signature via issuer DID `assertionMethod`; `iss` trusted for credential type; validity window; **holder binding** (`credentialSubject.id` == holder DID, mismatch when subject missing ⇒ fail, strict by default); reject non-JWT items unless profile supports JSON-LD (no silent skip – count and report).
3. Revocation/status: pluggable `RevocationService` per profile (see §4 Q2): VC20_BSSL → BitstringStatusList, VC11_SL2021 → StatusList2021; fail-closed by property `dcp.verifier.revocation.fail-open=false`.
4. Scope satisfaction: requested type/id scopes must each be satisfied by at least one VC; unsatisfied ⇒ authentication failure.
5. Presentation Definition: see A8.
- **Acceptance:** negative test per step; connector `DcpVerifierAuthenticationProvider` e2e.

### A8 (P1) Presentation Exchange 2.1.1 (D2)
- **Scope:** the Holder side executes `presentationDefinition` supplied in `PresentationQueryMessage` and returns `presentationSubmission` in `PresentationResponseMessage`; Verifier side validates the submission.
- **Subset to implement (proposed, to verify):** `input_descriptors` with `id`, `constraints.fields[].path` (JSONPath `$.vc.*`, `$.credentialSubject.*`, `$.type`), `filter` (JSON Schema subset: `const`, `enum`, `pattern`, `type`, `minimum/maximum`), `format` restricted to supported profiles (`jwt_vc`, `jwt_vp`), `submission_requirements` of rule `all` and `pick` (count/min/max). Out of scope: `limit_disclosure`, `status_directives`, `holder`/`is_holder`, nested paths in `submission_requirements`; unsupported keywords ⇒ 400 `unsupported_presentation_definition` (not silent ignore).
- **Components:** `PresentationDefinitionEvaluator` (holder, in dcp-common so verifier can reuse for checking), `PresentationSubmissionBuilder` (descriptor_map with path `$.presentation[i]`/`$.verifiableCredential[j]`), JSONPath library (Jayway) added through root `pom.xml` dependency management; ADR required if the library choice is non-trivial.
- `validatePresentationQuery` stays XOR (scope vs definition); both/neither ⇒ 400.
- **Acceptance:** conformance vectors from the PE 2.1.1 spec examples; golden tests; mismatch yields empty submission with 200 or 404? (spec: no matching ⇒ 2xx with empty) – to verify.
- **Effort:** largest item in Phase A; independent of A1–A7 and can run in parallel after A5.

### A9 (P1) CredentialMessage / CredentialOfferMessage / CredentialStatus model alignment (N6, N7)
- `CredentialMessage`: `holderPid` optional; `credentials` allowed empty/absent when `status=REJECTED`; `status` becomes enum `CredentialStatus {ISSUED, REJECTED}` with strict deserialization; `rejectionReason` required only for REJECTED; `getType()` consistency (`CredentialMessage`).
- Rejection flow uses stored holder `holderPid` and no placeholder credential.
- `CredentialOfferMessage`: `credentials` may be empty only if schema allows (gap Q5 resolved by schema: allow); `CredentialObject.id` required in builder, `credentialType` optional; `credentialSchema` type verified against schema (String vs object).
- Breaking change: wire-compatible (only relaxes), but stricter enum on `status`. Update `docs/credential_message_examples.md` and any seed or Newman fixtures.
- **Acceptance:** model tests per rule; reject flow test asserting wire JSON shape.

### A10 (P1) Issuer metadata (N8, N9)
- Stable ids: credential object `id` MUST be configured; startup fails fast with a clear message if missing (no random UUID). Per-credential build errors fail startup rather than being swallowed. Issuer DID required (no default placeholder).
- All `CredentialObject` optional properties (`profile`, `issuancePolicy`, `bindingMethods`, `credentialSchema`, `offerReason`) populated (spec: MUST include) with config defaults documented.
- `GET /issuer/metadata`: **public** (unauthenticated) (decided, D8). `CredentialIssuanceClient.getIssuerMetadata` stops sending a bearer token.
- **Acceptance:** two startups produce identical ids; metadata served without Authorization header.

### A11 (P1) Endpoint scheme validation (N10)
- Central `EndpointValidator` in dcp-common: requires https when `server.ssl.enabled=true`, accepts http/https otherwise (D3); rejects userinfo, missing host, and non-http(s) schemes; (optional) private-address block-list behind `dcp.security.block-private-addresses`, default off in dev, on in prod, to reduce SSRF from DID-provided endpoints.
- Applied to: `CredentialDeliveryService.resolveCredentialServiceEndpoint`, `VerifierService.resolveHolderCredentialService`, `CredentialIssuanceClient.discoverIssuerService`, `DcpVerifierClient` (replace its ad-hoc check), DID resolver (A2).
- Hardcoded `/dcp/presentations/query` path replaced by service endpoint + `DCPConstants` path.

### A12 (P1) HTTP semantics
- JSON error body `{ "error": "<code>", "message": "..." }` via `@RestControllerAdvice` in each controller module; status mapping: 400 validation, 401 auth, 403 reserved for authenticated-but-forbidden, 404 unknown, 501 unsupported feature.
- `201 Created` + absolute `Location` (`<issuerBase>/issuer/requests/{issuerPid}`); 202 where async per spec; body shape per schema.
- Content-Type `application/json` on all responses; `@context` handling consistent with `DCPConstants`.

### A13 (P1) Audit
- Every rejection in A1/A5/A6/A7 emits a `DcpAuditEvent` with stable reason code; secrets/tokens never recorded. (Phase B adds `tenantId`.)

### A14 Connector security-chain wiring for DCP (`ConnectorSecurityConfig`)
**Review result: `ConnectorSecurityConfig` does NOT currently support DCP as an authentication mechanism.** It only has the switch in `protocolFilterChain`; the plumbing is missing.

| # | Finding | Evidence |
|---|---|---|
| S1 | `DcpAuthenticationFilter` (the filter added when `application.auth.dcp.enabled=true`) is a stub: `// TODO implement DCP JWT validation`, it just calls `filterChain.doFilter`. With DCP on, every protocol request reaches `anyRequest().hasRole(CONNECTOR)` unauthenticated (anonymous disabled) → all protocol calls are rejected (401). | `DcpAuthenticationFilter` |
| S2 | The real implementation `DcpVerifierAuthenticationFilter` (Bearer → `DcpBearerToken` → `AuthenticationManager` → `DcpVerifierAuthenticationProvider`) is not a bean and not wired anywhere in `ConnectorSecurityConfig`. `DcpVerifierAuthenticationProvider` is injected into the config constructor but never used (dead field). | `ConnectorSecurityConfig` ctor |
| S3 | The only `AuthenticationManager` bean is `@Conditional(InternalAuthenticationModeCondition)` and contains only the DAO provider, so even if the filter were wired there is no manager containing the DCP provider in KEYCLOAK/DISABLED modes, and in INTERNAL mode it would not contain it. | `ConnectorSecurityConfig.authenticationManager` |
| S4 | Two different flags: chain selection uses `application.auth.dcp.enabled`; the provider additionally requires `dcp.vp.enabled` (default false) and silently returns `null`. DCP can therefore be "enabled" and still authenticate nobody. | `DcpVerifierAuthenticationProvider` |
| S5 | `DcpVerifierAuthenticationFilter.shouldNotFilter` matches only un-prefixed paths (`/catalog`, `/negotiations/`, `/transfers/` …). Since the multitenant change, protocol paths are `/{tenantId}/catalog/...` (the chain matcher already covers `/*/...`), so the filter would skip all tenant-prefixed requests. | filter vs `protocolFilterChain` matchers |
| S6 | With DCP on the chain forces DCP even in KEYCLOAK/INTERNAL; no mixed/fallback mode. The legacy `WebSecurityConfig` chained DCP then Basic. Silent fallback in provider/filter (all exceptions swallowed) also masks forged-token failures (see A1). | `protocolFilterChain`, filter |
| S7 | `/dcp/**`, `/issuer/**` (and `/{tenantId}/dcp|issuer/**` in Phase B) are not in any chain matcher → default chain `permitAll`; controllers authenticate themselves. Acceptable only if each controller does it (see N3, N9); should be explicit. | `defaultFilterChain` |
| S8 | The authenticated principal is the Verifier's DID with only `ROLE_CONNECTOR`; no tenant is bound. Tenant comes from the path (`TenantAwareProtocolController.resolveTenant`), so DCP `aud` must be checked against the **tenant's** DID (D4) — not done. | provider, controllers |
| S9 | `WebSecurityConfig` (from the old pre-multitenant branch) is dead code: no `@Configuration`, never instantiated. If annotated it would duplicate beans (`passwordEncoder`, `corsConfigurationSource`, `userDetailsService`, `authenticationManager`) and use un-prefixed paths with `permitAll` for `/dcp/**`. | file |

**Target design**
1. **Single DCP filter:** keep `DcpVerifierAuthenticationFilter` (rename to `DcpAuthenticationFilter`, delete the stub), implemented as a plain `OncePerRequestFilter` that is **not** a `@Component` (avoid servlet auto-registration; same approach as `ApiTenantContextFilter`) and is declared as a `@Bean` conditioned on `DcpEnabledCondition`. Remove `shouldNotFilter` path list: the filter is applied only through `protocolFilterChain`, whose `securityMatcher` already defines scope (tenant-prefixed and un-prefixed paths).
2. **Dedicated manager:** new bean `dcpAuthenticationManager` = `ProviderManager(dcpVerifierAuthenticationProvider)`, qualified, `@Conditional(DcpEnabledCondition)`; independent of auth mode so DCP works next to DISABLED, INTERNAL and KEYCLOAK. Existing INTERNAL `authenticationManager` stays untouched (used by `InternalAuthServiceImpl`); inject by qualifier to avoid ambiguity.
3. **Flags:** one switch. `application.auth.dcp.enabled` selects DCP for the protocol chain; `dcp.vp.enabled` semantic (VP exchange vs plain Self-Issued ID Token) is documented separately or merged (decision for plan: keep `dcp.vp.enabled` only as "require VP query"; token verification itself is always on when DCP is enabled). Startup fails fast if DCP is enabled but DCP beans/keystore are missing.
4. **Mode matrix** (extends the table in the class Javadoc and `doc/security.md`):
   - `DISABLED` → admin and protocol permitAll; DCP flag ignored with a WARN.
   - `INTERNAL`/`KEYCLOAK` + `dcp.enabled=false` → unchanged.
   - `INTERNAL`/`KEYCLOAK` + `dcp.enabled=true` → admin chain by mode; protocol chain = DCP only (as documented today). Optional later: `application.auth.dcp.fallback=internal|keycloak` (not in Phase A).
5. **Failure semantics:** Bearer missing/invalid ⇒ no authentication set ⇒ `delegatedAuthenticationEntryPoint` 401; the provider throws `BadCredentialsException` (with reason code, audit event) instead of returning `null`; `IOException` toward holder ⇒ 401/502 per reason. No silent fallback when DCP is the only mechanism.
6. **Tenant binding (Phase B but designed now):** after authentication, a `DcpTenantAudienceCheck` verifies that token `aud` equals the DID of the tenant resolved from the path prefix (`Tenant.participantId`), using `TenantService.findEnabledTenantById`; in Phase A (single tenant) compares with `DidDocumentConfig.getDid()`. Principal stays the requester DID; tenant context is set by `TenantAwareProtocolController` as today and cleared by `TenantContextClearingInterceptor`.
7. **DCP endpoints chain:** add `@Order(2)` chain for `/dcp/**`, `/issuer/**`, `/*/dcp/**`, `/*/issuer/**` (shift protocol chain to 3, default to 4). Rules: `/{t}/.well-known/did.json`, `GET issuer/metadata`: permitAll; all other: authenticated by the DCP filter (Self-Issued ID Token only, no `ROLE_CONNECTOR` requirement for holder/issuer endpoints; role `ROLE_DCP_PEER`). Controllers still validate `aud`/scope (defense in depth). `DISABLED` mode keeps permitAll.
8. **Cleanup:** delete `WebSecurityConfig` (dead, superseded), `DcpAuthenticationFilter` stub, and the unused constructor parameter once the manager is wired; remove stale references in docs.
9. **Tests:** `@WebMvcTest`/IT for each mode (`DISABLED`, `INTERNAL`, `KEYCLOAK`) × `dcp.enabled` {false,true}: valid DCP bearer → 200 with `ROLE_CONNECTOR`; missing/forged bearer → 401; tenant-prefixed path covered; `/issuer/metadata` and DID doc public; `/api/**` unaffected by DCP. Update `ci/docker` Newman auth suites and `doc/security.md`.
10. **Dependencies:** needs A1 (real signature check) before the DCP chain can be considered secure; until then S1–S3 fixes alone would make forged tokens work. Ship A14 and A1 in the same slice.

**Reference: documented matrix and filter placement today** (`KEYCLOAK_INTEGRATION_COMPLETE_SUMMARY.md`; its `BASIC` rows now mean `INTERNAL`, which authenticates with an internal HS256 JWT via `InternalJwtAuthenticationFilter`, not HTTP Basic; the doc should be corrected in the docs slice).

| Chain (order) | Matcher | KEYCLOAK | INTERNAL | DISABLED | DCP (`application.auth.dcp.enabled=true`) |
|---|---|---|---|---|---|
| Admin (1) | `/api/**`, `/actuator/**`, `/env` | `KeycloakAuthenticationFilter` (+ `ApiTenantContextFilter` after) → roles from `realm_access.roles` | `InternalJwtAuthenticationFilter` (+ `ApiTenantContextFilter` after), roles from the `roles` claim, tenant from `tenantId` claim | permitAll | never used here (admin always uses the provider) |
| Protocol (2) | `/connector,catalog,negotiations,transfers,consumer/**` and `/*/…` | `KeycloakAuthenticationFilter` → `ROLE_CONNECTOR` | `InternalJwtAuthenticationFilter` → `ROLE_CONNECTOR` | permitAll | **`DcpAuthenticationFilter` (stub today, S1)**; intended `DcpVerifierAuthenticationFilter` → `DcpVerifierAuthenticationProvider` → `ROLE_CONNECTOR` |
| Default (3) | everything else, incl. `/dcp/**`, `/issuer/**`, `/.well-known/**` | permitAll | permitAll | permitAll | permitAll (to be replaced by A14 step 7) |

Notes: `DISABLED + dcp.enabled=true` is already rejected at startup by `AuthenticationModeResolver` (keep, and keep it in the new tests). Tenant context: admin chain sets it from the authenticated user (`ApiTenantContextFilter`); protocol chain gets it from the path via `TenantAwareProtocolController.resolveTenant`, so no filter sets a tenant for DCP-authenticated requests, which matches the Phase B design above.

### Phase A ordering / parallelism
```
A2 ─┬─> A1 ──> A5 ──> A7 ──> A8(evaluator reuse)
    │          └──> A6
A9 ─┴─> A3 ──> A4
A10, A11, A12, A13 parallel;  A14 ships together with A1 (needs working validation)
```
Suggested slices: (1) crypto/identity: A2, A1; (2) issuance: A9, A3, A4, A10; (3) holder/verifier: A5, A6, A7; (4) PE: A8; (5) cross-cutting: A11, A12, A13, A14; (6) QA slice; (7) docs slice.

## 3. Phase B – Multitenancy

- **Model:** tenant DID = `Tenant.participantId` (D4), e.g. `did:web:host:tenantA`; DID document served at `/{tenantId}/did.json` (and `/{tenantId}/.well-known/did.json` if needed), Phase A2 path resolution is the prerequisite.
- **Dependency:** dcp modules must not reach into `connector`; introduce a small SPI in `dcp-common` (`DcpTenantContext { Optional<String> currentTenantId(); String didFor(tenantId); }`) implemented in `connector` using `TenantContextHolder`/`TenantService`. ADR required (AGENTS.md: architecturally significant) via `decisions` skill.
- **Routes:** `/{tenantId}/dcp/**`, `/{tenantId}/issuer/**`; security chain matcher extended (`/*/dcp/**`, `/*/issuer/**`); tenant resolved via `TenantAwareProtocolController.resolveTenant`; disabled tenant ⇒ 404.
- **Data:** add `tenantId` (indexed, non-null) to `VerifiableCredential`, `CredentialRequest`, `CredentialStatusRecord`, `StatusList`, `DcpAuditEvent`; `KeyMetadata` per tenant (separate alias/keystore entry per tenant, kid per tenant). All repositories add tenant-scoped finders; no `findAll()`. Follow D-TEC-006 (DBRef tenant mitigation) and D-TEC-005 (programmatic indexes). **All 12 `initial_data*.json` seed files updated together** for any seeded model changed.
- **Migration:** existing documents backfilled with default tenant via startup migration; single-tenant deployments keep un-prefixed routes behind `dcp.multitenancy.enabled=false`.
- **Async:** propagate tenant through `TenantContextTaskDecorator` for `@Async` delivery and audit listener (listener currently `@Async`, would lose context).
- **Acceptance:** tenant A cannot read B's credentials/requests/status lists (IT with two tenants); token `aud` must equal the tenant's DID; DID doc per tenant has distinct key.

## 4. The 2 remaining open questions – impact if NOT addressed now

(Assumed to be Q1 *Issuer Service discovery direction* and Q2 *revocation mechanism*; items 3–7 in the gap doc are resolved schema-first. Confirmed, D7.)

### Q1 – Whose DID document publishes the `IssuerService` entry
Current code: the holder looks up `IssuerService` in the **issuer's** DID (`CredentialIssuanceClient.discoverIssuerService`, `dcp.issuer.location`), and the issuer looks up `CredentialService` in the **holder's** DID for delivery. This is the commonly accepted reading and works between two TRUE Connectors.
| Aspect | If left unresolved |
|---|---|
| Likelihood of mismatch | Low–medium; only appears with a third-party DCP implementation that publishes `IssuerService` on the *holder* DID or via credential offers only. |
| Impact | Interop failure on first contact with such a party: holder reports `IssuerServiceNotFoundException`; no data/security impact. |
| Blocks | No Phase A item; A3/A6/A10 proceed with current direction. |
| Cost to defer | Low. Mitigation: keep discovery behind `DidResolverService` + `IssuerServiceResolver` abstraction with a config fallback `dcp.issuer.service-endpoint` (explicit URL) so a different convention is a localised change. Offer-driven flow (`CredentialOfferMessage` includes issuer DID) already works independently of this question. |
| Recommendation | Safe to defer; document assumption in the ADR for A2/A10. |

### Q2 – Revocation mechanism (profile mapping vs global §6.10)
Current code: `StatusList2021RevocationService` + in-memory service, issuer-side `StatusListService`; **the real verifier path does no revocation check at all** (N5).
| Aspect | If left unresolved |
|---|---|
| Likelihood | High that it matters in production; a verifier accepting revoked credentials is a functional/security gap regardless of the spec ambiguity. |
| Impact | (a) Revoked VCs continue to authenticate (security-relevant, medium–high). (b) Wrong list format/status type per profile (`BitstringStatusList` vs `StatusList2021`) breaks interop with other issuers and can make verification fail-closed against valid credentials. (c) Issuer `StatusList` data model may need a migration later (purpose, size, per-tenant ids). |
| Blocks | A7 step 3 cannot be completed definitively; Phase B `StatusList.tenantId` design is affected if list format changes. |
| Cost to defer | Medium–high if data model fixed too early; low if A7 is built with a `RevocationService` strategy per `ProfileId` (as proposed) and fail-open/closed toggle. |
| Recommendation | Do not defer the **verifier check**; defer only the choice of the *global* mechanism. Implement per-profile mapping now (profile-specific text is normative for each profile), keep the strategy interface, and record an ADR noting the global §6.10 conflict. |

## 5. Cross-cutting requirements for the implementation plan
- Java conventions (`.github/instructions/java.instructions.md`), Javadoc, no FQNs (existing `it.eng.dcp.common.model.KeyMetadata` FQN in `KeyService` is out of scope).
- Tests: JUnit 5 + Mockito for units; Testcontainers ITs in `connector`; update Newman suites under `ci/docker/test-cases/` for new signature enforcement.
- Docs: update `dcp/doc/*` affected files in the docs slice; `doc/security.md`; `CHANGELOG.md` (Security section for A1/A5/A6).
- ADRs (via `decisions` skill): (1) DID path resolution + HTTPS policy, (2) access-token persistence for async issuance, (3) Presentation Exchange subset and library, (4) tenant SPI/multitenancy model, (5) revocation strategy.
- Verification: `mvn clean verify` (Docker), `mvn validate` (Checkstyle), `mvn -pl connector -Ptck verify`, SpotBugs scan.

## 6. Final assumptions (no open questions)
- D9: A8 – when nothing matches the presentation definition, respond 200 with an empty presentation list and no submission. The PE subset in A8 stands as proposed.
- D10: A3 – the stored token is the holder-supplied `token` claim; if the holder rejects it as expired, delivery moves to `DELIVERY_FAILED` and an admin retries.
