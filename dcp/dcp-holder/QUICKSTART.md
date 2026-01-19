# DCP-Holder Autoconfiguration - Quick Start Guide

## What Was Done

The `dcp-holder` module has been successfully configured as a Spring Boot autoconfiguration module that will automatically register all its beans when included as a dependency.

### Files Created/Modified

✅ **Created:**
- `dcp-holder/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `dcp-holder/src/main/resources/META-INF/spring.factories`
- `dcp-holder/AUTOCONFIGURATION.md` (comprehensive documentation)

✅ **Modified:**
- `catalog/pom.xml` - Added dcp-holder dependency
- `negotiation/pom.xml` - Added dcp-holder dependency
- `data-transfer/pom.xml` - Added dcp-holder dependency

## How to Use

### 1. Rebuild the Project

```powershell
mvn clean install
```

### 2. Configure DCP Properties

Add the following to your `application.properties` or `application.yml`:

**Minimum Required Configuration:**
```properties
# DCP Configuration
dcp.enabled=true
dcp.connector-did=did:web:your-domain.com:connector
dcp.base-url=https://your-domain.com
dcp.host=your-domain.com

# Keystore (optional - uses defaults if not specified)
dcp.keystore.path=eckey.p12
dcp.keystore.password=password
dcp.keystore.alias=dsptrueconnector

# MongoDB (required)
spring.data.mongodb.uri=mongodb://localhost:27017/dcp
```

### 3. Inject DCP Beans in Your Services

DCP beans are now automatically available in all modules (catalog, negotiation, data-transfer):

```java
@Service
public class YourCatalogService {
    
    private final HolderService holderService;
    private final PresentationService presentationService;
    private final DcpCredentialService dcpCredentialService;
    
    @Autowired
    public YourCatalogService(
            HolderService holderService,
            PresentationService presentationService,
            DcpCredentialService dcpCredentialService) {
        this.holderService = holderService;
        this.presentationService = presentationService;
        this.dcpCredentialService = dcpCredentialService;
    }
    
    // Use DCP services in your business logic
    public void processWithDcp() {
        // Your code here
    }
}
```

### 4. Enable/Disable DCP Per Environment

**Development (DCP enabled):**
```properties
# application-dev.properties
dcp.enabled=true
```

**Testing (DCP disabled):**
```properties
# application-test.properties
dcp.enabled=false
```

## Available DCP Beans

The following services are automatically registered and available for injection:

### Core Services
- `HolderService` - Holder-side operations
- `PresentationService` - Create and manage presentations
- `PresentationValidationService` - Validate presentations
- `DcpCredentialService` - Credential management
- `CredentialIssuanceClient` - Interact with issuers
- `DcpVerifierClient` - Interact with verifiers

### Support Services
- `VerifiablePresentationSigner` - Sign presentations
- `PresentationAccessTokenGenerator` - Generate access tokens
- `IssuerTrustService` - Manage trusted issuers
- `RevocationService` - Handle credential revocation
- `SchemaRegistryService` - Manage schemas
- `PresentationRateLimiter` - Rate limiting

### Repositories
- `VerifiableCredentialRepository` - Store credentials
- `CredentialRequestRepository` - Track requests
- `CredentialStatusRepository` - Track credential status

## Important: No Bean Duplication

**Question:** Won't DCP beans be created multiple times since catalog, negotiation, and data-transfer all depend on dcp-holder?

**Answer:** No! Spring Boot ensures beans are created **only once** per application context:

1. Maven deduplicates the dcp-holder JAR on the classpath
2. Spring Boot loads each autoconfiguration class only once
3. All modules share the same Spring ApplicationContext in the connector
4. Bean names are unique within the context

**Your current architecture is CORRECT and recommended!** ✅

## Verification

### Check Autoconfiguration

Run with debug logging:
```properties
# application.properties
logging.level.org.springframework.boot.autoconfigure=DEBUG
```

Look for this in startup logs:
```
DcpHolderAutoConfiguration matched:
   - @ConditionalOnProperty (dcp.enabled=true) matched
```

### List DCP Beans

Add this to your main application for testing:
```java
@SpringBootApplication
public class ConnectorApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(ConnectorApplication.class, args);
        
        // List all DCP beans
        System.out.println("\n=== DCP Beans ===");
        Arrays.stream(ctx.getBeanDefinitionNames())
            .filter(name -> name.toLowerCase().contains("dcp") || 
                          name.contains("Holder") || 
                          name.contains("Presentation"))
            .forEach(System.out::println);
    }
}
```

## Testing Individual Modules

Each module can now be tested independently with DCP functionality:

```java
@SpringBootTest
class CatalogServiceTest {
    
    @Autowired
    private HolderService holderService; // ✅ Automatically available
    
    @Autowired
    private CatalogService catalogService;
    
    @Test
    void testCatalogWithDcp() {
        // Test your catalog logic that uses DCP
        assertNotNull(holderService);
    }
}
```

### Disable DCP for Specific Tests

```java
@SpringBootTest(properties = {"dcp.enabled=false"})
class CatalogServiceWithoutDcpTest {
    // DCP beans won't be loaded
}
```

## Troubleshooting

### Issue: DCP beans not found
**Solution:** 
- Check `dcp.enabled` is not set to `false`
- Verify required properties are set (especially `dcp.connector-did`)
- Run `mvn clean install` to rebuild

### Issue: Configuration properties not binding
**Solution:**
- Ensure properties start with `dcp.` prefix
- Check for typos in property names
- Use `@ConfigurationPropertiesScan` if needed

### Issue: MongoDB connection errors
**Solution:**
- Verify MongoDB is running
- Check connection string in `spring.data.mongodb.uri`
- DCP module requires MongoDB for credential storage

## Next Steps

1. ✅ Run `mvn clean install` to rebuild all modules
2. ✅ Add DCP configuration to `application.properties`
3. ✅ Start the connector application
4. ✅ Verify DCP beans are loaded in startup logs
5. ✅ Inject and use DCP services in your code
6. ✅ Test individual modules (catalog, negotiation, data-transfer)

## Example Configuration Files

### application.properties (Production)
```properties
# DCP Configuration
dcp.enabled=true
dcp.connector-did=did:web:production.example.com:connector
dcp.base-url=https://production.example.com
dcp.host=production.example.com
dcp.clock-skew-seconds=120

# Keystore
dcp.keystore.path=file:/etc/connector/eckey.p12
dcp.keystore.password=${DCP_KEYSTORE_PASSWORD}
dcp.keystore.alias=dsptrueconnector

# Issuer
dcp.issuer.location=https://issuer.example.com

# Trusted Issuers (example)
dcp.trusted-issuers.MembershipCredential=did:web:issuer1.com,did:web:issuer2.com
dcp.trusted-issuers.DataProcessingCredential=did:web:trusted-issuer.com

# MongoDB
spring.data.mongodb.uri=mongodb://mongo:27017/dcp
```

### application-dev.yml (Development)
```yaml
dcp:
  enabled: true
  connector-did: did:web:localhost:connector
  base-url: http://localhost:8080
  host: localhost
  clock-skew-seconds: 300
  keystore:
    path: eckey.p12
    password: password
    alias: dsptrueconnector
  issuer:
    location: http://localhost:8081
  trusted-issuers:
    MembershipCredential:
      - did:web:localhost:issuer
      
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/dcp-dev
```

## Additional Resources

For detailed information, see `AUTOCONFIGURATION.md` in the dcp-holder directory.

## Summary

✅ **What you get:**
- Automatic DCP bean registration across all modules
- Single shared instance of all DCP services
- Easy enable/disable via configuration
- Independent module testing capability
- Clean, explicit dependency declaration

✅ **What you need to do:**
- Run `mvn clean install`
- Configure DCP properties
- Inject and use DCP beans in your services
- That's it! No manual configuration needed.

🎉 **The dcp-holder module is now fully autoconfigurable!**

