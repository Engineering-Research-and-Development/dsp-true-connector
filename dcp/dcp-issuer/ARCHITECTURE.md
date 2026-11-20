# DCP Issuer Architecture Overview

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     DCP Issuer Service                          │
│                     (Port: 8084)                                │
└─────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────────┐    ┌─────────────┐
│ REST Layer   │    │  Service Layer   │    │ Data Layer  │
│              │    │                  │    │             │
│ Controllers: │    │ Services:        │    │ Repository: │
│ - Issuer     │───▶│ - Issuer         │───▶│ - Credential│
│ - DID Doc    │    │ - Issuance       │    │   Request   │
│              │    │ - Delivery       │    │             │
│              │    │ - Metadata       │    │             │
└──────────────┘    └──────────────────┘    └─────────────┘
                              │                     │
                              │                     ▼
                              │              ┌─────────────┐
                              │              │  MongoDB    │
                              │              │  Database   │
                              │              └─────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │  External Deps   │
                    │                  │
                    │ - dcp-common     │
                    │ - KeyService     │
                    │ - DID Resolver   │
                    │ - HTTP Client    │
                    └──────────────────┘
```

## Component Interactions

```
┌──────────────┐
│   Client     │
│  (Holder)    │
└──────┬───────┘
       │ 1. Request Credentials
       ▼
┌──────────────────────────────┐
│  IssuerController            │
│  /issuer/requests            │
└──────┬───────────────────────┘
       │ 2. Validate & Authorize
       ▼
┌──────────────────────────────┐
│  IssuerService               │
│  - authorizeRequest()        │
│  - createCredentialRequest() │
└──────┬───────────────────────┘
       │ 3. Save Request
       ▼
┌──────────────────────────────┐
│  CredentialRequestRepository │
│  (MongoDB)                   │
└──────────────────────────────┘

       (Administrator approves)

┌──────────────────────────────┐
│  IssuerController            │
│  /requests/{id}/approve      │
└──────┬───────────────────────┘
       │ 4. Approve Request
       ▼
┌──────────────────────────────┐
│  IssuerService               │
│  - approveAndDeliver()       │
└──────┬───────────────────────┘
       │ 5. Generate Credentials
       ▼
┌──────────────────────────────┐
│  CredentialIssuanceService   │
│  - generateCredentials()     │
│  - signJWT()                 │
└──────┬───────────────────────┘
       │ 6. Deliver to Holder
       ▼
┌──────────────────────────────┐
│  CredentialDeliveryService   │
│  - deliverCredentials()      │
│  - resolveEndpoint()         │
└──────┬───────────────────────┘
       │ 7. POST to Holder API
       ▼
┌──────────────┐
│   Holder     │
│   Service    │
│ /credentials │
└──────────────┘
```

## Data Flow

```
1. Credential Request
   ┌─────────┐
   │ Holder  │
   └────┬────┘
        │ POST /issuer/requests
        │ Authorization: Bearer {token}
        │ {holderPid, credentials:[...]}
        ▼
   ┌─────────────┐
   │   Issuer    │────┐
   └─────────────┘    │ Save to MongoDB
                      ▼
              ┌───────────────┐
              │ Request: {    │
              │  issuerPid,   │
              │  holderPid,   │
              │  status: PEND │
              │ }             │
              └───────────────┘

2. Credential Approval
   ┌──────────┐
   │  Admin   │
   └────┬─────┘
        │ POST /issuer/requests/{id}/approve
        │ {customClaims: {...}}
        ▼
   ┌─────────────┐
   │   Issuer    │
   └─────┬───────┘
         │ 1. Retrieve request
         │ 2. Generate credentials
         │ 3. Sign with ES256
         ▼
   ┌─────────────┐
   │ JWT VC: {   │
   │  iss: did,  │
   │  sub: holder│
   │  vc: {...}  │
   │ }           │
   └─────┬───────┘
         │ 4. Deliver to holder
         ▼
   ┌─────────────┐
   │   Holder    │
   │   Service   │
   └─────────────┘

3. DID Document Resolution
   ┌─────────┐
   │ Client  │
   └────┬────┘
        │ GET /.well-known/did.json
        ▼
   ┌─────────────────┐
   │ DID Controller  │
   └────┬────────────┘
        │ Retrieve DID document
        ▼
   ┌─────────────┐
   │ KeyService  │
   └────┬────────┘
        │ Get public key
        ▼
   ┌─────────────┐
   │ DID Doc: {  │
   │  @context,  │
   │  id: did,   │
   │  verifyKey  │
   │ }           │
   └─────────────┘
```

## Credential Status Lists

Credentials of the `vc11-sl2021/jwt` and `vc20-bssl/jwt` profiles carry a `credentialStatus` entry for revocation (DCP v1.0.1 §6.10). `StatusListService` manages the lists in the `status_lists` collection:

- **One list type per profile**: StatusList2021 (`StatusList2021Entry`, `StatusList2021Credential`) for `vc11-sl2021/jwt` and BitstringStatusList (`BitstringStatusListEntry`, `BitstringStatusListCredential`) for `vc20-bssl/jwt`. Lists are kept per issuer DID, status purpose and profile.
- **Allocation is separate from status**: `StatusList.nextIndex` counts allocated entries and never goes down, so an index is never reused. A new entry's bit stays 0 (valid). When a list is full (131072 entries), a new list is started.
- **Revocation** (`POST /api/issuer/status-lists/{listId}/entries/{index}/revoke`) sets the bit to 1 and re-signs the list. Revocation is not reversible.
- **Publication**: the signed list (a compact JWT built by `StatusListCredentialGenerator`) is stored with the list and served without authentication at `<dcp.base-url>/issuer/status/{listId}`, the URL the credentials carry as `statusListCredential`. `dcp.base-url` is therefore required; the Issuer fails at startup without it. It is re-signed when served if the stored JWT was signed with a key that is no longer current, for example after key rotation.
- **Identifiers**: list IDs are random UUIDs, so the public URLs are globally unambiguous. In multi-participant mode (#426) the URL becomes `/<participantId>/issuer/status/{listId}`.
- **Concurrency**: `StatusList` has a `@Version` field; concurrent allocations or revocations of one list are retried on an optimistic locking conflict.

```
CredentialIssuanceService ──allocateStatusEntry(did, "revocation", profile)──▶ StatusListService
                                                                                │  nextIndex++ (bit stays 0)
Verifier ──GET /issuer/status/{listId}──▶ StatusListController ──publishStatusList()─┤
Admin ──POST /api/issuer/status-lists/{listId}/entries/{index}/revoke──▶ revoke()  │  bit = 1, re-sign
                                                                                ▼
                                                                       status_lists (MongoDB)
```

## Deployment Architecture

```
┌────────────────────────────────────────────────────┐
│                  Docker Host                       │
│                                                    │
│  ┌──────────────────────┐  ┌──────────────────┐  │
│  │  dcp-issuer:8084     │  │  MongoDB:27017   │  │
│  │                      │  │                  │  │
│  │  ┌────────────────┐ │  │  ┌────────────┐  │  │
│  │  │ Spring Boot    │ │  │  │  issuer_db │  │  │
│  │  │ Application    │◄├──┼──┤            │  │  │
│  │  └────────────────┘ │  │  └────────────┘  │  │
│  │                      │  │                  │  │
│  │  ┌────────────────┐ │  │                  │  │
│  │  │ Keystore       │ │  │                  │  │
│  │  │ eckey-issuer   │ │  │                  │  │
│  │  └────────────────┘ │  │                  │  │
│  └──────────────────────┘  └──────────────────┘  │
│            │                                      │
│            │ Port Mapping                         │
│            ▼                                      │
│    Host:8084 ──────┐                             │
└────────────────────┼──────────────────────────────┘
                     │
                     ▼
              External Access
              http://localhost:8084
```

## Module Dependencies

```
┌─────────────────────────────────────────────┐
│           dcp-issuer Module                 │
└────────────┬────────────────────────────────┘
             │
             │ depends on
             ▼
┌─────────────────────────────────────────────┐
│          dcp-common Module                  │
│                                             │
│  - CredentialRequest model                  │
│  - CredentialMessage model                  │
│  - IssuerMetadata model                     │
│  - KeyService                               │
│  - DidDocumentService                       │
│  - SelfIssuedIdTokenService                 │
│  - DidResolverService                       │
└─────────────────────────────────────────────┘
             │
             │ depends on
             ▼
┌─────────────────────────────────────────────┐
│            tools Module                     │
│                                             │
│  - OkHttpRestClient                         │
│  - GenericApiResponse                       │
│  - Utilities                                │
└─────────────────────────────────────────────┘
```

## Security Flow

```
1. Authentication
   ┌─────────┐
   │ Client  │
   └────┬────┘
        │ Authorization: Bearer {JWT}
        ▼
   ┌──────────────────────┐
   │ IssuerController     │
   └────┬─────────────────┘
        │ Extract token
        ▼
   ┌──────────────────────┐
   │ IssuerService        │
   │ - authorizeRequest() │
   └────┬─────────────────┘
        │ Validate JWT
        ▼
   ┌──────────────────────────┐
   │ SelfIssuedIdTokenService │
   │ - validateToken()        │
   └────┬─────────────────────┘
        │ Verify signature
        │ Check claims
        ▼
   ┌──────────────┐
   │ JWT Claims   │
   │ {sub, iss..} │
   └──────────────┘

2. Credential Signing
   ┌──────────────────────┐
   │ CredentialIssuance   │
   │ Service              │
   └────┬─────────────────┘
        │ Get signing key
        ▼
   ┌──────────────────────┐
   │ KeyService           │
   │ - getSigningJwk()    │
   └────┬─────────────────┘
        │ Load from keystore
        ▼
   ┌──────────────────────┐
   │ eckey-issuer.p12     │
   │ (EC Key, ES256)      │
   └────┬─────────────────┘
        │ Sign JWT
        ▼
   ┌──────────────────────┐
   │ Signed JWT VC        │
   │ with ES256 signature │
   └──────────────────────┘
```

## Configuration Hierarchy

```
┌───────────────────────────────────────┐
│     application.properties            │
│                                       │
│  ┌─────────────────────────────────┐ │
│  │ Issuer Configuration            │ │
│  │ - DID                           │ │
│  │ - Base URL                      │ │
│  │ - Supported Profiles            │ │
│  └─────────────────────────────────┘ │
│                                       │
│  ┌─────────────────────────────────┐ │
│  │ Keystore Configuration          │ │
│  │ - Path                          │ │
│  │ - Password                      │ │
│  │ - Alias                         │ │
│  │ - Rotation Days                 │ │
│  └─────────────────────────────────┘ │
│                                       │
│  ┌─────────────────────────────────┐ │
│  │ MongoDB Configuration           │ │
│  │ - Host                          │ │
│  │ - Port                          │ │
│  │ - Database                      │ │
│  │ - Authentication                │ │
│  └─────────────────────────────────┘ │
│                                       │
│  ┌─────────────────────────────────┐ │
│  │ Credentials Configuration       │ │
│  │ - Supported types               │ │
│  │ - Profiles                      │ │
│  │ - Formats                       │ │
│  └─────────────────────────────────┘ │
└───────────────────────────────────────┘
```

## API Flow Diagram

```
┌──────────┐
│  Client  │
└────┬─────┘
     │
     │ 1. GET /issuer/metadata
     │    Authorization: Bearer {token}
     ▼
┌─────────────┐
│  Issuer     │──▶ Return: {issuer, credentialsSupported:[...]}
└─────────────┘
     │
     │ 2. POST /issuer/requests
     │    {holderPid, credentials:[...]}
     ▼
┌─────────────┐
│  Issuer     │──▶ Return: {issuerPid, status: "PENDING"}
└─────────────┘
     │
     │ 3. POST /issuer/requests/{id}/approve
     │    {customClaims:{...}}
     ▼
┌─────────────┐
│  Issuer     │──▶ Generate & deliver credentials
└─────────────┘
     │
     │ 4. Credentials delivered to holder
     ▼
┌──────────┐
│  Holder  │
│  Service │
└──────────┘
```

---

**Generated**: December 19, 2025  
**Module**: dcp-issuer  
**Version**: 0.6.4-SNAPSHOT

