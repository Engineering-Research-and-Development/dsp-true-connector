# VP JWT Integration Flow Diagram

## Current Flow (Basic Auth)

```
┌─────────────────────────────────────────────────────────────────────┐
│ ContractNegotiationAPIService.sendContractRequestMessage()          │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             │ Creates ContractRequestMessage
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ okHttpRestClient.sendRequestProtocol(url, body, credentials)        │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             │ credentials = ?
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ credentialUtils.getConnectorCredentials()                           │
│                                                                      │
│   return "Basic Y29ubmVjdG9yQG1haWwuY29tOnBhc3N3b3Jk"              │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             │ Authorization: Basic ...
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ HTTP POST to Provider                                                │
│ https://provider.example/negotiations/request                       │
│                                                                      │
│ Headers:                                                             │
│   Authorization: Basic Y29ubmVjdG9yQG1haWwuY29tOnBhc3N3b3Jk        │
│                                                                      │
│ Body: ContractRequestMessage                                         │
└─────────────────────────────────────────────────────────────────────┘
```

---

## New Flow (VP JWT - Optional)

```
┌─────────────────────────────────────────────────────────────────────┐
│ ContractNegotiationAPIService.sendContractRequestMessage()          │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             │ Creates ContractRequestMessage
                             │ (NO CHANGES NEEDED)
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ okHttpRestClient.sendRequestProtocol(url, body, credentials)        │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             │ credentials = ?
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ credentialUtils.getConnectorCredentials()                           │
│                                                                      │
│ ┌──────────────────────────────────────────────────────────┐       │
│ │ if (dcp.vp.enabled && dcpCredentialService != null) {    │       │
│ │     try {                                                 │       │
│ │         vpJwt = dcpCredentialService.getVpJwt();         │  ◄────┼─── NEW
│ │         if (vpJwt != null) return vpJwt;                 │       │
│ │     } catch (Exception e) { /* log */ }                  │       │
│ │ }                                                         │       │
│ │ return basicAuth();  // Fallback                         │       │
│ └──────────────────────────────────────────────────────────┘       │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             │ If dcp.vp.enabled=true
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ DcpCredentialService.getVerifiablePresentationJwt()                 │  ◄─── NEW
│                                                                      │
│  ┌────────────────────────────────────────────────────────┐        │
│  │ 1. Build PresentationQueryMessage                      │        │
│  │    - scope = dcp.vp.scope (from config)               │        │
│  │                                                        │        │
│  │ 2. Call PresentationService.createPresentation()      │────┐   │
│  │                                                        │    │   │
│  │ 3. Extract JWT string from response                   │    │   │
│  │                                                        │    │   │
│  │ 4. Return "Bearer eyJhbGc..."                         │    │   │
│  └────────────────────────────────────────────────────────┘    │   │
└───────────────────────────────────────────────────────────────┼───┘
                                                                 │
                             ┌───────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ PresentationService.createPresentation(query)                       │
│                                                                      │
│  ┌────────────────────────────────────────────────────────┐        │
│  │ 1. Fetch credentials from repository                   │        │
│  │    - Filter by scope (credential types)               │        │
│  │                                                        │        │
│  │ 2. Group by profileId (homogeneity)                   │        │
│  │                                                        │        │
│  │ 3. For each group:                                     │        │
│  │    a. Collect credential IDs                          │        │
│  │    b. Collect full credentials (JWT strings)          │────┐   │
│  │    c. Build VerifiablePresentation                    │    │   │
│  │    d. Sign VP with holder's key                       │    │   │
│  │                                                        │    │   │
│  │ 4. Return PresentationResponseMessage                  │    │   │
│  └────────────────────────────────────────────────────────┘    │   │
└───────────────────────────────────────────────────────────────┼───┘
                                                                 │
                             ┌───────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ BasicVerifiablePresentationSigner.sign(vp, "jwt")                   │
│                                                                      │
│  ┌────────────────────────────────────────────────────────┐        │
│  │ 1. Get holder's signing key                            │        │
│  │                                                        │        │
│  │ 2. Build JWT claims:                                   │        │
│  │    {                                                   │        │
│  │      "iss": "did:web:consumer.example",               │        │
│  │      "sub": "did:web:consumer.example",               │        │
│  │      "vp": {                                           │        │
│  │        "verifiableCredential": [                       │        │
│  │          "eyJhbGc...FULL_VC_JWT..."  ◄─────────────────┼───────┼─── Embedded!
│  │        ]                                               │        │
│  │      },                                                │        │
│  │      "iat": 1765451602,                                │        │
│  │      "jti": "urn:uuid:..."                             │        │
│  │    }                                                   │        │
│  │                                                        │        │
│  │ 3. Sign with ES256 algorithm                           │        │
│  │                                                        │        │
│  │ 4. Return compact JWT string                           │        │
│  └────────────────────────────────────────────────────────┘        │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             │ Returns VP JWT
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ HTTP POST to Provider                                                │
│ https://provider.example/negotiations/request                       │
│                                                                      │
│ Headers:                                                             │
│   Authorization: Bearer eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsIm...  │  ◄─── NEW!
│                                                                      │
│ Body: ContractRequestMessage                                         │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ Provider (Verifier) Side                                             │
│                                                                      │
│ ┌────────────────────────────────────────────────────────┐         │
│ │ 1. Extract Authorization header                        │         │
│ │                                                        │         │
│ │ 2. Parse VP JWT:                                       │         │
│ │    - Verify holder signature (ES256)                  │         │
│ │    - Extract holder DID from "iss"                    │         │
│ │    - Resolve holder's public key from DID             │         │
│ │    - Validate VP signature                            │         │
│ │                                                        │         │
│ │ 3. Extract embedded VCs from vp.verifiableCredential: │         │
│ │    For each VC JWT:                                    │         │
│ │      - Parse VC JWT                                    │         │
│ │      - Verify issuer signature (ES256)                │         │
│ │      - Extract issuer DID from "iss"                  │         │
│ │      - Resolve issuer's public key from DID           │         │
│ │      - Validate VC signature                          │         │
│ │      - Check issuer is trusted                        │         │
│ │      - Check dates (not expired)                      │         │
│ │      - Check revocation status                        │         │
│ │                                                        │         │
│ │ 4. Make decision: Accept or Reject                     │         │
│ └────────────────────────────────────────────────────────┘         │
│                                                                      │
│ Note: This is SEPARATE WORK - not covered in current proposition    │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Configuration Decision Tree

```
                    Start Contract Negotiation
                             │
                             ▼
                ┌────────────────────────┐
                │ Is dcp.vp.enabled=true?│
                └────────┬───────────────┘
                         │
          ┌──────────────┴──────────────┐
          │ YES                          │ NO
          ▼                              ▼
┌─────────────────────┐        ┌──────────────────┐
│ DcpCredentialService│        │ Use Basic Auth   │
│ available?          │        │                  │
└────────┬────────────┘        │ "Basic ..."      │
         │                     └──────────────────┘
  ┌──────┴──────┐
  │ YES         │ NO
  ▼             ▼
┌─────────┐  ┌─────────┐
│ Try VP  │  │ Fallback│
│ JWT     │  │ Basic   │
└────┬────┘  └─────────┘
     │
     ▼
┌──────────────┐
│ VP created?  │
└────┬─────────┘
     │
  ┌──┴──┐
  │YES  │ NO
  ▼     ▼
┌───┐ ┌─────────┐
│Use│ │Fallback │
│VP │ │Basic    │
└───┘ └─────────┘
```

---

## Sequence Diagram (Detailed)

```
Consumer              CredentialUtils    DcpCredentialService    PresentationService    BasicVPSigner    Provider
   │                         │                      │                      │                   │            │
   │ sendContractRequest()   │                      │                      │                   │            │
   ├────────────────────────►│                      │                      │                   │            │
   │                         │ getConnectorCreds()  │                      │                   │            │
   │                         │                      │                      │                   │            │
   │                         │ Check: dcp.vp.enabled?                      │                   │            │
   │                         │                      │                      │                   │            │
   │                         │ getVpJwt()           │                      │                   │            │
   │                         ├─────────────────────►│                      │                   │            │
   │                         │                      │ createPresentation() │                   │            │
   │                         │                      ├─────────────────────►│                   │            │
   │                         │                      │                      │ Fetch credentials │            │
   │                         │                      │                      │ from repository   │            │
   │                         │                      │                      │                   │            │
   │                         │                      │                      │ sign(vp, "jwt")   │            │
   │                         │                      │                      ├──────────────────►│            │
   │                         │                      │                      │                   │ Build JWT  │
   │                         │                      │                      │                   │ with ES256 │
   │                         │                      │                      │  VP JWT           │            │
   │                         │                      │                      │◄──────────────────┤            │
   │                         │                      │  PresentationResponse│                   │            │
   │                         │                      │◄─────────────────────┤                   │            │
   │                         │  "Bearer eyJhbGc..." │                      │                   │            │
   │                         │◄─────────────────────┤                      │                   │            │
   │  "Bearer eyJhbGc..."    │                      │                      │                   │            │
   │◄────────────────────────┤                      │                      │                   │            │
   │                         │                      │                      │                   │            │
   │ HTTP POST /negotiations/request                │                      │                   │            │
   │ Authorization: Bearer eyJhbGc...                                      │                   │            │
   ├───────────────────────────────────────────────────────────────────────────────────────────────────────►│
   │                         │                      │                      │                   │            │
   │                         │                      │                      │                   │  Validate  │
   │                         │                      │                      │                   │  VP + VCs  │
   │                         │                      │                      │                   │            │
   │                         │                      │                      │                   │  Response  │
   │◄───────────────────────────────────────────────────────────────────────────────────────────────────────┤
   │                         │                      │                      │                   │            │
```

---

## Component Dependencies

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Negotiation Module                           │
│                                                                      │
│  ┌────────────────────────────────────────────────────────┐        │
│  │ ContractNegotiationAPIService                           │        │
│  │   - sendContractRequestMessage()                        │        │
│  │   - Uses: okHttpRestClient                              │        │
│  │   - Uses: credentialUtils                               │        │
│  └────────────────────────────┬───────────────────────────┘        │
└───────────────────────────────┼────────────────────────────────────┘
                                │ uses
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                           Tools Module                               │
│                                                                      │
│  ┌────────────────────────────────────────────────────────┐        │
│  │ CredentialUtils                                         │        │
│  │   - getConnectorCredentials()                           │        │
│  │   - Uses: DcpCredentialService (optional)              │        │
│  └────────────────────────────┬───────────────────────────┘        │
│                                │ uses (when enabled)                │
│                                ▼                                     │
│  ┌────────────────────────────────────────────────────────┐        │
│  │ DcpCredentialService (NEW)                              │  ◄─────┼─── NEW!
│  │   - getVerifiablePresentationJwt()                      │        │
│  │   - Uses: PresentationService                           │        │
│  │   - @ConditionalOnProperty("dcp.vp.enabled")           │        │
│  └────────────────────────────┬───────────────────────────┘        │
└───────────────────────────────┼────────────────────────────────────┘
                                │ uses
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                            DCP Module                                │
│                                                                      │
│  ┌────────────────────────────────────────────────────────┐        │
│  │ PresentationService                                     │        │
│  │   - createPresentation(query)                           │        │
│  │   - Uses: VerifiableCredentialRepository                │        │
│  │   - Uses: BasicVerifiablePresentationSigner            │        │
│  └────────────────────────────┬───────────────────────────┘        │
│                                │ uses                               │
│                                ▼                                     │
│  ┌────────────────────────────────────────────────────────┐        │
│  │ BasicVerifiablePresentationSigner                       │        │
│  │   - sign(vp, format)                                    │        │
│  │   - Uses: KeyService (holder's signing key)            │        │
│  └────────────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Key Benefits Visualization

```
┌──────────────────────────────────────────────────────────────┐
│                    BEFORE (Basic Auth)                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Authorization: Basic Y29ubmVjdG9yQG1haWwuY29tOnBhc3N3b3Jk │
│                                                              │
│  ❌ Weak security (static password)                         │
│  ❌ No verifiable identity                                  │
│  ❌ No credential presentation                              │
│  ❌ Not DCP compliant                                       │
│                                                              │
└──────────────────────────────────────────────────────────────┘

                            ▼  UPGRADE  ▼

┌──────────────────────────────────────────────────────────────┐
│                    AFTER (VP JWT)                            │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Authorization: Bearer eyJhbGciOiJFUzI1NiIsInR5cCI6...     │
│                                                              │
│  ✅ Strong security (ES256 signatures)                      │
│  ✅ Verifiable identity (holder DID)                        │
│  ✅ Credential presentation (embedded VCs)                  │
│  ✅ DCP compliant (W3C VC Data Model)                       │
│  ✅ Trust chain validation (issuer signatures)              │
│  ✅ Policy-driven access control                            │
│  ✅ Optional (feature flag controlled)                      │
│  ✅ Safe fallback (to basic auth)                           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Summary

The proposed integration adds **optional VP JWT support** to the contract negotiation flow by:

1. ✅ Creating a new `DcpCredentialService` (1 new file)
2. ✅ Enhancing `CredentialUtils` (1 modified file)
3. ✅ Adding configuration properties (1 config change)

**Total effort**: 2-4 hours  
**Risk**: Minimal (safe fallback)  
**Impact**: High (DCP compliance, stronger security)

The integration point (`CredentialUtils.getConnectorCredentials()`) is perfect because:
- Already used by all protocol calls
- Single place to modify
- Easy to test
- Safe fallback built-in
- No changes to business logic

