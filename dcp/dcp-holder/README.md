# DCP-Holder Module - Spring Boot Autoconfiguration

This module has been configured as a **Spring Boot autoconfiguration module**, making it automatically available when included as a dependency in other modules.

## Quick Start

### 1. Add Dependency

The `dcp-holder` dependency has already been added to:
- ✅ `catalog/pom.xml`
- ✅ `negotiation/pom.xml`
- ✅ `data-transfer/pom.xml`

### 2. Configure Properties

Add to your `application.properties`:

```properties
# Minimum required configuration
dcp.enabled=true
dcp.connector-did=did:web:your-domain.com:connector
spring.data.mongodb.uri=mongodb://localhost:27017/dcp
```

### 3. Use DCP Beans

Inject and use DCP services in your code:

```java
@Service
public class YourService {
    private final HolderService holderService;
    
    public YourService(HolderService holderService) {
        this.holderService = holderService;
    }
    
    public void yourMethod() {
        // Use DCP functionality
    }
}
```

**That's it!** No manual configuration needed.

## Documentation

📖 **Comprehensive documentation available:**

- **[QUICKSTART.md](./QUICKSTART.md)** - Start here! Quick reference guide
- **[IMPLEMENTATION_SUMMARY.md](./IMPLEMENTATION_SUMMARY.md)** - What was done and how it works
- **[ARCHITECTURE_RECOMMENDATIONS.md](./ARCHITECTURE_RECOMMENDATIONS.md)** - Why multiple dependencies don't cause duplication
- **[ARCHITECTURE_DIAGRAMS.md](./ARCHITECTURE_DIAGRAMS.md)** - Visual diagrams of the architecture
- **[AUTOCONFIGURATION.md](./AUTOCONFIGURATION.md)** - Detailed technical documentation

## How It Works

1. **Spring Boot discovers** the autoconfiguration via `META-INF/spring/*.AutoConfiguration.imports`
2. **DcpHolderAutoConfiguration loads** and checks if `dcp.enabled=true`
3. **Component scanning** finds all `@Service`, `@Component`, `@Repository` classes
4. **Beans are registered** in the Spring ApplicationContext
5. **All modules share** the same DCP bean instances (no duplication!)

## Key Features

✅ **Automatic Discovery** - No manual `@Import` or `@ComponentScan` needed  
✅ **Conditional Loading** - Enabled by default, can be disabled via `dcp.enabled=false`  
✅ **Single Instance** - All modules share the same DCP bean instances  
✅ **Property-Driven** - Configure via `application.properties`  
✅ **Independent Testing** - Each module can test with DCP functionality  

## Available Services

All these beans are automatically available for `@Autowired` injection:

**Core Services:**
- `HolderService` - Holder operations
- `PresentationService` - Create presentations
- `DcpCredentialService` - Credential management
- `PresentationValidationService` - Validate presentations
- `CredentialIssuanceClient` - Request credentials
- And many more...

**Repositories:**
- `VerifiableCredentialRepository` - Store credentials
- `CredentialRequestRepository` - Track requests
- `CredentialStatusRepository` - Track status

See [IMPLEMENTATION_SUMMARY.md](./IMPLEMENTATION_SUMMARY.md#available-dcp-beans) for the complete list.

## Configuration Properties

All properties use the `dcp.*` prefix:

```properties
# Core configuration
dcp.enabled=true
dcp.connector-did=did:web:example.com:connector
dcp.base-url=https://example.com
dcp.host=example.com
dcp.clock-skew-seconds=120

# Keystore
dcp.keystore.path=eckey.p12
dcp.keystore.password=password
dcp.keystore.alias=dsptrueconnector

# Issuer
dcp.issuer.location=https://issuer.example.com

# Trusted issuers
dcp.trusted-issuers.MembershipCredential=did:web:issuer1.com,did:web:issuer2.com
```

## FAQ

### Q: Won't DCP beans be created multiple times?
**A:** No! Spring Boot ensures beans are created only once per application context. See [ARCHITECTURE_RECOMMENDATIONS.md](./ARCHITECTURE_RECOMMENDATIONS.md) for detailed explanation.

### Q: How do I disable DCP?
**A:** Set `dcp.enabled=false` in your properties file.

### Q: Can I test modules independently?
**A:** Yes! Each module (catalog, negotiation, data-transfer) can be tested with full DCP functionality.

### Q: How do I verify it's working?
**A:** Check startup logs for `DcpHolderAutoConfiguration matched` or use the actuator `/beans` endpoint.

## Troubleshooting

**DCP beans not found:**
- Check `dcp.enabled` is not set to `false`
- Verify required properties are set
- Run `mvn clean install`

**Configuration not binding:**
- Ensure properties start with `dcp.` prefix
- Check for typos in property names

**MongoDB errors:**
- Verify MongoDB is running
- Check connection string

See [IMPLEMENTATION_SUMMARY.md](./IMPLEMENTATION_SUMMARY.md#troubleshooting) for more details.

## Build Status

✅ **Build verified:** `mvn clean compile` successful  
✅ **Autoconfiguration files:** Present in JAR  
✅ **Dependencies:** Added to catalog, negotiation, data-transfer  

## Next Steps

1. Run `mvn clean install` in the root directory
2. Configure DCP properties in `application.properties`
3. Start the connector application
4. Verify DCP beans are loaded
5. Inject and use DCP services in your code

## Architecture

```
connector (main application)
  ├── catalog → dcp-holder
  ├── negotiation → dcp-holder
  └── data-transfer → dcp-holder

Result:
  • ONE dcp-holder JAR on classpath
  • ONE set of DCP beans in Spring context
  • All modules share the same instances
  • ZERO duplication
```

See [ARCHITECTURE_DIAGRAMS.md](./ARCHITECTURE_DIAGRAMS.md) for detailed visual diagrams.

## Contributing

When adding new DCP services:
1. Annotate with `@Service`, `@Component`, or `@Repository`
2. Place in `it.eng.dcp` or `it.eng.dcp.common` package
3. They will be automatically discovered and registered

## License

See parent project license.

---

🎉 **The dcp-holder module is now fully Spring Boot autoconfigurable!**

For detailed information, start with [QUICKSTART.md](./QUICKSTART.md) or [IMPLEMENTATION_SUMMARY.md](./IMPLEMENTATION_SUMMARY.md).

