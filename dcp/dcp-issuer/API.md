# API Reference

Complete API documentation for DCP Issuer Service.

## Base URL

```
http://localhost:8084
```

For production, replace with your actual deployment URL.

## Authentication

The DCP protocol endpoints (`/issuer/metadata`, `/issuer/credentials`, `/issuer/requests/{id}`) require Bearer token authentication using a Self-Issued ID Token as per the DCP specification. The controllers validate the token; the security filter chain lets these requests through.

```http
Authorization: Bearer <self-issued-id-token>
```

The administration API (`/api/issuer/**`) requires HTTP Basic authentication of a user configured in `dcp.admin.users[n]` with the authority `DCP_ISSUER_ADMIN`:

```http
Authorization: Basic <base64(username:password)>
```

| Caller | Response |
|---|---|
| No credentials, wrong credentials, or a Bearer token | `401 Unauthorized` with `WWW-Authenticate: Basic realm="dcp-issuer-admin"` |
| Authenticated user without `DCP_ISSUER_ADMIN` | `403 Forbidden` |
| Administrator | The endpoint's own response |

No administration user is configured by default, so every administration request gets `401` until one is added (fail closed). See [DEPLOYMENT.md](DEPLOYMENT.md#administration-api-users) for the configuration.

DID documents (`/.well-known/did.json`, `/issuer/did.json`, `/issuer/.well-known/did.json`) and `/actuator/health` are public. Any other path is denied (`403`).

## Endpoints Overview

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/.well-known/did.json` | GET | No | Get issuer DID document |
| `/issuer/metadata` | GET | Yes | Get issuer metadata |
| `/issuer/credentials` | POST | Yes | Request credentials |
| `/issuer/requests/{id}` | GET | Yes | Get request status |
| `/issuer/status/{listId}` | GET | No | Get the signed status list credential (DCP §6.10) |
| `/api/issuer/requests/{id}/approve` | POST | HTTP Basic, `DCP_ISSUER_ADMIN` | Approve request (local admin API) |
| `/api/issuer/requests/{id}/reject` | POST | HTTP Basic, `DCP_ISSUER_ADMIN` | Reject request (local admin API) |
| `/api/issuer/status-lists/{listId}/entries/{index}/revoke` | POST | HTTP Basic, `DCP_ISSUER_ADMIN` | Revoke a credential (local admin API) |
| `/api/issuer/key/rotate` | POST | HTTP Basic, `DCP_ISSUER_ADMIN` | Manual key rotation (local admin API) |
| `/actuator/health` | GET | No | Health check |

---

## Public Endpoints

### Get Issuer DID Document

Retrieves the issuer's DID document containing public keys and service endpoints.

```http
GET /.well-known/did.json
```

**Authentication**: None (public endpoint)

**Response**: `200 OK`

```json
{
  "@context": [
    "https://www.w3.org/ns/did/v1"
  ],
  "id": "did:web:localhost%3A8084:issuer",
  "verificationMethod": [
    {
      "id": "did:web:localhost%3A8084:issuer#key-1",
      "type": "JsonWebKey2020",
      "controller": "did:web:localhost%3A8084:issuer",
      "publicKeyJwk": {
        "kty": "EC",
        "crv": "P-256",
        "x": "...",
        "y": "..."
      }
    }
  ],
  "authentication": [
    "did:web:localhost%3A8084:issuer#key-1"
  ],
  "assertionMethod": [
    "did:web:localhost%3A8084:issuer#key-1"
  ],
  "service": [
    {
      "id": "did:web:localhost%3A8084:issuer#IssuerService",
      "type": "IssuerService",
      "serviceEndpoint": "http://localhost:8084/issuer"
    }
  ]
}
```

**Status Codes**:
- `200 OK`: DID document returned successfully
- `404 Not Found`: Issuer not configured or DID document not generated

---

### Get Status List Credential

Returns the signed status list credential that the `credentialStatus.statusListCredential` of an issued credential points at. Verifiers use it to check revocation (DCP v1.0.1 §6.10). The endpoint is public by design and carries only status bits, no holder data.

```http
GET /issuer/status/{listId}
```

**Authentication**: None

**Path Parameters**:
- `listId` (string, required): List ID, the last path segment of the `statusListCredential` URL

**Response**: `200 OK` with a compact JWT signed by the Issuer's current signing key. The format follows the profile of the credentials in the list:

| Profile | Content-Type | Credential |
|---------|--------------|------------|
| `vc20-bssl/jwt` | `application/vc+ld+jwt` | VC 2.0 `BitstringStatusListCredential` as JWT claims; `credentialSubject.type` is `BitstringStatusList` |
| `vc11-sl2021/jwt` | `application/jwt` | VC 1.1 `StatusList2021Credential` in the `vc` claim; `credentialSubject.type` is `StatusList2021` |

Decoded claims of a `vc20-bssl/jwt` list:

```json
{
  "iss": "did:web:localhost%3A8084:issuer",
  "sub": "http://localhost:8084/issuer/status/3f2c...#list",
  "@context": ["https://www.w3.org/ns/credentials/v2"],
  "id": "http://localhost:8084/issuer/status/3f2c...",
  "type": ["VerifiableCredential", "BitstringStatusListCredential"],
  "issuer": {"id": "did:web:localhost%3A8084:issuer"},
  "validFrom": "2026-11-20T10:00:00Z",
  "credentialSubject": {
    "id": "http://localhost:8084/issuer/status/3f2c...#list",
    "type": "BitstringStatusList",
    "statusPurpose": "revocation",
    "encodedList": "uH4sIAAAAAAAA..."
  }
}
```

`encodedList` is the GZIP-compressed bitstring, base64url-encoded without padding; BitstringStatusList adds the multibase prefix `u`. Index 0 is the left-most bit of the first byte. A bit of 0 means valid, 1 means revoked. Each list has 131072 entries (16 KB, the minimum the status list specifications set for group privacy).

**Status Codes**:
- `200 OK`: List found and returned
- `404 Not Found`: Unknown list

---

## Protected Endpoints

### Get Issuer Metadata

Returns metadata about credential types supported by this issuer per DCP specification.

```http
GET /issuer/metadata
Authorization: Bearer <self-issued-id-token>
```

**Authentication**: Required (Bearer token)

**Response**: `200 OK`

```json
{
  "issuer": "did:web:localhost%3A8084:issuer",
  "credentialsSupported": [
    {
      "id": "MembershipCredential",
      "type": "CredentialObject",
      "credentialType": "MembershipCredential",
      "profile": "VC20_BSSL_JWT",
      "format": "jwt_vc"
    },
    {
      "id": "OrganizationCredential",
      "type": "CredentialObject",
      "credentialType": "OrganizationCredential",
      "profile": "VC11_SL2021_JWT",
      "format": "jwt_vc"
    }
  ]
}
```

**Status Codes**:
- `200 OK`: Metadata returned successfully
- `401 Unauthorized`: Missing or invalid Bearer token
- `500 Internal Server Error`: Server error

---

### Request Credentials

Initiates a credential request. Creates a pending request that must be approved by an administrator.

```http
POST /issuer/credentials
Authorization: Bearer <self-issued-id-token>
Content-Type: application/json
```

**Request Body**:

```json
{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "CredentialRequestMessage",
  "holderPid": "8a1f5c3e-2b7d-4e9a-9c1d-5f0e7b6a2d41",
  "credentials": [
    {
      "id": "MembershipCredential"
    },
    {
      "id": "OrganizationCredential"
    }
  ]
}
```

**Parameters**:
- `@context` (array, required): Must contain `https://w3id.org/dspace-dcp/v1.0/dcp.jsonld`
- `type` (string, required): `CredentialRequestMessage`
- `holderPid` (string, required): Request identifier on the Holder side (DCP §6.4.1). It is not the Holder DID; the Issuer takes the holder's DID from the `sub` of the Self-Issued ID Token
- `credentials` (array, required): List of credential types requested
  - `id` (string, required): Credential type identifier matching issuer metadata

**Response**: `201 Created`

```http
HTTP/1.1 201 Created
Location: /issuer/requests/abc123-def456-ghi789
```

**Status Codes**:
- `201 Created`: Request created successfully, check `Location` header for request ID
- `400 Bad Request`: Invalid request body or unsupported credential type
- `401 Unauthorized`: Missing or invalid Bearer token
- `500 Internal Server Error`: Server error

---

### Get Request Status

Retrieves the status of a credential request. Only the client that created the request (the token subject equals the DID stored with the request) can read it; any other authenticated client receives `404 Not Found`, the same response as for an unknown request.

```http
GET /issuer/requests/{requestId}
Authorization: Bearer <token>
```

**Path Parameters**:
- `requestId` (string, required): Request identifier from Location header

**Response**: `200 OK`

```json
{
  "issuerPid": "abc123-def456-ghi789",
  "holderPid": "8a1f5c3e-2b7d-4e9a-9c1d-5f0e7b6a2d41",
  "status": "PENDING",
  "credentialIds": [
    "MembershipCredential",
    "OrganizationCredential"
  ],
  "createdAt": "2026-01-13T10:00:00Z",
  "updatedAt": "2026-01-13T10:00:00Z"
}
```

**Request Status Values**:
- `PENDING`: Awaiting approval
- `APPROVED`: Approved and credentials issued
- `REJECTED`: Request rejected
- `DELIVERED`: Credentials delivered to holder

**Status Codes**:
- `200 OK`: Request found and returned
- `401 Unauthorized`: Missing or invalid Bearer token
- `404 Not Found`: Request not found, or not created by the calling client
- `500 Internal Server Error`: Server error

---

## Admin Endpoints

These endpoints form the local administration API of the Issuer, mounted at `/api/issuer`. They are not DCP protocol endpoints and are not defined by the DCP specification. Every path under `/api/issuer/` requires HTTP Basic authentication of an administration user with the authority `DCP_ISSUER_ADMIN` (see [Authentication](#authentication)): `401 Unauthorized` without valid credentials, `403 Forbidden` without the authority. Send the credentials over TLS only.

### Approve Request

Approves a pending credential request and triggers credential issuance.

```http
POST /api/issuer/requests/{requestId}/approve
Authorization: Basic <base64(username:password)>
Content-Type: application/json
```

**Path Parameters**:
- `requestId` (string, required): Request identifier

**Request Body**:

```json
{
  "customClaims": {
    "membershipNumber": "12345",
    "role": "admin",
    "country_code": "IT",
    "organization": "Example Corp"
  }
}
```

**Parameters**:
- `customClaims` (object, optional): Additional claims to include in credentials

**Response**: `200 OK`

```text
Credentials issued and delivered successfully
```

**Process**:
1. Updates request status to APPROVED
2. Generates verifiable credentials with custom claims
3. Delivers credentials to holder's DCP Storage endpoint
4. Updates request status to DELIVERED

**Status Codes**:
- `200 OK`: Request approved successfully
- `400 Bad Request`: Request already processed or invalid state
- `401 Unauthorized`: Missing or invalid administrator credentials
- `403 Forbidden`: Authenticated user without `DCP_ISSUER_ADMIN`
- `404 Not Found`: Request not found
- `500 Internal Server Error`: Server error or credential delivery failed. If any requested credential cannot be issued, no credential is delivered and the holder receives a `REJECTED` message with a reason naming the failed credential IDs (DCP 1.0.1 §6.5.1)

---

### Reject Request

Rejects a pending credential request.

```http
POST /api/issuer/requests/{requestId}/reject
Authorization: Basic <base64(username:password)>
Content-Type: application/json
```

**Path Parameters**:
- `requestId` (string, required): Request identifier

**Request Body**:

```json
{
  "reason": "Invalid request - holder not verified"
}
```

**Parameters**:
- `reason` (string, required): Reason for rejection; the endpoint returns `400 Bad Request` when it is missing or blank

**Response**: `200 OK`

```text
Credential request rejected successfully
```

**Status Codes**:
- `200 OK`: Request rejected successfully
- `400 Bad Request`: Request already processed
- `401 Unauthorized`: Missing or invalid administrator credentials
- `403 Forbidden`: Authenticated user without `DCP_ISSUER_ADMIN`
- `404 Not Found`: Request not found
- `500 Internal Server Error`: Server error

---

### Revoke Credential

Revokes the credential that holds a status list entry: sets the entry's bit to 1 and re-signs the published status list. Take `listId` and `index` from the credential's `credentialStatus` (`statusListCredential` ends with `/issuer/status/{listId}`; `statusListIndex` is the index). Revocation is not reversible. The Issuer emits the `CREDENTIAL_REVOKED` audit event.

```http
POST /api/issuer/status-lists/{listId}/entries/{index}/revoke
```

**Path Parameters**:
- `listId` (string, required): Status list ID
- `index` (integer, required): Status list index of the credential

**Response**: `200 OK`

```text
Credential revoked
```

Revoking an entry that is already revoked answers `200 OK` with `Credential already revoked` and changes nothing.

**Status Codes**:
- `200 OK`: Entry revoked, or already revoked
- `400 Bad Request`: The index has not been allocated to a credential
- `404 Not Found`: Unknown status list

---

### Manual Key Rotation

Manually triggers cryptographic key rotation outside of the automated schedule.

```http
POST /api/issuer/key/rotate
Authorization: Basic <base64(username:password)>
```

**Authentication**: HTTP Basic, authority `DCP_ISSUER_ADMIN` (`401` without valid credentials, `403` without the authority)

**Response**: `200 OK`

```json
{
  "message": "Key rotated successfully",
  "newKeyAlias": "issuer-20260113120000-abc123",
  "oldKeyAlias": "issuer-20251015100000-xyz789",
  "timestamp": 1736765400000
}
```

**Process**:
1. Generates new EC key pair
2. Archives current key in keystore
3. Updates DID document with new public key
4. Stores key metadata in MongoDB

**Status Codes**:
- `200 OK`: Key rotation successful
- `401 Unauthorized`: Missing or invalid administrator credentials
- `403 Forbidden`: Authenticated user without `DCP_ISSUER_ADMIN`
- `500 Internal Server Error`: Key rotation failed

---

## Health & Monitoring

### Health Check

```http
GET /actuator/health
```

**Authentication**: None

**Response**: `200 OK`

```json
{
  "status": "UP",
  "components": {
    "mongo": {
      "status": "UP"
    },
    "diskSpace": {
      "status": "UP"
    }
  }
}
```

**Status Codes**:
- `200 OK`: Service is healthy
- `503 Service Unavailable`: Service is down or unhealthy

### Metrics

```http
GET /actuator/metrics
```

Returns Prometheus-compatible metrics.

### Info

```http
GET /actuator/info
```

Returns application information.

---

## Credential Issuance Flow

```
┌──────────┐                    ┌──────────┐                    ┌──────────┐
│  Holder  │                    │  Issuer  │                    │  Admin   │
└────┬─────┘                    └────┬─────┘                    └────┬─────┘
     │                               │                               │
     │ 1. Request Credential         │                               │
     ├──────────────────────────────►│                               │
     │   POST /issuer/credentials    │                               │
     │                               │                               │
     │ 2. Request Created (201)      │                               │
     │◄──────────────────────────────┤                               │
     │   Location: /requests/123     │                               │
     │                               │                               │
     │                               │ 3. Review Request             │
     │                               │◄──────────────────────────────┤
     │                               │   GET /requests/123           │
     │                               │                               │
     │                               │ 4. Approve Request            │
     │                               │◄──────────────────────────────┤
     │                               │   POST /api/issuer/.../approve│
     │                               │                               │
     │                               │ 5. Generate & Sign VCs        │
     │                               │────────┐                      │
     │                               │        │                      │
     │                               │◄───────┘                      │
     │                               │                               │
     │ 6. Deliver Credentials        │                               │
     │◄──────────────────────────────┤                               │
     │   POST /dcp/credentials       │                               │
     │   (to holder's endpoint)      │                               │
     │                               │                               │
     │ 7. Acknowledge Receipt        │                               │
     ├──────────────────────────────►│                               │
     │   200 OK                      │                               │
     │                               │                               │
```

---

## Error Responses

All endpoints return consistent error responses:

```json
{
  "timestamp": "2026-01-13T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Credential type 'InvalidCredential' is not supported",
  "path": "/issuer/credentials"
}
```

**Common Status Codes**:
- `400 Bad Request`: Invalid request parameters
- `401 Unauthorized`: Missing or invalid authentication
- `403 Forbidden`: Insufficient permissions
- `404 Not Found`: Resource not found
- `500 Internal Server Error`: Server error
- `503 Service Unavailable`: Service temporarily unavailable

---

## Rate Limiting

**Recommended**: Implement rate limiting for production deployments:

- **Public endpoints**: 100 requests/minute per IP
- **Protected endpoints**: 20 requests/minute per token
- **Admin endpoints**: 5 requests/minute per token

---

## API Versioning

Current API version: **v1.0** (implicit, no version in path)

Future versions will use path-based versioning: `/v2/issuer/...`

---

## Examples

### Complete Request Flow Example

```bash
# 1. Get issuer DID document
curl http://localhost:8084/.well-known/did.json

# 2. Request credential (with token)
curl -X POST http://localhost:8084/issuer/credentials \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{
    "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
    "type": "CredentialRequestMessage",
    "holderPid": "8a1f5c3e-2b7d-4e9a-9c1d-5f0e7b6a2d41",
    "credentials": [{"id": "MembershipCredential"}]
  }'

# Response: Location: /issuer/requests/abc123

# 3. Admin approves request
curl -X POST http://localhost:8084/api/issuer/requests/abc123/approve \
  -u issuer-admin:<admin password> \
  -H "Content-Type: application/json" \
  -d '{
    "customClaims": {
      "membershipNumber": "12345",
      "role": "member"
    }
  }'

# 4. Check status
curl -H "Authorization: Bearer eyJhbGc..." \
     http://localhost:8084/issuer/requests/abc123
```

---

**See Also**:
- [Quick Start Guide](QUICKSTART.md)

