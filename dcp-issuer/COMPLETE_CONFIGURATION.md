# DCP-Issuer Complete Configuration Summary

## Overview

Successfully configured the dcp-issuer standalone Spring Boot application with all necessary dependencies, including:
- OkHttpClient with TLS/SSL support
- MongoDB repositories from dcp-common and tools modules
- Component scanning for all required packages
- SSL bundle configuration for production-ready TLS

## Final Configuration

### IssuerApplication.java

```java
@SpringBootApplication(scanBasePackages = {
    "it.eng.dcp.issuer",      // Issuer-specific components
    "it.eng.dcp.common",      // Shared DCP services (KeyService, DidDocumentService, etc.)
    "it.eng.tools"            // Tools (OkHttpClient, SSL, DAPS, etc.)
})
@EnableMongoRepositories(basePackages = {
    "it.eng.dcp.issuer.repository",    // Issuer repositories
    "it.eng.dcp.common.repository",    // Common repositories (KeyMetadataRepository)
    "it.eng.tools.repository"          // Tools repositories (ApplicationPropertiesRepository)
})
@EnableScheduling
public class IssuerApplication
```

### Key Features Enabled

#### 1. Component Scanning
- **it.eng.dcp.issuer** - All issuer services, controllers, and configuration
- **it.eng.dcp.common** - Shared services:
  - `SelfIssuedIdTokenService` - Token creation/validation
  - `DidDocumentService` - DID document generation
  - `DidResolverService` - DID resolution
  - `KeyService` - Key management
  - `KeyMetadataService` - Key metadata
  - `JtiReplayCache` - Replay attack prevention
  
- **it.eng.tools** - Infrastructure services:
  - `OkHttpClient` - HTTP client with TLS
  - `OkHttpRestClient` - REST client wrapper
  - `OcspTrustManagerFactory` - OCSP validation
  - `CachedOcspValidator` - OCSP caching
  - DAPS services (optional, for dataspace authentication)

#### 2. MongoDB Repository Scanning
All MongoDB repositories are now discovered:
- `CredentialRequestRepository` (issuer)
- `KeyMetadataRepository` (common)
- `ApplicationPropertiesRepository` (tools)

#### 3. SSL/TLS Configuration

**Spring SSL Bundle** (for OkHttpClient):
```properties
# SSL Bundle for TLS context
spring.ssl.bundle.jks.connector.key.alias=dcp-issuer
spring.ssl.bundle.jks.connector.key.password=password
spring.ssl.bundle.jks.connector.keystore.location=classpath:dcp-issuer.p12
spring.ssl.bundle.jks.connector.keystore.password=password
spring.ssl.bundle.jks.connector.keystore.type=PKCS12
spring.ssl.bundle.jks.connector.truststore.type=PKCS12
spring.ssl.bundle.jks.connector.truststore.location=classpath:dsp-truststore.p12
spring.ssl.bundle.jks.connector.truststore.password=password
```

**OCSP Configuration** (certificate validation - commented out by default):
```properties
#ocsp.enabled=false
#ocsp.revocation-check-enabled=false
```

**Note:** The SSL bundle references `dcp-issuer.p12` and uses `dsp-truststore.p12` for trust store.

#### 4. Required Properties

**Main application.properties**:
```properties
# Application Configuration
spring.application.name=dcp-issuer
server.port=8084

# DCP Configuration
dcp.connector-did=did:web:localhost%3A8084:issuer
dcp.base-url=http://localhost:8084
dcp.host=localhost
dcp.clock-skew-seconds=120

# Keystore Configuration
dcp.keystore.path=classpath:eckey-issuer.p12
dcp.keystore.password=password
dcp.keystore.alias=dcp-issuer
dcp.keystore.rotation-days=90

# MongoDB Configuration
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=issuer_db
spring.data.mongodb.authentication-database=admin

# Logging Configuration
logging.level.it.eng.dcp.issuer=DEBUG
logging.level.org.springframework.data.mongodb=DEBUG
logging.level.org.springframework.web=INFO

# Jackson Configuration
spring.jackson.serialization.write-dates-as-timestamps=false
spring.jackson.serialization.indent-output=true

# SSL Configuration (disabled by default)
server.ssl.enabled=false

# OCSP Configuration (commented out by default)
#ocsp.enabled=false
#ocsp.revocation-check-enabled=false
```

**Note:** Use `dcp.*` prefix for DCP-specific properties, not `issuer.*`

**Test application.properties** - Same structure with test values

#### 5. Credential Metadata Configuration

**credential-metadata-configuration.properties** (example):
```properties
# Configure supported credentials for the issuer metadata endpoint
# Each credential requires at least an ID and credentialType

# Example 1: MembershipCredential
dcp.credentials.supported[0].id=550e8400-e29b-41d4-a716-446655440000
dcp.credentials.supported[0].type=CredentialObject
dcp.credentials.supported[0].credentialType=MembershipCredential
dcp.credentials.supported[0].credentialSchema=https://example.com/schemas/membership-credential.json
dcp.credentials.supported[0].bindingMethods[0]=did:web
dcp.credentials.supported[0].profile=vc11-sl2021/jwt

# Example 2: CompanyCredential with issuance policy
dcp.credentials.supported[1].id=d5c77b0e-7f4e-4fd5-8c5f-28b5fc3f96d1
dcp.credentials.supported[1].type=CredentialObject
dcp.credentials.supported[1].credentialType=CompanyCredential
dcp.credentials.supported[1].offerReason=reissue
dcp.credentials.supported[1].profile=vc20-bssl/jwt

# Configure supported profiles (used as default if credential doesn't specify one)
dcp.supportedProfiles[0]=VC11_SL2021_JWT
dcp.supportedProfiles[1]=VC11_SL2021_JSONLD
```

**Note:** This configuration defines which credential types the issuer can issue and their metadata.


