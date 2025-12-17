# DCP Protocol Analysis: PresentationId vs Full VP in JWT

## Question
Is it possible, according to the DCP protocol, to include a `presentationId` in the JWT when sending a request to the verifier, instead of the currently implemented approach of embedding the whole VP (as JWT or JSON)?

The proposed flow would be:
1. Holder sends JWT with `presentationId` to Verifier
2. Verifier authorizes the request using the `presentationId`
3. Verifier resolves holder's DID document
4. Verifier gets Credential Service URL from DID document
5. Verifier sends request to Credential Service to fetch the full VP
6. Verifier validates the VP

## Analysis Based on DCP Specification v1.0

### Current DCP Protocol Design

According to the DCP specification (Section 4.3.1 and Section 5), the protocol is designed with the following flow:

#### **Supported Approach: Access Token with Deferred VP Fetch**

**YES - This approach is ALREADY SPECIFIED in the DCP protocol!**

The DCP specification **explicitly supports** a mechanism where the full VP is NOT embedded in the initial request:

**Section 4.3.1 - Verifiable Presentation Access Token:**
```
"A Self-Issued ID Token MAY contain an access token as a token claim that can 
be used by the Verifier to obtain Verifiable Presentations from the participant's 
Credential Service. The format of the token is implementation-specific and therefore 
should be treated as an opaque string by the Verifier."
```

**This means:**
1. ✅ The Self-Issued ID Token can contain an access `token` claim (not the full VP)
2. ✅ The Verifier uses this token to fetch the VP separately from the Credential Service
3. ✅ The token format is implementation-specific (could be a presentationId)

### Recommended DCP Flow (Per Specification)

```
┌──────────┐                    ┌──────────┐                    ┌─────────────────┐
│  Holder  │                    │ Verifier │                    │ Credential      │
│  Agent   │                    │  Agent   │                    │ Service (CS)    │
└────┬─────┘                    └────┬─────┘                    └────┬────────────┘
     │                               │                               │
     │ 1. DSP Request                │                               │
     │   Authorization: Bearer <JWT> │                               │
     ├──────────────────────────────>│                               │
     │                               │                               │
     │   JWT contains:               │                               │
     │   - iss: holder DID           │                               │
     │   - aud: verifier DID         |                               │
     |   - scope: MembershipCred     │                               │
     │   - token: <access-token>     │ (NOT the full VP)             │
     │                               │                               │
     │                               │ 2. Validate JWT signature     │
     │                               │    (using holder's DID doc)   │
     │                               │                               │
     │                               │ 3. Resolve holder DID         │
     │                               │    Get CS endpoint            │
     │                               │                               │
     │                               │ 4. Query VP                   │
     │                               │   POST /presentations/query   │
     │                               │   Authorization: Bearer <JWT> │
     │                               │   (JWT with token claim)      │
     │                               ├──────────────────────────────>│
     │                               │                               │
     │                               │                               │ 5. Validate token
     │                               │                               │    Check access
     │                               │                               │
     │                               │ 6. VP Response                │
     │                               │<──────────────────────────────┤
     │                               │                               │
     │                               │ 7. Validate VP                │
     │                               │    - Signature                │
     │                               │    - Expiration               │
     │                               │    - Revocation               │
     │                               │                               │
     │ 8. DSP Response               │                               │
     │   (authorized/denied)         │                               │
     │<──────────────────────────────┤                               │
```

### Key Specification Sections

#### Section 5.2 - Credential Service Endpoint Discovery
```
"The client DID Service MUST make the Credential Service available as a service 
entry in the DID document. The type attribute MUST be CredentialService."
```

**Example DID Document:**
```json
{
  "@context": ["https://www.w3.org/ns/did/v1"],
  "service": [{
    "id": "did:example:123#identity-hub",
    "type": "CredentialService",
    "serviceEndpoint": "https://cs.example.com"
  }]
}
```

#### Section 5.3.1 - Submitting an Access Token
```
"To provide the opportunity for Credential Service implementations to enforce 
proof-of-possession, the access token MUST be contained in the token claim of 
a Self-Issued ID Token."
```

#### Section 5.4 - Resolution API
```
"The Resolution API defines the Credential Service endpoint for querying credentials 
and returning a set of Verifiable Presentations."

Endpoint: POST /presentations/query
Request: PresentationQueryMessage
Response: PresentationResponseMessage
```

### Implementation Approach

#### 1. Self-Issued ID Token Structure (Initial Request)

**Current Implementation (Embedding Full VP):**
```json
{
  "iss": "did:web:localhost:8080",
  "sub": "did:web:localhost:8080",
  "aud": "did:web:verifier",
  "vp": {
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiablePresentation"],
    "verifiableCredential": [...]  // Full VCs embedded
  },
  "iat": 1734350400,
  "exp": 1734350700,
  "jti": "urn:uuid:..."
}
```

**DCP-Compliant Approach (Using Token Claim):**
```json
{
  "iss": "did:web:localhost:8080",
  "sub": "did:web:localhost:8080",
  "aud": "did:web:verifier",
  "token": "eyJhbGc...",  // Access token or presentationId
  "iat": 1734350400,
  "exp": 1734350700,
  "jti": "urn:uuid:..."
}
```

The `token` claim could contain:
- An opaque access token
- A presentationId reference
- Any implementation-specific identifier

#### 2. Verifier Fetches VP from Credential Service

**Request to Credential Service:**
```http
POST https://holder-cs.example.com/presentations/query
Authorization: Bearer <Self-Issued-ID-Token-with-token-claim>
Content-Type: application/json

{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "PresentationQueryMessage",
  "scope": ["MembershipCredential"]
}
```

**Response from Credential Service:**
```json
{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "PresentationResponseMessage",
  "presentation": [
    "eyJraWQiOiJvOVdzdTN2SF9LTXM3VE5UMFA4UDlKTHRIMmJRZWUtbVRwNVBleUdDNHQ4..."
  ]
}
```

### Benefits of Token Claim Approach

1. ✅ **Reduced JWT Size**: Initial request JWT is smaller
2. ✅ **On-Demand VP Fetch**: VP only retrieved when needed
3. ✅ **Consent Control**: Credential Service can enforce fine-grained access control
4. ✅ **Dynamic Credentials**: Fresh credentials fetched at validation time
5. ✅ **Privacy**: VP not sent unless verifier is authorized
6. ✅ **DCP Compliant**: Explicitly designed in the specification

### Implementation Requirements

#### Holder Side Changes:

1. **SelfIssuedIdTokenService.java**
   - Add support for `token` claim instead of `vp` claim
   - Generate access token or presentationId
   - Include in JWT payload

2. **Credential Service (DcpController.java)**
   - Implement `/presentations/query` endpoint
   - Validate incoming access token from `token` claim
   - Return PresentationResponseMessage with VP

#### Verifier Side Changes:

1. **VcVpAuthenticationProvider.java**
   - Extract `token` claim from Self-Issued ID Token
   - Resolve holder's DID document
   - Get Credential Service endpoint
   - Call `/presentations/query` with token
   - Validate returned VP

2. **DID Resolution**
   - Already implemented in DidResolverService
   - Extract `CredentialService` endpoint from DID document

### Configuration

```properties
# Enable token-based VP fetching (instead of embedded VP)
dcp.vp.use-token-claim=true

# Credential Service endpoint (for holder)
dcp.credential-service.base-url=https://localhost:8080

# Token validity for VP access
dcp.vp.token.validity-seconds=300
```

## Answer

**YES**, the DCP protocol **explicitly supports and recommends** using a `token` claim in the Self-Issued ID Token instead of embedding the full VP. This is the intended design for the following reasons:

1. **Section 4.3.1** explicitly defines "Verifiable Presentation Access Token" mechanism
2. **Section 5.2** requires Credential Service endpoint in DID document for VP fetching
3. **Section 5.3.1** mandates using the `token` claim for access control
4. **Section 5.4** defines the `/presentations/query` API for fetching VPs

### Current vs DCP-Compliant Implementation

| Aspect | Current Implementation | DCP-Compliant Approach |
|--------|----------------------|----------------------|
| Initial JWT | Contains full `vp` claim | Contains `token` claim |
| VP Location | Embedded in request | Fetched from Credential Service |
| Token Size | Large (includes all VCs) | Small (just reference) |
| Credential Freshness | Static at request time | Dynamic at fetch time |
| Access Control | N/A | Enforced by Credential Service |
| Privacy | VP exposed to all | VP only to authorized verifiers |
| DCP Compliance | Extension | **Specification Design** |

### Recommendation

**Implement the DCP-compliant approach** using the `token` claim with deferred VP fetching. This is:
- ✅ **Specification-compliant**
- ✅ **Better for privacy**
- ✅ **More scalable**
- ✅ **Supports consent management**
- ✅ **Reduces network overhead**

The `presentationId` you mentioned would be perfectly valid as the `token` claim value, as the specification states the token format is "implementation-specific."

