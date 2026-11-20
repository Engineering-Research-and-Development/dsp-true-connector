# API Reference

Complete API documentation for DCP Issuer Service.

## Base URL

```
http://localhost:8084
```

For production, replace with your actual deployment URL.

## Authentication

Most endpoints require Bearer token authentication using Self-Issued ID Token as per DCP specification.

```http
Authorization: Bearer <self-issued-id-token>
```

## Endpoints Overview

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/.well-known/did.json` | GET | No | Get issuer DID document |
| `/issuer/metadata` | GET | Yes | Get issuer metadata |
| `/issuer/credentials` | POST | Yes | Request credentials |
| `/issuer/requests/{id}` | GET | Yes | Get request status |
| `/api/issuer/requests/{id}/approve` | POST | No, see #405 | Approve request (local admin API) |
| `/api/issuer/requests/{id}/reject` | POST | No, see #405 | Reject request (local admin API) |
| `/api/issuer/key/rotate` | POST | No, see #405 | Manual key rotation (local admin API) |
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

Retrieves the status of a credential request.

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
- `404 Not Found`: Request not found
- `500 Internal Server Error`: Server error

---

## Admin Endpoints

These endpoints form the local administration API of the Issuer, mounted at `/api/issuer`. They are not DCP protocol endpoints and are not defined by the DCP specification. Authentication is not enforced yet (the `dcp-issuer` module has no security filter chain; tracked in #405), so restrict network access to them until that is delivered.

### Approve Request

Approves a pending credential request and triggers credential issuance.

```http
POST /api/issuer/requests/{requestId}/approve
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
- `404 Not Found`: Request not found
- `500 Internal Server Error`: Server error or credential delivery failed. If any requested credential cannot be issued, no credential is delivered and the holder receives a `REJECTED` message with a reason naming the failed credential IDs (DCP 1.0.1 §6.5.1)

---

### Reject Request

Rejects a pending credential request.

```http
POST /api/issuer/requests/{requestId}/reject
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
- `404 Not Found`: Request not found
- `500 Internal Server Error`: Server error

---

### Manual Key Rotation

Manually triggers cryptographic key rotation outside of the automated schedule.

```http
POST /api/issuer/key/rotate
```

**Authentication**: Not enforced yet (see #405)

⚠️ **Important**: Protect this endpoint with proper admin authentication in production!

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
- `401 Unauthorized`: Missing or invalid admin token
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

