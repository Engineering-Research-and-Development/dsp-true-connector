# TRUE Connector repository instructions

## Build, lint, and test

- Use Maven from the repository root. This is a multi-module build with `connector`, `catalog`, `tools`, `negotiation`, and `data-transfer`.
- Canonical full verification command:
  - `mvn clean verify`
  - This is the command used in the docs and CI. It runs Checkstyle in `validate`, unit tests, integration tests, and JaCoCo reporting.
  - Docker must be running for integration tests because `connector` integration tests use Testcontainers for MongoDB and MinIO.
- Lint / style gate:
  - `mvn validate`
  - `mvn checkstyle:check` is also valid when you only want the Checkstyle gate.
- Verify a single module and its dependencies:
  - `mvn -pl negotiation -am verify`
- Unit tests only:
  - `mvn test`
- Run all tests in a single module:
  - `mvn -pl catalog -am test`
- Run one unit test class:
  - `mvn -pl connector -am -Dtest=UserServiceTest test`
  - Replace `connector` with the target module as needed.
- Run one unit test method:
  - `mvn -pl connector -am -Dtest=UserServiceTest#loadUserByUsernameShouldReturnUser test`
- Run one integration test (`*IT.java`, via Failsafe):
  - `mvn -pl connector -am -Dit.test=ApplicationPropertyIT verify`
- Run one integration test method:
  - `mvn -pl connector -am -Dit.test=ApplicationPropertyIT#putPropertySuccessfulTest verify`
- Integration tests currently live in the `connector` module and are built around its shared `BaseIntegrationTest`.
- Run the TCK profile:
  - `mvn -pl connector -Ptck verify`
- Run the single TCK test class:
  - `mvn -pl connector -Ptck -Dit.test=TCKCompliance verify`
- The `tck` profile is defined in the root `pom.xml`; it disables the normal Surefire flow and reconfigures Failsafe to run only `TCKCompliance.java`.
- CI also runs dockerized end-to-end API suites with Docker Compose plus Newman:
  - start the test environment: `docker compose -f ci/docker/docker-compose.yml --env-file ci/docker/.env up -d`
  - run a collection: `newman run ci/docker/test-cases/api-tests/api-endpoints-tests.json`
  - stop the environment: `docker compose -f ci/docker/docker-compose.yml --env-file ci/docker/.env down -v`
- Other Newman suites live under `ci/docker/test-cases/` and cover negotiation, data transfer, dataset, and connector API flows.

## High-level architecture

- `connector` is the only executable module. `connector/src/main/java/it/eng/connector/ApplicationConnector.java` is the Spring Boot entry point and component-scans all five module package trees.
- `tools` is the shared substrate used everywhere else. It owns cross-cutting pieces such as authentication helpers, S3/MinIO support, REST clients, shared serializers/utilities, audit/event infrastructure, and shared controller constants.
- `catalog` handles catalog, dataset, distribution, offer, and artifact management for the dataspace catalog side.
- `negotiation` owns contract negotiation plus policy evaluation and enforcement.
- `data-transfer` owns transfer lifecycle handling and transfer strategies for the actual data movement step.

### Request surfaces

- Each business module splits HTTP handling into two surfaces:
  - `rest/api`: management/admin endpoints, typically under `/api/v1/...`
  - `rest/protocol`: DSP/DCAT-facing endpoints such as `/catalog`, `/negotiations`, and `/transfers`
- API controllers usually return `GenericApiResponse<?>`.
- Protocol controllers usually exchange `JsonNode` and rely on the module serializer to produce DSP-compliant payloads.

### Serialization boundary

- This codebase maintains a strict distinction between:
  - plain/internal JSON for admin APIs
  - protocol JSON(-LD) for DSP endpoints
- Use the module serializer instead of raw `ObjectMapper` when crossing that boundary:
  - `CatalogSerializer`
  - `NegotiationSerializer`
  - `TransferSerializer`
  - `ToolsSerializer`
- The key method pairs are `serializePlain` / `deserializePlain` versus `serializeProtocol` / `deserializeProtocol`.
- When docs and code disagree on protocol examples, prefer the serializer/constants in code. Some markdown docs still show older protocol examples.

### Persistence and domain flow

- Persistence is MongoDB-based across modules.
- Repositories are Spring Data repositories; several repositories also use the shared dynamic filtering support from `tools`.
- The main end-to-end flow is:
  - catalog exposes offers and distributions
  - negotiation creates and stores agreements
  - data-transfer consumes those agreements to create and progress transfer processes
- Policy checks live in `negotiation` and are part of the transfer/consumption path, not a separate subsystem.

### Security model

- Authentication in `connector` is configured via `ConnectorSecurityConfig`.
- The active authentication provider is selected with the `application.auth.provider` property.
- Optional DCP-related behavior can be enabled with `application.auth.dcp.enabled=true`.
- Protocol endpoints (`/connector/**`, `/catalog/**`, `/negotiations/**`, `/transfers/**`) require `ROLE_CONNECTOR`.
- Management endpoints under `/api/**` require `ROLE_ADMIN`.
- `doc/security.md` is the source-of-truth doc for authentication, TLS, and OCSP behavior. Read it before changing authentication behavior.

### Runtime profiles and roles

- The repository carries separate Spring property sets for the main runtime roles:
  - `connector/src/main/resources/application-consumer.properties`
  - `connector/src/main/resources/application-provider.properties`
  - `connector/src/main/resources/application-tck.properties`
- `consumer` runs on port `8080`.
- `provider` runs on port `8090`.
- The TCK profile is separate from the normal verify flow.

## Key repository conventions

- Use `it.eng.tools.controller.ApiEndpoints` for admin API route constants instead of hardcoding `/api/v1/...` paths in controllers and tests.
- New or changed protocol/admin payload handling should follow the existing serializer split rather than introducing ad-hoc JSON mapping.
- Model classes are usually mutable Jackson/Lombok POJOs with:
  - private no-args constructors
  - inner `Builder.newInstance()` builders
  - Bean Validation checks inside `build()`
  - Spring Data Mongo annotations when persisted
- This pattern is especially common in `catalog`, `negotiation`, `data-transfer`, and `tools` models. When adding a model, mirror the existing builder + validation approach instead of switching to a different style inside the same area.
- Public and protected Java methods should have Javadoc. Checkstyle enables `JavadocMethod` and `JavadocStyle` via `scripts/ci/checkstyle.xml`, so missing method Javadoc can fail `mvn validate`.
- JUnit conventions are repository-specific:
  - unit tests are `*Test.java`
  - integration tests are `*IT.java`
  - the TCK test is `connector/src/test/java/it/eng/connector/tck/TCKCompliance.java`
- Many integration tests extend `connector/src/test/java/it/eng/connector/integration/BaseIntegrationTest.java`.
  - That base class boots the app on port `8080`
  - starts MongoDB and MinIO Testcontainers
  - enables MockMvc and WireMock
- Data-transfer strategy resolution is centralized in `DataTransferStrategyFactory`. At the moment, `HTTP_PULL` and `HTTP_PUSH` are wired; there is an `S3TransferStrategy` type in the constructor, but it is not currently registered in the strategy map.
- Packaging is not a standard Spring Boot fat-jar flow. `connector/pom.xml` builds `dsp-true-connector.jar` plus `target/dependency-jars/`, and the Docker image in `connector/Dockerfile` expects that layout. If you change packaging, startup, or resource loading, update both files together.
- Dependency versions are centralized in the root `pom.xml` through properties and `dependencyManagement`, including explicit security/CVE override comments for several libraries. Prefer updating versions there rather than pinning them ad hoc in module POMs.
- The packaged connector jar excludes `.properties`, `.json`, and certificate material from the jar. Be careful when changing configuration loading or assuming resources are bundled the same way they are during local development.
- In Keycloak mode, user management behavior differs from the default Mongo-backed mode. Do not assume `/api/v1/users` is always available without checking the active auth mode first.
- `connector` test scope depends on `tests` classifier jars from `catalog`, `negotiation`, and `data-transfer`, so changes to shared test fixtures/utilities in those modules can affect connector integration tests and TCK runs.

## Existing scoped instruction files

- This repository already carries additional Copilot instruction files under `.github/instructions/`.
- The most relevant ones for day-to-day code changes are:
  - `java.instructions.md` for Javadoc/Checkstyle expectations
  - `model-class-guidelines.instructions.md` for the model-builder pattern
  - `junittest.instructions.md` for JUnit 5 / Mockito test conventions
- Follow those scoped instructions when working in their matching file patterns.

## Available repository skills

- The scoped instruction content above is also available as repository skills under `.github/skills/`.
- For workflow orchestration in Copilot-first sessions, prefer the Copilot-native workflow skills under `.github/skills/` as the primary entry point. Imported `.claude/skills/` files are secondary compatibility mirrors of the staged workflow.
- Copilot-native workflow skills:
  - `functional-slicing`
  - `task-decomposition`
  - `task-implementation`
  - `slice-implementation`
  - `playwright-cli`
- Java and workflow guidance skills:
  - `java-development`
  - `junit-5-tests`
  - `model-class-guidelines`
  - `java-11-to-java-17-upgrade`
  - `java-17-to-java-21-upgrade`
  - `github-actions-ci-cd-best-practices`
- DSP protocol guidance skills:
  - `dsp-foundations`
  - `dsp-catalog`
  - `dsp-transfer-process`
  - `dsp-contract-negotiation`
  - `dsp-compliance-review`
- Use the matching skill when you want broader task-level guidance rather than path-scoped instruction matching alone.

## Useful project docs

- `README.md` gives the module map and environment requirements.
- `doc/development_procedure.md` defines the team’s expected build/test flow; use `mvn clean verify` as the default validation command.
- `catalog/doc/catalog.md`, `negotiation/doc/model.md`, and `data-transfer/doc/data-transfer.md` are the best starting points for the three business modules.
- `KEYCLOAK_INTEGRATION_COMPLETE_SUMMARY.md` and `doc/security.md` are the best starting points for security/authentication changes.
