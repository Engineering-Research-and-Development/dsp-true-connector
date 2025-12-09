# Verifiable Credentials Guide

A comprehensive guide to understanding and using Verifiable Credentials (VC) in the DCP module.

---

## Table of Contents

1. [What are Verifiable Credentials?](#what-are-verifiable-credentials)
2. [Key Concepts](#key-concepts)
3. [The Issuer-Holder-Verifier Model](#the-issuer-holder-verifier-model)
4. [How the DCP Module Works](#how-the-dcp-module-works)
5. [API Endpoints and Use Cases](#api-endpoints-and-use-cases)
6. [Common Workflows](#common-workflows)
7. [Examples](#examples)

---

## What are Verifiable Credentials?

Imagine you have a digital passport or membership card that:
- **Can't be forged** - It's cryptographically signed, making tampering impossible
- **Is verifiable** - Anyone can check it's authentic without calling the issuer
- **Protects privacy** - You control what information you share and with whom
- **Works anywhere** - No central database needed

That's what Verifiable Credentials are! They're digital proofs of claims about you (or your organization) that can be independently verified.

### Real-World Analogy

Think of a Verifiable Credential like a university degree certificate:
- **Issuer**: The university that issues the certificate
- **Holder**: You (the graduate) who holds the certificate
- **Verifier**: An employer who wants to verify your degree is genuine
- **Claim**: "This person graduated with a Computer Science degree in 2024"

The difference? With Verifiable Credentials, the employer can verify the certificate's authenticity instantly without contacting the university.

---

## Key Concepts

### 1. **Claim**
An assertion about a subject. For example:
- "Organization X is a member of Dataspace Y"
- "Company Z has Gold membership status"
- "User A is authorized to access Resource B"

### 2. **Verifiable Credential (VC)**
A tamper-evident credential containing one or more claims, digitally signed by an issuer. It's like a digital certificate with built-in proof of authenticity.

**Example structure:**
```json
{
  "type": ["VerifiableCredential", "MembershipCredential"],
  "issuer": "did:example:university",
  "issuanceDate": "2024-12-01T00:00:00Z",
  "credentialSubject": {
    "id": "did:example:student123",
    "membershipLevel": "Gold",
    "status": "Active"
  },
  "proof": {
    "type": "JsonWebSignature2020",
    "created": "2024-12-01T00:00:00Z",
    "verificationMethod": "did:example:university#key-1",
    "jws": "eyJhbGc...signature..."
  }
}
```

### 3. **Verifiable Presentation (VP)**
A package containing one or more Verifiable Credentials that a Holder presents to a Verifier. Think of it as showing multiple ID cards together to prove different things about yourself.

### 4. **DID (Decentralized Identifier)**
A globally unique identifier that you control (like `did:web:example.com:user123`). It's used to identify participants and can be resolved to get public keys and service endpoints.

### 5. **Self-Issued ID Token**
A JSON Web Token (JWT) that a participant creates and signs themselves to prove their identity when making requests. It contains:
- **iss** (issuer): Your DID
- **sub** (subject): Your DID (same as issuer)
- **aud** (audience): The recipient's DID
- **token**: Optional access token for credential access

---

## The Issuer-Holder-Verifier Model

The DCP module implements the standard Issuer-Holder-Verifier model:

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  1. ISSUANCE                                                    │
│  ┌─────────┐                                        ┌─────────┐ │
│  │ Issuer  │────── Issues Credential ──────────────>│ Holder  │ │
│  └─────────┘       (e.g., membership cert)          └─────────┘ │
│      │                                                    │      │
│      │                                                    │      │
│      v                                                    v      │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │         Verifiable Data Registry (DID Registry)         │    │
│  │         - Stores DIDs and public keys                   │    │
│  │         - No credentials stored here!                   │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  2. PRESENTATION                                                │
│  ┌─────────┐                                     ┌──────────┐   │
│  │ Holder  │────── Presents Credential ─────────>│ Verifier │   │
│  └─────────┘       (when needed)                 └──────────┘   │
│                                                        │         │
│                                                        v         │
│                                             3. Verifier checks:  │
│                                             - Signature valid?   │
│                                             - Not expired?       │
│                                             - Not revoked?       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Actors

1. **Issuer**: A trusted authority that issues credentials (e.g., a Dataspace Governance Authority issuing membership credentials)

2. **Holder**: An entity that receives and stores credentials (e.g., your organization)

3. **Verifier**: An entity that requests and verifies credentials (e.g., a data provider checking if you're a member)

4. **Verifiable Data Registry**: A system (often blockchain or DID registry) that stores DIDs and public keys for verification

---

## How the DCP Module Works

The DCP (Decentralized Claims Protocol) module provides two main services:

### 1. **Credential Service** (`/dcp/*` endpoints)
Acts as a secure vault for your Verifiable Credentials:
- **Receives** credentials issued to you
- **Stores** them securely
- **Presents** them to authorized verifiers

### 2. **Issuer Service** (`/issuer/*` endpoints)
Allows you to act as a credential issuer:
- **Receives** credential requests from holders
- **Issues** credentials to holders
- **Tracks** request status

### Authentication & Security

All endpoints require a **Self-Issued ID Token** in the `Authorization` header:
```
Authorization: Bearer eyJraWQiOiJkaWQ6ZXhhbXBsZS...
```

This token proves:
- Who you are (your DID)
- That you control your private key
- What you're authorized to do

---

## API Endpoints and Use Cases

### 📥 Holder Endpoints (Receiving & Presenting Credentials)

#### 1. **Receive Credentials** - `POST /dcp/credentials`

**Use Case**: An issuer sends you credentials after approving your request.

**What it does**:
- Accepts credentials issued to you
- Validates the issuer's signature
- Stores credentials in your vault

**Authentication**: Requires Bearer token from the **Issuer**

**Request Example**:
```bash
POST /dcp/credentials
Authorization: Bearer <issuer-token>
Content-Type: application/json

{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "CredentialMessage",
  "issuerPid": "issuer-request-123",
  "holderPid": "did:web:example.com:holder",
  "status": "ISSUED",
  "credentials": [
    {
      "credentialType": "MembershipCredential",
      "payload": "eyJraWQiOiJkaWQ6...",  // JWT format
      "format": "jwt"
    }
  ]
}
```

**Response**:
```json
{
  "saved": 1,
  "skipped": 0
}
```

**When to use**: Automatically called by issuers. You don't typically call this directly unless implementing custom issuance flows.

---

#### 2. **Present Credentials** - `POST /dcp/presentations/query`

**Use Case**: A verifier wants to check your credentials before granting access.

**What it does**:
- Verifier queries for specific credentials
- You create a Verifiable Presentation
- Presentation is returned containing requested credentials

**Authentication**: Requires Bearer token from the **Holder**

**Request Example**:
```bash
POST /dcp/presentations/query
Authorization: Bearer <holder-token>
Content-Type: application/json

{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "PresentationQueryMessage",
  "scope": [
    "org.eclipse.dspace.dcp.vc.type:MembershipCredential"
  ]
}
```

**Scope Options**:
- `org.eclipse.dspace.dcp.vc.type:MembershipCredential` - Request credential by type
- `org.eclipse.dspace.dcp.vc.id:8247b87d-...` - Request credential by ID

**Response**:
```json
{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "PresentationResponseMessage",
  "presentation": [
    "eyJraWQiOiJkaWQ6ZXhhbXBsZS..."
  ]
}
```

**Note:** The `presentation` array contains Verifiable Presentations as JWT strings.

**When to use**: 
- When another participant needs to verify your credentials
- During dataspace protocol interactions (catalog browsing, contract negotiation)

---

#### 3. **Receive Credential Offers** - `POST /dcp/offers`

**Use Case**: An issuer proactively offers you new credentials (e.g., updated membership, key rotation).

**What it does**:
- Receives notification about available credentials
- Logs the offer for review
- You can then request the offered credentials

**Request Example**:
```bash
POST /dcp/offers
Content-Type: application/json

{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "CredentialOfferMessage",
  "issuer": "did:web:issuer.example.com",
  "credentials": [
    {
      "id": "membership-credential-v2",
      "type": "CredentialObject",
      "credentialType": "MembershipCredential",
      "offerReason": "reissue"
    }
  ]
}
```

**When to use**: Automatically called by issuers. Monitor logs to see credential offers.

---

### 📤 Issuer Endpoints (Issuing Credentials to Others)

#### 4. **Request Credentials** - `POST /issuer/credentials`

**Use Case**: You want to obtain credentials from an issuer (e.g., join a dataspace).

**What it does**:
- Submit a request to an issuer for specific credentials
- Issuer receives and can approve/reject
- Returns a request ID to track status

**Authentication**: Requires Bearer token from the **Holder** (requester)

**Request Example**:
```bash
POST /issuer/credentials
Authorization: Bearer <holder-token>
Content-Type: application/json

{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "CredentialRequestMessage",
  "holderPid": "did:web:example.com:holder",
  "credentials": [
    {"id": "membership-credential"},
    {"id": "organization-credential"}
  ]
}
```

**Response**:
```
HTTP 201 Created
Location: /issuer/requests/req-abc123
```

**When to use**:
- First time joining a dataspace
- Requesting additional credentials
- Updating expired credentials

---

#### 5. **Check Request Status** - `GET /issuer/requests/{requestId}`

**Use Case**: Check if your credential request has been processed.

**What it does**:
- Returns current status of your request
- Shows if approved, rejected, or pending

**Request Example**:
```bash
GET /issuer/requests/req-abc123
Authorization: Bearer <holder-token>
```

**Response**:
```json
{
  "issuerPid": "req-abc123",
  "holderPid": "did:web:example.com:holder",
  "status": "ISSUED",
  "createdAt": "2024-12-08T12:00:00Z"
}
```

**Status Values**:
- `RECEIVED`: Request received, pending review
- `ISSUED`: Credentials issued and delivered
- `REJECTED`: Request rejected

**When to use**: Poll this endpoint to check if your credential request has been approved.

---

#### 6. **Approve & Deliver Credentials** - `POST /issuer/requests/{requestId}/approve`

**Use Case**: As an issuer, approve a credential request and automatically deliver credentials to the holder.

**What it does**:
- Resolves holder's DID to find their Credential Service endpoint
- Sends credentials to holder's `/dcp/credentials` endpoint
- Updates request status to ISSUED

**Request Example**:
```bash
POST /issuer/requests/req-abc123/approve
Content-Type: application/json

{
  "credentials": [
    {
      "credentialType": "MembershipCredential",
      "payload": "eyJraWQiOiJkaWQ6ZXhhbXBsZS...",
      "format": "jwt"
    }
  ]
}
```

**Response**:
```json
{
  "status": "delivered",
  "message": "Credentials successfully delivered to holder",
  "credentialsCount": 1
}
```

**When to use**: After reviewing a credential request and deciding to approve it.

---

#### 7. **Reject Credential Request** - `POST /issuer/requests/{requestId}/reject`

**Use Case**: As an issuer, reject a credential request.

**What it does**:
- Marks request as rejected
- Notifies holder with rejection reason
- Updates status to REJECTED

**Request Example**:
```bash
POST /issuer/requests/req-abc123/reject
Content-Type: application/json

{
  "rejectionReason": "Organization not verified"
}
```

**Response**:
```json
{
  "status": "rejected",
  "message": "Credential request rejected and holder notified"
}
```

**When to use**: After reviewing a credential request and deciding to deny it.

---

### 🔧 Utility Endpoints

#### 8. **Get DID Document** - `GET /api/did`

**Use Case**: Retrieve your DID document to share your public keys and service endpoints.

**What it does**:
- Returns your DID document
- Contains public keys for verification
- Lists service endpoints (Credential Service, Issuer Service)

**Request Example**:
```bash
GET /api/did
```

**Response**:
```json
{
  "@context": ["https://www.w3.org/ns/did/v1"],
  "id": "did:web:example.com:connector",
  "verificationMethod": [
    {
      "id": "did:web:example.com:connector#key-1",
      "type": "JsonWebKey2020",
      "controller": "did:web:example.com:connector",
      "publicKeyJwk": {
        "kty": "EC",
        "crv": "P-256",
        "x": "...",
        "y": "..."
      }
    }
  ],
  "service": [
    {
      "id": "did:web:example.com:connector#credential-service",
      "type": "CredentialService",
      "serviceEndpoint": "https://example.com/dcp"
    },
    {
      "id": "did:web:example.com:connector#issuer-service",
      "type": "IssuerService",
      "serviceEndpoint": "https://example.com/issuer"
    }
  ]
}
```

**When to use**: 
- Share your DID document with others
- Verify your configuration is correct
- Debugging connectivity issues

---

#### 9. **Generate Test Token** - `POST /api/dev/token/generate`

**Use Case**: Generate a valid Self-Issued ID Token for testing (development only).

**What it does**:
- Creates a properly signed token
- Useful for testing without full OAuth setup

**⚠️ Warning**: This endpoint should be **disabled in production**!

**Request Example**:
```bash
POST /api/dev/token/generate
Content-Type: application/json

{
  "audienceDid": "did:web:verifier.example.com",
  "accessToken": "optional-access-token-123"
}
```

**Response**:
```json
{
  "token": "eyJraWQiOiJkaWQ6d2ViOmV4YW1wbGUuY29tI2tleS0xIiwiYWxnIjoiRVMyNTYifQ...",
  "authorization": "Bearer eyJraWQiOiJkaWQ6...",
  "audienceDid": "did:web:verifier.example.com"
}
```

**When to use**: Only during development and testing.

---

## Common Workflows

### Workflow 1: Joining a Dataspace (Obtaining Your First Credential)

```
You (Holder)                    Issuer (Dataspace Authority)
     │                                      │
     │  1. POST /issuer/credentials         │
     │──────────────────────────────────────>│
     │     (request membership credential)   │
     │                                      │
     │  2. HTTP 201 Created                 │
     │<──────────────────────────────────────│
     │     Location: /issuer/requests/req-1  │
     │                                      │
     │  3. Manual review by issuer          │
     │                            ⏰        │
     │                                      │
     │  4. Issuer approves                  │
     │                    POST /requests/req-1/approve
     │                                      │
     │  5. POST /dcp/credentials            │
     │<──────────────────────────────────────│
     │     (credentials delivered)           │
     │                                      │
     │  ✅ You now have credentials!        │
```

**Steps**:
1. **Request credentials** from the dataspace authority
2. **Wait** for manual approval (check status via `GET /issuer/requests/{id}`)
3. **Receive credentials** automatically when approved
4. **Use credentials** to access dataspace resources

---

### Workflow 2: Verifying Credentials During Data Access

```
You (Holder)            Your App            Verifier (Data Provider)
     │                     │                          │
     │  1. Request data    │                          │
     │────────────────────>│                          │
     │                     │  2. POST /dsp/catalog   │
     │                     │  + Self-Issued ID Token  │
     │                     │─────────────────────────>│
     │                     │                          │
     │                     │  3. POST /dcp/presentations/query
     │                     │<─────────────────────────│
     │                     │     (verify credentials) │
     │                     │                          │
     │                     │  4. Verifiable Presentation
     │                     │─────────────────────────>│
     │                     │     (contains credentials)│
     │                     │                          │
     │                     │  5. Access granted       │
     │  Data delivered     │<─────────────────────────│
     │<────────────────────│                          │
```

**Steps**:
1. Your app makes a request with a Self-Issued ID Token
2. Verifier queries your Credential Service for proof
3. Your Credential Service creates and returns a Verifiable Presentation
4. Verifier validates the credentials and grants access

---

### Workflow 3: Issuing Credentials to Others

```
Holder (Other Org)                  You (Issuer)
     │                                   │
     │  1. POST /issuer/credentials      │
     │──────────────────────────────────>│
     │                                   │
     │  2. HTTP 201 Created              │
     │<──────────────────────────────────│
     │   Location: /issuer/requests/req-X │
     │                                   │
     │                       3. Review request
     │                            ⏰     │
     │                                   │
     │             4. POST /issuer/requests/req-X/approve
     │                   (with credentials) │
     │                                   │
     │  5. Credentials auto-delivered    │
     │<──────────────────────────────────│
     │   (to holder's /dcp/credentials)  │
```

**Steps**:
1. Holder submits request to your `/issuer/credentials` endpoint
2. **Review** the request (verify organization, check requirements)
3. **Approve** via `POST /issuer/requests/{id}/approve` with credentials
4. Credentials are **automatically delivered** to holder

---

## Examples

### Example 1: Complete Credential Request Flow

#### Step 1: Request Credentials from Issuer

```bash
# Generate token for authentication
POST https://your-app.com/api/dev/token/generate
Content-Type: application/json

{
  "audienceDid": "did:web:issuer.example.com"
}

# Response:
{
  "token": "eyJraWQiOiJkaWQ6d2ViOnlvdXItYXBwLmNvbSNrZXktMSIsImFsZyI6IkVTMjU2In0...",
  "authorization": "Bearer eyJraWQi..."
}

# Request credentials
POST https://issuer.example.com/issuer/credentials
Authorization: Bearer eyJraWQi...
Content-Type: application/json

{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "CredentialRequestMessage",
  "holderPid": "did:web:your-app.com",
  "credentials": [
    {"id": "membership-credential"}
  ]
}

# Response:
HTTP 201 Created
Location: /issuer/requests/req-abc123
```

---

#### Step 2: Check Request Status

```bash
GET https://issuer.example.com/issuer/requests/req-abc123
Authorization: Bearer eyJraWQi...

# Response (pending):
{
  "issuerPid": "req-abc123",
  "holderPid": "did:web:your-app.com",
  "status": "RECEIVED",
  "createdAt": "2024-12-09T10:00:00Z"
}

# Response (after approval):
{
  "issuerPid": "req-abc123",
  "holderPid": "did:web:your-app.com",
  "status": "ISSUED",
  "createdAt": "2024-12-09T10:00:00Z"
}
```

---

#### Step 3: Credentials Are Automatically Delivered

When the issuer approves your request, they call:

```bash
# Issuer's action
POST https://issuer.example.com/issuer/requests/req-abc123/approve
Content-Type: application/json

{
  "credentials": [
    {
      "credentialType": "MembershipCredential",
      "payload": "eyJraWQiOiJkaWQ6ZXhhbXBsZTppc3N1ZXI0NTYja2V5LTEiLCJhbGciOiJFUzI1NiJ9...",
      "format": "jwt"
    }
  ]
}
```

This automatically triggers a request to your Credential Service:

```bash
# Automatically sent to your app
POST https://your-app.com/dcp/credentials
Authorization: Bearer <issuer-token>
Content-Type: application/json

{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "CredentialMessage",
  "issuerPid": "req-abc123",
  "holderPid": "did:web:your-app.com",
  "status": "ISSUED",
  "credentials": [
    {
      "credentialType": "MembershipCredential",
      "payload": "eyJraWQiOiJkaWQ6ZXhhbXBsZTppc3N1ZXI0NTYja2V5LTEiLCJhbGciOiJFUzI1NiJ9...",
      "format": "jwt"
    }
  ]
}

# Your app responds:
{
  "saved": 1
}
```

✅ **Credentials are now stored in your vault!**

---

### Example 2: Presenting Credentials to a Verifier

```bash
# Verifier queries your Credential Service
POST https://your-app.com/dcp/presentations/query
Authorization: Bearer <verifier-token>
Content-Type: application/json

{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "PresentationQueryMessage",
  "scope": [
    "org.eclipse.dspace.dcp.vc.type:MembershipCredential"
  ]
}

# Your app responds with Verifiable Presentation:
{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "PresentationResponseMessage",
  "presentation": [
    "eyJraWQiOiJkaWQ6d2ViOnlvdXItYXBwLmNvbSNrZXktMSIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJkaWQ6d2ViOnlvdXItYXBwLmNvbSIsInN1YiI6ImRpZDp3ZWI6eW91ci1hcHAuY29tIiwidnAiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiXSwidHlwZSI6WyJWZXJpZmlhYmxlUHJlc2VudGF0aW9uIl0sInZlcmlmaWFibGVDcmVkZW50aWFsIjpbImV5SmphR2tpT2lKa2FXUTZaWGhoYlhCc1pUcHBjM04xWlhJME5UWWphMlY1TFRFaUxDSmhiR2NpT2lKRlV6STFOaUo5Li4uIl19fQ.signature..."
  ]
}
```

---

### Example 3: Credential Formats

The DCP module supports two credential formats:

#### JWT Format (Compact)
```json
{
  "credentialType": "MembershipCredential",
  "payload": "eyJraWQiOiJkaWQ6ZXhhbXBsZTppc3N1ZXI0NTYja2V5LTEi...",
  "format": "jwt"
}
```

The payload is a compact JWT string. When decoded, it contains:
```json
{
  "iss": "did:example:issuer456",
  "sub": "did:example:holder123",
  "vc": {
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiableCredential", "MembershipCredential"],
    "credentialSubject": {
      "id": "did:example:holder123",
      "status": "Active",
      "membershipType": "Premium"
    }
  }
}
```

#### JSON-LD Format (Expanded)
```json
{
  "credentialType": "OrganizationCredential",
  "format": "json-ld",
  "payload": {
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      "https://example.org/credentials/v1"
    ],
    "id": "http://example.org/credentials/3732",
    "type": ["VerifiableCredential", "OrganizationCredential"],
    "issuer": "did:example:issuer456",
    "issuanceDate": "2024-12-08T12:30:00Z",
    "expirationDate": "2025-12-08T12:30:00Z",
    "credentialSubject": {
      "id": "did:example:holder123",
      "organizationName": "Example Corp",
      "role": "Member"
    },
    "proof": {
      "type": "JsonWebSignature2020",
      "created": "2024-12-08T12:30:00Z",
      "verificationMethod": "did:example:issuer456#key-1",
      "proofPurpose": "assertionMethod",
      "jws": "eyJhbGciOiJFUzI1NiIsImI2NCI6ZmFsc2V9..."
    }
  }
}
```

---

### Example 4: EU Location Credential - Complete Workflow

This example demonstrates a real-world scenario where a company needs to prove it's located in the European Union to access EU-only data services.

#### Scenario
Your company wants to access a data provider's catalog, but the provider only serves EU-based organizations. You need to obtain an "EU Location Credential" from a trusted authority and present it when requesting data.

---

#### Step 1: Understanding the EU Location Credential

The credential contains a claim stating that your organization is located within the European Union:

**Credential Claims:**
- **Subject**: Your company's DID
- **Location**: European Union
- **Country**: Specific EU member state (e.g., Germany, France, Italy)
- **Legal Entity**: Your company's legal name
- **Registration Number**: Official business registration number
- **Verified Date**: When the location was verified

---

#### Step 2: Request EU Location Credential from Authority

First, generate a token for authentication:

```bash
POST https://your-company.com/api/dev/token/generate
Content-Type: application/json

{
  "audienceDid": "did:web:eu-authority.europa.eu"
}

# Response:
{
  "token": "eyJraWQiOiJkaWQ6d2ViOnlvdXItY29tcGFueS5jb20ja2V5LTEiLCJhbGciOiJFUzI1NiJ9...",
  "authorization": "Bearer eyJraWQi..."
}
```

Now request the EU location credential:

```bash
POST https://eu-authority.europa.eu/issuer/credentials
Authorization: Bearer eyJraWQiOiJkaWQ6d2ViOnlvdXItY29tcGFueS5jb20ja2V5LTEi...
Content-Type: application/json

{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "CredentialRequestMessage",
  "holderPid": "did:web:your-company.com",
  "credentials": [
    {"id": "eu-location-credential"}
  ]
}

# Response:
HTTP 201 Created
Location: /issuer/requests/req-eu-loc-789
```

---

#### Step 3: Authority Verifies and Issues Credential

The EU authority reviews your request, verifies your company's legal registration in an EU member state, and approves the request.

**What the authority does:**
1. Verifies your company is registered in an EU country
2. Checks official business registries
3. Creates the credential with verified claims
4. Signs it with their private key

The authority calls:

```bash
POST https://eu-authority.europa.eu/issuer/requests/req-eu-loc-789/approve
Content-Type: application/json

{
  "credentials": [
    {
      "credentialType": "EULocationCredential",
      "format": "jwt",
      "payload": "eyJraWQiOiJkaWQ6d2ViOmV1LWF1dGhvcml0eS5ldXJvcGEuZXUja2V5LTEiLCJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJkaWQ6d2ViOnlvdXItY29tcGFueS5jb20iLCJpc3MiOiJkaWQ6d2ViOmV1LWF1dGhvcml0eS5ldXJvcGEuZXUiLCJleHAiOjE3OTY0ODY0MDAsImlhdCI6MTczMzg2NTYwMCwianRpIjoidXJuOnV1aWQ6ZXUtbG9jLWNyZWQtYWJjZDEyMzQtNTY3OC05MGFiLWNkZWYtMTIzNDU2Nzg5MGFiIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiLCJodHRwczovL2V1cm9wYS5ldS9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiRVVMb2NhdGlvbkNyZWRlbnRpYWwiXSwiY3JlZGVudGlhbFN1YmplY3QiOnsiaWQiOiJkaWQ6d2ViOnlvdXItY29tcGFueS5jb20iLCJsZWdhbE5hbWUiOiJZb3VyIENvbXBhbnkgR21iSCIsImxvY2F0aW9uIjp7InJlZ2lvbiI6IkV1cm9wZWFuIFVuaW9uIiwiY291bnRyeUNvZGUiOiJERSIsImNvdW50cnkiOiJHZXJtYW55In0sInJlZ2lzdHJhdGlvbk51bWJlciI6IkhSQiAxMjM0NTYiLCJyZWdpc3RyYXRpb25Db3VydCI6IkFtdHNnZXJpY2h0IE11bmljaCIsInZlcmlmaWVkRGF0ZSI6IjIwMjUtMTItMDkifX19.kL9mN2pQ5rS8tU7vW0xY3zA4bC6dE9fG2hI8jK0lM9nO3pQ7rS1tU5vW8xY2zA3bC5dE8fG6hI9jK2lM0nO4pQ"
    }
  ]
}
```

---

#### Step 4: Decoded JWT Payload

For reference, here's what the JWT contains when decoded:

**JWT Header:**
```json
{
  "kid": "did:web:eu-authority.europa.eu#key-1",
  "alg": "ES256"
}
```

**JWT Payload:**
```json
{
  "sub": "did:web:your-company.com",
  "iss": "did:web:eu-authority.europa.eu",
  "exp": 1796486400,
  "iat": 1733865600,
  "jti": "urn:uuid:eu-loc-cred-abcd1234-5678-90ab-cdef-1234567890ab",
  "vc": {
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      "https://europa.eu/credentials/v1"
    ],
    "type": [
      "VerifiableCredential",
      "EULocationCredential"
    ],
    "credentialSubject": {
      "id": "did:web:your-company.com",
      "legalName": "Your Company GmbH",
      "location": {
        "region": "European Union",
        "countryCode": "DE",
        "country": "Germany"
      },
      "registrationNumber": "HRB 123456",
      "registrationCourt": "Amtsgericht Munich",
      "verifiedDate": "2025-12-09"
    }
  }
}
```

**Key Claims Explained:**
- `sub`: Your company's DID (who the credential is about)
- `iss`: EU authority's DID (who issued it)
- `exp`: Expiration timestamp (December 31, 2026)
- `iat`: Issued at timestamp (December 9, 2025)
- `credentialSubject.location.region`: "European Union" - the main claim
- `credentialSubject.location.countryCode`: "DE" (ISO 3166-1 alpha-2 for Germany)
- `credentialSubject.registrationNumber`: Official business registration number
- `credentialSubject.verifiedDate`: When the EU authority verified the location

---

#### Step 5: Credential Automatically Delivered to Your Vault

The credential is automatically sent to your Credential Service:

```bash
# Automatically sent by the EU authority
POST https://your-company.com/dcp/credentials
Authorization: Bearer <eu-authority-token>
Content-Type: application/json

{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "CredentialMessage",
  "issuerPid": "req-eu-loc-789",
  "holderPid": "did:web:your-company.com",
  "status": "ISSUED",
  "credentials": [
    {
      "credentialType": "EULocationCredential",
      "format": "jwt",
      "payload": "eyJraWQiOiJkaWQ6d2ViOmV1LWF1dGhvcml0eS5ldXJvcGEuZXUja2V5LTEi..."
    }
  ]
}

# Your system responds:
{
  "saved": 1
}
```

✅ **EU Location Credential is now stored in your vault!**

---

#### Step 6: Using the EU Location Credential

Now when you try to access the EU data provider, they will verify your location.

**Scenario: Requesting a catalog from an EU-only data provider**

```bash
# 1. Your app makes a request with a Self-Issued ID Token
POST https://eu-data-provider.com/dsp/catalog
Authorization: Bearer <your-self-issued-token>
Content-Type: application/json

{
  "@context": ["https://w3id.org/dspace/v0.8/context.json"],
  "type": "CatalogRequestMessage"
}
```

**Behind the scenes:**

```bash
# 2. The data provider queries your Credential Service
POST https://your-company.com/dcp/presentations/query
Authorization: Bearer <provider-access-token>
Content-Type: application/json

{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "PresentationQueryMessage",
  "scope": [
    "org.eclipse.dspace.dcp.vc.type:EULocationCredential"
  ]
}

# 3. Your Credential Service automatically creates a Verifiable Presentation
# and returns it to the provider
{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "PresentationResponseMessage",
  "presentation": [
    "eyJraWQiOiJkaWQ6d2ViOnlvdXItY29tcGFueS5jb20ja2V5LTEiLCJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJkaWQ6d2ViOnlvdXItY29tcGFueS5jb20iLCJhdWQiOiJkaWQ6d2ViOmV1LWRhdGEtcHJvdmlkZXIuY29tIiwiaWF0IjoxNzMzODY1OTAwLCJleHAiOjE3MzM4NjY4MDAsInZwIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIl0sInR5cGUiOlsiVmVyaWZpYWJsZVByZXNlbnRhdGlvbiJdLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6WyJleUpyYVdRaU9pSmthV1E2ZDJWaU9tVjFMV0YxZEdodmNtbDBlUzVsZFhKdmNHRXVaWFVqYTJWNUxURWlMQ0poYkdjaU9pSkZVekkxTmlKOS5leUp6ZFdJaU9pSmthV1E2ZDJWaU9ubHZkWEl0WTI5dGNHRnVlUzVqYjIwaUxDSnBjM01pT2lKa2FXUTZkMlZpT21WMUxXRjFkR2h2Y21sMGVTNWxkWEp2Y0dFdVpYVWlMQ0psZUhBaU9qRTNPVFkwT0RZME1EQXNJbWxoZENJNk1UY3pNemcyTlRZd01Dd2lhblJwSWpvaWRYSnVPblYxYVdRNlpYVXRiRzlqTFdOeVpXUXRZV0pqWkRFeU16UXROVFkzT0MwNU1HRmlMV05rWldZdE1USXpORFUyTnpnNU1HRmlJaXdpZG1NaU9uc2lRR052Ym5SbGVIUWlPbHNpYUhSMGNITTZMeTkzZDNjdWR6TXViM0puTHpJd01UZ3ZZM0psWkdWdWRHbGhiSE12ZGpFaUxDSm9kSFJ3Y3pvdkwyVjFjbTl3WVM1bGRTOWpjbVZrWlc1MGFXRnNjeTkyTVNKZExDSjBlWEJsSWpwYklsWmxjbWxtYVdGaWJHVkRjbVZrWlc1MGFXRnNJaXdpUlZWTWIyTmhkR2x2YmtOeVpXUmxiblJwWVd3aVhTd2lZM0psWkdWdWRHbGhiRk4xWW1wbFkzUWlPbnNpYVdRaU9pSmthV1E2ZDJWaU9ubHZkWEl0WTI5dGNHRnVlUzVqYjIwaUxDSnNaV2RoYkU1aGJXVWlPaUpaYjNWeUlFTnZiWEJoYm5rZ1IyMWlTQ0lzSW14dlkyRjBhVzl1SWpwN0luSmxaMmx2YmlJNklrVjFjbTl3WldGdUlGVnVhVzl1SWl3aVkyOTFiblJ5ZVVOdlpHVWlPaUpFUlNJc0ltTnZkVzUwY25raU9pSkhaWEp0WVc1NUluMHNJbkpsWjJsemRISmhkR2x2YmsxMWJXSmxjaUk2SWtoU1FpQXhNak0wTlRZaUxDSnlaV2RwYzNSeVlYUnBiMjVEYjNWeWRDSTZJa0Z0ZEhOblpYSnBZMmgwSUUxMWJtbGphQ0lzSW5abGNtbG1hV1ZrUkdGMFpTSTZJakl3TWpVdE1USXRNRGtpZlgxOS5rTDltTjJwUTVyUzh0VTd2VzB4WTN6QTRiQzZkRTlmRzJoSThqSzBsTTluTzNwUTdyUzF0VTV2Vzh4WTJ6QTNiQzVkRThmRzZoSTlqSzJsTTBuTzRwUSJdfX0.mH9kL3pQ8rS5tU2vW7xY4zA6bC9dE3fG8hI5jK4lM2nO9pQ3rS9tU8vW5xY0zA8bC3dE5fG3hI2jK9lM8nO6pQ"
  ]
}
```

**What the provider verifies:**
1. ✅ Signature is valid (signed by EU authority)
2. ✅ Credential is not expired
3. ✅ Subject matches your DID
4. ✅ Location claim: `region = "European Union"`
5. ✅ EU authority is trusted (in their list of trusted issuers)

**Result:** Access granted! You receive the data catalog.

---

#### Step 7: Alternative Format - JSON-LD

The same EU Location Credential can also be issued in JSON-LD format:

```json
{
  "credentialType": "EULocationCredential",
  "format": "json-ld",
  "payload": {
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      "https://w3id.org/security/suites/jws-2020/v1",
      "https://europa.eu/credentials/v1"
    ],
    "id": "https://eu-authority.europa.eu/credentials/eu-loc-12345",
    "type": [
      "VerifiableCredential",
      "EULocationCredential"
    ],
    "issuer": "did:web:eu-authority.europa.eu",
    "issuanceDate": "2025-12-09T12:00:00Z",
    "expirationDate": "2026-12-31T23:59:59Z",
    "credentialSubject": {
      "id": "did:web:your-company.com",
      "legalName": "Your Company GmbH",
      "location": {
        "region": "European Union",
        "countryCode": "DE",
        "country": "Germany",
        "address": {
          "streetAddress": "Musterstraße 123",
          "postalCode": "80333",
          "city": "Munich",
          "state": "Bavaria"
        }
      },
      "registrationNumber": "HRB 123456",
      "registrationCourt": "Amtsgericht Munich",
      "legalForm": "GmbH",
      "verifiedDate": "2025-12-09",
      "verificationMethod": "Official Business Registry Check"
    },
    "proof": {
      "type": "JsonWebSignature2020",
      "created": "2025-12-09T12:00:00Z",
      "verificationMethod": "did:web:eu-authority.europa.eu#key-1",
      "proofPurpose": "assertionMethod",
      "jws": "eyJhbGciOiJFUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..mH9kL3pQ8rS5tU2vW7xY4zA6bC9dE3fG8hI5jK4lM2nO9pQ3rS9tU8vW5xY0zA8bC3dE5fG3hI2jK9lM8nO6pQ"
    }
  }
}
```

**Additional fields in JSON-LD format:**
- `address`: Full physical address details
- `legalForm`: Type of legal entity (GmbH, AG, SAS, etc.)
- `verificationMethod`: How the authority verified the claim

---

#### Step 8: Policy-Based Access Control

The data provider typically defines policies that require the EU location credential:

**Provider's Policy (DSP Offer):**
```json
{
  "odrl:permission": [
    {
      "odrl:action": "use",
      "odrl:constraint": [
        {
          "odrl:leftOperand": "location.region",
          "odrl:operator": "eq",
          "odrl:rightOperand": "European Union"
        }
      ]
    }
  ]
}
```

**Your system automatically:**
1. Detects the policy requires EU location
2. Maps it to `EULocationCredential`
3. Includes access token for the credential in the Self-Issued ID Token
4. Provider queries and verifies the credential
5. Access is granted or denied based on verification

---

#### Complete Request Flow Summary

```
1. Your Company                    2. EU Authority              3. EU Data Provider
      │                                    │                            │
      │ POST /issuer/credentials           │                            │
      │─────────────────────────────────────>                            │
      │ (request EU location credential)   │                            │
      │                                    │                            │
      │ HTTP 201 Created                   │                            │
      │<─────────────────────────────────────                            │
      │ Location: /requests/req-eu-loc-789 │                            │
      │                                    │                            │
      │                        Manual verification                       │
      │                        of company location                       │
      │                                    ⏰                           │
      │                                    │                            │
      │ POST /dcp/credentials              │                            │
      │<─────────────────────────────────────                            │
      │ (credential delivered)             │                            │
      │                                    │                            │
      │ ✅ Credential stored!              │                            │
      │                                    │                            │
      │ POST /dsp/catalog + Self-Issued Token                           │
      │──────────────────────────────────────────────────────────────────>
      │                                    │                            │
      │                                    │  POST /dcp/presentations/query
      │<───────────────────────────────────────────────────────────────────
      │ (verify EU location)               │                            │
      │                                    │                            │
      │ Verifiable Presentation            │                            │
      │────────────────────────────────────────────────────────────────>
      │ (contains EU location credential)  │                            │
      │                                    │                            │
      │                                    │         ✅ Verification    │
      │                                    │         ✅ Access Granted  │
      │                                    │                            │
      │ Catalog Response                   │                            │
      │<───────────────────────────────────────────────────────────────────
      │                                    │                            │
```

---

#### Key Takeaways from EU Location Example

1. **Location claims are verifiable** - The EU authority cryptographically signs the claim that your company is in the EU

2. **No need for repeated verification** - Once you have the credential, any EU data provider can verify it without contacting the authority

3. **Privacy-preserving** - You only share location information with providers that need it (via access tokens)

4. **Automatic workflow** - Once issued, credentials are automatically presented when policies require them

5. **Flexible formats** - Both JWT (compact) and JSON-LD (rich metadata) formats work

6. **Real compliance use case** - Essential for GDPR compliance, data residency requirements, and EU-only services

---

## Summary

### Key Takeaways

1. **Verifiable Credentials** are cryptographically-signed digital certificates that prove claims about you

2. **Three main actors**:
   - **Issuer**: Issues credentials
   - **Holder**: Stores and presents credentials
   - **Verifier**: Checks credentials

3. **Main workflows**:
   - **Get credentials**: Request → Wait for approval → Receive automatically
   - **Present credentials**: Verifier queries → You create presentation → Access granted
   - **Issue credentials**: Receive request → Review → Approve → Auto-deliver

4. **All requests require authentication** via Self-Issued ID Token (JWT)

5. **Two credential formats supported**: JWT (compact) and JSON-LD (expanded)

### Quick Reference Table

| I want to... | Endpoint | Method | Role |
|-------------|----------|--------|------|
| Request credentials from an issuer | `/issuer/credentials` | POST | Holder |
| Check my request status | `/issuer/requests/{id}` | GET | Holder |
| Receive credentials | `/dcp/credentials` | POST | Holder |
| Present credentials to a verifier | `/dcp/presentations/query` | POST | Holder |
| Approve a credential request | `/issuer/requests/{id}/approve` | POST | Issuer |
| Reject a credential request | `/issuer/requests/{id}/reject` | POST | Issuer |
| Get my DID document | `/api/did` | GET | Any |
| Generate test token | `/api/dev/token/generate` | POST | Dev only |

---

## Additional Resources

- **DCP Specification**: See `dcp_spec.txt` for the full protocol specification
- **Quick Start Guide**: See `QUICK_START.md` for rapid testing instructions
- **Example Credentials**: See `CREDENTIAL_MESSAGE_EXAMPLES.md` for Postman examples
- **W3C VC Data Model**: https://www.w3.org/TR/vc-data-model/
- **Decentralized Identifiers (DIDs)**: https://www.w3.org/TR/did-core/

---

**Questions or Issues?**

Check the application logs for detailed information about credential processing, validation, and any errors.

