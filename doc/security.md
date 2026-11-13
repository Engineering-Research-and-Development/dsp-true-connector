# Security

## Overview

The DSP True Connector supports multiple authentication and security mechanisms:

1. **TLS/SSL** - Transport layer security for encrypted communication
2. **Keycloak OAuth2/OIDC** - Token-based authentication for production use (`KEYCLOAK` mode)
3. **Internal Authentication** - Username/password backed by MongoDB, with self-issued JWTs (`INTERNAL` mode)
4. **Disabled** - All endpoints open, for local development only (`DISABLED` mode)
5. **DCP** - Decentralized Claims Protocol for protocol endpoints (stub, future integration)
6. **OCSP** - Certificate validation and revocation checking

How people sign in depends on the mode. In `KEYCLOAK` mode the UI signs in to Keycloak directly
(authorization code + PKCE) and the connector never sees a password or a refresh token. In
`INTERNAL` mode the connector itself offers `POST /api/v1/auth/login`, `/refresh` and `/logout`. See
[Internal Login Contract](#internal-login-contract-apiv1auth) and
[Keycloak Authentication Mode](#keycloak-authentication-mode-keycloak) below.

---

## TLS Configuration

Connector can operate both in http or httpS mode.
To enable https mode, certificates must be provided and following properties needs to be set with correct values:

```properties
## SSL Configuration
spring.ssl.bundle.jks.connector.key.alias = connector-a
spring.ssl.bundle.jks.connector.key.password = password
spring.ssl.bundle.jks.connector.keystore.location = classpath:connector-a.jks
spring.ssl.bundle.jks.connector.keystore.password = password
spring.ssl.bundle.jks.connector.keystore.type = JKS
spring.ssl.bundle.jks.connector.truststore.type=JKS
spring.ssl.bundle.jks.connector.truststore.location=classpath:truststore.jks
spring.ssl.bundle.jks.connector.truststore.password=password

server.ssl.enabled=true
server.ssl.key-alias=connector-a
server.ssl.key-password=password
server.ssl.key-store=classpath:connector-a.jks
server.ssl.key-store-password=password
```

Make sure to update values with correct one, provided keystore files are self signed and should not be used in production.

More information on how to generate keystore and truststore files can be found [here](./certificate/PKI_CERTIFICATE_GUIDE.md).

With `server.ssl.enabled=true` the truststore of the `connector` bundle is the only source of trust
for outbound calls. The JVM default truststore (`cacerts`) is not used, so the truststore must contain
every CA the connector should trust. That includes a public CA such as Let's Encrypt when a peer
connector, Keycloak or the S3 storage uses a public certificate.

> [!WARNING]
> **HTTP mode (`server.ssl.enabled=false`) is INSECURE and UNTRUSTED.** It is meant only for local
> development and first tests, until certificates are set up. Never use it in production or on a
> shared network.
>
> - **Nothing is encrypted.** The connector serves plain HTTP. Requests, responses, bearer tokens,
>   the M2M client secret and the transferred data can be read and changed by anyone on the network.
> - **No remote service is verified.** Every outbound client accepts every certificate when it calls
>   an `https` URL. This covers OkHttp (protocol calls and the Keycloak token request), Keycloak
>   discovery and JWKS, `HttpsURLConnection` and the S3 clients. They also accept plain `http`. None of
>   these is checked:
>   - who issued the certificate: a self-signed certificate or one from an unknown CA is accepted;
>   - whether the certificate has expired or is not valid yet;
>   - whether the host name matches the certificate's SAN;
>   - whether the certificate was revoked (OCSP);
>   - whether the certificate chain is complete and correctly signed.
> - **So the connector cannot tell whether it is talking to the real peer connector, Keycloak or S3
>   storage.** Anyone who can intercept or redirect the traffic, for example through DNS, a proxy or a
>   shared network, can pose as any of them. They can then:
>   - read the M2M client secret, tokens and data;
>   - return false catalogs, agreements or files;
>   - serve their own Keycloak signing keys, and then forge tokens that the connector accepts for any
>     tenant, super-admin or connector role.
> - The connector logs a `WARN` at startup for each client that trusts all certificates.
>
> Use `server.ssl.enabled=true` with a keystore and a truststore for any environment that is shared,
> reachable from other machines, or holds real data.

---

## Authentication Modes

The connector uses a single unified security configuration (`ConnectorSecurityConfig`) with four
ordered Spring Security filter chains: one for the internal loopback zone, one for admin endpoints,
one for protocol endpoints, and one default chain. Authentication behaviour is controlled by two
properties:

```properties
# Primary authentication provider: KEYCLOAK | INTERNAL | DISABLED
application.auth.provider=KEYCLOAK

# Optional: route protocol endpoints through DCP instead of the provider above.
# Cannot be combined with provider=DISABLED.
# application.auth.dcp.enabled=false
```

### Authentication Matrix

| `auth.provider` | `dcp.enabled` | `/api/**` `/actuator/**` | Protocol endpoints¹ |
|-----------------|---------------|--------------------------|----------------------|
| `KEYCLOAK`      | `false`       | Keycloak-issued JWT (UI signs in to Keycloak directly) → `ROLE_ADMIN` or `ROLE_SUPER_ADMIN`; `ROLE_SUPER_ADMIN` required for `/tenants/**`, most `/users/**`, `/properties/**`; no own-account PUT endpoints (profile and password are managed in Keycloak) | Keycloak JWT → `ROLE_CONNECTOR` |
| `KEYCLOAK`      | `true`        | Keycloak-issued JWT (UI signs in to Keycloak directly) → `ROLE_ADMIN` or `ROLE_SUPER_ADMIN`; `ROLE_SUPER_ADMIN` required for `/tenants/**`, most `/users/**`, `/properties/**`; no own-account PUT endpoints (profile and password are managed in Keycloak) | DCP → `ROLE_CONNECTOR` |
| `INTERNAL`      | `false`       | Self-issued JWT (obtained via `/api/v1/auth/login`) → `ROLE_ADMIN` or `ROLE_SUPER_ADMIN`; `ROLE_SUPER_ADMIN` required for `/tenants/**`, most `/users/**`, `/properties/**`; `ROLE_ADMIN` may call `/users/me` and own-account PUT endpoints | Self-issued JWT → `ROLE_CONNECTOR` |
| `INTERNAL`      | `true`        | Self-issued JWT (obtained via `/api/v1/auth/login`) → `ROLE_ADMIN` or `ROLE_SUPER_ADMIN`; `ROLE_SUPER_ADMIN` required for `/tenants/**`, most `/users/**`, `/properties/**`; `ROLE_ADMIN` may call `/users/me` and own-account PUT endpoints | DCP → `ROLE_CONNECTOR` |
| `DISABLED`      | `false`       | `permitAll()` — all management endpoints open, not for production | `permitAll()` |
| `DISABLED`      | `true`        | ❌ startup error | ❌ startup error |

¹ Protocol endpoints: `/connector/**`, `/catalog/**`, `/negotiations/**`, `/transfers/**`

> **Note:** `provider=DISABLED` combined with `dcp.enabled=true` is explicitly rejected at startup
> with an `IllegalStateException`.

### Filter Chains

| Order | Matcher | Authentication | Role |
|-------|---------|----------------|------|
| 0 | `/internal/**` | Internal-call token signed with an in-memory key, loopback peer only; same in `INTERNAL` and `KEYCLOAK` mode; `permitAll()` in `DISABLED` mode (see [Internal Call Zone](#internal-call-zone-internal)) | `ROLE_INTERNAL` |
| 1 | `/api/**`, `/actuator/**`, `/env` | The configured provider, never DCP. In `KEYCLOAK` mode: tenant, `connector-admin` and `platform` issuers, each with its own rules (see [Trust scope per chain](#trust-scope-per-chain)) | `ROLE_ADMIN` or `ROLE_SUPER_ADMIN` |
| 2 | `/connector/**`, `/catalog/**`, `/negotiations/**`, `/transfers/**` | DCP when `dcp.enabled=true`, otherwise the configured provider. In `KEYCLOAK` mode: `platform` issuer only (see [Protocol Trust](#protocol-trust-in-keycloak-mode)) | `ROLE_CONNECTOR` |
| 3 | everything else | `permitAll()` | none |

### Swagger/OpenAPI Documentation

Swagger UI and the OpenAPI JSON endpoints use `springdoc.swagger-ui.enabled` and
`springdoc.api-docs.enabled`. They are disabled in standard and TCK configurations and enabled
only in the Docker and Terraform demo property files, where they are intended for seeded mock
data. When enabled, `/swagger-ui/**` and `/v3/api-docs/**` are publicly reachable through the
default security chain. These routes return documentation and examples only; they do not grant
unauthenticated access to management or DSP operations. Actual calls remain subject to the
existing authentication and role requirements. Do not enable the documentation endpoints in
production.

The `ROLE_CONNECTOR` role is not bound to a specific tenant by the JWT. A protocol request's
`{tenantId}` path selects the enabled tenant whose data is returned; an otherwise valid connector
token can address another enabled tenant route. Do not treat connector tokens as tenant-specific
credentials. Deployments that require tenant-bound identities must enforce an explicit tenant
authorization rule or equivalent deployment boundary. This behavior is covered by
`CrossTenantIsolationIT`.

See the [OpenAPI guide](openapi.md) for the grouped documents and the Swagger authentication
walkthrough.

---

## Internal Login Contract (`/api/v1/auth/*`)

Only `INTERNAL` mode exposes these three endpoints, all `permitAll()` and served by `AuthController`
(active only when `application.auth.provider=INTERNAL`). In `KEYCLOAK` and `DISABLED` modes they do
not exist:

| Endpoint | Request body | Response |
|----------|---------------|----------|
| `POST /api/v1/auth/login` | `{"email": "...", "password": "..."}` | `200 OK` with `LoginResponse` |
| `POST /api/v1/auth/refresh` | `{"refresh_token": "..."}` | `200 OK` with `LoginResponse` |
| `POST /api/v1/auth/logout` | `{"refresh_token": "..."}` | `200 OK`, empty body (idempotent) |

`LoginResponse` is a flat, snake_case, token-only JSON body mirroring a typical identity-provider
token response:

```json
{
  "access_token": "<JWT>",
  "refresh_token": "<opaque-id>",
  "token_type": "Bearer",
  "expires_in": 900
}
```

`AuthController` delegates to `InternalAuthServiceImpl`, which validates credentials against MongoDB
and mints a self-signed JWT (see [Internal Authentication Mode](#internal-authentication-mode-internal)).
Login, refresh and logout publish the `APPLICATION_LOGIN`, `APPLICATION_TOKEN_REFRESHED` and
`APPLICATION_LOGOUT` audit events (and their `*_FAILED` counterparts).

A client that supports both modes must switch its sign-in flow with the mode: in `KEYCLOAK` mode it
signs in to Keycloak itself (see [Getting Tokens](#getting-tokens)).

---

## Keycloak Authentication Mode (`KEYCLOAK`)

The recommended mode for production. The connector acts as an OAuth2/OIDC resource server: it
validates JWTs issued by Keycloak and takes no part in how people sign in. It never handles user
passwords or Keycloak refresh tokens, and offers no login, refresh, logout or password-change route
(see [D-ARC-003](decisions/architecture/D-ARC-003-no-login-proxy-in-keycloak-mode.md)).

### Enabling Keycloak

```properties
application.auth.provider=KEYCLOAK
```

### Configuration Properties

> The UI uses the public `keycloak-api` client of the tenant or `connector-admin` realm and needs
> no connector configuration. The connector itself is configured for token validation (the trust
> properties) and for its own confidential machine-to-machine client:

```properties
# Keycloak trust: which issuers the connector accepts tokens from
application.keycloak.base-url=http://localhost:8180
application.keycloak.audience=dsp-true-connector
application.keycloak.platform-realm=platform
application.keycloak.admin-realm=connector-admin

# The client (azp) a platform token must come from to be accepted on protocol routes
application.keycloak.platform-client-id=dsp-connector-backend

# The client (azp) a tenant or connector-admin token must come from (the UI's public client)
application.keycloak.ui-client-id=keycloak-api

# --- Backend service-account client of the platform realm: used by KeycloakAuthenticationService
# --- for connector-to-connector (M2M) protocol calls (ROLE_CONNECTOR), via client-credentials grant.
# --- Confidential client - requires a client secret. The token endpoint is not configured: it is
# --- read from the discovery document of the platform realm.
application.keycloak.backend.client-id=dsp-connector-backend
application.keycloak.backend.client-secret=dsp-connector-backend-secret
application.keycloak.backend.token-caching=true
```

| Property | Default | Meaning |
|----------|---------|---------|
| `application.keycloak.base-url` | none, required in `KEYCLOAK` mode | Absolute `http`/`https` URL of Keycloak (trailing slashes are stripped). Every issuer is `<base-url>/realms/<realm>`, so it must equal the scheme, host and port of the `iss` claim (see [Hostname / `iss` alignment](#hostname--iss-alignment)) |
| `application.keycloak.audience` | `dsp-true-connector` | Audience every accepted access token must carry in `aud`; tokens without it get 401 |
| `application.keycloak.platform-realm` | `platform` | Realm of the machine-to-machine client |
| `application.keycloak.admin-realm` | `connector-admin` | Realm of the super-admins |
| `application.keycloak.platform-client-id` | `dsp-connector-backend` | Client a `platform` token must come from (`azp` claim) to be accepted on protocol routes. Must not be blank. Set it to the `client-id` of the peer connectors' M2M client if they use a different one |
| `application.keycloak.ui-client-id` | `keycloak-api` | Client a tenant or `connector-admin` token must come from (`azp` claim) on `/api/**`: the public client the UI signs in with. Must not be blank |
| `application.keycloak.backend.client-id` | none | Client the connector uses to request its own outbound token from the `platform` realm |
| `application.keycloak.backend.client-secret` | none | Secret of that client. Deliver it through the environment, see [M2M Client Secret](#m2m-client-secret-delivery-and-rotation) |
| `application.keycloak.backend.token-caching` | `true` | Whether the outbound token is cached until shortly before it expires |
| `application.keycloak.discovery-connect-timeout` | `5s` | Connect timeout for discovery and JWKS requests (for their TLS trust see [Trusted Issuers](#trusted-issuers)) |
| `application.keycloak.discovery-read-timeout` | `10s` | Read timeout of discovery and JWKS requests. The whole request, body included, must finish within connect + read timeout (the call timeout of the Keycloak client) |

The connector fails to start in `KEYCLOAK` mode when `base-url` is missing or not an absolute
`http`/`https` URL, when a realm name does not match `^[A-Za-z0-9._-]+$`, or when `audience` is
blank, when `platform-client-id` or `ui-client-id` is blank, or a timeout is not positive.

Removed properties: the `token-url` setting of the backend client is no longer read (the connector takes
the token endpoint from the discovery document of the `platform` realm; delete the line from
existing deployments, it is ignored if present). The Spring resource-server `issuer-uri` and `jwk-set-uri` settings are no
longer read (the connector derives issuers from `base-url` and the realm names), and the `admin.realm`
and `admin.server-url` settings under `application.keycloak` are replaced by `base-url` and
`admin-realm`. `application.keycloak.user-management` and `application.keycloak.user-management-client-id`
belonged to the removed Keycloak user API and are ignored; delete them (see [User management](#user-management)).

Earlier versions also had `application.keycloak.login.*` properties for a connector-side login
proxy. They were removed and are ignored if still present; delete them from existing deployments.

### What Happens in Keycloak Mode

**Admin zone (`/api/**`)**:
- Requires `Authorization: Bearer <JWT>` with `ROLE_ADMIN` (tenant realm token) or `ROLE_SUPER_ADMIN`
  (`connector-admin` realm token), issued by Keycloak to the UI (see [Getting Tokens](#getting-tokens))
- Roles are read from the `realm_access.roles` claim through a per-realm allow-list: each issuer type
  may grant exactly one role, every other role is dropped (see [Trust scope per chain](#trust-scope-per-chain))
- The tenant comes from the `tenant_id` claim; a super-admin can act for one tenant with `X-Tenant-Id`
  (see [Tenant context and `X-Tenant-Id`](#tenant-context-and-x-tenant-id))
- There is no user API: `/api/v1/users/**` answers **404** for a super-admin (403 for any other
  token). Users are managed in Keycloak (see [User management](#user-management))
- `/api/v1/auth/*` and `PUT /api/v1/users/*/password` do not exist: without a token they return
  **401**, with a valid token **404** (auth routes) or **403** for `ROLE_ADMIN` (password route).
  People change their password in Keycloak
- Sign-in, refresh and logout events are recorded in Keycloak's own event log; the connector
  publishes no login audit events in this mode

**Protocol zone (`/connector/**`, `/catalog/**`, `/negotiations/**`, `/transfers/**`)**:
- Requires `Authorization: Bearer <JWT>` from the `platform` realm with `aud`, `azp` and the
  `CONNECTOR` role (see [Protocol Trust](#protocol-trust-in-keycloak-mode)); anything else gets 401
- On authentication failure, returns a DSP-compliant error JSON (see
  `DataspaceProtocolEndpointsExceptionHandler`)

**Internal zone (`/internal/**`)**: loopback calls of the connector to itself, see
[Internal Call Zone](#internal-call-zone-internal). Not reachable with Keycloak tokens.

**Outbound connector-to-connector requests**:
- `KeycloakAuthenticationService` requests a client-credentials token for the
  `application.keycloak.backend.*` client from the token endpoint of the `platform` realm and caches it
  in `M2mTokenCache` (see [Outbound Token](#outbound-connector-to-connector-token))

### Getting Tokens

The connector has no token route in this mode. The UI reads the tenant list from
`GET /public/tenant`, picks the realm of the chosen tenant (or `connector-admin` for super-admins)
and signs in to Keycloak with the authorization code flow + PKCE, using the realm's public
`keycloak-api` client. It refreshes tokens against Keycloak and logs out at Keycloak's end-session
endpoint (`/realms/<realm>/protocol/openid-connect/logout`).

> **Trust scope:** see [Trusted Issuers](#trusted-issuers). `/api/**` accepts tenant tokens
> (`ROLE_ADMIN`) and `connector-admin` tokens (`ROLE_SUPER_ADMIN`); a `platform` token is
> authenticated there but only holds `ROLE_CONNECTOR`, so it gets 403. Protocol routes accept
> `platform` tokens only.

For manual testing, the seed realms also allow the password grant on `keycloak-api`:

```bash
curl -X POST http://localhost:8180/realms/tenant-EngineeringA/protocol/openid-connect/token \
  -d grant_type=password -d client_id=keycloak-api \
  -d username=admin -d password=password
```

Use the returned `access_token` as: `Authorization: Bearer <access_token>`. Connector-to-connector
(M2M) tokens are acquired transparently by `KeycloakAuthenticationService` using the
`application.keycloak.backend.*` client - application code never requests these directly.

To call a protocol route by hand, request a token from the `platform` realm with the client
credentials grant (tenant tokens get 401 on protocol routes):

```bash
curl -X POST http://localhost:8180/realms/platform/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=dsp-connector-backend \
  -d client_secret=dsp-connector-backend-secret
```

The Postman collection (`Keycloak` folder) has one token request per realm and checks the
`tenant_id` claim of the tenant tokens.

#### Auth-mode-conditional beans

Beans that belong to one auth mode are switched on with a `Condition` from
`it.eng.tools.auth.condition`: `InternalAuthenticationModeCondition` (`AuthController`,
`InternalAuthServiceImpl`), `InternalOrDisabledAuthenticationModeCondition` (`UserAPIController`,
`UserService`) and `KeycloakAuthenticationModeCondition` (the Keycloak trust beans such as
`KeycloakTrustProperties`). `ConnectorSecurityConfig` keeps one `adminFilterChain` branch per mode. When
a controller is switched off for a mode, also remove its request matchers from that mode's branch;
otherwise a `permitAll()` or role rule keeps answering for a route that no longer exists.

### Trusted Issuers

`TrustedIssuerRegistry` decides which Keycloak issuers the connector trusts and keeps one token
decoder per issuer. The trusted issuers are:

- **Static:** `<base-url>/realms/<platform-realm>` and `<base-url>/realms/<admin-realm>`.
- **Dynamic:** `<base-url>/realms/<realm>` for the `realm` of every **enabled** tenant.

Rules the registry follows:

- **The unverified `iss` only selects.** The connector reads `iss` from the token before it checks
  the signature, and uses it only to pick a registered issuer: an exact match with a static issuer,
  or `<base-url>/realms/<segment>` where the segment passes the realm pattern (`^[A-Za-z0-9._-]+$`,
  not `.` or `..`, not a reserved name) and an enabled tenant holds exactly that realm. The value is
  never used as a URL. Tenant realms are names, never URLs.
- **Unknown issuers fail closed.** An unparsable, unsigned (`alg: none`) or issuer-less token, an
  unknown issuer, or an issuer type that the route does not accept gets 401 without any network
  request.
- **Discovery and JWKS per issuer.** On first use the connector fetches
  `<issuer>/.well-known/openid-configuration` (the URL is built from `base-url` and the realm name),
  requires its `issuer` to equal the expected one, and loads the signing keys from the `jwks_uri` of
  that document. Redirects are not followed, the discovery response is capped at 256 KiB and the JWKS
  response at 64 KiB. Each request, body included, must finish within connect + read timeout, and a
  request waits at most twice that (plus 0.5 s) for the keys of an issuer; a slower load counts as
  failed. Keys are not loaded at startup, so the connector starts while Keycloak is down.
- **TLS trust for discovery and JWKS.** These requests use an OkHttp client derived from the shared
  outbound `okHttpClient`, so they have its TLS trust. With `server.ssl.enabled=true` they trust the
  truststore of the `connector` SSL bundle (`spring.ssl.bundle.jks.connector.truststore.*`), with
  [OCSP checks](#ocsp-certificate-validation) as configured. An HTTPS Keycloak whose certificate comes
  from a private certificate authority works once that CA is in the connector truststore, and host
  names are verified. In this mode `base-url` must be `https`: plain `http` is refused. With `server.ssl.enabled=false`
  they trust all certificates and do not verify host names, like the other outbound clients in that
  mode, so an HTTPS Keycloak needs no certificate setup during development. That is not secure: anyone
  between the connector and Keycloak can serve their own signing keys and forge tokens (see the
  [HTTP mode warning](#tls-configuration)).
- **Signature, expiry, issuer.** The decoder accepts asymmetric algorithms only (RS, PS, ES) and
  checks the signature, `exp`, `nbf` and the exact `iss`.
- **Caching and rotation.** Decoders are cached per issuer (at most 1000, evicted after 1 hour
  without use). A JWK set is used for 5 minutes and refreshed in the background shortly before it
  expires. A token with an unknown `kid` triggers a refetch, rate-limited to about two requests per
  30 seconds per issuer, so rotated keys are picked up without a restart.
- **Failures are not cached for long.** When discovery or the JWKS cannot be loaded, the request
  gets 401, and the issuer is retried at most once every 5 seconds.
- **Tenant changes need no restart.** The tenant lookup runs on every request and its result is
  not cached. A tenant that is created, enabled, disabled, deleted or moved to another realm is
  trusted or untrusted on the next request; there is no staleness window beyond the request itself.
  Only the signing keys are cached.
- **Validator hook.** The registry applies an extra validator per issuer after signature
  validation (`IssuerTokenValidators`): the checks in the next section.

#### Trust scope per chain

Each chain accepts a fixed set of issuers. Within a chain, the **issuing realm decides which role may
count**: a role name alone is never trusted. `IssuerTokenValidators` applies one rule set per issuer
type after the signature check, and `KeycloakRealmRoleConverter` maps roles through a per-realm
allow-list. Anything that fails a check gets 401. The rationale is in
[D-ARC-006](decisions/architecture/D-ARC-006-per-realm-role-allow-list-and-per-tenant-realm-credentials.md).

| Chain | Trusted issuers |
|-------|-----------------|
| Protocol | `platform` only |
| Admin (`/api/**`, `/actuator/**`, `/env`) | tenant realms, `connector-admin` and `platform` |
| Internal | no Keycloak issuer, see [Internal Call Zone](#internal-call-zone-internal) |

Rules per issuer type (admin chain; the `platform` row is also the protocol chain rule):

| Issuer type | `aud` | `azp` | Allowed role (becomes) | `tenant_id` |
|-------------|-------|-------|------------------------|-------------|
| Tenant realm | contains `application.keycloak.audience` | `application.keycloak.ui-client-id` | `ADMIN` (`ROLE_ADMIN`) | required, equal to the `_id` of the tenant that owns the realm |
| `connector-admin` | contains `audience` | `ui-client-id` | `SUPER_ADMIN` (`ROLE_SUPER_ADMIN`) | must be absent |
| `platform` | contains `audience` | `application.keycloak.platform-client-id` | `CONNECTOR` (`ROLE_CONNECTOR`), required | must be absent |

The tenant a token is bound to comes from the registry, not from the token: the validator compares
`tenant_id` with the tenant whose `realm` produced the verified `iss`. A tenant A token therefore
cannot carry tenant B's id, and an empty `tenant_id` counts as missing. A tenant or
`connector-admin` token without its own role is still valid at this stage; authorization then denies
it with 403.

**Wrong-realm privileged role: 401 plus audit.** The privileged realm roles are `ADMIN`,
`SUPER_ADMIN`, `CONNECTOR` and `INTERNAL` (compared case-insensitively). A token that carries a
privileged role other than its realm's own (for example `SUPER_ADMIN` in a tenant realm, or `ADMIN`
in `platform`) is rejected as a whole with 401, and the role is not simply dropped: a realm that
issues it is misconfigured or compromised, so it is treated as tampering. The rejection publishes
a `SECURITY_REALM_ROLE_VIOLATION` audit event (see [Audit events](#audit-events)). Roles that are not
privileged (for example `offline_access`) are ignored.

**Allow-list converter.** `KeycloakRealmRoleConverter` has no generic `ROLE_<name>` mapping. The issuer
type, taken from the verified `iss`, selects the single role it may grant (`ADMIN`, `SUPER_ADMIN`
or `CONNECTOR`, matched exactly as seeded); all other roles are dropped. No Keycloak token ever
yields `ROLE_INTERNAL`, and tenant realms never yield `ROLE_CONNECTOR`.

**401 versus 403.**

| Outcome | Cause |
|---------|-------|
| 401 | No token, unknown or unsupported issuer for the route, bad signature, expired, wrong or missing `aud`, `azp` or `tenant_id`, `tenant_id` on a `connector-admin` or `platform` token, or a wrong-realm privileged role |
| 403 | The token is valid and authenticated but its authority does not allow the route: a `platform` token (`ROLE_CONNECTOR` only) on `/api/**`, a tenant `ADMIN` on a super-admin route (`/api/v1/tenants/**`, `/api/v1/users`, `/api/v1/properties/**`), a token without its realm's role, or `X-Tenant-Id` from a principal that may not send it |
| 400 | `X-Tenant-Id` names an unknown or disabled tenant (see below), or `GET /api/v1/users` has no tenant |

On the admin chain a `platform` token is authenticated and converted to `ROLE_CONNECTOR` only, so it
gets 403 on `/api/**`, `/actuator/**` and `/env`; `/actuator/health` stays `permitAll`. The Keycloak
filters are registered in the security chains only, not as global servlet filters. A rejected token is
logged at DEBUG with the exception class and error codes only, never the token or its claims; a
realm role violation additionally logs a WARN with the request path.

#### Tenant context and `X-Tenant-Id`

`ApiTenantContextFilter` runs on `/api/**` in **both** modes (`INTERNAL` and `KEYCLOAK`) and sets the
tenant for the request from the principal:

- **Own tenant.** The `tenant_id` claim of the token. `INTERNAL` login tokens and Keycloak tenant-realm
  tokens use the same claim name. (`org` and `given_name` or other profile claims are never used for the
  tenant.) A principal without a tenant, such as a super-admin, has no tenant context and sees all
  tenants.
- **`X-Tenant-Id` header.** Lets a super-admin act for one tenant:

| Sender | Result |
|--------|--------|
| Super-admin (`ROLE_SUPER_ADMIN`) with no tenant of its own, header names an existing, enabled tenant | The request runs in that tenant; a `TENANT_DELEGATION` audit event is published |
| Same super-admin, header names an unknown or disabled tenant | **400** |
| Any other principal that sends the header: a tenant `ADMIN`, a super-admin that has its own tenant, or a principal without `ROLE_SUPER_ADMIN` | **403**, nothing is delegated |
| `ROLE_CONNECTOR` (`platform` token) | No tenant, the header is ignored; the chain answers 403 |
| No header or a blank header | The principal's own tenant (none for a super-admin) |

These rules are identical in `INTERNAL` mode: a seeded tenant user that sends `X-Tenant-Id` now gets
403 (before, the header was ignored). `/internal/**` never reads the header.

#### User management

In `KEYCLOAK` mode the connector does not create, list or change users. `UserAPIController` and
`UserService` exist only in `INTERNAL` and `DISABLED` mode, and there is no Keycloak counterpart:
`/api/v1/users/**` answers 404 for a super-admin and 403 for any other token. Users are managed in
Keycloak itself, through the Admin Console, the Admin REST API or a realm import, and the connector
only validates the tokens they get.

Which realm a user belongs in:

| Who | Realm | Rule |
|-----|-------|------|
| Super-admins | `connector-admin` (`application.keycloak.admin-realm`) | Realm role `SUPER_ADMIN`, **no** `tenant_id` claim. A super-admin picks a tenant per request with `X-Tenant-Id` |
| Tenant users | The tenant's own realm (the `realm` field of the tenant, e.g. `tenant-EngineeringA`) | Realm role `ADMIN` and a `tenant_id` claim equal to the tenant `_id` (e.g. `engineering-a`, not the realm name). A privileged role the realm may not grant (such as `SUPER_ADMIN`) gives 401 and a `SECURITY_REALM_ROLE_VIOLATION` audit event |
| Machine-to-machine | `platform` (`application.keycloak.platform-realm`) | Client `dsp-connector-backend` with realm role `CONNECTOR`. Not a human user |

A tenant realm can never be `master`, the platform realm or the admin realm (see
[Seed Realms and Tenant Identity](#seed-realms-and-tenant-identity)), so a super-admin created in a
tenant realm does not work. Create super-admins in `connector-admin` only.

Earlier builds of this branch had a read-only Keycloak user listing (`GET /api/v1/users` with
`X-Tenant-Id`) backed by a per-tenant `connector-user-reader` client whose secret was stored on the
tenant (`PUT /api/v1/tenants/{id}/realm-credentials`). It was removed together with its properties,
the tenant `clientSecret` field and the seed clients.

#### Audit events

Two events cover the multi-issuer rules (both are `AuditEventType` values; queries and metrics see them
like any other audit event). Neither holds a token, a secret or other claims.

| Event | When | Details |
|-------|------|---------|
| `SECURITY_REALM_ROLE_VIOLATION` | A token with a valid signature carried a privileged role its realm may not grant (401) | `realm`, `issuerType`, `subject`, `path` (bounded, control characters replaced) |
| `TENANT_DELEGATION` | A super-admin request was accepted with `X-Tenant-Id` | `principal`, `targetTenantId`; the event's tenant is the target tenant |

A request with a forged signature, a wrong `aud`, or an unknown issuer is rejected without an event:
only a verified token can show that a realm granted a role it must not.

#### Protocol trust in Keycloak mode

Protocol routes (`/connector/**`, `/catalog/**`, `/negotiations/**`, `/transfers/**`) are called by
other connectors, which authenticate as the machine-to-machine client of the `platform` realm. The
protocol chain therefore uses its own filter (`keycloakProtocolAuthenticationFilter`) and decoder
that accept the `platform` issuer only.

| Token | Protocol route |
|-------|----------------|
| `platform`, `aud` ok, `azp` = `platform-client-id`, role `CONNECTOR` | accepted, `ROLE_CONNECTOR` |
| `platform`, wrong or missing `aud`, `azp` or `CONNECTOR` | 401 |
| tenant realm token (including a tenant `ADMIN`) | 401 |
| `connector-admin` token (super-admin) | 401 |
| no token, unknown issuer, invalid signature, expired | 401 |

| Token | Admin route (`/api/**`, `/actuator/**`, `/env`) |
|-------|--------------------------------------------------|
| tenant `ADMIN`, with `aud`, `azp` `keycloak-api` and a matching `tenant_id` | accepted, `ROLE_ADMIN` |
| `connector-admin` `SUPER_ADMIN`, with `aud`, `azp` `keycloak-api`, no `tenant_id` | accepted, `ROLE_SUPER_ADMIN` |
| `platform` | 403 (`ROLE_CONNECTOR` only) |
| `platform` token with another privileged role, or a tenant token with `SUPER_ADMIN` | 401 and a `SECURITY_REALM_ROLE_VIOLATION` audit event |

The `ROLE_CONNECTOR` is still not bound to a tenant, as described under
[Authentication Modes](#authentication-modes). The protocol trust is covered by
`KeycloakProtocolTrustIT` and `KeycloakConnectorToConnectorIT`.

#### Outbound connector-to-connector token

`KeycloakAuthenticationService` obtains the Bearer token for protocol calls the connector makes to
other connectors.

- **Token endpoint from discovery.** The endpoint is the `token_endpoint` of
  `<base-url>/realms/<platform-realm>/.well-known/openid-configuration`. It is resolved on first use
  and again after a token request that could not reach it (a transport error, HTTP 404 or 405, or a 5xx).
  An OAuth client error such as a wrong secret (400 or 401) keeps it, and a missing client id or secret
  fails before discovery is fetched. It is not configured; the former `token-url` setting of the
  backend client was removed and leftover lines are ignored.
- **Cache.** The token is cached in `M2mTokenCache` under
  `keycloak-m2m:<client-id>@<token-endpoint>` and renewed 30 seconds before `exp`. The cache is
  separate from the one of the `INTERNAL` mode token (`connector-m2m`). After a peer answers a call
  with 401, the cached token is evicted (`CredentialUtils.invalidateCachedCredentials()`) and the next
  call requests a new one.
- **Failure.** When the endpoint cannot be resolved or the token request fails, the service throws
  `M2mTokenUnavailableException`, logs an ERROR and the outgoing protocol call is **not sent**; the
  caller gets an error response. The connector never sends the call without an `Authorization` header.
  The log line has the client id, the token endpoint, the HTTP status and the OAuth `error` and
  `error_description`. It never has the secret, the response body or a token.
- **No redirects.** The token request, like every call of the shared `okHttpClient`, does not follow
  redirects. A 3xx from the token endpoint is a failed request, so the client secret is never resent
  to another host.

#### M2M client secret delivery and rotation

The secret of the `application.keycloak.backend.client-id` client is a credential. Keep it out of the
property files in the image and out of the repository (the dev value
`dsp-connector-backend-secret` in the shipped files is for local use only).

- **Delivery.** Keep the secret in a secrets manager or a Kubernetes Secret and expose it to the
  connector as an environment variable. Spring's relaxed binding maps
  `APPLICATION_KEYCLOAK_BACKEND_CLIENT_SECRET` to `application.keycloak.backend.client-secret`, and
  `APPLICATION_KEYCLOAK_BACKEND_CLIENT_ID` to `client-id`. In Kubernetes, reference the Secret with
  `valueFrom.secretKeyRef` on the container.
- **Rotation.** The Keycloak operator regenerates the secret in the `platform` realm (client
  credentials tab) and updates the secrets manager or Secret. The connector does not rotate it.
  The connector reads the value at startup, so a new value reaches it with a restart (a rolling
  restart with several replicas).
- **What happens meanwhile.** A token issued before the rotation stays valid until it expires, so it
  is used until 30 seconds before `exp` or until a peer answers 401 and the cache is evicted. After
  that the connector asks Keycloak for a new token. While its configured secret differs from the one in
  Keycloak, Keycloak refuses the request, outbound protocol calls fail with the error described below,
  and inbound calls keep working. The connector needs no restart to recover once the configured secret
  and the one in Keycloak match again (for example when the operator sets the old value back): the
  next call requests a new token. This is covered by `KeycloakSecretRotationIT`.
- **What to check when outbound calls fail.** Read the ERROR log of `KeycloakAuthenticationService`:

| Log shows | Meaning and check |
|-----------|-------------------|
| `HTTP 401, error=unauthorized_client` (Keycloak: "Invalid client or Invalid client credentials") | The configured secret or client id does not match the client in the `platform` realm. Compare the delivered secret with the one in Keycloak |
| `Token endpoint of realm platform could not be resolved` | The discovery document could not be fetched. Check `application.keycloak.base-url`, `platform-realm`, `KC_HOSTNAME` and that Keycloak is reachable from the connector (see [Hostname / `iss` alignment](#hostname--iss-alignment)) |
| `Token request not sent: ... client-id or client-secret is not configured` | The environment variables are not set or empty |
| `HTTP 4xx` with another `error` | Read `error` and `error_description` in the log line |
| the peer answers 401 to a call that was sent | The peer's `platform-client-id`, `audience` or `CONNECTOR` role does not match this connector's token |

After a failed request the next call requests a new token, and resolves the token endpoint again when
the endpoint itself failed (transport error, 404, 405, 5xx), so nothing else has to be reset.

#### Tenant `realm` rules

`POST /api/v1/tenants` and `PUT /api/v1/tenants/{id}` validate `realm` (`null` is allowed: the tenant
then accepts no Keycloak tokens):

| Rule | Result |
|------|--------|
| Matches `^[A-Za-z0-9._-]+$`, has at most 255 characters and is not `.` or `..` | otherwise **400** |
| Is not `master`, the platform realm or the admin realm (compared case-insensitively) | otherwise **400** |
| Is not used by another tenant, enabled or disabled (compared case-sensitively) | otherwise **409** |

A tenant may keep its own current realm on update. `InitialDataLoader` bypasses these checks; the
seeds are asserted by `InitialDataSeedConsistencyTest`.

#### Hostname / `iss` alignment

Keycloak writes into `iss` the hostname it was started with, and the connector expects
`<base-url>/realms/<realm>`. If they differ, the discovery document's `issuer` does not match, the
signing keys are not loaded, and every token gets 401. So `application.keycloak.base-url` must have the
same scheme, host and port as the `iss` of the tokens.

- **Docker Compose** (`connector/docker-compose.yml`): Keycloak runs with
  `KC_HOSTNAME=http://localhost:8180` and `base-url` is `http://localhost:8180`, so browser and
  connector agree. That file has no connector service, so no backchannel option is needed there; a
  connector started on the host reaches Keycloak on the same URL.
- **k3s / Kubernetes:** the connector often reaches Keycloak through a cluster DNS name
  (`http://keycloak.<namespace>.svc:8080`) while browsers use the ingress URL. Tokens carry the
  public URL, so set `KC_HOSTNAME` to the public URL and `base-url` to the same value. The connector
  fetches the discovery document from `base-url` and the keys from the `jwks_uri` in that document, so
  both must be reachable from the connector. Either enable Keycloak's dynamic backchannel
  (`KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true`, which needs `KC_HOSTNAME` as a full URL), so that the
  endpoint URLs in the document use the host the connector called while `issuer` stays the public URL,
  or route the public URL to Keycloak from inside the cluster too. Do not point `base-url` at the
  cluster name while `iss` holds the ingress URL.

#### Troubleshooting

| Symptom | Cause |
|---------|-------|
| 401 on every request and the connector log has `Discovery document issuer does not match expected issuer` (logged as `Loading signing keys of issuer ... failed`) | `KC_HOSTNAME` and `application.keycloak.base-url` disagree; align them as described above |
| 401 for a valid token of a tenant realm, no network request in the log | No enabled tenant has that realm in its `realm` field, or the token's `iss` host differs from `base-url` |
| 401 for a tenant or `connector-admin` token on a protocol route | Expected: protocol routes accept `platform` tokens only, see [Protocol trust](#protocol-trust-in-keycloak-mode) |
| 401 for a `platform` token on a protocol route | The token lacks `aud` `dsp-true-connector`, its `azp` differs from `application.keycloak.platform-client-id`, or the service account lacks the `CONNECTOR` role |
| 403 for a `platform` token on `/api/**` | Expected: it only holds `ROLE_CONNECTOR` |
| 401 for a `connector-admin` token on `/api/**` | It has a `tenant_id` claim, lacks `aud` or `azp` `keycloak-api`, or carries a privileged role other than `SUPER_ADMIN` |
| 401 for a tenant token on `/api/**` | `tenant_id` is missing or differs from the tenant that owns the realm, `azp` is not `ui-client-id`, or the token has no `aud` |
| `SECURITY_REALM_ROLE_VIOLATION` in the audit log | A token carried a role its realm may not grant; check the roles of the realm and the service account |
| 400 on `/api/**` with `X-Tenant-Id` | The tenant does not exist or is disabled |
| 403 on `/api/**` with `X-Tenant-Id` | The sender is not a super-admin without a tenant |
| 404 on `GET /api/v1/users` in `KEYCLOAK` mode | Expected: the connector has no user API in this mode; manage users in Keycloak (see [User management](#user-management)) |
| WARN `Bearer token could not be verified` | The signing keys or the decoder of a trusted issuer were unavailable (for example Keycloak unreachable); the request got 401 |
| Outbound protocol call not sent, ERROR from `KeycloakAuthenticationService` | See [M2M client secret](#m2m-client-secret-delivery-and-rotation) |
| 401 for a tenant token, log has `Token rejected by issuer validation` | The token has no `aud` `dsp-true-connector` (the client lacks the `connector-audience` scope), a wrong `azp` or `tenant_id`, or it carries a privileged realm role its realm may not grant (the audit event says which realm) |
| 401 right after Keycloak was unreachable | A failed key load is retried after 5 seconds |

### Seed Realms and Tenant Identity

Realm exports in `ci/docker/keycloak_resources/` are imported on first start of the Keycloak in `connector/docker-compose.yml` (profile `keycloak`):

| Realm | Purpose | User(s) | `tenant_id` |
|-------|---------|---------|-------------|
| `platform` | Machine-to-machine client `dsp-connector-backend` (client credentials) | service account | none |
| `connector-admin` | Super-admins; public client `keycloak-api` | `superadmin@mail.com` / `password` | none |
| `tenant-EngineeringA` | Tenant `engineering-a` | `admin` (`admin@mail.com`) / `password` | `engineering-a` |
| `tenant-EngineeringB` | Tenant `engineering-b` | `adminB` (`adminB@mail.com`, stored by Keycloak as `adminb`) / `password` | `engineering-b` |
| `tenant-acme` | Tenant `acme` (demo only, seeded in `connector/src/main/resources/initial_data.json`, never used in tests) | `coyote`, `roadrunner` | `acme` |

The `tenant_id` claim of a tenant realm user must equal the `_id` of the tenant whose `realm` field names that realm. It is never the realm name (`tenant-EngineeringA` is the realm, `engineering-a` is the tenant id). The seeded tenants carry the matching `realm` values; `InitialDataLoader` does not run the realm validation, and `InitialDataSeedConsistencyTest` asserts the seeds are consistent with the realm files (a seed file may hold several tenants, each bound to a seeded realm). Every tenant realm needs both halves: a Mongo tenant whose `realm` names it (otherwise the realm is not trusted and its tokens get 401) and users whose `tenant_id` is that tenant's `_id` (otherwise the tenant binding gives 401).
To create the same realms in a Keycloak that is already running (no import), run `scripts/keycloak/bootstrap-keycloak.sh [--url <keycloak-url>] [--public-url <browser-url>]`. It uses the Admin REST API and is safe to re-run. For a new tenant, run `scripts/keycloak/create-tenant-realm.sh <tenant> <display-name> [tenant-name]`, then `scripts/keycloak/create-user.sh <realm> <username> <email> <password> ADMIN --tenant-id <tenant-id>`. The tenant still needs its Mongo tenant record (see above). See [keycloak-scripts.md](keycloak-scripts.md) for all options.

Seed data is not a migration. On every start the connector upserts each `initial_data*.json` document by `_id`, and Keycloak (`--import-realm`) imports only realms that do not exist yet. On a deployment seeded before the rename, this means:

- the seeded documents (same `_id`) are overwritten and now belong to `engineering-a` (or `engineering-b`), and the new tenant is added next to the old one, which stays enabled;
- data created by users under the old tenant id stays under that id;
- the old `tenant-Engineering` realm stays in Keycloak next to the new realms.

Re-seed such a deployment from empty databases, or move the old tenant's data to the new tenant id and remove the old tenant and realm by hand.

#### Changing seed realms

1. Either edit the realm JSON directly, or import the file into a Keycloak 26.0.0 instance, make the change through the admin console or admin API, and export it with `kc.sh export --realm <name> --users realm_file --file <name>-realm.json`.
2. If exported, remove the `org.keycloak.keys.KeyProvider` component from the file so signing keys are not committed; Keycloak generates new keys on import.
3. Keep the realm `frontendUrl` attribute at `http://localhost:8180`; the integration test base (`BaseKeycloakIntegrationTest`) strips it on import.
4. Keep `tenant_id` of tenant realm users equal to the matching Mongo tenant `_id` (see above).
5. For a new tenant realm, seed a tenant with that `realm` in the `initial_data*.json` file of the connector that uses it, and add the realm file to `TENANT_REALM_FILES` in `InitialDataSeedConsistencyTest`.

## Internal Authentication Mode (`INTERNAL`)

Use when Keycloak is not available. Users are stored in MongoDB and managed through
`/api/v1/users`. Both the admin zone and the protocol zone authenticate with a self-signed JWT
obtained via the [internal `/api/v1/auth/*` contract](#internal-login-contract-apiv1auth).

### Enabling Internal Mode

```properties
application.auth.provider=INTERNAL
```

### Configuration Properties

```properties
# HMAC-SHA256 secret used to sign/verify all INTERNAL-mode JWTs (admin, protocol, and M2M).
# Can be overridden via APPLICATION_SECURITY_JWT_SECRET environment variable. Must be >= 32 bytes.
application.security.jwt.secret=${APPLICATION_SECURITY_JWT_SECRET:connector-jwt-dev-secret-change-in-prod-min-32-bytes}
application.security.jwt.access-expiration-ms=900000
application.security.jwt.refresh-expiration-ms=604800000
```

> **`application.security.jwt.secret` must be identical across every connector instance that
> needs to trust each other's tokens** (e.g. connector A and connector B in a docker-compose or
> Kubernetes deployment). A mismatched secret causes every cross-connector protocol call to fail
> JWT signature validation with a 401.

### What Happens in Internal Mode

- Both admin and protocol zones require `Authorization: Bearer <JWT>`, obtained via
  `POST /api/v1/auth/login`
- `UserDetailsService` is backed by MongoDB (`UserService`)
- `/api/v1/users` endpoints are **active** — users created here are valid login credentials
- Initial users and data are seeded from `initial_data.json` on startup
- On authentication failure at protocol endpoints, returns a DSP-compliant error JSON
- Refresh tokens are opaque ids tracked server-side by `RefreshTokenStore` and rotated on every
  `POST /api/v1/auth/refresh` call; `POST /api/v1/auth/logout` revokes them idempotently

### Management Endpoint Access Control

The following endpoints are restricted to `ROLE_SUPER_ADMIN` in both `INTERNAL` and `KEYCLOAK` modes, with self-service exceptions for `ROLE_ADMIN`:

| Endpoint | Required role |
|----------|---------------|
| `GET /api/v1/users/me` | `ROLE_ADMIN`, `ROLE_SUPER_ADMIN` — self-service profile retrieval |
| `PUT /api/v1/users/*/update` | `ROLE_ADMIN`, `ROLE_SUPER_ADMIN` — own account update (service layer enforces ownership) |
| `PUT /api/v1/users/*/password` | `ROLE_ADMIN`, `ROLE_SUPER_ADMIN` — own password change (service layer enforces ownership) |
| `* /api/v1/users/**` (all other) | `ROLE_SUPER_ADMIN` |
| `/api/v1/tenants/**` | `ROLE_SUPER_ADMIN` |
| `/api/v1/properties/**` | `ROLE_SUPER_ADMIN` |

All other `/api/**` endpoints require at minimum `ROLE_ADMIN`.

**Convention**: All role names in `ConnectorSecurityConfig` must be derived from the `it.eng.connector.model.Role` enum directly (e.g., `Role.SUPER_ADMIN.name()`, `Role.ADMIN.authorityName()`). Hardcoded role strings are not used.

**DISABLED mode**: The `DISABLED` authentication mode permits all requests including management endpoints. It is intended only for local development and must **not** be used in production.

### Tenant S3 credential rotation guidance

`PUT /api/v1/tenants/{id}` can rotate a tenant's S3 credentials (or migrate its bucket) when
called by a `ROLE_SUPER_ADMIN` user with:

- `bucketName` + `accessKey` + `secretKey`
- and optionally `verifyConnection=true`

For production operations, prefer `verifyConnection=true` so the connector validates the
candidate credential set against the target bucket before persisting it. A failed verification
returns HTTP 400 and leaves the existing tenant/bucket credential state unchanged.

When migration moves a tenant to a new bucket, the connector intentionally does not delete data
from the previous bucket automatically. Perform old-bucket cleanup as a separate, explicit
operator step after confirming that no active transfers or artifact reads still depend on it.

### User Model (Internal Mode)

Two user roles are seeded in `initial_data.json`; the connector-to-connector role (`ROLE_CONNECTOR`) has no user account:

| Role | Email | `tenantId` | Purpose |
|------|-------|-----------|---------|
| `ROLE_SUPER_ADMIN` | `superadmin@mail.com` | `null` | Manages tenants and cross-tenant operations. No data scope restriction — all tenant data is visible. |
| `ROLE_ADMIN` | `admin@mail.com` | per-tenant (e.g., `engineering-a`) | Per-tenant admin. Manages catalog, dataset, negotiation, and transfer data for their assigned tenant only. May also call `GET /api/v1/users/me`, `PUT /api/v1/users/{id}/update`, and `PUT /api/v1/users/{id}/password` for their own account. |
| `ROLE_CONNECTOR` | none (no user) | none | Authority of the locally minted protocol token that authenticates DSP protocol calls (connector-to-connector). See [Connector-to-connector credentials](#connector-to-connector-credentials-m2m). |

The seeded connector user is gone. Each tenant should have its own `ROLE_ADMIN` user with `tenantId` set to that tenant's ID. Multiple tenants cannot share the same email address. `InitialDataLoader` upserts the seed documents by `_id` on start and never deletes, so a database created by an earlier version keeps its old connector user until you remove it by hand; the connector no longer uses it.

### Connector-to-connector credentials (M2M)

Connector-to-connector calls (for example provider connector to consumer connector protocol
callbacks) are authenticated with a protocol token that the connector mints locally. There is no login
and no user lookup: `InternalProtocolTokenIssuer` (`tools`, `INTERNAL` mode only) implements
`ConnectorCredentialProvider` and asks `JwtService.issueProtocolToken()` for a token;
`CredentialUtils.getConnectorCredentials()` uses it.

- **Key.** HMAC-SHA256 (HS256) with `application.security.jwt.secret`, the same secret that signs admin
  tokens. A receiving connector verifies the token with its own copy of the secret, so both connectors must
  be configured with the same value.
- **Claims.** `sub` `connector`, `realm_access.roles` `["CONNECTOR"]`, `iat`, `exp` and `jti`. There is no
  `email`, `tenant_id` or `token_type` claim and no refresh token. `exp` is `iat` plus the access TTL
  (`application.security.jwt.access-expiration-ms`).
- **Receiver.** The `INTERNAL` protocol chain requires `ROLE_CONNECTOR` and nothing else (no marker claim).
  `InternalJwtAuthenticationFilter` uses `email` as principal name, or `sub` when there is no `email`. The
  token gets 403 on `/api/**` and 401 on `/internal/**`.
- **Cache and retry.** The token goes through `M2mTokenCache` in the `connector-m2m` slot, which refreshes
  it shortly before expiry. `OkHttpRestClient` retries exactly once on an HTTP 401 response, invalidating
  the cached token first. This keeps M2M calls resilient to a secret rotation or a stale cache entry
  without a restart.
- **Audit.** `M2M_TOKEN_ISSUED` (detail `subject`) for every minted token; `M2M_TOKEN_ISSUE_FAILED` only
  when signing fails. Tokens and the secret are never logged.

Calls the connector makes to itself do not use this path. They go to `/internal/**`, see the next
section.

**Configuration:**

```properties
# APPLICATION_SECURITY_JWT_SECRET env var; never commit a real production secret here.
application.security.jwt.secret=${APPLICATION_SECURITY_JWT_SECRET:connector-jwt-dev-secret-change-in-prod-min-32-bytes}
```

> - `application.security.jwt.secret` is the HMAC key that **signs and verifies every JWT** issued
>   in INTERNAL mode (admin login and the connector-to-connector token above). It
>   must be identical across every connector instance that needs to validate each other's tokens.

In `KEYCLOAK` mode this component is not active; `CredentialUtils` uses
`KeycloakAuthenticationService`, see [Outbound Token](#outbound-connector-to-connector-token).

### Protocol token vs internal-call token

`INTERNAL` mode has two machine tokens. They look alike but must not be confused:

| | Protocol token | Internal-call token |
|---|---|---|
| Issuer | `InternalProtocolTokenIssuer` / `JwtService.issueProtocolToken()` | `InternalCallTokenIssuer` ([D-ARC-005](decisions/architecture/D-ARC-005-internal-call-zone-with-in-memory-key.md)) |
| Key | Configured secret `application.security.jwt.secret`, shared between connectors | Per-process key generated at startup, memory only |
| Authority | `ROLE_CONNECTOR` | `ROLE_INTERNAL` |
| Audience | A peer connector, on the protocol routes | The connector itself, on `/internal/**` |
| Tenant | None | `tenant_id` claim when a tenant context exists |
| Lifetime | Access TTL (`access-expiration-ms`) | 60 seconds |
| Caching | Cached in `M2mTokenCache` (`connector-m2m`); a peer 401 evicts it | New token per call, never cached |
| Reach | Any peer holding the same secret | Loopback connections only |

Neither token is accepted on the other's chain, and neither reaches `/api/**` (protocol token: 403;
internal-call token: 401).

### Internal Call Zone (`/internal`)

The connector calls a few of its own REST endpoints over HTTP on `localhost`: offer validation during
a provider-side contract request, the dataset formats and artifact lookups during a transfer, and
agreement enforcement. These calls go to `/internal/**`, a zone with its own security chain
(`@Order(0)`) that works the same in `INTERNAL` and `KEYCLOAK` mode. The decision is recorded in
[D-ARC-005](decisions/architecture/D-ARC-005-internal-call-zone-with-in-memory-key.md).

- **Token.** `InternalCallTokenIssuer` (`tools`) creates a new token for every call: HMAC-SHA256 with a
  256-bit key from `SecureRandom` generated at startup and held in memory only. Claims: `sub`
  `internal-call`, `iss` `urn:true-connector:internal`, `iat`, `exp` 60 seconds later, `roles`
  `["ROLE_INTERNAL"]`, and `tenant_id` when a tenant context exists. There is no cache, no property and
  no secret to provision. Only the process that created the token can verify it.
- **Chain.** The chain verifies signature, `iss` and `exp` (5 seconds of skew) with that key only. A
  token that is not an internal-call token (a Keycloak or user token) gets 401. A valid token without
  `ROLE_INTERNAL` gets 403. A request that does not come over a loopback connection gets 403: both the TCP
  peer (`getRemoteAddr()`) and the local socket address (`getLocalAddr()`) must be `127.0.0.1` or `::1`, so
  the check holds even when a forward-headers strategy (auto-enabled on Kubernetes) rewrites the remote
  address from `X-Forwarded-For`. In `DISABLED` mode the chain is `permitAll()`.
- **Tenant.** `InternalTenantContextFilter` sets the tenant from the token's `tenant_id`. It never reads
  `X-Tenant-Id`; that header is read on `/api/**` only (see
  [multi-tenant-technical.md](multi-tenant-technical.md#internal-calls)).
- **Routes.** `POST /internal/v1/offers/validate`, `POST /internal/v1/agreements/{id}/enforce`,
  `GET /internal/v1/datasets/{id}/formats` and `GET /internal/v1/datasets/{id}/artifact`. The controllers
  are `@Hidden`, so the routes are not in the OpenAPI documents. `POST /api/v1/offers/validate` and
  `POST /api/v1/agreements/{id}/enforce` no longer exist (404); the two dataset routes also remain under
  `/api/v1/datasets` for the UI.
- **Replaced.** The former internal service token (`InternalServiceTokenIssuer`,
  `CredentialUtils.getAPICredentials()`, `InternalServiceAuthenticationProvider`) was removed.
- **Reserved tenant ID.** `internal` (any case) cannot be a tenant ID: the chain matches `/internal/**`, so
  that tenant's protocol paths would land in it. Creating such a tenant is rejected with 400.

Tests: `InternalCallTokenIssuerTest`, `InternalCallSecurityIT`, `KeycloakInternalCallSecurityIT`,
`InternalConnectorToConnectorIT`.

### Password Strength Requirements

```properties
application.password.validator.minLength=8
application.password.validator.maxLength=16
application.password.validator.minLowerCase=1
application.password.validator.minUpperCase=1
application.password.validator.minDigit=1
application.password.validator.minSpecial=1
```

---

## Disabled Mode (`DISABLED`)

All endpoints are open with no authentication. Intended for local development only.

```properties
application.auth.provider=DISABLED
```

- All filter chains use `permitAll()`
- `/api/v1/users` endpoints remain active
- No JWT validation, no authentication challenge of any kind
- **Do not use in production**

---

## DCP Authentication (Future — Stub)

When `application.auth.dcp.enabled=true`, the protocol filter chain replaces the provider's
authentication with a `DcpAuthenticationFilter`. The current implementation is a pass-through stub
that will be filled in with Decentralized Claims Protocol JWT validation logic.

This flag is independent of `application.auth.provider` — it can be combined with `KEYCLOAK` or
`INTERNAL` (admin zone always uses the provider, only the protocol zone switches to DCP).

---

## OCSP Certificate Validation

For more information how to verify OCSP certificate and generate new ones, revoke and invalidate, please check following [link.](ocsp/OCSP_GUIDE.md)

Following set of properties will configure OCSP validation for TLS certificate:

```
# OCSP Validation Configuration
# Enable or disable OCSP validation
application.ocsp.validation.enabled=false
# Soft-fail mode: if true, allows connections when OCSP validation fails
# If false, connections will be rejected when OCSP validation fails
application.ocsp.validation.soft-fail=true
# Default cache duration in minutes for OCSP responses without nextUpdate field
application.ocsp.validation.default-cache-duration-minutes=60
# Timeout in seconds for OCSP responder connections
application.ocsp.validation.timeout-seconds=10
```

Whether outbound clients trust all certificates depends on `server.ssl.enabled`, not on OCSP: with
`server.ssl.enabled=false` they accept ALL certificates (see the [HTTP mode warning](#tls-configuration)).
With `server.ssl.enabled=true` they validate certificates against the connector truststore, and
OCSP adds revocation checks on top. 

To add revocation checks to the TLS validation of `server.ssl.enabled=true`, set

```
application.ocsp.validation.enabled=true
```

The *OcspX509TrustManager* then validates the certificate chain against the provided truststore
(PKIX) and performs the OCSP check. Host names are verified with `server.ssl.enabled=true` whether
OCSP is enabled or not.
 
If certificate does not have 

```
Authority Information Access [1]: 
    Access Method: OCSP (1.3.6.1.5.5.7.48.1) 
    Access Location:         URI: http://ocsp-server:8888 

```

then OCSP validation will be skipped. If URL is provided, there must exists at least 2 certificates in chain, for validation to be performed. Otherwise it will be skipped.

To perform strict OCSP validation set following property to 

```
application.ocsp.validation.soft-fail=false
```
