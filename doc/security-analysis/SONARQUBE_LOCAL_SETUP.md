# SonarQube Local Setup Guide

This guide explains how to run a full SAST (Static Application Security Testing) scan
using a **self-hosted SonarQube Community Edition** instance on your local machine.

> **SonarQube Community Edition is completely free** — it is the open-source version
> with no licence cost and no expiry. It covers all SAST features including
> OWASP Top 10, SANS Top 25, security hotspots, code smells, and code coverage.
> The paid tiers (Developer / Enterprise) add branch analysis, pull-request decoration,
> and additional language support — none of which are required for SAST on this project.

---

## SonarQube Editions at a Glance

| Edition | Cost | Branch analysis | PR decoration | OWASP / SANS SAST |
|---|---|---|---|---|
| **Community** | Free (open source) | ❌ main branch only | ❌ | ✅ |
| **Developer** | Paid | ✅ | ✅ | ✅ |
| **SonarCloud** (SaaS) | Free for public repos | ✅ | ✅ | ✅ |

**Recommendation:** Use Community Edition locally and SonarCloud in CI for public
repositories, or self-host Developer Edition if branch/PR analysis is needed.

---

## Prerequisites

| Tool | Version |
|---|---|
| Docker Desktop | ≥ 4.x |
| Java (for Maven) | 17 |
| Maven | ≥ 3.8 |

SonarQube requires the host OS `vm.max_map_count ≥ 524288`.
On **Windows with Docker Desktop (WSL 2 backend)** this is usually already satisfied.
If SonarQube fails to start, run once in a PowerShell (Admin) terminal:

```powershell
wsl -d docker-desktop sysctl -w vm.max_map_count=524288
```

---

## Step 1 — Start SonarQube

From the project root:

```powershell
docker compose -f ci/sonarqube/docker-compose.sonarqube.yml up -d
```

Wait ~60 seconds, then open **http://localhost:9000**.
Log in with the default credentials: `admin` / `admin`.
You will be prompted to set a new password on first login.

---

## Step 2 — Create the Project and Generate a Token

> ⚠️ **Important:** You must create the project in the UI *before* running the scanner.
> Using a global User Token without a pre-created project causes the error:
> `You're not authorized to analyze this project or the project doesn't exist`.

### 2a — Create the project

1. Open **http://localhost:9000** and log in.
2. Click **Projects → Create project → Create a local project**.
3. Set **Project key** — this value must match in **two places** in the codebase (see table below). The default configured value is `dsp-true-connector`. If you use a different key, update both files.
4. Set **Display name** to `DSP True Connector` (free text, not linked to code).
5. Click **Next**, choose **"Use the global setting"** for new code definition → **Create project**.

| File | Property / field to update |
|---|---|
| `pom.xml` → profile `sonar` | `<sonar.projectKey>` |
| `.github/workflows/security-scan.yml` | `env.SONAR_PROJECT_KEY` (top of file) |

> Both files are already consistent with each other — you only need to edit them
> if the key you typed in the UI is different from `dsp-true-connector`.

### 2b — Generate a Project Analysis Token (not a global User Token)

6. On the **"How do you want to analyze your repository?"** screen, choose **"Locally"**.
7. Under **"Provide a token"**, select **"Generate a project analysis token"**.
8. Give it a name (e.g. `local-scan`) and click **Generate**.
9. **Copy the token now** — it will not be shown again.

> A *Project Analysis Token* is scoped to this project only and has the correct
> permissions to push results. A *Global User Token* will fail unless the user
> has been explicitly granted the "Create Projects" global permission.

### Troubleshooting — "not authorized" error

If you see:
```
You're not authorized to analyze this project or the project doesn't exist on SonarQube
and you're not authorized to create it.
```

Do one of the following:

| Fix | Steps |
|---|---|
| **Recommended** — use a Project Analysis Token | Follow steps 6–9 above. Delete any old global token. |
| Grant Create Projects permission | **Administration → Security → Global Permissions** → tick **"Create Projects"** for your user or the `sonar-users` group |

---

## Step 3 — Run the Maven Analysis

From the project root:

```powershell
mvn verify sonar:sonar -Psonar -Dsonar.token=<paste-your-token-here>
```

The `-Psonar` flag activates the dedicated Maven profile in the root `pom.xml` which:
- Points to `http://localhost:9000`
- Configures source/test directories for all sub-modules
- Enables JaCoCo XML report generation so coverage is visible in the dashboard
- Applies sensible exclusions for generated code
- Marks known dev-only findings (trust-all TLS, CORS wildcard, etc.) as **Won't Fix**
  so they do not pollute the security report

Full analysis of all 6 modules typically takes **3–8 minutes** depending on hardware.

---

## Step 4 — Review Results

Open **http://localhost:9000/dashboard?id=dsp-true-connector** after the scan completes.

Key sections for security review:

| Section | What to look for |
|---|---|
| **Security Hotspots** | Code requiring manual review — cryptography, auth, injection points |
| **Issues → Security** | Confirmed vulnerabilities tagged with OWASP / SANS / CWE |
| **Issues → type:BUG** | Null-pointer risks, resource leaks, incorrect logic |
| **Measures → Coverage** | Test coverage per module |

Use the **OWASP Top 10** and **SANS Top 25** filters in the Issues view to align
findings with the categories in `SECURITY_FINDINGS_OVERVIEW.md`.

---

## Step 5 — Stop / Remove the Stack

```powershell
# Stop containers (data is preserved in Docker named volumes)
docker compose -f ci/sonarqube/docker-compose.sonarqube.yml down

# Stop AND wipe all data (removes named volumes)
docker compose -f ci/sonarqube/docker-compose.sonarqube.yml down -v
```

---

## GitHub Actions CI Integration

The workflow `.github/workflows/security-scan.yml` runs the same analysis automatically.

### Trigger Matrix

| Trigger | Condition |
|---|---|
| **Manual** (`workflow_dispatch`) | Run at any time from the GitHub Actions UI on any branch |
| **Pre-release gate** | Every push to a `release/**` branch |
| **Scheduled weekly** | Every Monday at 06:00 UTC on the default branch |

### Required Repository Secrets

Go to **Settings → Secrets and variables → Actions** and add:

| Secret | Value |
|---|---|
| `SONAR_TOKEN` | Project Analysis token generated in SonarQube (Step 2 above) |
| `SONAR_HOST_URL` | Full URL of your SonarQube server, e.g. `https://sonarqube.example.com` |

### Using SonarCloud Instead (free for public repos)

1. Sign in at https://sonarcloud.io with your GitHub account and import the repository.
2. Set `SONAR_HOST_URL` secret to `https://sonarcloud.io`.
3. Add a `SONAR_ORGANIZATION` secret with your SonarCloud organisation key.
4. In `.github/workflows/security-scan.yml`, add `-Dsonar.organization=${{ secrets.SONAR_ORGANIZATION }}`
   to the `mvn sonar:sonar` command.

SonarCloud gives you full branch and PR analysis for free on public repositories,
making it the most practical choice for open-source or inner-source projects.

---

## Suppressing Dev-Only Findings

Several findings in `SECURITY_FINDINGS_OVERVIEW.md` are intentional for local development
(trust-all TLS, OCSP disabled, CORS wildcard, etc.).
These are already suppressed in the `sonar` Maven profile using `sonar.issue.ignore.multicriteria`.

To suppress additional rules per-file, add entries in the `<properties>` block of the
`sonar` profile in the root `pom.xml`:

```xml
<sonar.issue.ignore.multicriteria.eN.ruleKey>java:SXXXX</sonar.issue.ignore.multicriteria.eN.ruleKey>
<sonar.issue.ignore.multicriteria.eN.resourceKey>**/path/to/File.java</sonar.issue.ignore.multicriteria.eN.resourceKey>
```

For inline suppressions in source code (context-specific):

```java
@SuppressWarnings("java:S2068") // password-like field is a configurable default, not a secret
private String keystorePassword;
```

---

## Related Files

| File | Purpose |
|---|---|
| `ci/sonarqube/docker-compose.sonarqube.yml` | Local SonarQube + PostgreSQL stack |
| `.github/workflows/security-scan.yml` | GitHub Actions SAST workflow (SonarQube + SpotBugs + OWASP) |
| `doc/security-analysis/SECURITY_FINDINGS_OVERVIEW.md` | Initial findings and risk register |
| `doc/security-analysis/SPOTBUGS_OWASP_SETUP.md` | SpotBugs + Find Security Bugs & OWASP Dependency-Check setup guide |
| Root `pom.xml` → profile `sonar` | Maven Sonar plugin configuration |
| Root `pom.xml` → profile `spotbugs` | SpotBugs + Find Security Bugs configuration |
| Root `pom.xml` → profile `owasp` | OWASP Dependency-Check configuration |
| `scripts/ci/spotbugs-exclude.xml` | SpotBugs false-positive suppression filter |
| `scripts/ci/owasp-suppressions.xml` | OWASP CVE suppression file |

