# Task Decomposition Plan — Swagger UI / OpenAPI Exposure

Date: 2026-10-06
Status: Draft plan (no GitHub issues created, no code changed)
Source: `docs/superpowers/specs/2026-10-06-swagger-ui-openapi-design.md`
Workflow: `.github/skills/task-decomposition/SKILL.md`

## Scope decision

Treated as **one functional slice** (not six separate slices), decomposed directly into
implementation / QA / docs task families. Proposed slice tag: `SW1`.

No parent GitHub issue exists yet for this initiative (verified via `gh issue list --search
"swagger OR openapi"` — no results). Before any child issue is created, open a
`functional-slice.yml` parent issue:

- Title: `[SLICE][SW1] Expose Swagger UI / OpenAPI for management + DSP protocol APIs`
- Slice tag: `SW1`
- Source: this design doc (`docs/superpowers/specs/2026-10-06-swagger-ui-openapi-design.md`)

## Dependency graph

```
T1 (core config)
 ├─ T2 (docs/example-loading + generator infra + drift test)
 │    ├─ T5 (catalog docs+examples)      ─┐
 │    ├─ T6 (negotiation/transfer docs)   ├─ parallel leaves
 │    ├─ T7 (admin API docs)              │
 │    └─ T8 (protocol JSON-LD docs)      ─┘
 └─ T3 (security permits + deploy props + enable/disable IT)
T4 (instruction/skill updates)  — independent, no code dependency, but should land before T5–T8 start
Q1 (slice QA)   — depends on T1..T8
D1 (slice docs) — depends on T1..T8 + Q1
```

## Child issue summary table

| # | Title (working) | Template | Family | Depends on | Parallel with |
|---|---|---|---|---|---|
| T1 | springdoc dependency & core `OpenApiConfig` | task.yml | impl | — | T4 |
| T2 | Docs-YAML/example loading customizer + generator Maven wiring + drift test | task.yml | impl | T1 | T3, T4 |
| T3 | Security permits, deployment property toggles, enable/disable IT | task.yml | impl | T1 | T2, T4 |
| T4 | AGENTS.md rule + `openapi-docs.instructions.md` + skill pointer updates | task.yml | impl | — | T1, T2, T3 |
| T5 | Catalog-side API docs + examples (catalog/dataset/distribution/data-service full; offer/artifact/proxy brief) | task.yml | impl | T1, T2 | T6, T7, T8 |
| T6 | Negotiation & transfer API docs + examples | task.yml | impl | T1, T2 | T5, T7, T8 |
| T7 | Administration API docs + examples (auth/tenant/user full; audit/properties/FTP/dashboard/version brief) | task.yml | impl | T1, T2 | T5, T6, T8 |
| T8 | Protocol (DSP) docs + JSON-LD examples | task.yml | impl | T1, T2 | T5, T6, T7 |
| Q1 | Slice-level QA: Swagger exposure end-to-end | qa-task.yml | QA | T1–T8 | — |
| D1 | Slice-level docs: ADR, `doc/openapi.md`, CHANGELOG, security doc, remaining §6a items | docs-task.yml | docs | T1–T8, Q1 | — |

## Codebase facts confirmed before writing implementation prompts

- `connector/src/main/java/it/eng/connector/ApplicationConnector.java` component-scans all 5 module package trees (`it.eng.connector`, `it.eng.catalog`, `it.eng.negotiation`, `it.eng.tools`, `it.eng.datatransfer`) — a single `@Bean` in `connector` can discover/apply docs from any module's classpath resources.
- `ConnectorSecurityConfig` defines 3 ordered `SecurityFilterChain`s: `@Order(1)` matches `/api/**,/actuator/**,/env`; `@Order(2)` matches protocol paths (`ROLE_CONNECTOR`); `@Order(3)` is the **default chain**, `securityMatcher` unset, `anyRequest().permitAll()`. `/swagger-ui/**` and `/v3/api-docs/**` are not under `/api/**` or the protocol matcher, so they already fall through to the **existing permitAll default chain** — T3 does not need new `requestMatchers` permits, only verification + deployment-profile toggles.
- `AuthController` (`connector/src/main/java/it/eng/connector/rest/api/AuthController.java`) exposes `POST /api/v1/auth/login` (`ApiEndpoints.AUTH_V1` is `permitAll` in both filter chains already). No new endpoint needed for Swagger tokens.
- `ApiEndpoints` constants live in `tools/src/main/java/it/eng/tools/controller/ApiEndpoints.java` — reuse for any new path references.
- Only `connector` and `data-transfer` currently have a `src/main/resources`/`src/test/resources` tree; `catalog`, `negotiation`, `tools` have **no `src/main/resources` directory yet** — T5/T6/T7 (whichever owns those modules) must create `src/main/resources/openapi/` from scratch.
- Existing mock/test fixture classes to reuse for examples: `catalog/src/test/java/it/eng/catalog/util/CatalogMockObjectUtil.java`, `negotiation/src/test/java/it/eng/negotiation/model/NegotiationMockObjectUtil.java`, `data-transfer/src/test/java/it/eng/datatransfer/util/DataTransferMockObjectUtil.java`, `tools/src/test/java/it/eng/tools/util/ToolsMockObjectUtil.java`.
- Serializers: `CatalogSerializer` (catalog), `NegotiationSerializer` (negotiation), `TransferSerializer` (data-transfer), `ToolsSerializer` (tools) — each exposes `serializePlain`/`serializeProtocol` pairs per `AGENTS.md`.
- Controllers confirmed per module: catalog (`CatalogController` protocol; `OfferAPIController`, `DatasetAPIController`, `DistributionAPIController`, `ProxyAPIController`, `CatalogAPIController`, `ArtifactAPIController`, `DataServiceAPIController` API); negotiation (`ContractNegotiationConsumerCallbackController`, `ContractNegotiationProviderController` protocol; `ContractNegotiationAPIController`, `AgreementAPIController` API); data-transfer (`ProviderDataTransferController`, `ConsumerDataTransferCallbackController` protocol; `DataTransferAPIController`, `RestArtifactController`, `FTPClientAPIController` API); tools (`AuditEventController`, `ApplicationPropertiesAPIController`, `TenantAPIController`, `TenantAwareProtocolController`); connector (`DashboardMetricsController`, `DSpaceVersionController`, `UserAPIController`, `KeycloakUserApiController`, `AuthController`).
- Root `pom.xml` `<pluginManagement>` (line ~446) currently has no `exec-maven-plugin` entry — T2 adds the first one.

## Per-issue detailed implementation plan

Each task below is written at `task.yml` field granularity so it can be pasted directly into issue
creation. `Depends on` / `Blocked by` use the working aliases (T1…T8, Q1, D1); real `#numbers` are
substituted at creation time.

---

### T1 — springdoc dependency & core `OpenApiConfig`

**Context**
What: introduce `springdoc-openapi-starter-webmvc-ui` and the core `OpenAPI`/`GroupedOpenApi` beans, property-toggled off by default.
Why: foundation for every other task in this slice; nothing else can configure groups/docs without this bean existing.
Where: root `pom.xml`, `connector/pom.xml`, new `connector/src/main/java/it/eng/connector/configuration/OpenApiConfig.java`.
Requirement source: `docs/superpowers/specs/2026-10-06-swagger-ui-openapi-design.md` §4.1–4.2.

**Source & Parent Traceability**
Slice tag: `SW1`. Source: design spec above. Parent/root issue: `[SLICE][SW1]` (create first).

**Implementation Prompt**
Files to create/modify:
- Root `pom.xml`: add `springdoc.version` property (2.8.x) and a `dependencyManagement` entry for `springdoc-openapi-starter-webmvc-ui`.
- `connector/pom.xml`: add the starter dependency (no version — managed by root).
- New `connector/src/main/java/it/eng/connector/configuration/OpenApiConfig.java`:
  - `@Configuration` + `@ConditionalOnProperty(prefix = "springdoc.swagger-ui", name = "enabled", havingValue = "true")`.
  - `@Bean OpenAPI openApiInfo()`: title, version (pull from `project.version` via `@Value`), description containing a short "how to try it" walkthrough, `Components` with a `bearerAuth` `SecurityScheme` (HTTP bearer, JWT format), configurable server URL via new property `openapi.server-url` (default `/`).
  - `@Bean GroupedOpenApi managementApiGroup()` matching `/api/**`.
  - `@Bean GroupedOpenApi dspProtocolGroup()` matching `/{tenantId}/**` equivalent path patterns actually used by protocol controllers (verify actual `@RequestMapping` prefixes in `CatalogController`/`ContractNegotiationProviderController`/`ProviderDataTransferController` — do not assume `/{tenantId}/**` literally matches springdoc path-matching syntax; confirm against Spring MVC pattern first).
  - `OperationCustomizer` or `GlobalOpenApiCustomizer` bean that orders tags: Auth, Tenant, User, Catalog, Dataset, Distribution, Data Service, Negotiation, Agreement, Transfer, then the rest (alphabetical).
  - Optional: `OperationCustomizer` adding an `X-Tenant-Id` header parameter to management-group operations only (super-admin tenant switching) — guard by group name.
Patterns to follow: Javadoc on all public methods (Checkstyle); no fully-qualified names, use imports; immutability (`final` fields/locals).
Properties: add `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`, `openapi.server-url=` **to every property file in the repo**, not just the base one, so no profile silently falls back to framework defaults and no file drifts out of sync. Confirmed full list (10 files):
  - `connector/src/main/resources/application-consumer.properties`
  - `connector/src/main/resources/application-provider.properties`
  - `connector/src/main/resources/application-tck.properties`
  - `connector/src/test/resources/application.properties`
  - `connector/src/test/resources/application-tck.properties`
  - `ci/docker/connector_a_resources/application.properties`
  - `ci/docker/connector_b_resources/application.properties`
  - `ci/tck/connector_tck_resources/application-tck.properties`
  - `terraform/app-resources/connector_a_resources/application.properties`
  - `terraform/app-resources/connector_b_resources/application.properties`
  All default to `false` in this task. T3 only *flips* the value on the files where it should actually be user-togglable (terraform, ci/docker demo) — it does not add the keys for the first time.

**Dependencies**
Depends on: none (first task). Blocks: T2, T3.

**DSP Protocol / TCK Impact**
Protocol-facing: no (adds configuration only, no behavior/endpoint change). TCK run required: no.

**Security & Quality Requirements**
New dependency introduced: `springdoc-openapi-starter-webmvc-ui` — check for known CVEs before pinning version, record chosen version/rationale for the docs task's CHANGELOG entry.

**Delivery Stream & Parallelization**
Stream: `openapi-infra`. Can run in parallel with: T4. Must wait for: none. Why separate: foundation bean other infra tasks (T2, T3) build on; no overlap with instruction-file work (T4).

**Type / Priority / Milestone**: feature / P1 — high / Next release.
**AI Model**: Sonnet 4.6 (standard implementation).
**Agent Mode**: Single agent — sequential execution.
**Agent Instructions**: Read `AGENTS.md`, this task's spec section, confirm actual protocol path patterns in the 3 protocol controllers before writing `GroupedOpenApi` path matchers — do not guess.

**Verification Checklist**
- [ ] `mvn clean verify` exits 0 (unit tests; Docker running for ITs elsewhere in `connector`)
- [ ] Checkstyle passes (Javadoc on new public methods in `OpenApiConfig`)
- [ ] Unit test: with `springdoc.swagger-ui.enabled=false` (default), `OpenApiConfig` beans are not created
- [ ] Unit test: with the property `true`, `OpenAPI` bean exists with `bearerAuth` scheme and both `GroupedOpenApi` beans present
- [ ] All GitHub Actions CI checks pass on the PR (verified via `gh pr checks`)

---

### T2 — Docs-YAML/example loading customizer + generator Maven wiring + drift test

**Context**
What: the mechanism that lets per-module YAML docs files and build-time-generated JSON examples attach to operations without touching controllers.
Why: enables T5–T8 to add documentation purely as resource files.
Where: `connector/src/main/java/it/eng/connector/configuration/` (new customizer classes), root `pom.xml` `pluginManagement`.

**Source & Parent Traceability**
Slice tag: `SW1`. Parent: `[SLICE][SW1]`. Depends on T1.

**Implementation Prompt**
Files to create/modify:
- `connector/src/main/java/it/eng/connector/configuration/OpenApiDocsCustomizer.java` (or similar name): implements `OperationCustomizer`. On each operation, resolve `tag = controller simple class name`, `operationId = method name` (springdoc default), load merged YAML from `classpath*:openapi/*-docs.yaml` (use `PathMatchingResourcePatternResolver`), look up `operations.<operationId>`, apply `summary`/`description`/`requestExamples`/`responses` if present; tolerate missing entries (operation stays undocumented).
- A second customizer (`GlobalOpenApiCustomizer` or reuse the same class) applies `tags.<ControllerClassName>.{name,description}` to `OpenAPI.tags`.
- A schema customizer applying the YAML `schemas:` section's field descriptions to model `Schema` objects (keyed by simple class name + field name).
- Example resolution: load `classpath*:openapi/examples/*.json`; `/api/**` operations use `<name>.plain.json`, DSP operations use `<name>.protocol.json`; register resolved examples under `components/examples` and reference them from `requestExamples`.
- Root `pom.xml` `<pluginManagement>`: add `exec-maven-plugin` (groupId `org.codehaus.mojo`) bound to `process-test-classes`, `classpathScope=test`, `mainClass` parameterized per module (each module's POM supplies its own `OpenApiExamplesGenerator` main class when it opts in — T5–T8 do the per-module enablement + the generator class itself).
- New unit/slice test (in `connector/src/test`) asserting: every `operationId` referenced by any loaded `-docs.yaml` file exists among actual Spring MVC mappings (drift check using `RequestMappingHandlerMapping` at test context startup); every operation of a hardcoded list of "fully-documented controllers" (empty list for now — T5–T8 append to it) has a non-blank summary+description once their YAML lands.
Patterns: keep YAML parsing isolated in a small loader class with Javadoc; no springdoc annotations ever added to controllers/models.

**Dependencies**
Depends on: T1. Blocked by: none. Blocks (soft): T5, T6, T7, T8 need this mechanism to exist, though they can draft their YAML content in parallel and wire it in once T2 merges.

**DSP Protocol / TCK Impact**: none directly (infrastructure only); enables protocol doc entries later (T8).

**Security & Quality Requirements**: ensure `PathMatchingResourcePatternResolver` classpath scans are read-only and fail soft (log + skip) on malformed YAML — must not crash app startup when `springdoc.swagger-ui.enabled=false` disables the whole config class anyway.

**Delivery Stream & Parallelization**
Stream: `openapi-infra`. Can run in parallel with: T3, T4. Must wait for: T1.

**Type / Priority / Milestone**: feature / P1 — high / Next release.
**AI Model**: Opus 4.8 (complex, multi-file, architectural).
**Agent Mode**: Single agent — sequential execution.
**Agent Instructions**: Read `doc/architecture.md`, confirm springdoc 2.8.x `OperationCustomizer`/`GlobalOpenApiCustomizer` SPI signatures before implementing (API surface differs across springdoc minor versions).

**Verification Checklist**
- [ ] `mvn clean verify` exits 0
- [ ] Checkstyle passes
- [ ] Drift test passes with zero docs files present (no false failures)
- [ ] Drift test fails (red) when a docs file references a non-existent `operationId` — covered by a dedicated negative unit test
- [ ] Missing example file is tolerated (operation keeps working, no `components/examples` entry)
- [ ] `exec-maven-plugin` entry added to root `pluginManagement` only (no module opts in yet — opt-in is each per-area task's job)
- [ ] All GitHub Actions CI checks pass on the PR (verified via `gh pr checks`)

---

### T3 — Security permits verification, deployment property toggles, enable/disable IT

**Context**
What: confirm Swagger paths are safely exposed only via the existing default `permitAll` chain, flip the property to `true` only on demo/server deployment profiles, and add a durable IT proving the toggle.
Why: `springdoc.swagger-ui.enabled` must be `false` by default everywhere except explicitly intended demo/server environments; this is a security-sensitive on/off switch.
Where: `connector/src/main/java/it/eng/connector/configuration/ConnectorSecurityConfig.java` (verification only, likely no code change), `ci/docker/connector_a_resources/application.properties`, `ci/docker/connector_b_resources/application.properties`, `terraform/app-resources/*/application.properties` (whichever exist), new IT in `connector/src/test/java/it/eng/connector/integration/`.

**Source & Parent Traceability**
Slice tag: `SW1`. Parent: `[SLICE][SW1]`. Depends on T1.

**Implementation Prompt**
Files to create/modify:
- Confirm (do not assume) that `/swagger-ui/**` and `/v3/api-docs/**` are **not** matched by the `@Order(1)` (`/api/**,/actuator/**,/env`) or `@Order(2)` protocol `securityMatcher` in `ConnectorSecurityConfig`, and therefore fall to the `@Order(3)` default `permitAll` chain already. If confirmed, **no new `requestMatchers` are needed** — document this finding explicitly in the PR description instead of adding redundant permits.
- Verify CORS configuration and `TckProtocolForwardingFilter` (`connector/src/main/java/it/eng/connector/filter/TckProtocolForwardingFilter.java`) do not intercept/rewrite `/swagger-ui/**` or `/v3/api-docs/**` requests; add a regression test if a gap is found.
- Deployment property files: all 10 property files already have the keys from T1 defaulted to `false`. In this task, flip `springdoc.api-docs.enabled=true` / `springdoc.swagger-ui.enabled=true` and set a real `openapi.server-url` **only** in `terraform/app-resources/connector_a_resources/application.properties` and `terraform/app-resources/connector_b_resources/application.properties` (the user-facing deployed/runnable environment where an operator needs to actually toggle it) and the `ci/docker/connector_a_resources/application.properties` / `ci/docker/connector_b_resources/application.properties` demo pair. Keep `connector/src/main/resources/application-consumer.properties`, `application-provider.properties`, `application-tck.properties`, `connector/src/test/resources/application*.properties`, and `ci/tck/connector_tck_resources/application-tck.properties` at `false` — but they **must still contain the keys** (added by T1) so every profile stays consistent and no file silently diverges from the others over time.
- New IT `SwaggerUiExposureIT` extending `BaseIntegrationTest`: with the toggle `true` (via a dedicated test property source or `@TestPropertySource`), assert `GET /v3/api-docs/management-api` and `GET /v3/api-docs/dsp-protocol` return 200; with it `false` (default), assert `GET /swagger-ui.html` and `GET /v3/api-docs` return 404.

**Dependencies**
Depends on: T1. Blocks: Q1 (QA reuses this IT's pattern for its own end-to-end check).

**DSP Protocol / TCK Impact**: none — no protocol endpoint behavior changes; purely exposure/documentation surface.

**Security & Quality Requirements**
- Swagger UI can execute state-changing calls once enabled — explicitly confirm in the PR that this is acceptable only for the demo profile's seeded mock data, and flag it for the docs task's risk register (D1, §8 of the spec).
- Packaged jar excludes `.properties` files — deployment property changes must be verified against the actual deployed property files, not assumed to ship inside the jar.

**Delivery Stream & Parallelization**
Stream: `openapi-infra`. Can run in parallel with: T2, T4. Must wait for: T1.

**Type / Priority / Milestone**: feature / P1 — high / Next release.
**AI Model**: Sonnet 4.6 (standard implementation).
**Agent Mode**: Single agent — sequential execution.
**Agent Instructions**: Read `doc/security.md` before changing anything security-related. Docker must be running for the IT.

**Verification Checklist**
- [ ] `mvn clean verify` exits 0 (Docker running — Testcontainers IT)
- [ ] `SwaggerUiExposureIT` passes: 404 when disabled (default profile), 200 when enabled (test property override)
- [ ] No regression in existing `/api/**` or protocol-path authorization tests
- [ ] All 10 `application*.properties` files contain the new keys (default `false`); only the 4 user-facing/demo files (`terraform/app-resources/*/application.properties`, `ci/docker/connector_*_resources/application.properties`) are flipped `true`; diff reviewed explicitly in the PR
- [ ] `doc/security.md` note added (or flagged as a D1 follow-up if full rewrite belongs there)
- [ ] All GitHub Actions CI checks pass on the PR (verified via `gh pr checks`)

---

### T4 — AGENTS.md rule, new `openapi-docs.instructions.md`, and skill pointer updates

**Context**
What: land the process rule ("changing a controller requires updating its `openapi/<module>-docs.yaml` entry") before T5–T8 start writing YAML, per spec §6a.
Why: keeps documentation from drifting silently; §6a explicitly calls for this to land with slice/infra work, not deferred to the final docs task.
Where: `AGENTS.md`, new `.github/instructions/openapi-docs.instructions.md`, `.github/instructions/java.instructions.md`, `.github/skills/java-development/SKILL.md`, `.github/skills/task-implementation/SKILL.md` (+ `.claude/skills/task-implementation/` mirror), `.github/ISSUE_TEMPLATE/task.yml`, this `task-decomposition` skill file.

**Source & Parent Traceability**
Slice tag: `SW1`. Parent: `[SLICE][SW1]`. No code dependency — independent of T1–T3.

**Implementation Prompt**
Files to create/modify:
- `AGENTS.md`: add a new bullet under **Non-Negotiable Constraints** — "Changing a REST controller (new/renamed/removed method, path, params, request/response shape, roles, status codes) requires updating the matching `openapi/<module>-docs.yaml` entry (and example generator if the payload changed) in the same change." Add `openapi-docs.instructions.md` as a new row in the **Key Documents** table.
- New `.github/instructions/openapi-docs.instructions.md` with front-matter `applyTo: ["**/rest/**/*Controller.java", "**/openapi/*-docs.yaml"]`: document the YAML shape (`tags.<ControllerClassName>.{name,description}`, `operations.<methodName>.{summary,description,requestExamples,responses}`), the rule that `operations` keys are the default springdoc `operationId` (method name, no overloads allowed in documented controllers), which doc level (full vs brief) each controller has (reuse the spec §4.4 table), how examples are generated (`serializePlain` for `/api/**`, `serializeProtocol` for DSP), and the "change controller → change YAML" checklist.
- `.github/instructions/java.instructions.md` and `.github/skills/java-development/SKILL.md`: add a one-line pointer under a "REST controllers" note referencing the new instruction file.
- `.github/skills/task-implementation/SKILL.md` (+ `.claude/skills/task-implementation/` mirror): clarify the task-local vs slice-level documentation boundary — the OpenAPI YAML entry is part of the implementation task (code-adjacent, verified by the T2 drift test); narrative docs (`doc/openapi.md`, architecture) stay in the slice docs task. Add the YAML update to the implementation Verification Checklist expectations.
- `.github/ISSUE_TEMPLATE/task.yml`: optionally add a checkbox "OpenAPI docs YAML updated" to the Verification Checklist placeholder text (non-breaking, template placeholder only).
- `.github/skills/task-decomposition/SKILL.md` (+ `.claude` mirror if present): note that implementation tasks touching controllers must list the YAML update in their Verification Checklist.
- `.github/skills/dsp-compliance-review/SKILL.md`: optional — add "protocol example JSON still matches `serializeProtocol` output" to its review checklist.

**Dependencies**
Depends on: none. Should complete before T5–T8 start (soft precedence, not a hard `Depends on`).

**DSP Protocol / TCK Impact**: none — documentation/process only.

**Security & Quality Requirements**: none beyond standard doc hygiene.

**Delivery Stream & Parallelization**
Stream: `openapi-process`. Can run in parallel with: T1, T2, T3. Must wait for: none. Why separate: pure instruction/skill file edits, zero code dependency, but content references file paths that should be double-checked once T1/T2 land (module names, class names) — recommend finishing last among the parallel batch even though there's no hard dependency.

**Type / Priority / Milestone**: docs / P2 — medium / Next release.
**AI Model**: Haiku 4.5 (simple, mechanical changes).
**Agent Mode**: Single agent — sequential execution.
**Agent Instructions**: Doc-only; no product code. Cross-check referenced file paths exist before citing them.

**Verification Checklist**
- [ ] `openapi-docs.instructions.md` created with correct `applyTo` front matter
- [ ] `AGENTS.md` Non-Negotiable Constraint and Key Documents row added
- [ ] Pointer lines added in `java.instructions.md` / `java-development` skill
- [ ] `task-implementation` skill (+ mirror) boundary clarification added
- [ ] [manual] Markdown renders correctly (tables/front-matter) when previewed
- [ ] All GitHub Actions CI checks pass on the PR (verified via `gh pr checks`)

---

### T5 — Catalog-side API docs + examples

**Context**
What: full-detail YAML docs + generated examples for `CatalogAPIController`, `DatasetAPIController`, `DistributionAPIController`, `DataServiceAPIController`; brief-level for `OfferAPIController` (`/validate`), `ArtifactAPIController`, `ProxyAPIController`.
Why: these are the highest-traffic management endpoints per spec §4.4 detail table.
Where: new `catalog/src/main/resources/openapi/catalog-docs.yaml`, new `catalog/src/test/java/it/eng/catalog/openapi/OpenApiExamplesGenerator.java`, `catalog/pom.xml`.

**Source & Parent Traceability**
Slice tag: `SW1`. Parent: `[SLICE][SW1]`. Depends on T1, T2.

**Implementation Prompt**
Files to create/modify:
- `catalog/src/main/resources/openapi/catalog-docs.yaml` (new directory — `catalog` has no `src/main/resources` yet): `tags` entries for each of the 7 controllers; `operations` entries keyed by method name for every mapping in the 4 "full" controllers (catalog → dataset → distribution → data service hierarchy, DCAT fields, FILE vs EXTERNAL artifact, S3 key = dataset id, catalog hides datasets missing in S3); one-line summaries for the 3 "brief" controllers.
- `catalog/src/test/java/it/eng/catalog/openapi/OpenApiExamplesGenerator.java`: `main` method using `CatalogMockObjectUtil` fixtures, serialize via `CatalogSerializer.serializePlain(...)` for API payloads, write to `target/classes/openapi/examples/<name>.plain.json`. Use seeded constants (dataset id, tenant) where an example must be executable against seeded data per spec §4.4.1.
- `catalog/pom.xml`: enable the root-managed `exec-maven-plugin` execution pointing at this generator's `mainClass`.
- Append `catalog` controllers to T2's drift test "fully-documented controllers" list (small follow-up PR to `connector` test, or coordinate via a shared constant — confirm with T2's author at implementation time).

**Dependencies**
Depends on: T1, T2. Can run in parallel with: T6, T7, T8.

**DSP Protocol / TCK Impact**: none — these are management API (`/api/**`) controllers, not protocol-facing.

**Security & Quality Requirements**: none beyond standard — examples must not leak real credentials/tokens, only seeded mock values.

**Delivery Stream & Parallelization**
Stream: `catalog-openapi`. Can run in parallel with: T6, T7, T8. Must wait for: T1, T2.

**Type / Priority / Milestone**: docs / P2 — medium / Next release.
**AI Model**: Sonnet 4.6 (standard implementation).
**Agent Mode**: Single agent — sequential execution.
**Agent Instructions**: Read `catalog/doc/catalog.md` and `.github/instructions/openapi-docs.instructions.md` (from T4) first.

**Verification Checklist**
- [ ] `mvn -pl catalog -am verify` exits 0
- [ ] Generator produces non-empty `target/classes/openapi/examples/*.plain.json` for each documented operation needing an example
- [ ] Every operation in the 4 "full" controllers has summary + description (asserted by T2's drift/doc-quality test once wired)
- [ ] `exec-maven-plugin` runs during `process-test-classes` without `-DskipTests` breaking the build when examples are absent (tolerant customizer)
- [ ] All GitHub Actions CI checks pass on the PR (verified via `gh pr checks`)

---

### T6 — Negotiation & transfer API docs + examples

**Context**
What: full-detail YAML docs + examples for `ContractNegotiationAPIController`, `AgreementAPIController`, `DataTransferAPIController`.
Why: core business-flow controllers — state machine (negotiation) and lifecycle (transfer) documentation is the highest-value Swagger content per spec §4.4.
Where: new `negotiation/src/main/resources/openapi/negotiation-docs.yaml`, new `data-transfer/src/main/resources/openapi/data-transfer-docs.yaml`, generator classes in both modules' `src/test/java`.

**Source & Parent Traceability**
Slice tag: `SW1`. Parent: `[SLICE][SW1]`. Depends on T1, T2.

**Implementation Prompt**
Files to create/modify:
- `negotiation/src/main/resources/openapi/negotiation-docs.yaml`: state machine overview (REQUESTED → OFFERED → ACCEPTED → AGREED → VERIFIED → FINALIZED / TERMINATED) in the `ContractNegotiationAPIController` tag description; per-operation consumer/provider role, required/resulting state, counterparty message side effect; document `/offer` and `/request` new-vs-existing variants and list filters; `AgreementAPIController`: when policy enforcement is evaluated and what the check returns.
- `negotiation/src/test/java/it/eng/negotiation/openapi/OpenApiExamplesGenerator.java`: reuse `NegotiationMockObjectUtil`, serialize negotiation API request/offer payloads via `NegotiationSerializer.serializePlain(...)`.
- `data-transfer/src/main/resources/openapi/data-transfer-docs.yaml`: lifecycle (REQUESTED → STARTED → SUSPENDED → COMPLETED / TERMINATED) in `DataTransferAPIController` tag; HTTP_PULL vs HTTP_PUSH distinction; agreement prerequisite; download/view content types and presigned flow per `S3-ARCHITECTURE.instructions.md`-level detail.
- `data-transfer/src/test/java/it/eng/datatransfer/openapi/OpenApiExamplesGenerator.java`: reuse `DataTransferMockObjectUtil`, serialize via `TransferSerializer.serializePlain(...)`.
- Both modules' `pom.xml`: enable the `exec-maven-plugin` execution.

**Dependencies**
Depends on: T1, T2. Can run in parallel with: T5, T7, T8.

**DSP Protocol / TCK Impact**: none — these are the `/api/**` management-side controllers (protocol message *documentation* is T8's job), but the content must stay consistent with actual state-machine behavior — cross-check against `negotiation/doc/model.md` before writing state-transition prose.

**Security & Quality Requirements**: none beyond standard.

**Delivery Stream & Parallelization**
Stream: `negotiation-transfer-openapi`. Can run in parallel with: T5, T7, T8. Must wait for: T1, T2.

**Type / Priority / Milestone**: docs / P2 — medium / Next release.
**AI Model**: Sonnet 4.6 (standard implementation).
**Agent Mode**: Single agent — sequential execution.
**Agent Instructions**: Read `negotiation/doc/model.md` and `data-transfer/doc/data-transfer.md` first; confirm this spans two modules — consider splitting into two PRs if the combined diff exceeds the 5-file guidance (one PR for negotiation, one for data-transfer) while keeping one tracking issue, or split into two sibling issues at creation time if preferred.

**Verification Checklist**
- [ ] `mvn -pl negotiation,data-transfer -am verify` exits 0
- [ ] State machine description in YAML matches actual `NegotiationStateMachine`/equivalent transitions (manual cross-check)
- [ ] Generators produce non-empty example JSON for both modules
- [ ] Every operation in `ContractNegotiationAPIController`, `AgreementAPIController`, `DataTransferAPIController` has summary + description
- [ ] All GitHub Actions CI checks pass on the PR (verified via `gh pr checks`)

---

### T7 — Administration API docs + examples

**Context**
What: full-detail docs for `TenantAPIController`, `UserAPIController`; brief for `AuthController`, `AuditEventController`, `ApplicationPropertiesAPIController`, `FTPClientAPIController`, `DashboardMetricsController`, `DSpaceVersionController`.
Why: covers tenant isolation, role matrix, and login examples needed for the Swagger "try it" walkthrough itself.
Where: new `tools/src/main/resources/openapi/tools-docs.yaml` (for `TenantAPIController`, `AuditEventController`, `ApplicationPropertiesAPIController`), new `connector/src/main/resources/openapi/connector-docs.yaml` (for `UserAPIController`, `AuthController`, `DashboardMetricsController`, `DSpaceVersionController`), `data-transfer`'s `FTPClientAPIController` folded into T6's `data-transfer-docs.yaml` instead (same module) — confirm at creation time to avoid overlap with T6.

**Source & Parent Traceability**
Slice tag: `SW1`. Parent: `[SLICE][SW1]`. Depends on T1, T2. Relates to: T6 (FTP controller lives in `data-transfer`, boundary must be stated explicitly to avoid overlap — recommend T6 owns `FTPClientAPIController` as a brief entry since it's module-local).

**Implementation Prompt**
Files to create/modify:
- `tools/src/main/resources/openapi/tools-docs.yaml` (new directory): `TenantAPIController` full detail — `ROLE_SUPER_ADMIN` requirement, tenant isolation/per-tenant bucket note, enable/disable effect on protocol calls, delete consequences, `X-Tenant-Id` header usage; `AuditEventController`, `ApplicationPropertiesAPIController` brief.
- `tools/src/test/java/it/eng/tools/openapi/OpenApiExamplesGenerator.java`: reuse `ToolsMockObjectUtil`, serialize via `ToolsSerializer.serializePlain(...)`.
- `connector/src/main/resources/openapi/connector-docs.yaml`: `UserAPIController` full detail — role matrix (SUPER_ADMIN vs ADMIN self-service), `CONNECTOR` role hidden from lists, INTERNAL vs KEYCLOAK auth-mode differences, password rules; `AuthController` brief + concrete login examples for both `admin@mail.com` and `connector@mail.com`; `DashboardMetricsController`, `DSpaceVersionController` brief.
- `connector/src/test/java/it/eng/connector/openapi/OpenApiExamplesGenerator.java`: build `LoginRequest` examples for both seeded users directly (no existing mock util needed for this simple DTO — confirm whether one already exists before writing a new one).
- Both modules' `pom.xml`: enable `exec-maven-plugin`.

**Dependencies**
Depends on: T1, T2. Can run in parallel with: T5, T6, T8.

**DSP Protocol / TCK Impact**: none.

**Security & Quality Requirements**: login examples must only reference seeded mock-data credentials already present in `initial_data*.json` — never introduce new credentials; confirm INTERNAL vs KEYCLOAK auth-mode doc notes against `doc/security.md` and `KEYCLOAK_INTEGRATION_COMPLETE_SUMMARY.md`.

**Delivery Stream & Parallelization**
Stream: `admin-openapi`. Can run in parallel with: T5, T6, T8. Must wait for: T1, T2.

**Type / Priority / Milestone**: docs / P2 — medium / Next release.
**AI Model**: Sonnet 4.6 (standard implementation).
**Agent Mode**: Single agent — sequential execution.
**Agent Instructions**: Read `doc/security.md`, confirm the FTP-controller ownership boundary with whoever implements T6 before finalizing scope.

**Verification Checklist**
- [ ] `mvn -pl tools,connector -am verify` exits 0
- [ ] Login examples execute successfully against seeded `admin@mail.com` / `connector@mail.com` in a manual/IT check
- [ ] `TenantAPIController` and `UserAPIController` operations fully documented (summary + description)
- [ ] No duplicate documentation of `FTPClientAPIController` between this task and T6
- [ ] All GitHub Actions CI checks pass on the PR (verified via `gh pr checks`)

---

### T8 — Protocol (DSP) docs + JSON-LD examples

**Context**
What: main-flow JSON-LD examples and brief documentation for all protocol controllers (`CatalogController`, `ContractNegotiationProviderController`, `ContractNegotiationConsumerCallbackController`, `ProviderDataTransferController`, `ConsumerDataTransferCallbackController`).
Why: protocol endpoints take raw `JsonNode` bodies — springdoc cannot infer schemas, so examples must be hand-written/generated from real serializer output.
Where: `catalog-docs.yaml` (protocol section), `negotiation-docs.yaml` (protocol section), `data-transfer-docs.yaml` (protocol section) — extends the YAML files created in T5/T6 rather than new files, plus new generator entries for protocol message examples.

**Source & Parent Traceability**
Slice tag: `SW1`. Parent: `[SLICE][SW1]`. Depends on T1, T2.

**Implementation Prompt**
Files to create/modify:
- Add `tags`/`operations` entries for the 5 protocol controllers to the YAML files from T5 (catalog) and T6 (negotiation, data-transfer) — coordinate merge order since this touches the same files; if those PRs have already merged, rebase onto them; if not, this task can add its own section and the two PRs merge independently (different top-level YAML keys, low conflict risk).
- Extend each module's `OpenApiExamplesGenerator` (from T5/T6) to also emit `.protocol.json` examples via `serializeProtocol(...)` for: `ContractRequestMessage` (initial and counteroffer), `ContractOfferMessage`, `ContractAgreementMessage`, `ContractAgreementVerificationMessage`, `ContractNegotiationEventMessage`, `ContractNegotiationTerminationMessage`, catalog request, transfer request/start/completion/suspension/termination messages.
- Document `ROLE_CONNECTOR` auth note and error shapes (from each module's `*ExceptionAdvice`) for every protocol operation.
- `Mock values such as urn:uuid:CONSUMER_PID are not seeded values` — per spec §4.4.1, where an example must be executable against seeded data, build the object from seeded constants instead of raw mock fixtures.

**Dependencies**
Depends on: T1, T2 (hard); soft-depends on T5/T6 having created the YAML files it extends — recommend scheduling T8 to start once T5/T6 PRs have at least opened, to minimize merge conflicts, even though strictly it could start in parallel on its own branch and rebase.

**DSP Protocol / TCK Impact**: protocol-facing documentation only, no behavior change — recommend a TCK profile run (`mvn -pl connector -Ptck verify`) as a regression guard since this task touches protocol message serialization paths indirectly (new generator code calling `serializeProtocol`).

**Security & Quality Requirements**: generated protocol examples must not be committed (build-time only per spec §4.4.1) — verify `.gitignore`/build output location is correct.

**Delivery Stream & Parallelization**
Stream: `protocol-openapi`. Can run in parallel with: T5, T6, T7 (with the merge-order caveat above). Must wait for: T1, T2.

**Type / Priority / Milestone**: docs / P2 — medium / Next release.
**AI Model**: Opus 4.8 (complex, multi-file, architectural).
**Agent Mode**: Single agent — sequential execution.
**Agent Instructions**: Read `.github/skills/dsp-foundations/SKILL.md`, `.github/skills/dsp-catalog/SKILL.md`, `.github/skills/dsp-contract-negotiation/SKILL.md`, `.github/skills/dsp-transfer-process/SKILL.md` before writing protocol examples, to keep message shapes DSP-2025-1-accurate.

**Verification Checklist**
- [ ] `mvn clean verify` exits 0
- [ ] `mvn -pl connector -Ptck verify` passes (TCK regression guard)
- [ ] Generated `.protocol.json` examples match current `serializeProtocol` output exactly (spot-checked against an existing `*Test.java` fixture)
- [ ] Every protocol operation has `ROLE_CONNECTOR` auth note + documented error shapes
- [ ] All GitHub Actions CI checks pass on the PR (verified via `gh pr checks`)

---

### Q1 — Slice-level QA: Swagger exposure end-to-end

**Context**
Slice: `[SLICE][SW1]`. Functional outcome under test: a user of a deployed connector can open Swagger UI, read full endpoint documentation, and execute requests against seeded data end-to-end in both `INTERNAL` and `KEYCLOAK` auth modes.
Where: `connector` integration tests, manual Postman/UI walkthrough.

**Source & Parent Traceability**
Slice tag: `SW1`. Parent slice: `[SLICE][SW1]`. Sibling implementation tasks gated: T1, T2, T3, T4, T5, T6, T7, T8.

**Slice-level QA scope**
End-to-end flows:
- Swagger UI reachable and `/v3/api-docs/management-api` + `/v3/api-docs/dsp-protocol` return 200 with expected paths when enabled; both return 404 when disabled (builds on T3's IT pattern but asserts the full merged OpenAPI document, not just reachability).
- Documentation-quality pass: every operation of every "fully documented" controller (per spec §4.4 table) has summary + description; every tag has a description (extends T2's drift test to the fully-populated YAML set from T5–T8).
- Login-and-call flow: `POST /api/v1/auth/login` as `connector@mail.com`, then a protocol call under the seeded tenant (`engineering`) succeeds; repeat under a second enabled tenant and record/document the cross-tenant token behavior (per spec §8 risk).
- Admin login-and-call flow: `admin@mail.com` token against a management endpoint.
Cross-task integration points: T5/T6/T8's shared YAML files (merge correctness), T2's customizer correctly resolving examples generated by T5–T8's per-module generators.
TCK compliance: required (slice touches protocol-facing documentation plumbing) — `mvn -pl connector -Ptck verify`.
Out of scope here: per-task unit/IT coverage already verified in each task's own Verification Checklist.

**Dependencies**
Depends on: T1, T2, T3, T4, T5, T6, T7, T8.

**Delivery Stream & Parallelization**
Stream: `openapi-qa`. Must wait for: all 8 sibling implementation tasks.

**Type / Priority / Milestone**: test / P1 — high / Next release.
**AI Model**: Sonnet 4.6 (standard implementation).
**Agent Mode**: Single agent — sequential execution.
**Agent Instructions**: This task's implementation *is* running the Verification Checklist, plus adding the durable cross-cutting integration test it calls for; do not write product code. Docker must be running.

**Verification Checklist**
- [ ] `mvn clean verify` exits 0 (unit + integration tests, Docker running)
- [ ] New/extended Testcontainers integration test covers Swagger-enabled end-to-end doc retrieval + login-and-protocol-call flow
- [ ] Documentation-quality test passes across all fully-documented controllers from T5–T8
- [ ] `mvn -pl connector -Ptck verify` passes 100%
- [ ] SpotBugs scan shows no new findings (`./spotbugs-scan.sh`)
- [ ] [manual] Swagger UI "try it" walkthrough for one full negotiation→transfer flow against local provider/consumer, using the documented login examples
- [ ] [manual] Cross-tenant token behavior (token of tenant A against tenant B path) verified and the result documented for D1 to pick up
- [ ] All GitHub Actions CI checks pass on the PR (verified via `gh pr checks`)

---

### D1 — Slice-level docs: ADR, `doc/openapi.md`, CHANGELOG, security doc, remaining §6a items

**Context**
Slice: `[SLICE][SW1]`. What was implemented: springdoc-based Swagger UI/OpenAPI exposure across management and protocol APIs, property-toggled, with YAML-only documentation and build-time-generated examples.
Primary doc surfaces: `doc/decisions/`, new `doc/openapi.md`, `doc/README.md`, `CHANGELOG.md`, `doc/security.md`, `CONTRIBUTING.md`.

**Source & Parent Traceability**
Slice tag: `SW1`. Parent slice: `[SLICE][SW1]`. Sibling implementation tasks shipped: T1–T8. Gating QA task: Q1.

**Documentation scope**
What was implemented across the slice: see Context. Authoritative documents to update:
- New ADR in `doc/decisions/` (use the `decisions` skill) — springdoc adoption + exposure model (property-toggle default-off, two groups, YAML-only docs, build-time examples).
- New `doc/openapi.md` — enable/disable instructions, try-it walkthrough (login as `admin@mail.com`/`connector@mail.com`), groups explanation, tenant path-variable usage; linked from `doc/README.md` index.
- `CHANGELOG.md` — entry under the upcoming version listing the new dependency and exposure feature.
- `doc/security.md` — update describing the permitAll Swagger paths and why they remain safe (docs-only exposure, every actual call still token-gated).
- Remaining §6a items not already landed by T4: item 6 (`dsp-compliance-review` skill — optional "protocol example JSON still matches serializeProtocol output" checklist line, if not already added by T8) and item 7 (`CONTRIBUTING.md` contributor-facing description of the YAML-docs rule).
Out of scope here: Javadoc on new public/protected methods (covered in each impl task), the `openapi-docs.instructions.md` file itself (T4).

**API changes register**
| Endpoint / message | Change | Authoritative doc & section | Postman collection update |
|---|---|---|---|
| (none — doc-only slice) | No endpoint/message shape changed | — | no |

**What was learned**
- Generating OpenAPI examples at build time from existing model test fixtures (reusing `serializePlain`/`serializeProtocol`) — candidate for a short reusable pattern note in `doc/openapi.md` itself or a new `doc/` guide if other future features want the same approach.
- Cross-tenant token behavior finding from Q1 — document as a named caveat in `doc/security.md` and `doc/openapi.md`.

**Dependencies**
Depends on: T1, T2, T3, T4, T5, T6, T7, T8, Q1.

**Delivery Stream & Parallelization**
Stream: `openapi-docs`. Must wait for: all 8 implementation tasks + Q1.

**Type / Priority / Milestone**: docs / P2 — medium / Next release.
**AI Model**: Sonnet 4.6 (standard implementation).
**Agent Mode**: Single agent — sequential execution.
**Agent Instructions**: Doc-only; no product code unless a doc-only patch proves insufficient, in which case stop and comment.

**Verification Checklist**
- [ ] ADR created under `doc/decisions/` and indexed in `doc/decisions/README.md`
- [ ] `doc/openapi.md` created and linked from `doc/README.md`
- [ ] `CHANGELOG.md` has an entry for this slice under the upcoming version
- [ ] `doc/security.md` updated with the Swagger exposure note
- [ ] API changes register row(s) resolved as `None` (confirmed no endpoint/message shape changed)
- [ ] `CONTRIBUTING.md` references the YAML-docs rule
- [ ] Cross-tenant token behavior finding from Q1 documented
- [ ] All GitHub Actions CI checks pass on the PR (verified via `gh pr checks`)

## Next step

Switch to `create_issues` mode when ready: open the `[SLICE][SW1]` parent issue, then create
T1–T8, Q1, D1 against it following `.github/skills/task-decomposition/SKILL.md`'s GitHub
creation workflow (issue creation, alias/label application, dependency links, project board
placement, and the summary-table comment on the parent issue).
