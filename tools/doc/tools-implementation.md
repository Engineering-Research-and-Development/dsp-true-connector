# Tools & Utilities — Implementation Guide

> **See also:** [Technical Documentation](./tools-technical.md) | [Application Properties API](./application-property.md) | [Generic Filtering](./generic-filtering.md)

## What is the Tools Module?

The tools module provides shared infrastructure for every feature of the TRUE Connector. As an operator or integrator, you interact with it through:

- **Configuration** — application properties, TLS/SSL bundles, S3 settings, OCSP settings.
- **The Properties API** — view and update connector settings at runtime without restart.
- **The Audit Events API** — browse a complete log of connector activity.

---

## Application Properties Management

### What are Application Properties?

Application properties are key/value pairs stored in MongoDB that control the behaviour of the connector. Unlike values in `application.properties`, these can be updated at runtime through the API and take effect immediately — no restart needed. They are loaded from MongoDB on startup and re-pushed into the live Spring `Environment` on each update.

### Viewing Properties

List all properties:

```bash
curl --location 'http://localhost:8080/api/v1/properties/' \
  --header 'Content-Type: application/json' \
  --header 'Authorization: Basic YWRtaW5AbWFpbC5jb206cGFzc3dvcmQ='
```

Filter by prefix (e.g. all `application.` properties):

```bash
curl --location 'http://localhost:8080/api/v1/properties/?key_prefix=application.' \
  --header 'Content-Type: application/json' \
  --header 'Authorization: Basic YWRtaW5AbWFpbC5jb206cGFzc3dvcmQ='
```

> For the full API reference and more examples, see [application-property.md](./application-property.md).

### Updating a Property

Send a `PUT` with a JSON array containing one or more updated properties:

```bash
curl --location --request PUT 'http://localhost:8080/api/v1/properties/' \
  --header 'Content-Type: application/json' \
  --header 'Authorization: Basic YWRtaW5AbWFpbC5jb206cGFzc3dvcmQ=' \
  --data '[
    {
      "key": "application.automatic.negotiation",
      "value": "true",
      "mandatory": false,
      "type": "ApplicationProperty"
    }
  ]'
```

The response is the full list of all properties after the update. The change is applied to the live environment immediately.

### Key Properties Reference

| Key | Values | Description |
|---|---|---|
| `application.automatic.negotiation` | `true` / `false` | Automatically accept incoming contract negotiation offers |
| `application.automatic.transfer` | `true` / `false` | Automatically start data transfer after negotiation completes |
| `application.protocol.authentication.enabled` | `true` / `false` | Enable authentication for DSP protocol endpoints |
| `application.encryption.key` | string | Key used for encrypting sensitive fields (e.g. S3 secret keys) in MongoDB |
| `application.ocsp.validation.enabled` | `true` / `false` | Enable OCSP certificate revocation checking |
| `application.ocsp.validation.softFail` | `true` / `false` | Allow connections even when OCSP check fails |
| `application.ocsp.validation.defaultCacheDurationMinutes` | integer | How long (minutes) to cache OCSP responses without a `nextUpdate` |
| `application.ocsp.validation.timeoutSeconds` | integer | Timeout (seconds) for contacting the OCSP responder |
| `spring.ssl.bundle.jks.connector.keystore.location` | path | Path to the connector keystore (e.g. `classpath:ssl-server.jks`) |
| `spring.ssl.bundle.jks.connector.keystore.password` | string | Password for the connector keystore |
| `spring.ssl.bundle.jks.connector.key.alias` | string | Alias of the connector key in the keystore |
| `server.ssl.enabled` | `true` / `false` | Enable HTTPS; when `false`, all TLS validation is disabled (development only) |

---

## S3 Storage Configuration

### When is S3 Used?

S3 (or any S3-compatible storage such as MinIO) is used for data transfers that move large binary objects between connectors. When a provider stores data in S3, the consumer retrieves it via an S3-compatible endpoint. For HTTP-PUSH transfers, the consumer must supply its own S3 bucket credentials so the provider can write the data directly.

### S3 Configuration Properties

Add these to your `application.properties` (or environment variables):

```properties
# S3 / MinIO endpoint (leave blank for AWS S3)
s3.endpoint=http://minio:9000

# Credentials for the connector's own S3 bucket
s3.accessKey=your-access-key
s3.secretKey=your-secret-key

# Default bucket and region
s3.bucketName=connector-bucket
s3.region=us-east-1

# External URL used to generate pre-signed download links
s3.externalPresignedEndpoint=https://minio.example.com

# Upload strategy: SYNC (default, reverse-proxy-safe) or ASYNC (faster)
s3.uploadMode=SYNC
```

> **AWS S3:** Leave `s3.endpoint` empty (or do not set it). The connector automatically detects AWS endpoints and skips MinIO-specific administration.

> **MinIO behind a reverse proxy (Caddy, NGINX):** Use `s3.uploadMode=SYNC` to avoid multipart upload compatibility issues.

### Providing S3 Credentials for HTTP-PUSH

When acting as a **consumer** and using the HTTP-PUSH transfer method, you must supply the target S3 bucket credentials in the `DataAddress`. The following keys are used (see `S3Utils`):

| Key | Description |
|---|---|
| `bucketName` | Name of the consumer's S3 bucket |
| `region` | Bucket region |
| `objectKey` | Target path/key for the uploaded object |
| `accessKey` | Consumer S3 access key |
| `secretKey` | Consumer S3 secret key |
| `endpointOverride` | Custom S3 endpoint (omit for AWS S3) |

The connector stores per-bucket credentials in MongoDB (collection `bucket_credentials`). The `secretKey` is encrypted at rest using `FieldEncryptionService` (AES-256-CBC).

---

## TLS / HTTPS Security

### TLS Configuration

The connector uses a named Spring SSL bundle called `"connector"`. Configure it in `application.properties`:

```properties
server.ssl.enabled=true
spring.ssl.bundle.jks.connector.keystore.location=classpath:ssl-server.jks
spring.ssl.bundle.jks.connector.keystore.password=changeit
spring.ssl.bundle.jks.connector.keystore.type=JKS
spring.ssl.bundle.jks.connector.key.alias=connector
spring.ssl.bundle.jks.connector.key.password=changeit
spring.ssl.bundle.jks.connector.truststore.location=classpath:truststore.jks
spring.ssl.bundle.jks.connector.truststore.password=changeit
```

This bundle is used for:
- Inbound HTTPS (served by the embedded Tomcat).
- Outbound connections (`OkHttpClient`) — the truststore defines which certificates are trusted.
- OCSP validation — the trust managers are wrapped with OCSP checking.

### Development / Testing (No TLS)

Set `server.ssl.enabled=false` to disable TLS. In this mode the connector accepts **all** certificates and hostnames without validation. **Never use this in production.**

---

## OCSP Certificate Validation

### What is OCSP?

OCSP (Online Certificate Status Protocol) is a way to check whether a TLS certificate has been revoked by the Certificate Authority that issued it, in real time. Without OCSP (or CRL) checking, a revoked certificate can still be used to establish a TLS connection.

### Enabling OCSP

OCSP is configured via application properties (prefix `application.ocsp.validation`):

```properties
application.ocsp.validation.enabled=true
application.ocsp.validation.softFail=true
application.ocsp.validation.defaultCacheDurationMinutes=60
application.ocsp.validation.timeoutSeconds=10
```

OCSP is only active when `server.ssl.enabled=true`.

### OCSP Configuration Reference

| Property | Default | Description |
|---|---|---|
| `enabled` | `false` | Set to `true` to activate OCSP checks |
| `softFail` | `true` | When `true`, a failed/unavailable OCSP check still allows the connection. Set to `false` for strict enforcement |
| `defaultCacheDurationMinutes` | `60` | Cache TTL when the OCSP response does not include a `nextUpdate` time |
| `timeoutSeconds` | `10` | How long (seconds) to wait for the OCSP responder before failing |

OCSP responses are cached (up to 1 000 entries, 24-hour hard cap). The cache respects the `nextUpdate` field from the OCSP response, refreshing early if the response has expired.

### OCSP Responder URL

No manual configuration is needed. The OCSP responder URL is read from the `AuthorityInfoAccess` extension embedded in each X.509 certificate. If a certificate does not include this extension, the check returns `UNKNOWN` status.

### When OCSP Validation Fails

| Situation | `softFail=true` | `softFail=false` |
|---|---|---|
| OCSP responder unreachable | Connection allowed, warning logged | TLS handshake rejected |
| Certificate status `REVOKED` | Connection allowed, warning logged | TLS handshake rejected |
| Certificate status `UNKNOWN` | Connection allowed | Connection allowed |
| Certificate has no OCSP URL | `UNKNOWN` — connection allowed | `UNKNOWN` — connection allowed |

---

## Audit Events

### What is Auditing?

Every significant action in the connector — logins, negotiation state changes, data transfers, property updates — is recorded as an audit event in MongoDB. Audit events are append-only and provide a complete operational history.

### Viewing Audit Events

List the 20 most recent events (default page size):

```bash
curl --location 'http://localhost:8080/api/v1/audit' \
  --header 'Content-Type: application/json' \
  --header 'Authorization: Basic YWRtaW5AbWFpbC5jb206cGFzc3dvcmQ='
```

Retrieve a specific event by ID:

```bash
curl --location 'http://localhost:8080/api/v1/audit/{auditEventId}' \
  --header 'Content-Type: application/json' \
  --header 'Authorization: Basic YWRtaW5AbWFpbC5jb206cGFzc3dvcmQ='
```

List all available event type names:

```bash
curl --location 'http://localhost:8080/api/v1/audit/types' \
  --header 'Content-Type: application/json' \
  --header 'Authorization: Basic YWRtaW5AbWFpbC5jb206cGFzc3dvcmQ='
```

### Filtering Audit Events

All query parameters are passed directly to the dynamic filter. See [generic-filtering.md](./generic-filtering.md) for the full syntax. Common examples:

Filter by a single event type:
```
GET /api/v1/audit?eventType=PROTOCOL_NEGOTIATION_AGREED
```

Filter by multiple event types:
```
GET /api/v1/audit?eventType=PROTOCOL_NEGOTIATION_AGREED&eventType=PROTOCOL_NEGOTIATION_FINALIZED
```

Filter by timestamp range:
```
GET /api/v1/audit?timestamp.from=2025-01-01T00:00:00Z&timestamp.to=2025-12-31T23:59:59Z
```

Filter by nested field (e.g. negotiation state inside `details`):
```
GET /api/v1/audit?details.contractNegotiation.state=FINALIZED
```

### Pagination and Sorting

| Parameter | Default | Description |
|---|---|---|
| `page` | `0` | Zero-based page number |
| `size` | `20` | Events per page |
| `sort` | `timestamp,desc` | Sort field and direction (`asc` or `desc`) |

### Audit Event Types

**Application lifecycle:**

| Event type | When it fires |
|---|---|
| `APPLICATION_START` | Connector starts up |
| `APPLICATION_STOP` | Connector shuts down |
| `APPLICATION_LOGIN` | User logs in |
| `APPLICATION_LOGOUT` | User logs out |

**Catalog protocol:**

| Event type | When it fires |
|---|---|
| `PROTOCOL_CATALOG_CATALOG_NOT_FOUND` | Requested catalog does not exist |
| `PROTOCOL_CATALOG_DATASET_NOT_FOUND` | Requested dataset does not exist |

**Contract negotiation protocol:**

| Event type | When it fires |
|---|---|
| `PROTOCOL_NEGOTIATION_REQUESTED` | Negotiation request received |
| `PROTOCOL_NEGOTIATION_OFFERED` | Offer sent or received |
| `PROTOCOL_NEGOTIATION_ACCEPTED` | Offer accepted |
| `PROTOCOL_NEGOTIATION_AGREED` | Agreement reached |
| `PROTOCOL_NEGOTIATION_VERIFIED` | Agreement verified |
| `PROTOCOL_NEGOTIATION_FINALIZED` | Negotiation finalised |
| `PROTOCOL_NEGOTIATION_TERMINATED` | Negotiation terminated |
| `PROTOCOL_NEGOTIATION_REJECTED` | Offer rejected |
| `PROTOCOL_NEGOTIATION_POLICY_EVALUATION_APPROVE` | Policy check passed |
| `PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DENIED` | Policy check failed |
| `PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DISABLED` | Policy evaluation is off |
| `PROTOCOL_NEGOTIATION_INVALID_OFFER` | Received offer is not valid |
| `PROTOCOL_NEGOTIATION_NOT_FOUND` | Negotiation not found |
| `PROTOCOL_NEGOTIATION_STATE_TRANSITION_ERROR` | Invalid state transition |

**Data transfer protocol:**

| Event type | When it fires |
|---|---|
| `PROTOCOL_TRANSFER_REQUESTED` | Transfer request received |
| `PROTOCOL_TRANSFER_STARTED` | Transfer started |
| `PROTOCOL_TRANSFER_COMPLETED` | Transfer completed |
| `PROTOCOL_TRANSFER_SUSPENDED` | Transfer suspended |
| `PROTOCOL_TRANSFER_TERMINATED` | Transfer terminated |
| `PROTOCOL_TRANSFER_NOT_FOUND` | Transfer process not found |
| `PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR` | Invalid state transition |
| `TRANSFER_COMPLETED` | Transfer fully completed |
| `TRANSFER_FAILED` | Transfer failed |
| `TRANSFER_VIEW` | Transfer data viewed |

**Usage control:**

| Event type | When it fires |
|---|---|
| `NEGOTIATION_ACCESS_COUNT_INCREASE` | Access count for an agreement incremented |

---

## Artifact Management

### What is an Artifact?

An artifact is the actual data that a provider makes available for transfer. A connector dataset references one artifact, which describes where the data lives and how to access it.

### Artifact Types

| Type | Description |
|---|---|
| `FILE` | A binary file stored in the connector's database. The `value` field holds the internal file identifier. |
| `EXTERNAL` | A URL pointing to an externally hosted resource. The `value` field holds the URL. Optionally, `authorization` holds an `Authorization` header value to use when fetching the resource. |

---

## Generic Filtering

### How Filtering Works

Any collection-returning API endpoint (currently audit events) supports dynamic filtering via query parameters. Parameters are automatically type-converted (boolean, date/time, number, or string) and combined as MongoDB `$and` queries.

For the full filter syntax and examples, see [generic-filtering.md](./generic-filtering.md).

---

## Common Configuration Scenarios

### Enabling Automatic Negotiation and Transfer

To allow the connector to automatically accept offers and start transfers without manual approval:

```bash
curl --location --request PUT 'http://localhost:8080/api/v1/properties/' \
  --header 'Content-Type: application/json' \
  --header 'Authorization: Basic YWRtaW5AbWFpbC5jb206cGFzc3dvcmQ=' \
  --data '[
    {
      "key": "application.automatic.negotiation",
      "value": "true",
      "mandatory": false,
      "type": "ApplicationProperty"
    },
    {
      "key": "application.automatic.transfer",
      "value": "true",
      "mandatory": false,
      "type": "ApplicationProperty"
    }
  ]'
```

Changes take effect immediately. No restart required.

### Configuring for S3 Data Transfer

1. Set the S3 properties in `application.properties` (or environment variables):

   ```properties
   s3.endpoint=http://minio:9000
   s3.accessKey=minioadmin
   s3.secretKey=minioadmin
   s3.bucketName=my-connector-bucket
   s3.region=us-east-1
   s3.externalPresignedEndpoint=https://minio.example.com
   s3.uploadMode=SYNC
   ```

2. Ensure the `application.encryption.key` property is set (used to encrypt stored bucket credentials):

   ```properties
   application.encryption.key=your-strong-encryption-key
   ```

3. When initiating an HTTP-PUSH transfer as a consumer, include the target S3 details in the `DataAddress`. The connector will store the credentials encrypted in MongoDB and use them during the transfer.

### Setting Up OCSP Validation

1. Ensure TLS is enabled:
   ```properties
   server.ssl.enabled=true
   ```

2. Configure the SSL bundle with a truststore that contains the CA certificates of peers you expect to connect to.

3. Enable OCSP via the properties API:
   ```bash
   curl --request PUT 'http://localhost:8080/api/v1/properties/' \
     --header 'Content-Type: application/json' \
     --header 'Authorization: Basic YWRtaW5AbWFpbC5jb206cGFzc3dvcmQ=' \
     --data '[
       {"key": "application.ocsp.validation.enabled", "value": "true", "mandatory": false, "type": "ApplicationProperty"},
       {"key": "application.ocsp.validation.softFail", "value": "true", "mandatory": false, "type": "ApplicationProperty"},
       {"key": "application.ocsp.validation.defaultCacheDurationMinutes", "value": "60", "mandatory": false, "type": "ApplicationProperty"},
       {"key": "application.ocsp.validation.timeoutSeconds", "value": "10", "mandatory": false, "type": "ApplicationProperty"}
     ]'
   ```

4. Start with `softFail=true` to log OCSP failures without breaking connections. Switch to `softFail=false` once you have confirmed your PKI infrastructure is correctly configured.

---

## See Also

- [Technical Documentation](./tools-technical.md) — architecture, class details, and data models
- [Application Properties API](./application-property.md) — full curl examples for properties CRUD
- [Generic Filtering](./generic-filtering.md) — filter syntax and examples for audit events and other APIs
