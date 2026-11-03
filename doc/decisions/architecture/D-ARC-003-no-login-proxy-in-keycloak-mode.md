# D-ARC-003 — No connector-side login in Keycloak mode

## Metadata
- Status: Accepted
- Date: 2026-10-30
- Owner: Security / authentication
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: security, keycloak, authentication, ui
- Risk Level: Medium

## Context
In `KEYCLOAK` mode the connector used to proxy `POST /api/v1/auth/login|refresh|logout` to Keycloak
(`KeycloakAuthServiceImpl`, resource-owner password grant on the `application.keycloak.login.*`
client), so the UI sent user passwords and Keycloak refresh tokens through the connector. With
several realms per connector (`connector-admin` and one realm per tenant), the proxy would also
have to pick a realm per request. Keycloak already offers sign-in, OTP, session handling, password
change and an event log, and the UI can learn each tenant's realm from `GET /public/tenant`.

## Decision
In `KEYCLOAK` mode the connector takes no part in how people sign in. The UI uses the
authorization code flow + PKCE against the tenant or `connector-admin` realm, refreshes tokens and
logs out through Keycloak, and sends the access token to the connector. The connector offers no
login, refresh, logout or password-change route in this mode. `INTERNAL` mode keeps its own
`/api/v1/auth/*` routes unchanged.

## Alternatives Considered
- **Keep the password-grant proxy, extended to several realms** → rejected because passwords and
  refresh tokens keep passing through the connector, the password grant is deprecated in OAuth 2.1,
  and MFA/OTP and Keycloak's login pages can't be used.
- **Backend-for-frontend (connector holds the session and the tokens)** → rejected for now because
  it puts session state and token storage back into the connector. It can still be added in front
  of the UI later without changing the connector's resource-server role.

## Rationale
Removing the proxy shrinks the attack surface: user credentials and refresh tokens never reach the
connector, and authentication features (MFA, lockout, password policy, events) live in one place.
The connector only has to validate tokens, which it must do anyway.

## Consequences

### Positive
- No user password or Keycloak refresh token passes through the connector in `KEYCLOAK` mode.
- `application.keycloak.login.*` and `KeycloakAuthServiceImpl` are gone; less configuration.
- Login, refresh and logout events are recorded in Keycloak's event log.

### Negative
- Clients that supported both modes through one `/api/v1/auth/login` call must implement the PKCE
  flow for `KEYCLOAK` mode.
- The connector's own audit log no longer has login events in `KEYCLOAK` mode.

### Risks
- A deployment keeps calling `/api/v1/auth/*` in `KEYCLOAK` mode → it gets 401 (no token) or 404,
  and the CHANGELOG and `doc/security.md` describe the migration.

## Related
- Decisions: [D-TEC-004](../technical/D-TEC-004-keycloak-user-registration.md)
- Docs: [doc/security.md](../../security.md#keycloak-authentication-mode-keycloak)
- Tickets: #7, #20, #21, #22, #23
