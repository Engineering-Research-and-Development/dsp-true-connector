# OpenAPI Documentation Customizer — Design

Date: 2026-10-07
Status: Approved for implementation
Scope: Issue #353

## Goal

Add infrastructure for attaching module-owned OpenAPI YAML descriptions and generated JSON
examples to springdoc operations without changing controllers or adding springdoc dependencies to
business modules. Keep the existing Swagger configuration and its defaults unchanged.

## Existing context

The requested base branch, `feature/swagger_ui_support`, already contains the dependency and
`OpenApiConfig` work from #352. Although #352 remains open in GitHub, its prerequisite code is
present on the base branch. No `openapi/*-docs.yaml` or generated example resources exist yet.

`OpenApiConfig` already uses springdoc 2.8.17's `OperationCustomizer` and `OpenApiCustomizer`
interfaces. The docs infrastructure will live in `connector`, which can discover resources from
the other modules on the runtime classpath.

## Design

### Resource loading and customization

Add a small, testable loader for `classpath*:openapi/*-docs.yaml` and
`classpath*:openapi/examples/*.json`. YAML resources are processed deterministically. Missing
resources are a no-op; a malformed YAML resource is logged and skipped rather than preventing
startup. The loader exposes the documented `tags`, `operations`, and `schemas` entries without
coupling business modules to springdoc.

An operation customizer associates an operation with its handler's controller simple class name
and method-name operation ID. It applies summary, description, request examples, and response
descriptions when entries exist. Tag metadata is applied to the OpenAPI document. Schema field
descriptions are applied by model simple class name and property name. Named example JSON is
registered in `components/examples`; management (`/api/**`) operations resolve `.plain.json`
examples and DSP operations resolve `.protocol.json` examples. Missing example files are ignored
without adding a dangling reference.

An optional `methodParameters` selector disambiguates overloads while retaining the method-name
operation key. For #353's `getAllTenants` case, it allows documentation to target only the paged
handler accepting `HttpServletRequest, int, int, String[]`, not the `/list` overload.

### Drift validation

Use the real `RequestMappingHandlerMapping` registry to collect handler method names and validate
every operation ID referenced by the loaded docs resources. An unknown documented operation ID
must fail the validation; an empty docs-resource set must pass. The validator also honors an
optional overload selector. Tests cover empty resources, unknown IDs, and the selected paged
`getAllTenants` overload.

### Example generator Maven wiring

Add `exec-maven-plugin` configuration to root `pom.xml`'s `pluginManagement` only. Its
`process-test-classes` execution uses `classpathScope=test` and a per-module generator main-class
property. Defining it in `pluginManagement` does not activate it: later module tasks opt in and
provide their generator classes. No module opts in as part of #353.

### Alternatives

1. **Central connector customizers (chosen):** one implementation serves all module resources and
   respects existing module boundaries.
2. **Module-local springdoc customizers:** rejected because it duplicates integration logic and
   adds protocol-library coupling to business modules.
3. **JUnit-based example generation:** rejected because skipping tests could produce packaged jars
   without generated examples.

## Verification

- Unit tests verify loading and applying docs, missing-resource behavior, malformed-resource
  handling, example resolution, and drift failures for unknown operation IDs.
- A test using `RequestMappingHandlerMapping` verifies documented operation IDs against actual
  MVC handlers; no docs files is a passing case.
- The optional method-parameter selector matches only the paged `getAllTenants` method.
- Confirm the exec plugin is present only under root `pluginManagement`, with no module opt-in.
- Run `mvn clean verify` as the required final local gate. No DSP TCK run is required because the
  change is documentation infrastructure and does not modify protocol behavior.

## Delivery

Create the issue branch from `feature/swagger_ui_support`, commit the implementation, and open a
pull request targeting `feature/swagger_ui_support`, as requested.
