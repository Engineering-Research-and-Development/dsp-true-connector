# Multi-Tenant User Guide

## Overview

The TRUE Connector supports multi-tenancy, allowing a single connector instance to serve multiple independent tenants. Each tenant has its own isolated catalog, contract negotiations, and data transfers. A special super-admin role can access all tenant data across the entire connector.

## Concepts

| Term | Description |
|------|-------------|
| **Tenant** | An isolated organisational unit with its own catalog, negotiations, and transfers |
| **Tenant ID** | A short, URL-safe identifier (e.g., `engineering-a`, `acme-corp`) |
| **Super-admin** | A user with `ROLE_SUPER_ADMIN` who can access data across all tenants |
| **X-Tenant-Id header** | Optional header used by super-admins (without a tenant of their own) to scope management API calls to a specific tenant. Tenant users must not send it (403) |

---

## Getting Started

### Default Tenant

On first startup a default tenant named **EngineeringA** with ID `engineering-a` is created automatically. The connector will refuse to start if no enabled tenant exists.

### Accessing Protocol Endpoints

All DSP protocol endpoints are prefixed with `/{tenantId}`:

| Endpoint type | URL pattern |
|---------------|-------------|
| Catalog | `/{tenantId}/catalog/request` |
| Negotiations (provider) | `/{tenantId}/negotiations` |
| Negotiations (consumer callback) | `/{tenantId}/consumer/negotiations/{consumerPid}/...` |
| Transfers (provider) | `/{tenantId}/transfers/request` |
| Transfers (consumer callback) | `/{tenantId}/consumer/transfers/{consumerPid}/...` |

**Example — requesting a catalog from the `engineering-a` tenant:**
```
POST http://connector-host:8090/engineering-a/catalog/request
```

### Accessing Management API Endpoints

Management API endpoints remain at `/api/v1/...`. When using a regular tenant user, calls are automatically scoped to their tenant. Super-admins can optionally pass `X-Tenant-Id` to target a specific tenant; tenant users must not send it (see [Super-Admin Access](#super-admin-access)).

---

## Managing Tenants

### Create a Tenant

```http
POST /api/v1/tenants
Authorization: Basic <admin-credentials>
Content-Type: application/json

{
  "id": "acme-corp",
  "name": "ACME Corporation",
  "participantId": "urn:connector:acme",
  "callbackAddress": "https://acme-connector.example.com",
  "realm": "tenant-acme-corp",
  "enabled": true
}
```

`realm` is optional and only used in `KEYCLOAK` mode: it names the Keycloak realm whose tokens the
tenant accepts. It must match `^[A-Za-z0-9._-]+$` (at most 255 characters) and must not be `.`, `..`,
`master`, `platform` or `connector-admin`, otherwise the request fails with `400`. A realm already used by
another tenant, enabled or disabled, fails with `409`. `PUT /api/v1/tenants/{tenantId}` applies the
same rules. Changing the realm of a tenant removes its stored realm credentials (they belong to the old
realm's client); set them again for the new realm.

### List Tenants

```http
GET /api/v1/tenants
Authorization: Basic <admin-credentials>
```

### Get a Specific Tenant

```http
GET /api/v1/tenants/{tenantId}
Authorization: Basic <admin-credentials>
```

### Enable / Disable a Tenant

```http
PUT /api/v1/tenants/{tenantId}/enable
PUT /api/v1/tenants/{tenantId}/disable
Authorization: Basic <admin-credentials>
```

Disabling a tenant causes all subsequent protocol requests for that tenant to return `403 Forbidden`.

### Delete a Tenant

```http
DELETE /api/v1/tenants/{tenantId}
Authorization: Basic <admin-credentials>
```

---

## User Model

Each connector deployment uses two categories of user:

| Role | Typical email | `tenantId` | Responsibilities |
|------|--------------|-----------|-----------------|
| **Super-admin** (`ROLE_SUPER_ADMIN`) | `superadmin@mail.com` | none | Create/manage tenants, cross-tenant visibility |
| **Tenant admin** (`ROLE_ADMIN`) | `admin@mail.com` | e.g., `engineering-a` | Manage catalog, datasets, negotiations, transfers for one tenant |

> Each tenant requires its own `ROLE_ADMIN` user with its `tenantId` set. Users with different tenants must have unique email addresses.
>
> There is no connector user. In `INTERNAL` mode the connector mints its own protocol token for connector-to-connector calls (see [security.md](security.md#connector-to-connector-credentials-m2m)); the connector user is no longer seeded.

The default `initial_data.json` seeds:
- `superadmin@mail.com` — `ROLE_SUPER_ADMIN`, no tenant
- `admin@mail.com` — `ROLE_ADMIN`, scoped to the default `engineering-a` tenant
- the tenants `engineering-a` (realm `tenant-EngineeringA`) and `acme` (realm `tenant-acme`, a Keycloak demo tenant with no `INTERNAL` users; its users `coyote` and `roadrunner` exist only in Keycloak)

---

## Super-Admin Access

A user with `ROLE_SUPER_ADMIN` can:

- List and manage tenants via `/api/v1/tenants`
- Access all management API data across all tenants (without `X-Tenant-Id` header)
- Scope management API calls to a specific tenant by adding the `X-Tenant-Id` header

**Example — list negotiations for `acme-corp` tenant as super-admin:**
```http
GET /api/v1/negotiations
Authorization: Basic <super-admin-credentials>
X-Tenant-Id: acme-corp
```

Without the `X-Tenant-Id` header, a super-admin receives data from all tenants.

### `X-Tenant-Id` rules

The same rules apply in `INTERNAL` and `KEYCLOAK` mode. The tenant of a request comes from the `tenant_id` claim of the access token.

| Who sends `X-Tenant-Id` | Result |
|-------------------------|--------|
| Super-admin without a tenant, header names an existing, enabled tenant | Accepted; the request runs for that tenant and a `TENANT_DELEGATION` audit event is written |
| Super-admin, header names an unknown or disabled tenant | `400 Bad Request` |
| Tenant user (`ROLE_ADMIN`), in `INTERNAL` or `KEYCLOAK` mode | `403 Forbidden`. Tenant users never need the header: their tenant is in the token. Clients that always send it must stop doing so for tenant users |
| Any other principal without `ROLE_SUPER_ADMIN`, or a super-admin that has a tenant of its own | `403 Forbidden` |
| `platform` (connector) token | No tenant; the route answers 403 anyway |

Audit entries of delegations show the super-admin as `principal` and the delegated tenant as `targetTenantId`.

---

## Users in Keycloak Mode

In `KEYCLOAK` mode the connector has no user API (`/api/v1/users/**` answers `404` for a super-admin). Create and manage users in Keycloak (Admin Console, Admin REST API or realm import):

- **Super-admins** go in the `connector-admin` realm (`application.keycloak.admin-realm`), with the realm role `SUPER_ADMIN` and **no** `tenant_id` attribute. They select a tenant per request with `X-Tenant-Id`.
- **Tenant users** go in the tenant's own realm (its `realm` field, e.g. `tenant-EngineeringA`), with the realm role `ADMIN` and a `tenant_id` attribute (mapped to the `tenant_id` claim) equal to the tenant `_id` (e.g. `engineering-a`), not the realm name. The realm is trusted only while an enabled tenant names it in `realm`, so a new tenant realm needs its Mongo tenant too; without one, its tokens get `401`.
- Never create a super-admin in a tenant realm: a tenant realm may not grant `SUPER_ADMIN`, so such a token gets `401` and a `SECURITY_REALM_ROLE_VIOLATION` audit event.

See [security.md](security.md#user-management) for the full rules.

---

## Error Responses

| Scenario | HTTP Status | Description |
|----------|-------------|-------------|
| Unknown tenant ID in path | `404 Not Found` | The `{tenantId}` in the URL does not match any tenant |
| Tenant is disabled | `403 Forbidden` | The tenant exists but has been disabled |
| Missing or invalid auth | `401 Unauthorized` | No valid credentials provided |
| `X-Tenant-Id` from a tenant user | `403 Forbidden` | Only super-admins may send the header |
| `X-Tenant-Id` names an unknown or disabled tenant | `400 Bad Request` | Super-admin requests only |

---

## Audit Events

All tenant lifecycle operations (create, delete, enable, disable) produce audit events visible at:

```http
GET /api/v1/audit-events
Authorization: Basic <admin-credentials>
```

---

## Infrastructure and Routing

### Path Variable Routing

Tenant isolation is achieved via URL path variable — `/{tenantId}/...`. No special reverse-proxy configuration is required. A standard Caddy or Nginx proxy that forwards traffic to the connector port will work without changes.

### Subdomain Routing (Advanced)

Subdomain-based routing (e.g., `acme-corp.connector.example.com`) is possible but requires:
1. A real domain with wildcard DNS (`*.connector.example.com → connector-IP`)
2. A DNS provider that supports wildcard certificates or Let's Encrypt DNS-01 challenge
3. Caddy or Nginx configured to extract the subdomain and forward it as a path prefix or custom header

For most deployments, path-variable routing is the recommended approach.

---

## Example: Two-Connector Setup (Consumer + Provider)

Assuming:
- Provider runs on port `8090` with tenant `engineering-a`
- Consumer runs on port `8080` with tenant `engineering-a`

### Consumer requests Provider catalog

```http
POST http://localhost:8090/engineering-a/catalog/request
Authorization: Basic <connector-credentials>
Content-Type: application/json

{
  "@context": "https://w3id.org/dspace/2025/1/context.jsonld",
  "@type": "dspace:CatalogRequestMessage",
  "dspace:filter": {}
}
```

### Consumer starts negotiation

```http
POST http://localhost:8090/engineering-a/negotiations/request
Authorization: Basic <connector-credentials>
Content-Type: application/json

{
  "@context": "https://w3id.org/dspace/2025/1/context.jsonld",
  "@type": "dspace:ContractRequestMessage",
  "dspace:dataset": "urn:uuid:<datasetId>",
  ...
}
```

The consumer's `callbackAddress` must include the consumer's tenant prefix so the provider can call back correctly — e.g., `http://localhost:8080/engineering-a`.

---

## Frequently Asked Questions

**Q: Can I run the connector with a single tenant?**  
A: Yes. The default `engineering-a` tenant is always present. If you don't need multiple tenants, simply use the default and ignore tenant management.

**Q: Can a user belong to multiple tenants?**  
A: Not in the current implementation. A user is associated with exactly one tenant. Super-admins are an exception — they have cross-tenant access.

**Q: Does disabling a tenant affect in-progress negotiations or transfers?**  
A: New requests for a disabled tenant are rejected (403). Existing, in-progress negotiations and transfers in the database are not automatically terminated.

**Q: What happens if two tenants have a negotiation between them on the same connector instance?**  
A: This is not the typical use case. The connector is designed for inter-connector communication. Two tenants on the same instance negotiating with each other is not supported in the current implementation.
