# DCP Module - Decentralized Claims Protocol

This module implements the Eclipse Decentralized Claims Protocol (DCP) for managing Verifiable Credentials in dataspaces.

## 📚 Documentation

- **[Verifiable Credentials Guide](doc/VERIFIABLE_CREDENTIALS_GUIDE.md)** - Comprehensive guide for understanding and using Verifiable Credentials (start here if you're new to VCs)
- **[ProfileId Guide](doc/PROFILEID_GUIDE.md)** - Understanding the mandatory profileId field and how it's automatically determined
- **[Quick Start Guide](doc/quick_start.md)** - Fast-track testing with Postman examples
- **[Credential Examples](doc/credential_message_examples.md)** - Ready-to-use JSON examples

## Overview

This module provides an auto-configuration for the DCP feature set so it will register its beans when the `dcp` JAR is present on the application's classpath.

## Features

The DCP module provides two main services:

### 1. Credential Service (`/dcp/*`)
Acts as a secure vault for Verifiable Credentials:
- Receives and stores credentials issued by trusted authorities
- Creates Verifiable Presentations on demand for verifiers
- Manages credential lifecycle and revocation checks

### 2. Issuer Service (`/issuer/*`)
Enables credential issuance capabilities:
- Processes credential requests from holders
- Issues credentials with cryptographic proofs
- Tracks issuance status and manages approval workflows

## Auto-configuration

### What the auto-configuration does

- Registers `it.eng.dcp.autoconfigure.DcpHolderAutoConfiguration` via Spring Boot's auto-configuration discovery (file: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`).
- Binds the configuration properties under the `dcp.*` prefix to `it.eng.dcp.config.DcpProperties` (enabled via `@EnableConfigurationProperties` in the auto-config).
- Imports `DCPMongoConfig` to enable the module's Mongo repositories and related configuration.
- Component-scans only the `it.eng.dcp` package to register controllers, services and other beans belonging to this module.

### Important implementation notes

- `DcpProperties` must NOT be annotated with `@Component` or other stereotype annotations. It should be a plain `@ConfigurationProperties` class. The auto-configuration enables the properties binding, which creates the single properties bean. If `DcpProperties` is also annotated with `@Component`, you'll end up with two beans and ambiguous injection errors.

### Configuration control

- The auto-configuration is conditional on the property `dcp.enabled`.
  - To disable the whole module auto-configuration, set:

    ```properties
    dcp.enabled=false
    ```

  - By default the auto-configuration is enabled.

### Example usage

- To enable the module (default): nothing to do if the `dcp` JAR is on the classpath.
- To disable the module, add to `application.properties` (main app):

    ```properties
    dcp.enabled=false
    ```

### Testing and verification

- Start the application with the `dcp` module on the classpath and confirm that beans like `it.eng.dcp.service.SelfIssuedIdTokenService` are present.
- If you want an automated check, you can add a small Spring Boot test in the `dcp` module that uses `ApplicationContext` to assert the presence or absence of key beans depending on `dcp.enabled`.

### Notes for the main application

- Since you removed package scans in `ApplicationConnector.java`, the DCP module will rely on its auto-configuration to register its beans. Ensure the `dcp` module is included as a dependency (it already is in the parent/connector POM). Also ensure the `META-INF/spring/...AutoConfiguration.imports` resource is packaged into the JAR (Maven does this by default when placed under `src/main/resources`).

