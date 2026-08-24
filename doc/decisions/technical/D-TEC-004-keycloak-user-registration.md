# D-TEC-004 — Keycloak user registration via Admin REST API

## Metadata
- Status: Accepted
- Date: 2026-06-26
- Owner: Engineering Research and Development
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: keycloak, user-management, admin-api
- Risk Level: Medium

## Context

When `application.auth.provider=KEYCLOAK` is active, the standard `UserService` is inactive
(`@Conditional(BasicOrDisabledAuthenticationModeCondition.class)`).  As a result, `POST /api/v1/users`
had no implementation, leaving a gap in the management API surface.

The requirement is to close this gap so that:
- `POST /api/v1/users` creates a user in the Keycloak realm when Keycloak mode is active.
- The management API surface (endpoints and response shapes) is identical regardless of auth mode.

Key constraints:
- No additional Maven dependency on the Keycloak Admin Client SDK (avoids version coupling).
- The connector's backend service account already has the realm `admin` role, which includes `manage-users`.
- An access token is already available via `KeycloakAuthenticationService` (client credentials flow).
- The Keycloak Admin REST API endpoint is `POST /admin/realms/{realm}/users`.

## Decision

Implement a `KeycloakUserService` (active only in Keycloak mode) that calls the Keycloak Admin REST
API using `java.net.http.HttpClient`.  A parallel `KeycloakUserApiController` is created (conditioned
on `KeycloakAuthenticationModeCondition`) alongside the existing `UserApiController` (conditioned on
`BasicOrDisabledAuthenticationModeCondition`).  Two new configuration properties are added:
`application.keycloak.admin.server-url` and `application.keycloak.admin.realm`.

## Alternatives Considered

- **Keycloak Admin Client SDK** → rejected: introduces a version-coupled SDK dependency; the raw
  REST API is stable and sufficient.
- **Common `UserManagementService` interface injected into a single unconditional controller** →
  rejected: requires a Spring injection point that is always satisfied regardless of mode, which
  complicates bean resolution when only one implementation is active.  Two thin conditional controllers
  is simpler and more explicit.
- **Derive realm name from `spring.security.oauth2.resourceserver.jwt.issuer-uri`** → rejected:
  requires URI parsing and is fragile; an explicit property is clearer and more operator-visible.

## Rationale

The two-controller approach keeps each auth mode's controller and service pair self-contained and
independently testable.  `java.net.http.HttpClient` is already present in the JDK 17 runtime and
is already used in the integration test base class.  Using client credentials token (existing flow)
avoids a second authentication path.

## Consequences

### Positive
- No new library dependency.
- `POST /api/v1/users` works in both Basic and Keycloak modes with the same request/response shape.
- Each mode's code is independently conditional and does not affect the other.

### Negative
- Duplication of the controller's routing methods (two controllers with the same path mappings,
  each active in a different mode).
- Requires two new properties to be set in Keycloak-mode deployments.

### Risks
- The backend service account must have the Keycloak `manage-users` role; if the role is not
  present, all user-creation calls in Keycloak mode will return 403.  The properties file and
  realm import are updated as part of this implementation.

## Related
- Decisions: —
- Docs: `connector/documentation/users.md`, `doc/security.md`
- Tickets: #248 (MT1 slice), #254
