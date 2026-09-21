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

## CatalogRequestMessage

- Requires `@context` containing `https://w3id.org/dspace/2025/1/context.jsonld`.
- Requires `@type` equal to `CatalogRequestMessage`.
- Optional `filter` property containing an implementation-specific query expression.
  - If a filter expression is not supported, the Catalog Service MUST return HTTP 400.

## DatasetRequestMessage

- Requires `@context` containing `https://w3id.org/dspace/2025/1/context.jsonld`.
- Requires `@type` equal to `DatasetRequestMessage`.
- MUST include a `dataset` property containing the identifier of the Dataset.

## Catalog response (ACK)

- A Catalog MUST contain zero to many Datasets.
- A Catalog MUST contain one to many Data Services referencing Connectors where Datasets MAY be obtained.

## Dataset response (ACK)

- A Dataset MUST have at least one `hasPolicy` attribute containing an `Offer` that defines the Policy for the Dataset.
- A Dataset MUST hold at least one `Distribution` object in the `distribution` attribute.
- Each `Distribution` MUST have at least one `DataService` specifying where the distribution is obtained.
  - `DataService.endpointURL` contains the URL for initiating Contract Negotiations and Transfer Processes.
  - The DSP version signalled by `DataService.endpointURL` MUST be consistent with the Catalog version it was served through.

## Offer rules (inside Catalog / Dataset responses)

- An Offer MUST have an `@id` that is a unique identifier.
- An Offer MUST be unique to a Dataset; the target of an Offer is derived from its enclosing Dataset context.
- When an Offer is inside a Catalog or Dataset response, it MUST NOT have a `target` attribute.
- Rules inside the Offer (permissions, prohibitions, obligations) MUST NOT have any `target` attributes. This prevents
  inconsistencies with the [ODRL inferencing rules for compact policies](https://www.w3.org/TR/odrl-model/#composition-compact).

## CatalogError

- Used when an error occurs after a CatalogRequestMessage or DatasetRequestMessage.
- The Catalog Service MUST return an appropriate HTTP status code and a `CatalogError` body.
- Schema: `catalog-error-schema.json`.

# HTTPS binding facts

- Catalog request endpoint: `POST <base>/catalog/request`
- Dataset request endpoint: `GET <base>/catalog/datasets/{id}`
- All request and response messages MUST use the `application/json` media type.
- Successful catalog and dataset requests return HTTP 200.
- Unsupported filter expressions MUST return HTTP 400 with a `CatalogError` body.
- Pagination, when supported, MUST use the HTTP `Link` header with `next` and `previous` relations.
- Compression MAY be used (`Content-Encoding: gzip`).

# Important extension points

- Filter expression syntax and execution model.
- Proof metadata endpoint: catalog protocol bindings SHOULD define a proof data endpoint so Consumers can
  discover required proof types before making a restricted catalog request. Proof type semantics are out of scope.
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
