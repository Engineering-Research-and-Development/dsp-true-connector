# DCP Common Library

This module provides shared models and utilities for DCP (Decentralized Claims Protocol) modules, specifically for the Issuer and Holder/Verifier components.

## Overview

The `dcp-common` library contains:
- **Shared data models**: Common data structures used across DCP modules
- **Utility classes**: Helper functions for DID conversion and other common operations
- **Base classes**: Abstract base classes for DCP messages

## Components

### Models

#### Core Models
- **BaseDcpMessage**: Abstract base class for all DCP protocol messages
- **DidDocument**: W3C DID Document representation with services and verification methods
- **ServiceEntry**: Service endpoint entry in a DID Document
- **VerificationMethod**: Cryptographic verification method for DID Documents

#### Enumerations
- **CredentialStatus**: Status enumeration for credential requests (PENDING, RECEIVED, ISSUED, REJECTED)
- **ProfileId**: Supported DCP profile identifiers (VC11_SL2021_JWT, VC11_SL2021_JSONLD)

### Utilities

- **DidUrlConverter**: Utility for converting URLs to DID:web identifiers and extracting base URLs

### Participant context

A DCP host serves one or more participants. `it.eng.dcp.common.participant` defines the SPI the library uses to find out who it acts as; the services take the participant as an explicit parameter and never rely on a thread-local.

| Type | Role | Default (registered only when the host defines no bean) |
|---|---|---|
| `ParticipantContext` | Immutable record: ID, DID, signing key reference, advertised service entries, trusted issuers, issuer metadata configuration and state (`ENABLED`/`DISABLED`). | - |
| `ParticipantContextProvider` | `find(id)` returns the context (also a disabled one, which reports `DISABLED`) or empty for an unknown ID; `findEnabled(id)` never returns a disabled participant. | `SingleParticipantContextProvider`: one participant, ID `default`, built from the `dcp.*` properties. |
| `ParticipantContextResolver` | Determines the participant ID of an HTTP request. | `FixedParticipantContextResolver` (`dcp.participant.resolver=fixed`). `PathParticipantContextResolver` (`path`) takes the first path segment before a DCP base path; an unscoped request and a reserved ID resolve to empty. |
| `ParticipantKeyProvider` | `activeSigningKey`, `publishedKeys` (active key plus keys retired within `dcp.key-grace-period`) and `rotate`. Signing, DID document generation and key rotation always go through it. | `DefaultParticipantKeyProvider`, wrapping `KeyService` and `KeyMetadataService`. |
| `ParticipantAdminAuthorization` | Asks the host whether an administration principal may act on a participant. | Allows any administration principal on the single participant. |

`ParticipantContextConfiguration` registers the defaults with `@ConditionalOnMissingBean`; `DcpHolderAutoConfiguration`, `DcpVerifierAutoConfiguration` and `IssuerApplication` import it. The Issuer does not use the participant context in its services or controllers: it is one central service, and `IssuerIdentity` resolves its single DID and signing key from these defaults. A host replaces a default by defining its own bean of that type, ahead of the library configuration. DCP keys are separate from TLS keys.

Controllers use `RequestParticipantContext` at the edge: `require(request)` resolves the usable participant for protocol routes and throws `ParticipantNotFoundException` (`404`, before any token validation) for an unscoped, unknown or disabled participant; `requireForAdmin(request, principal)` additionally asks `ParticipantAdminAuthorization` and throws `ParticipantForbiddenException` (`403`). Asynchronous work receives the `ParticipantContext` as a parameter.

The path resolver never resolves the IDs in `dcp.participant.reserved-ids` (`internal`, `api`, `actuator`, `.well-known` and the DSP protocol prefixes) or the configured base paths. Serving DID documents at their DID path is not part of this SPI (#424).

### Self-Issued ID Token validation

`SelfIssuedIdTokenService.validateToken(token, participant)` applies the checks of DCP v1.0.1 §4.3.3 *Validating Self-Issued ID Tokens*. A token is accepted only if all of them pass:

1. `iss` and `sub` are present and equal.
2. `aud` equals this participant's DID.
3. The `sub` DID document resolves, and its `id` equals `sub`.
4. A key is selected from that document:
   - with a `kid` header, the verification method whose ID (absolute DID URL or fragment) or JWK `kid` matches it;
   - without a `kid`, the document's single verification method. A document with several verification methods is rejected.
5. The selected key is referenced by the document's `capabilityInvocation` relationship.
6. The signature verifies with that key. Only EC keys are supported; other key types are rejected.
7. `nbf`, `exp` and `iat` are within limits (120 s clock skew; `iat` at most one hour old).
8. `jti` is present and has not been seen before.

A failed check throws `SecurityException` and publishes a `TOKEN_VALIDATION_FAILED` audit event. Logs carry `iss`, `kid` and the reason, never the token.

`HttpDidResolverService.resolvePublicKey()` returns `null` whenever no key satisfies the selection and relationship rules, so callers reject the token.

The DID documents this library serves (`DidDocumentService`) list their key under `authentication` and `capabilityInvocation`. A document that advertises an `IssuerService` endpoint also lists it under `assertionMethod`, the relationship for Verifiable Credential proofs. When reading other participants' documents:

- Relationship entries may be references, either absolute or `#fragment`. Verification method IDs may also be relative to the document `id`; all IDs are compared as absolute DID URLs.
- An embedded verification method in a relationship is recognized by its `id` only. Its key is used only if the same method is also listed under `verificationMethod`. Otherwise the token is rejected.
- When a `kid` matches no key of a cached DID document, the document is fetched again once (at most every 30 s per document), so a participant that has just rotated its key is accepted.

## Dependencies

- **Jackson**: For JSON serialization/deserialization
- **Lombok**: For reducing boilerplate code
- **Nimbus JOSE+JWT**: For JWT handling
- **Jakarta Validation**: For bean validation
- **Spring Data MongoDB**: For MongoDB annotations (provided scope)

## Usage

This module is intended to be used as a dependency in other DCP modules:

```xml
<dependency>
    <groupId>it.eng</groupId>
    <artifactId>dcp-common</artifactId>
    <version>${revision}</version>
</dependency>
```

## Package Structure

```
it.eng.dcp.common
├── participant/    # Participant context SPI and single-participant defaults
├── model/          # Shared data models
│   ├── BaseDcpMessage.java
│   ├── CredentialStatus.java
│   ├── DidDocument.java
│   ├── ProfileId.java
│   ├── ServiceEntry.java
│   └── VerificationMethod.java
└── util/           # Utility classes
    └── DidUrlConverter.java
```

## Building

This module is built as part of the parent project:

```bash
mvn clean install
```

Or build only this module:

```bash
cd dcp-common
mvn clean install
```

## Testing

Run tests with:

```bash
mvn test
```

## Version

Current version: 0.6.4-SNAPSHOT (inherited from parent)

