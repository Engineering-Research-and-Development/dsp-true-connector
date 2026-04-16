---
name: dsp-catalog
description: Catalog Protocol and Catalog HTTPS Binding guidance for Dataspace Protocol 2025-1. Use when implementing or reviewing catalog requests, dataset lookup, filters, pagination, proof metadata, or catalog-facing DSP endpoints.
---

# DSP Catalog

# Purpose

Use this skill for DSP 2025-1 catalog publication and retrieval behavior.

Load `dsp-foundations` together with this skill when possible.

# Use this skill when

- A task touches `/catalog` protocol endpoints.
- A task changes catalog request or dataset request payloads.
- A task involves filters, pagination, compression, proof metadata, or catalog brokers.
- A task reviews DSP catalog compliance.

# Instructions for Copilot

1. Treat Sections 5 and 6 of DSP 2025-1 as the main reference, plus the common requirements from Section 4.
2. Keep request and response payloads aligned with the DSP JSON-LD context and JSON Schema.
3. Do not invent a standard filter language; the spec leaves filters implementation-specific.
4. Separate normative catalog wire behavior from local business rules like search strategy, security overlays, or broker caching policy.

# Key protocol facts

- `CatalogRequestMessage` requires:
  - `@context` containing `https://w3id.org/dspace/2025/1/context.jsonld`
  - `@type` equal to `CatalogRequestMessage`
  - optional `filter`
- `DatasetRequestMessage` requires:
  - `@context` containing `https://w3id.org/dspace/2025/1/context.jsonld`
  - `@type` equal to `DatasetRequestMessage`
  - required dataset identifier
- The Catalog Service MUST respond with schema-compliant `Catalog` or `Dataset` payloads on success.
- A Catalog MUST contain zero to many Datasets and one to many Data Services.
- Request errors should return an appropriate HTTP status and a `Catalog Error` body.

# HTTPS binding facts

- Catalog request endpoint:
  - `POST <base>/catalog/request`
- Dataset request endpoint:
  - `GET <base>/catalog/datasets/{id}`
- Successful catalog and dataset requests return HTTP 200.
- Unsupported filter expressions MUST return HTTP 400.
- Pagination, when supported, MUST use the HTTP `Link` header with `next` and `previous` relations.
- Compression MAY be used.

# Important extension points

- Filter expression syntax and execution model.
- Proof metadata endpoint behavior and supported proof types.
- Catalog broker replication and caching strategy.
- Authorization overlay and token validation.

# Repository hints for TRUE Connector

- Primary module: `catalog`
- Likely touchpoints:
  - protocol controllers under `rest/protocol`
  - serializer code via `CatalogSerializer`
  - model and repository classes in the `catalog` module
- Preserve the existing serializer boundary between protocol JSON(-LD) and plain JSON.

# Output expectations

When using this skill:

1. Quote the normative behavior being changed.
2. Identify whether the change affects message shape, endpoint binding, or implementation-specific extensions.
3. Point to likely catalog module files to inspect.
4. Recommend tests for happy path, unsupported filter handling, auth-required access, and pagination if relevant.
