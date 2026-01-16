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
| `/issuer/requests` | POST | Yes | Create credential request (alt) |
| `/issuer/requests/{id}` | GET | Yes | Get request status |
| `/issuer/requests/{id}/approve` | POST | Yes | Approve request |
| `/issuer/requests/{id}/reject` | POST | Yes | Reject request |
| `/issuer/admin/rotate-key` | POST | Yes | Manual key rotation |
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
  "holderPid": "did:web:holder.example.com",
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
- `holderPid` (string, required): DID of the credential holder
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

### Create Credential Request (Alternative)

Alternative endpoint for creating credential requests.

```http
POST /issuer/requests
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body**: Same as `/issuer/credentials`

**Response**: Same as `/issuer/credentials`

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
  "holderPid": "did:web:holder.example.com",
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

### Approve Request

Approves a pending credential request and triggers credential issuance.

```http
POST /issuer/requests/{requestId}/approve
Authorization: Bearer <token>
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

```json
{
  "message": "Request approved and credentials issued",
  "requestId": "abc123-def456-ghi789",
  "status": "APPROVED"
}
```

**Process**:
1. Updates request status to APPROVED
2. Generates verifiable credentials with custom claims
3. Delivers credentials to holder's DCP Storage endpoint
4. Updates request status to DELIVERED

**Status Codes**:
- `200 OK`: Request approved successfully
- `400 Bad Request`: Request already processed or invalid state
- `401 Unauthorized`: Missing or invalid Bearer token
- `404 Not Found`: Request not found
- `500 Internal Server Error`: Server error or credential delivery failed

---

### Reject Request

Rejects a pending credential request.

```http
POST /issuer/requests/{requestId}/reject
Authorization: Bearer <token>
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
- `reason` (string, optional): Reason for rejection

**Response**: `200 OK`

```json
{
  "message": "Request rejected",
  "requestId": "abc123-def456-ghi789",
  "status": "REJECTED",
  "reason": "Invalid request - holder not verified"
}
```

**Status Codes**:
- `200 OK`: Request rejected successfully
- `400 Bad Request`: Request already processed
- `401 Unauthorized`: Missing or invalid Bearer token
- `404 Not Found`: Request not found
- `500 Internal Server Error`: Server error

---

## Admin Endpoints

### Manual Key Rotation

Manually triggers cryptographic key rotation outside of the automated schedule.

```http
POST /issuer/admin/rotate-key
Authorization: Bearer <admin-token>
```

**Authentication**: Required (admin-level Bearer token)

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
     │                               │   POST /requests/123/approve  │
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
    "holderPid": "did:web:holder.example.com",
    "credentials": [{"id": "MembershipCredential"}]
  }'

# Response: Location: /issuer/requests/abc123

# 3. Admin approves request
curl -X POST http://localhost:8084/issuer/requests/abc123/approve \
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
- [Configuration Guide](CONFIGURATION.md)
- [Integration Guide](INTEGRATION.md)

