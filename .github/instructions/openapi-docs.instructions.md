---
applyTo: ["**/rest/**/*Controller.java", "**/openapi/*-docs.yaml"]
---

# OpenAPI Documentation

Controller documentation is maintained in per-module YAML resources, not in Springdoc
annotations. Follow these rules whenever changing a REST controller or its `*-docs.yaml` file.

## YAML structure

Each module's `src/main/resources/openapi/<module>-docs.yaml` contains:

- `tags.<ControllerClassName>.name` and `tags.<ControllerClassName>.description` for the
  displayed tag.
- `operations.<methodName>.summary`, `description`, `requestExamples`, and `responses` for
  documented controller methods.
- Optional `schemas` entries for model-field descriptions.

The `operations` key is the controller method's default Springdoc `operationId`, which is the
method name. Documented controllers must not overload method names because an overloaded method
can produce a suffixed operation ID and make the YAML mapping ambiguous. Keep entries keyed by
the controller method name and verify them with the OpenAPI drift test.

## Documentation detail by controller

**Full management API documentation** includes operation summaries and descriptions, role and
state requirements, side effects, response descriptions, and relevant examples:

| Module | Controller |
|---|---|
| `catalog` | `CatalogAPIController`, `DatasetAPIController`, `DistributionAPIController`, `DataServiceAPIController` |
| `negotiation` | `ContractNegotiationAPIController`, `AgreementAPIController` |
| `data-transfer` | `DataTransferAPIController` |
| `tools` | `TenantAPIController` |
| `connector` | `UserAPIController` |

**Full DSP protocol documentation** covers the main catalog, negotiation, and transfer flows,
including JSON-LD examples, `ROLE_CONNECTOR` requirements, and protocol error responses:

| Module | Controller |
|---|---|
| `catalog` | `CatalogController` |
| `negotiation` | `ContractNegotiationProviderController`, `ContractNegotiationConsumerCallbackController` |
| `data-transfer` | `ProviderDataTransferController`, `ConsumerDataTransferCallbackController` |

**Brief documentation** provides a tag description and concise operation summaries for:
`OfferAPIController`, `ArtifactAPIController`, `ProxyAPIController`, `RestArtifactController`,
`FTPClientAPIController`, `AuditEventController`, `ApplicationPropertiesAPIController`,
`DashboardMetricsController`, `DSpaceVersionController`, `AuthController`, and
`KeycloakUserApiController`.

## Request and response examples

Examples are generated during the build from existing fixtures and serializers; generated JSON
belongs under `target/classes/openapi/examples/` and must not be committed. Use
`serializePlain` for management API (`/api/**`) examples and `serializeProtocol` for DSP
protocol examples so protocol JSON-LD context and type metadata match runtime serialization.
When a request or response payload changes, update the corresponding fixture/generator and YAML
example reference in the same change.

## Controller change checklist

1. Update the matching `operations.<methodName>` entry when changing a documented controller's
   method, path, parameters, payload, authorization role, response, or status code.
2. Update the matching `tags.<ControllerClassName>` entry when the controller's displayed name or
   responsibilities change.
3. Refresh generated examples when the serialized request or response shape changes; choose
   `serializePlain` for management APIs and `serializeProtocol` for DSP endpoints.
4. Keep protocol examples aligned with `serializeProtocol` output and document the applicable
   role and protocol error responses.
5. Run the relevant OpenAPI drift/documentation tests to check operation IDs, summaries,
   descriptions, tags, and examples.
6. Do not add Springdoc operation/response annotations to controllers or model classes.
