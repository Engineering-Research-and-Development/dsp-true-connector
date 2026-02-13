# TRUE Connector

<p align='left'>
  <a href="https://github.com/Engineering-Research-and-Development/dsp-true-connector/actions/workflows/build.yml"><img alt="Build" src="https://img.shields.io/github/actions/workflow/status/Engineering-Research-and-Development/dsp-true-connector/build.yml?branch=develop&label=Build" /></a>
  &nbsp;
  <a href="https://github.com/Engineering-Research-and-Development/dsp-true-connector/actions/workflows/tck_compliance.yml"><img alt="TCK compliance" src="https://img.shields.io/github/actions/workflow/status/Engineering-Research-and-Development/dsp-true-connector/tck_compliance.yml?branch=main&label=TCK%20compliance" /></a>
  &nbsp;
  <a href="https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/"><img alt="Dataspace protocol" src="https://img.shields.io/badge/Dataspace%20protocol-2025--1-blue" /></a>
</p>

## Development requirements

 - IDE : Eclipse STS, IntelliJ, VS Code etc.
  - Resources: 16 GB RAM, 5 GB of disk space, 4 Cores multithreaded processor
 - Languages/Frameworks: Java 17, Maven 3.9.4 (compatible with java 17), SpringBoot 3.1.2 (Spring framework 6)
 - Database: MongoDB 7.0.12
 - Optional: Keycloak 26.x (see doc/keycloak.md)
 - Libraries: lombok, fasterxml.jackson, okhttp3, com.auth0:java-jwt, org.apache.commons:commons-lang3, org.apache.sshd:sshd-core, org.apache.sshd:sshd-sftp
  - Testing: Junit, Mockito; integration tests - MockMvc, Test Containers, Docker
  - Debugging tools: IDE debug
  - FE Technologies: Angular 19
  - Other technologies/Protocols used: Dataspace Protocol, HTTPS, sftp, DCAT-AP
    - Useful Tools: Postman, Robo 3T (or any other MongoDB visualization tool)
  - [Repository source code and versioning](https://github.com/Engineering-Research-and-Development/dsp-true-connector)
  - [Task Management and Monitoring](https://github.com/users/Engineering-Research-and-Development/projects/2)
  - [CI/CD](https://github.com/Engineering-Research-and-Development/dsp-true-connector/actions)
  - Deploy management: Not yet, planned to be dockerized and maybe some cloud solution

Please refer to the [development procedure](doc/development_procedure.md) for more details.
Optional auth setup: [Keycloak guide](doc/keycloak.md).

## Project structure

Project is structured as multi-module maven project:

* catalog - module containing logic for processing catalog document
* negotiation - module containing logic for performing contract negotiation
* connector - wrapper module for starting application
* data-transfer - module maintaining transfer of the data
* tools - various tools and utilities needed across modules

## GUI tool for DSP TRUEConnector

* [GUI frontend](https://github.com/Engineering-Research-and-Development/dsp-true-connector-ui)

## Documentation

### Getting Started
- **[Keycloak Integration Complete Summary](KEYCLOAK_INTEGRATION_COMPLETE_SUMMARY.md)** - ⭐ **NEW** Comprehensive Keycloak integration overview
- [Development Procedure](doc/development_procedure.md) - Setup and coding guidelines
- [Keycloak Setup Guide](doc/keycloak.md) - OAuth2/OIDC authentication setup
- [Security Documentation](doc/security.md) - Security architecture and best practices
- [Postman Collection Guide](README_POSTMAN.md) - API testing with Postman

### Keycloak Integration Details
- [Keycloak Realm Configuration](KEYCLOAK_REALM_ENVIRONMENT_MAPPING.md) - Multi-realm setup
- [Token Flow Diagrams](KEYCLOAK_TOKEN_FLOW_DIAGRAMS.md) - Authentication flow visualizations
- [Postman Keycloak Refactoring](POSTMAN_KEYCLOAK_REFACTORING.md) - Collection updates
- [Realm Verification](REALM_VERIFICATION_SUMMARY.md) - Verification procedures

### Architecture & Refactoring
- [Refactoring Documentation](doc/refactoring/README.md) - Major refactoring efforts
  - [Authentication System](doc/refactoring/AUTHENTICATION_REFACTORING.md)
  - [Circular Dependency Resolution](doc/refactoring/CIRCULAR_DEPENDENCY_RESOLUTION.md)
  - [DAPS Integration](doc/refactoring/DAPS_REFACTORING_HISTORY.md)
  - [Keycloak Integration](doc/refactoring/KEYCLOAK_INTEGRATION_HISTORY.md)
  - [Provider/Consumer Roles](doc/refactoring/PROVIDER_CONSUMER_ROLES.md)
  - [Test Coverage](doc/refactoring/TEST_COVERAGE_AND_IMPROVEMENTS.md)

### Troubleshooting
- [Troubleshooting Guide](doc/troubleshooting/README.md) - Common issues and fixes
  - [AWS S3 Integration Fix](doc/troubleshooting/AWS_S3_INTEGRATION_FIX.md)
  - [Restart Connectors Fix](doc/troubleshooting/RESTART_CONNECTORS_FIX.md)
  - [Postman Import Guide](doc/troubleshooting/POSTMAN_IMPORT_GUIDE.md)

### Module Documentation
- [Catalog Module](catalog/doc/catalog.md)
- [Negotiation Module](negotiation/doc/model.md)
- [Data Transfer Module](data-transfer/doc/data-transfer.md)

