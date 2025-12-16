# Verifiable Credentials: Technical Guide

## Overview

This document provides technical details for developers working with Verifiable Credentials (VCs) and Verifiable Presentations (VPs) in the TRUE Connector. It covers implementation details, API examples, code references, debugging tips, and flow diagrams.

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Complete VC/VP Flow](#complete-vcvp-flow)
- [DID and DID Documents](#did-and-did-documents)
- [API Endpoints](#api-endpoints)
- [Example Requests](#example-requests)
- [Authentication Flow](#authentication-flow)
- [Code References](#code-references)
- [Debugging Tips](#debugging-tips)

---

## Architecture Overview

The DCP (Decentralized Claims Protocol) module implements VC/VP functionality with two main services:

### 1. Credential Service (`/dcp/*`)
Acts as a secure vault for Verifiable Credentials:
- Receives and stores credentials issued by trusted authorities
- Creates Verifiable Presentations on demand for verifiers
- Manages credential lifecycle and revocation checks

**Key Classes:**
- `HolderService` - Manages credential storage and presentation creation
- `PresentationService` - Creates VPs from stored VCs
- `PresentationValidationService` - Validates incoming VPs

### 2. Issuer Service (`/issuer/*`)
Enables credential issuance capabilities:
- Processes credential requests from holders
- Issues credentials with cryptographic proofs
- Tracks issuance status and manages approval workflows

**Key Classes:**
- `IssuerService` - Processes credential requests
- `CredentialDeliveryService` - Delivers credentials to holders
- `SelfIssuedIdTokenService` - Manages authentication tokens

**Module Location:** `dcp/src/main/java/it/eng/dcp/`

---

## Complete VC/VP Flow

### Step-by-Step Process

```
┌──────────┐         ┌──────────┐         ┌──────────┐
│  Issuer  │         │  Holder  │         │ Verifier │
└────┬─────┘         └────┬─────┘         └────┬─────┘
     │                    │                     │
     │ 1. Create DID      │                     │
     │    & Publish       │                     │
     │    DID Document    │                     │
     │                    │                     │
     │                    │ 2. Create DID       │
     │                    │    & Publish        │
     │                    │    DID Document     │
     │                    │                     │
     │ 3. Request Credentials                   │
     │◄───────────────────┤                     │
     │                    │                     │
     │ 4. Issue VC        │                     │
     │    (signed by      │                     │
     │     issuer)        │                     │
     ├───────────────────>│                     │
     │                    │                     │
     │                    │ 5. Store VC         │
     │                    │    in wallet        │
     │                    │                     │
     │                    │ 6. Query for VP     │
     │                    │◄────────────────────┤
     │                    │                     │
     │                    │ 7. Create VP        │
     │                    │    (signed by       │
     │                    │     holder)         │
     │                    ├────────────────────>│
     │                    │                     │
     │                    │                     │ 8. Validate VP
     │                    │                     │    - Verify holder signature
     │                    │                     │    - Extract VCs
     │                    │                     │    - Verify issuer signatures
     │                    │                     │    - Check expiration
     │                    │                     │    - Verify trust
     │                    │                     │
```

### Flow Description

1. **Issuer Setup**: Creates a DID (e.g., `did:web:issuer.example`) and publishes DID document with public key
2. **Holder Setup**: Creates a DID (e.g., `did:web:localhost:8080`) and publishes DID document
3. **Credential Request**: Holder requests credentials from issuer
4. **Credential Issuance**: Issuer creates and signs credential with claims about the holder
5. **Storage**: Holder stores credential in secure vault (MongoDB)
6. **Presentation Query**: Verifier requests presentation with specific credential types
7. **Presentation Creation**: Holder creates VP containing relevant VCs, signs with private key
8. **Validation**: Verifier validates holder signature, issuer signatures, expiration, and trust

---

## DID and DID Documents

### What is a DID?

A **Decentralized Identifier** (DID) is a globally unique identifier that can be resolved to a DID Document containing public keys and service endpoints.

**Format:** `did:{method}:{identifier}[#{fragment}]`

**Example:** `did:web:dataspace-issuer#key-1`
- Method: `web`
- Identifier: `dataspace-issuer`
- Fragment: `key-1` (references a specific key in the DID document)

### DID Resolution

For `did:web` method, the DID resolves to:
- `did:web:dataspace-issuer` → `https://dataspace-issuer/.well-known/did.json`
- `did:web:localhost%3A8080:holder` → `https://localhost:8080/holder/.well-known/did.json`

### Example DID Document

```json
{
  "id": "did:web:dataspace-issuer",
  "@context": [
    "https://www.w3.org/ns/did/v1",
    { "@base": "did:web:dataspace-issuer" }
  ],
  "verificationMethod": [
    {
      "id": "did:web:dataspace-issuer#key-1",
      "type": "JsonWebKey2020",
      "controller": "did:web:dataspace-issuer",
      "publicKeyJwk": {
        "kty": "OKP",
        "crv": "Ed25519",
        "x": "Hsq2QXPbbsU7j6JwXstbpxGSgliI04g_fU3z2nwkuVc"
      }
    }
  ],
  "authentication": ["key-1"],
  "service": [
    {
      "id": "issuer-credentialservice",
      "type": "CredentialService",
      "serviceEndpoint": "https://dataspace-issuer/issuer/credentials"
    }
  ]
}
```

**Key Fields:**
- `verificationMethod`: Contains public keys for signature verification
- `authentication`: Keys that can be used for authentication
- `service`: Endpoints for credential services

---

## Secure Token Service (SelfIssuedIdTokenService)

### Overview

The **SelfIssuedIdTokenService** is a critical security component that generates and validates self-issued ID tokens for authentication between connectors in the DCP protocol. These tokens are **required** for:

1. **Holder → Issuer**: When requesting credentials from an issuer
2. **Issuer → Holder**: When delivering credentials to a holder

**Purpose:** Provides mutual authentication and prevents unauthorized access to DCP endpoints.

**Location:** `dcp/src/main/java/it/eng/dcp/service/SelfIssuedIdTokenService.java`

---

### When Tokens Are Required

#### Scenario 1: Request Credentials (Holder → Issuer)
```
Holder                    Issuer
  │                          │
  │ 1. Generate token        │
  │    (aud: issuer DID)     │
  │                          │
  │ 2. POST /issuer/credentials
  │    Authorization: Bearer <token>
  ├─────────────────────────>│
  │                          │
  │                          │ 3. Validate token
  │                          │    - Verify signature
  │                          │    - Check expiration
  │                          │    - Validate audience
  │                          │
  │ 4. 201 Created           │
  │<─────────────────────────┤
```

#### Scenario 2: Deliver Credentials (Issuer → Holder)
```
Issuer                    Holder
  │                          │
  │ 1. Generate token        │
  │    (aud: holder DID)     │
  │                          │
  │ 2. POST /dcp/credentials │
  │    Authorization: Bearer <token>
  ├─────────────────────────>│
  │                          │
  │                          │ 3. Validate token
  │                          │    - Verify signature
  │                          │    - Check expiration
  │                          │    - Validate audience
  │                          │
  │ 4. 200 OK                │
  │<─────────────────────────┤
```

---

### Token Structure

The token is a **JWT (JSON Web Token)** signed with **ES256 (ECDSA with P-256 and SHA-256)**.

#### JWT Header
```json
{
  "alg": "ES256",
  "kid": "did:web:connector-a#key-1"
}
```

**Fields:**
- `alg`: Algorithm (always ES256 for elliptic curve signature)
- `kid`: Key ID from the connector's DID document (used to locate public key for verification)

#### JWT Payload (Claims)
```json
{
  "iss": "did:web:connector-a",
  "sub": "did:web:connector-a",
  "aud": "did:web:connector-b",
  "iat": 1734350400,
  "exp": 1734350700,
  "jti": "550e8400-e29b-41d4-a716-446655440000",
  "token": "optional-access-token"
}
```

**Standard Claims:**
- `iss` (Issuer): The DID of the connector creating the token (sender)
- `sub` (Subject): Same as issuer (self-issued token)
- `aud` (Audience): The DID of the connector that should accept this token (receiver)
- `iat` (Issued At): Unix timestamp when token was created
- `exp` (Expiration): Unix timestamp when token expires (5 minutes from creation)
- `jti` (JWT ID): Unique identifier for replay protection

**Custom Claims:**
- `token` (optional): Can contain an access token for additional context

#### Complete Token Example
**Compact JWT Format:**
```
eyJhbGciOiJFUzI1NiIsImtpZCI6ImRpZDp3ZWI6Y29ubmVjdG9yLWEja2V5LTEifQ.eyJpc3MiOiJkaWQ6d2ViOmNvbm5lY3Rvci1hIiwic3ViIjoiZGlkOndlYjpjb25uZWN0b3ItYSIsImF1ZCI6ImRpZDp3ZWI6Y29ubmVjdG9yLWIiLCJpYXQiOjE3MzQzNTA0MDAsImV4cCI6MTczNDM1MDcwMCwianRpIjoiNTUwZTg0MDAtZTI5Yi00MWQ0LWE3MTYtNDQ2NjU1NDQwMDAwIn0.Xy1z2A3b4C5d6E7f8G9h0I1j2K3l4M5n6O7p8Q9r0S1t2U3v4W5x6Y7z8A9b0C1d
```

---

### Token Creation Process

#### Method Signature
```java
public String createAndSignToken(String audienceDid, String accessToken)
```

#### Steps

1. **Validate Input**
   - Ensure `audienceDid` is provided
   - Retrieve connector's own DID from configuration (`dcp.connector.did`)

2. **Build Claims**
   ```java
   JWTClaimsSet claims = new JWTClaimsSet.Builder()
       .issuer(connectorDid)
       .subject(connectorDid)
       .audience(audienceDid)
       .issueTime(Date.from(now))
       .expirationTime(Date.from(now.plusSeconds(300)))  // 5 minutes
       .jwtID(UUID.randomUUID().toString())
       .claim("token", accessToken)  // optional
       .build();
   ```

3. **Obtain Signing Key**
   - Retrieve EC (Elliptic Curve) private key from `KeyService`
   - Key ID matches the `kid` in DID document

4. **Create JWT Header**
   ```java
   JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
       .keyID(signingJwk.getKeyID())
       .build();
   ```

5. **Sign Token**
   ```java
   SignedJWT jwt = new SignedJWT(header, claims);
   JWSSigner signer = new ECDSASigner(signingJwk.toECPrivateKey());
   jwt.sign(signer);
   ```

6. **Serialize and Return**
   ```java
   return jwt.serialize();  // Returns compact JWT string
   ```

---

### Token Validation Process

#### Method Signature
```java
public JWTClaimsSet validateToken(String token)
```

#### Validation Steps

1. **Parse JWT**
   ```java
   SignedJWT jwt = SignedJWT.parse(token);
   ```

2. **Extract Issuer and Key ID**
   ```java
   String issuer = jwt.getJWTClaimsSet().getIssuer();
   String kid = jwt.getHeader().getKeyID();
   ```

3. **Resolve Public Key from DID Document**
   - Use `DidResolverService` to fetch issuer's DID document
   - Locate public key matching the `kid`
   ```java
   JWK jwk = didResolver.resolvePublicKey(issuer, kid, null);
   ECKey ecPub = (ECKey) jwk;
   ```

4. **Verify Signature**
   ```java
   JWSVerifier verifier = new ECDSAVerifier(ecPub.toECPublicKey());
   if (!jwt.verify(verifier)) {
       throw new SecurityException("Invalid signature");
   }
   ```

5. **Validate Claims**
   - **Expiration Check**: `exp` must be in the future
     ```java
     if (exp.toInstant().isBefore(now)) {
         throw new SecurityException("Token expired");
     }
     ```
   - **Required Claims**: Ensure `iat`, `exp`, `jti` are present
   - **Audience Check**: Verify `aud` matches receiver's DID (done by caller)

6. **Replay Protection** (Optional)
   - Cache `jti` with expiration time
   - Reject if `jti` was already used
   ```java
   jtiCache.checkAndPut(jti, exp.toInstant());
   ```

7. **Return Claims**
   ```java
   return claims;  // Valid token, return claims for further processing
   ```

---

## Secure Token Service (SelfIssuedIdTokenService)

### Overview

The **SelfIssuedIdTokenService** is a critical security component that generates and validates self-issued ID tokens for authentication between connectors in the DCP protocol. These tokens are **required** for:

1. **Holder → Issuer**: When requesting credentials from an issuer
2. **Issuer → Holder**: When delivering credentials to a holder

**Purpose:** Provides mutual authentication and prevents unauthorized access to DCP endpoints.

**Location:** `dcp/src/main/java/it/eng/dcp/service/SelfIssuedIdTokenService.java`

---

### When Tokens Are Required

#### Scenario 1: Request Credentials (Holder → Issuer)
```
Holder                    Issuer
  │                          │
  │ 1. Generate token        │
  │    (aud: issuer DID)     │
  │                          │
  │ 2. POST /issuer/credentials
  │    Authorization: Bearer <token>
  ├─────────────────────────>│
  │                          │
  │                          │ 3. Validate token
  │                          │    - Verify signature
  │                          │    - Check expiration
  │                          │    - Validate audience
  │                          │
  │ 4. 201 Created           │
  │<─────────────────────────┤
```

#### Scenario 2: Deliver Credentials (Issuer → Holder)
```
Issuer                    Holder
  │                          │
  │ 1. Generate token        │
  │    (aud: holder DID)     │
  │                          │
  │ 2. POST /dcp/credentials │
  │    Authorization: Bearer <token>
  ├─────────────────────────>│
  │                          │
  │                          │ 3. Validate token
  │                          │    - Verify signature
  │                          │    - Check expiration
  │                          │    - Validate audience
  │                          │
  │ 4. 200 OK                │
  │<─────────────────────────┤
```

---

### Token Structure

The token is a **JWT (JSON Web Token)** signed with **ES256 (ECDSA with P-256 and SHA-256)**.

#### JWT Header
```json
{
  "alg": "ES256",
  "kid": "did:web:connector-a#key-1"
}
```

**Fields:**
- `alg`: Algorithm (always ES256 for elliptic curve signature)
- `kid`: Key ID from the connector's DID document (used to locate public key for verification)

#### JWT Payload (Claims)
```json
{
  "iss": "did:web:connector-a",
  "sub": "did:web:connector-a",
  "aud": "did:web:connector-b",
  "iat": 1734350400,
  "exp": 1734350700,
  "jti": "550e8400-e29b-41d4-a716-446655440000",
  "token": "optional-access-token"
}
```

**Standard Claims:**
- `iss` (Issuer): The DID of the connector creating the token (sender)
- `sub` (Subject): Same as issuer (self-issued token)
- `aud` (Audience): The DID of the connector that should accept this token (receiver)
- `iat` (Issued At): Unix timestamp when token was created
- `exp` (Expiration): Unix timestamp when token expires (5 minutes from creation)
- `jti` (JWT ID): Unique identifier for replay protection

**Custom Claims:**
- `token` (optional): Can contain an access token for additional context

#### Complete Token Example
**Compact JWT Format:**
```
eyJhbGciOiJFUzI1NiIsImtpZCI6ImRpZDp3ZWI6Y29ubmVjdG9yLWEja2V5LTEifQ.eyJpc3MiOiJkaWQ6d2ViOmNvbm5lY3Rvci1hIiwic3ViIjoiZGlkOndlYjpjb25uZWN0b3ItYSIsImF1ZCI6ImRpZDp3ZWI6Y29ubmVjdG9yLWIiLCJpYXQiOjE3MzQzNTA0MDAsImV4cCI6MTczNDM1MDcwMCwianRpIjoiNTUwZTg0MDAtZTI5Yi00MWQ0LWE3MTYtNDQ2NjU1NDQwMDAwIn0.Xy1z2A3b4C5d6E7f8G9h0I1j2K3l4M5n6O7p8Q9r0S1t2U3v4W5x6Y7z8A9b0C1d
```

---

### Token Creation Process

#### Method Signature
```java
public String createAndSignToken(String audienceDid, String accessToken)
```

#### Steps

1. **Validate Input**
   - Ensure `audienceDid` is provided
   - Retrieve connector's own DID from configuration (`dcp.connector.did`)

2. **Build Claims**
   ```java
   JWTClaimsSet claims = new JWTClaimsSet.Builder()
       .issuer(connectorDid)
       .subject(connectorDid)
       .audience(audienceDid)
       .issueTime(Date.from(now))
       .expirationTime(Date.from(now.plusSeconds(300)))  // 5 minutes
       .jwtID(UUID.randomUUID().toString())
       .claim("token", accessToken)  // optional
       .build();
   ```

3. **Obtain Signing Key**
   - Retrieve EC (Elliptic Curve) private key from `KeyService`
   - Key ID matches the `kid` in DID document

4. **Create JWT Header**
   ```java
   JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
       .keyID(signingJwk.getKeyID())
       .build();
   ```

5. **Sign Token**
   ```java
   SignedJWT jwt = new SignedJWT(header, claims);
   JWSSigner signer = new ECDSASigner(signingJwk.toECPrivateKey());
   jwt.sign(signer);
   ```

6. **Serialize and Return**
   ```java
   return jwt.serialize();  // Returns compact JWT string
   ```

---

### Token Validation Process

#### Method Signature
```java
public JWTClaimsSet validateToken(String token)
```

#### Validation Steps

1. **Parse JWT**
   ```java
   SignedJWT jwt = SignedJWT.parse(token);
   ```

2. **Extract Issuer and Key ID**
   ```java
   String issuer = jwt.getJWTClaimsSet().getIssuer();
   String kid = jwt.getHeader().getKeyID();
   ```

3. **Resolve Public Key from DID Document**
   - Use `DidResolverService` to fetch issuer's DID document
   - Locate public key matching the `kid`
   ```java
   JWK jwk = didResolver.resolvePublicKey(issuer, kid, null);
   ECKey ecPub = (ECKey) jwk;
   ```

4. **Verify Signature**
   ```java
   JWSVerifier verifier = new ECDSAVerifier(ecPub.toECPublicKey());
   if (!jwt.verify(verifier)) {
       throw new SecurityException("Invalid signature");
   }
   ```

5. **Validate Claims**
   - **Expiration Check**: `exp` must be in the future
     ```java
     if (exp.toInstant().isBefore(now)) {
         throw new SecurityException("Token expired");
     }
     ```
   - **Required Claims**: Ensure `iat`, `exp`, `jti` are present
   - **Audience Check**: Verify `aud` matches receiver's DID (done by caller)

6. **Replay Protection** (Optional)
   - Cache `jti` with expiration time
   - Reject if `jti` was already used
   ```java
   jtiCache.checkAndPut(jti, exp.toInstant());
   ```

7. **Return Claims**
   ```java
   return claims;  // Valid token, return claims for further processing
   ```

---

### Usage in Code

#### Creating a Token (Sender Side)

**Example: Holder requesting credentials from Issuer**

```java
// In CredentialIssuanceClient.java
public ResponseEntity<String> requestCredentials(String issuerDid, 
                                                 CredentialRequestMessage request) {
    // Create authentication token for the issuer
    String token = tokenService.createAndSignToken(
        issuerDid,      // audience: the issuer's DID
        null            // optional access token
    );
    
    // Send request with token in Authorization header
    String url = issuerServiceUrl + "/issuer/credentials";
    GenericApiResponse<String> response = httpClient.sendRequestProtocol(
        url,
        mapper.writeValueAsString(request),
        "Bearer " + token  // Authorization header
    );
    
    return ResponseEntity.status(response.getStatus()).body(response.getData());
}
```

**Example: Issuer delivering credentials to Holder**

```java
// In CredentialDeliveryService.java
public boolean deliverCredentials(String holderDid, 
                                  List<CredentialContainer> credentials) {
    // Create authentication token for the holder
    String token = tokenService.createAndSignToken(
        holderDid,      // audience: the holder's DID
        null            // optional access token
    );
    
    // Build credential message
    CredentialMessage message = CredentialMessage.Builder.newInstance()
        .holderPid(holderDid)
        .credentials(credentials)
        .status(CredentialStatus.ISSUED)
        .build();
    
    // Send to holder's credential endpoint
    String url = credentialServiceUrl + "/credentials";
    GenericApiResponse<String> response = httpClient.sendRequestProtocol(
        url,
        mapper.writeValueAsString(message),
        "Bearer " + token  // Authorization header
    );
    
    return response.isSuccess();
}
```

---

#### Validating a Token (Receiver Side)

**Example: Issuer receiving credential request from Holder**

```java
// In IssuerController.java
@PostMapping("/issuer/credentials")
public ResponseEntity<?> requestCredentials(
    @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
    @RequestBody CredentialRequestMessage request) {
    
    // Extract token from "Bearer <token>" header
    String token = authorization.substring("Bearer ".length());
    
    // Validate token
    try {
        JWTClaimsSet claims = tokenService.validateToken(token);
        
        // Verify audience matches our DID
        String connectorDid = dcpProperties.getConnectorDid();
        if (!connectorDid.equals(claims.getAudience().get(0))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Token audience mismatch");
        }
        
        // Get holder DID from issuer claim
        String holderDid = claims.getIssuer();
        
        // Process request...
        CredentialRequest credRequest = issuerService.processRequest(
            holderDid, 
            request
        );
        
        return ResponseEntity.created(
            URI.create("/issuer/requests/" + credRequest.getIssuerPid())
        ).build();
        
    } catch (SecurityException e) {
        log.error("Token validation failed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body("Invalid token");
    }
}
```

**Example: Holder receiving credentials from Issuer**

```java
// In DcpController.java
@PostMapping("/dcp/credentials")
public ResponseEntity<?> receiveCredentials(
    @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
    @RequestBody CredentialMessage message) {
    
    // Extract and validate token
    String token = authorization.substring("Bearer ".length());
    
    try {
        JWTClaimsSet claims = tokenService.validateToken(token);
        
        // Verify audience is us
        String connectorDid = dcpProperties.getConnectorDid();
        if (!connectorDid.equals(claims.getAudience().get(0))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        // Get issuer DID from token
        String issuerDid = claims.getIssuer();
        
        // Process credential delivery...
        int saved = holderService.storeCredentials(message, issuerDid);
        
        return ResponseEntity.ok(Map.of("saved", saved));
        
    } catch (SecurityException e) {
        log.error("Token validation failed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
```

---

### Configuration

#### Required Properties

```properties
# Connector's DID (REQUIRED for token creation)
dcp.connector.did=did:web:connector-a

# DID must be resolvable at:
# https://connector-a/.well-known/did.json
```

#### DID Document Requirements

The DID document must contain a verification method with EC key for signing:

```json
{
  "id": "did:web:connector-a",
  "verificationMethod": [
    {
      "id": "did:web:connector-a#key-1",
      "type": "JsonWebKey2020",
      "controller": "did:web:connector-a",
      "publicKeyJwk": {
        "kty": "EC",
        "crv": "P-256",
        "x": "WKn-ZIGevcwGIyyrzFoZNBdaq9_TsqzGl96oc0CWuis",
        "y": "y77t-RvAHRKTsSGdIYUfweuOvwrvDD-Q3Hv5J0fSKbE"
      }
    }
  ],
  "authentication": ["did:web:connector-a#key-1"],
  "@context": ["https://www.w3.org/ns/did/v1"]
}
```

**Key Points:**
- `kty` must be `EC` (Elliptic Curve)
- `crv` should be `P-256`
- Public key (`x`, `y`) is used by receivers to verify signatures
- Private key is stored securely by `KeyService`

---

### Security Features

1. **Short-Lived Tokens**
   - Tokens expire after 5 minutes
   - Reduces window for token theft/replay

2. **Cryptographic Signatures**
   - ES256 (ECDSA with P-256) provides strong security
   - Signature proves token was created by DID owner

3. **Audience Validation**
   - Token specifies intended recipient (`aud` claim)
   - Prevents token reuse with different recipients

4. **Replay Protection**
   - Unique `jti` for each token
   - Optional `JtiReplayCache` prevents reuse

5. **DID-Based Trust**
   - Public keys resolved from DID documents
   - No need for centralized certificate authority

---

### Troubleshooting

#### Issue: "connectorDid is not configured"
**Cause:** `dcp.connector.did` property not set

**Solution:**
```properties
# Add to application.properties
dcp.connector.did=did:web:connector-a
```

#### Issue: "Failed to resolve issuer public key"
**Cause:** DID document not accessible or missing verification method

**Solution:**
- Verify DID document is published at: `https://<host>/.well-known/did.json`
- Check `verificationMethod` array contains EC key with correct `kid`
- Ensure `authentication` array references the key ID

#### Issue: "Invalid signature"
**Cause:** Public key mismatch or token tampering

**Solution:**
- Verify sender's DID document contains correct public key
- Ensure private key used for signing matches public key in DID document
- Check for clock skew between sender and receiver

#### Issue: "Token expired"
**Cause:** Token older than 5 minutes or clock skew

**Solution:**
- Synchronize system clocks (use NTP)
- Generate new token for each request
- Don't cache tokens beyond their expiration

#### Issue: "Token audience mismatch"
**Cause:** Token created for different recipient

**Solution:**
- Ensure `audienceDid` parameter matches receiver's DID
- Verify receiver checks `aud` claim matches its own DID

---

### Testing Token Generation

**Manual Token Creation:**
```bash
# Get token via API endpoint (if available)
curl -X POST http://localhost:8080/api/token/generate \
  -H "Content-Type: application/json" \
  -d '{"audience": "did:web:connector-b"}'

# Response:
{
  "token": "eyJhbGciOiJFUzI1NiIsImtpZCI6ImRpZDp3ZWI6Y29ubmVjdG9yLWEja2V5LTEifQ..."
}
```

**Decode Token (for debugging):**
```bash
# Use jwt.io or jwt-cli to decode
echo "eyJhbGciOi..." | jwt decode -

# Or use jwt.io website
```

**Verify Token:**
```java
// In test or debug console
SelfIssuedIdTokenService tokenService = context.getBean(SelfIssuedIdTokenService.class);
String token = "eyJhbGciOi...";

try {
    JWTClaimsSet claims = tokenService.validateToken(token);
    System.out.println("Token valid!");
    System.out.println("Issuer: " + claims.getIssuer());
    System.out.println("Audience: " + claims.getAudience());
    System.out.println("Expires: " + claims.getExpirationTime());
} catch (SecurityException e) {
    System.err.println("Token invalid: " + e.getMessage());
}
```

---

## API Endpoints

### Holder Endpoints (Credential Service)

#### 1. Receive Credentials
```
POST /dcp/credentials
Authorization: Bearer <token>
Content-Type: application/json
```

Receives credentials from issuer and stores them in vault.

#### 2. Query Presentations
```
POST /dcp/presentations/query
Authorization: Bearer <verifier-token>
Content-Type: application/json
```

Creates and returns a VP containing requested credential types.

### Issuer Endpoints

#### 1. Request Credentials
```
POST /issuer/credentials
Authorization: Bearer <holder-token>
Content-Type: application/json
```

Holder requests credentials from issuer.

#### 2. Approve Request
```
POST /issuer/requests/{requestId}/approve
Content-Type: application/json
```

Issuer approves and delivers credentials to holder.

#### 3. Reject Request
```
POST /issuer/requests/{requestId}/reject
Content-Type: application/json
```

Issuer rejects credential request with reason.

#### 4. Get Request Status
```
GET /issuer/requests/{requestId}
```

Check status of credential request.

---

## Example Requests

### Example 1: Request Credentials (Holder → Issuer)

**Step 1: Generate Authentication Token**

Before requesting credentials, the holder must generate a self-issued ID token:

```java
// Generate token for the issuer
String issuerDid = "did:web:issuer.example";
String token = tokenService.createAndSignToken(issuerDid, null);
```

**Token Structure:**
```json
{
  "iss": "did:web:holder.example",
  "sub": "did:web:holder.example",
  "aud": "did:web:issuer.example",
  "iat": 1734350400,
  "exp": 1734350700,
  "jti": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Step 2: Send Credential Request**

**Request:**
```bash
POST http://localhost:8080/issuer/credentials
Authorization: Bearer eyJhbGciOiJFUzI1NiIsImtpZCI6ImRpZDp3ZWI6aG9sZGVyLmV4YW1wbGUja2V5LTEifQ...
Content-Type: application/json

{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "CredentialRequestMessage",
  "holderPid": "did:web:localhost%3A8081:holder",
  "credentials": [
    {"id": "membership-credential"}
  ]
}
```

**Note:** The `Authorization` header contains the self-issued token that:
- Proves the holder controls their DID
- Specifies the issuer as the intended audience
- Expires in 5 minutes

**Step 3: Issuer Validates Token**

```java
// Issuer side - in IssuerController
String token = authorization.substring("Bearer ".length());
JWTClaimsSet claims = tokenService.validateToken(token);

// Verify audience matches issuer's DID
if (!myDid.equals(claims.getAudience().get(0))) {
    throw new SecurityException("Invalid audience");
}

// Extract holder DID from token
String holderDid = claims.getIssuer();
```

**Response:**
```
HTTP/1.1 201 Created
Location: /issuer/requests/req-abc123
```

### Example 2: Approve Credential Request (Issuer)

**Simple Approval (Auto-generates credentials):**
```bash
POST http://localhost:8080/issuer/requests/req-abc123/approve
Content-Type: application/json

{}
```

**Response:**
```json
{
  "status": "delivered",
  "message": "Credentials successfully delivered to holder",
  "credentialsCount": 1,
  "credentialTypes": ["MembershipCredential"]
}
```

**Manual Approval (Custom credentials):**
```bash
POST http://localhost:8080/issuer/requests/req-abc123/approve
Content-Type: application/json

{
  "credentials": [
    {
      "credentialType": "MembershipCredential",
      "payload": "eyJraWQiOiJkaWQ6ZXhhbXBsZTppc3N1ZXI0NTYja2V5LTEi...",
      "format": "jwt"
    }
  ]
}
```

### Example 3: Receive Credentials (Issuer → Holder)

**Request:**
```bash
POST http://localhost:8081/dcp/credentials
Authorization: Bearer <holder-token>
Content-Type: application/json

{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "CredentialMessage",
  "issuerPid": "issuer-pid-12345",
  "holderPid": "did:example:holder123",
  "status": "ISSUED",
  "requestId": "request-67890",
  "credentials": [
    {
      "credentialType": "MembershipCredential",
      "payload": "eyJraWQiOiJkaWQ6ZXhhbXBsZTppc3N1ZXI0NTYja2V5LTEi...",
      "format": "jwt"
    }
  ]
}
```

**Response:**
```json
{
  "saved": 1
}
```

### Example 4: Query Presentation (Verifier → Holder)

**Request:**
```bash
POST http://localhost:8081/dcp/presentations/query
Authorization: Bearer <verifier-token>
Content-Type: application/json

{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "PresentationQueryMessage",
  "scope": ["MembershipCredential"]
}
```

**Response (JWT VP):**
```json
{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "PresentationResponseMessage",
  "presentation": [
    "eyJraWQiOiJvOVdzdTN2SF9LTXM3VE5UMFA4UDlKTHRIMmJRZWUtbVRwNVBleUdDNHQ4IiwiYWxnIjoiRVMyNTYifQ.eyJpc3MiOiJkaWQ6d2ViOmxvY2FsaG9zdDo4MDgwIiwic3ViIjoiZGlkOndlYjpsb2NhbGhvc3Q6ODA4MCIsInZwIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIl0sInR5cGUiOlsiVmVyaWZpYWJsZVByZXNlbnRhdGlvbiJdLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6W3sidHlwZSI6Ik1lbWJlcnNoaXBDcmVkZW50aWFsIiwiZm9ybWF0Ijoiand0Iiwiand0IjoiZXlKcmFXUWlPaUp2T1ZkemRUTjJTRjlMVFhNM1ZFNVVNRkE0VURsS1RIUklNbUpSWldVdGJWUndOVkJsZVVkRE5IUTRJaXdpWVd4bklqb2lSVk15TlRZaWZRLmV5SnpkV0lpT2lKa2FXUTZkMlZpT214dlkyRnNhRzl6ZERvNE1EZ3dJaXdpYVhOeklqb2laR2xrT25kbFlqcHNiMk5oYkdodmMzUTZPREE0TUNJc0ltVjRjQ0k2TVRjNU56TXpNVFEwTVN3aWFXRjBJam94TnpZMU56azFORFF4TENKMll5STZleUpqY21Wa1pXNTBhV0ZzVTNWaWFtVmpkQ0k2ZXlKdFpXMWlaWEp6YUdsd1ZIbHdaU0k2SWxCeVpXMXBkVzBpTENKdFpXMWlaWEp6YUdsd1NXUWlPaUpOUlUxQ1JWSXRNakZpTmpNNU1HSWlMQ0pwWkNJNkltUnBaRHAzWldJNmJHOWpZV3hvYjNOME9qZ3dPREFpTENKemRHRjBkWE1pT2lKQlkzUnBkbVVpZlN3aWRIbHdaU0k2V3lKV1pYSnBabWxoWW14bFEzSmxaR1Z1ZEdsaGJDSXNJazFsYldKbGNuTm9hWEJEY21Wa1pXNTBhV0ZzSWwwc0lrQmpiMjUwWlhoMElqcGJJbWgwZEhCek9pOHZkM2QzTG5jekxtOXlaeTh5TURFNEwyTnlaV1JsYm5ScFlXeHpMM1l4SWl3aWFIUjBjSE02THk5bGVHRnRjR3hsTG05eVp5OWpjbVZrWlc1MGFXRnNjeTkyTVNKZGZTd2lhblJwSWpvaWRYSnVPblYxYVdRNll6WTBOR0l5T0RJdE9XTTROQzAwTkRjekxXSmpZak10T0RGa05UbGpNVEprT1RVekluMC5pTXR4NGxHOWVscDdXSXZVS29hZjB3RmczdGVGY1FSN05sVTBNNkU3dzd0alFvVXlOOEFTd2Q1enBpNjliSXFuMWtMZE5vWTFRbUEtZjNTNVVVYkdIQSJ9XSwicHJvZmlsZUlkIjoiVkMxMV9TTDIwMjFfSldUIn0sImlhdCI6MTc2NTg4NTkzNiwianRpIjoidXJuOnV1aWQ6OTAwNzU3OGUtMzBlZC00YjUzLTk2MDMtOTUwNzJlMjkyZWMxIn0.XxLJAu1PsFPfFEmdBxSZMWo5BshBpciC7-NbsxLE27S6-jAHQjpJhafCokzXqxdkozJ0_4cywnJsRS_IWRtzAA"
  ]
}
```

**Decoded VP JWT (for reference):**

*Header:*
```json
{
  "kid": "o9Wsu3vH_KMs7TNT0P8P9JLtH2bQee-mTp5PeyGC4t8",
  "alg": "ES256"
}
```

*Payload:*
```json
{
  "iss": "did:web:localhost:8080",
  "sub": "did:web:localhost:8080",
  "vp": {
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiablePresentation"],
    "verifiableCredential": [{
      "type": "MembershipCredential",
      "format": "jwt",
      "jwt": "eyJraWQi... (embedded JWT VC)"
    }],
    "profileId": "VC11_SL2021_JWT"
  },
  "iat": 1765885936,
  "jti": "urn:uuid:9007578e-30ed-4b53-9603-95072e292ec1"
}
```

### Example 5: Decoded JWT Credential

**JWT Token:**
```
eyJraWQiOiJkaWQ6ZXhhbXBsZTppc3N1ZXI0NTYja2V5LTEiLCJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJkaWQ6ZXhhbXBsZTpob2xkZXIxMjMi...
```

**Decoded Header:**
```json
{
  "alg": "ES256",
  "typ": "JWT",
  "kid": "did:example:issuer456#key-1"
}
```

**Decoded Payload:**
```json
{
  "sub": "did:example:holder123",
  "iss": "did:example:issuer456",
  "iat": 1733363431,
  "exp": 1764899431,
  "jti": "urn:uuid:credential-12345678-1234-1234-1234-1234567890ab",
  "vc": {
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      "https://example.org/credentials/v1"
    ],
    "type": ["VerifiableCredential", "MembershipCredential"],
    "credentialSubject": {
      "id": "did:example:holder123",
      "membershipId": "MEMBER-2024-001",
      "membershipType": "Premium",
      "status": "Active"
    }
  }
}
```

---

## Authentication Flow

### VC/VP Authentication in Protocol Endpoints

The TRUE Connector supports three authentication methods with priority:

1. **VC/VP Authentication** (Highest priority)
2. **JWT Authentication** (Fallback)
3. **Basic Authentication** (Failsafe)

### Authentication Filter Chain

```
Request
  ↓
DataspaceProtocolEndpointsAuthenticationFilter
  ↓ (if protocol auth enabled)
VcVpAuthenticationFilter
  ├─> Parse VP from Authorization header
  ├─> Create VcVpAuthenticationToken
  └─> Delegate to VcVpAuthenticationProvider
       ├─> Validate using PresentationValidationService
       ├─> Check profile homogeneity
       ├─> Verify issuer trust
       ├─> Check expiration
       ├─> Check revocation
       └─> Validate schema
  ↓ (if VP validation fails)
JwtAuthenticationFilter
  └─> Try JWT token authentication
  ↓ (if JWT fails)
BasicAuthenticationFilter
  └─> Try username/password authentication
  ↓
Protected Endpoint
```

### Validation Process

**Location:** `dcp/src/main/java/it/eng/dcp/service/PresentationValidationService.java`

The validation service performs:
1. **Profile Homogeneity**: All credentials in VP must use the same profile
2. **Issuer Trust**: Issuers must be in the trusted issuers list
3. **Expiration Check**: `issuanceDate < now < expirationDate`
4. **Revocation Check**: Credentials must not be revoked
5. **Schema Validation**: Credentials must match expected schemas

### Sending Authenticated Requests

**Option 1: JWT VP Token (Recommended)**
```http
POST /connector/negotiations/request HTTP/1.1
Authorization: Bearer eyJraWQiOiJvOVdzdTN2SF9LTXM3VE5UMFA4UDlKTHRIMmJRZWUtbVRwNVBleUdDNHQ4...
Content-Type: application/json
```

The JWT contains a `vp` claim with the Verifiable Presentation.

**Option 2: JSON VP (Direct)**
```http
POST /connector/negotiations/request HTTP/1.1
Authorization: Bearer {"@context":["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"]...}
Content-Type: application/json
```

**Option 3: Base64-encoded JSON VP**
```http
POST /connector/negotiations/request HTTP/1.1
Authorization: Bearer eyJAY29udGV4dCI6WyJodHRwczovL3czaWQub3JnL2RzcGFjZS1kY3AvdjEuMC9kY3AuanNvbmxkIl0...
Content-Type: application/json
```

---

## Code References

### Key Components

#### 1. DCP Module Structure
```
dcp/src/main/java/it/eng/dcp/
├── autoconfigure/
│   └── DcpAutoConfiguration.java          # Auto-configuration
├── config/
│   └── DcpProperties.java                 # Configuration properties
├── model/
│   ├── VerifiableCredential.java          # VC model
│   ├── VerifiablePresentation.java        # VP model
│   ├── CredentialMessage.java             # Credential delivery message
│   └── PresentationQueryMessage.java      # Presentation request
├── repository/
│   ├── CredentialRepository.java          # MongoDB repository for VCs
│   └── CredentialRequestRepository.java   # MongoDB repository for requests
├── rest/
│   ├── DcpController.java                 # Holder endpoints (/dcp/*)
│   └── IssuerController.java              # Issuer endpoints (/issuer/*)
├── service/
│   ├── HolderService.java                 # Credential storage & presentation
│   ├── PresentationService.java           # VP creation
│   ├── PresentationValidationService.java # VP validation
│   ├── IssuerService.java                 # Credential issuance
│   ├── CredentialDeliveryService.java     # Credential delivery
│   └── SelfIssuedIdTokenService.java      # Authentication tokens
└── signer/
    └── BasicVerifiablePresentationSigner.java # VP signing
```

#### 2. Connector Authentication
```
connector/src/main/java/it/eng/connector/configuration/
├── VcVpAuthenticationToken.java           # Spring Security token for VP
├── VcVpAuthenticationProvider.java        # Validates VP tokens
├── VcVpAuthenticationFilter.java          # Extracts VP from headers
└── WebSecurityConfig.java                 # Security configuration
```

#### 3. Configuration Files
```
connector/src/main/resources/application.properties
```

**DCP Properties:**
```properties
# Enable/disable DCP module
dcp.enabled=true

# Connector's DID (REQUIRED for token generation)
dcp.connector.did=did:web:connector-a

# Trusted issuers (comma-separated DIDs)
dcp.trusted-issuers=did:web:issuer.example,did:web:dataspace-authority

# VP authentication for protocol endpoints
dcp.vp.enabled=true
dcp.vp.scope=MembershipCredential,OrganizationCredential
```

**Critical Configuration Note:**
- `dcp.connector.did` is **required** for the `SelfIssuedIdTokenService` to create authentication tokens
- The DID must be resolvable and contain valid EC keys in the DID document
- Without this configuration, credential requests and deliveries will fail

### Important Methods

#### Create Presentation
**File:** `dcp/src/main/java/it/eng/dcp/service/PresentationService.java`

```java
public PresentationResponseMessage createPresentation(PresentationQueryMessage query) {
    // Fetch credentials by type
    List<VerifiableCredential> credentials = 
        credentialRepository.findByCredentialTypeIn(query.getScope());
    
    // Group by profileId for homogeneity
    Map<String, List<VerifiableCredential>> groups = 
        credentials.stream().collect(Collectors.groupingBy(vc -> vc.getProfileId()));
    
    // Create VP for each profile group
    for (Map.Entry<String, List<VerifiableCredential>> group : groups.entrySet()) {
        VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
            .holderDid(groupCreds.get(0).getHolderDid())
            .credentials(fullCredentials)  // Embed full VCs
            .profileId(profile)
            .build();
        
        // Sign VP with holder's private key
        Object signedVp = vpSigner.signPresentation(vp);
        signedPresentations.add(signedVp);
    }
    
    return PresentationResponseMessage.Builder.newInstance()
        .presentation(signedPresentations)
        .build();
}
```

#### Validate Presentation
**File:** `dcp/src/main/java/it/eng/dcp/service/PresentationValidationService.java`

```java
public ValidationReport validate(PresentationResponseMessage presentation,
                                 List<String> requiredTypes,
                                 String tokenContext) {
    // Check profile homogeneity
    profileResolver.checkHomogeneity(presentation);
    
    // Verify issuer trust
    for (VC vc : extractedCredentials) {
        if (!issuerTrustService.isTrusted(vc.getIssuer())) {
            return ValidationReport.failed("Untrusted issuer: " + vc.getIssuer());
        }
    }
    
    // Check expiration
    if (vc.isExpired()) {
        return ValidationReport.failed("Credential expired");
    }
    
    // Check revocation
    if (revocationService.isRevoked(vc)) {
        return ValidationReport.failed("Credential revoked");
    }
    
    return ValidationReport.success();
}
```

#### Approve Credential Request
**File:** `dcp/src/main/java/it/eng/dcp/service/IssuerService.java`

```java
public ApprovalResponse approveRequest(String requestId, 
                                       ApprovalRequest approval) {
    // Retrieve request from database
    CredentialRequest request = credentialRequestRepository.findById(requestId);
    
    // Auto-generate credentials if not provided
    List<Credential> credentials = approval.getCredentials();
    if (credentials == null || credentials.isEmpty()) {
        credentials = autoGenerateCredentials(request);
    }
    
    // Deliver credentials to holder
    credentialDeliveryService.deliver(request.getHolderPid(), credentials);
    
    // Update status
    request.setStatus(CredentialRequestStatus.ISSUED);
    credentialRequestRepository.save(request);
    
    return ApprovalResponse.success(credentials.size());
}
```

---

## Debugging Tips

### 1. Enable Debug Logging

Add to `application.properties`:
```properties
# DCP module debug logging
logging.level.it.eng.dcp=DEBUG

# Spring Security authentication
logging.level.org.springframework.security=DEBUG

# HTTP requests/responses
logging.level.org.springframework.web.client.RestTemplate=DEBUG
```

### 2. Common Issues

#### Issue: "Could not resolve Credential Service endpoint"
**Cause:** Holder DID format incorrect or DID document not accessible

**Solution:**
- Ensure DID follows format: `did:web:localhost%3A8080:holder`
- Verify DID document at: `https://localhost:8080/holder/.well-known/did.json`
- Check `service` array contains `CredentialService` entry

#### Issue: "Failed to deliver credentials"
**Cause:** Holder's `/dcp/credentials` endpoint not running or not accessible

**Solution:**
- Verify holder connector is running
- Check endpoint: `POST http://holder:8080/dcp/credentials`
- Verify network connectivity between issuer and holder

#### Issue: "Credential request not found"
**Cause:** Invalid request ID or request already processed

**Solution:**
- Verify request ID from initial credential request response
- Check request status: `GET /issuer/requests/{requestId}`
- Ensure request hasn't been approved or rejected already

#### Issue: "Untrusted issuer"
**Cause:** Issuer DID not in trusted issuers list

**Solution:**
- Add issuer to trusted list in `application.properties`:
  ```properties
  dcp.trusted-issuers=did:web:issuer.example,did:web:another-issuer
  ```

#### Issue: "Credential expired"
**Cause:** Current time is outside the credential's validity period

**Solution:**
- Check `issuanceDate` and `expirationDate` in credential
- Ensure system clocks are synchronized
- Request new credentials if expired

#### Issue: "VP validation failed: profile mismatch"
**Cause:** VP contains credentials with different profileIds

**Solution:**
- Ensure all credentials in a VP use the same profile (e.g., `VC11_SL2021_JWT`)
- System should auto-group credentials by profile

### 3. Testing Tools

#### Use Postman
Import the collection: `True_connector_DSP.postman_collection.json`

**Key requests:**
- Create Participant
- Request Credentials
- Approve Credentials
- Query Presentation

#### Check MongoDB
View stored credentials:
```javascript
// Connect to MongoDB
use connector_db

// List all credentials
db.verifiable_credentials.find().pretty()

// Find credentials by type
db.verifiable_credentials.find({credentialType: "MembershipCredential"}).pretty()

// Check credential requests
db.credential_requests.find().pretty()
```

#### Decode JWT Tokens
Use [jwt.io](https://jwt.io) to decode and inspect JWT VCs and VPs.

### 4. Flow Debugging

Add breakpoints at:
1. `DcpController.receiveCredentials()` - When credentials arrive
2. `HolderService.createPresentation()` - When creating VP
3. `VcVpAuthenticationProvider.authenticate()` - When validating VP
4. `PresentationValidationService.validate()` - Detailed validation steps

### 5. Database Queries

**Check stored credentials:**
```java
// In debug console or test
List<VerifiableCredential> creds = credentialRepository.findByHolderDid("did:web:localhost:8080");
creds.forEach(c -> log.info("Credential: {} - Type: {} - Expires: {}", 
    c.getId(), c.getCredentialType(), c.getExpirationDate()));
```

**Check credential requests:**
```java
List<CredentialRequest> requests = credentialRequestRepository.findByHolderPid("did:web:holder123");
requests.forEach(r -> log.info("Request: {} - Status: {}", r.getId(), r.getStatus()));
```

---

## Flow Diagrams

### Complete Authentication Flow

See: [vc_vp_architecture_diagram.md](vc_vp_architecture_diagram.md)

```
Client ──> VcVpFilter ──> VcVpProvider ──> PresentationValidationService
                │              │                      │
                │              │                      ├─> ProfileResolver
                │              │                      ├─> IssuerTrustService
                │              │                      ├─> ExpirationCheck
                │              │                      ├─> RevocationService
                │              │                      └─> SchemaValidator
                │              │                      
                │              └─> Authenticated Token
                │                   (if valid)
                │
                └─> JwtFilter (if VP fails)
                     └─> BasicAuth (if JWT fails)
```

### VP Creation Flow

See: [VP_JWT_FLOW_DIAGRAM.md](../dcp/doc/VP_JWT_FLOW_DIAGRAM.md)

---

## Further Reading

### DCP Module Documentation
- [DCP README](../README.md)
- [Quick Start Guide](quick_start.md)
- [Practical Examples](practical_example.md)
- [Presentation Verification Flow](presentation_verification_flow.md)
- [Credential Message Examples](credential_message_examples.md)
- [Implementation Summary](../dcp/doc/IMPLEMENTATION_SUMMARY.md)

### General Documentation
- [Verifiable Credentials (Detailed)](verifiable_credentials.md)
- [Security](../../doc/security.md)

### External Resources
- [W3C Verifiable Credentials Data Model](https://www.w3.org/TR/vc-data-model/)
- [W3C DID Core](https://www.w3.org/TR/did-core/)
- [DCP Specification](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/)
