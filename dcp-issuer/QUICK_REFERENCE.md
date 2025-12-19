# DCP Issuer - Quick Reference Card

## 🚀 Quick Start

```bash
# Build
mvn clean package

# Run
java -jar target/dcp-issuer.jar

# Docker
docker-compose up -d
```

## 📋 Configuration (application.properties)

```properties
# Issuer Identity
issuer.did=did:web:localhost%3A8084:issuer
issuer.base-url=http://localhost:8084

# Server
server.port=8084

# MongoDB
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=issuer_db

# Keystore
issuer.keystore.path=classpath:eckey-issuer.p12
issuer.keystore.password=password
issuer.keystore.alias=issuer
```

## 🔌 API Endpoints

### Get Metadata
```bash
curl -H "Authorization: Bearer TOKEN" \
  http://localhost:8084/issuer/metadata
```

### Create Request
```bash
curl -X POST \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"holderPid":"did:web:holder","credentials":[{"id":"MembershipCredential"}]}' \
  http://localhost:8084/issuer/requests
```

### Approve Request
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"customClaims":{"role":"admin"}}' \
  http://localhost:8084/issuer/requests/REQUEST_ID/approve
```

### Reject Request
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"reason":"Invalid"}' \
  http://localhost:8084/issuer/requests/REQUEST_ID/reject
```

### Get DID Document
```bash
curl http://localhost:8084/.well-known/did.json
```

## 🛠️ Useful Commands

### Build
```bash
# Windows
build.cmd

# Linux/Mac
./build.sh

# Maven
mvn clean package
```

### Run
```bash
# Default
java -jar target/dcp-issuer.jar

# Custom config
java -jar target/dcp-issuer.jar --spring.config.location=/path/to/config/
```

### Docker
```bash
# Build image
docker build -t dcp-issuer:latest .

# Run container
docker run -d -p 8084:8084 --name dcp-issuer dcp-issuer:latest

# View logs
docker logs -f dcp-issuer

# Stop
docker stop dcp-issuer
```

### Docker Compose
```bash
# Start
docker-compose up -d

# Stop
docker-compose down

# View logs
docker-compose logs -f dcp-issuer

# Restart
docker-compose restart dcp-issuer
```

## 🔐 Generate Keystore

```bash
keytool -genkeypair -alias issuer \
  -keyalg EC -keysize 256 -sigalg SHA256withECDSA \
  -validity 365 -keystore eckey-issuer.p12 \
  -storetype PKCS12 -storepass password \
  -dname "CN=Issuer,OU=DCP,O=TrueConnector,C=IT"
```

## 📊 Health Check

```bash
curl http://localhost:8084/actuator/health
```

## 🐛 Troubleshooting

### Check service status
```bash
# Check if running
curl http://localhost:8084/actuator/health

# Check logs
tail -f logs/application.log

# Docker logs
docker logs dcp-issuer
```

### Common Issues

**Service won't start**
- ✓ Check MongoDB is running
- ✓ Verify keystore exists and password is correct
- ✓ Check port 8084 is available

**Can't access DID document**
- ✓ Service is running
- ✓ DID configuration is correct
- ✓ No firewall blocking access

**Credentials not issued**
- ✓ Holder DID is valid
- ✓ Holder service is accessible
- ✓ Check delivery logs

## 📁 Key Files

| File | Purpose |
|------|---------|
| `pom.xml` | Maven configuration |
| `src/main/resources/application.properties` | Configuration |
| `src/main/java/.../IssuerApplication.java` | Main class |
| `Dockerfile` | Docker image |
| `docker-compose.yml` | Multi-container setup |

## 🔗 Important URLs

| URL | Description |
|-----|-------------|
| http://localhost:8084 | Base URL |
| http://localhost:8084/issuer/metadata | Issuer metadata |
| http://localhost:8084/.well-known/did.json | DID document |
| http://localhost:8084/actuator/health | Health check |

## 📝 Supported Credential Types

Configure in `application.properties`:

```properties
issuer.credentials.supported[0].id=MembershipCredential
issuer.credentials.supported[0].credential-type=MembershipCredential
issuer.credentials.supported[0].profile=VC11_SL2021_JWT
issuer.credentials.supported[0].format=jwt_vc

issuer.credentials.supported[1].id=OrganizationCredential
issuer.credentials.supported[1].credential-type=OrganizationCredential
issuer.credentials.supported[1].profile=VC11_SL2021_JWT
```

## 🎯 Default Values

| Property | Default Value |
|----------|---------------|
| Server Port | 8084 |
| MongoDB Port | 27017 |
| Database | issuer_db |
| Clock Skew | 120 seconds |
| Key Rotation | 90 days |
| Profile | VC11_SL2021_JWT |

## 📚 Documentation

- `README.md` - Overview and quick start
- `DEPLOYMENT.md` - Deployment guide
- `IMPLEMENTATION.md` - Implementation details
- `IMPLEMENTATION_REPORT.md` - Complete report

---
**Version**: 1.0.0  
**Port**: 8084  
**Protocol**: HTTP (HTTPS in production)

