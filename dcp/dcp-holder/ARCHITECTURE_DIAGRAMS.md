# DCP-Holder Autoconfiguration Architecture Diagram

## Module Dependency Graph

```
┌─────────────────────────────────────────────────────────────────┐
│                    Connector Module                              │
│                  (Spring Boot Application)                       │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │          Single Spring ApplicationContext                │   │
│  │                                                           │   │
│  │  ┌─────────────────────────────────────────────────┐   │   │
│  │  │         DCP Beans (created once)                 │   │   │
│  │  │  • HolderService                                 │   │   │
│  │  │  • PresentationService                           │   │   │
│  │  │  • DcpCredentialService                          │   │   │
│  │  │  • CredentialIssuanceClient                      │   │   │
│  │  │  • ... (all other DCP beans)                     │   │   │
│  │  └─────────────────────────────────────────────────┘   │   │
│  │                      ▲  ▲  ▲                             │   │
│  │                      │  │  │                             │   │
│  │        ┌─────────────┴──┴──┴────────────┐              │   │
│  │        │  Shared by all modules           │              │   │
│  │        └─────────────┬──┬──┬────────────┘              │   │
│  │                      │  │  │                             │   │
│  │   ┌──────────────────┘  │  └──────────────────┐        │   │
│  │   │                     │                      │        │   │
│  │   ▼                     ▼                      ▼        │   │
│  │ ┌────────┐          ┌────────┐          ┌────────┐    │   │
│  │ │Catalog │          │Negotia-│          │  Data  │    │   │
│  │ │ Module │          │  tion  │          │Transfer│    │   │
│  │ │        │          │ Module │          │ Module │    │   │
│  │ └────────┘          └────────┘          └────────┘    │   │
│  │                                                          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

Legend:
━━━━  Contains/Runs
────  Depends on
▲▼    Injects/Uses
```

## Maven Dependency Tree

```
trueconnector (parent)
│
├── dcp (parent)
│   ├── dcp-common
│   └── dcp-holder
│       ├── depends on: dcp-common
│       └── contains: DcpHolderAutoConfiguration
│
├── catalog
│   ├── depends on: tools
│   └── depends on: dcp-holder ──┐
│                                 │
├── negotiation                   │  All three modules
│   ├── depends on: tools         │  depend on dcp-holder
│   └── depends on: dcp-holder ───┤  but Maven creates only
│                                 │  ONE dcp-holder.jar
├── data-transfer                 │  on the classpath
│   ├── depends on: tools         │
│   └── depends on: dcp-holder ───┘
│
└── connector (main application)
    ├── depends on: catalog ────────┐
    ├── depends on: negotiation ────┼─→ Transitively includes dcp-holder
    └── depends on: data-transfer ──┘
```

## Spring Boot Startup Sequence

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. Application Starts                                            │
│    SpringApplication.run(ConnectorApplication.class, args)       │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. Classpath Scanning                                            │
│    • Scans for META-INF/spring/*.AutoConfiguration.imports      │
│    • Finds dcp-holder.jar                                        │
│    • Reads: it.eng.dcp.holder.autoconfigure.DcpHolderAutoConfiguration       │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. Load DcpHolderAutoConfiguration                                     │
│    @Configuration                                                │
│    @ConditionalOnProperty(name="dcp.enabled", havingValue="true")│
│    @ComponentScan({"it.eng.dcp", "it.eng.dcp.common"})         │
│                                                                  │
│    Condition check:                                              │
│    ✓ dcp.enabled = true (from application.properties)           │
│    ✓ Proceed with configuration                                 │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. Component Scanning                                            │
│    Scans packages:                                               │
│    • it.eng.dcp.service.*                                        │
│    • it.eng.dcp.repository.*                                     │
│    • it.eng.dcp.rest.*                                           │
│    • it.eng.dcp.config.*                                         │
│    • it.eng.dcp.common.*                                         │
│                                                                  │
│    Finds all @Service, @Component, @Repository, @RestController │
│    Creates bean definitions (NO DUPLICATES)                      │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. Bean Instantiation                                            │
│    Creates beans in dependency order:                            │
│    1. Configuration beans (DcpProperties, etc.)                  │
│    2. Repository beans                                           │
│    3. Service beans                                              │
│    4. Controller beans                                           │
│                                                                  │
│    All beans are SINGLETONS (one instance per context)          │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. Dependency Injection                                          │
│    Catalog services    ─┐                                        │
│    Negotiation services├──→ @Autowired HolderService            │
│    Data transfer services─┘    (same instance for all)          │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│ 7. Application Ready                                             │
│    All modules can use DCP beans                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Bean Creation and Sharing

```
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Application Context                    │
│                                                                   │
│  Bean Registry (Single Instance Per Bean Name)                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                                                           │   │
│  │  "holderService" ──────────┐                            │   │
│  │                             │                            │   │
│  │  [HolderService@abc123] ◄──┴─── Single instance         │   │
│  │         ▲     ▲     ▲                                    │   │
│  │         │     │     │                                    │   │
│  │         │     │     └──── Data Transfer Service         │   │
│  │         │     └────────── Negotiation Service           │   │
│  │         └──────────────── Catalog Service               │   │
│  │                                                           │   │
│  │  "presentationService" ───┐                              │   │
│  │                            │                             │   │
│  │  [PresentationService@def456] ◄─ Single instance        │   │
│  │         ▲     ▲     ▲                                    │   │
│  │         │     │     │                                    │   │
│  │         │     │     └──── Data Transfer Service         │   │
│  │         │     └────────── Negotiation Service           │   │
│  │         └──────────────── Catalog Service               │   │
│  │                                                           │   │
│  │  ... (all other DCP beans follow the same pattern)       │   │
│  │                                                           │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘

Key Points:
✓ One bean registry per application context
✓ Bean names are unique within the registry
✓ Each bean is instantiated only once (singleton scope)
✓ All modules get references to the same instances
✓ Thread-safe singleton management by Spring
```

## Autoconfiguration File Structure

```
dcp-holder.jar
│
├── it/eng/dcp/
│   ├── autoconfigure/
│   │   └── DcpHolderAutoConfiguration.class
│   ├── service/
│   │   ├── HolderService.class
│   │   ├── PresentationService.class
│   │   └── ... (other services)
│   ├── repository/
│   │   ├── VerifiableCredentialRepository.class
│   │   └── ... (other repositories)
│   ├── rest/
│   │   ├── DcpController.class
│   │   └── ... (other controllers)
│   └── config/
│       ├── DcpProperties.class
│       └── ... (other config classes)
│
└── META-INF/
    ├── spring.factories
    │   └── org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
    │       it.eng.dcp.holder.autoconfigure.DcpHolderAutoConfiguration
    │
    └── spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
            └── it.eng.dcp.holder.autoconfigure.DcpHolderAutoConfiguration

Spring Boot reads these files and automatically loads DcpHolderAutoConfiguration!
```

## Configuration Flow

```
application.properties
        │
        │  dcp.enabled=true
        │  dcp.connector-did=did:web:example.com
        │  dcp.base-url=https://example.com
        │  ... (other properties)
        │
        ▼
┌─────────────────────────────────────┐
│    @ConfigurationProperties         │
│    DcpProperties                    │
│    ├── enabled: true                │
│    ├── connectorDid: "did:web..."  │
│    ├── baseUrl: "https://..."      │
│    └── ... (other fields)           │
└──────────────┬──────────────────────┘
               │
               │ Injected into
               ▼
┌─────────────────────────────────────┐
│    DcpHolderAutoConfiguration             │
│    ├── Checks @ConditionalOnProperty│
│    ├── Enables configuration        │
│    └── Triggers component scan       │
└──────────────┬──────────────────────┘
               │
               │ Scans and registers
               ▼
┌─────────────────────────────────────┐
│    DCP Service Beans                │
│    ├── HolderService                │
│    ├── PresentationService          │
│    └── ... (all other beans)        │
└──────────────┬──────────────────────┘
               │
               │ Injected into
               ▼
┌─────────────────────────────────────┐
│    Application Services             │
│    ├── CatalogService               │
│    ├── NegotiationService           │
│    └── DataTransferService          │
└─────────────────────────────────────┘
```

## Why No Bean Duplication?

```
Scenario: Three modules depend on dcp-holder

┌────────────┐  ┌────────────┐  ┌────────────┐
│  Catalog   │  │ Negotiation│  │    Data    │
│            │  │            │  │  Transfer  │
└─────┬──────┘  └─────┬──────┘  └─────┬──────┘
      │               │               │
      │ depends on    │ depends on    │ depends on
      │               │               │
      └───────────────┴───────────────┘
                      │
                      ▼
              ┌──────────────┐
              │  dcp-holder  │
              └──────────────┘

Maven Resolution:
  Step 1: Catalog     → dcp-holder:0.6.4-SNAPSHOT
  Step 2: Negotiation → dcp-holder:0.6.4-SNAPSHOT (already on classpath, skip)
  Step 3: DataTransfer→ dcp-holder:0.6.4-SNAPSHOT (already on classpath, skip)
  
  Result: ONE dcp-holder-0.6.4-SNAPSHOT.jar on classpath

Spring Boot:
  Step 1: Read META-INF/spring/*.AutoConfiguration.imports from ALL jars
  Step 2: Collect unique autoconfiguration classes
          DcpHolderAutoConfiguration appears once (even though found in one jar)
  Step 3: Load each autoconfiguration class ONCE
  Step 4: Execute @ComponentScan ONCE per package
  Step 5: Register beans with unique names
          If "holderService" already exists → skip
          If "holderService" doesn't exist → create
  
  Result: ONE instance of each DCP bean in the Spring context

All Modules Share:
  ┌─────────────────────────────────────────┐
  │  ApplicationContext                     │
  │  ┌────────────────────────────────────┐│
  │  │ Bean: holderService                ││
  │  │   Instance: HolderService@abc123   ││ ← Same instance
  │  │   Referenced by:                   ││   for all modules
  │  │   • CatalogService                 ││
  │  │   • NegotiationService             ││
  │  │   • DataTransferService            ││
  │  └────────────────────────────────────┘│
  └─────────────────────────────────────────┘
```

## Comparison with Manual Configuration

### ❌ Before (Manual Configuration)

```
CatalogConfiguration.java:
  @Configuration
  @ComponentScan("it.eng.dcp")  ← Manual scan
  public class CatalogConfiguration {
      // Manual bean definitions
  }

NegotiationConfiguration.java:
  @Configuration
  @ComponentScan("it.eng.dcp")  ← Manual scan
  public class NegotiationConfiguration {
      // Manual bean definitions
  }

DataTransferConfiguration.java:
  @Configuration
  @ComponentScan("it.eng.dcp")  ← Manual scan
  public class DataTransferConfiguration {
      // Manual bean definitions
  }

Problems:
  • Manual configuration in each module
  • Risk of inconsistent configuration
  • Harder to test
  • More boilerplate code
```

### ✅ After (Autoconfiguration)

```
No manual configuration needed!

Just add dependency:
  <dependency>
      <groupId>it.eng</groupId>
      <artifactId>dcp-holder</artifactId>
  </dependency>

Spring Boot automatically:
  1. Discovers DcpHolderAutoConfiguration
  2. Loads configuration
  3. Scans for beans
  4. Registers beans
  5. Makes them available

Benefits:
  ✓ Zero boilerplate
  ✓ Consistent configuration
  ✓ Easy to test
  ✓ Can be disabled via properties
  ✓ Follows Spring Boot conventions
```

## Testing Architecture

```
┌────────────────────────────────────────────────────────────────┐
│  Individual Module Tests (e.g., Catalog)                        │
│                                                                  │
│  @SpringBootTest                                                 │
│  class CatalogServiceTest {                                      │
│                                                                  │
│      @Autowired                                                  │
│      HolderService holderService; ◄──── DCP bean available!     │
│                                                                  │
│      @Autowired                                                  │
│      CatalogService catalogService; ◄─ Module's own service     │
│                                                                  │
│      @Test                                                       │
│      void testWithDcp() {                                        │
│          // Test catalog functionality that uses DCP             │
│          assertNotNull(holderService);                           │
│          catalogService.processDataset();                        │
│      }                                                           │
│  }                                                               │
│                                                                  │
│  Spring Boot Test Context:                                      │
│  ┌─────────────────────────────────────────────────┐           │
│  │ • Catalog beans                                  │           │
│  │ • DCP beans (via autoconfiguration)             │           │
│  │ • Test-specific beans                            │           │
│  └─────────────────────────────────────────────────┘           │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│  Module Tests Without DCP                                        │
│                                                                  │
│  @SpringBootTest(properties = {"dcp.enabled=false"})            │
│  class CatalogServiceWithoutDcpTest {                            │
│                                                                  │
│      @Autowired                                                  │
│      CatalogService catalogService; ◄─ Only module beans        │
│                                                                  │
│      // holderService NOT available (DCP disabled)              │
│                                                                  │
│      @Test                                                       │
│      void testWithoutDcp() {                                     │
│          // Test catalog functionality without DCP               │
│          catalogService.someNonDcpMethod();                      │
│      }                                                           │
│  }                                                               │
│                                                                  │
│  Spring Boot Test Context:                                      │
│  ┌─────────────────────────────────────────────────┐           │
│  │ • Catalog beans                                  │           │
│  │ • NO DCP beans (disabled)                       │           │
│  │ • Test-specific beans                            │           │
│  └─────────────────────────────────────────────────┘           │
└────────────────────────────────────────────────────────────────┘
```

## Summary Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    KEY INSIGHTS                                  │
│                                                                   │
│  1. Maven: Multiple dependencies → ONE JAR on classpath          │
│  2. Spring Boot: ONE autoconfiguration execution                 │
│  3. Spring: ONE ApplicationContext with unique beans             │
│  4. Result: ZERO duplication, MAXIMUM sharing                    │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  3 modules depend on dcp-holder                          │   │
│  │              ↓                                            │   │
│  │  1 dcp-holder JAR on classpath                          │   │
│  │              ↓                                            │   │
│  │  1 DcpHolderAutoConfiguration execution                        │   │
│  │              ↓                                            │   │
│  │  1 set of DCP beans in context                          │   │
│  │              ↓                                            │   │
│  │  All modules share the same instances                    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                   │
│  ✅ Your architecture is OPTIMAL                                 │
│  ✅ No changes needed                                            │
│  ✅ Spring handles everything correctly                          │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

