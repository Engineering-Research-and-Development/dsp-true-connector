# Presentation Verification Flow - Complete Guide

## Overview

This document explains the complete flow when a holder obtains a presentation using `PresentationQueryMessage`, how the JWT VP token is created, and where the verification logic exists for the verifier to check the holder's signature and issuer's signature on the VCs.

## Flow Diagram

```
Verifier → (1) PresentationQueryMessage → Holder
Holder   → (2) Creates JWT VP (signed by holder) → Verifier  
Verifier → (3) Verifies holder signature + issuer signatures → Decision
```

---

## Step 1: Verifier Sends PresentationQueryMessage

### Location
- **Class**: `DcpVerifierClient`
- **File**: `dcp/src/main/java/it/eng/dcp/service/DcpVerifierClient.java`
- **Method**: `fetchPresentations(String holderBase, List<String> requiredTypes, String accessToken)`

### What Happens
1. Verifier creates a `PresentationQueryMessage` with required credential types
2. Sends POST request to holder's endpoint: `/dcp/presentations/query`
3. Includes Bearer token for authentication (optional)

### Code Reference
```java
PresentationQueryMessage.Builder b = PresentationQueryMessage.Builder.newInstance();
if (requiredTypes != null && !requiredTypes.isEmpty()) b.scope(requiredTypes);
PresentationQueryMessage query = b.build();

String target = holderBase + "/dcp/presentations/query";
GenericApiResponse<String> resp = httpClient.sendRequestProtocol(target, body, authHeader);
```

---

## Step 2: Holder Creates JWT VP Token (Signed by Holder)

### 2.1 Holder Receives Request

**Location**: `DcpController`
- **File**: `dcp/src/main/java/it/eng/dcp/rest/DcpController.java`
- **Endpoint**: `POST /dcp/presentations/query`
- **Method**: `queryPresentations()`

```java
@PostMapping(path = "/presentations/query")
public ResponseEntity<?> queryPresentations(
    @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
    @RequestBody PresentationQueryMessage query) {
    
    // Validate verifier's bearer token
    var claims = holderService.authorizePresentationQuery(token);
    String holderDid = claims.getSubject();
    
    // Create presentation
    PresentationResponseMessage resp = holderService.createPresentation(query, holderDid);
    return ResponseEntity.ok(resp);
}
```

### 2.2 Presentation Creation Flow

**Location**: `HolderService` → `PresentationService` → `BasicVerifiablePresentationSigner`

#### a) HolderService delegates to PresentationService
- **File**: `dcp/src/main/java/it/eng/dcp/service/HolderService.java`
- **Method**: `createPresentation(PresentationQueryMessage query, String holderDid)`

```java
public PresentationResponseMessage createPresentation(PresentationQueryMessage query, String holderDid) {
    log.info("Creating presentation response for holder: {}", holderDid);
    PresentationResponseMessage response = presentationService.createPresentation(query);
    return response;
}
```

#### b) PresentationService fetches credentials and groups by profileId
- **File**: `dcp/src/main/java/it/eng/dcp/service/PresentationService.java`
- **Method**: `createPresentation(PresentationQueryMessage query)`

```java
public PresentationResponseMessage createPresentation(PresentationQueryMessage query) {
    // Fetch credentials based on scope (required types)
    List<VerifiableCredential> fetched = credentialRepository.findByCredentialTypeIn(requiredTypes);
    
    // Group credentials by profileId for homogeneous presentations
    Map<String, List<VerifiableCredential>> groups = fetched.stream()
        .collect(Collectors.groupingBy(vc -> vc.getProfileId()));
    
    List<Object> signedPresentations = new ArrayList<>();
    
    for (Map.Entry<String, List<VerifiableCredential>> group : groups.entrySet()) {
        // Build VerifiablePresentation
        VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
            .holderDid(groupCreds.get(0).getHolderDid())
            .credentialIds(credentialIds)
            .profileId(profile)
            .build();
        
        // Sign the VP
        String format = determineFormat(query.getPresentationDefinition(), profile);
        Object signed = vpSigner.sign(vp, format);  // Returns JWT string
        signedPresentations.add(signed);
    }
    
    return PresentationResponseMessage.Builder.newInstance()
        .presentation(signedPresentations)
        .build();
}
```

#### c) BasicVerifiablePresentationSigner creates JWT VP signed by holder
- **File**: `dcp/src/main/java/it/eng/dcp/service/BasicVerifiablePresentationSigner.java`
- **Method**: `sign(VerifiablePresentation vp, String format)`

**This is where the JWT VP token is created!**

```java
@Override
public Object sign(VerifiablePresentation vp, String format) {
    if ("jwt".equalsIgnoreCase(format)) {
        // 1. Get holder's signing key
        ECKey signingKey = keyService.getSigningJwk();
        
        // 2. Build JWT claims with VP embedded
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
            .claim("vp", buildVPClaim(vp))  // VP contains credential IDs/references
            .issuer(vp.getHolderDid())      // Holder DID as issuer
            .subject(vp.getHolderDid())      // Holder DID as subject
            .issueTime(new Date())
            .jwtID(vp.getId())
            .build();
        
        // 3. Create JWS header with algorithm and key ID
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
            .keyID(signingKey.getKeyID())
            .build();
        
        // 4. Create and sign the JWT
        SignedJWT signedJWT = new SignedJWT(header, claimsSet);
        JWSSigner signer = new ECDSASigner(signingKey);
        signedJWT.sign(signer);
        
        // 5. Return serialized JWT string
        return signedJWT.serialize();
    }
    // ... JSON-LD format handling ...
}
```

**JWT VP Structure:**
```
Header:
{
  "alg": "ES256",
  "kid": "holder-key-id"
}

Payload:
{
  "iss": "did:web:holder.example",
  "sub": "did:web:holder.example",
  "jti": "urn:uuid:presentation-id",
  "iat": 1702310400,
  "vp": {
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiablePresentation"],
    "verifiableCredential": ["urn:uuid:cred-1", "urn:uuid:cred-2"],
    "profileId": "VC-1.1-SL2021-JWT"
  }
}

Signature: <ECDSA signature using holder's private key>
```

---

## Step 3: Verifier Validates the JWT VP Token

### 3.1 Verifier Receives PresentationResponseMessage

**Location**: `DcpVerifierClient`
- **File**: `dcp/src/main/java/it/eng/dcp/service/DcpVerifierClient.java`
- **Method**: `fetchPresentations()` returns `PresentationResponseMessage`

The `PresentationResponseMessage` contains:
```java
{
  "@context": ["https://w3id.org/dspace/2024/1/context.json"],
  "@type": "PresentationResponseMessage",
  "presentation": [
    "eyJhbGc...JWT_VP_STRING..."  // JWT VP signed by holder
  ]
}
```

### 3.2 Verifier Validates the Presentation

**Location**: `VerifierService` (module `dcp-verifier`)
- **File**: `dcp/dcp-verifier/src/main/java/it/eng/dcp/verifier/service/VerifierService.java`
- **Entry point**: `validateAndQueryHolderPresentations(String selfIssuedIdToken)`, called by the connector's `DcpVerifierAuthenticationProvider`, which grants `ROLE_CONNECTOR` only when it succeeds
- **Validation**: `validatePresentationResponse()` and `validateCredential()`, both using `verifyProof()`

`VerifierService` is the only presentation validation path. The former Holder-side `PresentationValidationServiceImpl` had no callers and verified no proofs; it was removed (#402).

### 3.3 VP Proof Verification (DCP §5.4.3)

For each JWT VP in the response:

1. The holder DID is the VP `iss` (or `sub` when `iss` is absent) and must equal the presenting holder.
2. `HttpDidResolverService.resolveVerificationMethod(holderDid, kid, "authentication")` selects the verification method the JWT `kid` references in the holder's DID document (DCP §4.3.3 key selection). The method must be referenced by the `authentication` relationship; no other relationship is tried.
3. The DID part of the selected verification method's ID must equal the holder DID.
4. The JWT signature must verify with that key (EC keys, ES256).

### 3.4 VC Proof Verification Inside the VP

For each JWT VC in `vp.verifiableCredential`:

1. The issuer DID is the VC `iss`.
2. `resolveVerificationMethod(issuerDid, kid, "assertionMethod")` selects the issuer key. It must be referenced by `assertionMethod`; `capabilityInvocation` and `authentication` are not accepted.
3. The DID part of the selected verification method's ID must equal the VC `iss`, and the `issuer` property (`vc.issuer` in VC 1.1, top-level `issuer` in VC 2.0), when present, must name the same DID (§5.4.3: "the DID in the VerificationMethod of the Verifiable Credential has the same value as the issuer"). This check runs after the signature check.
4. The JWT signature must verify with that key.
5. Holder binding: every `credentialSubject.id` and a present `sub` equal the holder DID.
6. `exp` and `iat` are checked, and `IssuerTrustService` must trust the issuer for the credential type.

Any failure makes the presentation invalid: `VerifierService` throws `SecurityException`, publishes `PRESENTATION_INVALID`, and the request is not authenticated. Rejected cases include a tampered VP or VC (invalid signature), a VC signed with a key of a DID other than its issuer, and a VP key without `authentication`.

Not covered yet: credential revocation status (#418) and VP audience and lifetime (#403).

---

## Key Files Reference

| Component | File | Key Method |
|-----------|------|------------|
| **Verifier Client** | `DcpVerifierClient.java` | `fetchPresentations()` |
| **Holder Endpoint** | `DcpController.java` | `queryPresentations()` |
| **Presentation Creation** | `PresentationService.java` | `createPresentation()` |
| **VP JWT Signing** | `BasicVerifiablePresentationSigner.java` | `sign(vp, "jwt")` |
| **VP/VC Validation** | `VerifierService.java` | `validateAndQueryHolderPresentations()` |
| **Key selection** | `HttpDidResolverService.java` | `resolveVerificationMethod()` |
| **Key Management** | `KeyService.java` | `getSigningJwk()` |

---

## Next Steps After Receiving PresentationQueryMessage

1. **Holder** receives query at `/dcp/presentations/query`
2. **HolderService** authenticates verifier's bearer token
3. **PresentationService** fetches matching credentials from repository
4. **PresentationService** groups credentials by profileId (homogeneity requirement)
5. **BasicVerifiablePresentationSigner** creates JWT VP:
   - Signs VP with holder's private key
   - Embeds credential references in `vp.verifiableCredential`
   - Sets holder DID as `iss` and `sub`
6. **Holder** returns `PresentationResponseMessage` with JWT VP string
7. **Verifier** validates it in `VerifierService` (sections 3.2 to 3.4)
8. **Connector** grants `ROLE_CONNECTOR` only when validation succeeds
