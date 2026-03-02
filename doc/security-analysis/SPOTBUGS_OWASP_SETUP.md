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

They can be combined with each other:

```powershell
# Both scanners in one build
mvn verify -Pspotbugs,owasp
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

### NVD API Key — prerequisite for CI

> ⛔ **The OWASP CI job is disabled until this key is in place.**
> Without it the first NVD database download takes 30–60 minutes, making the job
> impractical for regular CI runs.

The NVD rate-limits unauthenticated requests to 5 per 30 seconds.
With a free API key the limit rises to 50 per 30 seconds — the first run drops to ~5 minutes.

**Getting the key (free, ~2 minutes):**

1. Go to https://nvd.nist.gov/developers/request-an-api-key
2. Fill in your name and email (personal email accepted — no .gov/.edu required)
3. Check your inbox — the key arrives within a few minutes

**Adding the key to GitHub:**

1. Go to **Settings → Secrets and variables → Actions → New repository secret**
2. Name: `NVD_API_KEY` — Value: paste the key

**Re-enabling the CI job:**

Open `.github/workflows/security-scan.yml` and remove the `if: false` line from the
`owasp-dc` job (it is marked with a comment `⛔ remove this line once NVD_API_KEY secret is configured`).

**Using the key locally:**

```powershell
mvn verify -Powasp -Dnvd.api.key=<your-key>

# Or set once per session:
$env:NVD_API_KEY = "<your-key>"
mvn verify -Powasp
```

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

| Job | Profile | Status | What it produces |
|---|---|---|---|
| `spotbugs-sast` | `-Pspotbugs` | ✅ Enabled | Job summary table + `spotbugs-reports-<run#>` artifact (HTML per module) |
| `owasp-dc` | `-Powasp` | ⛔ Disabled | Job summary table + `owasp-dependency-check-<run#>` artifact (combined HTML) |

> **Why is the OWASP job disabled?**
> The first run must download the full NVD vulnerability database (~200 MB).
> Without an API key this takes **30–60 minutes** due to NVD rate-limiting (5 requests / 30 s).
> The job is disabled until an `NVD_API_KEY` secret is added to the repository.
> With the key the first run completes in ~5 minutes and subsequent runs are faster still
> (incremental cache updates only).

### Trigger Matrix

| Trigger | Condition |
|---|---|
| **Manual** (`workflow_dispatch`) | Run at any time from the GitHub Actions UI |
| **Release tag** | Automatically when a semver tag is pushed (e.g. `1.5.0`, `1.5.0-RC1`). The `release.yml` workflow creates these tags — the security scan runs automatically as part of every release. |
| **Scheduled weekly** | Every Monday at 06:00 UTC |

> **Why tags instead of branches?**
> The project does not use `release/**` branches. The `release.yml` workflow builds the
> project, commits the version bump, and then pushes a plain semver tag (e.g. `1.5.0`).
> The security scan therefore triggers on `push: tags` matching the patterns
> `[0-9]+.[0-9]+.[0-9]+` (stable releases) and `[0-9]+.[0-9]+.[0-9]+-*`
> (pre-releases such as `1.5.0-RC1` or `1.5.0-SNAPSHOT`).

### Manual Trigger Inputs

When triggering manually from the Actions UI, two inputs are available:

| Input | Default | Description |
|---|---|---|
| `fail_on_spotbugs` | `true` | `true` → activates gate (`-Dspotbugs.skipCheck=false`); `false` → report-only |
| `fail_on_cvss` | `7` | CVSS threshold for OWASP. Set to `11` for report-only mode |

### Required Repository Secrets

| Secret | Required for | Status | Value |
|---|---|---|---|
| `NVD_API_KEY` | OWASP job | ⚠️ Required to enable the job | Free NVD API key — see section above |

### Viewing Results in GitHub

There are two ways to see results directly in GitHub — no download required for the summary view.

#### Option 1 — Job Summary (instant, no download)

After each workflow run the findings are rendered as **markdown tables directly in the
GitHub Actions UI**. No download or local tool needed.

**How to navigate there:**

```
Repository → Actions tab
  → click the "Security Scan" workflow run
    → click the job name (e.g. "SpotBugs + Find Security Bugs")
      → scroll down past the step logs to "Summary"
```

Or use the shortcut: click **"Summary"** in the left sidebar of any workflow run.

What you see per job:

| Job | Summary content |
|---|---|
| `SpotBugs + Find Security Bugs` | Total count table, per-module breakdown, top-20 findings with severity / rule / class / line |
| `OWASP Dependency-Check` | Total CVE count, full table of CVEs at or above threshold (sorted by CVSS score), collapsible section for below-threshold findings |

Example of what the OWASP summary looks like in GitHub:

```
## 🔒 OWASP Dependency-Check

| Setting        | Value |
|---|---|
| CVSS threshold | 7     |

### Summary
| | Count |
|---|---|
| Total vulnerabilities found       | 12 |
| At or above CVSS threshold (7.0)  | **3** |
| Below threshold                   | 9  |

### Vulnerabilities at or above threshold (CVSS ≥ 7.0)
| Severity   | CVE            | CVSS | Dependency          | Description |
|---|---|---|---|---|
| 🟠 HIGH    | CVE-2024-XXXXX | 8.1  | `nimbus-jose-jwt`   | … |
| 🟡 MEDIUM  | CVE-2024-YYYYY | 7.5  | `spring-security`   | … |

<details><summary>Below threshold (9 findings)</summary>
...
</details>
```

#### Option 2 — Download HTML artifact (full interactive report)

For the complete interactive HTML report (sortable table, CVE links, dependency tree):

1. Go to **Repository → Actions → Security Scan** run
2. Scroll to the bottom of the run page → **Artifacts** section
3. Click to download:
   - `spotbugs-reports-<N>` → unzip → open `*_spotbugs.html` in a browser
   - `owasp-dependency-check-<N>` → unzip → open `dependency-check-report.html` in a browser

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

