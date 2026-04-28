# Multi-Tenant User Guide

## Overview

The TRUE Connector supports multi-tenancy, allowing a single connector instance to serve multiple independent tenants. Each tenant has its own isolated catalog, contract negotiations, and data transfers. A special super-admin role can access all tenant data across the entire connector.

## Concepts

| Term | Description |
|------|-------------|
| **Tenant** | An isolated organisational unit with its own catalog, negotiations, and transfers |
| **Tenant ID** | A short, URL-safe identifier (e.g., `engineering`, `acme-corp`) |
| **Super-admin** | A user with `ROLE_SUPER_ADMIN` who can access data across all tenants |
| **X-Tenant-Id header** | Optional header used by super-admins to scope management API calls to a specific tenant |

---

## Getting Started

### Default Tenant

On first startup a default tenant named **Engineering** with ID `engineering` is created automatically. The connector will refuse to start if no enabled tenant exists.

### Accessing Protocol Endpoints

All DSP protocol endpoints are prefixed with `/{tenantId}`:

| Endpoint type | URL pattern |
|---------------|-------------|
| Catalog | `/{tenantId}/catalog/request` |
| Negotiations (provider) | `/{tenantId}/negotiations` |
| Negotiations (consumer callback) | `/{tenantId}/consumer/negotiations/{consumerPid}/...` |
| Transfers (provider) | `/{tenantId}/transfers/request` |
| Transfers (consumer callback) | `/{tenantId}/consumer/transfers/{consumerPid}/...` |

**Example — requesting a catalog from the `engineering` tenant:**
```
POST http://connector-host:8090/engineering/catalog/request
```

### Accessing Management API Endpoints

Management API endpoints remain at `/api/v1/...`. When using a regular tenant user, calls are automatically scoped to their tenant. Super-admins can optionally pass `X-Tenant-Id` to target a specific tenant.

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
  "connectorId": "urn:connector:acme",
  "callbackAddress": "https://acme-connector.example.com",
  "enabled": true
}
```

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

---

## Error Responses

| Scenario | HTTP Status | Description |
|----------|-------------|-------------|
| Unknown tenant ID in path | `404 Not Found` | The `{tenantId}` in the URL does not match any tenant |
| Tenant is disabled | `403 Forbidden` | The tenant exists but has been disabled |
| Missing or invalid auth | `401 Unauthorized` | No valid credentials provided |

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
- Provider runs on port `8090` with tenant `engineering`
- Consumer runs on port `8080` with tenant `engineering`

### Consumer requests Provider catalog

```http
POST http://localhost:8090/engineering/catalog/request
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
POST http://localhost:8090/engineering/negotiations/request
Authorization: Basic <connector-credentials>
Content-Type: application/json

{
  "@context": "https://w3id.org/dspace/2025/1/context.jsonld",
  "@type": "dspace:ContractRequestMessage",
  "dspace:dataset": "urn:uuid:<datasetId>",
  ...
}
```

The consumer's `callbackAddress` must include the consumer's tenant prefix so the provider can call back correctly — e.g., `http://localhost:8080/engineering`.

---

## Frequently Asked Questions

**Q: Can I run the connector with a single tenant?**  
A: Yes. The default `engineering` tenant is always present. If you don't need multiple tenants, simply use the default and ignore tenant management.

**Q: Can a user belong to multiple tenants?**  
A: Not in the current implementation. A user is associated with exactly one tenant. Super-admins are an exception — they have cross-tenant access.

**Q: Does disabling a tenant affect in-progress negotiations or transfers?**  
A: New requests for a disabled tenant are rejected (403). Existing, in-progress negotiations and transfers in the database are not automatically terminated.

**Q: What happens if two tenants have a negotiation between them on the same connector instance?**  
A: This is not the typical use case. The connector is designed for inter-connector communication. Two tenants on the same instance negotiating with each other is not supported in the current implementation.
