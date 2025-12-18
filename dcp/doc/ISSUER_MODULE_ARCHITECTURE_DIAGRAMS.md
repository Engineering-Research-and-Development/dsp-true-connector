# DCP Issuer Module - Architecture Diagrams

## Current Architecture (Before Split)

```
┌─────────────────────────────────────────────────────────────┐
│                     DCP Module (Port 8083)                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌────────────────────────────────────────────────────┐    │
│  │          REST Controllers                          │    │
│  ├────────────────────────────────────────────────────┤    │
│  │  DidDocumentController                             │    │
│  │    └─ GET /.well-known/did.json (SHARED!)         │    │
│  │                                                     │    │
│  │  IssuerController (Issuer functionality)           │    │
│  │    └─ POST /issuer/credentials                     │    │
│  │    └─ GET  /issuer/requests/{id}                   │    │
│  │                                                     │    │
│  │  DcpController (Holder functionality)              │    │
│  │    └─ POST /dcp/credentials                        │    │
│  │    └─ POST /dcp/presentations                      │    │
│  └────────────────────────────────────────────────────┘    │
│                                                             │
│  ┌────────────────────────────────────────────────────┐    │
│  │          Services (Mixed Concerns)                 │    │
│  ├────────────────────────────────────────────────────┤    │
│  │  DidDocumentService (SHARED - Problem!)            │    │
│  │  IssuerService                                     │    │
│  │  HolderService                                     │    │
│  │  KeyService (SHARED)                               │    │
│  │  CredentialIssuanceService                         │    │
│  │  PresentationService                               │    │
│  └────────────────────────────────────────────────────┘    │
│                                                             │
│  ┌────────────────────────────────────────────────────┐    │
│  │          Data Layer                                │    │
│  ├────────────────────────────────────────────────────┤    │
│  │  MongoDB (true_connector_provider)                 │    │
│  │    ├─ credential_requests (issuer)                 │    │
│  │    ├─ verifiable_credentials (holder)              │    │
│  │    └─ key_metadata (shared)                        │    │
│  └────────────────────────────────────────────────────┘    │
│                                                             │
│  ┌────────────────────────────────────────────────────┐    │
│  │          Keystore                                  │    │
│  │    eckey.p12 (SHARED - Security Risk!)             │    │
│  └────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘

Problems:
  ❌ Single DID for both issuer and holder
  ❌ Shared keystore - security risk
  ❌ Mixed concerns in single module
  ❌ Cannot scale independently
  ❌ Tight coupling between issuer and holder
```

## Proposed Architecture (After Split)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         dcp-common (Library)                            │
├─────────────────────────────────────────────────────────────────────────┤
│  Shared Models:                                                         │
│    ├─ DidDocument, VerificationMethod, ServiceEntry                    │
│    ├─ VerifiableCredential, VerifiablePresentation                     │
│    ├─ IssuerMetadata, CredentialRequest                                │
│    └─ DCP Message Models (CredentialMessage, etc.)                     │
│                                                                         │
│  Shared Utilities:                                                      │
│    ├─ JWT utilities                                                     │
│    └─ DID resolution utilities                                         │
└─────────────────────────────────────────────────────────────────────────┘
                                   ▲
                                   │ (depends on)
                    ┌──────────────┴──────────────┐
                    │                             │
                    │                             │
┌───────────────────▼─────────┐   ┌───────────────▼─────────────┐
│   DCP Holder/Verifier       │   │    DCP Issuer               │
│   (Port 8083)               │   │    (Port 8084)              │
├─────────────────────────────┤   ├─────────────────────────────┤
│                             │   │                             │
│  ┌────────────────────────┐ │   │  ┌────────────────────────┐ │
│  │   REST Controllers     │ │   │  │   REST Controllers     │ │
│  ├────────────────────────┤ │   │  ├────────────────────────┤ │
│  │  DidDocumentController │ │   │  │ IssuerDidDocument-     │ │
│  │    └─ GET /.well-known │ │   │  │     Controller         │ │
│  │       /did.json        │ │   │  │   └─ GET /.well-known  │ │
│  │  DcpController         │ │   │  │      /did.json         │ │
│  │    └─ Holder endpoints │ │   │  │  IssuerController      │ │
│  └────────────────────────┘ │   │  │    └─ /issuer/*        │ │
│                             │   │  │  IssuerAdminController │ │
│  ┌────────────────────────┐ │   │  │    └─ /issuer/admin/*  │ │
│  │   Services             │ │   │  └────────────────────────┘ │
│  ├────────────────────────┤ │   │                             │
│  │  DidDocumentService    │ │   │  ┌────────────────────────┐ │
│  │    (Holder DID)        │ │   │  │   Services             │ │
│  │  HolderService         │ │   │  ├────────────────────────┤ │
│  │  PresentationService   │ │   │  │ IssuerDidDocument-     │ │
│  │  ConsentService        │ │   │  │    Service             │ │
│  │  KeyService            │ │   │  │   (Issuer DID)         │ │
│  │    (Holder keys)       │ │   │  │ IssuerService          │ │
│  └────────────────────────┘ │   │  │ CredentialIssuance-    │ │
│                             │   │  │    Service              │ │
│  ┌────────────────────────┐ │   │  │ CredentialDelivery-    │ │
│  │   Data Layer           │ │   │  │    Service              │ │
│  ├────────────────────────┤ │   │  │ IssuerKeyService       │ │
│  │  MongoDB               │ │   │  │ IssuerKeyRotation-     │ │
│  │  (Port 27017)          │ │   │  │    Service             │ │
│  │  DB: true_connector_   │ │   │  └────────────────────────┘ │
│  │      provider          │ │   │                             │
│  │    ├─ verifiable_      │ │   │  ┌────────────────────────┐ │
│  │    │   credentials     │ │   │  │   Data Layer           │ │
│  │    ├─ consent_records  │ │   │  ├────────────────────────┤ │
│  │    └─ holder_key_      │ │   │  │  MongoDB               │ │
│  │       metadata         │ │   │  │  (Port 27018)          │ │
│  └────────────────────────┘ │   │  │  DB: issuer_db         │ │
│                             │   │  │    ├─ credential_       │ │
│  ┌────────────────────────┐ │   │  │    │   requests         │ │
│  │   Keystore             │ │   │  │    ├─ issuer_key_       │ │
│  │  eckey-holder.p12      │ │   │  │    │   metadata         │ │
│  └────────────────────────┘ │   │  │    └─ issued_          │ │
│                             │   │  │       credentials_log   │ │
│  DID:                       │   │  └────────────────────────┘ │
│  did:web:localhost%3A8083:  │   │                             │
│       holder                │   │  ┌────────────────────────┐ │
│                             │   │  │   Keystore             │ │
└─────────────────────────────┘   │  │  eckey-issuer.p12      │ │
                                  │  └────────────────────────┘ │
                                  │                             │
                                  │  DID:                       │
                                  │  did:web:localhost%3A8084:  │
                                  │       issuer                │
                                  │                             │
                                  └─────────────────────────────┘

Benefits:
  ✅ Separate DIDs for issuer and holder
  ✅ Independent keystores - enhanced security
  ✅ Clear separation of concerns
  ✅ Can scale independently
  ✅ Shared code in common library
```

## DID Document Separation

### Before Split (Single DID)

```
┌─────────────────────────────────────────────────────────┐
│  DID: did:web:localhost%3A8083:holder                   │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Service Endpoints:                                     │
│    ├─ CredentialService (holder)                       │
│    └─ IssuerService (issuer) ← PROBLEM: Mixed roles!   │
│                                                         │
│  Verification Methods:                                  │
│    └─ Shared key for both roles ← SECURITY RISK!       │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### After Split (Separate DIDs)

```
┌──────────────────────────────────┐  ┌──────────────────────────────────┐
│  Holder DID                      │  │  Issuer DID                      │
│  did:web:localhost%3A8083:holder │  │  did:web:localhost%3A8084:issuer │
├──────────────────────────────────┤  ├──────────────────────────────────┤
│                                  │  │                                  │
│  Service Endpoints:              │  │  Service Endpoints:              │
│    └─ CredentialService          │  │    └─ IssuerService              │
│       http://localhost:8083      │  │       http://localhost:8084/     │
│                                  │  │       issuer                     │
│  Verification Methods:           │  │                                  │
│    └─ Holder signing key         │  │  Verification Methods:           │
│       (eckey-holder.p12)         │  │    └─ Issuer signing key         │
│                                  │  │       (eckey-issuer.p12)         │
│                                  │  │                                  │
└──────────────────────────────────┘  └──────────────────────────────────┘
```

## Deployment Architecture

### Docker Compose Deployment

```
┌─────────────────────────────────────────────────────────────────┐
│                        Docker Host                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────┐  ┌─────────────────────────────┐  │
│  │  Holder Stack           │  │  Issuer Stack               │  │
│  ├─────────────────────────┤  ├─────────────────────────────┤  │
│  │                         │  │                             │  │
│  │  ┌──────────────────┐   │  │  ┌──────────────────────┐   │  │
│  │  │  DCP Holder      │   │  │  │  DCP Issuer          │   │  │
│  │  │  Container       │   │  │  │  Container           │   │  │
│  │  ├──────────────────┤   │  │  ├──────────────────────┤   │  │
│  │  │  Port: 8083      │───┼──┼──│  Port: 8084          │   │  │
│  │  │  JAR: dcp.jar    │   │  │  │  JAR: dcp-issuer.jar │   │  │
│  │  └──────────────────┘   │  │  └──────────────────────┘   │  │
│  │           │              │  │           │                 │  │
│  │           ▼              │  │           ▼                 │  │
│  │  ┌──────────────────┐   │  │  ┌──────────────────────┐   │  │
│  │  │  Holder MongoDB  │   │  │  │  Issuer MongoDB      │   │  │
│  │  ├──────────────────┤   │  │  ├──────────────────────┤   │  │
│  │  │  Port: 27017     │   │  │  │  Port: 27018         │   │  │
│  │  │  DB: true_       │   │  │  │  DB: issuer_db       │   │  │
│  │  │      connector_  │   │  │  │                      │   │  │
│  │  │      provider    │   │  │  │                      │   │  │
│  │  └──────────────────┘   │  │  └──────────────────────┘   │  │
│  │                         │  │                             │  │
│  │  Volumes:               │  │  Volumes:                   │  │
│  │    ├─ holder-mongodb-   │  │    ├─ issuer-mongodb-data  │  │
│  │    │   data             │  │    ├─ issuer-logs          │  │
│  │    └─ holder-logs       │  │    └─ issuer-keystore      │  │
│  │                         │  │                             │  │
│  └─────────────────────────┘  └─────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

External Access:
  ├─ http://localhost:8083/.well-known/did.json  (Holder DID)
  ├─ http://localhost:8083/dcp/*                 (Holder endpoints)
  ├─ http://localhost:8084/.well-known/did.json  (Issuer DID)
  └─ http://localhost:8084/issuer/*              (Issuer endpoints)
```

## Credential Issuance Flow

### Before Split (Internal Communication)

```
┌────────────┐                                    ┌────────────┐
│   Holder   │                                    │   Verifier │
└─────┬──────┘                                    └─────┬──────┘
      │                                                 │
      │  1. Request Credential                         │
      ├────────────────────────────────────┐           │
      │                                    ▼           │
      │                    ┌────────────────────────┐  │
      │                    │    DCP Module          │  │
      │                    │  (Port 8083)           │  │
      │                    ├────────────────────────┤  │
      │                    │  IssuerController      │  │
      │                    │         │              │  │
      │                    │         ▼              │  │
      │                    │  IssuerService         │  │
      │                    │         │              │  │
      │                    │  (Internal call)       │  │
      │                    │         │              │  │
      │  2. Credential     │         ▼              │  │
      │     Delivered      │  DcpController         │  │
      │  ◄─────────────────│  (Holder endpoint)     │  │
      │                    └────────────────────────┘  │
      │                                                │
      │  3. Present Credential                        │
      ├───────────────────────────────────────────────►│
      │                                                │
```

### After Split (Network Communication)

```
┌────────────┐          ┌────────────┐          ┌────────────┐
│   Holder   │          │   Issuer   │          │  Verifier  │
│ (Port 8083)│          │ (Port 8084)│          │            │
└─────┬──────┘          └─────┬──────┘          └─────┬──────┘
      │                       │                       │
      │  1. Request Credential                        │
      ├──────────────────────►│                       │
      │  POST /issuer/        │                       │
      │       credentials     │                       │
      │                       │                       │
      │  2. Request Created   │                       │
      │◄──────────────────────┤                       │
      │  201 Created          │                       │
      │  Location: /issuer/   │                       │
      │           requests/123│                       │
      │                       │                       │
      │                    [Manual Approval]          │
      │                       │                       │
      │  3. Credential        │                       │
      │     Delivery          │                       │
      │◄──────────────────────┤                       │
      │  POST /dcp/           │                       │
      │       credentials     │                       │
      │  (CredentialMessage)  │                       │
      │                       │                       │
      │  4. Present Credential                        │
      ├───────────────────────────────────────────────►│
      │  POST /verify/        │                       │
      │       presentations   │                       │
      │                       │                       │
```

## Key Rotation Flow

```
┌──────────────────────────────────────────────────────────────┐
│                    Key Rotation Service                      │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────────────────────────────────────────┐     │
│  │  Scheduled Task (Daily at 2 AM)                    │     │
│  └────────────┬───────────────────────────────────────┘     │
│               │                                              │
│               ▼                                              │
│  ┌────────────────────────────────────────────────────┐     │
│  │  Check Key Age                                     │     │
│  │    - Get active key metadata from MongoDB          │     │
│  │    - Calculate days since creation                 │     │
│  │    - Compare with rotation threshold (90 days)     │     │
│  └────────────┬───────────────────────────────────────┘     │
│               │                                              │
│               ▼                                              │
│         ┌─────────┐                                          │
│         │ Age >   │  NO                                      │
│         │90 days? ├──────────► Skip Rotation                │
│         └────┬────┘                                          │
│              │ YES                                           │
│              ▼                                               │
│  ┌────────────────────────────────────────────────────┐     │
│  │  Generate New Key Pair                             │     │
│  │    - Generate EC key (P-256)                       │     │
│  │    - Create self-signed certificate                │     │
│  │    - Generate unique alias with timestamp          │     │
│  └────────────┬───────────────────────────────────────┘     │
│               │                                              │
│               ▼                                              │
│  ┌────────────────────────────────────────────────────┐     │
│  │  Persist to Keystore                               │     │
│  │    - Load existing keystore (eckey-issuer.p12)     │     │
│  │    - Add new key entry                             │     │
│  │    - Save keystore to disk                         │     │
│  └────────────┬───────────────────────────────────────┘     │
│               │                                              │
│               ▼                                              │
│  ┌────────────────────────────────────────────────────┐     │
│  │  Update Key Metadata in MongoDB                    │     │
│  │    - Mark old key as inactive/archived             │     │
│  │    - Save new key metadata as active               │     │
│  │    - Record timestamp and alias                    │     │
│  └────────────┬───────────────────────────────────────┘     │
│               │                                              │
│               ▼                                              │
│  ┌────────────────────────────────────────────────────┐     │
│  │  Update In-Memory Key Pair                         │     │
│  │    - Reload key pair from keystore                 │     │
│  │    - Update DID document with new key              │     │
│  └────────────┬───────────────────────────────────────┘     │
│               │                                              │
│               ▼                                              │
│  ┌────────────────────────────────────────────────────┐     │
│  │  Log Rotation Success                              │     │
│  │    - Log new alias                                 │     │
│  │    - Log timestamp                                 │     │
│  └────────────────────────────────────────────────────┘     │
│                                                              │
│  Manual Trigger:                                             │
│    POST /issuer/admin/rotate-key                            │
│         └─► Triggers same flow                              │
│                                                              │
└──────────────────────────────────────────────────────────────┘

Key Metadata Timeline:
  
  Day 0                Day 90               Day 180
    │                    │                    │
    ▼                    ▼                    ▼
  ┌────┐              ┌────┐              ┌────┐
  │Key1│ (active)     │Key2│ (active)     │Key3│ (active)
  └────┘              └────┘              └────┘
    │                    │                    │
    │                    ▼                    ▼
    │                 ┌────┐              ┌────┐
    │                 │Key1│ (archived)   │Key2│ (archived)
    │                 └────┘              └────┘
    │                                        │
    │                                        ▼
    │                                     ┌────┐
    │                                     │Key1│ (archived)
    │                                     └────┘
    │
    └─► All archived keys kept for verification of old credentials
```

## Build and Deployment Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Development Machine                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Build Common Library                                   │
│     cd dcp-common                                           │
│     mvn clean install                                       │
│          │                                                  │
│          ▼                                                  │
│     dcp-common-0.6.4-SNAPSHOT.jar                          │
│          │                                                  │
│          └──────────┬─────────────────┐                    │
│                     │                 │                    │
│  2. Build Holder    │    3. Build Issuer                   │
│     cd dcp          │       cd dcp-issuer                  │
│     mvn clean       │       mvn clean package              │
│     package         │            │                         │
│          │          │            ▼                         │
│          ▼          │       dcp-issuer.jar                 │
│     dcp.jar         │            │                         │
│          │          │            │                         │
│          ▼          │            ▼                         │
│  ┌─────────────────────────────────────────────┐           │
│  │  4. Build Docker Images                     │           │
│  ├─────────────────────────────────────────────┤           │
│  │  cd dcp-issuer                              │           │
│  │  docker-compose build                       │           │
│  │       │                                     │           │
│  │       ▼                                     │           │
│  │  dcp-issuer:latest                          │           │
│  └─────────┬───────────────────────────────────┘           │
│            │                                               │
│            ▼                                               │
│  ┌─────────────────────────────────────────────┐           │
│  │  5. Deploy Containers                       │           │
│  ├─────────────────────────────────────────────┤           │
│  │  docker-compose up -d                       │           │
│  │       │                                     │           │
│  │       ├──► issuer-mongodb (Port 27018)      │           │
│  │       └──► dcp-issuer (Port 8084)           │           │
│  └─────────┬───────────────────────────────────┘           │
│            │                                               │
│            ▼                                               │
│  ┌─────────────────────────────────────────────┐           │
│  │  6. Verify Deployment                       │           │
│  ├─────────────────────────────────────────────┤           │
│  │  curl http://localhost:8084/                │           │
│  │       .well-known/did.json                  │           │
│  │  curl http://localhost:8084/                │           │
│  │       actuator/health                       │           │
│  └─────────────────────────────────────────────┘           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Testing Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Test Pyramid                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                         ┌──────┐                            │
│                         │  E2E │                            │
│                         │Tests │                            │
│                         └──┬───┘                            │
│                            │                                │
│               ┌────────────┴────────────┐                   │
│               │  Integration Tests      │                   │
│               │  - Docker tests         │                   │
│               │  - API tests            │                   │
│               │  - Database tests       │                   │
│               └──────────┬──────────────┘                   │
│                          │                                  │
│           ┌──────────────┴──────────────┐                   │
│           │      Unit Tests              │                  │
│           │  - Service tests             │                  │
│           │  - Controller tests          │                  │
│           │  - Model tests               │                  │
│           └──────────────────────────────┘                  │
│                                                             │
│  Test Coverage by Module:                                  │
│  ┌─────────────────────────────────────────────┐           │
│  │  dcp-common                                 │           │
│  │    └─ Unit tests for models/utilities      │           │
│  │       Target: 90% coverage                 │           │
│  └─────────────────────────────────────────────┘           │
│                                                             │
│  ┌─────────────────────────────────────────────┐           │
│  │  dcp-issuer                                 │           │
│  │    ├─ Unit tests (service/controller)      │           │
│  │    │  Target: 80% coverage                 │           │
│  │    ├─ Integration tests (API)              │           │
│  │    └─ Docker tests (end-to-end)            │           │
│  └─────────────────────────────────────────────┘           │
│                                                             │
│  ┌─────────────────────────────────────────────┐           │
│  │  dcp (holder)                               │           │
│  │    ├─ Unit tests                            │           │
│  │    ├─ Integration tests                    │           │
│  │    └─ Regression tests (ensure no break)   │           │
│  └─────────────────────────────────────────────┘           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

**Document Version**: 1.0  
**Created**: December 18, 2025  
**Purpose**: Visual representation of architecture changes

