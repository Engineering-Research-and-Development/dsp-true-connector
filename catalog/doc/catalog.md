# Catalog — User Guide

> **See also:** [Catalog Technical Documentation](./catalog-technical.md) | [Artifact Upload](./artifact-upload.md) | [Documentation Index](../../doc/README.md#catalog--artifacts)

---

## What is the Catalog?

The **Catalog** is the directory of data that a provider makes available for sharing. Think of it like an online product catalogue: a consumer visits the provider's connector and browses the catalog to see what data exists, how it can be accessed, and under what conditions it may be used.

- **Provider** — the organisation that owns and shares data. They populate the catalog with descriptions of their datasets.
- **Consumer** — the organisation that wants to use data. They read the catalog to discover what is available before starting a negotiation.

No actual data is transferred when browsing the catalog — only metadata describing the data.

---

## How the Catalog Works

1. A **Consumer connector** sends a Catalog Request to the Provider connector.
2. The **Provider connector** returns its Catalog — a structured description of all available data.
3. The Consumer browses the Catalog to find a Dataset they want.
4. If they want a specific dataset, they can request it individually.
5. The Consumer then starts the **negotiation** process to agree on terms, and finally initiates a **data transfer**.

This discovery step is defined by the [Dataspace Protocol (DSP) Catalog Protocol](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/). The TRUE Connector implements this protocol, so any compatible DSP connector can discover your catalog automatically.

### Retrieve a Remote Catalog (Consumer Side)

Use the proxy endpoint to fetch a provider's catalog without implementing the DSP protocol yourself:

```bash
curl -X POST http://localhost:8080/api/v1/proxy/catalogs \
  -H "Content-Type: application/json" \
  -u admin:password \
  -d '{ "Forward-To": "http://provider-host:8080" }'
```

---

## Catalog Structure

The catalog is a hierarchy of objects:

```
Catalog  (top-level container — one per connector)
├── Dataset  (a single data offering, e.g. "Employee Records")
│   ├── Offer / Policy  (usage rules, e.g. "may be used up to 5 times")
│   └── Distribution  (how to access the data)
│       └── DataService  (the technical endpoint, e.g. HTTP endpoint URL)
├── Distribution  (top-level distributions available across datasets)
└── DataService  (registered services)
```

### Catalog

The top-level container. A connector typically has one catalog. It carries metadata such as title, description, participant ID, and references to all Datasets, Distributions, and DataServices.

### Dataset

A Dataset represents a single piece of data or data collection that a provider offers. Each dataset has:
- Descriptive metadata (title, description, keywords).
- One or more **Offers** defining the usage policy.
- One or more **Distributions** describing how to access the data.
- An **Artifact** — the actual data (a file stored in S3 or a URL to an external system).

### Distribution

A Distribution describes *one way* to access a dataset. The same dataset can have multiple distributions, for example one for HTTP-PULL and one for HTTP-PUSH. Each distribution references a **DataService** for the endpoint details and carries a `format` value such as `HttpData-PULL` or `HttpData-PUSH`.

### DataService

A DataService describes the technical endpoint that provides access to the data. The most important field is `endpointURL` — the URL of the connector serving the data.

### Offer (Policy)

An Offer describes the usage rules (policy) that a consumer must agree to before receiving the data. Policies follow the [ODRL](https://www.w3.org/TR/odrl-model/) standard. Each Offer has one or more **Permissions**, which define what action is allowed (e.g. `use`) and under what **Constraints** (e.g. `count lteq 5` — no more than 5 uses).

Currently supported constraint operands: `count`, `dateTime`.  
Currently supported actions: `use`, `anonymize`.

---

## Managing Your Catalog

All management operations require Basic Auth with an administrator account.

### Viewing the Catalog

```bash
# Get the catalog
curl -X GET http://localhost:8080/api/v1/catalogs \
  -H "Accept: application/json" \
  -u admin:password
```

---

### Creating a DataService

A DataService must exist before you can create a Distribution. Create one first:

```bash
curl -X POST http://localhost:8080/api/v1/dataservices \
  -H "Content-Type: application/json" \
  -u admin:password \
  -d '{
    "title": "TRUE Connector DSP Service",
    "endpointURL": "http://provider-host:8080/",
    "endpointDescription": "connector",
    "creator": "My Organisation"
  }'
```

The response includes the auto-generated `@id` for the new DataService — save it for the next step.

---

### Creating a Distribution

A Distribution links a transfer format to a DataService. Replace `<dataservice-id>` with the ID from the previous step:

```bash
curl -X POST http://localhost:8080/api/v1/distributions \
  -H "Content-Type: application/json" \
  -u admin:password \
  -d '{
    "format": "HttpData-PULL",
    "title": "HTTP Pull access",
    "accessService": { "@id": "urn:uuid:<dataservice-id>" }
  }'
```

Save the returned distribution `@id`.

---

### Creating a Dataset

Dataset creation uses multipart form data because an artifact (the actual data) must be provided at the same time.

**With an external URL as artifact:**

```bash
curl -X POST http://localhost:8080/api/v1/datasets \
  -u admin:password \
  -F 'dataset={
    "title": "Employee Records",
    "keyword": ["HR", "Employee"],
    "hasPolicy": [{
      "@type": "Offer",
      "permission": [{ "action": "use" }]
    }],
    "distribution": [{ "@id": "urn:uuid:<distribution-id>" }]
  };type=application/json' \
  -F 'url=https://my-data-source.example.com/employees.csv'
```

**With a file upload:**

```bash
curl -X POST http://localhost:8080/api/v1/datasets \
  -u admin:password \
  -F 'dataset={
    "title": "Employee Records",
    "hasPolicy": [{"@type": "Offer", "permission": [{"action": "use"}]}],
    "distribution": [{"@id": "urn:uuid:<distribution-id>"}]
  };type=application/json' \
  -F 'file=@/path/to/employees.csv'
```

> **Note:** If both `file` and `url` are provided, the file takes priority.

---

### Managing Offers (Policies)

Offers are embedded inside a Dataset when it is created or updated. To validate that an offer from a negotiation request matches what is in the catalog:

```bash
curl -X POST http://localhost:8080/api/v1/offers/validate \
  -H "Content-Type: application/json" \
  -u admin:password \
  -d '{
    "@type": "Offer",
    "@id": "urn:uuid:<offer-id>",
    "target": "urn:uuid:<dataset-id>",
    "permission": [{ "action": "use" }]
  }'
```

A `200 OK` response means the offer is valid; `400 Bad Request` means it does not match any offer in the catalog.

---

### Deleting Resources

```bash
# Delete a dataset
curl -X DELETE http://localhost:8080/api/v1/datasets/<id> -u admin:password

# Delete a distribution
curl -X DELETE http://localhost:8080/api/v1/distributions/<id> -u admin:password

# Delete a data service
curl -X DELETE http://localhost:8080/api/v1/dataservices/<id> -u admin:password
```

---

## Example Catalog

The following is a complete example catalog as returned by the DSP protocol endpoint. This is also the format used in the initial data (`init_data`).

Field explanations:

| Field | Meaning |
|---|---|
| `@context` | JSON-LD context — identifies this as a DSP 2025-1 message |
| `@id` | Unique identifier for this catalog (URN format) |
| `@type` | Always `Catalog` for the top-level object |
| `title` | Human-readable name for the catalog |
| `description` | Multi-language description array |
| `participantId` | DSP identifier of the data provider organisation |
| `keyword` | Tags for discovery |
| `dataset` | Array of Dataset objects available in this catalog |
| `distribution` | Top-level distributions (access methods) |
| `service` | DataService objects describing connector endpoints |
| `conformsTo` | Profile or standard this catalog conforms to |
| `issued` / `modified` | Timestamps for creation and last update |

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

---

## Artifacts

Each dataset is backed by an artifact — the actual data. An artifact can be:

- A **file** uploaded to the connector and stored in S3-compatible object storage.
- An **external URL** pointing to data hosted on another system (with optional authorization).

> **See also:** [Artifact Upload](./artifact-upload.md) for detailed upload instructions.

---

## See Also

- [Catalog Technical Documentation](./catalog-technical.md)
- [Artifact Upload](./artifact-upload.md)
- [Documentation Index](../../doc/README.md#catalog--artifacts)
