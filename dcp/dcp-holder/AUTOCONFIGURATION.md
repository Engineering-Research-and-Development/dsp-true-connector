# DCP-Holder Spring Boot Autoconfiguration

## Overview

The `dcp-holder` module has been configured as a Spring Boot autoconfiguration module. This allows it to be automatically discovered and configured when included as a dependency in other modules (catalog, negotiation, data-transfer, connector).

## Implementation Details

### 1. Autoconfiguration Files

Two autoconfiguration descriptor files have been created:

- **`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`** (Spring Boot 3.x format)
- **`META-INF/spring.factories`** (Spring Boot 2.x format - for backward compatibility)

Both files register the `it.eng.dcp.autoconfigure.DcpHolderAutoConfiguration` class.

### 2. DcpHolderAutoConfiguration Class

The existing `DcpHolderAutoConfiguration` class provides:

- **Conditional Loading**: `@ConditionalOnProperty(prefix = "dcp", name = "enabled", havingValue = "true", matchIfMissing = true)`
  - The module is enabled by default
  - Can be disabled by setting `dcp.enabled=false` in application properties
- **Component Scanning**: Scans `it.eng.dcp` and `it.eng.dcp.common` packages
- **Configuration Properties**: Enables `DcpProperties` binding
- **MongoDB Configuration**: Imports `DCPMongoConfig`

### 3. Dependencies Added

The `dcp-holder` dependency has been added to:
- ✅ catalog/pom.xml
- ✅ negotiation/pom.xml
- ✅ data-transfer/pom.xml

The connector module already transitively includes dcp-holder through these modules.

### 4. Spring Boot Autoconfigure Processor

Added `spring-boot-autoconfigure-processor` to dcp-holder/pom.xml as an optional dependency for proper metadata generation.

## How It Works

1. When any module (catalog, negotiation, data-transfer) starts up, Spring Boot's autoconfiguration mechanism scans the classpath
2. It discovers the `AutoConfiguration.imports` file in the dcp-holder JAR
3. Spring loads `DcpHolderAutoConfiguration` which:
   - Checks if `dcp.enabled` property is true (default)
   - Scans for `@Service`, `@Component`, `@Repository` classes in `it.eng.dcp` packages
   - Registers all DCP beans in the Spring context
4. All DCP services become available for dependency injection in the consuming modules

## Avoiding Multiple Bean Instances

### Problem

Since the connector module depends on catalog, negotiation, and data-transfer, and all three now depend on dcp-holder, you might be concerned about multiple instances of DCP beans being created.

### Solution - How Spring Handles This

**Spring Boot's autoconfiguration mechanism ensures that beans are created only ONCE per application context**, even if multiple modules depend on the same autoconfiguration module. Here's why:

1. **Single Application Context**: The connector module (main application) creates a single Spring ApplicationContext
2. **ClassPath Deduplication**: Maven automatically deduplicates transitive dependencies. Even though catalog, negotiation, and data-transfer all depend on dcp-holder, only one dcp-holder JAR is on the classpath
3. **Single Autoconfiguration Execution**: Spring Boot processes each autoconfiguration class only once per application context
4. **Bean Name Uniqueness**: Spring ensures bean names are unique within the context

### Architecture Recommendation: Current Approach is CORRECT ✅

Your current architecture is actually the **recommended approach**:

```
connector (main application)
  ├── catalog → dcp-holder
  ├── negotiation → dcp-holder
  └── data-transfer → dcp-holder
```

**Benefits:**
- ✅ Each module explicitly declares its dependencies
- ✅ Modules can be tested independently with DCP functionality
- ✅ Clear dependency graph
- ✅ Maven handles deduplication automatically
- ✅ Spring creates beans only once

### Alternative Approaches (NOT Recommended)

#### ❌ Approach 1: Only connector depends on dcp-holder
```
connector → dcp-holder
  ├── catalog
  ├── negotiation
  └── data-transfer
```
**Problems:**
- Catalog/negotiation/data-transfer can't be tested independently
- Hidden dependency - modules use DCP but don't declare it
- Violates dependency inversion principle

#### ❌ Approach 2: Create a shared-services module
```
connector
  ├── catalog
  ├── negotiation
  ├── data-transfer
  └── shared-services → dcp-holder
```
**Problems:**
- Unnecessary indirection
- Shared-services becomes a "god module"
- Still doesn't solve any problem (same classpath behavior)

## Configuration

### Enabling/Disabling DCP Module

The DCP module is enabled by default. To disable it:

```properties
# application.properties
dcp.enabled=false
```

### Per-Environment Configuration

You can use Spring profiles to control DCP behavior:

```yaml
# application-dev.yml
dcp:
  enabled: true
  connector-did: did:web:localhost:connector
  
# application-prod.yml
dcp:
  enabled: true
  connector-did: did:web:production.example.com:connector
```

### Module-Specific Configuration

If you need module-specific behavior, use Spring profiles:

```java
@Service
@Profile("catalog")
public class CatalogSpecificDcpService {
    // Only loaded when 'catalog' profile is active
}
```

## Testing

### Testing Individual Modules

Each module (catalog, negotiation, data-transfer) can now be tested with full DCP functionality:

```java
@SpringBootTest
@AutoConfigureMockMvc
class CatalogServiceTest {
    
    @Autowired
    private HolderService holderService; // DCP bean automatically available
    
    @Autowired
    private CatalogService catalogService; // Catalog bean
    
    @Test
    void testCatalogWithDcp() {
        // Test catalog functionality that uses DCP services
    }
}
```

### Disabling DCP in Tests

If you want to test without DCP:

```java
@SpringBootTest(properties = {"dcp.enabled=false"})
class CatalogServiceWithoutDcpTest {
    // DCP beans won't be loaded
}
```

## Verification

To verify the setup is working:

1. **Check Autoconfiguration Report**:
   ```properties
   # application.properties
   debug=true
   ```
   Look for `DcpHolderAutoConfiguration` in the startup logs

2. **List All Beans**:
   ```java
   @SpringBootApplication
   public class ConnectorApplication {
       public static void main(String[] args) {
           ConfigurableApplicationContext ctx = SpringApplication.run(ConnectorApplication.class, args);
           String[] beanNames = ctx.getBeanDefinitionNames();
           Arrays.stream(beanNames)
               .filter(name -> name.contains("dcp") || name.contains("Dcp"))
               .forEach(System.out::println);
       }
   }
   ```

3. **Actuator Endpoint** (if enabled):
   ```bash
   curl http://localhost:8080/actuator/beans | grep -i dcp
   ```

## Troubleshooting

### Issue: DCP beans not found

**Solution**: Ensure:
- `dcp.enabled` is not explicitly set to `false`
- Required configuration properties are set (e.g., `dcp.connector-did`)
- Check for classpath issues: `mvn dependency:tree`

### Issue: Multiple bean definitions conflict

**Solution**: This should not happen, but if it does:
- Check for explicit `@ComponentScan` in your main application that might be scanning dcp packages
- Ensure you're not manually creating DCP beans with `@Bean` methods

### Issue: Properties not binding

**Solution**: Ensure:
- `@EnableConfigurationProperties(DcpProperties.class)` is present in `DcpHolderAutoConfiguration`
- Properties file has correct prefix: `dcp.*`

## Migration Guide

If you had manual bean configuration before:

### Before (Manual Configuration)
```java
@Configuration
@ComponentScan("it.eng.dcp")
public class CatalogConfiguration {
    // Manual DCP bean configuration
}
```

### After (Autoconfiguration)
```java
// Just add dependency - remove manual configuration
// DCP beans are automatically available
```

## Summary

✅ **What was done:**
- Created Spring Boot autoconfiguration descriptors
- Added `spring-boot-autoconfigure-processor` to dcp-holder
- Added dcp-holder dependency to catalog, negotiation, and data-transfer modules

✅ **What you get:**
- Automatic DCP bean registration across all modules
- Single instance of DCP beans in the application context
- Easy enable/disable via configuration
- Independent module testing capability
- Clean dependency declaration

✅ **What to do next:**
1. Run `mvn clean install` to rebuild all modules
2. Verify DCP beans are available in connector application
3. Test catalog, negotiation, and data-transfer independently
4. Remove any manual DCP bean configuration if present

## Best Practices

1. **Always declare explicit dependencies**: Each module using DCP should declare it in pom.xml
2. **Use configuration properties**: Control DCP behavior via `application.properties` rather than code
3. **Leverage profiles**: Use Spring profiles for environment-specific configurations
4. **Test independently**: Each module should be testable with its declared dependencies
5. **Monitor startup**: Use `debug=true` initially to verify autoconfiguration

## Additional Improvements (Optional)

### 1. Conditional Bean Creation

For advanced scenarios, you can make individual beans conditional:

```java
@Service
@ConditionalOnProperty(prefix = "dcp.features", name = "credential-issuance", havingValue = "true")
public class CredentialIssuanceClient {
    // Only loaded when feature is enabled
}
```

### 2. Custom Starters

For even more modularity, consider creating custom Spring Boot starters:
- `dcp-holder-spring-boot-starter`
- `dcp-verifier-spring-boot-starter`
- `dcp-issuer-spring-boot-starter`

### 3. Actuator Health Checks

Add DCP health indicators:

```java
@Component
public class DcpHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // Check DCP subsystem health
        return Health.up()
            .withDetail("did", dcpProperties.getConnectorDid())
            .build();
    }
}
```

## References

- [Spring Boot Autoconfiguration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration)
- [Creating Your Own Auto-configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration.custom-starter)
- [Configuration Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config.typesafe-configuration-properties)

