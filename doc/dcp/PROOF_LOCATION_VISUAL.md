# Visual Proof Location Comparison - VC 1.1 vs VC 2.0

## Side-by-Side Comparison

```
┌─────────────────────────────────────────┐  ┌─────────────────────────────────────────┐
│   VC 1.1: EXTERNAL PROOF (Two Layers)  │  │   VC 2.0: ENVELOPED PROOF (One Layer)  │
│   Profile: vc11-sl2021/jwt              │  │   Profile: vc20-bssl/jwt                │
└─────────────────────────────────────────┘  └─────────────────────────────────────────┘

┌─────────────────────────────────────────┐  ┌─────────────────────────────────────────┐
│ JWT HEADER                              │  │ JWT HEADER                              │
├─────────────────────────────────────────┤  ├─────────────────────────────────────────┤
│ {                                       │  │ {                                       │
│   "alg": "EdDSA",                       │  │   "alg": "EdDSA",                       │
│   "typ": "JWT",                         │  │   "typ": "vc+ld+jwt",    ← Different!  │
│   "kid": "did:web:...#key-1"            │  │   "kid": "did:web:...#key-1"            │
│ }                                       │  │ }                                       │
└─────────────────────────────────────────┘  └─────────────────────────────────────────┘
                  ↓                                              ↓
┌─────────────────────────────────────────┐  ┌─────────────────────────────────────────┐
│ JWT PAYLOAD                             │  │ JWT PAYLOAD                             │
├─────────────────────────────────────────┤  ├─────────────────────────────────────────┤
│ {                                       │  │ {                                       │
│   "vc": {            ← Wrapped in "vc"  │  │   "@context": [                         │
│     "@context": [                       │  │     ".../credentials/v2"  ← VC 2.0     │
│       ".../credentials/v1"  ← VC 1.1   │  │   ],                                    │
│     ],                                  │  │   "id": "...",                          │
│     "id": "...",                        │  │   "type": [...],                        │
│     "type": [...],                      │  │   "issuer": {...},                      │
│     "issuer": "...",                    │  │   "validFrom": "...",  ← New fields    │
│     "issuanceDate": "...", ← Old fields │  │   "validUntil": "...", ← New fields    │
│     "expirationDate": "...",← Old fields│  │   "credentialSubject": {...},           │
│     "credentialSubject": {...},         │  │   "credentialStatus": {                 │
│     "credentialStatus": {               │  │     "type": "BitstringStatusListEntry" │
│       "type": "StatusList2021Entry"     │  │     "statusListIndex": "94567",         │
│       "statusListIndex": "94567",       │  │     "statusListCredential": "..."       │
│       "statusListCredential": "..."     │  │   },                                    │
│     },                                  │  │   "iss": "...",                         │
│     ┌───────────────────────────────┐   │  │   "sub": "...",                         │
│     │ "proof": {    ← INTERNAL PROOF│   │  │   "nbf": 1705329000,                    │
│     │   "type": "Ed25519...",       │   │  │   "exp": 1736951400                     │
│     │   "created": "...",           │   │  │ }                                       │
│     │   "verificationMethod": "..." │   │  │ ❌ NO PROOF OBJECT                      │
│     │   "proofValue": "z58DAd..."   │   │  │    JWT itself IS the proof!             │
│     │ }                             │   │  │                                         │
│     └───────────────────────────────┘   │  │                                         │
│   },                                    │  │                                         │
│   "iss": "...",                         │  │                                         │
│   "sub": "...",                         │  │                                         │
│   "nbf": 1705329000,                    │  │                                         │
│   "exp": 1736951400                     │  │                                         │
│ }                                       │  │                                         │
└─────────────────────────────────────────┘  └─────────────────────────────────────────┘
                  ↓                                              ↓
┌─────────────────────────────────────────┐  ┌─────────────────────────────────────────┐
│ JWT SIGNATURE (External layer)          │  │ JWT SIGNATURE (Only layer - enveloped)  │
├─────────────────────────────────────────┤  ├─────────────────────────────────────────┤
│ ECDSA/EdDSA signature                   │  │ ECDSA/EdDSA signature                   │
│ - Signs header + payload                │  │ - Signs header + payload                │
│ - Proves JWT integrity                  │  │ - Proves JWT integrity                  │
│ - Separate from internal VC proof       │  │ - IS the VC proof (enveloped)           │
└─────────────────────────────────────────┘  └─────────────────────────────────────────┘

┌─────────────────────────────────────────┐  ┌─────────────────────────────────────────┐
│ VERIFICATION STEPS                      │  │ VERIFICATION STEPS                      │
├─────────────────────────────────────────┤  ├─────────────────────────────────────────┤
│ 1. ✅ Verify JWT signature              │  │ 1. ✅ Verify JWT signature              │
│ 2. ✅ Verify internal proof object      │  │ 2. ❌ (No internal proof - done!)       │
│    - Extract proof.proofValue           │  │                                         │
│    - Verify with proof.verificationMethod│  │                                         │
│ 3. ✅ Check StatusList2021              │  │ 3. ✅ Check BitstringStatusList         │
│                                         │  │                                         │
│ TWO PROOF LAYERS                        │  │ ONE PROOF LAYER                         │
└─────────────────────────────────────────┘  └─────────────────────────────────────────┘
```

## Key Visual Differences

### 1. Payload Structure

```
VC 1.1                           VC 2.0
======                           ======

{                                {
  "vc": {              ←───────┐   "@context": [...]  ← Direct properties
    "@context": [...]  │        │   "id": "...",
    "id": "...",       │ Nested │   "type": [...],
    "type": [...],     │ inside │   "credentialSubject": {...},
    "proof": {...}  ←──┘ "vc"   │   ❌ No "vc" wrapper
  },                            │   ❌ No "proof" object
  "iss": "...",                 │   "iss": "...",
  "sub": "..."                  │   "sub": "..."
}                                }
```

### 2. Proof Location

```
VC 1.1: payload.vc.proof                    VC 2.0: (no proof object)
────────────────────────────                ─────────────────────────

{                                           {
  "vc": {                                     "@context": [...],
    ...                                       "id": "...",
    "proof": {          ← HERE                ... credential data ...
      "type": "Ed25519Signature2020",         ❌ No proof object
      "verificationMethod": "...",            (JWT signature IS the proof)
      "proofValue": "..."                   }
    }
  }
}
```

### 3. Status List Type

```
VC 1.1                                      VC 2.0
======                                      ======

"credentialStatus": {                       "credentialStatus": {
  "type": "StatusList2021Entry",              "type": "BitstringStatusListEntry",
           ^^^^^^^^^^^^^^^^^^^                         ^^^^^^^^^^^^^^^^^^^^^^^^
  "statusListIndex": "94567",                 "statusListIndex": "94567",
  "statusListCredential": "..."               "statusListCredential": "..."
}                                           }
```

### 4. Context URLs

```
VC 1.1                                      VC 2.0
======                                      ======

"@context": [                               "@context": [
  "https://www.w3.org/                        "https://www.w3.org/
   2018/credentials/v1"                        ns/credentials/v2"
   ^^^^                                        ^^
]                                           ]
```

### 5. Date Fields

```
VC 1.1                                      VC 2.0
======                                      ======

"issuanceDate": "2024-01-15...",            "validFrom": "2024-01-15...",
"expirationDate": "2025-01-15..."           "validUntil": "2025-01-15..."
```

## Minimal Working Examples

### VC 1.1 Minimal

```json
{
  "vc": {
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiableCredential"],
    "issuer": "did:web:issuer.example.com",
    "issuanceDate": "2024-01-15T14:30:00Z",
    "credentialSubject": {
      "id": "did:web:holder.example.com"
    },
    "credentialStatus": {
      "type": "StatusList2021Entry",
      "statusListIndex": "94567",
      "statusListCredential": "https://example.com/status/1"
    },
    "proof": {
      "type": "Ed25519Signature2020",
      "created": "2024-01-15T14:30:00Z",
      "verificationMethod": "did:web:issuer.example.com#key-1",
      "proofPurpose": "assertionMethod",
      "proofValue": "z58DAdFfa..."
    }
  }
}
```

### VC 2.0 Minimal

```json
{
  "@context": ["https://www.w3.org/ns/credentials/v2"],
  "type": ["VerifiableCredential"],
  "issuer": "did:web:issuer.example.com",
  "validFrom": "2024-01-15T14:30:00Z",
  "credentialSubject": {
    "id": "did:web:holder.example.com"
  },
  "credentialStatus": {
    "type": "BitstringStatusListEntry",
    "statusListIndex": "94567",
    "statusListCredential": "https://example.com/status/3"
  }
}
```

Notice: VC 2.0 has NO proof object!

## Detection Algorithm

```javascript
function detectProfile(jwt) {
  const payload = decodeJWT(jwt);
  
  // Check if wrapped in "vc" claim
  const credential = payload.vc || payload;
  
  // Check context
  const context = credential['@context'];
  const isVC20 = context.some(c => c.includes('/ns/credentials/v2'));
  
  // Check status type
  const statusType = credential.credentialStatus?.type;
  
  if (isVC20 && statusType === 'BitstringStatusListEntry') {
    return 'vc20-bssl/jwt';
  }
  
  if (!isVC20 && statusType === 'StatusList2021Entry') {
    return 'vc11-sl2021/jwt';
  }
  
  return 'vc11-sl2021/jwt'; // default
}
```

## Quick Reference Table

| Feature | VC 1.1 | VC 2.0 |
|---------|--------|--------|
| Wrapped in "vc" claim? | ✅ Yes | ❌ No |
| Has proof object? | ✅ Yes (internal) | ❌ No (enveloped) |
| Context version | v1 (2018) | v2 (ns) |
| Status type | StatusList2021Entry | BitstringStatusListEntry |
| Date fields | issuanceDate/expirationDate | validFrom/validUntil |
| JWT typ | JWT | vc+ld+jwt |
| Proof layers | 2 (JWT + internal) | 1 (JWT only) |

---
