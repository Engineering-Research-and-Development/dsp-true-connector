# DCP Issuer - Deployment Guide

## Prerequisites

Before deploying the DCP Issuer service, ensure you have:

1. **Java 17+** installed
2. **Maven 3.6+** installed
3. **MongoDB 7.0+** running (or use Docker Compose)
4. **Docker** (optional, for containerized deployment)

## Quick Start

### Option 1: Run with Docker Compose (Recommended)

This is the easiest way to get started. It will start both the issuer service and MongoDB.

```bash
cd dcp-issuer
docker-compose up -d
```

The service will be available at: `http://localhost:8084`

### Option 2: Run Standalone

1. **Start MongoDB** (if not already running):
   ```bash
   mongod --dbpath /path/to/data
   ```

2. **Build the application**:
   ```bash
   cd dcp-issuer
   mvn clean package
   ```

3. **Run the application**:
   ```bash
   java -jar target/dcp-issuer.jar
   ```

## Detailed Deployment Steps

### Step 1: Generate Issuer Keystore

The issuer needs a cryptographic key for signing credentials. Generate one using:

```bash
keytool -genkeypair -alias issuer \
  -keyalg EC -keysize 256 -sigalg SHA256withECDSA \
  -validity 365 -keystore eckey-issuer.p12 \
  -storetype PKCS12 -storepass password \
  -dname "CN=Issuer, OU=DCP, O=TrueConnector, L=City, ST=State, C=IT"
```

Place the generated `eckey-issuer.p12` file in:
- `src/main/resources/` for standalone deployment
- Mount as volume for Docker deployment

### Step 2: Configure Application

Edit `src/main/resources/application.properties`:

```properties
# Update with your actual DID
issuer.did=did:web:yourdomain.com:issuer

# Update with your actual base URL
issuer.base-url=https://yourdomain.com

# Configure MongoDB
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=issuer_db

# Configure keystore
issuer.keystore.path=classpath:eckey-issuer.p12
issuer.keystore.password=your-secure-password
issuer.keystore.alias=issuer

# Configure supported credentials
issuer.credentials.supported[0].id=MembershipCredential
issuer.credentials.supported[0].credential-type=MembershipCredential
issuer.credentials.supported[0].profile=VC11_SL2021_JWT
issuer.credentials.supported[0].format=jwt_vc
```

### Step 3: Configure MongoDB

For production, secure your MongoDB instance:

```javascript
// Connect to MongoDB
use admin

// Create admin user
db.createUser({
  user: "admin",
  pwd: "secure-password",
  roles: ["userAdminAnyDatabase", "dbAdminAnyDatabase"]
})

// Create issuer database user
use issuer_db
db.createUser({
  user: "issuer_user",
  pwd: "issuer-password",
  roles: [{ role: "readWrite", db: "issuer_db" }]
})
```

Update application.properties:
```properties
spring.data.mongodb.username=issuer_user
spring.data.mongodb.password=issuer-password
spring.data.mongodb.authentication-database=admin
```

### Step 4: Build Application

```bash
# Windows
build.cmd

# Linux/Mac
chmod +x build.sh
./build.sh
```

Or manually:
```bash
mvn clean package
```

### Step 5: Deploy

#### Deployment Option A: Standalone JAR

```bash
java -jar target/dcp-issuer.jar
```

With custom configuration:
```bash
java -jar target/dcp-issuer.jar \
  --issuer.did=did:web:yourdomain.com:issuer \
  --issuer.base-url=https://yourdomain.com \
  --spring.data.mongodb.host=mongodb-server
```

#### Deployment Option B: Docker

Build the image:
```bash
docker build -t dcp-issuer:1.0.0 .
```

Run the container:
```bash
docker run -d \
  --name dcp-issuer \
  -p 8084:8084 \
  -e ISSUER_DID=did:web:yourdomain.com:issuer \
  -e ISSUER_BASE_URL=https://yourdomain.com \
  -e SPRING_DATA_MONGODB_HOST=mongodb \
  -v /path/to/eckey-issuer.p12:/app/eckey-issuer.p12 \
  dcp-issuer:1.0.0
```

#### Deployment Option C: Docker Compose

Update `docker-compose.yml` with your configuration, then:

```bash
docker-compose up -d
```

View logs:
```bash
docker-compose logs -f dcp-issuer
```

Stop services:
```bash
docker-compose down
```

### Step 6: Verify Deployment

1. **Check Health**:
   ```bash
   curl http://localhost:8084/actuator/health
   ```

2. **Get DID Document**:
   ```bash
   curl http://localhost:8084/.well-known/did.json
   ```

3. **Test Metadata Endpoint** (requires token):
   ```bash
   curl -H "Authorization: Bearer YOUR_TOKEN" \
     http://localhost:8084/issuer/metadata
   ```

## Production Considerations

### Security

1. **Enable HTTPS**: Update `application.properties`:
   ```properties
   server.ssl.enabled=true
   server.ssl.key-store=classpath:keystore.p12
   server.ssl.key-store-password=password
   server.ssl.key-store-type=PKCS12
   ```

2. **Secure MongoDB**: 
   - Enable authentication
   - Use TLS/SSL connections
   - Restrict network access

3. **Rotate Keys**: Configure automatic key rotation:
   ```properties
   issuer.keystore.rotation-days=90
   ```

4. **Environment Variables**: Store secrets in environment variables, not properties files:
   ```bash
   export ISSUER_KEYSTORE_PASSWORD=secure-password
   export SPRING_DATA_MONGODB_PASSWORD=mongo-password
   ```

### Monitoring

1. **Enable Actuator Endpoints**:
   ```properties
   management.endpoints.web.exposure.include=health,info,metrics
   management.endpoint.health.show-details=always
   ```

2. **Configure Logging**:
   ```properties
   logging.level.it.eng.dcp.issuer=INFO
   logging.file.name=/var/log/dcp-issuer/application.log
   logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %msg%n
   ```

3. **Metrics**: Integrate with monitoring tools (Prometheus, Grafana)

### Scalability

1. **Horizontal Scaling**: Run multiple instances behind a load balancer
2. **MongoDB Replica Set**: Use MongoDB replica sets for high availability
3. **Caching**: Implement caching for metadata and DID documents

### Backup

1. **MongoDB Backup**:
   ```bash
   mongodump --db issuer_db --out /backup/$(date +%Y%m%d)
   ```

2. **Keystore Backup**: Securely backup `eckey-issuer.p12`

## Troubleshooting

### Service Won't Start

**Problem**: Application fails to start

**Solutions**:
1. Check MongoDB is running: `mongo --eval "db.adminCommand('ping')"`
2. Verify keystore exists and password is correct
3. Check port 8084 is available: `netstat -an | grep 8084`
4. Review logs: `tail -f logs/application.log`

### Can't Access DID Document

**Problem**: `/.well-known/did.json` returns 404

**Solutions**:
1. Verify service is running: `curl http://localhost:8084/actuator/health`
2. Check DID configuration in application.properties
3. Ensure DidDocumentService is properly initialized

### Credentials Not Issued

**Problem**: Approval succeeds but credentials not delivered

**Solutions**:
1. Verify holder DID is valid and resolvable
2. Check holder service is accessible
3. Review credential delivery logs
4. Verify network connectivity to holder service

### MongoDB Connection Failed

**Problem**: Can't connect to MongoDB

**Solutions**:
1. Verify MongoDB is running
2. Check connection string in application.properties
3. Verify authentication credentials
4. Check network/firewall settings

## Maintenance

### Updating the Service

1. **Stop the service**:
   ```bash
   # Docker Compose
   docker-compose down
   
   # Standalone
   kill $(cat application.pid)
   ```

2. **Backup database and configuration**

3. **Deploy new version**:
   ```bash
   mvn clean package
   # or
   docker-compose up -d --build
   ```

### Log Rotation

Configure log rotation in `application.properties`:
```properties
logging.file.name=/var/log/dcp-issuer/application.log
logging.file.max-size=10MB
logging.file.max-history=30
```

## Support

For issues or questions:
- Check logs in `/var/log/dcp-issuer/` or `logs/`
- Review MongoDB logs
- Check network connectivity
- Verify configuration files

## Next Steps

After deployment:
1. Configure supported credential types
2. Set up monitoring and alerting
3. Implement backup procedures
4. Test integration with holder services
5. Configure key rotation schedule

