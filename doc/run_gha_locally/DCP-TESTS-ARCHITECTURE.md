# DCP Integration Tests - Visual Architecture

## Workflow Execution Flow

```
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                        GitHub Actions Trigger                             ┃
┃  • Push to: feature/*, hotfix/*, fix/*, develop, main                    ┃
┃  • Pull Request to: develop, main                                         ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
                                    ↓
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                           BUILD PHASE (Parallel)                          ┃
┃                              ~5 minutes total                             ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
           ↓                                              ↓
┌─────────────────────────────┐            ┌─────────────────────────────┐
│  build-connector-image      │            │  build-dcp-issuer-image     │
│  ─────────────────────────  │            │  ─────────────────────────  │
│                             │            │                             │
│  1. Checkout repo           │            │  1. Checkout repo           │
│  2. Setup JDK 17            │            │  2. Setup JDK 17            │
│  3. Maven cache             │            │  3. Maven cache             │
│  4. mvn clean verify        │            │  4. mvn clean package       │
│     (Full build ~5 min)     │            │     (Module only ~2 min)    │
│  5. Docker login (GHCR)     │            │  5. Setup Docker Buildx     │
│  6. Build Docker image      │            │  6. Build Docker image      │
│  7. Push to GHCR            │            │  7. Save as TAR file        │
│     ghcr.io/.../...::test   │            │     /tmp/dcp-issuer-*.tar   │
│                             │            │  8. Upload artifact         │
│  Permissions:               │            │     retention: 1 day        │
│  • contents: read           │            │                             │
│  • packages: write          │            │  Permissions:               │
│  • attestations: write      │            │  • contents: read           │
│  • id-token: write          │            │                             │
└─────────────────────────────┘            └─────────────────────────────┘
           ↓                                              ↓
           └────────────────────┬─────────────────────────┘
                                ↓
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                           TEST PHASE (Parallel)                           ┃
┃                              ~7 minutes total                             ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
           ↓                                              ↓
┌─────────────────────────────┐            ┌─────────────────────────────┐
│ dcp-credential-issuance     │            │ dcp-verifiable-presentation │
│ ─────────────────────────── │            │ ─────────────────────────── │
│                             │            │                             │
│  1. Checkout repo           │            │  1. Checkout repo           │
│  2. Download DCP artifact   │            │  2. Download DCP artifact   │
│  3. Load Docker image       │            │  3. Load Docker image       │
│  4. Verify images           │            │  4. Verify images           │
│  5. Start docker-compose    │            │  5. Start docker-compose    │
│     docker-compose-dcp.yml  │            │     docker-compose-dcp.yml  │
│  6. Wait 45s                │            │  6. Wait 45s                │
│  7. Health checks (10x5s)   │            │  7. Health checks (10x5s)   │
│  8. Verify containers       │            │  8. Verify containers       │
│  9. Run Newman tests        │            │  9. Run Newman tests        │
│     credential-issuance     │            │     vp-tests                │
│ 10. Collect logs (always)   │            │ 10. Collect logs (always)   │
│ 11. Stop & cleanup          │            │ 11. Stop & cleanup          │
│                             │            │                             │
│  Test Collection:           │            │  Test Collection:           │
│  • DCP Issuer health        │            │  • Connector A health       │
│  • DID document             │            │  • Connector B health       │
│  • Credential request       │            │  • Catalog with VP          │
└─────────────────────────────┘            └─────────────────────────────┘
```

## Docker Compose Architecture (docker-compose-dcp.yml)

```
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                           Network Architecture                            ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

    network-a                                          network-b
    ─────────                                          ─────────
        │                                                  │
        │                                                  │
   ┌────┴────┐                                        ┌───┴─────┐
   │         │                                        │         │
   │  MinIO  │◄───────────────────────────────────────►│  MinIO  │
   │ :9000   │        (Connected to both networks)    │ :9000   │
   │ :9001   │                                        │ :9001   │
   └─────────┘                                        └─────────┘
        │                                                  │
        │                                                  │
   ┌────┴────────┐                                   ┌────┴────────┐
   │             │                                   │             │
   │ DCP Issuer  │◄──────────────────────────────────►│ DCP Issuer  │
   │  :8084      │   (Connected to both networks)   │  :8084      │
   │             │                                   │             │
   │ Health:     │                                   │ Health:     │
   │ /actuator   │                                   │ /actuator   │
   │ /health     │                                   │ /health     │
   └─────────────┘                                   └─────────────┘
        │                                                  │
        │                                                  │
   ┌────┴────────┐                                   ┌────┴────────┐
   │             │                                   │             │
   │ Connector A │                                   │ Connector B │
   │  :8080      │                                   │  :8090      │
   │             │                                   │             │
   │ Depends on: │                                   │ Depends on: │
   │ • MongoDB A │                                   │ • MongoDB B │
   │ • MinIO     │                                   │ • MinIO     │
   │ • DCP Issuer│                                   │ • DCP Issuer│
   └─────────────┘                                   └─────────────┘
        │                                                  │
        │                                                  │
   ┌────┴────────┐                                   ┌────┴────────┐
   │             │                                   │             │
   │ MongoDB A   │                                   │ MongoDB B   │
   │  :27017     │                                   │  :27018     │
   │             │                                   │             │
   └─────────────┘                                   └─────────────┘
```

## Service Startup Sequence

```
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                         Service Startup Timeline                          ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

Time    Service              Status       Health Check
────────────────────────────────────────────────────────────────────────────
t=0s    docker-compose up    Starting     N/A
        ─────────────────    ────────     ───────

t=2s    MongoDB A            Running      N/A (no health check)
        MongoDB B            Running      N/A (no health check)

t=5s    MinIO                Starting     Waiting...
                                          curl http://localhost:9000/minio/health/live
                                          Interval: 30s, Timeout: 20s, Retries: 3

t=15s   MinIO                Healthy      ✓ Health check passed

t=15s   MinIO-init           Running      Creates buckets and exits
                             Exited(0)    

t=20s   DCP Issuer           Starting     Waiting...
                                          wget http://localhost:8084/actuator/health
                                          Interval: 30s, Timeout: 10s, Retries: 3
                                          Start period: 40s

t=25s   Connector A          Starting     N/A (no health check)
        Connector B          Starting     N/A (no health check)

t=40s   DCP Issuer           Running      Health check starting...

t=60s   DCP Issuer           Healthy      ✓ Health check passed

t=60s   All Services         Running      Ready for tests
        ────────────         ───────      ───────────────

WORKFLOW HEALTH CHECK (additional)
────────────────────────────────────────────────────────────────────────────
t=45s   Wait period          Sleeping     45 seconds initial wait
t=90s   Manual health check  Running      10 retries × 5s = 50s max
        curl :8084/health    ✓ Pass       DCP Issuer confirmed healthy
```

## Artifact Lifecycle

```
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                         Artifact Management                               ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

1. BUILD PHASE
   ────────────
   
   build-dcp-issuer-image job:
   
   Maven Build          Docker Build         Save Image         Upload Artifact
   ───────────          ────────────         ──────────         ───────────────
   mvn clean      ──►   docker build   ──►   Save as      ──►   Upload to GitHub
   package              (no push)            TAR file           Artifacts API
   
   dcp/dcp-issuer/      dcp-issuer:test      /tmp/dcp-         Name: dcp-issuer-image
   target/*.jar                              issuer-*.tar       Size: ~150 MB
                                                                Retention: 1 day
                                                                Scope: Workflow run

2. TEST PHASE
   ───────────
   
   Test jobs (parallel):
   
   Download           Load into Docker     Use in Compose      Cleanup
   ────────           ────────────────     ──────────────      ───────
   Download from ──►  docker load     ──►  docker compose ──► Tests complete,
   GitHub              from TAR             up -d               artifact auto-
   Artifacts API                                                deleted after 1 day
   
   /tmp/dcp-          dcp-issuer:test      Running in          
   issuer-*.tar       in Docker daemon     containers          

3. LIFECYCLE
   ─────────
   
   Time          Artifact Status
   ──────────────────────────────────────────────────────────────────────────
   Build time    Created and uploaded
   Test time     Downloaded and used (multiple jobs can download same artifact)
   +24 hours     Automatically deleted by GitHub
   
   Storage Impact:
   • Max size: ~150 MB
   • Typical concurrent runs: 1-2
   • Total storage: ~150-300 MB (negligible)
```

## Permission Model

```
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                        Least Privilege Permissions                        ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

Workflow Level (default):
─────────────────────────
permissions:
  contents: read       ✓ Read repository code
  packages: write      ✓ Publish to GHCR (for connector image)


Job: build-connector-image
───────────────────────────
permissions:
  contents: read       ✓ Read repository code
  packages: write      ✓ Push Docker image to GHCR
  attestations: write  ✓ Create build attestations
  id-token: write      ✓ Generate OIDC tokens

Purpose: Needs to publish connector image to registry


Job: build-dcp-issuer-image
────────────────────────────
permissions:
  contents: read       ✓ Read repository code

Purpose: Only needs to read code and build locally
Note: Does NOT need packages:write (no registry push)


Jobs: Test Jobs (credential-issuance, vp-tests)
────────────────────────────────────────────────
permissions: inherited from workflow (contents: read, packages: write)

Purpose: Need to read code and potentially pull published connector image
Note: packages:write inherited but not used in test jobs
```

## Test Collections Structure

```
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                            Test Organization                              ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

ci/docker/test-cases/
│
├── dcp-credential-issuance-tests/
│   └── dcp-credential-issuance-tests.json
│       │
│       ├── Test 1: Health Check - DCP Issuer
│       │   GET http://localhost:8084/actuator/health
│       │   Validates: status == "UP"
│       │
│       ├── Test 2: Get DID Document - DCP Issuer
│       │   GET http://localhost:8084/.well-known/did.json
│       │   Validates: @context, id properties exist
│       │
│       └── Test 3: Request Credential - Connector A
│           POST http://localhost:8084/credentials/issue
│           Body: { credentialType, holderDid }
│           Validates: response contains credential
│
└── dcp-verifiable-presentation-tests/
    └── dcp-verifiable-presentation-tests.json
        │
        ├── Test 1: Health Check - Connector A
        │   GET http://localhost:8080/actuator/health
        │   Validates: status == 200
        │
        ├── Test 2: Health Check - Connector B
        │   GET http://localhost:8090/actuator/health
        │   Validates: status == 200
        │
        └── Test 3: Request Catalog with VP - Connector A to B
            POST http://localhost:8090/catalog/request
            Headers: Authorization: Bearer {{vp_token}}
            Validates: response contains catalog, dcat:dataset

Note: These are PLACEHOLDER tests. Expand with actual DCP test scenarios.
```

## Error Handling Flow

```
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                         Error Handling Strategy                           ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

Test Execution:
───────────────

Run Newman Tests
      │
      ├─── Success ──► Continue to logs collection
      │                      │
      │                      ├─── Collect logs (if: always())
      │                      │         │
      │                      │         ├─── DCP Issuer logs
      │                      │         ├─── Connector A logs
      │                      │         └─── Connector B logs
      │                      │
      │                      └─── Stop services (if: always())
      │                               │
      │                               └─── docker compose down -v
      │
      └─── Failure ──► Set continue-on-error: true
                              │
                              ├─── Dump Docker logs (if: failure())
                              │         │
                              │         └─── jwalton/gh-docker-logs@v2
                              │
                              ├─── Collect logs (if: always())
                              │         │
                              │         ├─── DCP Issuer logs
                              │         ├─── Connector A logs
                              │         └─── Connector B logs
                              │
                              ├─── Stop services (if: always())
                              │         │
                              │         └─── docker compose down -v
                              │
                              └─── Fail job (if: newman-tests.outcome == 'failure')
                                        │
                                        └─── exit 1

Guarantees:
• Logs ALWAYS collected (success or failure)
• Services ALWAYS stopped (prevents resource leaks)
• Clear failure indication (exit 1)
• Comprehensive debugging information
```

## Resource Cleanup Guarantee

```
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                         Cleanup Strategy                                  ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

Docker Resources:
─────────────────

- name: Stop Docker Compose services
  if: always()                              ◄─── ALWAYS runs
  run: |
    docker compose \
      -f ./ci/docker/docker-compose-dcp.yml \
      --env-file ./ci/docker/.env \
      down -v                                 ◄─── Remove volumes too

Result:
• Containers stopped and removed
• Networks removed
• Volumes removed (-v flag)
• Runs regardless of test success/failure
• Prevents accumulation of stopped containers


GitHub Artifacts:
─────────────────

- name: Upload DCP Issuer Docker image artifact
  uses: actions/upload-artifact@v4
  with:
    name: dcp-issuer-image
    path: /tmp/dcp-issuer-image.tar
    retention-days: 1                         ◄─── Auto-delete after 1 day

Result:
• Artifact automatically deleted after 24 hours
• No manual cleanup required
• Prevents storage accumulation
• Configurable retention period


Workflow Concurrency:
─────────────────────

concurrency:
  group: dcp-tests-${{ github.ref }}
  cancel-in-progress: true                    ◄─── Cancel old runs

Result:
• New push cancels previous workflow run
• Prevents multiple concurrent runs for same branch
• Saves CI minutes
• Reduces resource contention
```

## Key Advantages Over Registry Approach

```
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                Artifact-Based vs Registry-Based Comparison                ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

Aspect                Artifact-Based (✓)        Registry-Based (✗)
──────────────────────────────────────────────────────────────────────────────
Security              No credentials needed     Requires registry credentials
                      Scoped to workflow        Public/private access issues
                      Auto-cleanup             Manual cleanup needed

Performance           No network push/pull      Network overhead for push/pull
                      ~30s faster per run       Slower due to registry ops
                      Efficient local storage   External registry dependency

Cost                  GitHub artifact storage   Registry storage costs
                      Minimal (~150 MB × 1d)    Accumulates over time
                      Included in GitHub        May require paid plan

Complexity            Simple: save → upload     Complex: auth → push → pull
                      2 steps                   3+ steps with auth

Maintenance           Auto-cleanup after 1 day  Manual image pruning
                      Zero maintenance          Regular cleanup scripts

Visibility            Private to workflow       Potentially public
                      No accidental exposure    Risk of exposure

Registry Pollution    N/A (no registry)         Test tags accumulate
                      Clean registry            Requires cleanup jobs

Failure Impact        Isolated to workflow      May affect registry
                      No external state         Registry state changes

Use Case Fit          Perfect for CI testing    Better for long-term images
                      Temporary images          Deployment images
                      Build artifacts           Production releases
```

---

## Quick Reference

**Files Created:**
1. `.github/workflows/dcp-tests.yml` - Main workflow
2. `ci/docker/docker-compose-dcp.yml` - Compose config with DCP issuer
3. `ci/docker/test-cases/dcp-credential-issuance-tests/*.json` - Test collection
4. `ci/docker/test-cases/dcp-verifiable-presentation-tests/*.json` - Test collection
5. `ci/docker/DCP-TESTS-README.md` - Detailed documentation
6. `DCP-TESTS-IMPLEMENTATION-SUMMARY.md` - Implementation summary

**Key Commands:**
```bash
# Local testing
docker compose -f ci/docker/docker-compose-dcp.yml --env-file ci/docker/.env up -d
newman run ci/docker/test-cases/dcp-credential-issuance-tests/dcp-credential-issuance-tests.json
docker compose -f ci/docker/docker-compose-dcp.yml down -v

# Check workflow
gh workflow view dcp-tests.yml
gh run list --workflow=dcp-tests.yml

# Trigger manually
gh workflow run dcp-tests.yml
```

**Estimated Times:**
- Build phase: ~5 minutes (parallel)
- Test phase: ~7 minutes (parallel)
- Total: ~12 minutes per workflow run

