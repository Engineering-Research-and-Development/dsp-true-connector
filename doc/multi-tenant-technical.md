# Multi-Tenant Technical Reference

## Architecture Overview

Multi-tenancy in TRUE Connector uses a **shared database, discriminator-field** approach. A single MongoDB instance stores data for all tenants. Every tenant-scoped document carries a `tenantId` field that is used to filter all queries.

Tenant isolation is enforced at:
1. **Protocol layer** — URL path variable `/{tenantId}/` resolved by `TenantAwareProtocolController`
2. **Service layer** — `TenantContextHolder` (ThreadLocal) propagates the tenant scope into all repository calls
3. **Management API layer** — `ApiTenantContextFilter` sets the tenant from the authenticated user's `tenantId` field

---

## Key Components

### `Tenant` model (`tools` module)

```java
@Document(collection = "tenants")
public class Tenant {
    @Id private String id;          // URL-safe tenant ID (e.g., "engineering")
    private String name;
    private String connectorId;
    private String callbackAddress;
    private boolean enabled;
}
```

Stored in the `tenants` collection. The `id` field is the tenant identifier used in URLs and ThreadLocal.

### `TenantContextHolder` (`tools` module)

```java
public class TenantContextHolder {
    private static final ThreadLocal<String> TENANT_CONTEXT = new ThreadLocal<>();

    public static void setTenantId(String tenantId) {
        TENANT_CONTEXT.set(tenantId);
        MDC.put("tenantId", tenantId);
    }

    public static String getTenantId() {
        return TENANT_CONTEXT.get();
    }

    public static void clear() {
        TENANT_CONTEXT.remove();
        MDC.remove("tenantId");
    }
}
```

ThreadLocal value is `null` for super-admin requests without `X-Tenant-Id` header, causing service methods to fall back to non-tenant (all-tenant) queries.

### `TenantAwareProtocolController` (`tools` module)

Abstract base class for all DSP protocol controllers:

```java
public abstract class TenantAwareProtocolController {
    private final TenantService tenantService;

    protected void resolveTenant(String tenantId) {
        Tenant tenant = tenantService.findEnabledTenantById(tenantId);
        TenantContextHolder.setTenantId(tenantId);
    }
}
```

Each concrete protocol controller extends this class, maps to `/{tenantId}/...`, and calls `resolveTenant(tenantId)` as the **first operation** in every handler method (before deserialization).

### `TenantContextClearingInterceptor` (`connector` module)

`HandlerInterceptor` that clears `TenantContextHolder` after each request to prevent ThreadLocal leaks. Registered in `WebMvcConfig`.

### `ApiTenantContextFilter` (`connector` module)

`OncePerRequestFilter` that sets the tenant context from the authenticated user's `tenantId` field for management API (`/api/**`) requests. Runs after `BasicAuthenticationFilter` in the security filter chain.

---

## Tenant-Scoped Modules

### Catalog (`catalog` module)

Tenant-aware models:
- `Catalog` — `tenantId` field, `@JsonIgnore`
- `Dataset` — `tenantId` field, `@JsonIgnore`
- `Distribution` — `tenantId` field, `@JsonIgnore`
- `DataService` — `tenantId` field, `@JsonIgnore`
- `Artifact` — `tenantId` field, `@JsonIgnore`

Repository methods:
```java
Optional<Catalog> findByIdAndTenantId(String id, String tenantId);
List<Catalog> findAllByTenantId(String tenantId);
```

Service routing (`CatalogService`):
```java
String tenantId = TenantContextHolder.getTenantId();
if (tenantId != null) {
    return repository.findAllByTenantId(tenantId);
} else {
    return repository.findAll();
}
```

Protocol controller: `CatalogController` at `/{tenantId}/catalog`.

### Negotiation (`negotiation` module)

Tenant-aware models:
- `ContractNegotiation` — `tenantId` field, `@JsonIgnore`
- `Agreement` — `tenantId` field, `@JsonIgnore`

Repository methods include `findByIdAndTenantId`, `findByProviderPidAndTenantId`, `findByConsumerPidAndTenantId`, etc.

Protocol controllers:
- `ContractNegotiationProviderController` at `/{tenantId}/negotiations`
- `ContractNegotiationConsumerCallbackController` at `/{tenantId}/consumer/negotiations`

### Data Transfer (`data-transfer` module)

Tenant-aware models:
- `TransferProcess` — `tenantId` field, `@JsonIgnore`

Repository methods include `findByIdAndTenantId`, `findByProviderPidAndTenantId`, `findByConsumerPidAndTenantId`, `findByAgreementIdAndTenantId`, `findByStateAndRoleAndTenantId`, etc.

Protocol controllers:
- `ProviderDataTransferController` at `/{tenantId}/transfers`
- `ConsumerDataTransferCallbackController` at `/{tenantId}/consumer/transfers`

---

## Async Event Flow

Contract negotiation completion triggers data transfer initialization via a Spring application event (`InitializeTransferProcess`). Because the `AsynchronousSpringEventsConfig` runs the event listener on a separate thread, the request-scoped `TenantContextHolder` ThreadLocal is **not available** in the listener.

To propagate tenant context across async boundaries, `tenantId` is embedded in the event payload:

```java
// In ContractNegotiationConsumerService (request thread)
applicationEventPublisher.publishEvent(
    new InitializeTransferProcess(callbackAddress, agreementId, datasetId, role,
        contractNegotiation.getTenantId())  // tenant ID from negotiation
);

// In DataTransferEventListener (async worker thread)
TransferProcess.Builder.newInstance()
    ...
    .tenantId(initializeTransferProcess.getTenantId())  // from event payload
    .build();
```

---

## Security Configuration

### Filter Chain for Protocol Endpoints

Protocol endpoints (`/{tenantId}/catalog/**`, `/{tenantId}/negotiations/**`, `/{tenantId}/transfers/**`) require `ROLE_CONNECTOR`.

`TenantAwareProtocolController.resolveTenant()` validates the tenant ID and sets the ThreadLocal **after** authentication succeeds.

### Filter Chain for Management API

Management endpoints (`/api/**`) require `ROLE_ADMIN` or `ROLE_SUPER_ADMIN`.

`ApiTenantContextFilter` (registered after `BasicAuthenticationFilter`) reads the authenticated `User.tenantId` and sets `TenantContextHolder`. For super-admins (users with `tenantId=null`), the `X-Tenant-Id` request header overrides the user's own tenant.

### User Model

Three user roles participate in the multi-tenant security model:

| Role | `tenantId` | Scope | Notes |
|------|-----------|-------|-------|
| `ROLE_SUPER_ADMIN` | `null` | All tenants | Manages tenant lifecycle. `X-Tenant-Id` header scopes a single request to one tenant. |
| `ROLE_ADMIN` | per-tenant | Own tenant only | Day-to-day management of catalog, dataset, negotiation, and transfer for one tenant. |
| `ROLE_CONNECTOR` | per-tenant | Own tenant only | Authenticates DSP protocol calls. One per tenant. |

### Internal Service Calls

When a module makes an internal management API call on behalf of the current request (e.g., negotiation module validates an offer against the catalog), the call uses a synthetic **internal service account**:

- **BASIC mode**: `InternalServiceAuthenticationProvider` authenticates `internal-service:<secret>` without a MongoDB lookup. The synthetic user has `ROLE_ADMIN` and `tenantId=null`, so `ApiTenantContextFilter` reads `X-Tenant-Id` from the forwarded header.
- **KEYCLOAK mode**: the backend client credentials JWT is used. The service account must not have a `tenantId` claim so tenant context is read from `X-Tenant-Id`.

The forwarded `X-Tenant-Id` value comes from `TenantContextHolder.getTenantId()` captured on the originating request thread.

---

## Tenant Lifecycle — Startup Guard

`InitialDataLoader` (connector module) runs at startup and:
1. Calls `ensureEngineeringTenant()` to create the default tenant if absent.
2. Calls `ensureAtLeastOneEnabledTenant()` — if no enabled tenant exists, the application **fails fast** with an `IllegalStateException`.

---

## Repository Query Conventions

All tenant-scoped repositories follow this pattern:

```java
// Non-tenant fallback (super-admin / async)
Optional<T> findById(String id);
List<T> findAll();

// Tenant-scoped queries
Optional<T> findByIdAndTenantId(String id, String tenantId);
List<T> findAllByTenantId(String tenantId);
```

Service methods always check `TenantContextHolder.getTenantId()`:
- Non-null → use tenant-scoped query
- Null → use non-tenant query (all data)

---

## Logging and MDC

`TenantContextHolder.setTenantId()` also sets `MDC.put("tenantId", tenantId)`.

All `logback.xml` files include `%X{tenantId}` in the log pattern:
```xml
<pattern>%date{dd-MM-yyyy HH:mm:ss.SSS} [%thread] [%X{tenantId}] %-5level %logger{36} - %msg%n</pattern>
```

This makes it easy to filter logs by tenant in log aggregation tools.

---

## Audit Events

All tenant lifecycle operations produce `AuditEvent` records with the following event types:

| Event Type | Description |
|------------|-------------|
| `TENANT_CREATED` | A new tenant was created |
| `TENANT_DELETED` | A tenant was deleted |
| `TENANT_ENABLED` | A tenant was re-enabled |
| `TENANT_DISABLED` | A tenant was disabled |
| `TENANT_NOT_FOUND` | A request arrived for an unknown tenant ID |

---

## Database Collections

| Collection | Tenant-aware | Notes |
|------------|--------------|-------|
| `tenants` | N/A (tenant list itself) | Managed by `TenantService` |
| `catalog` | ✅ `tenantId` field | `@Document(collection = "catalog")` |
| `dataset` | ✅ `tenantId` field | |
| `distribution` | ✅ `tenantId` field | |
| `data_service` | ✅ `tenantId` field | |
| `artifact` | ✅ `tenantId` field | |
| `contract_negotiation` | ✅ `tenantId` field | |
| `agreement` | ✅ `tenantId` field | |
| `transfer_process` | ✅ `tenantId` field | |
| `users` | ✅ `tenantId` field | User is scoped to one tenant |
| `application_properties` | ⚠️ Not yet tenant-aware | Phase 5 item |

---

## Known Limitations and Deferred Items

| Item | Status | Notes |
|------|--------|-------|
| `EndpointAvailableFilter` (artifact access) | Deferred (Phase 5) | Uses non-tenant fallback queries for `isAgreementValid` / `isDataTransferStarted` |
| `DataTransferProperties.consumerCallbackAddress()` | Deferred (Phase 5) | Static property; per-tenant callback address requires Phase 5 |
| `ContractNegotiationProperties.connectorId()` | Deferred (Phase 5) | Hardcoded; per-tenant connector ID requires Phase 5 |
| S3 storage isolation | Deferred (Phase 6) | Team decision needed on separate bucket vs. key prefix |
| Keycloak JWT tenant binding | Deferred (Phase 7) | JWT claim → tenantId mapping |
| AuditEvent `tenantId` field | Deferred | Add `tenantId` to `AuditEvent` model for per-tenant audit log filtering |
| User management in Keycloak mode | N/A | `/api/v1/users` not available when Keycloak auth is active |

---

## Testing

### Unit Tests

Controller tests follow the pattern used in negotiation module:
- Add `@Mock TenantService tenantService`
- `@BeforeEach`: stub `tenantService.findEnabledTenantById(TENANT_ID)` with a test `Tenant`
- `@AfterEach`: call `TenantContextHolder.clear()`
- Pass `TENANT_ID` as the first argument to all controller method calls

Service tests that do not set `TenantContextHolder` will exercise the non-tenant fallback path automatically, so existing service tests require minimal changes.

### Integration Tests

Integration tests extend `BaseIntegrationTest` and use `"engineering"` as the `TENANT_ID` constant. All protocol endpoint URLs include `"/" + TENANT_ID + "/"` prefix.
