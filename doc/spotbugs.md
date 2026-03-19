# SpotBugs Static Analysis

## Overview

[SpotBugs](https://spotbugs.github.io/) is a bytecode-level static analysis tool that detects common bug patterns in Java code.
It is integrated into this project together with the [Find Security Bugs](https://find-sec-bugs.github.io/) plugin, which adds approximately 150 additional security-focused detectors covering OWASP Top 10 and other vulnerability classes.

The combination provides two levels of analysis in a single scan:

| Tool | Focus |
|------|-------|
| SpotBugs | General correctness bugs (null dereferences, resource leaks, bad practices, …) |
| Find Security Bugs | Security vulnerabilities (injection, XXE, path traversal, weak crypto, …) |

---

## Why It Is Here

Dependency version upgrades and code changes can introduce regressions that are not caught by unit or integration tests.
SpotBugs provides a complementary safety net by analysing compiled bytecode independently of runtime behaviour.

Key motivations:

- **Security hardening** — Find Security Bugs detects vulnerability patterns before they reach production.
- **Code quality gate** — the `--gate` mode can block a build when findings above a configured threshold are detected, making it suitable for CI pipelines.
- **Full-project visibility** — the aggregated report (`target/spotbugs-aggregate.xml`) gives a single-file view across all Maven modules, making triage easier.

---

## Maven Profile

SpotBugs is activated through the `spotbugs` Maven profile and is **not** part of the default build.
The profile is defined in the root `pom.xml` and applies to all modules.

### Configuration defaults

| Parameter | Default | Description |
|-----------|---------|-------------|
| `spotbugs.effort` | `max` | Analysis depth: `min` / `default` / `max` |
| `spotbugs.threshold` | `medium` | Minimum severity to report: `low` / `medium` / `high` |
| `spotbugs.skipCheck` | `true` | When `true` the build never fails on findings (scan-only mode) |

Override any parameter on the command line:

```
mvn verify -Pspotbugs -Dspotbugs.effort=default -Dspotbugs.threshold=high
```

### False-positive suppressions

Findings that have been triaged and confirmed as false positives are suppressed via:

```
scripts/ci/spotbugs-exclude.xml
```

Add new `<Match>` entries to that file after reviewing a finding.

---

## How It Works — Two-Step Design

SpotBugs is intentionally split into two Maven executions per module:

```
verify phase
  └─ spotbugs-scan  (goal: spotbugs)   ← always runs, never fails
  └─ spotbugs-gate  (goal: check)      ← reads the XML; fails if skipCheck=false
```

**Why two steps?**

Maven's reactor is sequential.
If `spotbugs:check` were bound directly to `verify` without the `skipCheck` guard, the reactor would abort on the first module with findings and the remaining modules would never be scanned.

By keeping `skipCheck=true` (the default) during the scan phase, every module is analysed and its report written to `target/spotbugsXml.xml`.
The gate check is only activated explicitly, either via the property flag or via the helper scripts.

### Per-module reports

After running with `-Pspotbugs`, each module produces:

```
<module>/target/spotbugsXml.xml
```

### Aggregate report

The root module additionally merges all per-module XML files into:

```
target/spotbugs-aggregate.xml
```

This is produced automatically by the `maven-antrun-plugin` execution `spotbugs-aggregate` bound to the `verify` phase in the root POM, and only runs in the root aggregator (`inherited=false`).

---

## Running SpotBugs

### Using the helper scripts (recommended)

Two wrapper scripts are provided at the project root. They handle the two-step scan/gate workflow automatically and print a summary of all report locations.

#### Linux / macOS

```bash
# Scan only — never fails, writes reports
./spotbugs-scan.sh

# Gate mode — scan all modules, then fail if findings are found
./spotbugs-scan.sh --gate
```

#### Windows (Command Prompt)

```cmd
:: Scan only — never fails, writes reports
spotbugs-scan.cmd

:: Gate mode — scan all modules, then fail if findings are found
spotbugs-scan.cmd --gate
```

> **Note:** Run the scripts from the **project root directory**.

---

### Using Maven directly

#### Scan only (safe, never fails)

```bash
mvn verify -Pspotbugs
```

#### Gate mode (fails after all modules are scanned)

```bash
# Step 1 — scan (never fails)
mvn verify -Pspotbugs -Dspotbugs.skipCheck=true

# Step 2 — check all reports and fail if findings found (-fae = fail at end)
mvn spotbugs:check -Pspotbugs -Dspotbugs.skipCheck=false -fae
```

The `-fae` flag (`--fail-at-end`) is essential in gate mode: it ensures Maven checks every module before reporting failures, instead of stopping at the first one.

---

## HTML Report (per module)

A human-readable HTML report can be generated for each module using the Maven Site plugin:

```bash
mvn site -Pspotbugs
```

Output: `<module>/target/site/spotbugs.html`

---

## CI Integration

To integrate SpotBugs into a CI pipeline:

1. Run the scan unconditionally on every build:
   ```bash
   mvn verify -Pspotbugs
   ```
2. Archive `**/target/spotbugsXml.xml` and `target/spotbugs-aggregate.xml` as build artefacts.
3. Optionally enable the gate to break the build on new findings:
   ```bash
   mvn spotbugs:check -Pspotbugs -Dspotbugs.skipCheck=false -fae
   ```

