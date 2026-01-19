# Architecture Recommendations: Avoiding Multiple DCP Instances

## Executive Summary

**Your current architecture is OPTIMAL!** ✅

Each module (catalog, negotiation, data-transfer) should explicitly declare the dcp-holder dependency. Spring Boot's autoconfiguration mechanism ensures that **DCP beans are created only once** in the application context, even though multiple modules depend on it.

## Current Architecture (RECOMMENDED)

```
connector (main Spring Boot application)
  ├── catalog → dcp-holder
  ├── negotiation → dcp-holder
  └── data-transfer → dcp-holder
```

### Why This Works

1. **Maven Dependency Resolution**
   - Maven automatically deduplicates transitive dependencies
   - Only ONE dcp-holder JAR ends up on the classpath
   - All three modules share the same JAR

2. **Single Application Context**
   - The connector module is the main Spring Boot application
   - It creates ONE ApplicationContext for the entire application
   - All modules (catalog, negotiation, data-transfer) are scanned into this single context

3. **Spring Boot Autoconfiguration**
   - Spring Boot processes each autoconfiguration class exactly once per context
   - `DcpHolderAutoConfiguration` is loaded only once
   - All beans defined/scanned by `DcpHolderAutoConfiguration` are created once

4. **Bean Uniqueness**
   - Spring ensures bean names are unique within the context
   - If a bean already exists, it won't be created again
   - `@ComponentScan` is idempotent - scanning the same package multiple times doesn't duplicate beans

### Benefits

✅ **Clear Dependency Declaration**
- Each module explicitly states what it needs
- No hidden dependencies
- Easy to understand what each module requires

✅ **Independent Testing**
- Each module can be tested with its declared dependencies
- No need for the main connector to run tests
- Mock/test configurations are module-specific

✅ **Proper Separation of Concerns**
- Catalog knows it needs DCP functionality
- Negotiation knows it needs DCP functionality
- Data-transfer knows it needs DCP functionality
- Dependencies are explicit, not implicit

✅ **Follows Maven Best Practices**
- Transitive dependencies are properly managed
- No dependency conflicts
- Clear dependency tree

✅ **Follows Spring Boot Best Practices**
- Autoconfiguration works as designed
- No manual bean configuration needed
- Configuration-driven behavior

## Alternative Approaches (NOT Recommended)

### ❌ Approach 1: Only Connector Depends on DCP

```
connector → dcp-holder
  ├── catalog (no dcp-holder dependency)
  ├── negotiation (no dcp-holder dependency)
  └── data-transfer (no dcp-holder dependency)
```

**Problems:**
1. **Hidden Dependencies:** Catalog/negotiation/data-transfer USE DCP but don't DECLARE it
2. **Testing Issues:** Cannot test modules independently with DCP functionality
3. **Violates Dependency Inversion:** Modules depend on something they don't declare
4. **Build Problems:** Module builds may succeed even if they break DCP integration
5. **Maintenance Nightmare:** Unclear which modules actually use DCP

**When to Consider:**
- Never. This is an anti-pattern.

### ❌ Approach 2: Create Shared-Services Module

```
connector
  ├── catalog → shared-services
  ├── negotiation → shared-services
  ├── data-transfer → shared-services
  └── shared-services → dcp-holder
```

**Problems:**
1. **Unnecessary Indirection:** Adds complexity without benefits
2. **God Module:** shared-services becomes a catch-all for everything
3. **Same Result:** Still results in single dcp-holder on classpath
4. **Extra Maintenance:** One more module to manage
5. **Unclear Purpose:** What else goes in shared-services?

**When to Consider:**
- Only if you have MANY shared libraries (10+) that ALL modules need
- Even then, prefer explicit dependencies

### ❌ Approach 3: Duplicate Code in Each Module

```
connector
  ├── catalog (has its own DCP code)
  ├── negotiation (has its own DCP code)
  └── data-transfer (has its own DCP code)
```

**Problems:**
1. **Code Duplication:** Nightmare to maintain
2. **Inconsistency:** Different modules may have different DCP behavior
3. **Bug Multiplication:** Fix a bug once, need to fix it three times
4. **Waste of Effort:** Why reinvent the wheel?

**When to Consider:**
- Never. This violates DRY principle.

## How Spring Prevents Duplicate Beans

### Scenario Analysis

Let's trace what happens when the connector application starts:

```java
@SpringBootApplication
@ComponentScan({"it.eng.catalog", "it.eng.negotiation", "it.eng.datatransfer"})
public class ConnectorApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConnectorApplication.class, args);
    }
}
```

**Step 1: Classpath Scanning**
- Spring Boot scans classpath for autoconfiguration classes
- Finds `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Registers `it.eng.dcp.autoconfigure.DcpHolderAutoConfiguration` (only once, even though dcp-holder JAR is referenced by 3 modules)

**Step 2: Autoconfiguration Execution**
- Spring evaluates `@ConditionalOnProperty` → passes (dcp.enabled=true)
- Loads `DcpProperties` configuration
- Imports `DCPMongoConfig`
- Adds component scan for `it.eng.dcp` and `it.eng.dcp.common` to the scan queue

**Step 3: Component Scanning**
- Spring scans all specified packages (including `it.eng.dcp`)
- For each `@Service`, `@Component`, `@Repository` found:
  - Checks if bean name already exists in context
  - If yes: skips (no duplication)
  - If no: creates bean definition
- Result: Each DCP service bean is created exactly once

**Step 4: Bean Instantiation**
- Spring creates beans based on dependency order
- Each bean is a singleton (default scope)
- All modules share the same bean instances

### Proof: Check Bean Count

You can verify there's no duplication:

```java
@SpringBootApplication
public class ConnectorApplication implements CommandLineRunner {
    
    @Autowired
    private ApplicationContext context;
    
    @Override
    public void run(String... args) {
        // Count HolderService beans (should be 1)
        String[] holderBeans = context.getBeanNamesForType(HolderService.class);
        System.out.println("HolderService bean count: " + holderBeans.length);
        
        // Count PresentationService beans (should be 1)
        String[] presentationBeans = context.getBeanNamesForType(PresentationService.class);
        System.out.println("PresentationService bean count: " + presentationBeans.length);
        
        // All modules get the same instance
        HolderService catalogHolder = context.getBean("holderService", HolderService.class);
        HolderService negotiationHolder = context.getBean("holderService", HolderService.class);
        System.out.println("Same instance? " + (catalogHolder == negotiationHolder)); // true
    }
}
```

## Configuration Recommendations

### Module-Specific Behavior (If Needed)

If you need different DCP behavior in different modules, use profiles or properties:

**Option 1: Conditional Beans with Profiles**

```java
@Service
@Profile("catalog")
public class CatalogSpecificDcpExtension {
    // Only loaded when 'catalog' profile is active
}

@Service
@Profile("negotiation")
public class NegotiationSpecificDcpExtension {
    // Only loaded when 'negotiation' profile is active
}
```

**Option 2: Configuration Properties**

```properties
# application.properties
dcp.catalog.enabled=true
dcp.negotiation.enabled=true
dcp.data-transfer.enabled=false
```

```java
@Service
@ConditionalOnProperty("dcp.catalog.enabled")
public class CatalogDcpFeature {
    // Only loaded if property is true
}
```

**Option 3: Qualifier-Based Injection**

```java
// In your service
@Service
public class CatalogService {
    
    @Autowired
    @Qualifier("holderService")
    private HolderService holderService; // Same instance everywhere
    
    // Or create module-specific wrapper
    @Bean
    public CatalogDcpService catalogDcpService(HolderService holderService) {
        return new CatalogDcpService(holderService);
    }
}
```

### Feature Flags

For granular control:

```properties
# application.properties
dcp.features.credential-issuance=true
dcp.features.presentation-validation=true
dcp.features.revocation-checking=true
```

```java
@Service
@ConditionalOnProperty(prefix = "dcp.features", name = "credential-issuance", havingValue = "true")
public class CredentialIssuanceClient {
    // Only loaded if feature is enabled
}
```

## Performance Considerations

### Startup Performance

**Current Architecture:**
- ✅ Single bean initialization
- ✅ Shared resources (MongoDB connections, HTTP clients)
- ✅ Minimal memory footprint

**If Beans Were Duplicated (hypothetical):**
- ❌ 3x initialization time
- ❌ 3x memory usage
- ❌ 3x database connections
- ❌ Potential state inconsistency

### Runtime Performance

**Current Architecture:**
- ✅ Single MongoDB connection pool
- ✅ Shared credential cache
- ✅ Consistent state across modules
- ✅ Lower memory pressure

## Monitoring & Observability

### Track Bean Usage

Add actuator to see which beans are loaded:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```properties
management.endpoints.web.exposure.include=beans,health,info
```

**Endpoint:** `http://localhost:8080/actuator/beans`

Look for DCP beans and verify there's only one of each.

### Logging

Add logging to verify single instance:

```java
@Service
@Slf4j
public class HolderService {
    
    @PostConstruct
    public void init() {
        log.info("HolderService initialized: {}", this);
    }
}
```

You should see this log line only ONCE during startup.

## Migration Path (If You Had Different Architecture)

If you previously had a different architecture, here's how to migrate:

### From "Only Connector Depends on DCP"

**Before:**
```xml
<!-- connector/pom.xml -->
<dependency>
    <groupId>it.eng</groupId>
    <artifactId>dcp-holder</artifactId>
</dependency>

<!-- catalog/pom.xml -->
<!-- No dcp-holder dependency -->
```

**After:**
```xml
<!-- catalog/pom.xml -->
<dependency>
    <groupId>it.eng</groupId>
    <artifactId>dcp-holder</artifactId>
    <version>${revision}</version>
</dependency>
```

**Steps:**
1. Add dcp-holder to catalog/negotiation/data-transfer pom.xml
2. Remove any manual `@Import` of DCP configuration classes
3. Remove any manual `@ComponentScan` for dcp packages
4. Rely on autoconfiguration
5. Test each module independently

### From "Manual Configuration"

**Before:**
```java
@Configuration
@ComponentScan("it.eng.dcp")
public class CatalogConfiguration {
    // Manual bean definitions
}
```

**After:**
```xml
<!-- Just add dependency - remove manual configuration -->
<dependency>
    <groupId>it.eng</groupId>
    <artifactId>dcp-holder</artifactId>
    <version>${revision}</version>
</dependency>
```

**Steps:**
1. Delete manual configuration classes
2. Remove `@Import` annotations
3. Add dcp-holder dependency
4. Configure via properties
5. Test

## Frequently Asked Questions

### Q: Won't Maven create multiple dcp-holder JARs?
**A:** No. Maven resolves transitive dependencies and deduplicates. Only one JAR on classpath.

### Q: What if different modules need different versions of dcp-holder?
**A:** Maven enforces version consistency. If there's a conflict, it picks one version (usually latest). Use `<dependencyManagement>` to control versions.

### Q: How can I verify beans aren't duplicated?
**A:** 
1. Check startup logs (should see each bean created once)
2. Use actuator `/beans` endpoint
3. Add `@PostConstruct` logging to beans
4. Use debugger to check object references

### Q: What about transaction boundaries?
**A:** All modules share the same transaction manager. DCP operations from any module participate in the same transaction context.

### Q: Can I disable DCP for specific modules?
**A:** Use profiles or conditional beans. The core DCP beans are still singleton, but module-specific features can be toggled.

### Q: What about thread safety?
**A:** Spring beans are singleton by default. DCP services must be thread-safe (and they are, using proper synchronization and immutable data).

## Conclusion

**Your current architecture is the recommended approach!**

✅ **Keep doing:**
- Each module explicitly depends on dcp-holder
- Use Spring Boot autoconfiguration
- Configure via properties
- Trust Spring's bean management

❌ **Don't do:**
- Create intermediate "shared" modules
- Manually configure DCP beans
- Put DCP dependency only in connector
- Worry about duplication (Spring prevents it)

🎯 **Key Takeaway:**
> Spring Boot autoconfiguration + Maven dependency management = Zero duplication, maximum clarity

The perceived "risk" of multiple instances is actually a feature, not a bug. Multiple modules declaring the same dependency makes the architecture **clearer** and **more maintainable**, while Spring ensures there's still only **one shared instance** at runtime.

