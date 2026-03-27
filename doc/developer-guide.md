# Developer Guide

> **See also:** [DSP Implementation Guide](./dsp-implementation-guide.md) | [Implementation Reference](./implementation-reference.md) | [User Guide](./user-guide.md)

## Overview

This guide is for developers who want to understand, extend, or contribute to TRUE Connector. It covers the architecture, key design patterns, testing approach, and how to navigate the codebase.

---

## Architecture Overview

### Module Structure

TRUE Connector is a multi-module Maven project. The `connector/` module is the Spring Boot application entry point; the other modules implement the DSP protocol logic and shared utilities.

```
connector/          ← Spring Boot entry point, security, user management
├── catalog/        ← DSP Catalog Protocol (datasets, distributions, offers)
├── negotiation/    ← DSP Contract Negotiation Protocol (state machine, agreements)
├── data-transfer/  ← DSP Transfer Process Protocol (HTTP-PULL, HTTP-PUSH)
└── tools/          ← Shared utilities: S3, OCSP, properties, audit, encryption
```

All five modules are loaded via a single `@ComponentScan` in the `connector` module's `ApplicationConnector` class, sharing one Spring application context and one MongoDB database.

### Technology Stack

| Technology | Role |
|-----------|------|
| Java 17 | Application language |
| Spring Boot | Application framework, dependency injection, web layer |
| MongoDB | Persistence for all domain objects and audit events |
| Spring Security | Authentication and authorization |
| OkHttp | Outbound connector-to-connector HTTP calls (with TLS) |
| AWS SDK / MinIO | S3-compatible object storage |
| Lombok | Boilerplate reduction for model classes |
| Jakarta Validation | Bean validation on domain objects |
| JUnit 5 + Mockito | Unit and integration testing |

### DSP Protocol Modules

Each DSP protocol module follows the same layered structure:

```
Protocol Controller  (/catalog/*, /negotiations/*, /transfers/*)
        ↓
Management API Controller (/api/v1/*)
        ↓
Service Layer (business logic, state transitions, outbound calls)
        ↓
Repository Layer (Spring Data MongoDB)
        ↓
Domain Model (immutable, builder-based)
```

**Catalog module** — implements DSP Catalog Protocol. Publishes and manages datasets, distributions, DataServices, and artifacts. Two API surfaces: DSP protocol endpoints (`/catalog/*`) and management API (`/api/v1/*`).

> **See also:** [Catalog Technical Docs](../catalog/doc/catalog-technical.md)

**Negotiation module** — implements DSP Contract Negotiation Protocol. Manages the full negotiation lifecycle for both Provider and Consumer roles. Includes automatic negotiation service and policy enforcement at agreement time.

> **See also:** [Negotiation Technical Docs](../negotiation/doc/negotiation-technical.md) | [Data Models](../negotiation/doc/model.md) | [Policy Enforcement](../negotiation/doc/policy-enforcement.md)

**Data-transfer module** — implements DSP Transfer Process Protocol. Pluggable transfer strategies (HTTP-PULL, HTTP-PUSH). Enforces agreement-based access control before serving data.

> **See also:** [Data Transfer Technical Docs](../data-transfer/doc/data-transfer-technical.md)

### Platform Module (`connector/`)

The `connector` module provides:

- **Application entry point** — `ApplicationConnector` with `@SpringBootApplication` and `@ComponentScan` over all five packages.
- **Security configuration** — `WebSecurityConfig` with three filters: `DataspaceProtocolEndpointsAuthenticationFilter`, `JwtAuthenticationFilter`, `BasicAuthenticationFilter`.
- **User management** — CRUD for human operator accounts.
- **MongoDB configuration** — auditing, custom type converters, initial data seeding.
- **DSP version endpoint** — `GET /.well-known/dspace-version`.

> **See also:** [Connector Technical Docs](../connector/doc/connector-technical.md)

### Shared Utilities (`tools/`)

The `tools` module is not a DSP protocol module. It provides infrastructure shared across all other modules:

- **S3 storage** — `S3Utils`, `BucketCredentialsService`, upload strategies (`S3UploadMode`)
- **OCSP validation** — `OcspCertificateValidator`, integration with Spring SSL
- **Application properties** — database-backed runtime-configurable properties (`ApplicationPropertyService`)
- **Audit events** — Spring application events logged to MongoDB
- **Field encryption** — `FieldEncryptionService` for sensitive fields (e.g. S3 secret keys)
- **Generic filtering** — `GenericFilterBuilder` for building MongoDB queries from HTTP query parameters
- **OkHttp REST client** — `OkHttpRestClient` for outbound DSP calls

> **See also:** [Tools Technical Docs](../tools/doc/tools-technical.md)

---

## Security Architecture

TRUE Connector uses two distinct authentication mechanisms:

| Caller | Mechanism | Endpoints |
|--------|-----------|-----------|
| Human operator / management tool | HTTP Basic Auth (email:password) | `/api/v1/*` |
| Remote DSP connector | JWT Bearer Token (DAPS-issued) | `/catalog/*`, `/negotiations/*`, `/transfers/*`, `/consumer/*` |

The three security filters run in order: `DataspaceProtocolEndpointsAuthenticationFilter` → `JwtAuthenticationFilter` → `BasicAuthenticationFilter`. The JWT filter validates tokens via `DapsService` (in `tools/`). Protocol authentication can be disabled at runtime via the `application.protocol.authentication.enabled` property for testing.

> **See also:** [Connector Technical Docs](../connector/doc/connector-technical.md) for full security configuration details.

---

## Adding a New DSP Feature

Follow this sequence when implementing a new protocol feature:

1. **Define domain model classes** in the appropriate module's `model/` package.
   - Use the builder pattern with a static inner `Builder` class.
   - Annotate with `@Getter`, `@NoArgsConstructor(access = AccessLevel.PRIVATE)`, `@JsonDeserialize(builder = ...)`.
   - Annotate required fields with `@NotNull`.
   - Validate in `Builder.build()` using Jakarta Validation.
   - See [Negotiation Data Models](../negotiation/doc/model.md) for examples.

2. **Implement the protocol controller** (DSP wire protocol) — handles inbound DSP messages.
   - Follow the DSP specification for request/response message types.
   - Return appropriate HTTP status codes (201 for creation, 200 for state updates, 400/404 for errors).

3. **Implement the management API controller** — handles management operations.
   - Annotate with `@RestController`, `@RequestMapping("/api/v1/...")`.
   - Require authentication; use `GenericApiResponse` as the response wrapper.

4. **Implement the service layer** — business logic, state machine enforcement, outbound calls.
   - Inject `OkHttpRestClient` for outbound connector-to-connector calls.
   - Publish Spring application events for cross-module notifications.
   - Emit audit events via `AuditEventService`.

5. **Add MongoDB repository** — extend `MongoRepository<Entity, String>`.

6. **Write unit tests** (JUnit 5 + Mockito) and integration tests.

---

## Key Patterns Used

### Builder Pattern for Models

All domain model classes use an immutable builder pattern enforced by `@NoArgsConstructor(access = AccessLevel.PRIVATE)`. The builder validates the object using Jakarta Validation before returning it.

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = MyModel.Builder.class)
public class MyModel implements Serializable {

    @NotNull
    private String requiredField;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final MyModel model = new MyModel();

        public static Builder newInstance() { return new Builder(); }

        public Builder requiredField(String value) {
            model.requiredField = value;
            return this;
        }

        public MyModel build() {
            // validate and return
        }
    }
}
```

> **See also:** [Negotiation Data Models](../negotiation/doc/model.md) for complete examples.

### Strategy Pattern for Transfer

Data transfer execution is decoupled from protocol handling via the `DataTransferStrategy` interface:

```java
CompletableFuture<Void> transfer(TransferProcess transferProcess);
```

`DataTransferStrategyFactory` maps a format string (`"HttpData-PULL"`, `"HttpData-PUSH"`) to the appropriate strategy at runtime. New transfer formats can be added by implementing `DataTransferStrategy` and registering it in the factory.

> **See also:** [Data Transfer Technical Docs](../data-transfer/doc/data-transfer-technical.md)

### Spring Application Events for Cross-Module Communication

Modules communicate without direct dependencies by publishing and listening to Spring `ApplicationEvent` subclasses. The `tools/event/` package defines the cross-module event types (audit events, property change events, negotiation lifecycle events, transfer lifecycle events, policy enforcement requests).

To publish an event: `applicationEventPublisher.publishEvent(new MyEvent(source, payload))`.
To consume an event: annotate a method with `@EventListener` or `@TransactionalEventListener`.

> **See also:** [Tools Technical Docs](../tools/doc/tools-technical.md) for event type definitions.

### Generic Filtering

All list endpoints support dynamic filtering and pagination. The `GenericFilterBuilder` (in `tools/`) constructs MongoDB `Query` objects from HTTP query parameters automatically, with type detection for strings, numbers, booleans, and enums.

```bash
# Example: list negotiations filtered by state and role
GET /api/v1/negotiations?state=REQUESTED&role=PROVIDER&page=0&size=20
```

> **See also:** [Generic Filtering](../tools/doc/generic-filtering.md)

---

## Configuration & Properties

Application properties are stored in MongoDB and configurable at runtime via the Properties API — no restart required. The `ApplicationPropertyService` reloads properties into the live Spring `Environment` after each update.

Key property keys relevant to development:

| Key | Default | Description |
|-----|---------|-------------|
| `application.automatic.negotiation` | `false` | Auto-accept negotiations |
| `application.automatic.transfer` | `false` | Auto-start transfers |
| `application.protocol.authentication.enabled` | `true` | JWT auth on protocol endpoints |
| `server.ssl.enabled` | `true` | Enable HTTPS (set `false` for local HTTP) |

> **See also:** [Application Properties Guide](../tools/doc/application-property.md) | [Tools Implementation Guide](../tools/doc/tools-implementation.md)

---

## Testing

### Unit Tests

- Framework: JUnit 5 + Mockito via `@ExtendWith(MockitoExtension.class)`
- Mock dependencies with `@Mock`; inject with `@InjectMocks`
- Cover positive and negative paths; use `@ParameterizedTest` for multiple inputs
- Target ≥ 80% line and branch coverage
- Run: `mvn test`

### Integration Tests

Integration tests live in `connector/src/test/java/it/eng/connector/integration/`. They use Spring Boot test slices and Testcontainers for MongoDB.

> **See also:** [Test Containers Guide](./test-containers-starting-guide.md)

### TCK Compliance Testing

The connector passes the Dataspace Protocol Technology Compatibility Kit (TCK). TCK tests can be run with the `tck` Spring profile active.

> **See also:** [TCK Compliance](./tck/tck-compliancy.md)

---

## Development Workflow

> **See also:** [Development Procedure](./development-procedure.md) — branching, PR process, GitHub Actions, Definition of Done.

### Build

```bash
mvn clean install
```

### SpotBugs / Code Quality

```bash
./spotbugs-scan.sh    # Linux/Mac
spotbugs-scan.cmd     # Windows
```

> **See also:** [SpotBugs](./spotbugs.md)

---

## See Also

| Resource | Link |
|----------|------|
| Catalog Technical Docs | [catalog-technical.md](../catalog/doc/catalog-technical.md) |
| Negotiation Technical Docs | [negotiation-technical.md](../negotiation/doc/negotiation-technical.md) |
| Data Transfer Technical Docs | [data-transfer-technical.md](../data-transfer/doc/data-transfer-technical.md) |
| Connector Technical Docs | [connector-technical.md](../connector/doc/connector-technical.md) |
| Tools Technical Docs | [tools-technical.md](../tools/doc/tools-technical.md) |
| Data Models | [model.md](../negotiation/doc/model.md) |
| Policy Enforcement | [policy-enforcement.md](../negotiation/doc/policy-enforcement.md) |
| Generic Filtering | [generic-filtering.md](../tools/doc/generic-filtering.md) |
| Application Properties | [application-property.md](../tools/doc/application-property.md) |
| Development Procedure | [development-procedure.md](./development-procedure.md) |
