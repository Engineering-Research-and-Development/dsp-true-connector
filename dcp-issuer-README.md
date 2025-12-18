# DCP Issuer Module

> **Status**: 🚧 Planning Phase - Module not yet implemented  
> **See**: [Implementation Plan](doc/ISSUER_MODULE_SPLIT_PLAN.md) for detailed roadmap

## Overview

The DCP Issuer is a standalone Spring Boot application that provides Verifiable Credentials issuance capabilities for dataspaces. It enables trusted authorities to issue cryptographically signed credentials to participants.

This module is being extracted from the monolithic `dcp` module to:
- ✅ Enable independent deployment and scaling
- ✅ Separate issuer and holder identities (DIDs)
- ✅ Enhance security through isolated keystores
- ✅ Improve maintainability and testing

## Architecture

```
┌─────────────────────────────────────────┐
│         DCP Issuer Service              │
│         (Port 8084)                     │
├─────────────────────────────────────────┤
│                                         │
│  DID: did:web:localhost%3A8084:issuer   │
│                                         │
│  Endpoints:                             │
│    ├─ /.well-known/did.json            │
│    ├─ /issuer/metadata                 │
│    ├─ /issuer/credentials              │
│    └─ /issuer/admin/*                  │
│                                         │
│  Features:                              │
│    ├─ Credential issuance               │
│    ├─ Request approval workflow         │
│    ├─ Automated key rotation            │
│    └─ Issuer metadata management        │
│                                         │
└─────────────────────────────────────────┘
            │
            ▼
    ┌───────────────┐
    │  MongoDB      │
    │  issuer_db    │
    │  (Port 27018) │
    └───────────────┘
```

## Quick Start (Post-Implementation)

### Prerequisites

- Java 17+
- Maven 3.6+
- MongoDB 7.0+
- Docker (optional)

### Build and Run

```bash
# Build the project
cd dcp-issuer
mvn clean package

# Run standalone
java -jar target/dcp-issuer.jar

# OR run with Docker
docker-compose up -d
```

### Verify Installation

```bash
# Check DID document
curl http://localhost:8084/.well-known/did.json

# Check health
curl http://localhost:8084/actuator/health

# Get issuer metadata (requires auth token)
curl -H "Authorization: Bearer <token>" \
     http://localhost:8084/issuer/metadata
```

## Configuration

### Minimal Configuration

```properties
# Issuer identity
issuer.did=did:web:localhost%3A8084:issuer
issuer.base-url=http://localhost:8084

# Keystore
issuer.keystore.path=classpath:eckey-issuer.p12
issuer.keystore.password=password
issuer.keystore.rotation-days=90

# MongoDB
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=issuer_db
```

### Credential Configuration

```properties
# Define supported credentials
issuer.credentials.supported[0].id=MembershipCredential
issuer.credentials.supported[0].credential-type=MembershipCredential
issuer.credentials.supported[0].profile=VC11_SL2021_JWT
issuer.credentials.supported[0].format=jwt_vc
```

See [Configuration Guide](doc/CONFIGURATION.md) for complete options.

## API Reference

### Public Endpoints

#### Get Issuer DID Document
```http
GET /.well-known/did.json
```

Returns the issuer's DID document with public keys and service endpoints.

**Response**: `200 OK`
```json
{
  "@context": "https://www.w3.org/ns/did/v1",
  "id": "did:web:localhost%3A8084:issuer",
  "service": [{
    "id": "IssuerService",
    "type": "IssuerService",
    "serviceEndpoint": "http://localhost:8084/issuer"
  }],
  "verificationMethod": [...]
}
```

### Protected Endpoints (Require Authentication)

#### Get Issuer Metadata
```http
GET /issuer/metadata
Authorization: Bearer <self-issued-id-token>
```

Returns metadata about credentials supported by this issuer.

**Response**: `200 OK`
```json
{
  "issuer": "did:web:localhost%3A8084:issuer",
  "credentialsSupported": [
    {
      "id": "MembershipCredential",
      "type": "CredentialObject",
      "credentialType": "MembershipCredential",
      "profile": "VC11_SL2021_JWT",
      "format": "jwt_vc"
    }
  ]
}
```

#### Request Credentials
```http
POST /issuer/credentials
Authorization: Bearer <self-issued-id-token>
Content-Type: application/json

{
  "holderPid": "did:web:holder.example.com",
  "credentials": [
    {"id": "MembershipCredential"}
  ]
}
```

**Response**: `201 Created`
```
Location: /issuer/requests/abc123
```

#### Check Request Status
```http
GET /issuer/requests/{requestId}
```

**Response**: `200 OK`
```json
{
  "issuerPid": "abc123",
  "holderPid": "did:web:holder.example.com",
  "status": "PENDING",
  "createdAt": "2025-12-18T10:00:00Z"
}
```

### Admin Endpoints

#### Manual Key Rotation
```http
POST /issuer/admin/rotate-key
```

Manually triggers key rotation outside of the automated schedule.

**Response**: `200 OK`
```json
{
  "message": "Key rotated successfully",
  "newKeyAlias": "issuer-20251218120000-abc123",
  "timestamp": 1702900800000
}
```

## Key Management

### Automated Key Rotation

The issuer automatically rotates its signing keys based on configured intervals:

- **Default rotation period**: 90 days
- **Schedule**: Daily at 2:00 AM (server time)
- **Process**:
  1. Check active key age
  2. If age > rotation period, generate new key
  3. Archive old key (kept for verification)
  4. Update DID document with new key

### Manual Rotation

For emergency key rotation:

```bash
curl -X POST http://localhost:8084/issuer/admin/rotate-key
```

⚠️ **Note**: Protect this endpoint with proper authentication in production!

### Key History

All previous keys are archived in MongoDB and the keystore, allowing verification of credentials issued with older keys.

## Credential Issuance Flow

```
┌──────────┐                    ┌──────────┐                    ┌──────────┐
│  Holder  │                    │  Issuer  │                    │  Admin   │
└────┬─────┘                    └────┬─────┘                    └────┬─────┘
     │                               │                               │
     │ 1. Request Credential         │                               │
     ├──────────────────────────────►│                               │
     │   POST /issuer/credentials    │                               │
     │                               │                               │
     │ 2. Request Created            │                               │
     │◄──────────────────────────────┤                               │
     │   201 Created                 │                               │
     │   Location: /requests/123     │                               │
     │                               │                               │
     │                               │ 3. Review Request             │
     │                               │◄──────────────────────────────┤
     │                               │                               │
     │                               │ 4. Approve Request            │
     │                               │◄──────────────────────────────┤
     │                               │   POST /requests/123/approve  │
     │                               │                               │
     │ 5. Credential Delivered       │                               │
     │◄──────────────────────────────┤                               │
     │   POST /dcp/credentials       │                               │
     │   (to holder's endpoint)      │                               │
     │                               │                               │
```

## Security Considerations

### Separate Identity
- Issuer has its own DID: `did:web:localhost%3A8084:issuer`
- Separate from holder DID: `did:web:localhost%3A8083:holder`
- No shared cryptographic material

### Keystore Isolation
- Dedicated keystore: `eckey-issuer.p12`
- Not shared with holder or other services
- Regular rotation enforced

### Database Isolation
- Separate MongoDB database: `issuer_db`
- No cross-database queries
- Independent backup and recovery

### Authentication
- All credential requests require valid Self-Issued ID Token
- Token validation includes signature verification
- Clock skew tolerance configurable (default: 120 seconds)

## Monitoring and Operations

### Health Checks

```bash
# Application health
curl http://localhost:8084/actuator/health

# With details (requires authorization)
curl http://localhost:8084/actuator/health?details=true
```

### Metrics

```bash
# Prometheus metrics
curl http://localhost:8084/actuator/prometheus

# Application metrics
curl http://localhost:8084/actuator/metrics
```

### Logs

Default log location: `logs/issuer.log`

Enable debug logging:
```properties
logging.level.it.eng.dcp.issuer=DEBUG
```

## Docker Deployment

### Basic Deployment

```bash
# Build and start
docker-compose up -d

# View logs
docker-compose logs -f dcp-issuer

# Stop
docker-compose down
```

### Production Deployment

```bash
# Use production compose file
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ISSUER_DID` | `did:web:localhost%3A8084:issuer` | Issuer's DID |
| `ISSUER_BASE_URL` | `http://localhost:8084` | Base URL |
| `SPRING_DATA_MONGODB_HOST` | `localhost` | MongoDB host |
| `SPRING_DATA_MONGODB_PORT` | `27017` | MongoDB port |
| `ISSUER_KEYSTORE_ROTATION_DAYS` | `90` | Key rotation period |

## Development

### Project Structure

```
dcp-issuer/
├── src/
│   ├── main/
│   │   ├── java/it/eng/dcp/issuer/
│   │   │   ├── IssuerApplication.java
│   │   │   ├── rest/
│   │   │   │   ├── IssuerController.java
│   │   │   │   ├── IssuerDidDocumentController.java
│   │   │   │   └── IssuerAdminController.java
│   │   │   ├── service/
│   │   │   │   ├── IssuerService.java
│   │   │   │   ├── IssuerDidDocumentService.java
│   │   │   │   ├── IssuerKeyService.java
│   │   │   │   ├── IssuerKeyRotationService.java
│   │   │   │   └── ...
│   │   │   ├── config/
│   │   │   │   ├── IssuerProperties.java
│   │   │   │   ├── IssuerAutoConfiguration.java
│   │   │   │   └── IssuerMongoConfig.java
│   │   │   ├── repository/
│   │   │   └── model/
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── eckey-issuer.p12
│   │       └── META-INF/
│   └── test/
├── doc/
│   ├── ISSUER_MODULE_SPLIT_PLAN.md
│   ├── ISSUER_MODULE_TECHNICAL_SPEC.md
│   └── ...
├── pom.xml
├── Dockerfile
└── docker-compose.yml
```

### Running Tests

```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# With coverage
mvn clean verify jacoco:report
```

### Building

```bash
# Build JAR
mvn clean package

# Build without tests
mvn clean package -DskipTests

# Build Docker image
docker-compose build
```

## Migration from Monolithic DCP

If you're upgrading from the monolithic `dcp` module:

1. **Backup data**: Export existing credential requests and key metadata
2. **Deploy issuer**: Start new issuer service on port 8084
3. **Migrate data**: Import data to new `issuer_db` database
4. **Update holder**: Point holder to new issuer endpoint
5. **Verify**: Test credential issuance flow
6. **Decommission**: Remove issuer code from old `dcp` module

See [Migration Guide](doc/MIGRATION.md) for detailed steps.

## Troubleshooting

### Common Issues

**Issue**: DID document returns 404  
**Solution**: Verify application started successfully and port 8084 is accessible

**Issue**: MongoDB connection failed  
**Solution**: Check MongoDB is running and connection properties are correct

**Issue**: Key rotation fails  
**Solution**: Ensure keystore path is writable (not in classpath for production)

**Issue**: Credential request rejected  
**Solution**: Verify the requested credential type is configured in metadata

See [Troubleshooting Guide](doc/TROUBLESHOOTING.md) for more issues.

## Documentation

- 📖 [Implementation Plan](doc/ISSUER_MODULE_SPLIT_PLAN.md) - Complete implementation roadmap
- 🏗️ [Technical Specification](doc/ISSUER_MODULE_TECHNICAL_SPEC.md) - Detailed technical specs
- 📊 [Architecture Diagrams](doc/ISSUER_MODULE_ARCHITECTURE_DIAGRAMS.md) - Visual architecture
- 📋 [Documentation Index](doc/ISSUER_MODULE_INDEX.md) - Complete documentation guide
- ⚙️ [Configuration Guide](doc/CONFIGURATION.md) - All configuration options
- 🚀 [Deployment Guide](doc/DEPLOYMENT.md) - Production deployment
- 🔑 [Key Rotation Guide](doc/KEY_ROTATION.md) - Key management details

## Contributing

When contributing to this module:

1. Follow existing code style and patterns
2. Add unit tests for new functionality (target: 80% coverage)
3. Update documentation for API changes
4. Test Docker build before submitting
5. Ensure all tests pass: `mvn clean verify`

## License

This module is part of the TRUE Connector project.

## Support

For issues and questions:
- Review documentation in `doc/` directory
- Check existing issues in project tracker
- Consult with development team

---

**Module Status**: 🚧 Planning - Implementation pending  
**Target Completion**: ~21 working days from start  
**Last Updated**: December 18, 2025

