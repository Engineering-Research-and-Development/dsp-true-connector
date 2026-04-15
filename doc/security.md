# Security

## Overview

The DSP True Connector supports multiple authentication and security mechanisms:

1. **TLS/SSL** - Transport layer security for encrypted communication
2. **Keycloak OAuth2/OIDC** - Token-based authentication for production use (`KEYCLOAK` mode)
3. **Basic Auth** - Username/password backed by MongoDB (`BASIC` mode)
4. **Disabled** - All endpoints open, for local development only (`DISABLED` mode)
5. **DCP** - Decentralized Claims Protocol for protocol endpoints (stub, future integration)
6. **OCSP** - Certificate validation and revocation checking

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

---

## Authentication Modes

The connector uses a single unified security configuration (`ConnectorSecurityConfig`) with three
ordered Spring Security filter chains — one for admin endpoints, one for protocol endpoints, and
one default chain.  Authentication behaviour is controlled by two properties:

```properties
# Primary authentication provider: KEYCLOAK | BASIC | DISABLED
application.auth.provider=KEYCLOAK

# Optional: route protocol endpoints through DCP instead of the provider above.
# Cannot be combined with provider=DISABLED.
# application.auth.dcp.enabled=false
```

### Authentication Matrix

| `auth.provider` | `dcp.enabled` | `/api/**` `/actuator/**` | Protocol endpoints¹ |
|-----------------|---------------|--------------------------|----------------------|
| `KEYCLOAK`      | `false`       | Keycloak JWT → `ROLE_ADMIN` | Keycloak JWT → `ROLE_CONNECTOR` |
| `KEYCLOAK`      | `true`        | Keycloak JWT → `ROLE_ADMIN` | DCP → `ROLE_CONNECTOR` |
| `BASIC`         | `false`       | HTTP Basic → `ROLE_ADMIN` | HTTP Basic → `ROLE_CONNECTOR` |
| `BASIC`         | `true`        | HTTP Basic → `ROLE_ADMIN` | DCP → `ROLE_CONNECTOR` |
| `DISABLED`      | `false`       | `permitAll()` | `permitAll()` |
| `DISABLED`      | `true`        | ❌ startup error | ❌ startup error |

¹ Protocol endpoints: `/connector/**`, `/catalog/**`, `/negotiations/**`, `/transfers/**`

> **Note:** `provider=DISABLED` combined with `dcp.enabled=true` is explicitly rejected at startup
> with an `IllegalStateException`.

---

## Keycloak Authentication Mode (`KEYCLOAK`)

The recommended mode for production. The connector acts as an OAuth2/OIDC resource server,
validating JWTs issued by Keycloak.

### Enabling Keycloak

```properties
application.auth.provider=KEYCLOAK
```

### Configuration Properties

```properties
# JWT validation — Keycloak as resource server
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/dsp-connector
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8180/realms/dsp-connector/protocol/openid-connect/certs

# Outbound authentication (connector-to-connector calls)
keycloak.auth-server-url=http://localhost:8180
keycloak.realm=dsp-connector
keycloak.resource=dsp-connector-consumer-backend
keycloak.credentials.secret=dsp-connector-consumer-secret
```

### What Happens in Keycloak Mode

**Admin zone (`/api/**`)**:
- Requires `Authorization: Bearer <token>` with `ROLE_ADMIN`
- Roles extracted from `realm_access.roles` claim in the JWT
- `/api/v1/users` returns **404** — user management is handled in Keycloak Admin Console

**Protocol zone (`/connector/**`, `/catalog/**`, `/negotiations/**`, `/transfers/**`)**:
- Requires `Authorization: Bearer <token>` with `ROLE_CONNECTOR`
- On authentication failure, returns a DSP-compliant error JSON (see
  `DataspaceProtocolEndpointsExceptionHandler`)

**Outbound requests**:
- `AuthenticationCache` acquires and caches a client-credentials token for connector-to-connector calls

### Getting Tokens

**Password grant (user login)**:
```bash
curl -X POST http://localhost:8180/realms/dsp-connector/protocol/openid-connect/token \
  -d "client_id=dsp-connector-ui" \
  -d "username=admin@test.com" \
  -d "password=admin123" \
  -d "grant_type=password"
```

**Client credentials (service account)**:
```bash
# Consumer
curl -X POST http://localhost:8180/realms/dsp-connector/protocol/openid-connect/token \
  -d "client_id=dsp-connector-consumer-backend" \
  -d "client_secret=dsp-connector-consumer-secret" \
  -d "grant_type=client_credentials"

# Provider
curl -X POST http://localhost:8180/realms/dsp-connector/protocol/openid-connect/token \
  -d "client_id=dsp-connector-provider-backend" \
  -d "client_secret=dsp-connector-provider-secret" \
  -d "grant_type=client_credentials"
```

Use the returned `access_token` as: `Authorization: Bearer <token>`

### Realm Configuration

The bundled Keycloak realm is at `ci/docker/keycloak_resources/realm-dsp-connector.json`.

| Item | Value |
|------|-------|
| Realm | `dsp-connector` |
| Keycloak URL | `http://localhost:8180` |
| Consumer client | `dsp-connector-consumer-backend` / `dsp-connector-consumer-secret` |
| Provider client | `dsp-connector-provider-backend` / `dsp-connector-provider-secret` |
| UI client (public) | `dsp-connector-ui` |
| Admin user | `admin@test.com` → `ROLE_ADMIN` |
| Connector user | `connector@test.com` → `ROLE_CONNECTOR` |

---

## Basic Authentication Mode (`BASIC`)

Use when Keycloak is not available. Users are stored in MongoDB and managed through
`/api/v1/users`.

### Enabling Basic Auth

```properties
application.auth.provider=BASIC
```

### What Happens in Basic Mode

- Both admin and protocol zones use `Authorization: Basic <base64(user:password)>`
- `UserDetailsService` is backed by MongoDB (`UserService`)
- `/api/v1/users` endpoints are **active** — users created here are valid credentials
- Initial users and data are seeded from `initial_data.json` on startup
- On authentication failure at protocol endpoints, returns a DSP-compliant error JSON

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
- No JWT validation, no Basic auth challenge
- **Do not use in production**

---

## DCP Authentication (Future — Stub)

When `application.auth.dcp.enabled=true`, the protocol filter chain replaces the provider's
authentication with a `DcpAuthenticationFilter`. The current implementation is a pass-through stub
that will be filled in with Decentralized Claims Protocol JWT validation logic.

This flag is independent of `application.auth.provider` — it can be combined with `KEYCLOAK` or
`BASIC` (admin zone always uses the provider, only the protocol zone switches to DCP).

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

Current implementation, if OCSP is **DISABLED** (default configuration) will create OkHttpRestClient with truststore that allows ALL certificates. 

If you want to have proper TLS communication, with hostname validation enabled, this can be achieved by setting 

```
application.ocsp.validation.enabled=true
```

This will create proper *OcspX509TrustManager* that will load provided truststore, and perform:

 - hostname validation (PKIX)
 - OCSP check
 
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
