# Data Transfer

## Overview

This module owns DSP transfer-process lifecycle handling in TRUE Connector.

Normative DSP behavior:

- Consumer sends `TransferRequestMessage`
- Provider creates a `TransferProcess` in `REQUESTED`
- Provider performs the initial `REQUESTED -> STARTED` transition
- Either party may later complete, suspend, restart from `SUSPENDED`, or terminate

Implementation-specific behavior in this repository:

- the actual byte movement is delegated to dataplanes through DPS
- built-in HTTP-PULL and HTTP-PUSH do not use DPS `prepare`
- streaming transports (`stream:grpc`, `stream:kafka`) do use DPS `prepare`
- transfer availability depends on formats exposed in the catalog, which in turn follow registered
  dataplanes

## Protocol base paths

TRUE Connector commonly runs with tenant-aware DSP paths. In that deployment model, the default
tenant `engineering` yields endpoints such as:

- `POST /engineering/transfers/request`
- `POST /engineering/negotiations/request`
- `POST /engineering/catalog/request`

The unversioned root endpoint `/.well-known/dspace-version` remains tenant-unaware.

## Transfer request shape

Example request:

```json
{
  "@context": "https://w3id.org/dspace/2025/1/context.jsonld",
  "@type": "TransferRequestMessage",
  "consumerPid": "urn:uuid:CONSUMER_PID_TRANSFER",
  "agreementId": "urn:uuid:AGREEMENT_ID",
  "format": "HttpData-PULL",
  "callbackAddress": "http://consumer.example/engineering/consumer"
}
```

Repository-specific notes:

- `format` must match a distribution currently advertised by the provider dataset
- push transfers carry transport-specific sink details in `dataAddress`
- pull transfers receive their final `dataAddress` later in `TransferStartMessage`

## Current transfer types

| Format | DSP transfer style | DPS behavior in TRUE Connector |
|---|---|---|
| `HttpData-PULL` | Pull | Provider CP generates presigned URL directly; consumer pull DP executes the download |
| `HttpData-PUSH` | Push | Consumer CP creates temporary sink credentials; provider push DP copies provider S3 -> consumer S3 |
| `stream:grpc` | Pull-like streaming | Provider gRPC DP prepares a session; consumer gRPC DP streams chunks into consumer storage |
| `stream:kafka` | Pull-like brokered streaming | Provider Kafka DP prepares topic metadata; consumer Kafka DP drains broker records into consumer storage |

## HTTP-PULL

Current repository flow:

1. Consumer sends `TransferRequestMessage` with `format=HttpData-PULL`
2. Provider admin calls start transfer
3. Provider CP generates a presigned GET URL directly through `S3ClientService`
4. Provider sends `TransferStartMessage` containing that URL in `dataAddress.endpoint`
5. Consumer admin calls download
6. Consumer-side pull DP receives DPS `POST /dataflows/start`
7. Pull DP downloads from the presigned URL and stores the artifact in the consumer bucket under
   `objectKey = transferProcessId`
8. DP notifies completion back to the CP

Important implementation detail:

- the built-in flow does **not** require a provider-side pull dataplane

## HTTP-PUSH

Current repository flow:

1. Consumer admin requests transfer with `format=HttpData-PUSH`
2. Consumer CP resolves the tenant bucket and creates temporary write-only S3 credentials
3. Consumer sends those sink details in `TransferRequestMessage.dataAddress`
4. Provider admin starts the transfer
5. Provider admin triggers data movement
6. Provider-side push DP receives DPS `POST /dataflows/start`
7. The DP reads the source artifact from provider S3 using CP-supplied `source.*` credentials and
   uploads it to consumer S3 using `sink.*` credentials
8. Temporary credentials are cleaned up after completion or termination

Important implementation detail:

- the built-in flow does **not** use a consumer-side push dataplane

## Streaming transports

### gRPC

- provider side uses DPS `prepare` to allocate a session and transport metadata
- consumer side uses DPS `start` to connect and stream chunks
- source and sink access stay behind `SourceReader` / `SinkWriter`
- provider-side prepared sessions are sticky-routed to the same dataplane instance

### Kafka

- provider side uses DPS `prepare` to allocate topic and broker metadata
- consumer side uses DPS `start` to subscribe and drain records
- current built-in flow is finite S3-backed streaming
- suspend/resume are not implemented for `stream:kafka`

## `prepare` vs `start`

Current TRUE Connector behavior:

| Transfer type | `prepare` used? | Why |
|---|---|---|
| `HttpData-PULL` | No | Presigned URL can be produced directly by the provider CP |
| `HttpData-PUSH` | No | Sink credentials are created directly by the consumer CP |
| `stream:grpc` | Yes | Session metadata must exist before the consumer can connect |
| `stream:kafka` | Yes | Topic and broker metadata must exist before the consumer can subscribe |

This is an implementation decision, not a DSP requirement.

## Viewing transferred data

After a transfer reaches `COMPLETED`, the consumer can call:

`GET /api/v1/transfers/{transferProcessId}/view`

The CP generates a fresh presigned GET URL directly via `S3ClientService`. No dataplane call is
required for `viewData`.

## Interaction with the catalog

The transfer module depends on catalog capabilities being aligned with dataplane registrations:

- users discover supported formats from dataset distributions and `/api/v1/datasets/{id}/formats`
- those formats are re-derived from active dataplane registrations
- if no dataplane advertises a format, the transfer router cannot select a matching dataplane

In other words, catalog format synchronization and dataplane routing must stay consistent for
transfer initiation to succeed.

## Source of truth in code

Key implementation touchpoints:

- `data-transfer/.../DataTransferAPIService`
- `data-transfer/.../AbstractDataTransferService`
- `data-transfer/.../AutomaticDataTransferService`
- `data-transfer/.../DataPlaneClient`
- `data-transfer/.../DataPlaneRouter`
- `data-transfer/.../DataPlaneRegistrationService`
- `data-transfer/.../DataFlowCallbackController`
