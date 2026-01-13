# Verifiable Credentials Examples - VC 1.1 vs VC 2.0

**Date:** January 5, 2026  
**Purpose:** Show concrete examples of VC 1.1 and VC 2.0 credentials with different status lists and proof mechanisms

---

## Table of Contents

1. [Profile: vc11-sl2021/jwt (VC 1.1 with StatusList2021)](#profile-vc11-sl2021jwt)
2. [Profile: vc20-bssl/jwt (VC 2.0 with BitStringStatusList)](#profile-vc20-bssljwt)
3. [Key Differences Summary](#key-differences-summary)
4. [JWT Structure Comparison](#jwt-structure-comparison)

---

## Profile: vc11-sl2021/jwt

### Overview
- **VC Data Model:** 1.1
- **Revocation:** StatusList2021
- **Proof:** External proofs using JWT
- **Format:** JWT (compact serialization)

### 1.1 Structure: External Proof with JWT

In VC 1.1 with JWT, the proof is **external** to the credential data structure. The entire credential (including the separate proof object) is encoded in the JWT payload.

### Example 1: VC 1.1 Credential (Decoded JWT Payload)

```json
{
  "vc": {
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      "https://w3id.org/vc/status-list/2021/v1"
    ],
    "id": "https://example.com/credentials/3732",
    "type": ["VerifiableCredential", "MembershipCredential"],
    "issuer": "did:web:issuer.example.com",
    "issuanceDate": "2024-01-15T14:30:00Z",
    "expirationDate": "2025-01-15T14:30:00Z",
    "credentialSubject": {
      "id": "did:web:holder.example.com",
      "type": "Member",
      "membershipNumber": "12345",
      "organizationName": "Example Organization",
      "memberSince": "2020-01-01"
    },
    "credentialStatus": {
      "id": "https://example.com/status/1#94567",
      "type": "StatusList2021Entry",
      "statusPurpose": "revocation",
      "statusListIndex": "94567",
      "statusListCredential": "https://example.com/status/1"
    },
    "proof": {
      "type": "Ed25519Signature2020",
      "created": "2024-01-15T14:30:00Z",
      "verificationMethod": "did:web:issuer.example.com#key-1",
      "proofPurpose": "assertionMethod",
      "proofValue": "z58DAdFfa9SkqZMVPxAQpic7ndSayn1PzZs6ZjWp1CktyGesjuTSwRdoWhAfGFCF5bppETSTojQCrfFPP2oumHKtz"
    }
  },
  "iss": "did:web:issuer.example.com",
  "sub": "did:web:holder.example.com",
  "nbf": 1705329000,
  "exp": 1736951400,
  "jti": "https://example.com/credentials/3732"
}
```

### Key Points - VC 1.1:

1. **@context**: References VC 1.1 context (`/2018/credentials/v1`)
2. **credentialStatus**: Uses `StatusList2021Entry` type
   - `statusListIndex`: Numeric string pointing to position in status list
   - `statusListCredential`: URL to the status list credential
3. **proof**: External proof object **inside** the `vc` claim
   - Separate from credential data
   - Has its own metadata (type, created, verificationMethod, etc.)
4. **JWT claims**: Standard JWT claims wrap the VC
   - `iss`: Issuer DID
   - `sub`: Subject DID
   - `nbf`: Not before
   - `exp`: Expiration
   - `jti`: JWT ID (same as credential ID)

### Example 2: VC 1.1 Status List Credential

The credential references a StatusList2021 credential that looks like this:

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1",
    "https://w3id.org/vc/status-list/2021/v1"
  ],
  "id": "https://example.com/status/1",
  "type": ["VerifiableCredential", "StatusList2021Credential"],
  "issuer": "did:web:issuer.example.com",
  "issuanceDate": "2024-01-01T00:00:00Z",
  "credentialSubject": {
    "id": "https://example.com/status/1#list",
    "type": "StatusList2021",
    "statusPurpose": "revocation",
    "encodedList": "H4sIAAAAAAAAA-3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA"
  },
  "proof": {
    "type": "Ed25519Signature2020",
    "created": "2024-01-01T00:00:00Z",
    "verificationMethod": "did:web:issuer.example.com#key-1",
    "proofPurpose": "assertionMethod",
    "proofValue": "z3FXQjecWRsGHzY3F8dFj8h..."
  }
}
```

**How StatusList2021 Works:**
- `encodedList`: Base64-encoded gzipped bitstring
- Each index position represents a credential
- Position `94567` in the decoded bitstring indicates revocation status
- `0` = valid, `1` = revoked

### Example 3: VC 1.1 JWT (Compact Format)

```
eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCIsImtpZCI6ImRpZDp3ZWI6aXNzdWVyLmV4YW1wbGUuY29tI2tleS0xIn0.eyJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vdzNpZC5vcmcvdmMvc3RhdHVzLWxpc3QvMjAyMS92MSJdLCJpZCI6Imh0dHBzOi8vZXhhbXBsZS5jb20vY3JlZGVudGlhbHMvMzczMiIsInR5cGUiOlsiVmVyaWZpYWJsZUNyZWRlbnRpYWwiLCJNZW1iZXJzaGlwQ3JlZGVudGlhbCJdLCJpc3N1ZXIiOiJkaWQ6d2ViOmlzc3Vlci5leGFtcGxlLmNvbSIsImlzc3VhbmNlRGF0ZSI6IjIwMjQtMDEtMTVUMTQ6MzA6MDBaIiwiZXhwaXJhdGlvbkRhdGUiOiIyMDI1LTAxLTE1VDE0OjMwOjAwWiIsImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOndlYjpob2xkZXIuZXhhbXBsZS5jb20iLCJ0eXBlIjoiTWVtYmVyIiwibWVtYmVyc2hpcE51bWJlciI6IjEyMzQ1Iiwib3JnYW5pemF0aW9uTmFtZSI6IkV4YW1wbGUgT3JnYW5pemF0aW9uIiwibWVtYmVyU2luY2UiOiIyMDIwLTAxLTAxIn0sImNyZWRlbnRpYWxTdGF0dXMiOnsiaWQiOiJodHRwczovL2V4YW1wbGUuY29tL3N0YXR1cy8xIzk0NTY3IiwidHlwZSI6IlN0YXR1c0xpc3QyMDIxRW50cnkiLCJzdGF0dXNQdXJwb3NlIjoicmV2b2NhdGlvbiIsInN0YXR1c0xpc3RJbmRleCI6Ijk0NTY3Iiwic3RhdHVzTGlzdENyZWRlbnRpYWwiOiJodHRwczovL2V4YW1wbGUuY29tL3N0YXR1cy8xIn0sInByb29mIjp7InR5cGUiOiJFZDI1NTE5U2lnbmF0dXJlMjAyMCIsImNyZWF0ZWQiOiIyMDI0LTAxLTE1VDE0OjMwOjAwWiIsInZlcmlmaWNhdGlvbk1ldGhvZCI6ImRpZDp3ZWI6aXNzdWVyLmV4YW1wbGUuY29tI2tleS0xIiwicHJvb2ZQdXJwb3NlIjoiYXNzZXJ0aW9uTWV0aG9kIiwicHJvb2ZWYWx1ZSI6Ino1OERBZEZmYTlTa3FaTVZQeEFRcGljN25kU2F5bjFQelpzNlpqV3AxQ2t0eUdlc2p1VFN3UmRvV2hBZkdGQ0Y1YnBwRVRTVG9qUUNyZkZQUDJvdW1IS3R6In19LCJpc3MiOiJkaWQ6d2ViOmlzc3Vlci5leGFtcGxlLmNvbSIsInN1YiI6ImRpZDp3ZWI6aG9sZGVyLmV4YW1wbGUuY29tIiwibmJmIjoxNzA1MzI5MDAwLCJleHAiOjE3MzY5NTE0MDAsImp0aSI6Imh0dHBzOi8vZXhhbXBsZS5jb20vY3JlZGVudGlhbHMvMzczMiJ9.signature_bytes_here
```

**JWT Structure:**
```
HEADER.PAYLOAD.SIGNATURE
```

- **HEADER**: Algorithm, type, key ID
- **PAYLOAD**: Contains the entire `vc` claim with external proof
- **SIGNATURE**: JWT signature over header + payload

---

## Profile: vc20-bssl/jwt

### Overview
- **VC Data Model:** 2.0
- **Revocation:** BitStringStatusList
- **Proof:** Enveloped proofs using JWT/JOSE
- **Format:** JWT (compact serialization)

### 2.0 Structure: Enveloped Proof with JWT

In VC 2.0 with JWT, the proof is **enveloped** - the JWT structure itself provides the proof. There's no separate proof object inside the credential.

### Example 4: VC 2.0 Credential (Decoded JWT Payload)

```json
{
  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://www.w3.org/ns/credentials/examples/v2"
  ],
  "id": "https://example.com/credentials/8392",
  "type": ["VerifiableCredential", "MembershipCredential"],
  "issuer": {
    "id": "did:web:issuer.example.com",
    "name": "Example Organization"
  },
  "validFrom": "2024-01-15T14:30:00Z",
  "validUntil": "2025-01-15T14:30:00Z",
  "credentialSubject": {
    "id": "did:web:holder.example.com",
    "type": "Member",
    "membershipNumber": "12345",
    "organizationName": "Example Organization",
    "memberSince": "2020-01-01"
  },
  "credentialStatus": {
    "id": "https://example.com/credentials/status/3#94567",
    "type": "BitstringStatusListEntry",
    "statusPurpose": "revocation",
    "statusListIndex": "94567",
    "statusListCredential": "https://example.com/credentials/status/3"
  },
  "iss": "did:web:issuer.example.com",
  "sub": "did:web:holder.example.com",
  "nbf": 1705329000,
  "exp": 1736951400,
  "jti": "https://example.com/credentials/8392"
}
```

### Key Points - VC 2.0:

1. **@context**: References VC 2.0 context (`/ns/credentials/v2`)
2. **credentialStatus**: Uses `BitstringStatusListEntry` type (different from VC 1.1!)
   - Same fields but different type name
   - Still uses `statusListIndex` and `statusListCredential`
3. **NO proof object**: The JWT envelope IS the proof
   - Proof is implicit in the JWT signature
   - No separate proof metadata inside the payload
4. **validFrom/validUntil**: New field names (not issuanceDate/expirationDate)
5. **issuer**: Can be an object (not just a string)
6. **JWT claims**: Same as VC 1.1

### Example 5: VC 2.0 BitStringStatusList Credential

```json
{
  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://www.w3.org/ns/credentials/examples/v2"
  ],
  "id": "https://example.com/credentials/status/3",
  "type": ["VerifiableCredential", "BitstringStatusListCredential"],
  "issuer": "did:web:issuer.example.com",
  "validFrom": "2024-01-01T00:00:00Z",
  "credentialSubject": {
    "id": "https://example.com/credentials/status/3#list",
    "type": "BitstringStatusList",
    "statusPurpose": "revocation",
    "encodedList": "H4sIAAAAAAAAA-3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA",
    "ttl": 3600000,
    "statusMessages": [
      {
        "status": "0x0",
        "message": "valid"
      },
      {
        "status": "0x1", 
        "message": "revoked"
      }
    ]
  }
}
```

**Key Differences from StatusList2021:**
- Type is `BitstringStatusListCredential` (not `StatusList2021Credential`)
- `credentialSubject.type` is `BitstringStatusList` (not `StatusList2021`)
- **ttl field**: Time-to-live in milliseconds (VC 2.0 addition)
- **statusMessages**: Optional human-readable messages for status codes
- **NOTE**: DCP spec says to use `validUntil` instead of `ttl` due to conflict

### Example 6: VC 2.0 JWT (Compact Format)

```
eyJhbGciOiJFZERTQSIsInR5cCI6InZjK2xkK2p3dCIsImtpZCI6ImRpZDp3ZWI6aXNzdWVyLmV4YW1wbGUuY29tI2tleS0xIn0.eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvbnMvY3JlZGVudGlhbHMvdjIiLCJodHRwczovL3d3dy53My5vcmcvbnMvY3JlZGVudGlhbHMvZXhhbXBsZXMvdjIiXSwiaWQiOiJodHRwczovL2V4YW1wbGUuY29tL2NyZWRlbnRpYWxzLzgzOTIiLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiTWVtYmVyc2hpcENyZWRlbnRpYWwiXSwiaXNzdWVyIjp7ImlkIjoiZGlkOndlYjppc3N1ZXIuZXhhbXBsZS5jb20iLCJuYW1lIjoiRXhhbXBsZSBPcmdhbml6YXRpb24ifSwidmFsaWRGcm9tIjoiMjAyNC0wMS0xNVQxNDozMDowMFoiLCJ2YWxpZFVudGlsIjoiMjAyNS0wMS0xNVQxNDozMDowMFoiLCJjcmVkZW50aWFsU3ViamVjdCI6eyJpZCI6ImRpZDp3ZWI6aG9sZGVyLmV4YW1wbGUuY29tIiwidHlwZSI6Ik1lbWJlciIsIm1lbWJlcnNoaXBOdW1iZXIiOiIxMjM0NSIsIm9yZ2FuaXphdGlvbk5hbWUiOiJFeGFtcGxlIE9yZ2FuaXphdGlvbiIsIm1lbWJlclNpbmNlIjoiMjAyMC0wMS0wMSJ9LCJjcmVkZW50aWFsU3RhdHVzIjp7ImlkIjoiaHR0cHM6Ly9leGFtcGxlLmNvbS9jcmVkZW50aWFscy9zdGF0dXMvMyM5NDU2NyIsInR5cGUiOiJCaXRzdHJpbmdTdGF0dXNMaXN0RW50cnkiLCJzdGF0dXNQdXJwb3NlIjoicmV2b2NhdGlvbiIsInN0YXR1c0xpc3RJbmRleCI6Ijk0NTY3Iiwic3RhdHVzTGlzdENyZWRlbnRpYWwiOiJodHRwczovL2V4YW1wbGUuY29tL2NyZWRlbnRpYWxzL3N0YXR1cy8zIn0sImlzcyI6ImRpZDp3ZWI6aXNzdWVyLmV4YW1wbGUuY29tIiwic3ViIjoiZGlkOndlYjpob2xkZXIuZXhhbXBsZS5jb20iLCJuYmYiOjE3MDUzMjkwMDAsImV4cCI6MTczNjk1MTQwMCwianRpIjoiaHR0cHM6Ly9leGFtcGxlLmNvbS9jcmVkZW50aWFscy84MzkyIn0.signature_bytes_here
```

**JWT Header Difference:**
```json
{
  "alg": "EdDSA",
  "typ": "vc+ld+jwt",  // ← Different media type for VC 2.0
  "kid": "did:web:issuer.example.com#key-1"
}
```

Note: `typ` is `vc+ld+jwt` for VC 2.0 (vs just `JWT` for VC 1.1)

---

## Key Differences Summary

### Visual Comparison Table

| Aspect | VC 1.1 (vc11-sl2021/jwt) | VC 2.0 (vc20-bssl/jwt) |
|--------|--------------------------|------------------------|
| **@context** | `/2018/credentials/v1` | `/ns/credentials/v2` |
| **Validity Fields** | `issuanceDate`, `expirationDate` | `validFrom`, `validUntil` |
| **Issuer** | String only | String or Object |
| **Status Type** | `StatusList2021Entry` | `BitstringStatusListEntry` |
| **Status List Type** | `StatusList2021Credential` | `BitstringStatusListCredential` |
| **Status List Subject** | `StatusList2021` | `BitstringStatusList` |
| **TTL Field** | ❌ Not present | ✅ Present (but use validUntil per DCP) |
| **Proof Location** | ❌ External (inside vc claim) | ✅ Enveloped (JWT itself) |
| **JWT typ header** | `JWT` | `vc+ld+jwt` |
| **Proof Object** | ✅ Present in payload | ❌ Not present (implicit) |

### Proof Location Visualization

#### VC 1.1 - External Proof

```
┌─────────────────────────────────────────┐
│          JWT Structure                  │
├─────────────────────────────────────────┤
│ Header: { alg, typ, kid }               │
├─────────────────────────────────────────┤
│ Payload:                                │
│   vc: {                                 │
│     @context: [...],                    │
│     id: "...",                          │
│     type: [...],                        │
│     credentialSubject: {...},           │
│     credentialStatus: {...},            │
│     ┌─────────────────────────────┐    │
│     │ proof: {                    │    │ ← EXTERNAL PROOF
│     │   type: "Ed25519...",       │    │   (separate object)
│     │   created: "...",           │    │
│     │   verificationMethod: "..." │    │
│     │   proofValue: "..."         │    │
│     │ }                           │    │
│     └─────────────────────────────┘    │
│   }                                     │
├─────────────────────────────────────────┤
│ Signature: ECDSA/EdDSA signature        │ ← JWT signature
└─────────────────────────────────────────┘
```

**Two Layers of Proof:**
1. Internal proof object (within VC)
2. JWT signature (over entire payload)

#### VC 2.0 - Enveloped Proof

```
┌─────────────────────────────────────────┐
│          JWT Structure                  │
├─────────────────────────────────────────┤
│ Header: { alg, typ, kid }               │
├─────────────────────────────────────────┤
│ Payload:                                │
│   @context: [...],                      │
│   id: "...",                            │
│   type: [...],                          │
│   credentialSubject: {...},             │
│   credentialStatus: {...},              │
│   ❌ NO proof object                    │
│   (proof is the JWT envelope itself)    │
│                                          │
├─────────────────────────────────────────┤
│ Signature: ECDSA/EdDSA signature        │ ← ENVELOPED PROOF
└─────────────────────────────────────────┘   (single layer)
```

**Single Layer of Proof:**
- JWT signature IS the proof
- Simpler structure
- Proof metadata in JWT header

---

## JWT Structure Comparison

### VC 1.1 JWT Decoded Structure

```json
{
  "header": {
    "alg": "EdDSA",
    "typ": "JWT",
    "kid": "did:web:issuer.example.com#key-1"
  },
  "payload": {
    "vc": {
      "@context": ["https://www.w3.org/2018/credentials/v1", "..."],
      "id": "https://example.com/credentials/3732",
      "type": ["VerifiableCredential", "MembershipCredential"],
      "issuer": "did:web:issuer.example.com",
      "issuanceDate": "2024-01-15T14:30:00Z",
      "expirationDate": "2025-01-15T14:30:00Z",
      "credentialSubject": { /* ... */ },
      "credentialStatus": {
        "type": "StatusList2021Entry",  // ← VC 1.1 status type
        "statusListIndex": "94567",
        "statusListCredential": "https://example.com/status/1"
      },
      "proof": {  // ← EXTERNAL PROOF OBJECT
        "type": "Ed25519Signature2020",
        "created": "2024-01-15T14:30:00Z",
        "verificationMethod": "did:web:issuer.example.com#key-1",
        "proofPurpose": "assertionMethod",
        "proofValue": "z58DAdFfa9SkqZMVPxAQpic7..."
      }
    },
    "iss": "did:web:issuer.example.com",
    "sub": "did:web:holder.example.com",
    "nbf": 1705329000,
    "exp": 1736951400,
    "jti": "https://example.com/credentials/3732"
  },
  "signature": "..." // JWT signature
}
```

### VC 2.0 JWT Decoded Structure

```json
{
  "header": {
    "alg": "EdDSA",
    "typ": "vc+ld+jwt",  // ← Different type
    "kid": "did:web:issuer.example.com#key-1"
  },
  "payload": {
    "@context": ["https://www.w3.org/ns/credentials/v2", "..."],
    "id": "https://example.com/credentials/8392",
    "type": ["VerifiableCredential", "MembershipCredential"],
    "issuer": {
      "id": "did:web:issuer.example.com",
      "name": "Example Organization"
    },
    "validFrom": "2024-01-15T14:30:00Z",    // ← Different field name
    "validUntil": "2025-01-15T14:30:00Z",   // ← Different field name
    "credentialSubject": { /* ... */ },
    "credentialStatus": {
      "type": "BitstringStatusListEntry",  // ← VC 2.0 status type
      "statusListIndex": "94567",
      "statusListCredential": "https://example.com/credentials/status/3"
    },
    // ❌ NO proof object - JWT envelope IS the proof
    "iss": "did:web:issuer.example.com",
    "sub": "did:web:holder.example.com",
    "nbf": 1705329000,
    "exp": 1736951400,
    "jti": "https://example.com/credentials/8392"
  },
  "signature": "..." // JWT signature (ENVELOPED PROOF)
}
```

---

## Practical Code Examples

### How to Detect Profile from JWT

```java
public ProfileId detectProfileFromJwt(String jwtString) {
    // Parse JWT
    Jwt jwt = jwtParser.parse(jwtString);
    JsonNode payload = jwt.getPayload();
    
    // Check context to determine VC version
    JsonNode context = payload.get("@context");
    if (context == null) {
        // Try inside vc claim (VC 1.1 style)
        context = payload.path("vc").get("@context");
    }
    
    boolean isVc20 = false;
    if (context.isArray()) {
        for (JsonNode ctx : context) {
            if (ctx.asText().contains("/ns/credentials/v2")) {
                isVc20 = true;
                break;
            }
        }
    }
    
    // Check status type
    JsonNode credStatus = payload.get("credentialStatus");
    if (credStatus == null) {
        credStatus = payload.path("vc").get("credentialStatus");
    }
    
    String statusType = credStatus.path("type").asText();
    
    // Determine profile
    if (isVc20 && statusType.equals("BitstringStatusListEntry")) {
        return ProfileId.VC20_BSSL_JWT;
    } else if (!isVc20 && statusType.equals("StatusList2021Entry")) {
        return ProfileId.VC11_SL2021_JWT;
    }
    
    // Default fallback
    return ProfileId.VC11_SL2021_JWT;
}
```

### How to Verify Each Profile

#### VC 1.1 Verification

```java
public boolean verifyVc11Credential(String jwtString) {
    // 1. Verify JWT signature (external layer)
    Jwt jwt = jwtVerifier.verify(jwtString);
    if (!jwt.isValid()) {
        return false;
    }
    
    // 2. Extract VC from payload
    JsonNode vcClaim = jwt.getPayload().get("vc");
    
    // 3. Verify internal proof object
    JsonNode proof = vcClaim.get("proof");
    String verificationMethod = proof.get("verificationMethod").asText();
    String proofValue = proof.get("proofValue").asText();
    
    // Resolve DID and verify internal proof
    boolean proofValid = verifyProofValue(vcClaim, proofValue, verificationMethod);
    
    // 4. Check status list
    JsonNode status = vcClaim.get("credentialStatus");
    boolean notRevoked = checkStatusList2021(status);
    
    return proofValid && notRevoked;
}
```

#### VC 2.0 Verification

```java
public boolean verifyVc20Credential(String jwtString) {
    // 1. Verify JWT signature (enveloped proof - only layer)
    Jwt jwt = jwtVerifier.verify(jwtString);
    if (!jwt.isValid()) {
        return false;  // No separate proof to check
    }
    
    // 2. Extract credential from payload (no vc claim wrapping)
    JsonNode credential = jwt.getPayload();
    
    // 3. Check bitstring status list
    JsonNode status = credential.get("credentialStatus");
    boolean notRevoked = checkBitstringStatusList(status);
    
    return notRevoked;
}
```

### Storage Considerations

```java
// Store VC 1.1
public void storeVc11(String jwtString) {
    Jwt jwt = jwtParser.parse(jwtString);
    JsonNode vcClaim = jwt.getPayload().get("vc");
    
    VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
        .profileId("vc11-sl2021/jwt")
        .jwtRepresentation(jwtString)
        .credential(vcClaim)  // Store the vc claim
        .credentialStatus(vcClaim.get("credentialStatus"))
        .build();
    
    repository.save(vc);
}

// Store VC 2.0
public void storeVc20(String jwtString) {
    Jwt jwt = jwtParser.parse(jwtString);
    JsonNode credential = jwt.getPayload();  // Direct payload
    
    VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
        .profileId("vc20-bssl/jwt")
        .jwtRepresentation(jwtString)
        .credential(credential)  // Store the payload directly
        .credentialStatus(credential.get("credentialStatus"))
        .build();
    
    repository.save(vc);
}
```

---

## DCP Specification Notes

### Important: validUntil vs ttl

From DCP Spec Section A.1:

> **vc20-bssl/jwt Remarks:**  
> "Ignore ttl, use validUntil (*).  
> (*) In its current form, the BitStringStatusList credential data model conflicts with the VC DataModel 2.0, specifically regarding the validity period (ttl vs validUntil)."

**What this means:**
- BitStringStatusList spec defines a `ttl` field
- VC 2.0 uses `validUntil` field
- **DCP says: Use `validUntil`, ignore `ttl`**

**Example - DO THIS:**
```json
{
  "type": "BitstringStatusListCredential",
  "validFrom": "2024-01-01T00:00:00Z",
  "validUntil": "2025-01-01T00:00:00Z",  // ← Use this
  "credentialSubject": {
    "type": "BitstringStatusList",
    "encodedList": "..."
    // ttl: 3600000  ← IGNORE THIS per DCP spec
  }
}
```

---

## Summary for Implementation

### When Processing a Credential

1. **Parse JWT** → Extract payload
2. **Check @context** → Determine VC 1.1 vs 2.0
3. **Check credentialStatus.type** → Determine status list type
4. **Determine profile:**
   - VC 2.0 + BitstringStatusListEntry → `vc20-bssl/jwt`
   - VC 1.1 + StatusList2021Entry → `vc11-sl2021/jwt`
5. **Verify appropriately:**
   - VC 1.1: Verify JWT + internal proof
   - VC 2.0: Verify JWT only (enveloped proof)
6. **Store with correct profile ID**

### Key Takeaways

✅ **VC 1.1 (vc11-sl2021/jwt):**
- Has external proof object inside vc claim
- Uses StatusList2021
- Uses issuanceDate/expirationDate
- Context: `/2018/credentials/v1`

✅ **VC 2.0 (vc20-bssl/jwt):**
- NO proof object (JWT envelope IS the proof)
- Uses BitstringStatusList
- Uses validFrom/validUntil
- Context: `/ns/credentials/v2`

✅ **Both profiles:**
- Use JWT compact format
- Store as JWT string
- Same verification methods for JWT layer
- Different status list checking

---

**For more details, see:**
- DCP Specification Section A.1
- W3C VC Data Model 1.1: https://www.w3.org/TR/vc-data-model/
- W3C VC Data Model 2.0: https://www.w3.org/TR/vc-data-model-2.0/
- StatusList2021: https://www.w3.org/TR/vc-status-list/
- BitstringStatusList: https://www.w3.org/TR/vc-bitstring-status-list/

