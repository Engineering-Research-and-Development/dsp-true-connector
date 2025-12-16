# Practical Example: VP with Embedded JWT VCs

## Complete Flow Example

### Step 1: Issuer Creates and Signs a Credential (JWT Format)

**Issuer creates a JWT VC:**

```java
// Issuer side
JWTClaimsSet vcClaims = new JWTClaimsSet.Builder()
    .issuer("did:web:issuer.example")
    .subject("did:web:localhost:8080")  // Holder DID
    .claim("vc", Map.of(
        "@context", List.of("https://www.w3.org/2018/credentials/v1"),
        "type", List.of("VerifiableCredential", "MembershipCredential"),
        "credentialSubject", Map.of(
            "id", "did:web:localhost:8080",
            "memberLevel", "Gold",
            "organization", "Dataspace Consortium"
        )
    ))
    .issueTime(new Date())
    .expirationTime(new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000))
    .jwtID("urn:uuid:f2b52fbd-7f02-43f2-af40-13562a0e0424")
    .build();

SignedJWT vcJwt = new SignedJWT(header, vcClaims);
vcJwt.sign(issuerSigner);

String vcJwtString = vcJwt.serialize();
// "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Imlzc3Vlci1rZXktMSJ9.eyJpc3MiOiJkaWQ..."
```

**Decoded VC JWT:**
```json
// Header
{
  "alg": "ES256",
  "typ": "JWT",
  "kid": "issuer-key-1"
}

// Payload
{
  "iss": "did:web:issuer.example",
  "sub": "did:web:localhost:8080",
  "vc": {
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiableCredential", "MembershipCredential"],
    "credentialSubject": {
      "id": "did:web:localhost:8080",
      "memberLevel": "Gold",
      "organization": "Dataspace Consortium"
    }
  },
  "iat": 1702310400,
  "exp": 1733846400,
  "jti": "urn:uuid:f2b52fbd-7f02-43f2-af40-13562a0e0424"
}

// Signature
<ECDSA signature by issuer>
```

### Step 2: Holder Stores the Credential

**Holder receives and stores the credential:**

```java
// Holder side - CredentialDeliveryService or HolderService
VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
    .id("urn:uuid:f2b52fbd-7f02-43f2-af40-13562a0e0424")
    .holderDid("did:web:localhost:8080")
    .credentialType("MembershipCredential")
    .profileId("VC11_SL2021_JWT")
    .issuerDid("did:web:issuer.example")
    .issuanceDate(Instant.ofEpochSecond(1702310400))
    .expirationDate(Instant.ofEpochSecond(1733846400))
    .credential(extractVcClaim(vcJwtString))  // Parsed JSON from vc claim
    .jwtRepresentation(vcJwtString)           // ✅ Store original JWT!
    .build();

credentialRepository.save(vc);
```

### Step 3: Verifier Requests Presentation

**Verifier sends PresentationQueryMessage:**

```json
POST https://holder.example/dcp/presentations/query
Authorization: Bearer <verifier-access-token>

{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "@type": "PresentationQueryMessage",
  "scope": ["MembershipCredential"]
}
```

### Step 4: Holder Creates VP with Embedded VC

**PresentationService creates VP:**

```java
// Fetch credentials
List<VerifiableCredential> fetched = credentialRepository.findByCredentialTypeIn(["MembershipCredential"]);
// Returns: [vc with jwtRepresentation="eyJhbGc..."]

// Group by profile
Map<String, List<VerifiableCredential>> groups = ...

// For each group, create VP with embedded VCs
List<Object> fullCredentials = groupCreds.stream()
    .map(vc -> vc.getJwtRepresentation())  // ✅ Use JWT representation
    .collect(Collectors.toList());
// Result: ["eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Imlzc3Vlci1rZXktMSJ9.eyJpc3M..."]

VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
    .holderDid("did:web:localhost:8080")
    .credentialIds(["urn:uuid:f2b52fbd-7f02-43f2-af40-13562a0e0424"])
    .credentials(fullCredentials)  // ✅ Embed full JWT VCs
    .profileId("VC11_SL2021_JWT")
    .build();
```

**BasicVerifiablePresentationSigner creates VP JWT:**

```java
// Build VP claim with embedded VCs
JWTClaimsSet vpClaims = new JWTClaimsSet.Builder()
    .issuer("did:web:localhost:8080")      // Holder DID
    .subject("did:web:localhost:8080")     // Holder DID
    .claim("vp", Map.of(
        "@context", List.of("https://www.w3.org/2018/credentials/v1"),
        "type", List.of("VerifiablePresentation"),
        "verifiableCredential", [
            "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Imlzc3Vlci1rZXktMSJ9.eyJpc3M..."
            // ✅ Full JWT VC embedded!
        ],
        "profileId", "VC11_SL2021_JWT"
    ))
    .issueTime(new Date())
    .jwtID("urn:uuid:470a92ff-1495-41ab-b2ef-c999492c5a4d")
    .build();

SignedJWT vpJwt = new SignedJWT(holderHeader, vpClaims);
vpJwt.sign(holderSigner);

String vpJwtString = vpJwt.serialize();
```

**Resulting VP JWT (compact format):**

```
eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImhvbGRlci1rZXktMSJ9.eyJpc3MiOiJkaWQ6d2ViOmxvY2FsaG9zdDo4MDgwIiwic3ViIjoiZGlkOndlYjpsb2NhbGhvc3Q6ODA4MCIsInZwIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIl0sInR5cGUiOlsiVmVyaWZpYWJsZVByZXNlbnRhdGlvbiJdLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6WyJleUpoYkdjaU9pSkZVekkxTmlJc0luUjVjQ0k2SWtwWFZDSXNJbXRwWkNJNkltbHpjM1ZsY2kxclpYa3RNU0o5LmV5SnBjM01pT2lKa2FXUTZkMlZpT21semMzVmxjaTVsZUdGdGNHeGxJaXdpYzNWaUlqb2laR2xrT25kbFlqcHNiMk5oYkdodmMzUTZPREE0TUNJc0luWmpJanA3SWtCamIyNTBaWGgwSWpwYkltaDBkSEJ6T2k4dmQzZDNMbmN6TG05eVp5OHlNREU0TDJOeVpXUmxiblJwWVd4ekwzWXhJbDBzSW5SNWNHVWlPbHNpVm1WeWFXWnBZV0pzWlVOeVpXUmxiblJwWVd3aUxDSk5aVzFpWlhKemFHbHdRM0psWkdWdWRHbGhiQ0pkTENKamNtVmtaVzUwYVdGc1UzVmlhbVZqZENJNmV5SnBaQ0k2SW1ScFpEcDNaV0k2Ykc5allXeG9iM04wT2pnd09EQWlMQ0p0WlcxaVpYSklaWFpsYkNJNklrZHZiR1FpTENKdmNtZGhibWw2WVhScGIyNGlPaUpFWVhSaGMzQmhZMlVnUTI5dWMyOXlkR2wxYlNKOWZTd2lhV0YwSWpveE56QXlNekV3TkRBd0xDSmxlSEFpT2pFM016TTRORGWDBNQ3tLInFhc1psc2lPblZ6dHh1enpnaDVnZno0alZVOTA1ZZWlhNndlZG9hMFpaV0JrWVN2elBlSmxOV1Q1emVtSWlMQ0pxZEdraU9pSjFjbTQ2ZFhWcFpEcG1NbUkxTW1aaVpDMDNaakF5TFRRelpqSXRZV1kwTUMweE16VTJNbUV3WlRBME1qUWlmUS5zaWduYXR1cmUuLi4iXSwicHJvZmlsZUlkIjoiVkMxMV9TTDIwMjFfSldUIn0sImlhdCI6MTc2NTQ1MTYwMiwianRpIjoidXJuOnV1aWQ6NDcwYTkyZmYtMTQ5NS00MWFiLWIyZWYtYzk5OTQ5MmM1YTRkIn0.signature-by-holder...
```

**Decoded VP JWT:**

```json
// Header
{
  "alg": "ES256",
  "typ": "JWT",
  "kid": "holder-key-1"
}

// Payload
{
  "iss": "did:web:localhost:8080",
  "sub": "did:web:localhost:8080",
  "vp": {
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiablePresentation"],
    "verifiableCredential": [
      "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Imlzc3Vlci1rZXktMSJ9.eyJpc3MiOiJkaWQ6d2ViOmlzc3Vlci5leGFtcGxlIiwic3ViIjoiZGlkOndlYjpsb2NhbGhvc3Q6ODA4MCIsInZjIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIl0sInR5cGUiOlsiVmVyaWZpYWJsZUNyZWRlbnRpYWwiLCJNZW1iZXJzaGlwQ3JlZGVudGlhbCJdLCJjcmVkZW50aWFsU3ViamVjdCI6eyJpZCI6ImRpZDp3ZWI6bG9jYWxob3N0OjgwODAiLCJtZW1iZXJMZXZlbCI6IkdvbGQiLCJvcmdhbml6YXRpb24iOiJEYXRhc3BhY2UgQ29uc29ydGl1bSJ9fSwiaWF0IjoxNzAyMzEwNDAwLCJleHAiOjE3MzM4NDY0MDAsImp0aSI6InVybjp1dWlkOmYyYjUyZmJkLTdmMDItNDNmMi1hZjQwLTEzNTYyYTBlMDQyNCJ9.signature-by-issuer..."
      // ✅ Full JWT VC embedded! (not just ID)
    ],
    "profileId": "VC11_SL2021_JWT"
  },
  "iat": 1765451602,
  "jti": "urn:uuid:470a92ff-1495-41ab-b2ef-c999492c5a4d"
}

// Signature
<ECDSA signature by holder>
```

### Step 5: Holder Returns PresentationResponseMessage

```json
HTTP/1.1 200 OK
Content-Type: application/json

{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "@type": "PresentationResponseMessage",
  "presentation": [
    "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImhvbGRlci1rZXktMSJ9.eyJpc3MiOiJkaWQ6d2ViOmxvY2FsaG9zdDo4MDgwIiwic3ViIjoiZGlkOndlYjpsb2NhbGhvc3Q6ODA4MCIsInZwIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIl0sInR5cGUiOlsiVmVyaWZpYWJsZVByZXNlbnRhdGlvbiJdLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6WyJleUpoYkdjaU9pSkZVekkxTmlJc0luUjVjQ0k2SWtwWFZDSXNJbXRwWkNJNkltbHpjM1ZsY2kxclpYa3RNU0o5LmV5SnBjM01pT2lKa2FXUTZkMlZpT21semMzVmxjaTVsZUdGdGNHeGxJaXdpYzNWaUlqb2laR2xrT25kbFlqcHNiMk5oYkdodmMzUTZPREE0TUNJc0luWmpJanA3SWtCamIyNTBaWGgwSWpwYkltaDBkSEJ6T2k4dmQzZDNMbmN6TG05eVp5OHlNREU0TDJOeVpXUmxiblJwWVd4ekwzWXhJbDBzSW5SNWNHVWlPbHNpVm1WeWFXWnBZV0pzWlVOeVpXUmxiblJwWVd3aUxDSk5aVzFpWlhKemFHbHdRM0psWkdWdWRHbGhiQ0pkTENKamNtVmtaVzUwYVdGc1UzVmlhbVZqZENJNmV5SnBaQ0k2SW1ScFpEcDNaV0k2Ykc5allXeG9iM04wT2pnd09EQWlMQ0p0WlcxaVpYSklaWFpsYkNJNklrZHZiR1FpTENKdmNtZGhibWw2WVhScGIyNGlPaUpFWVhSaGMzQmhZMlVnUTI5dWMyOXlkR2wxYlNKOWZTd2lhV0YwSWpveE56QXlNekV3TkRBd0xDSmxlSEFpT2pFM016TTRORGWMNEE3tLInFhc1psc2lPblZ6dHh1enpnaDVnZno0alZVOTA1ZZWlhNmdlZG9hMFpaV0JrWVN2elBlSmxOV1Q1emVtSWlMQ0pxZEdraU9pSjFjbTQ2ZFhWcFpEcG1NbUkxTW1aaVpDMDNaakF5TFRRelpqSXRZV1kwTUMweE16VTJNbUV3WlRBME1qUWlmUS5zaWduYXR1cmUtYnktaXNzdWVyLi4uIl0sInByb2ZpbGVJZCI6IlZDMTFfU0wyMDIxX0pXVCJ9LCJpYXQiOjE3NjU0NTE2MDIsImp0aSI6InVybjp1dWlkOjQ3MGE5MmZmLTE0OTUtNDFhYi1iMmVmLWM5OTk0OTJjNWE0ZCJ9.signature-by-holder..."
  ]
}
```

### Step 6: Verifier Validates the Presentation

**Verifier validation steps:**

```java
// 1. Parse VP JWT
SignedJWT vpJwt = SignedJWT.parse(vpJwtString);

// 2. Verify VP signature using holder's public key
String holderDid = vpJwt.getJWTClaimsSet().getIssuer(); // "did:web:localhost:8080"
String kid = vpJwt.getHeader().getKeyID(); // "holder-key-1"
JWK holderKey = didResolver.resolvePublicKey(holderDid, kid);
ECDSAVerifier vpVerifier = new ECDSAVerifier((ECKey) holderKey);
boolean vpValid = vpJwt.verify(vpVerifier); // ✅ Verifies holder signed the VP

// 3. Extract embedded VCs from vp.verifiableCredential
Object vpClaim = vpJwt.getJWTClaimsSet().getClaim("vp");
List<String> vcJwts = (List<String>) ((Map) vpClaim).get("verifiableCredential");

// 4. For each embedded VC, verify issuer signature
for (String vcJwtString : vcJwts) {
    SignedJWT vcJwt = SignedJWT.parse(vcJwtString);
    
    // Extract issuer DID
    String issuerDid = vcJwt.getJWTClaimsSet().getIssuer(); // "did:web:issuer.example"
    String vcKid = vcJwt.getHeader().getKeyID(); // "issuer-key-1"
    
    // Resolve issuer's public key
    JWK issuerKey = didResolver.resolvePublicKey(issuerDid, vcKid);
    ECDSAVerifier vcVerifier = new ECDSAVerifier((ECKey) issuerKey);
    
    // Verify VC signature
    boolean vcValid = vcJwt.verify(vcVerifier); // ✅ Verifies issuer signed the VC
    
    // Extract VC claims
    Object vcClaim = vcJwt.getJWTClaimsSet().getClaim("vc");
    String credType = extractType(vcClaim); // "MembershipCredential"
    
    // Check issuer trust
    boolean trusted = issuerTrustService.isTrusted(credType, issuerDid);
    
    // Check dates
    Date exp = vcJwt.getJWTClaimsSet().getExpirationTime();
    boolean notExpired = exp.after(new Date());
    
    // Check revocation
    boolean notRevoked = !revocationService.isRevoked(vcClaim);
    
    // All checks pass → Accept credential
}
```

## Key Differences: Before vs After

### Before (ID-only)

```json
{
  "vp": {
    "verifiableCredential": [
      "urn:uuid:f2b52fbd-7f02-43f2-af40-13562a0e0424"  // Just ID
    ]
  }
}
```

**Problem**: Verifier must fetch the VC separately
- Extra HTTP request to holder's credential service
- Requires additional authentication
- Adds latency
- Not shown in DCP spec examples

### After (Embedded JWT VC) ✅

```json
{
  "vp": {
    "verifiableCredential": [
      "eyJhbGc...FULL_JWT_VC..."  // Complete JWT VC
    ]
  }
}
```

**Benefits**:
- ✅ Self-contained - verifier has everything needed
- ✅ Immediate validation - no additional fetches
- ✅ DCP spec compliant - matches Example 5
- ✅ Secure - verifier validates both holder and issuer signatures
- ✅ Efficient - single round-trip

## Verification Chain

```
1. Verifier → Parse VP JWT
2. Verifier → Resolve holder's DID → Get public key
3. Verifier → Verify VP signature (holder signed it)
   ✅ Proves: Holder created this presentation
   
4. Verifier → Extract embedded VC JWTs from vp.verifiableCredential
5. Verifier → For each VC:
   a. Parse VC JWT
   b. Resolve issuer's DID → Get public key
   c. Verify VC signature (issuer signed it)
      ✅ Proves: Issuer created this credential
   d. Check issuer is trusted for this credential type
      ✅ Proves: Issuer is authorized
   e. Check dates (not expired)
      ✅ Proves: Credential is still valid
   f. Check revocation status
      ✅ Proves: Credential not revoked
      
6. Verifier → Make access decision
   ✅ All checks passed → Grant access
```

## Summary

The implementation now supports **embedding full JWT VCs** in the VP's `verifiableCredential` array, which:

1. ✅ **Complies with DCP spec** Section 5.4.2 Example 5
2. ✅ **Enables complete signature validation** without extra fetches
3. ✅ **Follows W3C VC Data Model** best practices
4. ✅ **Maintains backward compatibility** with ID-only VPs
5. ✅ **Improves security and performance** for the entire verification flow

The verifier can now validate the entire trust chain (holder → VP → VC → issuer) in a single operation!

