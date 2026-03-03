# DCP-Verifier Autoconfiguration

## Overview

The `dcp-verifier` module is a **Spring Boot autoconfiguration module** that provides automatic token verification capabilities. It is automatically discovered when included as a dependency.

## Usage

### Add Dependency

```xml
<dependency>
    <groupId>it.eng</groupId>
    <artifactId>dcp-verifier</artifactId>
    <version>${revision}</version>
</dependency>
```

### Inject and Use Services

```java
@Service
public class YourService {
    private final SelfIssuedIdTokenService tokenService;
    
    @Autowired
    public YourService(SelfIssuedIdTokenService tokenService) {
        this.tokenService = tokenService;
    }
    
    public void verifyToken(String token) {
        try {
            JWTClaimsSet claims = tokenService.validateToken(token);
            // Token is valid - use claims
        } catch (SecurityException e) {
            // Token validation failed
        }
    }
}
```

## Available Services

Beans from `dcp-common` automatically available for injection:

- `SelfIssuedIdTokenService` - Token creation and validation
- `JtiReplayCache` - Replay attack prevention
- `DidResolverService` - DID resolution
- `KeyService` - Cryptographic key management
- `BaseDidDocumentConfiguration` - DID document configuration

## Configuration Properties

```properties
# Enable/disable the verifier module (default: true)
dcp.verifier.enabled=true

# Connector DID (required)
dcp.connector-did=did:web:example.com:connector

# Keystore (EC key used for signing / DID verification method)
dcp.keystore.path=eckey.p12
dcp.keystore.password=password
dcp.keystore.alias=dsptrueconnector
```

## Architecture

### Autoconfiguration Class

```java
@Configuration
@ConditionalOnProperty(prefix = "dcp.verifier", name = "enabled", 
                       havingValue = "true", matchIfMissing = true)
@ComponentScan({"it.eng.dcp.common", "it.eng.dcp.verifier"})
public class DcpVerifierAutoConfiguration {
}
```

### Discovery Files

**Spring Boot 3.x:**  
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Legacy:**  
`META-INF/spring.factories`

### Bean Sharing

When multiple modules include `dcp-verifier`:
- Spring Boot loads autoconfiguration once
- All modules share the same bean instances
- No duplication occurs (managed by ApplicationContext)

## Testing

```java
@SpringBootTest
class TokenVerificationTest {
    
    @Autowired
    private SelfIssuedIdTokenService tokenService;
    
    @Test
    void testValidation() {
        // Test token validation
    }
}
```

## Module Comparison

| Feature | dcp-holder | dcp-verifier |
|---------|-----------|--------------|
| **Purpose** | Credential holding & presentation | Token verification |
| **Property Prefix** | `dcp.*` | `dcp.verifier.*` |
| **MongoDB Required** | Yes | No |
| **Component Scan** | `it.eng.dcp`, `it.eng.dcp.common` | `it.eng.dcp.common`, `it.eng.dcp.verifier` |

Both modules can coexist without conflicts and share `dcp-common` services.

