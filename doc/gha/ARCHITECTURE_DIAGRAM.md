# Integration Tests Workflow - Visual Architecture

## 🏗️ Workflow Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         INTEGRATION TESTS WORKFLOW                          │
│                        (.github/workflows/integration-tests.yml)            │
└─────────────────────────────────────────────────────────────────────────────┘

                                    TRIGGERS
                          ┌──────────────────────────┐
                          │  Push: feature/*, fix/*  │
                          └────────────┬─────────────┘
                                       │
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                              BUILD PHASE                                     │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────┐  ┌──────────────────────────────────┐   │
│  │  build-connector-image          │  │  build-dcp-issuer-image          │   │
│  ├─────────────────────────────────┤  ├──────────────────────────────────┤   │
│  │ 1. Checkout code                │  │ 1. Checkout code                 │   │
│  │ 2. Setup JDK 17 (+ Maven cache) │  │ 2. Setup JDK 17 (+ Maven cache)  │   │
│  │ 3. Maven: clean verify          │  │ 3. Maven: build DCP issuer       │   │
│  │ 4. Login to GHCR                │  │ 4. Build Docker image            │   │
│  │ 5. Build Docker image           │  │ 5. Save as .tar file             │   │
│  │ 6. Push to GHCR                 │  │ 6. Upload artifact (1 day)       │   │
│  └────────────┬────────────────────┘  └───────────────┬──────────────────┘   │
│               │                                       │                      │
│               └───────────────────┬───────────────────┘                      │
│                                   │                                          │
└───────────────────────────────────┼──────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                          TEST EXECUTION PHASE                                │
│                         (Matrix Strategy: 9 jobs)                            │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│                        ┌─────────────────────┐                               │
│                        │  integration-tests  │                               │
│                        │   (Matrix Job)      │                               │
│                        └─────────┬───────────┘                               │
│                                  │                                           │
│        ┌─────────────────────────┼─────────────────────────┐                 │
│        │                         │                         │                 │
│        ▼                         ▼                         ▼                 │
│  ┌──────────┐            ┌──────────┐             ┌──────────┐               │
│  │ STANDARD │            │ STANDARD │             │   DCP    │               │
│  │   ONLY   │            │ AND DCP  │             │   ONLY   │               │
│  │  TESTS   │            │  TESTS   │             │  TESTS   │               │
│  └────┬─────┘            └────┬─────┘             └────┬─────┘               │
│       │                       │                        │                     │
│       ├─ api-endpoints        ├─ negotiation (std)     └─ [future]           │
│       ├─ dataset-api          ├─ negotiation (dcp)                           │
│       └─ connector            ├─ datatransfer-pull (std)                     │
│                               ├─ datatransfer-pull (dcp)                     │
│                               ├─ datatransfer-push (std)                     │
│                               └─ datatransfer-push (dcp)                     │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

## 📊 Matrix Job Execution Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Matrix Job: integration-tests                        │
│                         (Runs 9 times in parallel)                          │
└─────────────────────────────────────────────────────────────────────────────┘

Each Matrix Instance:

START
  │
  ├─► 1. Checkout repository
  │
  ├─► 2. Download DCP issuer artifact?  ──┐
  │                                       │
  │   IF matrix.requires-dcp == true  ────┤
  │                                       │
  │   ├─ Download artifact                │
  │   ├─ Load Docker image                │
  │   └─ Verify image                     │
  │                                       │
  ├─► 3. Start docker-compose  ◄──────────┘
  │       (matrix.docker-compose-file)
  │
  ├─► 4. Wait for services (30s)
  │
  ├─► 5. Check containers running
  │
  ├─► 6. Health checks  ──────────────────┐
  │                                       │
  │   IF matrix.requires-dcp == true  ────┤
  │                                       │
  │   ├─ Check DCP issuer health          │
  │   ├─ Check Connector A health         │
  │   └─ Check Connector B health         │
  │                                       │
  ├─► 7. Obtain VCs?  ◄───────────────────┘
  │                                       
  │   IF matrix.requires-vc == true  ─────┐
  │                                       │
  │   └─ Run dcp-obtain-vc collection     │
  │       (MUST succeed)                  │
  │                                       │
  ├─► 8. Run test collection  ◄───────────┘
  │       (matrix.collection)
  │       via Newman
  │
  ├─► 9. On failure:  ────────────────────┐
  │                                       │
  │   IF failure() ───────────────────────┤
  │                                       │
  │   ├─ Dump all docker logs             │
  │   └─ Collect service logs             │
  │                                       │
  ├─► 10. Cleanup  ◄──────────────────────┘
  │        (always runs)
  │
  │    └─ docker compose down -v
  │
END
```

## 🔄 Standard vs DCP Mode Comparison

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         STANDARD MODE                                    │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Docker Compose: docker-compose.yml                                      │
│                                                                          │
│  Services:                                                               │
│  ┌──────────┐   ┌──────────┐   ┌───────────┐                             │
│  │  MongoDB │   │  MinIO   │   │ Connector │                             │
│  │  :27017  │   │  :9000   │   │   :8080   │                             │
│  └──────────┘   └──────────┘   └───────────┘                             │
│                                                                          │
│  Test Collections:                                                       │
│  • api-endpoints-tests.json                                              │
│  • dataset-api-tests.json                                                │
│  • connector-tests.json                                                  │
│  • negotiation-api-without-counteroffer-tests.json                       │
│  • datatransfer-api-http-pull-tests.json                                 │
│  • datatransfer-api-http-push-tests.json                                 │
│                                                                          │
│  Requires: ✗ DCP Issuer   ✗ VC Obtainment                                │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────┐
│                            DCP MODE                                        │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Docker Compose: docker-compose-dcp.yml                                    │
│                                                                            │
│  Services:                                                                 │
│  ┌──────────┐   ┌──────────┐   ┌─────────────┐   ┌─────────────┐           │
│  │  MongoDB │   │  MinIO   │   │ Connector A │   │ Connector B │           │
│  │  :27017  │   │  :9000   │   │   :8080     │   │   :8090     │           │
│  └──────────┘   └──────────┘   └─────────────┘   └─────────────┘           │
│                                                                            │
│  ┌──────────────┐                                                          │
│  │  DCP Issuer  │                                                          │
│  │    :8084     │                                                          │
│  └──────────────┘                                                          │
│                                                                            │
│  Test Collections:                                                         │
│  1. dcp-obtain-vc/dcp-gha-tests.postman_collection.json (FIRST!)           │
│  2. negotiation-api-without-counteroffer-tests.json                        │
│  3. datatransfer-api-http-pull-tests.json                                  │
│  4. datatransfer-api-http-push-tests.json                                  │
│                                                                            │
│  Requires: ✓ DCP Issuer   ✓ VC Obtainment                                  │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

## 🎯 Test Flow in DCP Mode

```
DCP Mode Test Execution Order:

┌─────────────────────────────────────────────────────────────────┐
│                     STEP 1: VC OBTAINMENT                       │
│                         (CRITICAL)                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Collection: dcp-obtain-vc/dcp-gha-tests.postman_collection     │
│                                                                 │
│  Purpose: Obtain Verifiable Credentials for:                    │
│           • Connector A                                         │
│           • Connector B                                         │
│                                                                 │
│  Failure Handling: MUST succeed or entire test fails            │
│                                                                 │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│               STEP 2: RUN ACTUAL TEST COLLECTION                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Examples:                                                      │
│  • negotiation-api-without-counteroffer-tests.json              │
│  • datatransfer-api-http-pull-tests.json                        │
│  • datatransfer-api-http-push-tests.json                        │
│                                                                 │
│  Uses VCs obtained in Step 1 for authentication                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 🔐 Authentication Flow (DCP Mode)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    DCP Authentication Flow                              │
└─────────────────────────────────────────────────────────────────────────┘

1. Connector A/B Request VC
   │
   ├─► POST to DCP Issuer (:8084)
   │   with DID and proof
   │
   ▼
2. DCP Issuer Validates Request
   │
   ├─► Verify DID ownership
   │   Verify presentation proof
   │
   ▼
3. DCP Issuer Issues VC
   │
   ├─► Generate Verifiable Credential
   │   Sign with issuer key
   │
   ▼
4. Connector Stores VC
   │
   ├─► Save to MongoDB
   │   Use for subsequent API calls
   │
   ▼
5. Connector Uses VC for DSP Protocol
   │
   ├─► Negotiation requests
   │   Data transfer requests
   │   Include VC in headers/body
   │
   ▼
6. Receiving Connector Verifies VC
   │
   └─► Validate signature
       Check issuer trust
       Verify claims
```

## 📦 Artifact Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Artifact Management                              │
└─────────────────────────────────────────────────────────────────────────┘

build-dcp-issuer-image job:
   │
   ├─► Build DCP Issuer Docker image
   │
   ├─► Save image as .tar file
   │   /tmp/dcp-issuer-image.tar
   │
   ├─► Upload to GitHub Actions artifacts
   │   Name: "dcp-issuer-image"
   │   Retention: 1 day
   │
   ▼

integration-tests job (DCP mode only):
   │
   ├─► Download artifact "dcp-issuer-image"
   │   to /tmp/
   │
   ├─► Load into Docker
   │   docker load --input /tmp/dcp-issuer-image.tar
   │
   ├─► Verify image exists
   │   docker inspect dcp-issuer:test
   │
   ├─► Use in docker-compose-dcp.yml
   │   image: dcp-issuer:test
   │
   ▼
   Ready for testing!

Benefits:
• DCP issuer built once, reused 3 times
• Saved ~10-15 minutes of build time
• Consistent image across all DCP tests
```

## 🎭 Matrix Strategy Visualization

```
Matrix Definition:
┌───────────────────────────────────────────────────────────────────┐
│ test-suite | collection | docker-compose | mode | dcp | vc        │
├───────────────────────────────────────────────────────────────────┤
│ api-endpoints | api.json | compose.yml | std | no | no            │
│ dataset-api | dataset.json | compose.yml | std | no | no          │
│ connector | connector.json | compose.yml | std | no | no          │
│ negotiation | negot.json | compose.yml | std | no | no            │
│ negotiation | negot.json | compose-dcp.yml | dcp | yes | yes      │
│ dt-pull | dt-pull.json | compose.yml | std | no | no              │
│ dt-pull | dt-pull.json | compose-dcp.yml | dcp | yes | yes        │
│ dt-push | dt-push.json | compose.yml | std | no | no              │
│ dt-push | dt-push.json | compose-dcp.yml | dcp | yes | yes        │
└───────────────────────────────────────────────────────────────────┘
                                │
                                ▼
                    Creates 9 parallel jobs:
┌──────────────────────────────────────────────────────────────────────┐
│ Job 1: api-endpoints (standard)                                      │
│ Job 2: dataset-api (standard)                                        │
│ Job 3: connector (standard)                                          │
│ Job 4: negotiation-api-without-counteroffer (standard)               │
│ Job 5: negotiation-api-without-counteroffer (dcp) ← Downloads DCP    │
│ Job 6: datatransfer-api-http-pull (standard)                         │
│ Job 7: datatransfer-api-http-pull (dcp) ← Downloads DCP              │
│ Job 8: datatransfer-api-http-push (standard)                         │
│ Job 9: datatransfer-api-http-push (dcp) ← Downloads DCP              │
└──────────────────────────────────────────────────────────────────────┘
                All run in parallel!
```

## ⚡ Performance Optimization

```
Without Matrix (Sequential):
┌──────────────────────────────────────────────────────────────────┐
│ Test 1 → Test 2 → Test 3 → Test 4 → Test 5 → Test 6 → Test 7     │
│                                                                  │
│ Total Time: ~45-60 minutes                                       │
└──────────────────────────────────────────────────────────────────┘

With Matrix (Parallel):
┌──────────────────────────────────────────────────────────────────┐
│ Test 1 ┐                                                         │
│ Test 2 ├─ All run                                                │
│ Test 3 │  simultaneously                                         │
│ Test 4 ├─ on separate                                            │
│ Test 5 │  runners                                                │
│ Test 6 ├─                                                        │
│ Test 7 ┘                                                         │
│                                                                  │
│ Total Time: ~15-20 minutes                                       │
│ Time Saved: ~30-40 minutes (60-70% faster!)                      │
└──────────────────────────────────────────────────────────────────┘
```

## 🛡️ Error Handling & Recovery

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Error Handling Strategy                        │
└─────────────────────────────────────────────────────────────────────┘

Strategy: fail-fast: false
   │
   └─► All matrix jobs complete even if one fails
       │
       ├─► Benefit: Get full picture of test status
       └─► Drawback: Uses more CI minutes

On Test Failure:
   │
   ├─► 1. Newman reports failure
   │
   ├─► 2. Dump all docker logs
   │      (jwalton/gh-docker-logs@v2)
   │
   ├─► 3. Collect service-specific logs
   │      • DCP Issuer (if DCP mode)
   │      • Connector A/B (if DCP mode)
   │      • MongoDB
   │      • MinIO
   │
   ├─► 4. Mark job as failed (red X)
   │
   └─► 5. Cleanup: docker compose down -v
           (runs even on failure via if: always())

Recovery:
   │
   ├─► Review logs in GitHub Actions UI
   ├─► Reproduce locally with same docker-compose
   ├─► Fix issue in code/tests
   └─► Push fix → Workflow re-runs automatically
```

---

**Note**: This visual guide complements the detailed documentation in `INTEGRATION_TESTS_GUIDE.md`

