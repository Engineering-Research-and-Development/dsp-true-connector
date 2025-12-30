# DCP Issuer Module Split Plan

## Executive Summary

This document outlines a comprehensive plan to extract the Verifiable Credentials Issuer functionality from the current monolithic `dcp` module into a standalone Maven module with its own Spring Boot application, Docker image, and deployment configuration.

## Current State Analysis

### Existing Architecture

The current `dcp` module contains **two distinct functional domains**:

1. **Holder/Verifier Functionality** (Credential Service)
   - DID Document endpoint: `/.well-known/did.json`
   - Credential storage and presentation
   - Verifiable Presentation creation
   - Credential validation

2. **Issuer Functionality** (Issuer Service) 
   - Issuer DID Document (currently shared with holder)
   - Credential issuance workflows
   - Credential request processing
   - Credential approval/rejection
   - Issuer metadata management

### Problem Statement

Currently, **both holder and issuer share the same DID document** exposed at `/.well-known/did.json`. This creates:
- **Security concerns**: Issuer and holder identities should be separate
- **Scalability issues**: Cannot scale issuer independently
- **Deployment complexity**: Cannot deploy issuer separately from holder
- **Identity confusion**: Single DID represents both roles

### Current Components

#### Controllers
- `DidDocumentController` - Exposes `/.well-known/did.json` (shared for both roles)
- `IssuerController` - Issuer-specific endpoints (`/issuer/*`)
- `DcpController` - Holder/Verifier endpoints (`/dcp/*`)

#### Services
**Issuer-specific:**
- `IssuerService` - Core issuer business logic
- `CredentialIssuanceService` - Credential generation
- `CredentialDeliveryService` - Credential delivery to holders
- `CredentialMetadataService` - Issuer metadata management
- `InMemoryIssuerTrustService` - Issuer trust validation

**Shared (need duplication or library extraction):**
- `DidDocumentService` - DID document generation
- `KeyService` - Key management and rotation
- `KeyMetadataService` - Key metadata persistence
- `SelfIssuedIdTokenService` - Token validation

**Holder-specific:**
- `HolderService` - Holder operations
- `PresentationService` - VP creation
- `PresentationValidationService` - VP validation
- `ConsentService` - Consent management

#### Models (Shared)
- `DidDocument`, `VerificationMethod`, `ServiceEntry`
- `IssuerMetadata`, `CredentialRequest`
- `KeyMetadata`, `VerifiableCredential`
- All DCP message models

#### Repositories
- `CredentialRequestRepository` (issuer-specific)
- `KeyMetadataRepository` (shared)
- `VerifiableCredentialRepository` (holder-specific)
- `CredentialStatusRepository` (shared)

## Proposed Architecture

### Module Structure

```
dsp-true-connector/
├── dcp/                          # Holder/Verifier module (existing, refactored)
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/it/eng/dcp/
│       │   │   ├── holder/       # Holder-specific code
│       │   │   ├── verifier/     # Verifier-specific code
│       │   │   ├── rest/
│       │   │   │   ├── DcpController.java
│       │   │   │   └── DidDocumentController.java (holder DID)
│       │   │   └── autoconfigure/
│       │   └── resources/
│       │       ├── application-holder.properties
│       │       └── eckey-holder.p12
│       └── test/
│
├── dcp-issuer/                   # NEW: Issuer module
│   ├── pom.xml
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── src/
│       ├── main/
│       │   ├── java/it/eng/dcp/issuer/
│       │   │   ├── IssuerApplication.java
│       │   │   ├── rest/
│       │   │   │   ├── IssuerController.java
│       │   │   │   └── IssuerDidDocumentController.java
│       │   │   ├── service/
│       │   │   │   ├── IssuerService.java
│       │   │   │   ├── IssuerDidDocumentService.java
│       │   │   │   ├── CredentialIssuanceService.java
│       │   │   │   ├── CredentialDeliveryService.java
│       │   │   │   ├── CredentialMetadataService.java
│       │   │   │   ├── IssuerKeyService.java
│       │   │   │   └── IssuerKeyRotationService.java
│       │   │   ├── config/
│       │   │   │   ├── IssuerProperties.java
│       │   │   │   ├── IssuerMongoConfig.java
│       │   │   │   └── IssuerAutoConfiguration.java
│       │   │   ├── repository/
│       │   │   │   ├── CredentialRequestRepository.java
│       │   │   │   └── IssuerKeyMetadataRepository.java
│       │   │   └── model/
│       │   │       └── (issuer-specific models)
│       │   └── resources/
│       │       ├── application.properties
│       │       ├── application-issuer.properties
│       │       ├── eckey-issuer.p12
│       │       └── META-INF/
│       │           └── spring/
│       └── test/
│
└── dcp-common/                   # NEW: Shared library
    ├── pom.xml
    └── src/
        ├── main/
        │   └── java/it/eng/dcp/common/
        │       ├── model/        # Shared models
        │       │   ├── DidDocument.java
        │       │   ├── VerificationMethod.java
        │       │   ├── ServiceEntry.java
        │       │   ├── VerifiableCredential.java
        │       │   ├── IssuerMetadata.java
        │       │   └── ...
        │       └── util/         # Shared utilities
        │           ├── JwtUtil.java
        │           └── DidUtil.java
        └── test/
```

## Implementation Plan

### Phase 1: Create Common Library Module ✅ COMPLETED

**Goal**: Extract shared models and utilities into a reusable library

**Status**: ✅ All tasks completed - December 18, 2025

#### Tasks:
1. **Create `dcp-common` module**
   - [x] Create `dcp-common/pom.xml` with minimal dependencies
   - [x] Add to parent `pom.xml` modules section
   
2. **Move shared models**
   - [x] `DidDocument`, `VerificationMethod`, `ServiceEntry`
   - [x] `ProfileId`, `CredentialStatus`
   - [x] All DCP message models (`BaseDcpMessage`, `CredentialMessage`, etc.)
   - [x] `KeyMetadata`
   
3. **Move shared utilities**
   - [x] `DidUrlConverter` - DID/URL conversion
   - [x] `SelfSignedCertGenerator` - Certificate generation
   
4. **Move shared services**
   - [x] `KeyService` - Key management and rotation
   - [x] `KeyMetadataService` - Key metadata persistence
   
5. **Move shared repositories**
   - [x] `KeyMetadataRepository` - MongoDB repository

6. **Update dependencies**
   - [x] Update `dcp` module to depend on `dcp-common`
   - [x] Fix all imports (32 files updated)
   - [x] Run tests to verify no breakage (233 tests passing)
   
7. **Migrate tests**
   - [x] Move 9 test files to dcp-common
   - [x] Delete duplicate tests from dcp
   - [x] All tests passing in both modules

**Results**: 
- 9 classes migrated (7 models + 2 services)
- 2 utilities migrated
- 1 repository migrated
- 9 test files migrated
- 32 files updated in dcp module
- 16 duplicate files deleted
- 233 total tests passing ✅

### Phase 2: Create Issuer Module Structure

**Goal**: Set up the new issuer module with Spring Boot application

#### Tasks:
1. **Create module structure**
   - [ ] Create `dcp-issuer/pom.xml`
   - [ ] Add Spring Boot parent and dependencies
   - [ ] Add dependency on `dcp-common`
   - [ ] Add to parent `pom.xml` modules section

2. **Create Spring Boot application**
   - [ ] Create `IssuerApplication.java` with `@SpringBootApplication`
   - [ ] Create `application.properties` with issuer-specific config
   - [ ] Create `application-issuer.properties` profile

3. **Set up auto-configuration**
   - [ ] Create `IssuerAutoConfiguration.java`
   - [ ] Create `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
   - [ ] Enable component scanning for issuer package

### Phase 3: Move Issuer Functionality

**Goal**: Migrate all issuer-specific code to new module

#### Tasks:
1. **Copy and refactor controllers**
   - [ ] Move `IssuerController.java` to `dcp-issuer/rest/`
   - [ ] Create `IssuerDidDocumentController.java` for issuer DID
   - [ ] Update package names and imports

2. **Copy and refactor services**
   - [ ] Move `IssuerService.java`
   - [ ] Move `CredentialIssuanceService.java`
   - [ ] Move `CredentialDeliveryService.java`
   - [ ] Move `CredentialMetadataService.java`
   - [ ] Move `InMemoryIssuerTrustService.java`
   - [ ] Create `IssuerDidDocumentService.java` (copy from `DidDocumentService`)
   - [ ] Create `IssuerKeyService.java` (copy from `KeyService`)
   - [ ] Create `IssuerKeyRotationService.java` for key rotation logic

3. **Copy and refactor repositories**
   - [ ] Move `CredentialRequestRepository.java`
   - [ ] Create `IssuerKeyMetadataRepository.java`

4. **Copy configuration classes**
   - [ ] Create `IssuerProperties.java` (based on `DcpProperties`)
   - [ ] Create `IssuerMongoConfig.java` (based on `DCPMongoConfig`)
   - [ ] Create `CredentialMetadataConfig.java` copy

### Phase 4: Separate DID Documents

**Goal**: Create distinct DID documents for issuer and holder

#### Tasks:
1. **Update Issuer DID Document Service**
   ```java
   // IssuerDidDocumentService.java
   public DidDocument provideIssuerDidDocument() {
       String issuerDid = "did:web:localhost%3A8084:issuer";
       String baseEndpoint = protocol + "://localhost:" + serverPort;
       
       return DidDocument.Builder.newInstance()
           .id(issuerDid)
           .service(List.of(
               new ServiceEntry("IssuerService", "IssuerService", baseEndpoint + "/issuer")
           ))
           .verificationMethod(List.of(
               VerificationMethod.Builder.newInstance()
                   .id(issuerDid + "#" + keyService.getKidFromPublicKey())
                   .type("JsonWebKey2020")
                   .controller(issuerDid)
                   .publicKeyJwk(keyService.convertPublicKeyToJWK())
                   .build()
           ))
           .build();
   }
   ```

2. **Update Holder DID Document Service**
   - [ ] Remove issuer service entry from holder DID
   - [ ] Update to use holder-specific DID: `did:web:localhost%3A8083:holder`

3. **Create separate keystores**
   - [ ] `eckey-issuer.p12` for issuer keys
   - [ ] `eckey-holder.p12` for holder keys
   - [ ] Update services to load correct keystore

### Phase 5: Configuration & Properties

**Goal**: Create comprehensive configuration for standalone issuer

#### Tasks:
1. **Create `IssuerProperties.java`**
   ```java
   @ConfigurationProperties(prefix = "issuer")
   public class IssuerProperties {
       private String did;  // did:web:localhost%3A8084:issuer
       private String baseUrl;
       private int clockSkewSeconds = 120;
       private List<String> supportedProfiles;
       private Keystore keystore = new Keystore();
       private Mongodb mongodb = new Mongodb();
       
       public static class Keystore {
           private String path = "classpath:eckey-issuer.p12";
           private String password;
           private String alias = "issuer";
           private int rotationDays = 90;
       }
       
       public static class Mongodb {
           private String host = "localhost";
           private int port = 27017;
           private String database = "issuer_db";
           private String username;
           private String password;
       }
   }
   ```

2. **Create `application-issuer.properties`**
   ```properties
   # Issuer Application Configuration
   spring.application.name=dcp-issuer
   server.port=8084
   
   # Issuer DID Configuration
   issuer.did=did:web:localhost%3A8084:issuer
   issuer.base-url=http://localhost:8084
   issuer.clock-skew-seconds=120
   
   # Keystore Configuration
   issuer.keystore.path=classpath:eckey-issuer.p12
   issuer.keystore.password=password
   issuer.keystore.alias=issuer
   issuer.keystore.rotation-days=90
   
   # MongoDB Configuration
   spring.data.mongodb.host=localhost
   spring.data.mongodb.port=27017
   spring.data.mongodb.database=issuer_db
   spring.data.mongodb.authentication-database=admin
   
   # Credential Metadata Configuration
   issuer.credentials.supported[0].id=MembershipCredential
   issuer.credentials.supported[0].credential-type=MembershipCredential
   issuer.credentials.supported[0].profile=VC11_SL2021_JWT
   issuer.credentials.supported[0].format=jwt_vc
   
   # SSL Configuration (if needed)
   server.ssl.enabled=false
   ```

### Phase 6: Build Configuration

**Goal**: Configure Maven to build standalone executable JAR

#### Tasks:
1. **Update `dcp-issuer/pom.xml`**
   ```xml
   <build>
       <finalName>dcp-issuer</finalName>
       <plugins>
           <plugin>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-maven-plugin</artifactId>
               <configuration>
                   <mainClass>it.eng.dcp.issuer.IssuerApplication</mainClass>
                   <layout>JAR</layout>
               </configuration>
               <executions>
                   <execution>
                       <goals>
                           <goal>repackage</goal>
                       </goals>
                   </execution>
               </executions>
           </plugin>
           <plugin>
               <groupId>org.apache.maven.plugins</groupId>
               <artifactId>maven-dependency-plugin</artifactId>
               <executions>
                   <execution>
                       <id>copy-dependencies</id>
                       <phase>prepare-package</phase>
                       <goals>
                           <goal>copy-dependencies</goal>
                       </goals>
                       <configuration>
                           <outputDirectory>${project.build.directory}/dependency-jars</outputDirectory>
                       </configuration>
                   </execution>
               </executions>
           </plugin>
       </plugins>
   </build>
   ```

2. **Verify build**
   - [ ] Run `mvn clean package` in `dcp-issuer/`
   - [ ] Verify `target/dcp-issuer.jar` is created
   - [ ] Test with `java -jar target/dcp-issuer.jar`

### Phase 7: Docker Configuration

**Goal**: Create Docker image and compose configuration for issuer

#### Tasks:
1. **Create `dcp-issuer/Dockerfile`**
   ```dockerfile
   FROM eclipse-temurin:17-jre-alpine
   
   RUN mkdir -p /home/nobody/data/log/issuer
   
   WORKDIR /home/nobody
   
   # Copy dependencies
   COPY /target/dependency-jars /home/nobody/app/dependency-jars
   
   # Add the application's jar
   ADD /target/dcp-issuer.jar /home/nobody/app/dcp-issuer.jar
   
   RUN chown -R nobody:nogroup /home/nobody
   
   USER 65534
   
   # Run the jar file
   ENTRYPOINT ["java", "-jar", "/home/nobody/app/dcp-issuer.jar"]
   ```

2. **Create `dcp-issuer/docker-compose.yml`**
   ```yaml
   services:
     issuer-mongodb:
       container_name: issuer-mongodb
       image: mongo:7.0.12
       ports:
         - "27018:27017"
       volumes:
         - issuer-mongodb-data:/data/db
       environment:
         - MONGO_INITDB_DATABASE=issuer_db
   
     dcp-issuer:
       container_name: dcp-issuer
       build:
         context: .
         dockerfile: Dockerfile
       ports:
         - "8084:8084"
       depends_on:
         - issuer-mongodb
       environment:
         - SPRING_PROFILES_ACTIVE=issuer
         - SPRING_DATA_MONGODB_HOST=issuer-mongodb
         - SPRING_DATA_MONGODB_PORT=27017
         - SPRING_DATA_MONGODB_DATABASE=issuer_db
         - ISSUER_DID=did:web:localhost%3A8084:issuer
         - ISSUER_BASE_URL=http://localhost:8084
       volumes:
         - ./config:/home/nobody/config
         - ./logs:/home/nobody/data/log/issuer
   
   volumes:
     issuer-mongodb-data:
   ```

3. **Create configuration directory**
   - [ ] Create `dcp-issuer/config/` directory
   - [ ] Add external `application-issuer.properties` for overrides
   - [ ] Add external `credential-metadata-configuration.properties`

### Phase 8: Key Management & Rotation

**Goal**: Implement comprehensive key management for issuer

#### Tasks:
1. **Create `IssuerKeyRotationService.java`**
   ```java
   @Service
   public class IssuerKeyRotationService {
       
       private final IssuerKeyService keyService;
       private final IssuerKeyMetadataRepository metadataRepository;
       private final IssuerProperties properties;
       
       @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
       public void checkAndRotateKeys() {
           Optional<KeyMetadata> activeKey = metadataRepository.findByActiveTrue();
           
           if (activeKey.isEmpty()) {
               log.warn("No active key found, generating initial key");
               rotateKey();
               return;
           }
           
           Instant createdAt = activeKey.get().getCreatedAt();
           long daysSinceCreation = ChronoUnit.DAYS.between(createdAt, Instant.now());
           
           if (daysSinceCreation >= properties.getKeystore().getRotationDays()) {
               log.info("Key rotation required after {} days", daysSinceCreation);
               rotateKey();
           }
       }
       
       @Transactional
       public void rotateKey() {
           String keystorePath = properties.getKeystore().getPath();
           String password = properties.getKeystore().getPassword();
           
           String newAlias = keyService.rotateKeyAndUpdateMetadata(keystorePath, password);
           log.info("Key rotated successfully, new alias: {}", newAlias);
       }
   }
   ```

2. **Add scheduled task configuration**
   - [ ] Enable `@EnableScheduling` in `IssuerApplication`
   - [ ] Configure task executor if needed

3. **Create key rotation REST endpoint**
   ```java
   @RestController
   @RequestMapping("/issuer/admin")
   public class IssuerAdminController {
       
       @PostMapping("/rotate-key")
       public ResponseEntity<?> rotateKey() {
           // Require admin authentication
           keyRotationService.rotateKey();
           return ResponseEntity.ok(Map.of("message", "Key rotated successfully"));
       }
   }
   ```

### Phase 9: Testing

**Goal**: Comprehensive testing of issuer module

#### Tasks:
1. **Unit tests**
   - [ ] `IssuerServiceTest.java`
   - [ ] `IssuerDidDocumentServiceTest.java`
   - [ ] `CredentialIssuanceServiceTest.java`
   - [ ] `IssuerKeyRotationServiceTest.java`

2. **Integration tests**
   - [ ] Test DID document endpoint
   - [ ] Test credential request flow
   - [ ] Test credential approval flow
   - [ ] Test key rotation

3. **Docker tests**
   - [ ] Build Docker image
   - [ ] Start with docker-compose
   - [ ] Verify endpoints accessible
   - [ ] Test MongoDB connectivity

### Phase 10: Documentation

**Goal**: Complete documentation for new module

#### Tasks:
1. **Create `dcp-issuer/README.md`**
   - [ ] Overview of issuer module
   - [ ] Architecture diagram
   - [ ] Configuration guide
   - [ ] Deployment guide
   - [ ] API documentation

2. **Create `dcp-issuer/doc/DEPLOYMENT.md`**
   - [ ] Docker deployment instructions
   - [ ] Kubernetes deployment (if applicable)
   - [ ] Configuration examples
   - [ ] Monitoring and logging

3. **Create `dcp-issuer/doc/KEY_ROTATION.md`**
   - [ ] Key rotation concepts
   - [ ] Automatic rotation configuration
   - [ ] Manual rotation procedures
   - [ ] Troubleshooting

4. **Update root `README.md`**
   - [ ] Add `dcp-issuer` module to module list
   - [ ] Update architecture section

### Phase 11: Cleanup & Refactoring

**Goal**: Clean up original `dcp` module

#### Tasks:
1. **Remove issuer code from `dcp` module**
   - [ ] Delete `IssuerController.java`
   - [ ] Delete `IssuerService.java`
   - [ ] Delete `CredentialIssuanceService.java`
   - [ ] Delete `CredentialDeliveryService.java`
   - [ ] Delete `CredentialMetadataService.java`
   - [ ] Delete `InMemoryIssuerTrustService.java`

2. **Update `DidDocumentService` in `dcp` module**
   - [ ] Remove issuer service entry
   - [ ] Update DID to holder-specific

3. **Update tests**
   - [ ] Remove issuer-related tests
   - [ ] Fix broken imports
   - [ ] Verify all tests pass

4. **Update dependencies**
   - [ ] Remove unused dependencies
   - [ ] Verify `dcp-common` is properly used

## Component Mapping

### Services to Migrate to `dcp-issuer`

| Current Service | New Location | Notes |
|----------------|--------------|-------|
| `IssuerService` | `dcp-issuer/service/IssuerService.java` | Core issuer logic |
| `CredentialIssuanceService` | `dcp-issuer/service/CredentialIssuanceService.java` | Credential generation |
| `CredentialDeliveryService` | `dcp-issuer/service/CredentialDeliveryService.java` | Delivery to holders |
| `CredentialMetadataService` | `dcp-issuer/service/CredentialMetadataService.java` | Metadata management |
| `InMemoryIssuerTrustService` | `dcp-issuer/service/InMemoryIssuerTrustService.java` | Trust validation |
| `DidDocumentService` | `dcp-issuer/service/IssuerDidDocumentService.java` | Issuer DID (forked) |
| `KeyService` | `dcp-issuer/service/IssuerKeyService.java` | Key management (forked) |
| `KeyMetadataService` | `dcp-issuer/service/IssuerKeyMetadataService.java` | Key metadata (forked) |
| N/A | `dcp-issuer/service/IssuerKeyRotationService.java` | Key rotation (new) |
| `SelfIssuedIdTokenService` | `dcp-issuer/service/SelfIssuedIdTokenService.java` | Token validation (copy) |

### Services to Keep in `dcp` (Holder/Verifier)

| Service | Purpose |
|---------|---------|
| `HolderService` | Holder operations |
| `PresentationService` | VP creation |
| `PresentationValidationService` | VP validation |
| `ConsentService` | Consent management |
| `DcpCredentialService` | Credential storage |
| `DcpCompliantTokenService` | Token service |
| `RevocationService` | Revocation checks |
| `SchemaRegistryService` | Schema validation |

### Models to Move to `dcp-common`

| Model | Reason |
|-------|--------|
| `DidDocument` | Used by both issuer and holder |
| `VerificationMethod` | Part of DID document |
| `ServiceEntry` | Part of DID document |
| `VerifiableCredential` | Core VC model |
| `VerifiablePresentation` | Core VP model |
| `IssuerMetadata` | Shared metadata format |
| `CredentialRequest` | Message format |
| `CredentialMessage` | Message format |
| `CredentialRequestMessage` | Message format |
| `BaseDcpMessage` | Base message class |
| `KeyMetadata` | Key metadata model |

## Port Allocation

| Service | Port | Purpose |
|---------|------|---------|
| DCP Holder/Verifier | 8083 | Holder credential service |
| DCP Issuer | 8084 | Issuer credential service |
| Holder MongoDB | 27017 | Holder database |
| Issuer MongoDB | 27018 | Issuer database |

## DID Structure

### Issuer DID
```
did:web:localhost%3A8084:issuer
```

**DID Document:**
```json
{
  "@context": "https://www.w3.org/ns/did/v1",
  "id": "did:web:localhost%3A8084:issuer",
  "service": [
    {
      "id": "IssuerService",
      "type": "IssuerService",
      "serviceEndpoint": "http://localhost:8084/issuer"
    }
  ],
  "verificationMethod": [
    {
      "id": "did:web:localhost%3A8084:issuer#key-1",
      "type": "JsonWebKey2020",
      "controller": "did:web:localhost%3A8084:issuer",
      "publicKeyJwk": {...}
    }
  ]
}
```

### Holder DID
```
did:web:localhost%3A8083:holder
```

**DID Document:**
```json
{
  "@context": "https://www.w3.org/ns/did/v1",
  "id": "did:web:localhost%3A8083:holder",
  "service": [
    {
      "id": "CredentialService",
      "type": "CredentialService",
      "serviceEndpoint": "http://localhost:8083"
    }
  ],
  "verificationMethod": [
    {
      "id": "did:web:localhost%3A8083:holder#key-1",
      "type": "JsonWebKey2020",
      "controller": "did:web:localhost%3A8083:holder",
      "publicKeyJwk": {...}
    }
  ]
}
```

## API Endpoints

### Issuer Endpoints (Port 8084)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/.well-known/did.json` | GET | Issuer DID document |
| `/issuer/metadata` | GET | Issuer metadata |
| `/issuer/credentials` | POST | Request credentials |
| `/issuer/requests/{id}` | GET | Check request status |
| `/issuer/requests/{id}/approve` | POST | Approve credential request |
| `/issuer/requests/{id}/reject` | POST | Reject credential request |
| `/issuer/admin/rotate-key` | POST | Manual key rotation |

### Holder Endpoints (Port 8083)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/.well-known/did.json` | GET | Holder DID document |
| `/dcp/credentials` | POST | Receive credentials |
| `/dcp/presentations` | POST | Create presentations |
| `/dcp/consent` | GET/POST | Manage consent |

## Database Schema

### Issuer Database (`issuer_db`)

**Collections:**
- `credential_requests` - Credential request tracking
- `issuer_key_metadata` - Key metadata and rotation history
- `issued_credentials_log` - Audit log of issued credentials

### Holder Database (`true_connector_provider`)

**Collections:**
- `verifiable_credentials` - Stored credentials
- `consent_records` - Consent management
- `holder_key_metadata` - Key metadata

## Security Considerations

1. **Separate Keystores**
   - Issuer uses `eckey-issuer.p12`
   - Holder uses `eckey-holder.p12`
   - No shared cryptographic material

2. **Separate DIDs**
   - Issuer: `did:web:localhost%3A8084:issuer`
   - Holder: `did:web:localhost%3A8083:holder`
   - Clear separation of identities

3. **Key Rotation**
   - Automatic rotation after configurable period
   - Manual rotation endpoint for emergencies
   - Archive old keys for verification

4. **Database Isolation**
   - Separate MongoDB instances
   - No cross-database queries
   - Independent scaling

## Migration Strategy

### Zero-Downtime Migration (if needed)

1. **Deploy issuer module alongside existing module**
2. **Configure routing to new issuer endpoints**
3. **Migrate credential requests to new system**
4. **Verify functionality**
5. **Decommission old issuer code**

### Testing Strategy

1. **Unit tests** - All services have >80% coverage
2. **Integration tests** - Full flow testing
3. **Docker tests** - Container orchestration
4. **Load tests** - Performance validation
5. **Security tests** - Penetration testing

## Success Criteria

- [ ] Issuer module builds as standalone JAR
- [ ] Docker image created successfully
- [ ] Separate DID documents for issuer and holder
- [ ] All issuer endpoints functional on port 8084
- [ ] Key rotation works automatically and manually
- [ ] All tests pass (unit, integration, docker)
- [ ] Documentation complete
- [ ] Original `dcp` module cleaned up
- [ ] No regression in holder/verifier functionality

## Risks & Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Shared code conflicts | High | Create `dcp-common` library early |
| MongoDB schema changes | Medium | Version database schemas |
| Breaking existing integrations | High | Maintain backward compatibility |
| Key migration issues | High | Thorough testing of key rotation |
| Docker orchestration complexity | Medium | Comprehensive docker-compose examples |

## Timeline Estimate

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Phase 1: Common Library | 2 days | None |
| Phase 2: Module Structure | 1 day | Phase 1 |
| Phase 3: Move Functionality | 3 days | Phase 2 |
| Phase 4: Separate DIDs | 2 days | Phase 3 |
| Phase 5: Configuration | 1 day | Phase 4 |
| Phase 6: Build Config | 1 day | Phase 5 |
| Phase 7: Docker | 2 days | Phase 6 |
| Phase 8: Key Management | 2 days | Phase 7 |
| Phase 9: Testing | 3 days | Phase 8 |
| Phase 10: Documentation | 2 days | Phase 9 |
| Phase 11: Cleanup | 2 days | Phase 10 |
| **Total** | **21 days** | |

## Next Steps

1. **Review and approve this plan**
2. **Set up development branch**: `feature/issuer-module-split`
3. **Begin Phase 1**: Create `dcp-common` library
4. **Iterative development**: Complete one phase at a time
5. **Continuous testing**: Run tests after each phase
6. **Documentation**: Update docs as you go

---

**Document Version**: 1.0  
**Created**: December 18, 2025  
**Status**: Draft - Awaiting Review

