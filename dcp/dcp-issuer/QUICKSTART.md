# Quick Start Guide

Get the DCP Issuer up and running in minutes.

## Prerequisites

- **Java 17+** (OpenJDK or Oracle JDK)
- **Maven 3.6+**
- **MongoDB 7.0+**
- **Docker** (optional, for containerized deployment)

## Installation

### Option 1: Local Development Setup

#### Step 1: Generate EC Key Pair

For development purposes, generate an EC key pair:

```bash
keytool -genkeypair -alias dcp-issuer -keyalg EC -groupname secp256r1 \
  -sigalg SHA256withECDSA \
  -dname "CN=DCP Issuer,OU=Development,O=MyOrg,C=US" \
  -keystore eckey-issuer.p12 -storetype PKCS12 \
  -storepass password -keypass password
```

This creates `eckey-issuer.p12` in your current directory.

#### Step 2: Start MongoDB

```bash
# Using Docker
docker run -d --name dcp-issuer-mongo -p 27017:27017 mongo:7.0

# OR if you have MongoDB installed locally
mongod --dbpath /path/to/data
```

#### Step 3: Build the Application

```bash
cd dcp-issuer
mvn clean package
```

#### Step 4: Run the Application

```bash
java -jar target/dcp-issuer.jar
```

The service will start on port **8084**.

#### Step 5: Verify Installation

```bash
# Check DID document (public endpoint)
curl http://localhost:8084/.well-known/did.json

# Expected response: DID document with issuer's public keys
```

### Option 2: Docker Deployment

#### Step 1: Build and Run with Docker Compose

```bash
cd dcp-issuer
docker-compose up -d
```

This will:
- Build the DCP Issuer Docker image
- Start MongoDB container
- Start DCP Issuer container
- Configure networking between services

#### Step 2: Verify

```bash
# Check container status
docker-compose ps

# View logs
docker-compose logs -f dcp-issuer

# Test DID endpoint
curl http://localhost:8084/.well-known/did.json
```

### Option 3: Using Maven Spring Boot Plugin

For rapid development:

```bash
cd dcp-issuer
mvn spring-boot:run
```

## Basic Configuration

### Minimal Configuration

Create `application.properties` or configure via environment variables:

```properties
# Issuer Identity
issuer.did=did:web:localhost%3A8084:issuer
issuer.base-url=http://localhost:8084

# MongoDB
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=issuer_db

# Keystore
issuer.keystore.path=file:./eckey-issuer.p12
issuer.keystore.password=password
issuer.keystore.alias=dcp-issuer
issuer.keystore.rotation-days=90

# Supported Credentials
issuer.credentials.supported[0].id=MembershipCredential
issuer.credentials.supported[0].credential-type=MembershipCredential
issuer.credentials.supported[0].profile=VC20_BSSL_JWT
issuer.credentials.supported[0].format=jwt_vc
```

See [Configuration Guide](CONFIGURATION.md) for complete options.

## First Steps

### 1. Access the DID Document

```bash
curl http://localhost:8084/.well-known/did.json
```

You should see your issuer's DID document with public keys and service endpoints.

### 2. Check Health

```bash
curl http://localhost:8084/actuator/health
```

Expected response:
```json
{
  "status": "UP"
}
```

### 3. Get Issuer Metadata

You'll need a Bearer token for this. For testing, you can generate a self-issued ID token.

```bash
curl -H "Authorization: Bearer <your-token>" \
     http://localhost:8084/issuer/metadata
```

Expected response:
```json
{
  "issuer": "did:web:localhost%3A8084:issuer",
  "credentialsSupported": [
    {
      "id": "MembershipCredential",
      "type": "CredentialObject",
      "credentialType": "MembershipCredential",
      "profile": "VC20_BSSL_JWT",
      "format": "jwt_vc"
    }
  ]
}
```

## Testing the Credential Flow

### 1. Request a Credential

```bash
curl -X POST http://localhost:8084/issuer/credentials \
  -H "Authorization: Bearer <your-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "holderPid": "did:web:holder.example.com",
    "credentials": [
      {"id": "MembershipCredential"}
    ]
  }'
```

Expected response:
```
HTTP/1.1 201 Created
Location: /issuer/requests/abc123
```

### 2. Check Request Status

```bash
curl -H "Authorization: Bearer <your-token>" \
     http://localhost:8084/issuer/requests/abc123
```

### 3. Approve the Request (Admin)

```bash
curl -X POST http://localhost:8084/issuer/requests/abc123/approve \
  -H "Content-Type: application/json" \
  -d '{
    "customClaims": {
      "membershipNumber": "12345",
      "role": "admin"
    }
  }'
```

The credential will be automatically delivered to the holder's endpoint.

## Next Steps

- 📖 Read [Configuration Guide](CONFIGURATION.md) for production setup
- 🚀 See [Deployment Guide](DEPLOYMENT.md) for production deployment
- 📡 Check [API Reference](API.md) for complete endpoint documentation
- 🔌 Read [Integration Guide](INTEGRATION.md) to use in your dataspace

## Common Issues

### Port Already in Use

If port 8084 is already in use:

```properties
server.port=8085
issuer.base-url=http://localhost:8085
issuer.did=did:web:localhost%3A8085:issuer
```

### MongoDB Connection Failed

Ensure MongoDB is running:

```bash
# Check MongoDB status
mongosh --host localhost --port 27017

# If using Docker
docker ps | grep mongo
```

### Keystore Not Found

Ensure the keystore path is correct:

```properties
# For classpath resource
issuer.keystore.path=classpath:eckey-issuer.p12

# For file system
issuer.keystore.path=file:/absolute/path/to/eckey-issuer.p12
```

### DID Document Returns 404

Check that:
- Application started successfully
- Port 8084 is accessible
- No reverse proxy is blocking `/.well-known/` path

See [Troubleshooting Guide](TROUBLESHOOTING.md) for more solutions.

## Development Mode

For active development with auto-reload:

```bash
# Using Spring Boot DevTools
mvn spring-boot:run

# With debug port
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

## Clean Uninstall

```bash
# Stop the application
# Ctrl+C if running in foreground

# Stop Docker containers
docker-compose down

# Remove MongoDB data (optional)
docker volume rm dcp-issuer_mongodb-data

# Clean build artifacts
mvn clean
```

---

**Next**: [Configuration Guide](CONFIGURATION.md) | [API Reference](API.md) | [Deployment Guide](DEPLOYMENT.md)

