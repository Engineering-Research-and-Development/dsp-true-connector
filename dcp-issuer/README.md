# DCP Issuer Service

Standalone Verifiable Credentials Issuer Service for the TRUE Connector.

## Overview

The DCP Issuer service is a standalone Spring Boot application that handles:
- Verifiable Credential issuance
- DID document management for the issuer
- Key rotation and management
- Credential request processing
- Integration with holder services

## Features

- **Credential Issuance**: Issues verifiable credentials based on configured types
- **DID Document**: Exposes issuer DID document at `/.well-known/did.json`
- **Key Management**: Handles cryptographic keys for signing credentials
- **Request Management**: Tracks credential requests and their status
- **Metadata Endpoint**: Provides issuer metadata with supported credential types

## Generate EC Key Pair for development purposes

```cmd
keytool -genkeypair -alias issuer-ec -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA -dname "CN=DSP TRUEConnector,OU=Engineering,O=R&D,C=IT" -keystore eckey-issuer.p12 -storetype PKCS12 -storepass password -keypass password

```

## Building

Build the module using Maven:

```bash
mvn clean package
```

This will create an executable JAR: `target/dcp-issuer.jar`

## Running

### Standalone

Run the service directly:

```bash
java -jar target/dcp-issuer.jar
```

### Docker

Build and run with Docker:

```bash
docker build -t dcp-issuer:latest .
docker run -p 8084:8084 dcp-issuer:latest
```

### Docker Compose

Start the service with MongoDB:

```bash
docker-compose up -d
```

## Configuration

The service is configured via `application.properties`. Key properties:

### Issuer Identity
```properties
issuer.did=did:web:localhost%3A8084:issuer
issuer.base-url=http://localhost:8084
```

### MongoDB
```properties
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=issuer_db
```

### Keystore
```properties
issuer.keystore.path=classpath:eckey-issuer.p12
issuer.keystore.password=password
issuer.keystore.alias=issuer
issuer.keystore.rotation-days=90
```

### Credentials
```properties
issuer.credentials.supported[0].id=MembershipCredential
issuer.credentials.supported[0].credential-type=MembershipCredential
issuer.credentials.supported[0].profile=VC11_SL2021_JWT
issuer.credentials.supported[0].format=jwt_vc
```

## API Endpoints

### Get Issuer Metadata
```
GET /issuer/metadata
Authorization: Bearer {token}
```

Returns issuer metadata including supported credential types.

### Create Credential Request
```
POST /issuer/requests
Authorization: Bearer {token}
Content-Type: application/json

{
  "holderPid": "did:web:holder",
  "credentials": [
    {"id": "MembershipCredential"}
  ]
}
```

### Approve Request
```
POST /issuer/requests/{requestId}/approve
Content-Type: application/json

{
  "customClaims": {
    "country_code": "IT",
    "role": "admin"
  }
}
```

### Reject Request
```
POST /issuer/requests/{requestId}/reject
Content-Type: application/json

{
  "reason": "Invalid request"
}
```

### Get DID Document
```
GET /.well-known/did.json
```

Returns the issuer's DID document.

## Key Rotation

The service supports automatic key rotation. Configure the rotation period:

```properties
issuer.keystore.rotation-days=90
```

## Security

- All API endpoints require Bearer token authentication
- Credentials are signed with ES256 algorithm
- DID documents are publicly accessible
- MongoDB should be secured in production

## Monitoring

Health check endpoint:
```
GET /actuator/health
```

## Development

### Prerequisites
- Java 17+
- Maven 3.6+
- MongoDB 7.0+

### Running Tests
```bash
mvn test
```

### Running Locally
```bash
mvn spring-boot:run
```

## Integration

The issuer service integrates with:
- **Holder Services**: Delivers credentials via DCP Storage API
- **DID Resolver**: Resolves holder DIDs to find credential endpoints
- **dcp-common**: Shared models and utilities

## Troubleshooting

### Service won't start
- Check MongoDB is running and accessible
- Verify keystore file exists and password is correct
- Check port 8084 is not in use

### Credentials not issued
- Verify credential types are configured in `issuer.credentials.supported`
- Check holder DID is resolvable
- Review logs for delivery errors

### DID document not accessible
- Ensure `/.well-known/did.json` endpoint is not blocked
- Verify issuer DID configuration matches deployment URL

## License

Copyright (c) Engineering Ingegneria Informatica S.p.A.

