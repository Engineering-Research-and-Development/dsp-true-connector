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

### Self-Issued ID Token validation

`SelfIssuedIdTokenService.validateToken()` applies the checks of DCP v1.0.1 §4.3.3 *Validating Self-Issued ID Tokens*. A token is accepted only if all of them pass:

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

