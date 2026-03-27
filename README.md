# TRUE Connector

<p align='left'>
  <a href="https://github.com/Engineering-Research-and-Development/dsp-true-connector/actions/workflows/build.yml"><img alt="Build" src="https://img.shields.io/github/actions/workflow/status/Engineering-Research-and-Development/dsp-true-connector/build.yml?branch=develop&label=Build" /></a>
  &nbsp;
  <a href="https://github.com/Engineering-Research-and-Development/dsp-true-connector/actions/workflows/tck_compliance.yml"><img alt="TCK compliance" src="https://img.shields.io/github/actions/workflow/status/Engineering-Research-and-Development/dsp-true-connector/tck_compliance.yml?branch=main&label=TCK%20compliance" /></a>
  &nbsp;
  <a href="https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/"><img alt="Dataspace protocol" src="https://img.shields.io/badge/Dataspace%20protocol-2025--1-blue" /></a>
</p>

## About TRUE Connector

TRUE Connector is a **Dataspace Protocol (DSP) compliant connector** for secure, trusted data exchange across distributed dataspace ecosystems. It enables participants to negotiate contracts, transfer data, and manage access policies in a decentralized environment.

## Quick Start

- **New to the connector?** Start with the [Getting Started Guide](doc/README.md#getting-started)
- **Want to develop?** Read the [Development Guide](doc/development-procedure.md)
- **Ready to deploy?** See [Deployment Options](doc/README.md#deployment--operations)
- **Questions about security?** Check [Security & Certificates](doc/README.md#security--certificates)

## Documentation

All detailed documentation is organized in the [**Documentation Index**](doc/README.md), including:

- **Development**: Setup, testing, configurations, and development workflow
- **Deployment**: Kubernetes, Terraform, and operations guides
- **Security**: TLS/OCSP configuration, PKI management, and compliance
- **API & Integration**: Data transfer, artifact upload, contract negotiation, authorization
- **Architecture**: Domain models, policy enforcement, catalog structure
- **Advanced Topics**: S3 configuration, async improvements, performance tuning

## Project Structure

This is a multi-module Maven project:

- **catalog** - Catalog document processing and artifact management
- **negotiation** - Contract negotiation and policy enforcement
- **connector** - Main application wrapper and API endpoints
- **data-transfer** - Data transfer operations and SFTP support
- **tools** - Shared utilities and filtering

## Key Technologies

- **Runtime**: Java 17, Spring Boot 3.1.2, MongoDB 7.0.12
- **Protocols**: Dataspace Protocol (DSP), HTTPS, SFTP, DCAT-AP
- **Frontend**: Angular 19 UI ([separate repository](https://github.com/Engineering-Research-and-Development/dsp-true-connector-ui))

## Development Requirements

- **IDE**: Eclipse STS, IntelliJ, VS Code
- **Resources**: 16 GB RAM, 5 GB disk, 4+ cores
- **Dependencies**: Maven 3.9.4+, Docker (for integration tests)

See [Development Guide](doc/development-procedure.md) for detailed setup instructions.

## Links

- [GitHub Repository](https://github.com/Engineering-Research-and-Development/dsp-true-connector)
- [Project Dashboard](https://github.com/users/Engineering-Research-and-Development/projects/2)
- [CI/CD Workflows](https://github.com/Engineering-Research-and-Development/dsp-true-connector/actions)
- [Frontend UI](https://github.com/Engineering-Research-and-Development/dsp-true-connector-ui)