# VP Verifiable Credential Format Analysis - DCP Spec Compliance

## Your Question

You're asking whether the `verifiableCredential` field in a VP JWT can contain:
1. Just credential IDs (URIs) - **Currently implemented**
2. Full JWT representations of credentials - **What you want to know**
3. Both fields (`id` and `presentation`) in the VerifiablePresentation model

## Current Implementation

Your current VP JWT payload looks like this:

```json
{
  "iss": "did:web:localhost:8080",
  "sub": "did:web:localhost:8080",
  "vp": {
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiablePresentation"],
    "verifiableCredential": [
      "urn:uuid:f2b52fbd-7f02-43f2-af40-13562a0e0424"  // Just ID
    ],
    "profileId": "VC11_SL2021_JWT"
  },
  "iat": 1765451602,
  "jti": "urn:uuid:470a92ff-1495-41ab-b2ef-c999492c5a4d"
}
```

## DCP Specification Analysis

### ✅ **YES - You CAN Include Full JWT Credentials in VP**

According to the DCP specification:

#### 1. **Presentation Response Message Definition** (Section 5.4.2)

> **presentation**: An array of Verifiable Presentations. The Verifiable Presentations may be **strings, JSON objects, or a combination of both** depending on the format.

#### 2. **Example 5 in DCP Spec** - Presentation Submission with Nested Credentials

```json
{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "PresentationResponseMessage",
  "presentation": ["jwtPresentation"],
  "presentationSubmission": {
    "id": "Presentation example 2",
    "definition_id": "Example with multiple VPs",
    "descriptor_map": [
      {
        "id": "id_credential",
        "format": "jwt_vp",
        "path": "$.presentation[0]",
        "path_nested": {
          "id": "id_nested_credential",
          "format": "jwt_vc",
          "path": "$.vp.verifiableCredential[0]"  // ← JWT VC path
        }
      }
    ]
  }
}
```

**Key Observation**: The `path_nested` with `"format": "jwt_vc"` and `"path": "$.vp.verifiableCredential[0]"` **proves** that the spec expects full JWT VCs to be embedded in the `verifiableCredential` array, not just IDs.

#### 3. **W3C VC Data Model Reference**

Per the DCP spec comment in `BasicVerifiablePresentationSigner.java`:

```java
/**
 * - verifiableCredential: array of credential references (URIs or embedded VCs)
 */
```

This aligns with W3C VC Data Model 1.1 and 2.0, which allow:
- **Credential IDs (URIs)**: `["urn:uuid:abc-123"]`
- **Embedded Credentials (full VCs)**: `["eyJhbGc...JWT_VC...", {...JSON-LD VC...}]`
- **Mixed**: `["urn:uuid:abc", "eyJhbGc...JWT..."]`

### ✅ **Presentation Validation Requirements** (Section 5.4.3)

The spec states:

> The Verifier MUST validate the signature of the **Verifiable Credential** by using the key obtained from the resolved VerificationMethod of the Verifiable Credential.

**This implies the VC must be present** (not just referenced by ID) for the verifier to validate its signature. Otherwise, the verifier would need an out-of-band mechanism to fetch credentials by ID, which is not specified.

## What Formats Are Allowed?

### For `verifiableCredential` Array:

According to W3C VC Data Model and DCP spec:

| Format | Example | DCP Compliant? |
|--------|---------|----------------|
| **Credential ID (URI)** | `"urn:uuid:f2b52fbd-..."` | ✅ Yes (but verifier needs to fetch) |
| **JWT VC (compact)** | `"eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9..."` | ✅ **YES** |
| **JSON-LD VC (object)** | `{"@context": [...], "id": "...", "type": [...], "credentialSubject": {...}, "proof": {...}}` | ✅ Yes |
| **Mixed** | `["urn:uuid:abc", "eyJhbGc...", {...}]` | ✅ Yes (but must be homogeneous per profile) |

### Homogeneity Requirement (Section A.1.1)

> Verifiable Credentials MUST be homogenous. This means the **same data model version and proof mechanism** MUST be used for both credentials and presentations.

**For your case (VC11_SL2021_JWT profile)**:
- VP JWT can contain **multiple JWT VCs** in `verifiableCredential`
- All VCs must use JWT format (not JSON-LD)
- All VCs must use VC Data Model 1.1 + StatusList2021

## What Should You Change?

### Current Implementation Gap

Your `BasicVerifiablePresentationSigner` currently does:

```java
vpClaim.putPOJO("verifiableCredential", vp.getCredentialIds());
// Only puts IDs like ["urn:uuid:abc-123"]
```

### Recommended Enhancement

You should support **both**:

1. **Credential IDs** - for lightweight presentations (verifier fetches VCs separately)
2. **Full JWT VCs** - for self-contained presentations (verifier validates immediately)

### Implementation Options

#### Option 1: Add a `credentials` field to VerifiablePresentation model

```java
public class VerifiablePresentation {
    private List<String> credentialIds;  // Existing - IDs/URIs
    private List<Object> credentials;     // NEW - Full VCs (JWT strings or JSON objects)
}
```

Then in `buildVPClaim()`:

```java
private Object buildVPClaim(VerifiablePresentation vp) {
    ObjectNode vpClaim = mapper.createObjectNode();
    vpClaim.putArray("@context").add("https://www.w3.org/2018/credentials/v1");
    vpClaim.putArray("type").add("VerifiablePresentation");
    
    // Priority: use full credentials if available, otherwise use IDs
    if (vp.getCredentials() != null && !vp.getCredentials().isEmpty()) {
        vpClaim.putPOJO("verifiableCredential", vp.getCredentials());
    } else {
        vpClaim.putPOJO("verifiableCredential", vp.getCredentialIds());
    }
    
    if (vp.getProfileId() != null) {
        vpClaim.put("profileId", vp.getProfileId());
    }
    
    return mapper.convertValue(vpClaim, Object.class);
}
```

#### Option 2: Change PresentationService to embed full VCs

In `PresentationService.createPresentation()`:

```java
// Instead of just storing credential IDs
List<String> credentialIds = groupCreds.stream()
    .map(VerifiableCredential::getId)
    .collect(Collectors.toList());

// Store full credential JWTs
List<Object> fullCredentials = groupCreds.stream()
    .map(vc -> {
        // If VC has a JWT representation, use it
        if (vc.getJwtRepresentation() != null) {
            return vc.getJwtRepresentation();
        }
        // Otherwise, create JWT from VC
        return credentialSigner.sign(vc, "jwt");
    })
    .collect(Collectors.toList());

VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
    .holderDid(groupCreds.get(0).getHolderDid())
    .credentialIds(credentialIds)      // Keep IDs for reference
    .credentials(fullCredentials)       // Add full VCs
    .profileId(profile)
    .build();
```

## Can Both Fields (`id` and `presentation`) Be Present?

### In VerifiablePresentation Model

Looking at your current model:

```java
public class VerifiablePresentation {
    private String id;                    // VP identifier
    private List<String> credentialIds;   // Credential IDs
    private JsonNode presentation;        // Raw presentation payload
    private JsonNode proof;               // Proof/signature
}
```

**YES**, both fields can and should be present:

1. **`id`**: Unique identifier for the VP itself (e.g., `"urn:uuid:470a92ff-..."`)
2. **`presentation`**: The actual credential data (when format is JSON-LD)

For JWT format VPs:
- `id` → becomes `jti` claim in JWT
- `presentation` → stored in `vp` claim (which contains `verifiableCredential` array)
- `proof` → the JWT signature itself

For JSON-LD format VPs:
- `id` → `"id"` field in VP JSON
- `presentation` → the `verifiableCredential` array
- `proof` → separate `"proof"` object with JsonWebSignature2020

## Recommendation

### ✅ **You SHOULD include full JWT VCs in the VP**

**Reasons**:
1. **DCP Spec Example** shows `path_nested` pointing to embedded VCs
2. **Validation Requirements** expect verifier to validate VC signatures immediately
3. **Better Security** - verifier validates full chain without extra round-trips
4. **Performance** - no need to fetch credentials separately
5. **Compliance** - aligns with W3C VC Data Model best practices

### Updated VP JWT Example

```json
{
  "iss": "did:web:localhost:8080",
  "sub": "did:web:localhost:8080",
  "vp": {
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiablePresentation"],
    "verifiableCredential": [
      "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJkaWQ6d2ViOmlzc3Vlci5leGFtcGxlIiwic3ViIjoiZGlkOndlYjpsb2NhbGhvc3Q6ODA4MCIsInZjIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIl0sInR5cGUiOlsiVmVyaWZpYWJsZUNyZWRlbnRpYWwiLCJNZW1iZXJzaGlwQ3JlZGVudGlhbCJdLCJjcmVkZW50aWFsU3ViamVjdCI6eyJpZCI6ImRpZDp3ZWI6bG9jYWxob3N0OjgwODAiLCJtZW1iZXJMZXZlbCI6IkdvbGQifX0sImlhdCI6MTcwMjMxMDQwMCwiZXhwIjoxNzMzODQ2NDAwLCJqdGkiOiJ1cm46dXVpZDpmMmI1MmZiZC03ZjAyLTQzZjItYWY0MC0xMzU2MmEwZTA0MjQifQ.signature..."
    ],
    "profileId": "VC11_SL2021_JWT"
  },
  "iat": 1765451602,
  "jti": "urn:uuid:470a92ff-1495-41ab-b2ef-c999492c5a4d"
}
```

## Next Steps

1. ✅ Update `VerifiablePresentation` model to support both `credentialIds` and `credentials`
2. ✅ Modify `BasicVerifiablePresentationSigner.buildVPClaim()` to embed full VCs
3. ✅ Update `PresentationService` to fetch full VC objects (not just IDs)
4. ✅ Ensure `VerifiableCredential` model can store JWT representation
5. ✅ Update `PresentationValidationServiceImpl` to parse and verify embedded JWT VCs

Would you like me to implement these changes?

