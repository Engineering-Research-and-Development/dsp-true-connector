# DCP Issuer Module Split - Documentation Index

## Overview

This directory contains comprehensive documentation for splitting the Verifiable Credentials Issuer functionality from the monolithic `dcp` module into a standalone service.

## Quick Navigation

### 📋 Start Here
- **[Executive Summary](ISSUER_MODULE_SUMMARY.md)** - High-level overview, perfect for stakeholders
- **[Architecture Diagrams](ISSUER_MODULE_ARCHITECTURE_DIAGRAMS.md)** - Visual representation of the changes

### 📖 Detailed Documentation
- **[Implementation Plan](ISSUER_MODULE_SPLIT_PLAN.md)** - Complete 11-phase implementation guide
- **[Technical Specification](ISSUER_MODULE_TECHNICAL_SPEC.md)** - Code examples and detailed specs

## Document Descriptions

### 1. ISSUER_MODULE_SUMMARY.md
**Purpose**: Executive-level overview  
**Audience**: Project managers, architects, stakeholders  
**Content**:
- Problem statement
- Proposed solution overview
- Key benefits
- Timeline estimate
- Success criteria

**Read this if**: You need a quick understanding of what's being done and why

---

### 2. ISSUER_MODULE_ARCHITECTURE_DIAGRAMS.md
**Purpose**: Visual architecture documentation  
**Audience**: Developers, architects, DevOps engineers  
**Content**:
- Current vs. proposed architecture diagrams
- DID document separation visualization
- Deployment architecture
- Credential issuance flows
- Key rotation flow
- Build and deployment flow
- Testing architecture

**Read this if**: You prefer visual explanations and need to understand the system architecture

---

### 3. ISSUER_MODULE_SPLIT_PLAN.md
**Purpose**: Comprehensive implementation guide  
**Audience**: Development team  
**Content**:
- Current state analysis
- Proposed architecture details
- 11-phase implementation plan
- Component mapping
- Port allocation
- DID structure
- API endpoints
- Database schema
- Security considerations
- Migration strategy
- Testing strategy
- Success criteria
- Risk mitigation
- Timeline estimates

**Read this if**: You're implementing the split and need step-by-step guidance

---

### 4. ISSUER_MODULE_TECHNICAL_SPEC.md
**Purpose**: Detailed technical specifications with code examples  
**Audience**: Developers  
**Content**:
- Complete `pom.xml` files for all modules
- Full source code examples for:
  - Application class
  - Configuration classes
  - Controllers
  - Services
  - Properties files
- Docker configuration
  - Dockerfile
  - docker-compose.yml
  - docker-compose-dev.yml
- Build and run scripts

**Read this if**: You need actual code examples and configuration files to implement

---

## Implementation Workflow

Follow this sequence when implementing the split:

```
1. Read ISSUER_MODULE_SUMMARY.md
   └─► Understand the why and what
   
2. Review ISSUER_MODULE_ARCHITECTURE_DIAGRAMS.md
   └─► Visualize the before and after
   
3. Study ISSUER_MODULE_SPLIT_PLAN.md
   └─► Understand the complete plan
   
4. Reference ISSUER_MODULE_TECHNICAL_SPEC.md
   └─► Copy and adapt code examples
   
5. Execute phases from ISSUER_MODULE_SPLIT_PLAN.md
   └─► Implement step by step
```

## Phase-by-Phase Checklist

Use this checklist to track progress:

### Phase 1: Create Common Library ⬜
- [ ] Read: Plan sections "Phase 1"
- [ ] Reference: Technical Spec "dcp-common/pom.xml"
- [ ] Create `dcp-common` module
- [ ] Move shared models
- [ ] Update dependencies

### Phase 2: Create Issuer Module Structure ⬜
- [ ] Read: Plan sections "Phase 2"
- [ ] Reference: Technical Spec "dcp-issuer/pom.xml"
- [ ] Create module structure
- [ ] Create Spring Boot application
- [ ] Set up auto-configuration

### Phase 3: Move Issuer Functionality ⬜
- [ ] Read: Plan sections "Phase 3", "Component Mapping"
- [ ] Reference: Technical Spec "Controllers", "Services"
- [ ] Copy and refactor controllers
- [ ] Copy and refactor services
- [ ] Copy and refactor repositories

### Phase 4: Separate DID Documents ⬜
- [ ] Read: Plan sections "Phase 4", "DID Structure"
- [ ] Reference: Diagrams "DID Document Separation"
- [ ] Update Issuer DID Document Service
- [ ] Update Holder DID Document Service
- [ ] Create separate keystores

### Phase 5: Configuration & Properties ⬜
- [ ] Read: Plan sections "Phase 5"
- [ ] Reference: Technical Spec "Application Properties"
- [ ] Create `IssuerProperties.java`
- [ ] Create `application-issuer.properties`
- [ ] Create metadata configuration

### Phase 6: Build Configuration ⬜
- [ ] Read: Plan sections "Phase 6"
- [ ] Reference: Technical Spec "Module Dependencies"
- [ ] Update `dcp-issuer/pom.xml`
- [ ] Test Maven build
- [ ] Verify executable JAR

### Phase 7: Docker Configuration ⬜
- [ ] Read: Plan sections "Phase 7"
- [ ] Reference: Technical Spec "Docker Configuration", Diagrams "Deployment Architecture"
- [ ] Create Dockerfile
- [ ] Create docker-compose.yml
- [ ] Create configuration directory
- [ ] Test Docker build

### Phase 8: Key Management & Rotation ⬜
- [ ] Read: Plan sections "Phase 8"
- [ ] Reference: Technical Spec "IssuerKeyRotationService", Diagrams "Key Rotation Flow"
- [ ] Create `IssuerKeyRotationService`
- [ ] Add scheduled task configuration
- [ ] Create admin endpoints

### Phase 9: Testing ⬜
- [ ] Read: Plan sections "Phase 9", "Testing Strategy"
- [ ] Reference: Diagrams "Testing Architecture"
- [ ] Write unit tests
- [ ] Write integration tests
- [ ] Write Docker tests

### Phase 10: Documentation ⬜
- [ ] Read: Plan sections "Phase 10"
- [ ] Create `dcp-issuer/README.md`
- [ ] Create deployment guide
- [ ] Create key rotation guide
- [ ] Update root README

### Phase 11: Cleanup & Refactoring ⬜
- [ ] Read: Plan sections "Phase 11"
- [ ] Remove issuer code from `dcp`
- [ ] Update `DidDocumentService`
- [ ] Update tests
- [ ] Verify no regressions

## Key Concepts

### Verifiable Credentials (VC)
Cryptographically signed credentials that can be independently verified. The issuer module creates and signs these credentials.

### DID (Decentralized Identifier)
A globally unique identifier that doesn't require a central authority. Each service (issuer and holder) has its own DID.

### DID Document
A JSON document containing public keys and service endpoints for a DID. Allows others to verify signatures and discover services.

### Key Rotation
The process of periodically replacing cryptographic keys to enhance security. Old keys are archived for verifying previously issued credentials.

## Module Relationships

```
Parent POM (trueconnector)
    │
    ├─► dcp-common (library)
    │       └─► Shared models and utilities
    │
    ├─► dcp (holder/verifier)
    │       └─► Depends on: dcp-common
    │
    └─► dcp-issuer (issuer)
            └─► Depends on: dcp-common, tools
```

## Port and Service Map

| Service | Port | DID | Database | Purpose |
|---------|------|-----|----------|---------|
| DCP Holder | 8083 | `did:web:localhost%3A8083:holder` | `true_connector_provider` (27017) | Credential storage, presentation |
| DCP Issuer | 8084 | `did:web:localhost%3A8084:issuer` | `issuer_db` (27018) | Credential issuance |

## API Endpoints Quick Reference

### Issuer (Port 8084)
- `GET /.well-known/did.json` - Issuer DID document
- `GET /issuer/metadata` - Issuer metadata
- `POST /issuer/credentials` - Request credentials
- `GET /issuer/requests/{id}` - Check request status
- `POST /issuer/requests/{id}/approve` - Approve request
- `POST /issuer/admin/rotate-key` - Manual key rotation

### Holder (Port 8083)
- `GET /.well-known/did.json` - Holder DID document
- `POST /dcp/credentials` - Receive credentials
- `POST /dcp/presentations` - Create presentations

## Related Documentation

### Existing DCP Documentation
- [Verifiable Credentials Overview](verifiable-credentials-overview.md)
- [VC Quick Reference](verifiable-credentials-quick-reference.md)
- [DCP Configuration Examples](DCP_CONFIGURATION_EXAMPLES.md)
- [Issuer Metadata](issuer-metadata.md)
- [Quick Start Guide](quick_start.md)

### External References
- [W3C DID Specification](https://www.w3.org/TR/did-core/)
- [W3C Verifiable Credentials](https://www.w3.org/TR/vc-data-model/)
- [Eclipse Dataspace Components](https://github.com/eclipse-edc/Connector)

## Troubleshooting

### Common Issues During Implementation

**Issue**: Maven dependency resolution fails  
**Solution**: Ensure `dcp-common` is built and installed first: `cd dcp-common && mvn clean install`

**Issue**: Docker build fails  
**Solution**: Verify JAR is built: `mvn clean package` before `docker-compose build`

**Issue**: MongoDB connection fails  
**Solution**: Check MongoDB is running and port is correct in properties

**Issue**: DID document returns 404  
**Solution**: Verify `DidDocumentController` is properly registered and Spring Boot is scanning the package

**Issue**: Key rotation fails  
**Solution**: Ensure keystore path is writable and not in classpath for production

## Getting Help

If you encounter issues or have questions:

1. **Check this documentation** - Most questions are answered here
2. **Review existing DCP docs** - See "Related Documentation" above
3. **Check logs** - Enable DEBUG logging: `logging.level.it.eng.dcp.issuer=DEBUG`
4. **Test in isolation** - Use unit tests to isolate problems

## Glossary

- **DCP**: Decentralized Claims Protocol
- **DID**: Decentralized Identifier
- **VC**: Verifiable Credential
- **VP**: Verifiable Presentation
- **JWK**: JSON Web Key
- **JWT**: JSON Web Token
- **P12/PKCS12**: Public Key Cryptography Standards #12 (keystore format)
- **EC**: Elliptic Curve (cryptography)
- **MongoDB**: NoSQL database used for persistence
- **Spring Boot**: Java application framework
- **Maven**: Build and dependency management tool
- **Docker**: Containerization platform

## Timeline Summary

| Phase | Duration | Cumulative |
|-------|----------|------------|
| 1. Common Library | 2 days | 2 days |
| 2. Module Setup | 1 day | 3 days |
| 3. Move Code | 3 days | 6 days |
| 4. Separate DIDs | 2 days | 8 days |
| 5. Configuration | 1 day | 9 days |
| 6. Build | 1 day | 10 days |
| 7. Docker | 2 days | 12 days |
| 8. Key Rotation | 2 days | 14 days |
| 9. Testing | 3 days | 17 days |
| 10. Documentation | 2 days | 19 days |
| 11. Cleanup | 2 days | 21 days |

**Estimated Total**: 21 working days (~4 weeks)

## Success Metrics

- ✅ All modules build successfully
- ✅ Docker images created without errors
- ✅ All tests pass (unit, integration, docker)
- ✅ No regression in existing functionality
- ✅ Separate DIDs functioning correctly
- ✅ Key rotation working (automatic and manual)
- ✅ Documentation complete and accurate
- ✅ Code coverage > 80% for new code

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-12-18 | AI Assistant | Initial creation |

---

**Last Updated**: December 18, 2025  
**Status**: Complete - Ready for Implementation

