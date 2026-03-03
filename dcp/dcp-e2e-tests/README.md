# DCP E2E Tests

End-to-end integration tests for the DCP protocol. Tests run against two services:
- **Issuer** — issues Verifiable Credentials
- **HolderVerifier** — acts as both Holder and Verifier

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Test Environments](#test-environments)
- [Running Tests](#running-tests)
- [Property File Structure](#property-file-structure)
- [Architecture](#architecture)
- [Modules](#modules)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 17+ |
| Maven | 3.8+ |
| Docker Desktop | Any recent version (Docker environment only) |

---

## Test Environments

Two environments are supported. The environment is selected via the `-DtestGroup` Maven property.

### Spring Environment (default — local development)

Services start as embedded Spring Boot applications inside the same JVM as the test.
MongoDB runs in a Testcontainer. **Docker is only needed for MongoDB**, not for the services themselves.

```
┌──────────────────────────────────────────────────────┐
│                      Test JVM                        │
│                                                      │
│  ┌──────────────────┐     ┌────────────────────────┐ │
│  │  IssuerApplication│     │ HolderVerifierTest     │ │
│  │  :8082           │     │ Application :8081       │ │
│  └──────────────────┘     └────────────────────────┘ │
│                                                      │
│            ┌─────────────────────┐                   │
│            │  MongoDB Container  │                   │
│            │  (Testcontainers)   │                   │
│            └─────────────────────┘                   │
└──────────────────────────────────────────────────────┘
```

**Hostnames:** `localhost`

### Docker Environment (CI / integration)

All services run in isolated Docker containers on a shared network.
Requires pre-built JARs (from `mvn install`).

```
┌──────────────────────────────────────────────────────┐
│                   Docker Network                     │
│                                                      │
│  ┌──────────┐  ┌───────────────────┐  ┌───────────┐ │
│  │  issuer  │  │  holderverifier   │  │  mongodb  │ │
│  │  :8082   │  │  :8081            │  │  :27017   │ │
│  └──────────┘  └───────────────────┘  └───────────┘ │
└──────────────────────────────────────────────────────┘
```

**Hostnames:** `issuer`, `holderverifier`, `mongodb`

---

## Running Tests

### Maven (command line)

```bash
# Spring environment — embedded apps, localhost (no Docker needed for services)
mvn verify -DtestGroup=spring -pl dcp/dcp-e2e-tests

# Docker environment — requires Docker Desktop and pre-built JARs
mvn clean install -DskipTests          # build JARs first
mvn verify -DtestGroup=e2e -pl dcp/dcp-e2e-tests
```

### IntelliJ IDEA

1. Open **Run → Edit Configurations**
2. Click **+** → **Maven**
3. Set the following:

| Field | Value |
|---|---|
| **Working directory** | `$MODULE_DIR$` (the `dcp-e2e-tests` directory) |
| **Command line** | `verify -DtestGroup=spring` |
| **Name** | `E2E Tests - Spring` |

4. Click **OK** and run the configuration.

Alternatively, to run a single test class directly:
1. Open the test class (e.g. `DcpCredentialFlowTestE2E`)
2. Before running, add the VM option `-Dtest.environment=spring` in the run configuration:
   **Run → Edit Configurations → VM options:** `-Dtest.environment=spring`
3. Run the test normally with the green ▶ button.

### Eclipse

1. Open **Run → Run Configurations**
2. Create a new **Maven Build** configuration
3. Set the following:

| Field | Value |
|---|---|
| **Base directory** | `${project_loc:dcp-e2e-tests}` |
| **Goals** | `verify` |
| **Parameters → Name** | `testGroup` |
| **Parameters → Value** | `spring` |

4. Click **Run**.

To run a single test class directly:
1. Right-click the test class → **Run As → JUnit Test**
2. Open **Run → Run Configurations → JUnit → (your test)**
3. In the **Arguments** tab, add to **VM arguments:** `-Dtest.environment=spring`
4. Click **Run**.

---

## Property File Structure

Properties are split into a **base file** (shared) and **environment-specific override files**.
Spring Boot loads the base profile first, then overlays the environment sub-profile.

### HolderVerifier

| File | Loaded by | Contains |
|---|---|---|
| `application-holderverifier.properties` | Both | Port, keystore, endpoints, service entries, logging |
| `application-holderverifier-spring.properties` | Spring mode | `localhost` DIDs, base URL, issuer location, trusted issuers |
| `application-holderverifier-docker.properties` | Docker mode | `holderverifier`/`issuer` container DIDs, base URL, trusted issuers |

### Issuer

| File | Loaded by | Contains |
|---|---|---|
| `application-issuer.properties` | Both | Port, keystore, MongoDB db, Jackson, logging |
| `application-issuer-spring.properties` | Spring mode | `localhost` DID, base URL, issuer-location |
| `application-issuer-docker.properties` | Docker mode | `issuer` container DID, base URL, issuer-location |

Spring Boot profile activation per environment:

```
Spring: --spring.profiles.active=holderverifier,holderverifier-spring
Docker: --spring.profiles.active=holderverifier,holderverifier-docker
```

---

## Architecture

### Key Classes

| Class | Location | Purpose |
|---|---|---|
| `DcpTestEnvironment` | `src/test/java/.../environment/` | Base class for all E2E tests. Starts services, exposes REST clients and base URLs. |
| `SharedDockerEnvironment` | `src/test/java/.../environment/` | Manages Docker containers via Testcontainers (Docker mode only). |
| `HolderVerifierTestApplication` | `src/main/java/.../` | Spring Boot entry point combining Holder + Verifier for in-process testing. |

### Environment Selection

`DcpTestEnvironment` reads the `test.environment` system property:

| `test.environment` value | Environment started | Default? |
|---|---|---|
| `spring` | Embedded Spring Boot + MongoDB container | ✅ Yes |
| `docker` | Full Docker containers via Testcontainers | No |

The value is passed automatically by Maven via `<systemPropertyVariables>` in the Failsafe plugin configuration inside each Maven profile (`spring-tests`, `docker-tests`).

### REST Clients

All test classes that extend `DcpTestEnvironment` get:

| Field | Points to |
|---|---|
| `issuerClient` | Issuer service base URL |
| `holderClient` | HolderVerifier service (holder endpoints) |
| `verifierClient` | HolderVerifier service (verifier endpoints) |
| `issuerBaseUrl` | `http://localhost:8082` or `http://issuer:8082` |
| `holderBaseUrl` | `http://localhost:8081` or `http://holderverifier:8081` |
| `verifierBaseUrl` | Same as `holderBaseUrl` |

---

## Modules

This module (`dcp-e2e-tests`) depends on:

- `dcp-holder` — Holder service logic and auto-configuration
- `dcp-verifier` — Verifier service logic and auto-configuration
- `dcp-issuer` — Issuer service logic
- `dcp-common` — Shared DCP models and services

> **Note:** `dcp-holder`, `dcp-verifier`, and `dcp-common` are **library JARs**.
> They do not contain `application.properties` or keystores in their JARs — those must be provided
> by the consuming application (this module). This prevents classpath pollution when the library
> is used as a dependency.

---

## Troubleshooting

### `Could not resolve placeholder 'dcp.issuer.location'`
The wrong profile was activated, or the environment sub-profile file is missing from `src/test/resources`.
Check that `application-holderverifier-spring.properties` (or `-docker`) exists.

### `Cannot connect to Docker`
Ensure Docker Desktop is running before executing Docker-mode tests.

### `Port already in use`
Another process is using port 8081 or 8082. Stop it or change the port in the property files.

### JARs not found during Docker image build
Run `mvn clean install -DskipTests` from the `dcp/` directory first to build all module JARs.

### Tests time out during container startup
Increase Docker Desktop resource limits (Memory ≥ 4 GB recommended).
