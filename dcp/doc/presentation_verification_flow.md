# Presentation Verification Flow - Complete Guide

## Overview

This document explains the complete flow when a holder obtains a presentation using `PresentationQueryMessage`, how the JWT VP token is created, and where the verification logic exists for the verifier to check the holder's signature and issuer's signature on the VCs.

## Flow Diagram

```
Verifier → (1) PresentationQueryMessage → Holder
Holder   → (2) Creates JWT VP (signed by holder) → Verifier  
Verifier → (3) Verifies holder signature + issuer signatures → Decision
```

## Authenticating the Query (DCP v1.0.1 §5.1, §5.3.1)

The client (holder) authenticates to the Verifier with a Self-Issued ID Token (`iss` = `sub` = holder DID,
`aud` = Verifier DID). Its `token` claim carries an **access token** for the holder's Credential Service,
built by `SelfIssuedIdTokenService.createAccessTokenWithScopes()` in the format of
[ADR D-TEC-014](../../doc/decisions/technical/D-TEC-014-credential-service-access-token-format.md):

| Part | Value |
|---|---|
| JWS header `typ` | `at+jwt` (never accepted as a Self-Issued ID Token) |
| `iss`, `sub` | holder DID |
| `aud` | Verifier DID: only this Verifier can use it |
| `scope` | the scopes the Verifier may query (`dcp.vp.scope`); no scope authorizes nothing |
| `iat`, `nbf`, `exp`, `jti` | 5 minutes validity |

The Verifier does not send that access token as its bearer token. `VerifierService` builds a Self-Issued ID
Token **of its own** (`iss` = `sub` = Verifier DID, `aud` = holder DID) with the received access token,
unchanged, in its `token` claim, and sends it to `POST {CredentialService}/presentations/query`.

The Credential Service (`HolderService.authorizePresentationQuery()`) then checks, in order:

1. the bearer token is a valid Self-Issued ID Token with `aud` = its own DID (`SelfIssuedIdTokenService.validateToken()`);
2. its `token` claim holds an access token, otherwise **401** (#407 decision Q2);
3. the access token is valid (`SelfIssuedIdTokenService.validateAccessToken()`): `typ` `at+jwt`, issued by this
   holder (`iss` = `sub` = own DID), signed with a `capabilityInvocation` key of the holder DID document,
   current, and `aud` = the Verifier's `sub`, so a token captured by one Verifier cannot be used by another.

Any failure is **401**. `PresentationService` then takes the authorized scopes **only** from the validated
access token: effective scopes = requested ∩ authorized. A requested scope that is not authorized is dropped
and the response is still 2xx, with fewer presentations. Every credential lookup is constrained to the holder
DID (the access token `sub`); there is no unscoped `findAll()`. Rate limiting is keyed by the Verifier DID.

---

## Step 1: Verifier Sends PresentationQueryMessage

### Location
- **Class**: `DcpVerifierClient`
- **File**: `dcp/src/main/java/it/eng/dcp/service/DcpVerifierClient.java`
- **Method**: `fetchPresentations(String holderBase, List<String> requiredTypes, String selfIssuedIdToken)`

The connector's active Verifier path is `VerifierService.validateAndQueryHolderPresentations()` (module
`dcp-verifier`); `DcpVerifierClient` is a standalone client with the same contract.

### What Happens
1. Verifier creates a `PresentationQueryMessage` with required credential types
2. Sends POST request to holder's endpoint: `/dcp/presentations/query`
3. Authenticates with the Verifier's own Self-Issued ID Token carrying the holder-issued access token
   (required; see *Authenticating the Query* above)

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
    
    // Validate the Verifier's Self-Issued ID Token, then the access token in its "token" claim
    PresentationQueryAuthorization queryAuthorization = holderService.authorizePresentationQuery(token);

    // Rate limit per Verifier DID, then create the presentation
    holderService.checkRateLimit(queryAuthorization.verifierDid());
    PresentationResponseMessage resp = holderService.createPresentation(query, queryAuthorization);
    return ResponseEntity.ok(resp);
}
```

### 2.2 Presentation Creation Flow

**Location**: `HolderService` → `PresentationService` → `BasicVerifiablePresentationSigner`

#### a) HolderService delegates to PresentationService
- **File**: `dcp/src/main/java/it/eng/dcp/service/HolderService.java`
- **Method**: `createPresentation(PresentationQueryMessage query, PresentationQueryAuthorization authorization)`

```java
public PresentationResponseMessage createPresentation(PresentationQueryMessage query,
                                                      PresentationQueryAuthorization authorization) {
    return presentationService.createPresentation(
            query, authorization.holderDid(), authorization.accessTokenClaims());
}
```

#### b) PresentationService fetches credentials and groups by profileId
- **File**: `dcp/src/main/java/it/eng/dcp/service/PresentationService.java`
- **Method**: `createPresentation(PresentationQueryMessage query, String holderDid, JWTClaimsSet accessTokenClaims)`

```java
public PresentationResponseMessage createPresentation(PresentationQueryMessage query, String holderDid,
                                                      JWTClaimsSet accessTokenClaims) {
    // Effective scopes = requested ∩ authorized (access token "scope"); lookups constrained to the holder
    List<VerifiableCredential> fetched =
            credentialRepository.findByHolderDidInAndCredentialTypeIn(holderDidForms(holderDid), credentialTypes);
    
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

**Location**: `PresentationValidationServiceImpl`
- **File**: `dcp/src/main/java/it/eng/dcp/service/PresentationValidationServiceImpl.java`
- **Method**: `validate(PresentationResponseMessage rsp, List<String> requiredCredentialTypes, TokenContext tokenCtx)`

### 3.3 JWT VP Signature Verification Logic

**IMPORTANT**: Currently, the VP signature verification is **NOT explicitly implemented** in the validation service. However, based on the pattern used for other JWT validation, here's where it SHOULD be added:

#### Expected VP Signature Verification Flow (following SelfIssuedIdTokenService pattern):

**Reference Implementation**: `SelfIssuedIdTokenService.validateToken()`
- **File**: `dcp/src/main/java/it/eng/dcp/service/SelfIssuedIdTokenService.java`

```java
public JWTClaimsSet validateToken(String token) {
    try {
        // 1. Parse the JWT
        SignedJWT jwt = SignedJWT.parse(token);
        
        // 2. Extract holder DID from issuer claim
        String issuer = jwt.getJWTClaimsSet().getIssuer();
        String kid = jwt.getHeader().getKeyID();
        
        // 3. Resolve holder's public key using DID resolver
        JWK jwk = didResolver.resolvePublicKey(issuer, kid, null);
        ECKey ecPub = (ECKey) jwk;
        
        // 4. Verify the signature using holder's public key
        JWSVerifier verifier = new ECDSAVerifier(ecPub.toECPublicKey());
        if (!jwt.verify(verifier)) {
            throw new SecurityException("Invalid signature");
        }
        
        // 5. Validate claims (exp, iat, jti)
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        Instant now = Instant.now();
        if (claims.getExpirationTime().toInstant().isBefore(now)) {
            throw new SecurityException("Token expired");
        }
        
        return claims;
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}
```

### 3.4 Credential Validation Inside VP

Once the VP signature is verified, the service validates each embedded credential:

**Location**: `PresentationValidationServiceImpl.validate()`

```java
// For each presentation (JWT string or JSON-LD object)
for (Object pObj : presentations) {
    // Parse VP and extract credentials
    VerifiablePresentation vp = parseVp(pObj);
    List<VerifiableCredential> creds = extractCredentialsFromVp(vp);
    
    // For each credential in the presentation
    for (VerifiableCredential vc : creds) {
        // 1. Verify issuer trust
        String issuer = extractIssuer(vc);
        if (!issuerTrustService.isTrusted(vc.getCredentialType(), issuer)) {
            report.addError("ISSUER_UNTRUSTED", "Issuer not trusted: " + issuer);
        }
        
        // 2. Verify credential signature (VC JWT)
        // This uses the same pattern: parse VC JWT, resolve issuer's key, verify signature
        
        // 3. Check dates (issuance, expiration)
        if (vc.getExpirationDate() != null && vc.getExpirationDate().isBefore(now)) {
            report.addError("VC_EXPIRED", "Credential expired");
        }
        
        // 4. Check revocation status
        if (revocationService.isRevoked(vc)) {
            report.addError("VC_REVOKED", "Credential revoked");
        }
        
        // 5. Validate schema
        if (schemaId != null && !schemaRegistryService.exists(schemaId)) {
            report.addError("VC_SCHEMA_NOT_FOUND", "Schema not found");
        }
    }
}
```

---

## Summary: Where is the Verification Logic?

### For VP (Verifiable Presentation) Signature Verification:

**Current Status**: Not explicitly implemented yet in the validation service.

**Should be implemented in**: `PresentationValidationServiceImpl` following the pattern from `SelfIssuedIdTokenService.validateToken()`

**The verification would**:
1. Parse JWT VP string using `SignedJWT.parse(vpJwtString)`
2. Extract holder DID from `iss` claim
3. Resolve holder's public key using `DidResolver.resolvePublicKey(holderDid, kid)`
4. Verify signature using `jwt.verify(new ECDSAVerifier(holderPublicKey))`
5. Validate JWT claims (exp, iat, jti)

### For VC (Verifiable Credential) Signature Verification:

**Location**: Should be in `PresentationValidationServiceImpl.validate()`

**The verification process**:
1. Extract VCs from VP's `verifiableCredential` array
2. For each VC JWT:
   - Parse JWT using `SignedJWT.parse(vcJwtString)`
   - Extract issuer DID from `iss` claim
   - Verify issuer is trusted: `issuerTrustService.isTrusted(credType, issuerDid)`
   - Resolve issuer's public key: `didResolver.resolvePublicKey(issuerDid, kid)`
   - Verify signature: `jwt.verify(new ECDSAVerifier(issuerPublicKey))`
   - Check credential status (dates, revocation, schema)

---

## Key Files Reference

| Component | File | Key Method |
|-----------|------|------------|
| **Verifier Client** | `DcpVerifierClient.java` | `fetchPresentations()` |
| **Holder Endpoint** | `DcpController.java` | `queryPresentations()` |
| **Presentation Creation** | `PresentationService.java` | `createPresentation()` |
| **VP JWT Signing** | `BasicVerifiablePresentationSigner.java` | `sign(vp, "jwt")` |
| **VP/VC Validation** | `PresentationValidationServiceImpl.java` | `validate()` |
| **JWT Verification Pattern** | `SelfIssuedIdTokenService.java` | `validateToken()` |
| **DID Resolution** | `DidDocumentService.java` | `resolvePublicKey()` |
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
7. **Verifier** receives response via `DcpVerifierClient`
8. **Verifier** validates using `PresentationValidationServiceImpl`:
   - Parse JWT VP and verify holder's signature (**to be implemented**)
   - Extract embedded VCs
   - Verify each VC's issuer signature
   - Check issuer trust, dates, revocation, schema
9. **Verifier** makes access decision based on `ValidationReport`

---

## Implementation Gap

**Missing**: Explicit JWT VP signature verification in `PresentationValidationServiceImpl`

**Recommendation**: Add a method like `verifyVpSignature(String vpJwt)` that:
- Parses the JWT VP
- Resolves holder's public key from DID
- Verifies the signature
- Returns the parsed VP claims

This should be called before validating the embedded credentials to ensure the VP itself is authentic and signed by the claimed holder.

