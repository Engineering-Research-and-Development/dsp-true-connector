# Implementation Reference

> Operations reference for deploying and configuring TRUE Connector in production environments.
>
> **See also:** [User Guide](./user-guide.md) | [Developer Guide](./developer-guide.md) | [DSP Implementation Guide](./dsp-implementation-guide.md)

---

## Deployment

### Kubernetes with Terraform

TRUE Connector ships with a complete Terraform configuration for Kubernetes deployment.

> **See also:**
> - [Terraform Deployment Guide](../terraform/terraform-deployment-guide.md) — Complete step-by-step deployment (local Kind cluster + remote cluster)
> - [Terraform Overview](../terraform/terraform.md) — Structure and organization
> - [Quick Reference](../terraform/quick-reference.md) — Common scenario configurations
> - [Usage Examples](../terraform/usage-examples.md) — Real-world examples

### Spring Profiles

Configure which Spring profile to activate based on your environment:

> **See also:** [Spring Profiles](./profiles.md) — Which profile to use for each environment (default, tck, development, etc.)

---

## Security Configuration

### TLS / HTTPS

TRUE Connector uses Spring Boot's SSL bundle mechanism for TLS configuration. TLS settings are stored as application properties in MongoDB and can be updated at runtime.

Key TLS properties:

| Property | Description |
|----------|-------------|
| `server.ssl.enabled` | Enable HTTPS (`true`) or plain HTTP (`false` — dev only) |
| `spring.ssl.bundle.jks.connector.keystore.location` | Path to the connector keystore (e.g. `classpath:ssl-server.jks`) |
| `spring.ssl.bundle.jks.connector.keystore.password` | Keystore password |
| `spring.ssl.bundle.jks.connector.key.alias` | Alias of the connector key in the keystore |

> **See also:** [Security Configuration](./security.md) — Full TLS setup, keystore/truststore configuration, and certificate rotation.

### Certificate Management (PKI)

> **See also:** [PKI Certificate Guide](./certificate/pki-certificate-guide.md) — Complete guide for PKI infrastructure, certificate generation, and renewal.

### OCSP Certificate Validation

OCSP (Online Certificate Status Protocol) validates that peer certificates have not been revoked. TRUE Connector integrates OCSP into the OkHttp TLS layer used for outbound connector-to-connector calls.

Key OCSP properties:

| Property | Default | Description |
|----------|---------|-------------|
| `application.ocsp.validation.enabled` | `false` | Enable OCSP certificate revocation checking |
| `application.ocsp.validation.softFail` | `false` | Allow connections even when OCSP check fails (soft fail) |
| `application.ocsp.validation.defaultCacheDurationMinutes` | `60` | Cache OCSP responses when no `nextUpdate` is present |
| `application.ocsp.validation.timeoutSeconds` | `10` | Timeout for contacting the OCSP responder |

> **See also:**
> - [OCSP Guide](./ocsp/ocsp-guide.md) — OCSP responder setup, certificate validation, and revocation management
> - [Tools Implementation Guide](../tools/doc/tools-implementation.md) — OCSP configuration properties

### Authentication Configuration

TRUE Connector uses two authentication mechanisms:

**1. HTTP Basic Auth** — for human operators calling the management API (`/api/v1/*`):

```
Authorization: Basic <base64(email:password)>
```

Default admin account: `admin@mail.com` / `password` (change immediately on first start).

**2. JWT Bearer Token** — for connector-to-connector DSP protocol calls:

```
Authorization: Bearer <daps-issued-jwt>
```

Tokens are validated against the DAPS JWKS endpoint configured via:

| Property | Description |
|----------|-------------|
| `application.daps.enabledDapsInteraction` | Enable live DAPS token validation |
| `application.daps.dapsJWKSUrl` | JWKS endpoint for key resolution |
| `application.protocol.authentication.enabled` | Master switch for protocol endpoint authentication |

> ⚠️ Set `application.protocol.authentication.enabled=false` only for development/testing. Never in production.

> **See also:** [Connector Technical Docs](../connector/doc/connector-technical.md) — Full security architecture details.

### Verifiable Credentials

> **See also:** [Verifiable Credentials](./verifiable-credentials.md) — VC support, trust triangle, and integration.

### Identity Hub

> **See also:** [Identity Hub Integration](./identity-hub.md) — Identity management, federation, and participant identity configuration.

---

## S3 Storage Configuration

### Overview

S3-compatible object storage (AWS S3 or MinIO) is used for:

- **Artifact storage** — uploaded data files stored in the provider's S3 bucket
- **HTTP-PULL transfers** — provider generates presigned URLs pointing to S3
- **HTTP-PUSH transfers** — provider uploads data to the consumer's S3 bucket

### Setup

Configure S3 in `application.properties` or as environment variables:

```properties
# S3 / MinIO endpoint (leave empty for AWS S3)
s3.endpoint=http://minio:9000

# Provider's own bucket credentials
s3.accessKey=your-access-key
s3.secretKey=your-secret-key
s3.bucketName=connector-bucket
s3.region=us-east-1
```

> **See also:** [S3 Configuration](./s3-configuration.md) — Complete setup guide including MinIO Docker configuration.

### Upload Modes

> **See also:** [S3 Upload Mode Configuration](./solutions/s3-upload-mode-configuration.md) — Strategies for different upload scenarios.

### Async Upload Performance

> **See also:** [Async S3 Upload Improvements](./solutions/async-s3-upload-improvements.md) — Performance optimizations for high-throughput S3 uploads.

---

## Application Properties Reference

### How Properties Work

Application properties are key/value pairs stored in MongoDB. Unlike values in `application.properties` files, these can be updated at runtime via the Properties API and take effect immediately without a connector restart.

**View all properties:**

```bash
curl http://localhost:8080/api/v1/properties/ \
  -u admin@mail.com:password
```

**Update a property:**

```bash
curl -X PUT http://localhost:8080/api/v1/properties/ \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '[{
    "key": "application.automatic.negotiation",
    "value": "true",
    "mandatory": false,
    "type": "ApplicationProperty"
  }]'
```

### Key Operational Properties

| Property | Default | Description |
|----------|---------|-------------|
| `application.automatic.negotiation` | `false` | Auto-accept all incoming negotiation requests |
| `application.automatic.transfer` | `false` | Auto-start data transfer after agreement finalized |
| `application.protocol.authentication.enabled` | `true` | Enable JWT auth for DSP protocol endpoints |
| `application.encryption.key` | — | Key for encrypting sensitive fields (e.g. S3 secret keys) in MongoDB |
| `application.ocsp.validation.enabled` | `false` | Enable OCSP certificate revocation checking |
| `application.ocsp.validation.softFail` | `false` | Continue if OCSP check fails |
| `application.ocsp.validation.defaultCacheDurationMinutes` | `60` | OCSP response cache duration |
| `application.ocsp.validation.timeoutSeconds` | `10` | OCSP responder connection timeout |
| `application.daps.enabledDapsInteraction` | `false` | Enable live DAPS token validation |
| `application.daps.dapsJWKSUrl` | — | DAPS JWKS endpoint URL |
| `application.password.validator.minLength` | `8` | Minimum password length |
| `application.password.validator.maxLength` | `16` | Maximum password length |
| `server.ssl.enabled` | `true` | Enable HTTPS |
| `spring.ssl.bundle.jks.connector.keystore.location` | — | Keystore file path |
| `spring.ssl.bundle.jks.connector.keystore.password` | — | Keystore password |
| `spring.ssl.bundle.jks.connector.key.alias` | — | Key alias in keystore |

> **See also:**
> - [Application Properties API](../tools/doc/application-property.md) — Full API reference with all property keys
> - [Tools Implementation Guide](../tools/doc/tools-implementation.md) — Configuration guide with examples

---

## Data Transfer Configuration

### Choosing a Transfer Method

| Method | Use When |
|--------|----------|
| **HttpData-PULL** | Consumer pulls data from provider's S3 bucket; consumer stores it locally |
| **HttpData-PUSH** | Provider pushes data to consumer's S3 bucket; consumer provides credentials |
| **External REST** | Artifact is not stored in S3; provider serves it via REST endpoint |

For **HTTP-PUSH**, the consumer must have S3 bucket credentials registered in the connector:

```bash
# Register consumer S3 bucket credentials
curl -X POST http://localhost:8080/api/v1/bucket-credentials \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '{
    "bucketName": "consumer-bucket",
    "region": "us-east-1",
    "accessKey": "ACCESS_KEY",
    "secretKey": "SECRET_KEY",
    "endpointOverride": "http://minio:9000"
  }'
```

> **See also:** [Data Transfer User Guide](../data-transfer/doc/data-transfer.md) | [Data Transfer Technical Docs](../data-transfer/doc/data-transfer-technical.md)

---

## Audit & Monitoring

### Audit Events

All connector actions are recorded as audit events in MongoDB. The audit event log provides a complete history of catalog changes, negotiations, agreements, and data transfers.

**View recent audit events:**

```bash
curl http://localhost:8080/api/v1/audit \
  -u admin@mail.com:password
```

> **See also:** [Tools Implementation Guide](../tools/doc/tools-implementation.md) — Audit event types and API reference.

---

## User Management

### Default Users

| Email | Role | Default Password | Purpose |
|-------|------|-----------------|---------|
| `admin@mail.com` | `ROLE_ADMIN` | `password` | Human operator account |
| `connector@mail.com` | `ROLE_CONNECTOR` | `password` | Connector-to-connector identity |

> ⚠️ Change both passwords immediately after first startup.

### Managing Users

```bash
# List users
curl http://localhost:8080/api/v1/users -u admin@mail.com:password

# Create user
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '{"email": "operator@example.com", "password": "SecurePass1!", "role": "ROLE_ADMIN"}'

# Change password
curl -X PUT http://localhost:8080/api/v1/users/{id}/password \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '{"password": "NewSecurePass1!"}'

# Delete user
curl -X DELETE http://localhost:8080/api/v1/users/{id} -u admin@mail.com:password
```

> **See also:** [Connector Platform Features](../connector/doc/connector-platform-features.md) — Full user management guide.

---

## TCK Compliance Testing

TRUE Connector passes the Dataspace Protocol Technology Compatibility Kit (TCK), which verifies protocol compliance against the DSP 2025-1 specification.

> **See also:** [TCK Compliance](./tck/tck-compliancy.md) — How to run TCK tests and interpret results.

---

## See Also

| Resource | Link |
|----------|------|
| Security Configuration | [security.md](./security.md) |
| PKI Certificate Guide | [certificate/pki-certificate-guide.md](./certificate/pki-certificate-guide.md) |
| OCSP Guide | [ocsp/ocsp-guide.md](./ocsp/ocsp-guide.md) |
| S3 Configuration | [s3-configuration.md](./s3-configuration.md) |
| S3 Upload Modes | [solutions/s3-upload-mode-configuration.md](./solutions/s3-upload-mode-configuration.md) |
| Async S3 Improvements | [solutions/async-s3-upload-improvements.md](./solutions/async-s3-upload-improvements.md) |
| Spring Profiles | [profiles.md](./profiles.md) |
| Verifiable Credentials | [verifiable-credentials.md](./verifiable-credentials.md) |
| Identity Hub | [identity-hub.md](./identity-hub.md) |
| Application Properties API | [../tools/doc/application-property.md](../tools/doc/application-property.md) |
| Tools Implementation Guide | [../tools/doc/tools-implementation.md](../tools/doc/tools-implementation.md) |
| Connector Platform Features | [../connector/doc/connector-platform-features.md](../connector/doc/connector-platform-features.md) |
| Connector Technical Docs | [../connector/doc/connector-technical.md](../connector/doc/connector-technical.md) |
| TCK Compliance | [tck/tck-compliancy.md](./tck/tck-compliancy.md) |
| Terraform Deployment Guide | [../terraform/terraform-deployment-guide.md](../terraform/terraform-deployment-guide.md) |
