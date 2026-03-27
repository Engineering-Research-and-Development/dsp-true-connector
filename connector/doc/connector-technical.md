# Connector Module — Technical Documentation

> **See also:** [Operator Guide](./connector-platform-features.md) | [User Management](./users.md) | [Negotiation](./negotiation.md) | [Transfer](./transfer.md)

## Overview

The `connector` module is the **application entry point and platform-level services layer** for the TRUE Connector. It
is a Spring Boot application that bootstraps all DSP protocol modules (catalog, negotiation, data-transfer) and provides
the cross-cutting platform features they rely on:

- **Application lifecycle** — Spring Boot main class and component scanning
- **Security** — authentication and authorisation for both management API users and connector-to-connector DSP protocol
  endpoints
- **User management** — CRUD operations for human operator accounts
- **DSP version endpoint** — `/.well-known/dspace-version` for protocol compatibility discovery
- **MongoDB configuration** — database connection, auditing, and custom type converters
- **Initial data loading** — database seeding on first startup and S3 mock-data provisioning

This module does **not** implement DSP protocol logic. Protocol-specific behaviour lives in the `catalog`,
`negotiation`, and `datatransfer` modules, which are loaded by this module's component scan.

---

## Application Entry Point

**Class:** `it.eng.connector.ApplicationConnector`

```java
@SpringBootApplication
@ComponentScan({"it.eng.connector", "it.eng.catalog", "it.eng.negotiation", "it.eng.tools", "it.eng.datatransfer"})
public class ApplicationConnector {
    public static void main(String[] args) {
        System.setProperty("server.error.include-stacktrace", "never");
        SpringApplication.run(ApplicationConnector.class, args);
    }
}
```

The `@ComponentScan` declaration is the integration point for all modules. Every Spring-managed bean in the five
packages above is discovered and registered in the single shared application context. Stack traces are suppressed from
HTTP error responses at startup for security.

---

## Security Architecture

Security is configured in `WebSecurityConfig` (`@EnableWebSecurity`, `@EnableMethodSecurity`). Three filters are
added to the Spring Security filter chain; they execute in the following order for every incoming request:

1. `DataspaceProtocolEndpointsAuthenticationFilter`
2. `JwtAuthenticationFilter`
3. `BasicAuthenticationFilter`

### Authentication Types

#### 1. JWT Bearer Token — Connector-to-Connector (DSP Protocol Endpoints)

Used when a remote connector calls the DSP protocol endpoints (`/connector/**`, `/negotiations/**`, `/catalog/**`,
`/transfers/**`). The remote connector attaches a DAPS-issued JWT as an `Authorization: Bearer <token>` header.

**Filter:** `JwtAuthenticationFilter` extends `OncePerRequestFilter`

- Reads the `Authorization` header.
- If the value starts with `Bearer `, the token is extracted and wrapped in a `JwtAuthenticationToken`.
- The token is passed to `AuthenticationManager`, which delegates to `JwtAuthenticationProvider`.
- `JwtAuthenticationProvider` validates the token via `DapsService.validateToken()`.
- On success a `JwtAuthenticationToken(principal, authenticated=true)` is stored in `SecurityContextHolder`.
- On failure the security context is cleared and the request continues unauthenticated (the authorisation rules will
  then reject it).
- If no `Bearer` header is present the filter skips and the next filter in the chain runs.

**Token validation is handled by `DapsService`** (in the `tools` module), which performs DAPS JWKS validation. The
behaviour is controlled by application properties stored in MongoDB:

| Property | Description |
|---|---|
| `application.daps.enabledDapsInteraction` | Whether live DAPS validation is active |
| `application.daps.extendedTokenValidation` | OCSP and additional checks |
| `application.daps.dapsJWKSUrl` | JWKS endpoint used for key resolution |

#### 2. Basic Auth — Management API (Human Operators)

Used when a human operator calls the management API (`/api/**`). Credentials are sent as
`Authorization: Basic base64(email:password)`.

**Filter:** `BasicAuthenticationFilter` (Spring Security built-in)

- Decodes the `Basic` header.
- Delegates to `DaoAuthenticationProvider`, which loads the user from MongoDB via `UserRepository.findByEmail()` and
  verifies the BCrypt-encoded password.
- On success a `UsernamePasswordAuthenticationToken` with `ROLE_ADMIN` authority is stored in the security context.

#### Protocol Authentication Bypass

**Filter:** `DataspaceProtocolEndpointsAuthenticationFilter` extends `OncePerRequestFilter`

This filter runs **before** the JWT filter. Its purpose is to allow protocol endpoints to be called without
authentication during development or testing.

- Reads the `application.protocol.authentication.enabled` property from MongoDB at runtime (default: `true`).
- When `true`: the filter does nothing; JWT validation proceeds normally.
- When `false`: the filter injects a synthetic `UsernamePasswordAuthenticationToken` with `ROLE_CONNECTOR` authority,
  effectively bypassing DAPS JWT validation for protocol requests.
- This filter is **skipped entirely** for URLs containing `/api` (the `shouldNotFilter` override).

### Authorisation Configuration

URL-level access control is defined in `WebSecurityConfig.securityFilterChain()`:

| URL Pattern | Required Role | Notes |
|---|---|---|
| `/env` | `ROLE_ADMIN` | Spring Boot env endpoint |
| `/actuator/**` | `ROLE_ADMIN` | Actuator endpoints |
| `/connector/**` | `ROLE_CONNECTOR` | DSP connector protocol |
| `/negotiations/**` | `ROLE_CONNECTOR` | DSP negotiation protocol |
| `/catalog/**` | `ROLE_CONNECTOR` | DSP catalog protocol |
| `/transfers/**` | `ROLE_CONNECTOR` | DSP transfer protocol |
| `/api/**` | `ROLE_ADMIN` | Management API (users, properties, catalog CRUD, etc.) |
| `/.well-known/dspace-version` | Open | No authentication required |
| All other paths | Open | Permitted for all |

Additional notes:

- CSRF protection is **disabled** (stateless REST API).
- Sessions are **disabled** (`sessionManagement.disable()`).
- Anonymous access is **disabled**.
- CORS is configured via `application.cors.*` properties (wildcard defaults if not set).
- Authentication failures are handled by `DataspaceProtocolEndpointsAuthenticationEntryPoint`, which delegates to
  Spring MVC's `HandlerExceptionResolver` for consistent JSON error responses.

### Password Validation

**Class:** `PasswordCheckValidator` (uses the [Passay](https://www.passay.org/) library)

**Configuration class:** `PasswordStrengthProperties`  
**Configuration prefix:** `application.password.validator`

| Property | Default | Description |
|---|---|---|
| `minLength` | `8` | Minimum password length |
| `maxLength` | `16` | Maximum password length |
| `minLowerCase` | `1` | Minimum lowercase characters |
| `minUpperCase` | `1` | Minimum uppercase characters |
| `minDigit` | `1` | Minimum digit characters |
| `minSpecial` | `1` | Minimum special characters |

`PasswordCheckValidator.isValid(String password)` returns a `PasswordValidationResult` that carries a validity flag and
a list of violation messages. `UserService` throws `BadRequestException` with the joined violation messages when
validation fails.

---

## User Management

### User Model

**Class:** `it.eng.connector.model.User`  
**MongoDB collection:** `users`  
Implements `UserDetails` (Spring Security).

| Field | Type | Notes |
|---|---|---|
| `id` | `String` | MongoDB `_id`; generated as `urn:uuid:<uuid>` |
| `firstName` | `String` | User's given name |
| `lastName` | `String` | User's family name |
| `email` | `String` | Unique identifier / username |
| `password` | `String` | BCrypt-encoded; `@JsonIgnore` — never serialised |
| `enabled` | `boolean` | Account active flag |
| `expired` | `boolean` | Account expiry flag |
| `locked` | `boolean` | Account lock flag |
| `role` | `Role` | Enum; determines granted authority |

**Role enum:** `it.eng.connector.model.Role`

| Value | Purpose |
|---|---|
| `ROLE_ADMIN` | Human operator; access to `/api/**` management endpoints |
| `ROLE_CONNECTOR` | Connector identity; access to DSP protocol endpoints |
| `ROLE_USER` | Defined but not currently used in authorisation rules |

### UserDTO

**Class:** `it.eng.connector.model.UserDTO`  
Used as request body for create/update operations.

| Field | Used in |
|---|---|
| `firstName` | Create, Update |
| `lastName` | Create, Update |
| `email` | Create |
| `password` | Create (initial password), Password change (current password) |
| `newPassword` | Password change |
| `role` | Create |

### User API Endpoints

**Base path:** `/api/v1/users`  
**Auth required:** Basic Auth with `ROLE_ADMIN`  
**Content-Type / Accept:** `application/json`

All responses are wrapped in `GenericApiResponse<T>`.

#### GET `/api/v1/users`

Returns all users.

**Response:** `GenericApiResponse<Collection<JsonNode>>`

#### GET `/api/v1/users/{email}`

Returns a single user by email address.

**Path variable:** `email` — the user's email  
**Response:** `GenericApiResponse<Collection<JsonNode>>` (single-element list)  
**Error:** `404 Not Found` if no user with that email exists

#### POST `/api/v1/users`

Creates a new user.

**Request body:**
```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "email": "jane.doe@example.com",
  "password": "SecurePass1!",
  "role": "ROLE_ADMIN"
}
```

**Validations:**
- Email must not already exist in the database.
- Password must pass `PasswordCheckValidator` rules.

**Response:** `GenericApiResponse<JsonNode>` with the created user

#### PUT `/api/v1/users/{id}/update`

Updates `firstName` and/or `lastName` for the user identified by `id`. Only the currently authenticated user can
update their own record.

**Path variable:** `id` — the user's database ID  
**Request body:**
```json
{
  "firstName": "Janet",
  "lastName": "Smith"
}
```

**Constraint:** `principal.getName()` (logged-in user's email) must match the target user's email; otherwise
`BadRequestException` is thrown.

#### PUT `/api/v1/users/{id}/password`

Changes the password for the user identified by `id`. Only the currently authenticated user can change their own
password.

**Path variable:** `id` — the user's database ID  
**Request body:**
```json
{
  "password": "CurrentPassword1!",
  "newPassword": "NewPassword2@"
}
```

**Validations:**
- `password` must match the stored BCrypt hash.
- `newPassword` must pass `PasswordCheckValidator` rules.
- The calling user's email must match the target user's email.

---

## DSP Version Endpoint

**Controller:** `DSpaceVersionController`  
**Service:** `DSpaceVersionService`  
**Path:** `GET /.well-known/dspace-version`  
**Auth:** Open (no authentication required)

This endpoint implements the [Dataspace Protocol version discovery](https://docs.internationaldataspaces.org/ids-knowledgebase/dataspace-protocol)
mechanism. Remote connectors call it to verify protocol compatibility before initiating catalog or negotiation
interactions.

`DSpaceVersionService.getVersion()` reads configuration from MongoDB (property group `application.dspace.version`) and
builds the response. The DSP version is hardcoded to `"2025-1"`.

### Response Models

**`VersionResponse`** — top-level response object

| Field | Type | Description |
|---|---|---|
| `protocolVersions` | `List<Version>` | Supported protocol versions |

**`Version`** — describes one supported DSP version

| Field | Type | Description |
|---|---|---|
| `binding` | `String` | Transport binding; default `"HTTPS"` |
| `path` | `String` | Context root where the connector is deployed |
| `version` | `String` | DSP protocol version; currently `"2025-1"` |
| `identifierType` | `String` | Identifier type for the service |
| `serviceId` | `String` | Unique service identifier |
| `auth` | `Auth` | Authentication mechanism descriptor |

**`Auth`** — describes the authentication mechanism

| Field | Type | Description |
|---|---|---|
| `protocol` | `String` | Auth protocol (e.g., `"https"`) |
| `version` | `String` | Auth version (e.g., `"2025-1"`) |
| `profile` | `List<String>` | OAuth profiles (comma-separated in config, split to list) |

### Configurable Properties (MongoDB `application_properties` collection)

| Property key | Example value | Description |
|---|---|---|
| `application.dspace.version.path` | `/` | Deployment path |
| `application.dspace.version.identifierType` | _(empty)_ | Identifier type |
| `application.dspace.version.serviceId` | _(empty)_ | Service ID |
| `application.dspace.version.auth.protocol` | `https` | Auth protocol |
| `application.dspace.version.auth.version` | `2025-1` | Auth version |
| `application.dspace.version.auth.profile` | `authorization_code,refresh_token` | Comma-separated OAuth profiles |

---

## Database Configuration

**Class:** `MongoConfig`  
**Annotations:** `@EnableMongoAuditing`, `@EnableMongoRepositories`

### Repository Scanning

All MongoDB repositories across all modules are registered from a single configuration:

```
it.eng.tools.repository
it.eng.tools.s3.repository
it.eng.connector.repository
it.eng.catalog.repository
it.eng.negotiation.repository
it.eng.datatransfer.repository
```

### Auditing

`@EnableMongoAuditing` activates Spring Data MongoDB auditing. `AuditorAwareImpl` implements `AuditorAware<String>` and
returns the email address of the currently authenticated `User` from `SecurityContextHolder`. Fields annotated with
`@CreatedBy` and `@LastModifiedBy` on any `@Document` class are automatically populated with this value. `@CreatedDate`
and `@LastModifiedDate` fields are populated with the current timestamp.

### Custom Type Converters

`MongoCustomConversions` registers bidirectional String ↔ enum converters for:

| Enum | Module |
|---|---|
| `it.eng.catalog.model.Action` | catalog |
| `it.eng.catalog.model.LeftOperand` | catalog |
| `it.eng.catalog.model.Operator` | catalog |
| `it.eng.negotiation.model.Action` | negotiation |
| `it.eng.negotiation.model.LeftOperand` | negotiation |
| `it.eng.negotiation.model.Operator` | negotiation |

These converters ensure enums are persisted and read as their string representation rather than ordinal.

---

## Initial Data Loading

**Class:** `InitialDataLoader`

### MongoDB Seed Data

Runs as a `CommandLineRunner` on every application startup. Selects the seed file based on the active Spring profile:

| Active Profile | File loaded |
|---|---|
| _(none)_ | `initial_data.json` |
| `provider` | `initial_data-provider.json` |
| `consumer` | `initial_data-consumer.json` |
| `tck` | `initial_data-tck.json` |

The JSON file is expected to be a map of MongoDB collection names to arrays of documents:

```json
{
  "users": [ { "_id": "...", "email": "..." } ],
  "application_properties": [ { "_id": "...", "value": "..." } ]
}
```

**Idempotency:** If a document with the same `_id` already exists in the collection, it is skipped. Documents without
an `_id` field are always inserted as new documents.

Collections seeded by `initial_data.json`:

| Collection | Contents |
|---|---|
| `users` | Default admin and connector users |
| `catalogs` | Sample catalog |
| `datasets` | Sample dataset |
| `dataservices` | Sample data service |
| `distributions` | Sample distributions |
| `artifacts` | Sample artifact |
| `application_properties` | DAPS, security, and DSP version configuration |

### S3 Mock Data

Triggered on `ApplicationReadyEvent`. Uploads `ENG-employee.json` (from the classpath) to the configured S3 bucket
with object key `urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5`. This key corresponds to the sample artifact's dataset
ID in `initial_data.json`. If the file does not exist on the classpath the step is silently skipped.

### Audit Events

- On `ApplicationReadyEvent`: publishes `AuditEventType.APPLICATION_START`.
- On `ContextClosedEvent`: publishes `AuditEventType.APPLICATION_STOP`.

### Filter Registration

**Class:** `FilterRegistrationConfig`

Registers `EndpointAvailableFilter` (from the `datatransfer` module) on the `/artifacts/*` URL pattern. This filter
checks that an agreement exists and a transfer process is active before allowing artifact download requests through.

---

## Cross-Module Integration

The connector module is the only module with a `main` method; all other modules are libraries included on the classpath.
Integration is achieved through:

| Mechanism | Description |
|---|---|
| `@ComponentScan` | Discovers beans from all module packages in one application context |
| `@EnableMongoRepositories` | Registers repositories from all modules in `MongoConfig` |
| `MongoCustomConversions` | Provides shared enum converters for catalog and negotiation models |
| `FilterRegistrationConfig` | Wires the data-transfer `EndpointAvailableFilter` into the servlet container |
| `ApplicationPropertiesService` | Shared property service used by `DSpaceVersionService` and security filters |
| `DapsService` | Shared JWT validation service used by `JwtAuthenticationProvider` |

---

## See Also

- [User Management Guide](./users.md) — user API endpoint details and examples
- [Negotiation Notes](./negotiation.md) — negotiation scheduling configuration
- [Transfer Notes](./transfer.md) — data transfer data plane notes
- [Operator Guide](./connector-platform-features.md) — administrator-focused guide
