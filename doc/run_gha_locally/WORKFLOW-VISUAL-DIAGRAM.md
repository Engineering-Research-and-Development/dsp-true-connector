# GitHub Actions DCP Tests Workflow - Visual Flow

## Workflow Structure

```
┌─────────────────────────────────────────────────────────────┐
│                    GitHub Actions Trigger                    │
│  Push: feature/*, hotfix/*, fix/*, develop, main            │
│  Pull Request: develop, main                                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ├──> Parallel Build
                              │
                ┌─────────────┴─────────────┐
                │                           │
                ▼                           ▼
┌───────────────────────────┐   ┌───────────────────────────┐
│ build-connector-image     │   │ build-dcp-issuer-image    │
│ • Build with Maven        │   │ • Build DCP Issuer Maven  │
│ • Build Docker image      │   │ • Build Docker image      │
│ • Push to GHCR            │   │ • Save as artifact        │
└───────────────────────────┘   └───────────────────────────┘
                │                           │
                └─────────────┬─────────────┘
                              │
                              ▼
                ┌─────────────────────────┐
                │ dcp-integration-tests   │
                │ (Single comprehensive)  │
                └─────────────────────────┘
                              │
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐   ┌─────────────────┐   ┌──────────────┐
│   Setup      │   │  Health Checks  │   │ Load Images  │
│   • Checkout │   │  • DCP Issuer   │   │ • Connector  │
│   • Download │   │  • Connector A  │   │ • DCP Issuer │
│   • Load img │   │  • Connector B  │   │              │
└──────────────┘   └─────────────────┘   └──────────────┘
                              │
                              ▼
                ┌─────────────────────────┐
                │ Start Docker Compose    │
                │ • dcp-issuer            │
                │ • connector-a           │
                │ • connector-b           │
                │ • mongodb               │
                │ • minio                 │
                └─────────────────────────┘
                              │
                              ▼
        ┌─────────────────────────────────────────┐
        │           TEST EXECUTION FLOW           │
        └─────────────────────────────────────────┘
                              │
                              ▼
        ╔═════════════════════════════════════════╗
        ║ STEP 1: Obtain Verifiable Credentials  ║
        ║ Collection: dcp-gha-tests              ║
        ║ Purpose: Get VCs for both connectors   ║
        ║ Status: MANDATORY (must succeed)        ║
        ╚═════════════════════════════════════════╝
                              │
                    ┌─────────┴─────────┐
                    │                   │
                    ▼                   ▼
              [SUCCESS]           [FAILURE]
                    │                   │
                    │                   └──> ❌ STOP WORKFLOW
                    │                        "Cannot proceed without VCs"
                    │
                    ▼
        ┌─────────────────────────────────────────┐
        │ STEP 2: Run Negotiation Tests          │
        │ Collection: negotiation-api-without-    │
        │             counteroffer-tests          │
        │ Purpose: Test DSP negotiation with DCP  │
        │ Uses: VCs from Step 1                   │
        └─────────────────────────────────────────┘
                    │
                    ▼
        ┌─────────────────────────────────────────┐
        │ STEP 3: Run Data Transfer Tests        │
        │ Collection: datatransfer-api-http-pull  │
        │ Purpose: Test DSP data transfer with DCP│
        │ Uses: VCs from Step 1                   │
        └─────────────────────────────────────────┘
                    │
                    ▼
        ┌─────────────────────────────────────────┐
        │      Collect Logs & Cleanup             │
        │ • DCP Issuer logs                       │
        │ • Connector A logs                      │
        │ • Connector B logs                      │
        │ • MongoDB logs                          │
        │ • Stop Docker Compose                   │
        └─────────────────────────────────────────┘
                    │
                    ▼
        ┌─────────────────────────────────────────┐
        │      Evaluate Test Results              │
        │ • Check Step 1 outcome                  │
        │ • Check Step 2 outcome                  │
        │ • Check Step 3 outcome                  │
        │ • Fail if any step failed               │
        └─────────────────────────────────────────┘
                    │
            ┌───────┴────────┐
            │                │
            ▼                ▼
      [ALL PASSED]    [ANY FAILED]
            │                │
            ▼                ▼
        ✅ SUCCESS       ❌ FAILURE
```

---

## Test Dependencies

```
                ┌──────────────────────┐
                │   DCP Issuer         │
                │   (Credential Store) │
                └──────────────────────┘
                          │
                          │ Issues VCs
                          ▼
        ┌─────────────────────────────────┐
        │        STEP 1                   │
        │  Obtain Verifiable Credentials  │
        │                                 │
        │  Connector A ←── VC ──→ Issuer │
        │  Connector B ←── VC ──→ Issuer │
        └─────────────────────────────────┘
                          │
                          │ VCs stored in connectors
                          │
                ┌─────────┴─────────┐
                │                   │
                ▼                   ▼
    ┌───────────────────┐   ┌──────────────────┐
    │    STEP 2         │   │    STEP 3        │
    │  Negotiation      │   │  Data Transfer   │
    │                   │   │                   │
    │  Uses VC from     │   │  Uses VC from    │
    │  Step 1 for DCP   │   │  Step 1 for DCP  │
    │  authentication   │   │  authentication  │
    └───────────────────┘   └──────────────────┘
```

---

## Service Communication

```
┌──────────────────────────────────────────────────────────┐
│               Docker Compose Network                      │
│                                                           │
│  ┌─────────────┐      ┌─────────────┐                   │
│  │ Connector A │◄────►│ Connector B │                   │
│  │  :8080      │      │  :8080      │                   │
│  └─────────────┘      └─────────────┘                   │
│         │                     │                          │
│         │                     │                          │
│         └──────────┬──────────┘                          │
│                    │                                     │
│                    ▼                                     │
│          ┌──────────────────┐                           │
│          │   DCP Issuer     │                           │
│          │    :8084         │                           │
│          └──────────────────┘                           │
│                    │                                     │
│          ┌─────────┴─────────┐                          │
│          │                   │                          │
│          ▼                   ▼                          │
│   ┌────────────┐      ┌───────────┐                    │
│   │  MongoDB   │      │   MinIO   │                    │
│   │  :27017    │      │   :9000   │                    │
│   └────────────┘      └───────────┘                    │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

## Timeline (Estimated)

```
Time    Activity
──────  ────────────────────────────────────────────
0:00    ├─ Start workflow (parallel)
        │
0:00    ├─ Build Connector Image (5-10 min)
0:00    └─ Build DCP Issuer Image (3-5 min)
        │
10:00   ├─ Both builds complete
        │
10:00   ├─ dcp-integration-tests starts
10:00   │  ├─ Load images (30s)
10:30   │  ├─ Start Docker Compose (45s)
11:15   │  ├─ Health checks (30s)
11:45   │  │
11:45   │  ├─ STEP 1: Obtain VCs (30s)
12:15   │  │  └─ Check: SUCCESS ✅
        │  │
12:15   │  ├─ STEP 2: Negotiation (2-5 min)
17:15   │  │
17:15   │  ├─ STEP 3: Data Transfer (2-5 min)
22:15   │  │
22:15   │  ├─ Collect logs (30s)
22:45   │  └─ Evaluate results
        │
23:00   └─ Workflow complete
```

**Total Time:** ~15-30 minutes

---

## Success vs Failure Paths

### Success Path ✅

```
Step 1: Obtain VCs
  └─> SUCCESS ✅
       │
Step 2: Negotiation
  └─> SUCCESS ✅
       │
Step 3: Data Transfer  
  └─> SUCCESS ✅
       │
Final Result: ✅ ALL TESTS PASSED
```

### Failure Path: Step 1 Fails ❌

```
Step 1: Obtain VCs
  └─> FAILURE ❌
       │
       └─> STOP IMMEDIATELY
           "Cannot proceed without VCs"
           
Step 2: SKIPPED (not run)
Step 3: SKIPPED (not run)

Final Result: ❌ WORKFLOW FAILED
```

### Failure Path: Step 2 or 3 Fails ❌

```
Step 1: Obtain VCs
  └─> SUCCESS ✅
       │
Step 2: Negotiation
  └─> FAILURE ❌
       │
       └─> CONTINUE (collect results)
           │
Step 3: Data Transfer  
  └─> RUNS (may pass or fail)
       │
Final Result: ❌ WORKFLOW FAILED
              (due to Step 2 failure)
```

---

## File Locations

```
.github/workflows/
└── dcp-tests.yml ...................... Main workflow file

ci/docker/
├── docker-compose-dcp.yml ............. Service definitions
├── .env ............................... Environment variables
└── test-cases/
    ├── dcp-obtain-vc/
    │   └── dcp-gha-tests.postman_collection.json
    ├── negotiation-api-without-counteroffer-tests/
    │   └── negotiation-api-without-counteroffer-tests.json
    └── datatransfer-api-http-pull-tests/
        └── datatransfer-api-http-pull-tests.json
```

---

## Key Points

1. **Sequential Execution** ⚠️
   - Tests run one after another, not in parallel
   - VCs from Step 1 available to Steps 2 & 3

2. **Mandatory Step 1** 🔒
   - If Step 1 fails, workflow stops
   - Steps 2 & 3 require VCs to function

3. **Single Service Instance** 💡
   - Docker services start once
   - All tests share the same instances
   - More efficient than separate jobs

4. **Clear Dependencies** 📋
   - Step 2 depends on Step 1 (needs VCs)
   - Step 3 depends on Step 1 (needs VCs)
   - Explicit in step names and comments

5. **Comprehensive Logging** 📝
   - All service logs collected
   - Available even if tests fail
   - Aids in debugging

