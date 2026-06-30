# Catalog

Example catalog (from init_data)

## Tenant isolation and @DBRef cascade safety

### Risk: unfiltered @DBRef resolution

`Catalog`, `Dataset`, and `Distribution` documents use Spring Data MongoDB `@DBRef` to store
cross-document references. Spring Data resolves a `@DBRef` by raw OID lookup — it does not
apply any tenant filter. In a multi-tenant deployment this means a `Catalog` owned by
tenant A could theoretically reference a `Dataset` owned by tenant B if the service layer
does not guard against it.

### Mitigation: service-layer tenantId consistency assertion

`CatalogService` applies a tenantId guard at every point where a cross-document reference
is written or updated. The guard is implemented by:

1. **Tenantid-scoped repository queries** — `CatalogRepository` exposes
   `findCatalogByDatasetIdAndTenantId` and `findCatalogByDataServiceIdAndTenantId`.
   These variants include `AND tenantId = :tenantId` in their query derivation, so the
   lookup only matches catalogs owned by the same tenant.

2. **`CatalogService.assertSameTenant()`** — called inside each update helper before any
   reference is persisted:
   - `updateCatalogDatasetAfterSave`
   - `updateCatalogDataServiceAfterSave`
   - `updateCatalogDistributionAfterSave`

   If the referenced entity belongs to a different tenant the method throws
   `CatalogErrorException`, which the `CatalogExceptionAdvice` maps to HTTP 404.

3. **Cross-tenant isolation integration test** — `CrossTenantIsolationIT` verifies that
   a tenant's catalog, datasets, and data services are not visible to a different tenant.

### Pattern for future development

Any service method that writes a `@DBRef`-backed cross-document link **must** call
`assertSameTenant()` before persisting. See ADR
[D-TEC-006](../../doc/decisions/technical/D-TEC-006-dbref-tenant-filter-mitigation.md)
for the full rationale and alternatives considered.

---

```json
{
  "@context": [
    "https://w3id.org/dspace/2025/1/context.jsonld"
  ],
  "@id": "urn:uuid:1dc45797-3333-4955-8baf-ab7fd66ac4d5",
  "@type": "Catalog",
  "title": "Testcatalog - TRUEConnector team information",
  "description": [
    {
      "value": "Sample connectorB catalog offering TRUEConnector team information",
      "@language": "en"
    }
  ],
  "participantId": "urn:example:DataProviderA",
  "keyword": [
    "Employee",
    "Information",
    "Test",
    "TRUEConnector team information",
    "ConnectorB"
  ],
  "dataset": [
    {
      "@context": [
        "https://w3id.org/dspace/2025/1/context.jsonld"
      ],
      "@id": "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
      "@type": "Dataset",
      "title": "TRUEConnector team information dataset",
      "description": [
        {
          "value": "Dataset offering TRUEConnector team information",
          "@language": "en"
        }
      ],
      "keyword": [
        "Personal information",
        "Employee",
        "TRUEConnector team",
        "REST"
      ],
      "hasPolicy": [
        {
          "@type": "Offer",
          "@id": "urn:uuid:fdc45798-a123-4955-8baf-ab7fd66ac4d5",
          "permission": [
            {
              "action": "use",
              "constraint": [
                {
                  "leftOperand": "count",
                  "operator": "lteq",
                  "rightOperand": "5"
                }
              ]
            }
          ]
        }
      ],
      "distribution": [
        {
          "@type": "Distribution",
          "format": "HttpData-PULL",
          "accessService": {
            "@type": "DataService",
            "@id": "urn:uuid:1dc45797-4444-conn-8baf-ab7fd66ac4d5",
            "conformsTo": "conformsToSomething",
            "creator": "Engineering Informatica S.p.A.",
            "description": [
              {
                "value": "DSP TRUEConnector service offering team information",
                "@language": "en"
              }
            ],
            "endpointDescription": "connector",
            "endpointURL": "http://localhost:8080/",
            "identifier": "DSP TRUE Connector Unique identifier for testing",
            "issued": "2024-04-23T18:26:00+02:00",
            "keyword": [
              "REST",
              "transfer"
            ],
            "modified": "2024-04-23T18:26:00+02:00",
            "theme": [
              "Data"
            ],
            "title": "DSP TRUE Connector"
          },
          "@id": "urn:uuid:1dc45797-pull-4932-8baf-ab7fd66ql4d5",
          "issued": "2024-04-23T18:26:00+02:00",
          "modified": "2024-04-23T18:26:00+02:00",
          "title": "HTTP Data PULL"
        },
        {
          "@type": "Distribution",
          "format": "HttpData-PUSH",
          "accessService": {
            "@type": "DataService",
            "@id": "urn:uuid:1dc45797-4444-conn-8baf-ab7fd66ac4d5",
            "conformsTo": "conformsToSomething",
            "creator": "Engineering Informatica S.p.A.",
            "description": [
              {
                "value": "DSP TRUEConnector service offering team information",
                "@language": "en"
              }
            ],
            "endpointDescription": "connector",
            "endpointURL": "http://localhost:8080/",
            "identifier": "DSP TRUE Connector Unique identifier for testing",
            "issued": "2024-04-23T18:26:00+02:00",
            "keyword": [
              "REST",
              "transfer"
            ],
            "modified": "2024-04-23T18:26:00+02:00",
            "theme": [
              "Data"
            ],
            "title": "DSP TRUE Connector"
          },
          "@id": "urn:uuid:1dc45797-push-4932-8baf-ab7fd66ql4d5",
          "issued": "2024-04-23T18:26:00+02:00",
          "modified": "2024-04-23T18:26:00+02:00",
          "title": "HTTP Data PUSH"
        }
      ],
      "conformsTo": "conformsToSomething",
      "creator": "Engineering Informatica S.p.A.",
      "identifier": "Unique identifier for test Dataset",
      "issued": "2024-04-23T18:26:00+02:00",
      "modified": "2024-04-23T18:26:00+02:00",
      "theme": [
        "Data"
      ]
    }
  ],
  "distribution": [
    {
      "@type": "Distribution",
      "format": "HttpData-PULL",
      "accessService": {
        "@type": "DataService",
        "@id": "urn:uuid:1dc45797-4444-conn-8baf-ab7fd66ac4d5",
        "conformsTo": "conformsToSomething",
        "creator": "Engineering Informatica S.p.A.",
        "description": [
          {
            "value": "DSP TRUEConnector service offering team information",
            "@language": "en"
          }
        ],
        "endpointDescription": "connector",
        "endpointURL": "http://localhost:8080/",
        "identifier": "DSP TRUE Connector Unique identifier for testing",
        "issued": "2024-04-23T18:26:00+02:00",
        "keyword": [
          "REST",
          "transfer"
        ],
        "modified": "2024-04-23T18:26:00+02:00",
        "theme": [
          "Data"
        ],
        "title": "DSP TRUE Connector"
      },
      "@id": "urn:uuid:1dc45797-pull-4932-8baf-ab7fd66ql4d5",
      "issued": "2024-04-23T18:26:00+02:00",
      "modified": "2024-04-23T18:26:00+02:00",
      "title": "HTTP Data PULL"
    },
    {
      "@type": "Distribution",
      "format": "HttpData-PUSH",
      "accessService": {
        "@type": "DataService",
        "@id": "urn:uuid:1dc45797-4444-conn-8baf-ab7fd66ac4d5",
        "conformsTo": "conformsToSomething",
        "creator": "Engineering Informatica S.p.A.",
        "description": [
          {
            "value": "DSP TRUEConnector service offering team information",
            "@language": "en"
          }
        ],
        "endpointDescription": "connector",
        "endpointURL": "http://localhost:8080/",
        "identifier": "DSP TRUE Connector Unique identifier for testing",
        "issued": "2024-04-23T18:26:00+02:00",
        "keyword": [
          "REST",
          "transfer"
        ],
        "modified": "2024-04-23T18:26:00+02:00",
        "theme": [
          "Data"
        ],
        "title": "DSP TRUE Connector"
      },
      "@id": "urn:uuid:1dc45797-push-4932-8baf-ab7fd66ql4d5",
      "issued": "2024-04-23T18:26:00+02:00",
      "modified": "2024-04-23T18:26:00+02:00",
      "title": "HTTP Data PUSH"
    }
  ],
  "service": [
    {
      "@type": "DataService",
      "@id": "urn:uuid:1dc45797-4444-conn-8baf-ab7fd66ac4d5",
      "conformsTo": "conformsToSomething",
      "creator": "Engineering Informatica S.p.A.",
      "description": [
        {
          "value": "DSP TRUEConnector service offering team information",
          "@language": "en"
        }
      ],
      "endpointDescription": "connector",
      "endpointURL": "http://localhost:8080/",
      "identifier": "DSP TRUE Connector Unique identifier for testing",
      "issued": "2024-04-23T18:26:00+02:00",
      "keyword": [
        "REST",
        "transfer"
      ],
      "modified": "2024-04-23T18:26:00+02:00",
      "theme": [
        "Data"
      ],
      "title": "DSP TRUE Connector"
    }
  ],
  "conformsTo": "conformsToSomething",
  "creator": "Engineering Informatica S.p.A.",
  "identifier": "Unique identifier for test ConnectorB Catalog",
  "issued": "2024-04-23T18:26:00+02:00",
  "modified": "2024-04-23T18:26:00+02:00",
  "theme": [
    "Data"
  ]
}
```