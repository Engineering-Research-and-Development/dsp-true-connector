# Documentation Index

Welcome to the TRUE Connector documentation. Use this index to navigate detailed guides organized by role and use case.

## Getting Started

Start here if you're new to TRUE Connector:

- **[Project Overview](../README.md)** - High-level introduction and quick links
- **[User Guide](user-guide.md)** - Plain-language overview for operators: publish data, discover data, understand key concepts
- **[DSP Implementation Guide](dsp-implementation-guide.md)** - How to use all DSP protocol features with curl examples (Catalog, Negotiation, Transfer)
- **[Development Requirements & Setup](development-procedure.md)** - Environment setup and prerequisites
- **[Test Containers Guide](test-containers-starting-guide.md)** - Docker and testing infrastructure setup

## DSP Protocol

Guides for using the Dataspace Protocol features of TRUE Connector:

- **[DSP Implementation Guide](dsp-implementation-guide.md)** - PRIMARY guide: how to use Catalog, Negotiation, and Transfer protocols with curl examples and end-to-end workflows
- **[DSP Protocol Reference](dsp-protocol-reference.md)** - Concepts glossary, state tables, message types, and all DSP endpoint paths

### DSP Module Technical Documentation
- **[Catalog Technical Docs](../catalog/doc/catalog-technical.md)** - Catalog API architecture, all endpoints, serialization
- **[Catalog User Guide](../catalog/doc/catalog.md)** - Managing datasets, distributions, offers, and artifacts
- **[Artifact Upload](../catalog/doc/artifact-upload.md)** - Uploading data files to the catalog
- **[Negotiation Technical Docs](../negotiation/doc/negotiation-technical.md)** - State machine, all DSP endpoints, service architecture
- **[Negotiation Protocol Flows](../negotiation/doc/negotiation-protocol-flows.md)** - Step-by-step negotiation guide for consumers and providers
- **[Data Transfer Technical Docs](../data-transfer/doc/data-transfer-technical.md)** - Transfer strategies, state machine, all endpoints
- **[Data Transfer User Guide](../data-transfer/doc/data-transfer.md)** - HTTP-PULL, HTTP-PUSH, and external REST transfer flows

## Development

Guides for developers building and extending the connector:

- **[Developer Guide](developer-guide.md)** - Architecture overview, module structure, key patterns, testing approach

- **[Development Procedure](development-procedure.md)** - Workflow, Definition of Ready/Done, GitHub Actions, task management
- **[Spring Profiles](profiles.md)** - Configuration profiles for different environments
- **[Property Configuration](update-properties.md)** - How to configure connector properties
- **[Test Containers](test-containers-starting-guide.md)** - Integration testing with Docker containers
- **[Code Quality](spotbugs.md)** - SpotBugs scanning and code analysis

**Module-specific documentation:**
- **[Negotiation - Policy Enforcement](../negotiation/doc/policy-enforcement.md)** - Policy evaluation and enforcement logic
- **[Negotiation - Data Models](../negotiation/doc/model.md)** - Domain model classes and structures
- **[Tools - Generic Filtering](../tools/doc/generic-filtering.md)** - Filtering API and examples
- **[Tools - Application Property System](../tools/doc/application-property.md)** - Property management and access

## Deployment & Operations

Guides for deploying and operating TRUE Connector:

- **[Implementation Reference](implementation-reference.md)** - Production configuration: security, S3, OCSP, properties, user management

### Kubernetes & Terraform

- **[Terraform Overview](../terraform/terraform.md)** - Terraform structure and organization
- **[Terraform Deployment Guide](../terraform/terraform-deployment-guide.md)** - Complete step-by-step deployment (local Kind + remote cluster)
- **[Terraform Quick Reference](../terraform/quick-reference.md)** - Quick configuration reference for common scenarios
- **[Terraform Usage Examples](../terraform/usage-examples.md)** - Real-world Terraform examples

## Security & Certificates

Security configuration, TLS setup, and certificate management:

- **[Security Configuration](security.md)** - TLS/HTTPS, OCSP validation, certificate setup
- **[PKI Certificate Guide](certificate/pki-certificate-guide.md)** - Complete guide for PKI infrastructure, certificate generation, and renewal
- **[OCSP Guide](ocsp/ocsp-guide.md)** - OCSP responder setup, certificate validation, and revocation management

## Data Transfer & Integration

API documentation and integration guides:

### Catalog & Artifacts
- **[Catalog Structure](../catalog/doc/catalog.md)** - Understanding catalog organization (from init_data)
- **[Artifact Upload](../catalog/doc/artifact-upload.md)** - How to upload artifacts and data using Postman or REST API

### Data Transfer
- **[Data Transfer Overview](../data-transfer/doc/data-transfer.md)** - Data transfer assumptions and flow
- **[SFTP Configuration](../data-transfer/doc/sftp.md)** - SFTP transfer implementation and setup

### Contract Management
- **[Contract Negotiation](../connector/doc/negotiation.md)** - Negotiation flow and scheduler management
- **[User Authorization](../connector/doc/users.md)** - User types and authorization mechanisms
- **[Data Transfer Operations](../connector/doc/transfer.md)** - Transfer endpoint configuration

## Architecture & Domain

Understanding the connector's architecture and domain concepts:

- **[Developer Guide](developer-guide.md)** - Module structure, technology stack, design patterns, how to add new features
- **[Connector Technical Docs](../connector/doc/connector-technical.md)** - Security architecture, authentication filters, user management internals
- **[Connector Platform Features](../connector/doc/connector-platform-features.md)** - Operator guide for the connector module
- **[Tools Technical Docs](../tools/doc/tools-technical.md)** - Shared utilities: S3, OCSP, encryption, audit, properties
- **[Tools Implementation Guide](../tools/doc/tools-implementation.md)** - Operator guide for tools module features

- **[Policy Enforcement](../negotiation/doc/policy-enforcement.md)** - Policy evaluation entry point and mechanisms
- **[Data Models](../negotiation/doc/model.md)** - Core domain classes and JSON representations
- **[Catalog Structure](../catalog/doc/catalog.md)** - Catalog document organization and DCAT-AP alignment
- **[Verifiable Credentials](verifiable-credentials.md)** - VC support and integration
- **[Identity Hub Integration](identity-hub.md)** - Identity management and federation

## Advanced Topics

Configuration, optimization, and specialized deployments:

### S3 & Storage
- **[S3 Configuration](s3-configuration.md)** - S3 storage integration and setup
- **[S3 Upload Modes](solutions/s3-upload-mode-configuration.md)** - Upload mode strategies and configuration
- **[Async S3 Upload Improvements](solutions/async-s3-upload-improvements.md)** - Performance optimizations for S3 uploads

### Compliance & Testing
- **[TCK Compliance](tck/tck-compliancy.md)** - Dataspace Protocol compliance testing
- **[Code Quality & SpotBugs](spotbugs.md)** - Static analysis and bug detection

## Changelog

- **[CHANGELOG](../CHANGELOG.md)** - Release notes and version history

## Quick Navigation

**By Role:**
- **New user / Operator** → [User Guide](user-guide.md) → [DSP Implementation Guide](dsp-implementation-guide.md)
- **Developer** → [Developer Guide](developer-guide.md) → module technical docs
- **DevOps / Implementer** → [Implementation Reference](implementation-reference.md) → [Terraform Deployment Guide](../terraform/terraform-deployment-guide.md)
- **Security Engineer** → [Security Configuration](security.md) and [PKI Guide](certificate/pki-certificate-guide.md)
- **API Consumer** → [DSP Implementation Guide](dsp-implementation-guide.md) and [Artifact Upload](../catalog/doc/artifact-upload.md)

**By Task:**
- **I want to set up the development environment** → [Development Setup](development-procedure.md)
- **I want to publish data** → [Catalog User Guide](../catalog/doc/catalog.md) → [Artifact Upload](../catalog/doc/artifact-upload.md)
- **I want to access data from another connector** → [DSP Implementation Guide](dsp-implementation-guide.md)
- **I want to understand DSP concepts** → [DSP Protocol Reference](dsp-protocol-reference.md)
- **I want to deploy to Kubernetes** → [Terraform Deployment Guide](../terraform/terraform-deployment-guide.md)
- **I need to configure TLS/HTTPS** → [Security Configuration](security.md)
- **I need to upload data/artifacts** → [Artifact Upload](../catalog/doc/artifact-upload.md)
- **I want to extend the connector** → [Developer Guide](developer-guide.md)
- **I want to understand the architecture** → [Developer Guide](developer-guide.md) & [Negotiation Technical Docs](../negotiation/doc/negotiation-technical.md)

## Document Map

### Root Documentation
```
README.md (Project overview)
CHANGELOG.md (Release notes)
```

### /doc/ Directory
```
doc/README.md (This index)
doc/user-guide.md                  ← NEW: Getting started guide for operators
doc/dsp-implementation-guide.md    ← NEW: PRIMARY DSP usage guide with curl examples
doc/dsp-protocol-reference.md      ← NEW: DSP concepts glossary and endpoint reference
doc/developer-guide.md             ← NEW: Architecture and extension guide for developers
doc/implementation-reference.md    ← NEW: Production configuration reference
doc/development-procedure.md
doc/security.md
doc/test-containers-starting-guide.md
doc/update-properties.md
doc/profiles.md
doc/spotbugs.md
doc/verifiable-credentials.md
doc/identity-hub.md
doc/s3-configuration.md
doc/certificate/pki-certificate-guide.md
doc/ocsp/ocsp-guide.md
doc/solutions/s3-upload-mode-configuration.md
doc/solutions/async-s3-upload-improvements.md
doc/tck/tck-compliancy.md
```

### Terraform Documentation
```
terraform/terraform.md
terraform/terraform-deployment-guide.md
terraform/quick-reference.md
terraform/usage-examples.md
```

### Module Documentation
```
catalog/doc/catalog.md
catalog/doc/catalog-technical.md   ← NEW (Phase 1): Full catalog API and architecture
catalog/doc/artifact-upload.md
connector/doc/connector-technical.md      ← NEW (Phase 1): Security and platform internals
connector/doc/connector-platform-features.md ← NEW (Phase 1): Operator guide
connector/doc/transfer.md
connector/doc/users.md
connector/doc/negotiation.md
data-transfer/doc/data-transfer.md
data-transfer/doc/data-transfer-technical.md ← NEW (Phase 1): Transfer architecture
data-transfer/doc/sftp.md
negotiation/doc/negotiation-technical.md  ← NEW (Phase 1): State machine and all endpoints
negotiation/doc/negotiation-protocol-flows.md ← NEW (Phase 1): Step-by-step negotiation guide
negotiation/doc/policy-enforcement.md
negotiation/doc/model.md
tools/doc/tools-technical.md        ← NEW (Phase 1): Shared utilities architecture
tools/doc/tools-implementation.md   ← NEW (Phase 1): Operator guide for tools
tools/doc/generic-filtering.md
tools/doc/application-property.md
```

## Documentation Standards

Templates and guidelines for creating consistent documentation when adding new features or protocols.
See the **[Template Directory](template/README.md)** for an overview and usage instructions.

| File | Purpose |
|------|---------|
| [template/documentation-standards.md](template/documentation-standards.md) | Comprehensive guidelines: audiences, layers, quality standards, naming conventions |
| [template/documentation-skill.md](template/documentation-skill.md) | Copilot skill definition for automated documentation generation |
| [template/documentation-prompt-template.md](template/documentation-prompt-template.md) | Ready-to-use prompt template — copy, fill placeholders, run in Copilot CLI |

Use these when documenting new features such as the Decentralized Claims Protocol.

## Need Help?

- Check the [Project Dashboard](https://github.com/users/Engineering-Research-and-Development/projects/2) for tasks and discussions
- Review [GitHub Issues](https://github.com/Engineering-Research-and-Development/dsp-true-connector/issues) for known problems and solutions
- Check the [CI/CD Status](https://github.com/Engineering-Research-and-Development/dsp-true-connector/actions) to see current build health
