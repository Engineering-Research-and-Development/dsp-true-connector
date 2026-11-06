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

In `INTERNAL` (and `DISABLED`) authentication mode the connector has implemented simple user management, stored in MongoDB, including following:

 - list user by email
 - list all uers
 - create user
 - update user
 - update password
 
All endpoints require `Content-Type: application/json` and an `Authorization` header for a user with `ROLE_SUPER_ADMIN`. `ROLE_ADMIN` users cannot access these endpoints and will receive HTTP 403.

These endpoints do **not** exist in `KEYCLOAK` mode, see [Users in Keycloak mode](#users-in-keycloak-mode).
 
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
> - `callbackAddress` is **not** a stored field — it is derived at runtime as `${application.baseURL}/{id}`.
> - Bucket onboarding supports three request shapes:
>   - no bucket fields: fully automatic bucket provisioning (`dsp-{tenantId}`)
>   - `bucketName` only: reuse an existing bucket and ensure credentials for that name
>   - `bucketName` + `accessKey` + `secretKey`: persist externally supplied credentials (optionally with `verifyConnection=true`)

```json
{
  "id"           : "my-tenant",
  "name"         : "My Tenant",
  "description"  : "Optional tenant description",
  "participantId": "urn:connector:my-tenant",
  "automaticNegotiation": false,
  "automaticTransfer": false,
  "enabled": true
}
```

Existing-bucket mode:

```json
{
  "id"           : "my-tenant-existing-bucket",
  "name"         : "My Tenant Existing Bucket",
  "participantId": "urn:connector:my-tenant-existing-bucket",
  "bucketName"   : "existing-bucket-name"
}
```

External-credentials mode:

```json
{
  "id"               : "my-tenant-external-credentials",
  "name"             : "My Tenant External Credentials",
  "participantId"    : "urn:connector:my-tenant-external-credentials",
  "bucketName"       : "external-bucket-name",
  "accessKey"        : "external-access-key",
  "secretKey"        : "external-secret-key",
  "verifyConnection" : true
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

Create-tenant validation errors return HTTP 400 in these cases:

- invalid bucket-field combinations (for example only `accessKey`, or `bucketName` + `accessKey` without `secretKey`)
- `bucketName` already owned by another tenant
- `verifyConnection=true` with external credentials that fail bucket connectivity verification

### Update tenant

`PUT /api/v1/tenants/{id}`

Mutable fields: `name`, `description`, `automaticNegotiation`, `automaticTransfer`, and optional
bucket-rotation/migration inputs (`bucketName`, `accessKey`, `secretKey`, `verifyConnection`).

The `participantId` and `enabled` state are immutable in this endpoint.

```json
{
  "name"                 : "Updated Tenant Name",
  "automaticNegotiation" : true,
  "automaticTransfer"    : true
}
```

Bucket migration (existing bucket mode):

```json
{
  "name"                 : "Updated Tenant Name",
  "automaticNegotiation" : true,
  "automaticTransfer"    : true,
  "bucketName"           : "existing-bucket-name"
}
```

Credential rotation / migration with external credentials:

```json
{
  "name"                 : "Updated Tenant Name",
  "automaticNegotiation" : true,
  "automaticTransfer"    : true,
  "bucketName"           : "external-bucket-name",
  "accessKey"            : "external-access-key",
  "secretKey"            : "external-secret-key",
  "verifyConnection"     : true
}
```

Update validation errors return HTTP 400 in these cases:

- invalid bucket-field combinations (for example only `accessKey`, or `bucketName` + `accessKey` without `secretKey`)
- `bucketName` already owned by another tenant (self-reconfirm is allowed)
- `verifyConnection=true` with external credentials that fail bucket connectivity verification

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

### Users in Keycloak mode

When the connector runs in **Keycloak authentication mode** (`application.auth.provider=KEYCLOAK`), it does **not** create, list or change users. There is no user API in this mode: `/api/v1/users/**` answers `404` for a super-admin and `403` for any other token, and `PUT /api/v1/users/{id}/password` is not offered either. Users are created and managed in Keycloak itself (Admin Console, Admin REST API or realm import); the connector only validates the tokens Keycloak issues to them.

Create each user in the realm that matches its role. Do not mix super-admins with regular tenant users:

| Who | Realm | Property / rule |
|-----|-------|-----------------|
| **Super-admins** | `connector-admin` | `application.keycloak.admin-realm` (default `connector-admin`). The user needs the realm role `SUPER_ADMIN` and **no** `tenant_id` attribute. A super-admin selects a tenant per request with the `X-Tenant-Id` header. |
| **Tenant users** | The tenant's own realm, e.g. `tenant-EngineeringA` | The tenant's `realm` field. Each user needs the realm role `ADMIN` and a `tenant_id` attribute (mapped to the `tenant_id` claim) equal to the tenant `_id`, e.g. `engineering-a`, not the realm name. |
| **Machine-to-machine** | `platform` | `application.keycloak.platform-realm`, client `dsp-connector-backend` with realm role `CONNECTOR`. Not a human user. |

A tenant user's token is accepted only when both of these hold:

1. **A Mongo tenant owns the realm.** An enabled tenant has the realm name in its `realm` field. Otherwise the connector does not trust the realm and every token from it gets `401`.
2. **The `tenant_id` claim is that tenant's `_id`.** If it holds anything else, such as the realm name, the token gets `401`.

The seed data follows both rules:

| Realm | Mongo tenant (`_id`, seeded in) | `tenant_id` of the realm users |
|-------|--------------------------------|--------------------------------|
| `tenant-EngineeringA` | `engineering-a`, connector A seeds | `engineering-a` |
| `tenant-EngineeringB` | `engineering-b`, connector B seeds | `engineering-b` |
| `tenant-acme` (demo) | `acme`, only `connector/src/main/resources/initial_data.json` | `acme` |

The `tenant-acme` users (`coyote`, `roadrunner`) used to carry `tenant_id` `tenant-acme` (the realm name), and no Mongo tenant owned that realm, so their tokens were rejected. Both are fixed.

- A tenant realm can never be `master`, the platform realm or the admin realm, so a tenant user can never end up in the super-admin realm.
- A super-admin created in a tenant realm does not work: a tenant realm may not grant `SUPER_ADMIN`, so the token gets `401` and a `SECURITY_REALM_ROLE_VIOLATION` audit event.

See [security.md – User management](../../doc/security.md#user-management) and [Seed Realms and Tenant Identity](../../doc/security.md#seed-realms-and-tenant-identity) for the full rules, and [D-ARC-006](../../doc/decisions/architecture/D-ARC-006-per-realm-role-allow-list-and-per-tenant-realm-credentials.md) for the per-realm role rules. The earlier design in which the connector created users through the Keycloak Admin REST API ([D-TEC-004](../../doc/decisions/technical/D-TEC-004-keycloak-user-registration.md)) is superseded.
