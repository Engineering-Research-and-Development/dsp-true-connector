# SpotBugs + Find Security Bugs & OWASP Dependency-Check

This document explains what was added, why, how it is configured, and how to use it —
both locally and in CI.

---

## Overview

Two new Maven profiles were added to complement the existing SonarQube SAST scan:

| Profile | Tool | What it finds | Command |
|---|---|---|---|
| `-Pspotbugs` | SpotBugs + Find Security Bugs | Bytecode-level security bugs SonarQube misses | `mvn verify -Pspotbugs` |
| `-Powasp` | OWASP Dependency-Check | CVEs in all (transitive) dependencies | `mvn verify -Powasp` |

They can be combined with each other and with `-Psonar`:

```powershell
# All three scanners in one build
mvn verify sonar:sonar -Psonar,spotbugs,owasp -Dsonar.token=<token>
```

---

## Why These Tools on Top of SonarQube?

SonarQube (Community Edition) does excellent source-level taint analysis but has two gaps:

### Gap 1 — Bytecode-level patterns (filled by SpotBugs + Find Security Bugs)

SpotBugs operates on compiled `.class` files rather than source code, so it catches a
different class of bugs. The **Find Security Bugs** plugin adds ~150 security-specific
detectors on top of plain SpotBugs. Notable detectors that are **not** in SonarQube CE:

| Detector | Category | Relevance to this project |
|---|---|---|
| `PREDICTABLE_RANDOM` | Crypto | `java.util.Random` used where `SecureRandom` is needed |
| `STATIC_IV` | Crypto | IV derived from key material (FIND-04 in the findings overview) |
| `PERMISSIVE_JWT` | Auth | JWT `none`-algorithm accepted without validation |
| `SSRF` | Network | SSRF in `HttpClient` / `RestTemplate` calls (relevant to DID resolver — FIND-10) |
| `URLCONNECTION_SSRF_FD` | Network | `URL.openConnection()` with user-controlled input |
| `SPRING_CSRF_UNRESTRICTED_REQUEST_MAPPING` | Spring | `@RequestMapping` missing explicit HTTP method constraint |
| `SPRING_UNVALIDATED_REDIRECT` | Spring | Spring MVC redirect with unvalidated parameter |
| `HARD_CODE_PASSWORD` | Secrets | Hardcoded credentials in Java constants (complements Sonar's S2068) |
| `TRUST_ALL_SSL` | TLS | `TrustManager` that accepts all certificates |
| `WEAK_HOSTNAME_VERIFIER` | TLS | `HostnameVerifier` that always returns `true` |
| `SENSITIVE_DATA_EXPOSURE` | Data | Sensitive values logged or exposed in responses |

### Gap 2 — Dependency CVEs (filled by OWASP Dependency-Check)

SonarQube CE does **not** scan JAR files for known CVEs.
OWASP Dependency-Check resolves every JAR on the classpath (including transitive ones),
looks each up in the **NIST National Vulnerability Database (NVD)**, and reports all
matching CVEs with their CVSS scores. This is the only way to get a complete picture of
third-party risk for libraries like `nimbus-jose-jwt`, `spring-security`, `aws-sdk`, etc.

---

## SpotBugs + Find Security Bugs

### Configuration

All configuration lives in the `spotbugs` profile in the **root `pom.xml`**:

```xml
<profile>
    <id>spotbugs</id>
    ...
    <properties>
        <spotbugs.effort>max</spotbugs.effort>          <!-- min | default | max -->
        <spotbugs.threshold>medium</spotbugs.threshold> <!-- low | medium | high -->
        <spotbugs.skipCheck>true</spotbugs.skipCheck>   <!-- true = report-only (default) -->
    </properties>
</profile>
```

| Property | Default | Meaning |
|---|---|---|
| `spotbugs.effort` | `max` | Analysis depth. `max` = most thorough, slowest |
| `spotbugs.threshold` | `medium` | Report bugs at this confidence and above. `low` = more noise |
| `spotbugs.skipCheck` | `true` | `true` = report-only, all modules always complete. `false` = build fails when findings exceed threshold |

The profile uses **two separate Maven executions** to achieve this:

| Execution | Goal | Behaviour |
|---|---|---|
| `spotbugs-report` | `spotbugs` | Always runs — generates HTML + XML for every module, **never fails** |
| `spotbugs-gate` | `check` | Skipped by default (`skipCheck=true`) — reads the XML and fails the build when activated |

This means `mvn verify -Pspotbugs` will **always scan every module** and produce a complete
report, regardless of how many findings there are. No module is skipped because an earlier
one had violations.

Override properties on the command line:

```powershell
# Default — report-only, all modules scanned, build always succeeds
mvn verify -Pspotbugs

# Gate mode — fail the build if findings exceed the threshold
mvn verify -Pspotbugs -Dspotbugs.skipCheck=false

# Adjust analysis depth and threshold
mvn verify -Pspotbugs -Dspotbugs.effort=default -Dspotbugs.threshold=high

# Gate mode with higher threshold (less noise)
mvn verify -Pspotbugs -Dspotbugs.skipCheck=false -Dspotbugs.threshold=high
```

### Report Locations

After `mvn verify -Pspotbugs`, each sub-module produces two files:

```
<module>/target/spotbugs/spotbugs.html     ← open in browser
<module>/target/spotbugs/spotbugsXml.xml   ← machine-readable
```

For example, the connector module report is at:
`connector/target/spotbugs/spotbugs.html`

### Suppressing False Positives

The exclusion filter is at `scripts/ci/spotbugs-exclude.xml`.

To suppress a finding, add a `<Match>` block. Always include a comment explaining why:

```xml
<FindBugsFilter>
    <!-- Trust-all SSL is intentional in DevTlsConfig — dev profile only, never production -->
    <Match>
        <Bug pattern="TRUST_ALL_SSL"/>
        <Class name="it.eng.connector.configuration.DevTlsConfig"/>
    </Match>
</FindBugsFilter>
```

Available matchers: `<Bug pattern="...">`, `<Class name="...">`, `<Method name="...">`,
`<Field name="...">`. Regex variants: `<Class ~=".*Test$"/>`.

Full reference: https://spotbugs.readthedocs.io/en/latest/filter.html

### Supported Patterns Reference

Find Security Bugs rule catalogue: https://find-sec-bugs.github.io/bugs.htm

---

## OWASP Dependency-Check

### Configuration

All configuration lives in the `owasp` profile in the **root `pom.xml`**:

```xml
<profile>
    <id>owasp</id>
    ...
    <properties>
        <owasp.failBuildOnCVSS>7</owasp.failBuildOnCVSS>
        <owasp.dataDirectory>./target/dependency-check-data</owasp.dataDirectory>
        <owasp.skipCheck>true</owasp.skipCheck>   <!-- true = report-only (default) -->
    </properties>
</profile>
```

| Property | Default | Meaning |
|---|---|---|
| `owasp.failBuildOnCVSS` | `7` | CVSS score threshold for gate mode. `7` = HIGH+, `4` = MEDIUM+, `11` = never fails |
| `owasp.dataDirectory` | `./target/dependency-check-data` | Shared NVD cache — one directory reused across all sub-modules |
| `owasp.skipCheck` | `true` | `true` = report-only, reactor always completes. `false` = fail when CVSS threshold is exceeded |

The profile uses **two executions** on the **root module only** (`inherited=false`):

| Execution | Goal | Behaviour |
|---|---|---|
| `owasp-aggregate-report` | `aggregate` | Always runs — scans **all** sub-modules, produces **one combined report**, never fails |
| `owasp-gate` | `check` | Skipped by default (`skipCheck=true`) — reads the report and fails if CVSS threshold is exceeded |

The `aggregate` goal is key: it runs once on the root, pulls in every sub-module's
classpath automatically, and produces a **single HTML/XML/JSON report** for the entire
project. No per-module fragmentation, no reactor halt mid-way.

### NVD API Key (optional but strongly recommended)

Without an API key, the NVD rate-limits requests to 5 per 30 seconds.
The first run may take **30–60 minutes** without a key, or **5–10 minutes** with one.

Get a free key at: https://nvd.nist.gov/developers/request-an-api-key
(Personal emails are accepted — no .gov or .edu required.)

Pass the key at runtime:

```powershell
mvn verify -Powasp -Dnvd.api.key=<your-key>
```

Or set the environment variable before running Maven:

```powershell
$env:NVD_API_KEY = "<your-key>"
mvn verify -Powasp
```

The NVD data is cached in `target/dependency-check-data/` at the root.
Subsequent runs reuse the cache and only download incremental updates (much faster).

### Running Locally — Step by Step

```powershell
# Default — report-only, full project scanned in one pass, build always succeeds
mvn verify -Powasp -Dnvd.api.key=<your-key>

# Gate mode — fail on HIGH severity (CVSS >= 7)
mvn verify -Powasp -Dowasp.skipCheck=false -Dnvd.api.key=<your-key>

# Gate mode — fail on MEDIUM and above (CVSS >= 4)
mvn verify -Powasp -Dowasp.skipCheck=false -Dowasp.failBuildOnCVSS=4 -Dnvd.api.key=<your-key>
```

### Report Location

After `mvn verify -Powasp` there is **one combined report** in the **root `target/` directory**:

```
dsp-true-connector/
└── target/
    ├── dependency-check/
    │   ├── dependency-check-report.html  ← open this in a browser
    │   ├── dependency-check-report.xml   ← machine-readable (CI tooling)
    │   └── dependency-check-report.json  ← JSON format
    └── dependency-check-data/            ← NVD cache (auto-populated, ~200 MB)
        └── odc.mv.db                     ← reused on subsequent runs (fast)
```

This single report covers every dependency across all 12 sub-modules — there is no
per-module report to hunt down. Just open
`target/dependency-check/dependency-check-report.html` directly in any browser.

> `target/` is already in `.gitignore` — neither the report nor the NVD cache
> will be accidentally committed.

### Suppressing False Positives

The suppression file is at `scripts/ci/owasp-suppressions.xml`.

**When to suppress:**
- The vulnerable code path is not reachable from this project's usage of the library
- The CVE applies to a different artifact with the same groupId/artifactId (CPE mismatch)
- The fix is already present (backport) even if the version number suggests otherwise

**Template** (copy, fill in, add a comment):

```xml
<suppress>
    <notes><![CDATA[
        CVE-XXXX-XXXXX: False positive — the vulnerable code path in <library>
        is not reachable because <reason>.
        Reviewed by: <name>, <date>.
    ]]></notes>
    <packageUrl regex="true">^pkg:maven/com\.example/some\-artifact@.*$</packageUrl>
    <cve>CVE-XXXX-XXXXX</cve>
</suppress>
```

Suppression format reference: https://jeremylong.github.io/DependencyCheck/general/suppressing.html

---

## Triage Workflow

For each finding (SpotBugs or OWASP), decide one of the following:

```
Finding
  ├── True positive
  │     ├── Fix the code / upgrade the dependency   → open a ticket, fix it
  │     └── Accepted risk (won't fix now)           → suppress with justification comment
  └── False positive
        └── Suppress with explanation comment       → add to exclude/suppression file
```

> **Important:** Every suppression entry must have a `<notes>` or comment explaining
> *why* it is suppressed. This is an audit trail — a suppression with no reason is
> indistinguishable from an ignored real vulnerability.

---

## GitHub Actions CI Integration

The workflow `.github/workflows/security-scan.yml` runs all three scanners in parallel jobs.

### Jobs

| Job | Profile | Artifact uploaded |
|---|---|---|
| `sonarqube-sast` | `-Psonar` | SonarQube dashboard (external link) |
| `spotbugs-sast` | `-Pspotbugs` | `spotbugs-reports-<run#>` (HTML per module) |
| `owasp-dc` | `-Powasp` | `owasp-dependency-check-<run#>` (HTML per module) |

### Trigger Matrix

| Trigger | Condition |
|---|---|
| **Manual** (`workflow_dispatch`) | Run at any time from the GitHub Actions UI |
| **Pre-release gate** | Every push to a `release/**` branch |
| **Scheduled weekly** | Every Monday at 06:00 UTC |

### Manual Trigger Inputs

When triggering manually from the Actions UI, three extra inputs are available:

| Input | Default | Description |
|---|---|---|
| `fail_on_quality_gate` | `true` | Whether to fail if the SonarQube Quality Gate fails |
| `fail_on_spotbugs` | `true` | `true` → activates gate (`-Dspotbugs.skipCheck=false`); `false` → report-only |
| `fail_on_cvss` | `7` | CVSS threshold for OWASP. Set to `11` for report-only mode |

### Required Repository Secrets

| Secret | Required for | Value |
|---|---|---|
| `SONAR_TOKEN` | SonarQube job | Project Analysis token from SonarQube UI |
| `SONAR_HOST_URL` | SonarQube job | Full URL of your SonarQube server |
| `NVD_API_KEY` | OWASP job | Free NVD API key (optional but recommended) |

### Downloading Reports from CI

1. Open the GitHub Actions run from the **Actions** tab.
2. Scroll to the bottom of the run page — **Artifacts** section.
3. Download `spotbugs-reports-<N>` or `owasp-dependency-check-<N>`.
4. Unzip and open any `*_spotbugs.html` or `*_dc-report.html` in a browser.

Artifacts are retained for **30 days**.

### NVD Cache in CI

The OWASP job caches the NVD data between runs using `actions/cache` with the key:

```
owasp-nvd-<runner-os>-<run-id>
```

The restore key `owasp-nvd-<runner-os>-` picks up the most recent cache from any
previous run, so incremental NVD updates are downloaded rather than the full database.

---

## Files Added / Modified

| File | Type | Purpose |
|---|---|---|
| `pom.xml` → profile `spotbugs` | Modified | SpotBugs 4.9.8.2 + Find Security Bugs 1.13.0 Maven plugin configuration |
| `pom.xml` → profile `owasp` | Modified | OWASP Dependency-Check 12.1.0 Maven plugin configuration |
| `scripts/ci/spotbugs-exclude.xml` | New | SpotBugs exclusion filter — suppress false positives and test classes |
| `scripts/ci/owasp-suppressions.xml` | New | OWASP CVE suppression file — suppress false positives after triage |
| `.github/workflows/security-scan.yml` | Modified | Added `spotbugs-sast` and `owasp-dc` parallel jobs; added dispatch inputs |

---

## Related Documentation

| File | Description |
|---|---|
| `doc/security-analysis/SECURITY_FINDINGS_OVERVIEW.md` | Initial manual security findings and risk register |
| `doc/security-analysis/SONARQUBE_LOCAL_SETUP.md` | How to run SonarQube locally with Docker |
| `scripts/ci/spotbugs-exclude.xml` | SpotBugs false-positive suppression filter |
| `scripts/ci/owasp-suppressions.xml` | OWASP CVE suppression file |
| `.github/workflows/security-scan.yml` | GitHub Actions security scan workflow |

