# Connector Platform Features — Operator Guide

> **See also:** [Technical Documentation](./connector-technical.md) | [User Management](./users.md) | [Negotiation](./negotiation.md) | [Transfer](./transfer.md)

## What is the Connector Module?

The connector is the **main application**. When you start the TRUE Connector, you are starting this module, which in
turn loads everything else: the catalog, contract negotiation, and data-transfer protocol engines.

On top of the DSP protocol logic, the connector module handles the things that keep the application running safely:

- **Security** — who is allowed to call which endpoints, and how they prove their identity
- **User accounts** — creating and managing the human operators who configure and operate the connector
- **Protocol version discovery** — a standard endpoint that lets other connectors check DSP compatibility
- **Database** — all data is stored in MongoDB; the module sets this up and seeds initial data on first start

---

## Security & Authentication

### How Authentication Works

There are two separate authentication mechanisms, each used for a different type of caller:

**1. Human operators — Basic Auth**

When you (or any management tool) calls the management API (`/api/v1/...`), you authenticate with your email address
and password using HTTP Basic Authentication:

```
Authorization: Basic <base64(email:password)>
```

The connector checks your credentials against the user database and, if valid and your account has `ROLE_ADMIN`, grants
access.

**2. Connector-to-connector — JWT Bearer Token**

When a remote DSP connector calls the protocol endpoints (`/catalog/**`, `/negotiations/**`, `/transfers/**`,
`/connector/**`), it presents a JWT token issued by a DAPS (Dynamic Attribute Provisioning Service):

```
Authorization: Bearer <jwt-token>
```

The connector validates the token's signature against the DAPS JWKS endpoint. This is automatic — your operators do
not manage these tokens directly.

**Disabling protocol authentication (development/testing only)**

Protocol endpoint authentication can be disabled at runtime by setting the `application.protocol.authentication.enabled`
property to `false` in the database. When disabled, protocol requests are accepted without a JWT token. This must
**never** be used in a production environment.

### Open Endpoints

The following endpoints require no authentication:

- `GET /.well-known/dspace-version` — DSP version discovery

### Default Users

Two users are created the first time the connector starts (from `initial_data.json`):

| Email | Role | Default Password | Purpose |
|---|---|---|---|
| `admin@mail.com` | `ROLE_ADMIN` | `password` | Human operator account for managing the connector |
| `connector@mail.com` | `ROLE_CONNECTOR` | `password` | Connector identity for basic-auth-secured protocol endpoints |

> ⚠️ **Change these passwords immediately after first startup.** See [Changing Password](#changing-password) below.

### Password Requirements

All passwords set through the management API must satisfy these rules (configurable via
`application.password.validator.*` properties):

| Rule | Default |
|---|---|
| Minimum length | 8 characters |
| Maximum length | 16 characters |
| Minimum lowercase letters | 1 |
| Minimum uppercase letters | 1 |
| Minimum digits | 1 |
| Minimum special characters | 1 |

Example of a valid password: `SecureP4ss!`

---

## Managing Users

All user management endpoints are under `/api/v1/users` and require Basic Auth with an account that has `ROLE_ADMIN`.

### Viewing Users

**List all users:**

```bash
curl -s -u admin@mail.com:password \
  -H "Content-Type: application/json" \
  http://localhost:8080/api/v1/users
```

**Find a specific user by email:**

```bash
curl -s -u admin@mail.com:password \
  -H "Content-Type: application/json" \
  http://localhost:8080/api/v1/users/jane.doe@example.com
```

### Creating a New User

Only `ROLE_ADMIN` users can be created for management purposes. To add a new operator account:

```bash
curl -s -u admin@mail.com:password \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Jane",
    "lastName": "Doe",
    "email": "jane.doe@example.com",
    "password": "SecureP4ss!",
    "role": "ROLE_ADMIN"
  }' \
  http://localhost:8080/api/v1/users
```

The connector will reject the request if:
- The email address is already registered.
- The password does not meet the strength requirements.

### Updating a User

You can update your own `firstName` and `lastName`. You cannot update another user's details.

You will need your own user's database `id` (visible in the GET response):

```bash
curl -s -u jane.doe@example.com:SecureP4ss! \
  -X PUT \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Janet",
    "lastName": "Smith"
  }' \
  http://localhost:8080/api/v1/users/<your-user-id>/update
```

Fields not included in the request body are left unchanged.

### Changing Password

You can only change your own password. The request must include both the current password and the new password:

```bash
curl -s -u jane.doe@example.com:SecureP4ss! \
  -X PUT \
  -H "Content-Type: application/json" \
  -d '{
    "password": "SecureP4ss!",
    "newPassword": "NewSecure5@"
  }' \
  http://localhost:8080/api/v1/users/<your-user-id>/password
```

The connector will:
1. Verify that `password` matches your current stored password.
2. Validate `newPassword` against the password strength rules.
3. Update the password only if both checks pass.

---

## Protocol Version Information

```
GET /.well-known/dspace-version
```

This endpoint is part of the [Dataspace Protocol](https://docs.internationaldataspaces.org/ids-knowledgebase/dataspace-protocol)
standard. Remote connectors call it automatically to check that the two connectors are running compatible DSP versions
before starting a catalog request or negotiation.

You do not normally need to call this yourself, but it is useful for diagnostics.

**Example request:**

```bash
curl -s http://localhost:8080/.well-known/dspace-version
```

**Example response:**

```json
{
  "protocolVersions": [
    {
      "binding": "HTTPS",
      "path": "/",
      "version": "2025-1",
      "identifierType": "",
      "serviceId": "",
      "auth": {
        "protocol": "https",
        "version": "2025-1",
        "profile": ["authorization_code", "refresh_token"]
      }
    }
  ]
}
```

The version (`2025-1`) and authentication details are read from the `application_properties` collection in MongoDB and
can be updated via the properties API (`/api/v1/properties`).

---

## Database

The connector uses **MongoDB** to store all data. A single MongoDB instance is shared across all modules.

### Collections

| Collection | Contents |
|---|---|
| `users` | Operator and connector user accounts |
| `application_properties` | Runtime configuration (DAPS URLs, DSP version, security flags) |
| `catalogs` | DSP catalog definitions |
| `datasets` | Dataset descriptions within catalogs |
| `dataservices` | Data service endpoints |
| `distributions` | Dataset distribution descriptions |
| `artifacts` | Artifact metadata and references |
| `contract_negotiations` | In-progress and completed contract negotiations |
| `agreements` | Finalised contract agreements |
| `transfer_request_messages` | Transfer request records |
| `transfer_process` | Transfer process state |
| `transfer_start_messages` | Transfer start message records |
| `policy_enforcements` | Policy enforcement audit records |
| `fs.files` / `fs.chunks` | GridFS binary storage (file data) |

All documents that are created or updated through the management API are automatically stamped with `createdBy`,
`lastModifiedBy` (the authenticated user's email), `createdDate`, and `lastModifiedDate` fields.

---

## Initial Setup

### What Happens on First Start

When the connector starts for the first time (or when the database is empty):

1. **MongoDB seed data is loaded** from the appropriate `initial_data*.json` classpath resource (selected by active
   Spring profile, or `initial_data.json` if no profile is active).
2. **Default users are created** — `admin@mail.com` and `connector@mail.com` with role-specific permissions.
3. **Application properties are inserted** — DAPS configuration, security flags, DSP version settings.
4. **Sample catalog data is inserted** — a sample catalog, dataset, data service, distribution, and artifact for
   demonstration purposes.
5. **Mock S3 data is uploaded** — `ENG-employee.json` is uploaded to the configured S3 bucket as sample artifact data.

On subsequent starts, existing documents (identified by `_id`) are skipped — the seed data load is **idempotent**.

### Profile-Specific Seed Data

| Spring Profile | Seed File |
|---|---|
| _(none / default)_ | `initial_data.json` |
| `provider` | `initial_data-provider.json` |
| `consumer` | `initial_data-consumer.json` |
| `tck` | `initial_data-tck.json` |

---

## Common Issues & Questions

**Q: How do I reset a forgotten password?**

There is no self-service password reset via the API. A MongoDB administrator must either update the BCrypt-encoded
password field directly in the `users` collection, or delete the user document so a new user with the same email can be
created via the API.

**Q: How do I add a new admin user?**

Use the `POST /api/v1/users` endpoint authenticated as an existing admin. Set `"role": "ROLE_ADMIN"` in the request
body. See [Creating a New User](#creating-a-new-user) above.

**Q: Where is the default admin account?**

Email: `admin@mail.com`, default password: `password`. This account is created from `initial_data.json` when the
database is first populated. Change this password immediately after deployment.

**Q: Can I disable JWT authentication for testing?**

Yes. Set the `application.protocol.authentication.enabled` property to `false` via the properties API. When disabled,
the connector accepts protocol requests without a valid JWT token and grants them `ROLE_CONNECTOR` access automatically.
Re-enable this before going to production.

**Q: Why can't I update another user's password or name?**

The connector enforces that you can only modify your own account. Attempting to update a different user's record returns
a `400 Bad Request` with the message `"Not allowed to change other user email"`.

**Q: What does `ROLE_CONNECTOR` do?**

It grants access to the DSP protocol endpoints (`/catalog/**`, `/negotiations/**`, `/transfers/**`, `/connector/**`).
This role is intended for connector identities used in basic-auth-secured protocol communication, not for human
operator accounts.

---

## See Also

- [Technical Documentation](./connector-technical.md) — implementation details for developers
- [User Management](./users.md) — detailed user API reference with request/response examples
- [Negotiation Notes](./negotiation.md) — contract negotiation scheduling configuration
- [Transfer Notes](./transfer.md) — data transfer data plane notes
