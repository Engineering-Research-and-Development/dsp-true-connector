# D-TEC-014 — Credential Service access tokens are holder-signed JWTs bound to the Verifier

## Metadata
- Status: Accepted
- Date: 2026-11-20
- Owner: TRUE Connector team
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: dcp, security, credential-service, access-token, jwt
- Risk Level: Medium

## Context

In the DCP presentation flow (DCP v1.0.1 §5.1), a client (the holder) authenticates to a Verifier with a
Self-Issued ID Token. Its `token` claim carries an access token for the holder's Credential Service. The
Verifier then queries the Credential Service with a Self-Issued ID Token of its own that carries that access
token in `token` (§5.1 step 6, §5.3.1). The Credential Service validates both and releases only the
credentials the access token authorizes (§5.4.1.2). The specification leaves the access token format to the
implementation (§4.3.1): the Verifier treats it as opaque, and only the Credential Service that issued it
reads it.

Before #407 the connector already nested a holder-signed JWT with `scope` in its Self-Issued ID Token
(`SelfIssuedIdTokenService.createStsCompatibleToken()`), but its `aud` was the holder itself. The Verifier
forwarded it as its bearer token, and the Credential Service trusted the `scope` of any valid token. A token
without `scope` was treated as unrestricted.

The format decides which properties the Credential Service can check: who issued the token, which Verifier
may use it, what it authorizes and until when. It also decides whether the Credential Service needs state.
The DCP modules are meant to become a reusable library usable by single- and multi-tenant hosts (spec #439),
so the choice must not depend on connector infrastructure.

## Decision

The access token is a JWS-signed JWT that the holder signs with its own DID key (the `capabilityInvocation`
key that signs its Self-Issued ID Tokens):

- header `typ` = `at+jwt` (RFC 9068), `alg` = `ES256`, `kid` = the signing key ID
- `iss` = `sub` = holder DID
- `aud` = the DID of the Verifier the token is issued to
- `scope` = the scopes that Verifier may query (array of `[alias]:[discriminator]` or simple strings)
- `iat`, `nbf`, `exp` (5 minutes) and `jti`

`SelfIssuedIdTokenService.createAccessTokenWithScopes()` creates it, and `validateAccessToken()` is the only
place that validates it. The Credential Service accepts it only when `typ` is `at+jwt`, `iss` and `sub` are its
own DID, `aud` contains the `sub` of the Verifier's validated Self-Issued ID Token, the signature verifies with
a `capabilityInvocation` key of its own DID document, and the time claims are current. An access token without
`scope` authorizes no credentials. `validateToken()` rejects a token typed `at+jwt`, so an access token is never
accepted as a Self-Issued ID Token. A query without an access token gets 401 (#407 decision Q2).

## Alternatives Considered
- **Opaque random token stored server-side with its scopes and expiry** → rejected: the Credential Service
  would need a shared, replicated token store keyed by participant context, and a lookup per query, for no
  security gain over a signed and audience-bound JWT with a 5-minute lifetime.
- **Reuse the Keycloak/M2M token infrastructure** → rejected: DCP protocol endpoints are never authenticated
  with Keycloak tokens (spec #439, zone security), and the DCP library must not depend on the connector's
  identity provider.
- **Keep the old nested token with `aud` = holder DID** → rejected: the token is not bound to a Verifier, so a
  Verifier that captured it could present it on behalf of another party, and it cannot be told apart from a
  Self-Issued ID Token addressed to the holder.

## Rationale

A signed JWT keeps the Credential Service stateless and lets it verify every property it needs offline: the
signature and `iss`/`sub` prove that this holder issued the token, `aud` binds it to one Verifier (the
security requirement of #407), `scope` carries the authorization, and `exp` bounds its life. It reuses the
key, DID resolution and EC verification code that already validate Self-Issued ID Tokens
(`SelfIssuedIdTokenService`, `HttpDidResolverService`), so key rotation works the same way for both tokens.
Resolving the key through the holder's own DID document, rather than the local keystore, keeps tokens signed
just before a rotation valid while the previous key is still published.

The `at+jwt` type makes token confusion impossible in both directions: a Self-Issued ID Token addressed to a
Verifier (same `iss`/`sub`/`aud` shape) is not an access token, and an access token is not a Self-Issued ID
Token. Treating a missing `scope` as "nothing" keeps the default closed.

The access token `jti` is not recorded: the token is only ever presented inside the Verifier's Self-Issued ID
Token, whose `jti` is checked against the replay cache, and a holder may legitimately be queried more than once
within the token's lifetime.

## Consequences

### Positive
- The Credential Service authorizes queries only from a validated, Verifier-bound token; spec-conformant
  Self-Issued ID Tokens without `scope` no longer read every credential.
- No server-side token state, so the scheme works unchanged with several replicas.
- The format is produced and checked in one class in `dcp-common`, so it moves with the DCP library.

### Negative
- The holder's Credential Service resolves its own DID document to verify its access tokens (cached by
  `HttpDidResolverService`).
- An issued access token cannot be revoked before `exp`; the 5-minute lifetime bounds the exposure.
- Callers that configure no `dcp.vp.scope` now get no credentials instead of all credentials.

### Risks
- Access tokens from other DCP implementations are not interoperable with this Credential Service. Mitigation:
  by the specification, only the Credential Service that issued an access token ever validates it.
- A Verifier could replay an access token inside new Self-Issued ID Tokens until `exp`. Mitigation: the token is
  bound to that Verifier only, authorizes only the scopes the holder granted it, and expires after 5 minutes.
- Multi-tenant hosts need the own DID and keys per participant. Mitigation: `validateAccessToken()` takes the own
  DID from the token service configuration; the participant context of spec #439 (#441) will supply it per
  routed participant.

## Related
- Decisions: —
- Docs: [`dcp/doc/presentation_verification_flow.md`](../../../dcp/doc/presentation_verification_flow.md), [`dcp/doc/vc_vp_authentication.md`](../../../dcp/doc/vc_vp_authentication.md), [`dcp/doc/DCP_SPEC_GAPS.md`](../../../dcp/doc/DCP_SPEC_GAPS.md), [`dcp/doc/SPEC_DCP_PARTICIPANT_CONTEXT_AND_ZONE_SECURITY.md`](../../../dcp/doc/SPEC_DCP_PARTICIPANT_CONTEXT_AND_ZONE_SECURITY.md)
- Tickets: #407, roadmap #397, spec #439
