# Integration Tests Workflow - Visual Architecture

Diagrams for `.github/workflows/integration-tests.yml`. The text reference is
[INTEGRATION_TESTS_GUIDE.md](INTEGRATION_TESTS_GUIDE.md).

## Job graph

```
                     push to feature/* | hotfix/* | fix/*
                                     │
             ┌───────────────────────┴────────────────────────┐
             ▼                                                ▼
┌──────────────────────────────┐                ┌──────────────────────────────┐
│ build-connector-image        │                │ build-dcp-issuer-image       │
│ 1. checkout                  │                │ 1. checkout                  │
│ 2. JDK 21 + Maven cache      │                │ 2. JDK 21 + Maven cache      │
│ 3. mvn clean verify          │                │ 3. mvn package dcp/ (no test)│
│ 4. docker build + push       │                │ 4. docker build + push       │
│    :ci-<run>-<sha>, :test    │                │    :ci-<run>-<sha>, :test    │
└──────────────┬───────────────┘                └───────────────┬──────────────┘
               └──────────────────────┬─────────────────────────┘
                                      ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ integration-tests   (matrix: 12 jobs, fail-fast: false)                      │
│                                                                              │
│ standard (docker-compose.yml)          dcp (docker-compose-dcp.yml)          │
│ ├─ api-endpoints-tests                 ├─ negotiation-without-counteroffer   │
│ ├─ api-endpoints-tenant-tests          ├─ datatransfer-http-pull             │
│ ├─ dataset-api-tests                   ├─ datatransfer-http-push             │
│ ├─ connector-tests                     └─ dcp-credential-issuance            │
│ ├─ negotiation-without-counteroffer                                          │
│ ├─ negotiation-with-counteroffer                                             │
│ ├─ datatransfer-http-pull                                                    │
│ └─ datatransfer-http-push                                                    │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       ▼   if: always()
                         ┌──────────────────────────────┐
                         │ cleanup-images               │
                         │ delete this run's versions   │
                         │ + ci-only orphans older 6 h  │
                         └──────────────────────────────┘
```

## One matrix job

```
START
  ├─► checkout
  ├─► docker compose up -d          IMAGE_TAG=ci-<run>-<sha> → pulls this run's images
  ├─► wait for connector-a :8080 and connector-b :8090   (36 × 10 s)
  ├─► if requires-dcp: wait for dcp-issuer :8084         (10 × 5 s)
  ├─► docker ps -a
  ├─► if requires-vc: newman dcp-obtain-vc collection    (must pass)
  ├─► newman <collection>
  ├─► if failure(): dump logs of every compose service
  └─► always: docker compose down -v
END
```

## Image flow and isolation

```
 branch A run 101                      GHCR                        branch B run 102
 ────────────────                      ────                        ────────────────
 build ──push──► dsp-true-connector:ci-101-aaa  (+ :test)
                                                         ◄──push── build
                 dsp-true-connector:ci-102-bbb  (+ :test, moved here)
 tests ──pull──► :ci-101-aaa                                       tests ──pull──► :ci-102-bbb
 cleanup ─────►  delete :ci-101-aaa   (no longer holds :test)
                                                                   cleanup ─────►  keep :ci-102-bbb
                                                                                   (still holds :test)
 a later run, ≥ 6 h on ─────────────► delete :ci-102-bbb once :test has moved on (orphan)
```

Each run's tests only ever pull the tag that run built, so a parallel run on another branch cannot
change what is being tested. `:test` keeps pointing at the latest build for TCK and Terraform.

## Compose stacks

```
STANDARD  ci/docker/docker-compose.yml
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────────┐
│ connector-a │  │ mongodb-a   │  │ connector-b │  │ mongodb-b   │  │ s3storage    │
│ :8080       │  │ :27017      │  │ :8090       │  │ :27018      │  │ RustFS :9000 │
└─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘  └──────────────┘

DCP  ci/docker/docker-compose-dcp.yml  =  STANDARD  +
┌─────────────┐  ┌─────────────┐
│ dcp-issuer  │  │ mongodb     │
│ :8084       │  │ :27019      │
└─────────────┘  └─────────────┘
```

## DCP mode test order

```
1. dcp-obtain-vc/dcp-gha-tests.postman_collection.json
   Connector A and B request credentials from the DCP Issuer and store them.
   Must pass, otherwise the job fails before the real test.
2. The matrix collection (negotiation, pull, push)
   DSP calls between the connectors authenticate with the credentials from step 1.

dcp-credential-issuance-tests skips step 1: issuing credentials is what it tests.
```
