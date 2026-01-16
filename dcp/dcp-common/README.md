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

