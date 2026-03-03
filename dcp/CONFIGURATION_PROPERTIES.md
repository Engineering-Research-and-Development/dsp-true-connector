# DCP Module — Configuration Properties Reference

This document lists all configuration properties for the **`dcp-holder`** and **`dcp-verifier`** Spring Boot autoconfiguration libraries.
All properties share the `dcp.*` prefix (bound to `DcpProperties`), with a few standard Spring properties also required.

---

## Table of Contents

1. [Quick Start — Minimal Configuration](#1-quick-start--minimal-configuration)
2. [Core Identity Properties](#2-core-identity-properties)
3. [Keystore Properties](#3-keystore-properties)
4. [DID Document Service Entries](#4-did-document-service-entries)
5. [Trusted Issuers](#5-trusted-issuers)
6. [Issuer Location (Holder only)](#6-issuer-location-holder-only)
7. [Verifiable Presentation (VP) Properties](#7-verifiable-presentation-vp-properties)
8. [Revocation Cache (Holder only)](#8-revocation-cache-holder-only)
9. [Token Validation](#9-token-validation)
10. [Module Enable/Disable Switches](#10-module-enabledisable-switches)
11. [Required Spring Properties](#11-required-spring-properties)
12. [SSL / TLS (optional)](#12-ssl--tls-optional)
13. [Complete Example — Holder + Verifier](#13-complete-example--holder--verifier)
14. [Host Application Note — `dcp.did.cache.ttl`](#14-host-application-note--dcpdidcachettl)

---

## 1. Quick Start — Minimal Configuration

The absolute minimum required to start the holder and verifier libraries:

```properties
# --- Identity ---
dcp.connector-did=did:web:localhost%3A8080:connector

# --- Keystore (EC key used for signing) ---
dcp.keystore.path=eckey.p12
dcp.keystore.password=password
dcp.keystore.alias=dsptrueconnector

# --- DID Document: at least one service entry is required ---
dcp.service-entries[0].id=TRUEConnector-Credential-Service
dcp.service-entries[0].type=CredentialService
dcp.service-entries[0].endpoint-path=/dcp

# --- Trusted issuers (required for credential validation) ---
dcp.trusted-issuers.MembershipCredential=did:web:localhost%3A8084

# --- Spring / MongoDB (required by dcp-holder) ---
# Note: server.port belongs to the host application, not this library.
# dcp-common reads it to build DID document service endpoint URLs when
# dcp.base-url is not set. Set it in the host application (e.g. server.port=8080),
# or use dcp.base-url to avoid the dependency on server.port altogether.
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=true_connector
spring.data.mongodb.authentication-database=admin
```

---

## 2. Core Identity Properties

These properties define **who** the connector is in the DCP ecosystem.

| Property | Required | Default | Description |
|---|---|---|---|
| `dcp.connector-did` | **Yes** | — | The connector's primary DID (e.g. `did:web:example.com:connector`). Used as `iss`/`sub` in self-issued tokens, and as the DID document identifier. Must be a valid `did:web` DID. |
| `dcp.connector-did-verifier` | No | falls back to `dcp.connector-did` | Override the DID used specifically for the verifier role. Use only when the verifier needs a separate DID from the holder. |
| `dcp.host` | No | `localhost` | Hostname used to build service endpoint URLs in the DID document when `dcp.base-url` is not set. |
| `dcp.base-url` | No | built from `protocol + host + port` | Explicit base URL for service endpoints (e.g. `https://connector.example.com`). Overrides `dcp.host` and `server.port` for URL construction. |
| `dcp.auto-register-path-endpoints` | No | `true` | When `true`, the DID document controller dynamically registers path-based endpoints derived from the DID path segments (e.g. `/connector/.well-known/did.json`). Set to `false` to expose only `/.well-known/did.json`. |
| `dcp.enable-legacy-endpoints` | No | `true` | When `true`, also registers legacy convenience endpoints such as `/{role}/did.json` (e.g. `/connector/did.json`). Set to `false` for strict W3C-only endpoint registration. |

### DID Format Examples

| Scenario | `dcp.connector-did` | Registered endpoints |
|---|---|---|
| Root domain, no path | `did:web:example.com` | `/.well-known/did.json` |
| With port | `did:web:localhost%3A8080` | `/.well-known/did.json` |
| With path segment | `did:web:localhost%3A8080:consumer` | `/consumer/.well-known/did.json`, `/consumer/did.json` (legacy) |
| Deep path | `did:web:example.com:api:v1:consumer` | `/api/v1/consumer/.well-known/did.json`, `/consumer/did.json` (legacy) |

> **Note:** Colons in the host:port must be percent-encoded as `%3A` inside a `did:web` DID.

---

## 3. Keystore Properties

The keystore holds the EC key pair used for signing self-issued ID tokens and for the DID document's verification method.

| Property | Required | Default | Description |
|---|---|---|---|
| `dcp.keystore.path` | No | `eckey.p12` | Path to the PKCS#12 keystore file. Can be a classpath resource (`classpath:eckey.p12`) or an absolute filesystem path. |
| `dcp.keystore.password` | No | `password` | Password to open the keystore. |
| `dcp.keystore.alias` | No | `dsptrueconnector` | Alias of the EC key entry inside the keystore. The active alias can also be managed at runtime via the key rotation API; this value is the fallback when no active key metadata is stored in MongoDB. |

> **Security:** Change `dcp.keystore.password` from the default value in any non-development environment.

---

## 4. DID Document Service Entries

The DID document **must** have at least one service entry. Each service entry describes a capability of the connector that other participants can discover.

> **Important:** If no service entries are configured the application will fail to start with an `IllegalStateException`.

### Property Pattern

```properties
dcp.service-entries[<index>].id=<string>
dcp.service-entries[<index>].type=<fixed value — see table below>
dcp.service-entries[<index>].endpoint-path=<fixed value — see table below>
dcp.service-entries[<index>].issuer-location=<url>     # absolute URL; takes precedence over endpoint-path
```

| Sub-property | Required | Description |
|---|---|---|
| `id` | **Yes** | Unique human-readable identifier of the service within the DID document (e.g. `TRUEConnector-Credential-Service`). Free text. |
| `type` | **Yes** | **Fixed value** — must be one of the allowed service type strings (see below). Remote participants use this value to look up the correct endpoint in the DID document. |
| `endpoint-path` | Yes (or `issuer-location`) | **Fixed value** — must match the actual REST controller path in the application (see below). Appended to the connector's base URL to form the full service endpoint URL. |
| `issuer-location` | Yes (or `endpoint-path`) | Absolute URL for the service endpoint. When set, `endpoint-path` is ignored. Used only when pointing to a **remote** issuer that is not hosted by this application. |

### Allowed `type` and `endpoint-path` Values

These values are **not** free text. The `type` is a fixed string used by remote participants to discover the service, and the `endpoint-path` must match the actual path of the REST controller that handles requests for that service.

| Role | `type` | `endpoint-path` | Controller / path |
|---|---|---|---|
| **Holder** (credential service) | `CredentialService` | `/dcp` | `DcpController` — `POST /dcp/presentations/query`, `POST /dcp/credentials`, `POST /dcp/offers` |

> **How the verifier uses this:** When a verifier receives a request, it resolves the holder's DID document, looks up the service entry with `type=CredentialService`, and calls `<serviceEndpoint>/presentations/query`. The `endpoint-path=/dcp` produces a `serviceEndpoint` of `http(s)://<host>:<port>/dcp`, so the final query URL becomes `http(s)://<host>:<port>/dcp/presentations/query` — which maps directly to `DcpController`.

> **Note:** Setting `endpoint-path` to any value other than `/dcp` for a `CredentialService` entry will cause verifiers to call a non-existent endpoint and fail.

### Connector Configuration (Holder + Verifier)

```properties
dcp.service-entries[0].id=TRUEConnector-Credential-Service
dcp.service-entries[0].type=CredentialService
dcp.service-entries[0].endpoint-path=/dcp
```


---

## 5. Trusted Issuers

Defines which credential issuers are trusted for each credential type. Credentials whose `issuer` DID is not on the trust list will be **rejected**.

| Property | Required | Default | Description |
|---|---|---|---|
| `dcp.trusted-issuers.<CredentialType>` | **Yes** (at least one) | empty map | Comma-separated list of trusted issuer DIDs for the given credential type. The `<CredentialType>` key is case-sensitive and must match the `type` field in the received credential. |

```properties
# Single issuer
dcp.trusted-issuers.MembershipCredential=did:web:issuer.example.com

# Multiple issuers for the same type
dcp.trusted-issuers.MembershipCredential=did:web:issuer1.example.com,did:web:issuer2.example.com

# Multiple credential types
dcp.trusted-issuers.MembershipCredential=did:web:issuer.example.com
dcp.trusted-issuers.VerifiableCredential=did:web:issuer.example.com
```

> **Warning:** If this map is empty, all credential verifications will fail and a startup warning is logged.

---

## 6. Issuer Location (Holder only)

Tells the holder where to discover the external credential issuer service when requesting new credentials.

| Property | Required | Default | Description |
|---|---|---|---|
| `dcp.issuer.location` | No | empty | DID or URL of the credential issuer. When set, the holder uses this to discover the issuer's service endpoint via DID resolution and then requests credentials. Example: `did:web:localhost%3A8084` or `http://issuer-host:8084/issuer`. |

> **Note:** This property is used by `CredentialIssuanceClient`. If you do not need automatic credential issuance leave it empty.

---

## 7. Verifiable Presentation (VP) Properties

Controls whether the connector generates a Verifiable Presentation JWT for authenticating outgoing requests to other connectors.

| Property | Required | Default | Description |
|---|---|---|---|
| `dcp.vp.enabled` | No | `false` | Set to `true` to enable VP JWT generation for outbound connector authentication. When `false` the connector skips VP-based authentication. |
| `dcp.vp.scope` | No | empty string (all credentials) | Comma-separated list of credential types to include in the VP. If empty, all stored credentials are considered. Example: `MembershipCredential` or `MembershipCredential,DataspaceCredential`. |

```properties
dcp.vp.enabled=true
dcp.vp.scope=MembershipCredential
```

---

## 8. Revocation Cache (Holder only)

The holder checks StatusList2021 revocation status when validating incoming credentials. The result is cached to avoid repeated HTTP fetches.

| Property | Required | Default | Description |
|---|---|---|---|
| `dcp.revocation.cache.ttlSeconds` | No | `300` | How long (in seconds) a fetched revocation status list is cached in memory before being re-fetched from the remote URL. Increase for better performance, decrease for fresher revocation data. |

```properties
dcp.revocation.cache.ttlSeconds=600
```

---

## 9. Token Validation

Controls tolerance when validating the timing claims of incoming self-issued ID tokens.

| Property | Required | Default | Description |
|---|---|---|---|
| `dcp.clock-skew-seconds` | No | `120` | Allowed clock skew in seconds when verifying `exp`, `nbf`, and `iat` JWT claims. Increase if your environment has clock synchronisation issues. Must be `>= 0`. |

```properties
dcp.clock-skew-seconds=120
```

---

## 10. Module Enable/Disable Switches

| Property | Applies to | Default | Description |
|---|---|---|---|
| `dcp.holder.enabled` | `dcp-holder` | `true` | Set to `false` to prevent the holder autoconfiguration from loading. |
| `dcp.verifier.enabled` | `dcp-verifier` | `true` | Set to `false` to prevent the verifier autoconfiguration from loading. |

```properties
# Disable holder if this node only acts as a verifier
dcp.holder.enabled=false
dcp.verifier.enabled=true
```

---

## 11. Required Spring Properties

These are standard Spring Boot properties that the DCP libraries depend on.

| Property | Required | Description |
|---|---|---|
| `server.port` | **Yes** (host app) | The HTTP port the host application listens on. This is a standard Spring Boot property that belongs to the host application, not the library itself. It is read by `BaseDidDocumentConfiguration` to build service endpoint URLs in the DID document when `dcp.base-url` is not set. |
| `server.ssl.enabled` | No (`false`) | Set to `true` when the host application is served over HTTPS. Affects the protocol used in service endpoint URL construction (`http` vs `https`) and selects the secure OkHttpClient configuration. |
| `spring.data.mongodb.host` | **Yes** (for holder) | MongoDB host. Required by `dcp-holder` for credential and key metadata storage. |
| `spring.data.mongodb.port` | **Yes** (for holder) | MongoDB port (default `27017`). |
| `spring.data.mongodb.database` | **Yes** (for holder) | MongoDB database name. |
| `spring.data.mongodb.authentication-database` | No | Authentication database (typically `admin`). |
| `spring.data.mongodb.username` | No | MongoDB username (omit for unauthenticated local instances). |
| `spring.data.mongodb.password` | No | MongoDB password. |

### SSL bundle (when `server.ssl.enabled=true`)

When TLS is enabled, the DCP HTTP client reads its trust material from the Spring SSL bundle named **`connector`**:

```properties
spring.ssl.bundle.jks.connector.truststore.location=classpath:certs/dsp-truststore.p12
spring.ssl.bundle.jks.connector.truststore.password=password
spring.ssl.bundle.jks.connector.truststore.type=PKCS12
```

---

## 12. SSL / TLS (optional)

| Property | Default | Description |
|---|---|---|
| `server.ssl.enabled` | `false` | Enable HTTPS on the embedded server. |
| `server.ssl.key-alias` | — | Key alias inside the server keystore. |
| `server.ssl.key-store` | — | Path to the server keystore (e.g. `classpath:certs/connector.p12`). |
| `server.ssl.key-store-password` | — | Password for the server keystore. |
| `server.ssl.key-store-type` | — | Keystore type (e.g. `PKCS12`). |

---

## 13. Complete Example — Holder + Verifier

The following is a full example `application.properties` for a connector that acts as both holder and verifier:

```properties
# ============================================================
# Core Identity
# ============================================================
dcp.connector-did=did:web:localhost%3A8080:consumer
dcp.host=localhost
dcp.auto-register-path-endpoints=true
dcp.enable-legacy-endpoints=true

# ============================================================
# Keystore (EC key for signing / DID verification method)
# ============================================================
dcp.keystore.path=eckey.p12
dcp.keystore.password=password
dcp.keystore.alias=dsptrueconnector

# ============================================================
# DID Document Service Entries
# type and endpoint-path are NOT free text — they must match
# the fixed values defined in the "Allowed type and endpoint-path
# Values" table. endpoint-path=/dcp maps to DcpController.
# ============================================================
dcp.service-entries[0].id=TRUEConnector-Credential-Service
dcp.service-entries[0].type=CredentialService
dcp.service-entries[0].endpoint-path=/dcp

# ============================================================
# Trusted Issuers
# ============================================================
dcp.trusted-issuers.MembershipCredential=did:web:localhost%3A8084
dcp.trusted-issuers.VerifiableCredential=did:web:localhost%3A8084

# ============================================================
# Issuer Location (holder will request credentials from here)
# ============================================================
dcp.issuer.location=did:web:localhost%3A8084

# ============================================================
# Verifiable Presentation
# ============================================================
dcp.vp.enabled=false
dcp.vp.scope=MembershipCredential

# ============================================================
# Revocation Cache
# ============================================================
dcp.revocation.cache.ttlSeconds=300

# ============================================================
# Token Validation
# ============================================================
dcp.clock-skew-seconds=120

# ============================================================
# Module switches (both enabled by default, shown for clarity)
# ============================================================
dcp.holder.enabled=true
dcp.verifier.enabled=true

# ============================================================
# Spring / MongoDB (required for dcp-holder)
# server.port is a host-application property, not a library property.
# It is read by dcp-common to build DID document service endpoint URLs
# when dcp.base-url is not set. Set it in the host application's
# application.properties (e.g. server.port=8080).
# ============================================================
server.ssl.enabled=false
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=true_connector_consumer
spring.data.mongodb.authentication-database=admin
```

---

## 14. Host Application Note — `dcp.did.cache.ttl`

`dcp.did.cache.ttl` is consumed by `CredentialUtils` in the `tools` module of the host application (not by `dcp-holder` or `dcp-verifier` directly) via `@Value("${dcp.did.cache.ttl:300}")`. The default is `300` seconds. If the host application includes the `tools` module and the default is not suitable, set this property there:

```properties
dcp.did.cache.ttl=600
```




