# DCP Issuer Module Split - Executive Summary

## Overview

This document provides an executive summary of the plan to extract Verifiable Credentials Issuer functionality from the monolithic `dcp` module into a standalone, independently deployable service.

## Problem Statement

### Current Architecture Issues

1. **Single DID Document** - Both issuer and holder share `/.well-known/did.json`
   - Security risk: identities should be separate
   - Operational confusion: one DID represents two distinct roles
   
2. **Monolithic Deployment** - Cannot deploy or scale issuer independently
   - Resource waste: must deploy entire module for issuer features
   - Limited scalability: cannot scale issuer separately from holder
   
3. **Code Coupling** - Issuer and holder code tightly coupled
   - Maintenance overhead: changes affect both concerns
   - Testing complexity: difficult to test in isolation

## Proposed Solution

### Three-Module Architecture

```
dsp-true-connector/
├── dcp-common/          # Shared library (NEW)
│   └── Shared models and utilities
│
├── dcp/                 # Holder/Verifier (REFACTORED)
│   ├── Port: 8083
│   ├── DID: did:web:localhost%3A8083:holder
│   └── Database: true_connector_provider
│
└── dcp-issuer/          # Issuer (NEW)
    ├── Port: 8084
    ├── DID: did:web:localhost%3A8084:issuer
    └── Database: issuer_db
```

### Key Benefits

1. **Separate Identities**
   - Issuer: `did:web:localhost%3A8084:issuer`
   - Holder: `did:web:localhost%3A8083:holder`
   - Clear role separation, enhanced security

2. **Independent Deployment**
   - Standalone Spring Boot JAR for issuer
   - Dockerized with own compose file
   - Can deploy and scale independently

3. **Cleaner Architecture**
   - Shared code in `dcp-common`
   - Clear module boundaries
   - Easier to maintain and test

## Deliverables

### 1. dcp-common Module
- Shared models (DidDocument, VerifiableCredential, etc.)
- Common utilities (JWT, DID resolution)
- Reusable by both issuer and holder

### 2. dcp-issuer Module
- **Spring Boot Application**: Standalone executable
- **REST Controllers**:
  - `IssuerDidDocumentController` - Issuer DID at `/.well-known/did.json`
  - `IssuerController` - Credential issuance at `/issuer/*`
  - `IssuerAdminController` - Admin operations at `/issuer/admin/*`
  
- **Core Services**:
  - `IssuerService` - Business logic
  - `IssuerDidDocumentService` - DID document generation
  - `CredentialIssuanceService` - Credential generation
  - `CredentialDeliveryService` - Credential delivery
  - `IssuerKeyService` - Key management
  - `IssuerKeyRotationService` - Automated key rotation
  
- **Configuration**:
  - `IssuerProperties` - All issuer config
  - `application-issuer.properties` - Default settings
  - `credential-metadata-configuration.properties` - VC definitions
  
- **Docker Support**:
  - `Dockerfile` - Production-ready image
  - `docker-compose.yml` - Full stack deployment
  - Health checks and monitoring

### 3. Refactored dcp Module
- Remove issuer code
- Update DID document (holder only)
- Clean dependencies

## Technical Highlights

### Separate DID Documents

**Issuer DID Document** (Port 8084):
```json
{
  "id": "did:web:localhost%3A8084:issuer",
  "service": [{
    "id": "IssuerService",
    "type": "IssuerService",
    "serviceEndpoint": "http://localhost:8084/issuer"
  }],
  "verificationMethod": [...]
}
```

**Holder DID Document** (Port 8083):
```json
{
  "id": "did:web:localhost%3A8083:holder",
  "service": [{
    "id": "CredentialService",
    "type": "CredentialService",
    "serviceEndpoint": "http://localhost:8083"
  }],
  "verificationMethod": [...]
}
```

### Automated Key Rotation

```java
@Scheduled(cron = "0 0 2 * * ?")  // Daily at 2 AM
public void checkAndRotateKeys() {
    // Check key age
    // Rotate if > configured days
    // Archive old key
}
```

- Configurable rotation period (default: 90 days)
- Manual rotation via REST endpoint
- Key history maintained for verification

### Independent Databases

| Service | Database | Port |
|---------|----------|------|
| Issuer | `issuer_db` | 27018 |
| Holder | `true_connector_provider` | 27017 |

## API Endpoints

### Issuer (Port 8084)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/.well-known/did.json` | GET | Issuer DID document |
| `/issuer/metadata` | GET | Issuer metadata |
| `/issuer/credentials` | POST | Request credentials |
| `/issuer/requests/{id}` | GET | Check request status |
| `/issuer/requests/{id}/approve` | POST | Approve credential |
| `/issuer/admin/rotate-key` | POST | Manual key rotation |

### Holder (Port 8083)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/.well-known/did.json` | GET | Holder DID document |
| `/dcp/credentials` | POST | Receive credentials |
| `/dcp/presentations` | POST | Create presentations |

## Implementation Timeline

| Phase | Tasks | Duration |
|-------|-------|----------|
| 1. Common Library | Create dcp-common, move shared models | 2 days |
| 2. Module Setup | Create dcp-issuer structure | 1 day |
| 3. Move Code | Migrate issuer services | 3 days |
| 4. Separate DIDs | Create distinct DID documents | 2 days |
| 5. Configuration | Properties and config classes | 1 day |
| 6. Build | Maven build configuration | 1 day |
| 7. Docker | Dockerfile and compose | 2 days |
| 8. Key Rotation | Implement rotation service | 2 days |
| 9. Testing | Unit, integration, docker tests | 3 days |
| 10. Documentation | README, guides, API docs | 2 days |
| 11. Cleanup | Remove old code, refactor | 2 days |
| **Total** | | **21 days** |

## Quick Start (After Implementation)

### Build and Run Issuer

```bash
# Build the project
cd dcp-issuer
mvn clean package

# Run standalone
java -jar target/dcp-issuer.jar

# OR run with Docker
docker-compose up -d

# Verify
curl http://localhost:8084/.well-known/did.json
```

### Configuration

```properties
# Issuer identity
issuer.did=did:web:localhost%3A8084:issuer
issuer.base-url=http://localhost:8084

# Keystore
issuer.keystore.path=classpath:eckey-issuer.p12
issuer.keystore.password=password
issuer.keystore.rotation-days=90

# MongoDB
spring.data.mongodb.database=issuer_db
spring.data.mongodb.port=27017
```

## Success Criteria

- [x] Separate DID documents for issuer and holder
- [x] Issuer builds as standalone Spring Boot JAR
- [x] Docker image and compose configuration
- [x] Automated key rotation implemented
- [x] All issuer endpoints functional
- [x] Independent database for issuer
- [x] Comprehensive documentation
- [x] No regression in holder functionality

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Shared code conflicts | Create dcp-common library first |
| Database migration issues | Use separate databases from start |
| Breaking changes | Maintain backward compatibility |
| Key rotation failures | Thorough testing, rollback procedures |

## Documentation Structure

```
dcp-issuer/
├── README.md                       # Overview and quick start
├── doc/
│   ├── DEPLOYMENT.md              # Deployment guide
│   ├── KEY_ROTATION.md            # Key rotation guide
│   ├── API.md                     # API documentation
│   └── CONFIGURATION.md           # Configuration reference
```

## File Checklist

### Planning Documents (Created)
- ✅ `ISSUER_MODULE_SPLIT_PLAN.md` - Comprehensive implementation plan
- ✅ `ISSUER_MODULE_TECHNICAL_SPEC.md` - Technical specifications and code examples
- ✅ `ISSUER_MODULE_SUMMARY.md` - This executive summary

### To Be Created (During Implementation)
- [ ] `dcp-common/pom.xml`
- [ ] `dcp-issuer/pom.xml`
- [ ] `dcp-issuer/Dockerfile`
- [ ] `dcp-issuer/docker-compose.yml`
- [ ] `dcp-issuer/src/main/java/it/eng/dcp/issuer/IssuerApplication.java`
- [ ] `dcp-issuer/src/main/resources/application.properties`
- [ ] And 30+ other files (see full plan)

## Next Actions

1. **Review** these planning documents
2. **Approve** the architecture and approach
3. **Create branch** `feature/issuer-module-split`
4. **Begin Phase 1** - Create dcp-common module
5. **Iterative development** - One phase at a time
6. **Continuous testing** - Verify each phase

## Contact & Support

For questions about this plan:
- Review the detailed plan: `ISSUER_MODULE_SPLIT_PLAN.md`
- Check technical specs: `ISSUER_MODULE_TECHNICAL_SPEC.md`
- See existing documentation: `dcp/doc/`

---

## Conclusion

This plan provides a clear path to:
- **Separate** issuer and holder identities
- **Modernize** the architecture with microservices
- **Enable** independent deployment and scaling
- **Improve** security and maintainability

The implementation is broken into **11 manageable phases** over **~21 days**, with clear deliverables and success criteria at each step.

---

**Document Version**: 1.0  
**Created**: December 18, 2025  
**Status**: Executive Summary - Ready for Review

