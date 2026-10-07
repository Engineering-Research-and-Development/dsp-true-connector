# Swagger UI / OpenAPI Exposure — Design and Implementation Plan

Date: 2026-10-06
Status: Draft for review (no code changed)

## 1. Goal

Expose the connector's HTTP endpoints through Swagger UI (OpenAPI 3) so users of a deployed
server can read endpoint documentation and execute requests against seeded mock data
(`initial_data*.json`) end to end, in both `INTERNAL` and `KEYCLOAK` auth modes (DCP is out of scope).

## 2. Decisions (agreed)

| Topic | Decision |
|---|---|
| Library | `springdoc-openapi-starter-webmvc-ui` 2.8.x (Spring Boot 3.5, Spring MVC) |
| Exposure | Property-toggled: `springdoc.api-docs.enabled` / `springdoc.swagger-ui.enabled`, default `false`, `true` only on demo/server profile |
| Tokens | No new token endpoint. `POST /api/v1/auth/login` (`AuthController`, same contract in INTERNAL and KEYCLOAK) is used for both tokens: `admin@mail.com` (API) and `connector@mail.com` (`ROLE_CONNECTOR`, protocol) |
| Groups | Two Swagger groups: **Management API** (`/api/**`) and **DSP Protocol** (`/{tenantId}/**`) |
| Tenant | `tenantId` path example defaults to the seeded tenant (`engineering`); any enabled tenant can be typed in |
| Documentation placement | **YAML only** (confirmed). Outside controllers: per-module YAML docs files + springdoc customizers; no `@Operation`/`@ApiResponses` annotations anywhere (controllers unchanged); examples generated at build time from model test fixtures, not committed |
| Detail level | Full descriptions + examples: negotiation, agreement, data transfer, tenant, user, catalog, dataset, distribution, data service (API controllers) and main protocol flows. Brief: offer `/validate`, artifact, proxy, audit, properties, FTP, dashboard, version |

## 3. Findings from code analysis

- 24 controllers, ~118 mappings. Only `connector` is executable; dependency, config and customizers go there; each module contributes only resource files (docs YAML, generated examples).
- Security (`ConnectorSecurityConfig`): admin chain `/api/**` (order 1), protocol chain (order 2, `ROLE_CONNECTOR`), default chain `permitAll` (order 3). Swagger paths fall into the default chain.
- `/api/v1/auth/**` is `permitAll`; `AuthController` has no role restriction on login, so `connector@mail.com` (seeded, `ROLE_CONNECTOR`, tenant `engineering`) obtains a connector JWT (used by `ConnectorCredentialProviderImpl`, Postman collection).
- Protocol endpoints take `JsonNode` bodies (JSON-LD): springdoc cannot infer schemas, so examples must be hand-written.
- Protocol tenant is a path variable resolved by `TenantAwareProtocolController.resolveTenant` -> `findEnabledTenantById`. Management API: super-admin tokens have no tenant and can use the `X-Tenant-Id` header (`ApiTenantContextFilter`).

## 4. Design

### 4.1 Dependencies
- Root `pom.xml`: `springdoc.version` property + `dependencyManagement` entry.
- `connector/pom.xml`: starter dependency.

### 4.2 Configuration
- New `OpenApiConfig` in `connector` (`@ConditionalOnProperty springdoc.swagger-ui.enabled=true`):
  - `OpenAPI` bean: title, version, description with the "how to try it" walkthrough, configurable server URL (`openapi.server-url`), `bearerAuth` JWT scheme.
  - `GroupedOpenApi` beans: `management-api`, `dsp-protocol` (optionally split catalog / negotiation / transfer).
  - `OpenApiCustomizer` to order tags by workflow: Auth, Tenant, User, Catalog, Dataset, Distribution, Data Service, Negotiation, Agreement, Transfer, then the rest.
  - Optional `X-Tenant-Id` header parameter on the management group (super-admin tenant switching).
- Properties: defaults `false` in base config; `true` in demo/server property files (docker `ci/docker/*`, `terraform/app-resources/*`). The packaged jar excludes `.properties`, so deployment property files must be updated explicitly.

### 4.3 Security
- Default chain: permit `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` (only documentation is public; every call still requires a token).
- Verify CORS config and `TckProtocolForwardingFilter` do not interfere.
- Update `doc/security.md`.

### 4.4 Documentation without cluttering controllers

Principle: controllers stay unchanged (no `@Operation`, `@ApiResponses`, or JSON in annotations). Documentation is attached externally by springdoc customizers, keyed by the stable default `operationId` (controller method name) and tag (controller class name).

- **Per-module docs files** (owned by the module, resources only, no springdoc dependency in business modules): `src/main/resources/openapi/<module>-docs.yaml`. Each entry: tag description, operation summary/description (roles, required/resulting state, side effects), response descriptions (200/201/400/401/403/404/409, shapes from the `*ExceptionAdvice` classes), and references to named examples.
- **Customizers in `connector`** (`OpenApiConfig`): an `OpenApiCustomizer`/`OperationCustomizer` loads `classpath*:openapi/*-docs.yaml` and the generated example files (4.4.1) and applies them to operations. Missing entries are tolerated (operation just stays undocumented), a test lists undocumented operations.
- **Model/DTO field descriptions:** also YAML-only (a `schemas:` section in the same docs file, applied via a schema customizer), so no springdoc annotations are added to models or controllers.
- YAML shape per entry: `tags.<ControllerClassName>.{name,description}`, `operations.<methodName>.{summary,description,requestExamples,responses}`; operation keys are the default springdoc `operationId` (method name). Overloaded method names are not allowed in documented controllers (the drift test fails on ambiguity).
- Controller drift is caught by a test: every `operationId` referenced in a docs file must exist, and every fully-documented controller operation must have an entry.

Detail level per controller:

| Controller | Content |
|---|---|
| ContractNegotiationAPIController (11) | State machine overview (REQUESTED -> OFFERED -> ACCEPTED -> AGREED -> VERIFIED -> FINALIZED / TERMINATED); per operation: consumer vs provider role, required/resulting state, counterparty message side effect; `/offer` and `/request` new vs existing variants; list filters |
| AgreementAPIController (1) | When enforcement is evaluated and what the policy check returns |
| DataTransferAPIController (10) | Lifecycle (REQUESTED -> STARTED -> SUSPENDED -> COMPLETED / TERMINATED); HTTP_PULL vs HTTP_PUSH; agreement prerequisite; download/view content types and presigned flow |
| TenantAPIController (8) | `ROLE_SUPER_ADMIN`; tenant isolation / per-tenant bucket; enable/disable effect on protocol calls; delete consequences; `X-Tenant-Id` |
| UserAPIController (7) | Role matrix (SUPER_ADMIN vs ADMIN self-service); `CONNECTOR` hidden from lists; INTERNAL vs KEYCLOAK differences; password rules |
| CatalogAPIController (5) | Catalog -> dataset -> distribution -> data service hierarchy |
| DatasetAPIController (7) | DCAT fields; FILE vs EXTERNAL artifact; S3 key = dataset id; catalog hides datasets missing in S3 |
| DistributionAPIController (5), DataServiceAPIController (5) | DCAT role, links, required fields from builder validation |
| Protocol controllers (catalog, negotiation, transfer, callbacks) | Main-flow JSON-LD examples; `ROLE_CONNECTOR` auth note; error shapes |
| Brief level | Offer `/validate`, artifact, proxy, audit, properties, FTP, dashboard, version, auth: tag + one-line summary; auth gets login examples for admin and connector users |

All new Java code keeps Javadoc (Checkstyle `JavadocMethod`/`JavadocStyle`); no fully qualified names; use imports.

#### 4.4.1 Examples generated at build time from existing model fixtures
Model classes already have tests/fixtures that build mock objects (e.g. `NegotiationMockObjectUtil`, `ContractRequestMessageTest`) and serialize them with `serializePlain` (API endpoints, no JSON-LD prefixes) or `serializeProtocol` (DSP endpoints, with `@context`/`@type`). Examples reuse these, so they always match the real serializers. Generated files are **not committed**.

Constraint: `src/test` code is not on the runtime classpath, so it cannot be called from controllers or from the running app; the `tests` jar must not be added to the production jar. Therefore examples are materialised at build time into each module's `target/classes`, so they ship inside the module jar:
- Each module gets a small generator class in `src/test/java` (e.g. `OpenApiExamplesGenerator`, a `main` method) that serializes the module's mock objects and writes `openapi/examples/<name>.plain.json` (API) or `<name>.protocol.json` (DSP) into `target/classes`.
- It runs from Maven via `exec-maven-plugin` bound to `process-test-classes` with `classpathScope=test` (after test classes compile, before `package`), configured once in the root `pom.xml` `pluginManagement` and enabled per module.
- Fallback if exec-plugin is not wanted: a JUnit generator test writing to `target/classes` during `test` (still before `package`), but then `-DskipTests` produces a jar without examples. Customizer must tolerate missing example files either way; an IT with Swagger enabled asserts they are present.
- Rule: `/api/**` operations use `*.plain.json`, protocol operations use `*.protocol.json`.
- Mock values such as `urn:uuid:CONSUMER_PID` are not seeded values. Where an example must be executable against seeded data (dataset id, tenant, assigner/assignee), the generator builds the object from seeded constants instead.
- Initial example set: ContractRequestMessage (initial and counteroffer), ContractOfferMessage, ContractAgreementMessage, ContractAgreementVerificationMessage, ContractNegotiationEventMessage, ContractNegotiationTerminationMessage, the API negotiation request/offer payloads, catalog request, transfer request/start/completion/suspension/termination messages, and catalog/dataset/distribution/data service API payloads.
- Docs files reference examples by name; the customizer resolves them to `components/examples`.

### 4.5 Seed data
Examples reference existing seeded values. If seed shapes change, all 12 `initial_data*.json` files must be updated together. Currently none is expected.

## 5. Testing

- Integration test (`*IT`, `BaseIntegrationTest`): with Swagger enabled, `/v3/api-docs/management-api` and `/v3/api-docs/dsp-protocol` return 200 and contain key paths; with it disabled, UI and docs return 404.
- Documentation-quality test: every operation of the fully documented controllers has summary + description; every tag has a description.
- Flow test: login as `connector@mail.com` via `/api/v1/auth/login`, call a protocol endpoint under the seeded tenant; additionally under a second tenant to record and document cross-tenant token behaviour.
- Run `mvn clean verify` (Docker required) and the TCK profile (`mvn -pl connector -Ptck verify`); compliance must not regress. Run SpotBugs.

## 6. Documentation and process

- ADR in `doc/decisions/` (use the `decisions` skill) for introducing springdoc and the exposure model.
- New `doc/openapi.md` (enable/disable, try-it walkthrough, groups, tenants); link from `doc/README.md`.
- Update `CHANGELOG.md` and `doc/security.md`.

## 6a. Keeping the YAML in sync with controllers (process step)

Because docs live outside the controller, a controller change can silently leave Swagger stale. Besides the build-time drift test (every documented key must exist; every operation of a fully documented controller must have an entry), the rule is written into the instruction surfaces:

Files to update (checked in this repo):
1. `AGENTS.md` — add a Non-Negotiable Constraint: changing a REST controller (new/renamed/removed method, path, params, request/response shape, roles, status codes) requires updating the matching `openapi/<module>-docs.yaml` entry (and example generator if the payload changed) in the same change. Also add the new instruction file to the Key Documents table.
2. New `.github/instructions/openapi-docs.instructions.md` with `applyTo` for `**/rest/**/*Controller.java` and `**/openapi/*-docs.yaml`: YAML shape, key naming, which doc level (full vs brief) a controller has, how examples are generated (`serializePlain` for `/api/**`, `serializeProtocol` for DSP), and the "change controller -> change YAML" checklist. This is the file that gets auto-loaded when editing controllers.
3. `.github/instructions/java.instructions.md` and its mirror `.github/skills/java-development/SKILL.md` — one-line pointer in a "REST controllers" note to the new instruction.
4. `.github/skills/task-implementation/SKILL.md` and its mirror `.claude/skills/task-implementation/SKILL.md` — the "task-local vs slice-level documentation boundary" currently says API documentation belongs to the slice docs task. Clarify: the OpenAPI YAML entry is part of the implementation task (it is code-adjacent, verified by the drift test); narrative docs (`doc/openapi.md`, architecture) stay in the docs task. Add the YAML update to the implementation Verification Checklist expectations.
5. `.github/skills/task-decomposition/SKILL.md` (and `.claude` mirror) — implementation tasks that touch controllers must list the YAML update in their Verification Checklist; the `task.yml` issue template may get an optional checkbox "OpenAPI docs YAML updated".
6. `.github/skills/dsp-compliance-review/SKILL.md` — optional: add "protocol example JSON still matches serializeProtocol output" to the review checklist.
7. `CONTRIBUTING.md` / `doc/openapi.md` — contributor-facing description of the rule.

This belongs to delivery slice 6 (docs), but item 2 and the AGENTS.md rule should land with slice 1 so slices 2-5 already follow them.

## 7. Delivery slices

1. **Infrastructure:** dependency, docs/examples loading customizers, example generator + exec-plugin wiring, `OpenApiConfig`, toggle, security permits, deployment properties, enable/disable IT.
2. **Catalog-side API docs:** catalog, dataset, distribution, data service (full); offer, artifact, proxy (brief).
3. **Negotiation and transfer API docs:** negotiation, agreement, data transfer (full).
4. **Administration API docs:** auth (brief + login examples), tenant, user (full); audit, properties, FTP, dashboard, version (brief).
5. **Protocol docs:** main-flow JSON-LD examples.
6. **Docs and ADR:** ADR, `doc/openapi.md`, changelog, security doc, remaining instruction/skill updates from section 6a (AGENTS.md rule and `openapi-docs.instructions.md` land in slice 1).

Slices 2-4 are independent after slice 1; slice 5 depends on slice 1; slice 6 depends on all.

## 8. Risks and open points

- Public demo server with weak seeded passwords: demo environment should override passwords or restrict network access.
- Swagger can execute state-changing calls; acceptable for demo with mock data.
- Cross-tenant token behaviour (token of tenant A against tenant B path) must be verified in the filters and documented.
- KEYCLOAK mode requires `connector@mail.com` (connector role) to exist in the Keycloak realm; verify in `KeycloakAuthServiceImpl` / realm config and document as a deployment prerequisite.
- Examples are generated at build time from model test fixtures (not committed); seeded-value overrides need manual review, and the exec-plugin/skip-tests behaviour must be verified in the first slice.
- Some API request DTOs may have no existing model test or fixture (to be checked per DTO during decomposition).
