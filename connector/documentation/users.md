# Connector authorization

There are 2 types of authorizations that can access Connector endpoints:

 * connector  
 * human user
 
## Connector

This authorization does not represent human in real life, but it is used to identify and authorize other connectors performing when interacting with connector. Authorization will be used to access endpoints defined by [Dataspace Protocol.](https://docs.internationaldataspaces.org/ids-knowledgebase/dataspace-protocol)

 - [requesting catalog](https://docs.internationaldataspaces.org/ids-knowledgebase/dataspace-protocol/catalog/catalog.binding.https), 
 - [contract negotiation](https://docs.internationaldataspaces.org/ids-knowledgebase/dataspace-protocol/contract-negotiation/contract.negotiation.binding.https) 
 - [transfer process](https://docs.internationaldataspaces.org/ids-knowledgebase/dataspace-protocol/transfer-process/transfer.process.binding.https)
 
If connector protocol endpoints are secured with Basic authorization, then user with role CONNECTOR should be created. And when other connectors are interacting with it, they should send Authorization header with Basic auth.

In case when authorization is performed via Bearer token - in form of JWT (Json Web Token), then connector authorization should not exist. 
JWToken should be send with each request and connector will evaluate token and if all checks are successful, action will be allowed.

## Human user

Human user represents real human user, responsible for interacting with connector, via API endpoints:
 - making configuration modifications 
 - updating catalog
 - interacting with contract negotiation (start, approve, verify...)
 - performing actual data transfer
 
This user is identified with username, password and role.ADMIN. From API perspective, authorization is done using Basic authorization (header key - 'Authorization', header value 'Basic encoded(username:password)'.

Information about users and their roles are stored in database.

## User API endpoints (/api/v1/users)

Connector has implemented simple user management, including following:

 - list user by email
 - list all uers
 - create user
 - update user
 - update password
 
All endpoints require `Content-Type: application/json` and an `Authorization` header for a user with `ROLE_SUPER_ADMIN`. `ROLE_ADMIN` users cannot access these endpoints and will receive HTTP 403.
 
### Create user 

POST request 

When creating new user request should be like following:

```
{
  "firstName" : "GHA Test user",
  "lastName" : "DSP-TRUEConnector",
  "email" : "user_gha@mail.com",
  "password" : "GhaPassword123!",
  "role" : "ROLE_ADMIN"
}
```

### Update user (can be only for self)

*/api/v1/users/{{userId}}/update*

PUT request

```
{
  "firstName" : "UPDATE_NAME",
  "lastName" : "UPDATE_LAST_NAME"
}

```

It will check if any of fields are passed and if is, it will update firstName and/or lastname.

If updating other user (than logged in), connector will return error message.

### Update password (for self)

*/api/v1/users/{{userId}}/password*
 
 PUT request
 
```
 {
  "newPassword" : "ValidaPasswordUpdate123!",
  "password" : "ValidPassword123!"
}
```

 - It will check if password matches with existing password 
 - Password validity enforcement for new password will be applied (min/max length, must contains digits, lower/upper case, special characters...)
 - If both checks are ok, old password will be replaced with new value

## Tenant API endpoints (/api/v1/tenants)

Connector supports multi-tenant operation. Tenants are managed via the tenant API (SUPER_ADMIN role required).

All endpoints require `Content-Type: application/json` and an Authorization header for a user with `ROLE_SUPER_ADMIN`.

| Method | Path | Description |
|---|---|---|
| GET    | `/api/v1/tenants`            | List all tenants |
| GET    | `/api/v1/tenants/{id}`       | Get tenant by ID |
| POST   | `/api/v1/tenants`            | Create a tenant |
| PUT    | `/api/v1/tenants/{id}`       | Update a tenant |
| PUT    | `/api/v1/tenants/{id}/enable`  | Enable a tenant |
| PUT    | `/api/v1/tenants/{id}/disable` | Disable a tenant |
| DELETE | `/api/v1/tenants/{id}`       | Delete a tenant |

### Create tenant

POST request

> **Important**: `id`, `name`, and `participantId` are **required**.
> - `id` is **chosen by the caller** and must consist only of alphanumeric characters and hyphens (e.g. `my-tenant`). The server does not auto-generate it.
> - `participantId` is the DSP participant identity for this tenant and must be unique across all tenants.
> - `callbackAddress` is **not** a stored field — it is derived at runtime as `${application.callback.address}/{id}`.

```json
{
  "id"           : "my-tenant",
  "name"         : "My Tenant",
  "description"  : "Optional tenant description",
  "participantId": "urn:connector:my-tenant"
}
```

Example response:

```json
{
  "success": true,
  "data": {
    "id"                   : "my-tenant",
    "name"                 : "My Tenant",
    "description"          : "Optional tenant description",
    "participantId"        : "urn:connector:my-tenant",
    "automaticNegotiation" : false,
    "automaticTransfer"    : false,
    "enabled"              : false,
    "bucketName"           : null
  }
}
```

A newly created tenant is **disabled** by default. Call `PUT /api/v1/tenants/{id}/enable` to activate it before assigning users.

### Update tenant

`PUT /api/v1/tenants/{id}`

Mutable fields: `name`, `description`, `participantId`, `automaticNegotiation`, `automaticTransfer`, `bucketName`.

The `enabled` state is **not** changed by this endpoint — use the dedicated enable/disable endpoints instead.

```json
{
  "name"                 : "Updated Tenant Name",
  "participantId"        : "urn:connector:my-tenant-v2",
  "automaticNegotiation" : true,
  "automaticTransfer"    : true
}
```

### Enable / disable tenant

```
PUT /api/v1/tenants/{id}/enable
PUT /api/v1/tenants/{id}/disable
```

These endpoints toggle the `enabled` flag. A disabled tenant's users cannot perform protocol operations and are rejected from tenant context resolution.

### Delete tenant

```
DELETE /api/v1/tenants/{id}
```

Deletes the tenant record. If the tenant had an S3 bucket configured (`bucketName`), the bucket is **not** automatically deleted to prevent accidental data loss. Clean it up manually once all artifact data has been migrated or is no longer needed.

## User management and multi-tenancy

### tenantId field

From the MT1 release, the create-user request body accepts an optional `tenantId` field that links the user to a specific tenant:

```json
{
  "firstName" : "Alice",
  "lastName"  : "Example",
  "email"     : "alice@example.com",
  "password"  : "SecurePass123!",
  "role"      : "ROLE_ADMIN",
  "tenantId"  : "my-tenant"
}
```

Rules:

- If `tenantId` is provided it **must reference an enabled tenant** (`enabled = true`). If the tenant does not exist or is disabled, the connector returns a 4xx error.
- Users with `ROLE_SUPER_ADMIN` are **exempt** from tenant-existence validation; they may be created without a `tenantId`.

### Keycloak mode user creation

When the connector is running in **Keycloak authentication mode** (`application.auth.provider=KEYCLOAK`), the `POST /api/v1/users` endpoint delegates to `KeycloakUserService`, which:

1. Obtains a client-credentials token from Keycloak using the configured service account.
2. Calls the Keycloak Admin REST API (`POST /admin/realms/{realm}/users`) to create the user in the Keycloak realm.
3. Returns the user JSON on success.

Required properties for Keycloak mode:

| Property | Description |
|---|---|
| `application.keycloak.admin.server-url` | Base URL of the Keycloak server (e.g. `http://keycloak:8080`) |
| `application.keycloak.admin.realm`      | Realm in which users should be created (e.g. `dsp-connector`) |
| `application.keycloak.server-url`       | Keycloak server for token validation (existing property) |
| `application.keycloak.realm`            | Token validation realm (existing property) |

The service account configured under `application.keycloak.client.*` must have the `manage-users` role assigned in the target realm.

See [ADR D-TEC-001](../../doc/decisions/technical/D-TEC-001-keycloak-user-registration.md) for the design rationale.