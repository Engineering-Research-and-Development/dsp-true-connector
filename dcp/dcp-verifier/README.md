# DCP-Verifier Module - Spring Boot Autoconfiguration

This module has been configured as a **Spring Boot autoconfiguration module**, making it automatically available when included as a dependency in other modules.

## Quick Start

### 1. Add Dependency

Add the `dcp-verifier` dependency to your module's `pom.xml`:

```xml
<dependency>
    <groupId>it.eng</groupId>
    <artifactId>dcp-verifier</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. Configure Properties (Optional)

Add to your `application.properties` (if needed):

```properties
# DCP Verifier configuration (enabled by default)
dcp.verifier.enabled=true

# DCP Common configuration (required for token validation)
dcp.connector-did=did:web:your-domain.com:connector
```

### 3. Use DCP Verifier Beans

Inject and use the token validation service in your code:

```java
@Service
public class YourVerificationService {
    private final SelfIssuedIdTokenService tokenService;
    
    public YourVerificationService(SelfIssuedIdTokenService tokenService) {
        this.tokenService = tokenService;
    }
    
    public void verifyToken(String token) {
        try {
            JWTClaimsSet claims = tokenService.validateToken(token);
            // Token is valid, use claims
            log.info("Token validated for issuer: {}", claims.getIssuer());
        } catch (SecurityException e) {
            // Token validation failed
            log.error("Token validation failed: {}", e.getMessage());
        }
    }
}
```

**That's it!** No manual configuration needed.

## How It Works

1. **Spring Boot discovers** the autoconfiguration via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
2. **DcpVerifierAutoConfiguration loads** and checks if `dcp.verifier.enabled=true` (default)
3. **Component scanning** finds all services from `dcp-common` package including `SelfIssuedIdTokenService`
4. **Beans are registered** in the Spring ApplicationContext
5. **All modules share** the same DCP bean instances (no duplication!)

## Key Features

✅ **Automatic Discovery** - No manual `@Import` or `@ComponentScan` needed  
✅ **Conditional Loading** - Enabled by default, can be disabled via `dcp.verifier.enabled=false`  
✅ **Single Instance** - All modules share the same DCP bean instances  
✅ **Property-Driven** - Configure via `application.properties`  
✅ **Independent Testing** - Each module can test with token verification functionality  
✅ **Shared Services** - Uses `dcp-common` services for core token validation logic

## Available Services

All these beans from `dcp-common` are automatically available for `@Autowired` injection:

**Token Services:**
- `SelfIssuedIdTokenService` - Create and validate Self-Issued ID Tokens
- `JtiReplayCache` - Prevent token replay attacks
- `DidResolverService` - Resolve DIDs to retrieve public keys
- `KeyService` - Manage cryptographic keys

**Configuration:**
- `BaseDidDocumentConfiguration` - Base DID document configuration
- `DidDocumentConfig` - DID document details

See `dcp-common` module for the complete list of available services.

## Configuration Properties

### DCP Verifier Properties

```properties
# Enable/disable the verifier module (default: true)
dcp.verifier.enabled=true
```

### DCP Common Properties (inherited)

```properties
# Connector DID (required for token validation)
dcp.connector-did=did:web:example.com:connector

# DID document configuration
dcp.did-document.did=did:web:example.com:connector
dcp.did-document.keystore-path=/path/to/keystore.p12
dcp.did-document.keystore-password=password
dcp.did-document.key-alias=key1
```

## Architecture

### Multi-Module Support

When multiple modules (catalog, negotiation, data-transfer) include `dcp-verifier` as a dependency:

1. **Single AutoConfiguration** - Spring Boot loads `DcpVerifierAutoConfiguration` only once
2. **Shared Bean Instances** - All modules share the same `SelfIssuedIdTokenService` and other beans
3. **No Duplication** - Spring's ApplicationContext ensures singleton beans across the application

### Package Structure

```
dcp-verifier/
├── src/main/java/
│   └── it/eng/dcp/verifier/
│       └── autoconfigure/
│           └── DcpVerifierAutoConfiguration.java
└── src/main/resources/
    └── META-INF/
        ├── spring.factories (legacy support)
        └── spring/
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## Disabling the Module

If you need to disable the verifier module in a specific environment:

```properties
dcp.verifier.enabled=false
```

Or via environment variable:

```bash
DCP_VERIFIER_ENABLED=false
```

## Token Validation

The `SelfIssuedIdTokenService` provides comprehensive token validation:

- ✅ JWT signature verification using DID-resolved public keys
- ✅ Expiration time (`exp`) validation with clock skew tolerance
- ✅ Issue time (`iat`) validation
- ✅ Not-before time (`nbf`) validation
- ✅ Issuer (`iss`) and Subject (`sub`) equality check
- ✅ Audience (`aud`) verification
- ✅ Replay attack prevention via JTI cache
- ✅ DID document verification relationship validation

## Usage Examples

### Basic Token Validation

```java
@RestController
@RequestMapping("/api/verify")
public class TokenVerificationController {
    
    private final SelfIssuedIdTokenService tokenService;
    
    public TokenVerificationController(SelfIssuedIdTokenService tokenService) {
        this.tokenService = tokenService;
    }
    
    @PostMapping("/token")
    public ResponseEntity<?> verifyToken(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        
        try {
            JWTClaimsSet claims = tokenService.validateToken(token);
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "issuer", claims.getIssuer(),
                "subject", claims.getSubject(),
                "audience", claims.getAudience()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of(
                "valid", false,
                "error", e.getMessage()
            ));
        }
    }
}
```

### Integration with Security Filter

```java
@Component
public class DcpTokenFilter extends OncePerRequestFilter {
    
    private final SelfIssuedIdTokenService tokenService;
    
    public DcpTokenFilter(SelfIssuedIdTokenService tokenService) {
        this.tokenService = tokenService;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            try {
                JWTClaimsSet claims = tokenService.validateToken(token);
                // Token is valid, set authentication context
                // ... your authentication logic
            } catch (SecurityException e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
```

## Relationship with dcp-holder

Both `dcp-holder` and `dcp-verifier` can coexist in the same application:

- **dcp-holder**: Focuses on holding and presenting credentials
- **dcp-verifier**: Focuses on token validation and verification

Both modules depend on `dcp-common` for shared services, and Spring Boot ensures no bean duplication occurs.

## Testing

The autoconfiguration is automatically available in tests. Use `@SpringBootTest` to get full context:

```java
@SpringBootTest
class TokenValidationTest {
    
    @Autowired
    private SelfIssuedIdTokenService tokenService;
    
    @Test
    void testTokenValidation() {
        // Your test using tokenService
    }
}
```

Or use `@Import` for specific configuration:

```java
@Import(DcpVerifierAutoConfiguration.class)
class UnitTest {
    // Your test
}
```

## Troubleshooting

### Beans Not Found

If you get "No qualifying bean" errors:

1. Check that `dcp.verifier.enabled=true` (it's true by default)
2. Verify the dependency is in your `pom.xml`
3. Check that you're using `@SpringBootApplication` or `@EnableAutoConfiguration`

### Token Validation Fails

If token validation always fails:

1. Verify `dcp.connector-did` is set correctly
2. Check that DID documents are resolvable
3. Ensure the keystore is properly configured in `dcp-common` configuration
4. Check logs for detailed error messages

## Migration from Manual Configuration

If you previously had manual `@ComponentScan` or `@Import` for DCP services:

1. **Remove** manual `@ComponentScan("it.eng.dcp.common")` annotations
2. **Remove** manual `@Import(...)` for DCP configurations  
3. **Add** the `dcp-verifier` dependency to your `pom.xml`
4. **Configure** properties in `application.properties`

The autoconfiguration will handle everything automatically.

## Summary

The `dcp-verifier` module provides automatic token verification capabilities through Spring Boot autoconfiguration. Simply add the dependency, and all token validation services become available throughout your application with no manual configuration required.
