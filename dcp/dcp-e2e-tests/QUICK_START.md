# DCP E2E Tests — Quick Start

> Full documentation: [README.md](README.md)

---

## Run via Maven

```bash
# Spring (embedded apps, localhost) — default for local development
mvn verify -DtestGroup=spring -pl dcp-e2e-tests

# Docker (containers, requires Docker Desktop + pre-built JARs)
mvn clean install -DskipTests
mvn verify -DtestGroup=e2e -pl dcp-e2e-tests
```

---

## Run from IntelliJ IDEA

### Option A — Maven run configuration (recommended)

1. **Run → Edit Configurations → + → Maven**
2. Fill in:

| Field | Spring env | Docker env |
|---|---|---|
| Working directory | `dcp/dcp-e2e-tests` | `dcp/dcp-e2e-tests` |
| Command line | `verify -DtestGroup=spring` | `verify -DtestGroup=e2e` |

3. Click **Run**.

### Option B — Run a single test class directly

1. Open e.g. `DcpCredentialFlowTestE2E`
2. **Run → Edit Configurations → (select the test) → VM options:**
   ```
   -Dtest.environment=spring
   ```
3. Click the green ▶ button.

---

## Run from Eclipse

### Option A — Maven run configuration (recommended)

1. **Run → Run Configurations → Maven Build → New**
2. Fill in:

| Field | Value |
|---|---|
| Base directory | `${project_loc:dcp-e2e-tests}` |
| Goals | `verify` |
| Parameter name | `testGroup` |
| Parameter value | `spring` (or `e2e` for Docker) |

3. Click **Run**.

### Option B — Run a single test class directly

1. Right-click the test class → **Run As → JUnit Test**
2. **Run → Run Configurations → JUnit → (your test) → Arguments tab**
3. Add to **VM arguments:**
   ```
   -Dtest.environment=spring
   ```
4. Click **Run**.

---

## Which environment should I use?

| Situation | Use |
|---|---|
| Local development / debugging | `spring` — faster, no Docker build needed |
| CI pipeline / full integration | `e2e` (Docker) — production-like, isolated containers |
