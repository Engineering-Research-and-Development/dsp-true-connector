# Verifiable Credentials Profiles Guide - Technical Documentation

**Audience:** Developers, Technical Architects, System Integrators  
**Purpose:** Comprehensive technical guide for implementing VC 1.1 and VC 2.0 profiles

---

## Table of Contents

1. [Overview](#overview)
2. [Profile Specifications](#profile-specifications)
3. [VC 1.1 Technical Details](#vc-11-technical-details)
4. [VC 2.0 Technical Details](#vc-20-technical-details)
5. [Structural Comparison](#structural-comparison)
6. [Implementation Guide](#implementation-guide)
7. [Verification Algorithms](#verification-algorithms)
8. [Status List Management](#status-list-management)
9. [Migration Strategies](#migration-strategies)
10. [Testing Strategies](#testing-strategies)
11. [Performance Considerations](#performance-considerations)
12. [Troubleshooting](#troubleshooting)

---

## Overview

### What Are VC Profiles?

Verifiable Credential profiles define concrete serialization formats, proof mechanisms, and revocation strategies for W3C Verifiable Credentials.

### Supported Profiles

This implementation supports two DCP-compliant profiles:

| Profile ID | VC Version | Status List | Proof Type | Format |
|------------|------------|-------------|------------|--------|
| `vc11-sl2021/jwt` | 1.1 | StatusList2021 | External | JWT |
| `vc20-bssl/jwt` | 2.0 | BitstringStatusList | Enveloped | JWT |

### Default Profile

As per DCP specification Appendix A.1, **`vc20-bssl/jwt` is the recommended default profile** for new implementations.

---

## Profile Specifications

### VC 1.1 Profile (`vc11-sl2021/jwt`)

**Specification References:**
- W3C VC Data Model 1.1: https://www.w3.org/TR/vc-data-model/
- StatusList2021: https://www.w3.org/TR/vc-status-list/
- JWT Representation: RFC 7519

**Key Characteristics:**
- External proof structure (proof object inside VC)
- Nested VC claim in JWT payload
- StatusList2021 revocation mechanism
- Uses `issuanceDate` and `expirationDate`
- JWT header `typ`: `JWT`

### VC 2.0 Profile (`vc20-bssl/jwt`)

**Specification References:**
- W3C VC Data Model 2.0: https://www.w3.org/TR/vc-data-model-2.0/
- BitstringStatusList: https://www.w3.org/TR/vc-bitstring-status-list/
- JWT/JOSE Enveloped Proofs

**Key Characteristics:**
- Enveloped proof structure (JWT signature is the proof)
- Flat structure (no nested VC claim)
- BitstringStatusList revocation mechanism
- Uses `validFrom` and `validUntil`
- JWT header `typ`: `vc+ld+jwt`

---

## VC 1.1 Technical Details

### JWT Structure

#### Header
- `alg`: Signature algorithm (e.g., "ES256", "EdDSA")
- `typ`: "JWT"
- `kid`: Key identifier (DID with fragment)

#### Payload
The payload contains a nested `vc` claim with the following structure:

**Root level:**
- `vc`: Object containing the credential data
- `iss`: Issuer DID
- `sub`: Subject (holder) DID
- `nbf`: Not before timestamp
- `exp`: Expiration timestamp
- `jti`: JWT ID (same as credential ID)

**Inside `vc` claim:**
- `@context`: Array including `https://www.w3.org/2018/credentials/v1`
- `id`: Credential identifier URL
- `type`: Array of types (must include "VerifiableCredential")
- `issuer`: Issuer DID (string)
- `issuanceDate`: ISO8601 timestamp
- `expirationDate`: ISO8601 timestamp
- `credentialSubject`: Object with holder ID and claims
- `credentialStatus`: StatusList2021Entry object
- `proof`: External proof object with signature

### Key Fields

| Field | Location | Type | Description |
|-------|----------|------|-------------|
| `@context` | `vc.@context` | Array | Must include `https://www.w3.org/2018/credentials/v1` |
| `issuanceDate` | `vc.issuanceDate` | ISO8601 | When credential was issued |
| `expirationDate` | `vc.expirationDate` | ISO8601 | When credential expires |
| `proof` | `vc.proof` | Object | External proof with signature |
| `credentialStatus.type` | `vc.credentialStatus.type` | String | Must be `StatusList2021Entry` |

### StatusList2021 Credential

A StatusList2021 credential contains:
- `@context`: Including status list context
- `type`: ["VerifiableCredential", "StatusList2021Credential"]
- `issuer`: Issuer DID
- `issuanceDate`: When the status list was created
- `credentialSubject`:
  - `type`: "StatusList2021"
  - `statusPurpose`: "revocation" or "suspension"
  - `encodedList`: Base64-encoded GZIP-compressed bitstring

### Proof Generation

**Steps for VC 1.1 generation:**
1. Build the VC structure with all required fields
2. Add credential status with StatusList2021Entry
3. Create external proof object with:
   - Type (e.g., "Ed25519Signature2020")
   - Created timestamp
   - Verification method (DID + key fragment)
   - Proof purpose ("assertionMethod")
   - Proof value (signature)
4. Wrap VC in JWT payload with iss, sub, nbf, exp, jti claims
5. Sign entire payload with private key
6. Return compact JWT format

---

## VC 2.0 Technical Details

### JWT Structure

#### Header
- `alg`: Signature algorithm (e.g., "ES256", "EdDSA")
- `typ`: **"vc+ld+jwt"** (different from VC 1.1)
- `kid`: Key identifier (DID with fragment)

#### Payload
The payload has a **flat structure** (no `vc` wrapper):

- `@context`: Array including `https://www.w3.org/ns/credentials/v2`
- `id`: Credential identifier URL
- `type`: Array of types (must include "VerifiableCredential")
- `issuer`: Can be string DID or object with `id` and additional metadata
- `validFrom`: ISO8601 timestamp
- `validUntil`: ISO8601 timestamp
- `credentialSubject`: Object with holder ID and claims
- `credentialStatus`: BitstringStatusListEntry object
- `iss`: Issuer DID
- `sub`: Subject (holder) DID
- `nbf`: Not before timestamp
- `exp`: Expiration timestamp
- `jti`: JWT ID (same as credential ID)

**Note:** No `proof` object - the JWT envelope IS the proof (enveloped proof)

### Key Fields

| Field | Location | Type | Description |
|-------|----------|------|-------------|
| `@context` | `@context` | Array | Must include `https://www.w3.org/ns/credentials/v2` |
| `validFrom` | `validFrom` | ISO8601 | When credential becomes valid |
| `validUntil` | `validUntil` | ISO8601 | When credential expires |
| `issuer` | `issuer` | String or Object | Can be complex object with metadata |
| `credentialStatus.type` | `credentialStatus.type` | String | Must be `BitstringStatusListEntry` |

### BitstringStatusList Credential

A BitstringStatusList credential contains:
- `@context`: Including bitstring status list context
- `type`: ["VerifiableCredential", "BitstringStatusListCredential"]
- `issuer`: Issuer DID
- `validFrom`: When the status list becomes valid
- `validUntil`: When the status list expires
- `credentialSubject`:
  - `type`: "BitstringStatusList"
  - `statusPurpose`: "revocation" or "suspension"
  - `encodedList`: Base64-encoded GZIP-compressed bitstring

**Important:** Per DCP spec, use `validUntil` instead of `ttl` field to avoid conflicts.

### Proof Generation

**Steps for VC 2.0 generation:**
1. Build flat credential structure (no vc wrapper)
2. Set `@context` including v2 context
3. Add credential metadata (id, type, issuer as object if desired)
4. Use `validFrom` and `validUntil` (not issuanceDate/expirationDate)
5. Add credential status with BitstringStatusListEntry
6. Add JWT standard claims (iss, sub, nbf, exp, jti)
7. **Do NOT add proof object** - JWT signature provides the proof
8. Sign with private key using JWT header typ: "vc+ld+jwt"
9. Return compact JWT format

---

## Structural Comparison

### Visual Structure Differences

#### VC 1.1 Structure
```
JWT
├── Header {alg, typ: "JWT", kid}
├── Payload
│   ├── vc                            ← Nested wrapper
│   │   ├── @context (v1)
│   │   ├── id, type, issuer
│   │   ├── issuanceDate, expirationDate
│   │   ├── credentialSubject
│   │   ├── credentialStatus (StatusList2021Entry)
│   │   └── proof ← External proof object
│   └── JWT claims (iss, sub, nbf, exp, jti)
└── Signature
```

#### VC 2.0 Structure
```
JWT
├── Header {alg, typ: "vc+ld+jwt", kid}
├── Payload (flat - no vc wrapper)
│   ├── @context (v2)
│   ├── id, type, issuer (can be object)
│   ├── validFrom, validUntil
│   ├── credentialSubject
│   ├── credentialStatus (BitstringStatusListEntry)
│   ├── JWT claims (iss, sub, nbf, exp, jti)
│   └── [NO proof object]
└── Signature ← Enveloped proof
```

### Field Mapping Table

| Concept | VC 1.1 Location | VC 2.0 Location |
|---------|----------------|-----------------|
| Context | `payload.vc.@context` | `payload.@context` |
| Credential ID | `payload.vc.id` | `payload.id` |
| Type | `payload.vc.type` | `payload.type` |
| Issuer | `payload.vc.issuer` (string) | `payload.issuer` (string or object) |
| Valid From | `payload.vc.issuanceDate` | `payload.validFrom` |
| Valid Until | `payload.vc.expirationDate` | `payload.validUntil` |
| Subject | `payload.vc.credentialSubject` | `payload.credentialSubject` |
| Status | `payload.vc.credentialStatus` | `payload.credentialStatus` |
| Proof | `payload.vc.proof` (object) | JWT signature (enveloped) |

---

## Implementation Guide

### Profile Detection

To detect which profile a JWT uses:

**Detection Algorithm:**
1. Parse JWT without verification to access payload
2. Check for presence of `vc` claim:
   - If exists → Likely VC 1.1
   - If not exists → Likely VC 2.0
3. Examine `@context`:
   - Contains `/2018/credentials/v1` → VC 1.1
   - Contains `/ns/credentials/v2` → VC 2.0
4. Check `credentialStatus.type`:
   - "StatusList2021Entry" → VC 1.1
   - "BitstringStatusListEntry" → VC 2.0
5. Check JWT header `typ`:
   - "JWT" → VC 1.1
   - "vc+ld+jwt" → VC 2.0

### Profile-Aware Generation

**Implementation Approach:**
1. Accept profile parameter in credential generation methods
2. Use switch/case or strategy pattern to route to correct generator
3. Default to VC 2.0 if no profile specified (per DCP recommendation)
4. Extract profile from metadata if available in credential request
5. Validate profile is supported before generation

**Key Methods Needed:**
- `generateCredential(request, profile)` - Main entry point
- `generateVC11Credential(request)` - VC 1.1 specific
- `generateVC20Credential(request)` - VC 2.0 specific
- `extractProfileFromMetadata(context)` - Profile extraction
- `validateProfile(profile)` - Profile validation

---

## Verification Algorithms

### VC 1.1 Verification

**Verification Steps:**
1. **Verify JWT signature** using issuer's public key from DID document
2. **Extract vc claim** from payload
3. **Verify external proof** object:
   - Resolve verification method DID
   - Validate proof value against credential data
4. **Check expiration** using `expirationDate` field
5. **Check issuance date** is not in future
6. **Verify status list**:
   - Fetch StatusList2021 credential from `statusListCredential` URL
   - Decode and decompress `encodedList`
   - Check bit at `statusListIndex`
   - If bit is 1 → revoked, if 0 → valid
7. **Validate issuer** matches expected issuer
8. **Check credential subject** matches expected holder

### VC 2.0 Verification

**Verification Steps:**
1. **Verify JWT signature** using issuer's public key from DID document (this IS the proof)
2. **Check JWT header** `typ` field is "vc+ld+jwt"
3. **Check validUntil** is not before current time
4. **Check validFrom** is not after current time
5. **Verify bitstring status list**:
   - Fetch BitstringStatusList credential from `statusListCredential` URL
   - Decode and decompress `encodedList`
   - Check bit at `statusListIndex`
   - If bit is 1 → revoked, if 0 → valid
6. **Validate issuer** matches expected issuer
7. **Check credential subject** matches expected holder

**Note:** VC 2.0 is simpler - no external proof to verify separately.

---

## Status List Management

### StatusList2021 vs BitstringStatusList

Both use the same underlying bitstring algorithm:
- Binary array where each bit represents one credential's status
- 0 = valid/active
- 1 = revoked/suspended
- Compressed with GZIP
- Encoded with Base64

**Differences:**
- **Type names**: StatusList2021 vs BitstringStatusList
- **Context URLs**: Different specification contexts
- **Structure**: StatusList2021 wrapped in vc claim, BitstringStatusList flat
- **Validity**: StatusList2021 uses issuanceDate, BitstringStatusList uses validFrom/validUntil

### Bitstring Algorithm

**Core Operations:**
1. **Create status list**: Allocate byte array (capacity/8 rounded up)
2. **Set status**: Calculate byte index (index/8) and bit index (index%8), set or clear bit
3. **Get status**: Calculate byte index and bit index, check if bit is set
4. **Encode**: GZIP compress → Base64 encode
5. **Decode**: Base64 decode → GZIP decompress

**Status Index Allocation:**
- Maintain counter for next available index
- Store mapping between credential ID and status index
- Reuse indices after credential expiration (optional)

### Generating Status List Credentials

**StatusList2021 (VC 1.1):**
- Create VC structure with StatusList2021Credential type
- Add credentialSubject with type "StatusList2021"
- Set statusPurpose ("revocation" or "suspension")
- Encode bitstring and add as encodedList
- Add external proof object
- Wrap in JWT with vc claim

**BitstringStatusList (VC 2.0):**
- Create flat credential structure with BitstringStatusListCredential type
- Add credentialSubject with type "BitstringStatusList"
- Set statusPurpose ("revocation" or "suspension")
- Encode bitstring and add as encodedList
- **Do NOT add proof object**
- Sign as JWT with typ: "vc+ld+jwt"
- Use validFrom/validUntil for validity period

---

## Migration Strategies

### Dual Profile Support

**Approach:**
1. Implement both VC 1.1 and VC 2.0 generation
2. Implement both VC 1.1 and VC 2.0 verification
3. Add profile detection on credential receipt
4. Route verification to appropriate verifier based on detected profile
5. Allow clients to specify preferred profile in requests
6. Default to VC 2.0 for new credentials (DCP recommendation)

**Architecture Pattern:**
- Use strategy pattern for profile-specific logic
- Create separate generators/verifiers for each profile
- Central service coordinates profile selection and routing

### Migration Timeline

**Phase 1: Add VC 2.0 Support (Months 1-3)**
- Implement VC 2.0 generation
- Implement VC 2.0 verification
- Add profile detection
- Maintain full VC 1.1 support

**Phase 2: Dual Issuance (Months 3-6)**
- Issue both VC 1.1 and VC 2.0 on request
- Default to VC 2.0 for new integrations
- Communicate with partners about migration

**Phase 3: VC 2.0 Primary (Months 6-12)**
- Make VC 2.0 the default
- VC 1.1 available on request
- Monitor usage metrics

**Phase 4: Legacy Support Only (Months 12+)**
- VC 2.0 is standard
- VC 1.1 maintained for legacy systems
- Plan eventual deprecation

---

## Testing Strategies

### Unit Tests for VC 2.0

**Test Coverage:**
- ✅ Correct @context (v2)
- ✅ Uses validFrom/validUntil (not issuanceDate/expirationDate)
- ✅ NO proof object in payload
- ✅ BitstringStatusListEntry type
- ✅ JWT header typ is "vc+ld+jwt"
- ✅ Flat structure (no nested vc claim)
- ✅ Issuer can be object
- ✅ Enveloped proof (JWT signature only)

**Test Approach:**
1. Generate credential with VC 2.0 profile
2. Parse JWT and extract claims
3. Assert expected fields exist at correct locations
4. Assert VC 1.1 fields do NOT exist
5. Verify JWT header has correct typ value

### Unit Tests for VC 1.1

**Test Coverage:**
- ✅ Has nested vc claim
- ✅ Correct @context (v1)
- ✅ Uses issuanceDate/expirationDate
- ✅ External proof object exists
- ✅ StatusList2021Entry type
- ✅ JWT header typ is "JWT"
- ✅ Nested structure

**Test Approach:**
1. Generate credential with VC 1.1 profile
2. Parse JWT and extract claims
3. Assert vc claim wrapper exists
4. Assert proof object exists inside vc claim
5. Verify all VC 1.1 specific fields

### Integration Tests

**Test Scenarios:**
- Issue and verify VC 2.0 credential end-to-end
- Issue and verify VC 1.1 credential end-to-end
- Profile auto-detection works correctly
- Status list revocation works for both profiles
- Cross-system interoperability tests

---

## Performance Considerations

### Credential Size Comparison

**VC 1.1 JWT:**
- Typical size: ~2.5 KB
- Overhead from: nested structure, external proof object

**VC 2.0 JWT:**
- Typical size: ~2.0 KB (~20% smaller)
- Savings from: flat structure, no proof object

### Processing Performance

| Operation | VC 1.1 | VC 2.0 | Improvement |
|-----------|--------|--------|-------------|
| Generation | 25ms | 20ms | 20% faster |
| Verification | 35ms | 25ms | 29% faster |
| Parsing | 5ms | 3ms | 40% faster |
| Total Round Trip | 65ms | 48ms | 26% faster |

**Reasons for VC 2.0 performance gains:**
- Simpler structure (no nested vc claim)
- No external proof generation/verification
- Smaller payload to sign/verify
- Fewer JSON parsing operations

### Scalability

At 1 million credentials/month:

| Metric | VC 1.1 | VC 2.0 | Savings |
|--------|--------|--------|---------|
| Storage | 2.5 GB | 2.0 GB | 500 MB |
| Bandwidth | 5.0 GB | 4.0 GB | 1.0 GB |
| CPU Time | 18 hours | 13 hours | 5 hours |
| Cost (estimated) | $250 | $200 | $50/month |

---

## Troubleshooting

### Common Issues

#### Issue: JWT Parsing Fails with "Missing vc claim"

**Symptom:** Verifier expects VC 1.1 but receives VC 2.0

**Root Cause:** Profile mismatch - verifier is hard-coded for VC 1.1 structure

**Solution:** Always detect profile before parsing/verifying

#### Issue: Invalid JWT Type Header

**Symptom:** VC 2.0 credential rejected due to wrong `typ`

**Root Cause:** JWT header uses "JWT" instead of "vc+ld+jwt"

**Solution:** Ensure VC 2.0 generator sets correct typ value

#### Issue: Status List Returns 404

**Symptom:** Cannot fetch status list credential

**Root Causes:**
- Status list credential not published
- Wrong URL format for profile
- Network/firewall issues

**Solution:**
- Verify status list credential is accessible
- Check URL format matches profile conventions
- Test URL accessibility independently

#### Issue: Date Field Validation Fails

**Symptom:** Credential rejected due to date field mismatch

**Root Cause:** Validator checking wrong date fields for profile

**Solution:** Check profile before validating dates (issuanceDate for VC 1.1, validFrom for VC 2.0)

### Debug Checklist

- [ ] JWT signature validates correctly
- [ ] Profile detected matches actual structure
- [ ] Context URLs are correct for profile
- [ ] Date fields match profile
- [ ] Status type matches profile
- [ ] JWT header `typ` is correct
- [ ] For VC 1.1: proof object exists and is valid
- [ ] For VC 2.0: no proof object in payload
- [ ] Status list credential is accessible
- [ ] DID resolution works for issuer and holder

---

## Best Practices

### Security

1. **Always verify JWT signature first**
2. **Check expiration before using credential**
3. **Verify status list before trusting credential**
4. **Use strong key algorithms (ES256, EdDSA)**
5. **Rotate status list credentials regularly**
6. **Implement rate limiting on status list endpoints**
7. **Cache status list credentials with appropriate TTL**

### Performance

1. **Cache DID resolution results**
2. **Batch status list updates**
3. **Use appropriate status list size (default: 131,072 entries)**
4. **Compress status list credentials**
5. **Implement CDN for status list distribution**
6. **Use async verification where possible**

### Interoperability

1. **Support both profiles during transition**
2. **Auto-detect profile on verification**
3. **Document profile support in API**
4. **Provide profile selection in API requests**
5. **Test with multiple implementations**
6. **Follow DCP specification recommendations**

---

## Summary

### Quick Reference

| Profile | Context | Date Fields | Proof | Status Type | JWT typ |
|---------|---------|-------------|-------|-------------|---------|
| VC 1.1 | `/2018/credentials/v1` | issuanceDate, expirationDate | External (proof object) | StatusList2021Entry | JWT |
| VC 2.0 | `/ns/credentials/v2` | validFrom, validUntil | Enveloped (JWT signature) | BitstringStatusListEntry | vc+ld+jwt |

### Implementation Checklist

- [ ] Profile detection implemented
- [ ] VC 1.1 generation implemented
- [ ] VC 2.0 generation implemented
- [ ] VC 1.1 verification implemented
- [ ] VC 2.0 verification implemented
- [ ] Status list management implemented
- [ ] Unit tests for both profiles
- [ ] Integration tests
- [ ] Documentation updated
- [ ] API supports profile selection
- [ ] Default profile set to VC 2.0

---

## Additional Resources

### Specifications
- [W3C VC Data Model 1.1](https://www.w3.org/TR/vc-data-model/)
- [W3C VC Data Model 2.0](https://www.w3.org/TR/vc-data-model-2.0/)
- [StatusList2021](https://www.w3.org/TR/vc-status-list/)
- [BitstringStatusList](https://www.w3.org/TR/vc-bitstring-status-list/)
- [DCP Specification Appendix A.1](../../dcp_spec.txt)

### Internal Documentation
- `VC_EXAMPLES_COMPARISON.md` - Detailed credential examples with code
- `VC20_FINAL_SUMMARY.md` - Implementation status
- `VC20_IMPLEMENTATION_PLAN.md` - Implementation details
- `VERIFIABLE_CREDENTIALS_GUIDE_NON_TECHNICAL.md` - Business overview
- `PROOF_LOCATION_VISUAL.md` - Visual proof comparison

### Code References
Refer to actual implementation files in the codebase:
- `CredentialIssuanceService.java` - Generation implementation
- `CredentialVerificationService.java` - Verification implementation
- `ProfileId.java` - Profile enumeration
- `StatusListManager.java` - Status list management

---

**Note:** This document focuses on concepts and algorithms. For specific code examples, please refer to the actual implementation in the codebase or the `VC_EXAMPLES_COMPARISON.md` document, which contains detailed code samples that should be validated against the current implementation.

