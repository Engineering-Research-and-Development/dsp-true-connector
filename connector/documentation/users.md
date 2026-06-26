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
 
All endpoints requires Content-Type: application/json, and Authorization header with username:password for existing user with role ADMIN.
 
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

### Create tenant

POST request

> **Important**: The `id` and `callbackAddress` fields in the request body are **ignored** — the server auto-generates both.
> - `id` is auto-generated as a random UUID.
> - `callbackAddress` is derived programmatically as `${application.callback.address}/{id}`.

```
{
  "name"       : "My Tenant",
  "description": "Optional tenant description",
  "connectorId": "urn:connector:my-tenant"
}
```

Example response:

```json
{
  "success": true,
  "data": {
    "id"              : "550e8400-e29b-41d4-a716-446655440000",
    "name"            : "My Tenant",
    "connectorId"     : "urn:connector:my-tenant",
    "callbackAddress" : "http://localhost:8080/550e8400-e29b-41d4-a716-446655440000",
    "enabled"         : false
  }
}
```

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
  "tenantId"  : "550e8400-e29b-41d4-a716-446655440000"
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