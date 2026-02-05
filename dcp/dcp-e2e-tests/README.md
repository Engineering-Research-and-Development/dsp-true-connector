# BaseDcpE2ETest Refactoring - Testcontainers Implementation

## Summary

The `BaseDcpE2ETest` class has been successfully refactored to use **Testcontainers** with Docker for E2E testing. Each DCP application (Issuer, Holder, Verifier) now runs in its own isolated Docker container, providing a more realistic and reproducible test environment.

## Quick Start

### Prerequisites
- Docker Desktop running on your machine
- Maven installed
- Java 17

### Build and Test

```bash
# 1. Build all DCP modules (creates executable JARs)
cd C:\Users\davjovanov\Documents\work\dsp-true-connector\dcp
mvn clean install

# 2. Run E2E tests
cd dcp-e2e-tests
mvn test
```

## What Changed

### New Capabilities
1. **Isolated Containers** - Each service runs in its own Docker container
2. **Automatic Image Management** - Docker images are built and cleaned up automatically
3. **Network Isolation** - Services communicate over a Docker network
4. **Separate Verifier** - Verifier now runs in its own container (was combined with Holder before)

### Architecture

**Before:**
```
┌──────────────────────────────────────┐
│        Same JVM Process              │
│  ┌──────────┐    ┌────────────────┐ │
│  │  Issuer  │    │ Holder+Verifier│ │
│  │ Context  │    │    Context     │ │
│  └──────────┘    └────────────────┘ │
└──────────────────────────────────────┘
```

**After:**
```
┌─────────────────────────────────────────┐
│           Docker Environment             │
│  ┌─────┐  ┌──────┐  ┌─────────┐        │
│  │Issue│  │Holder│  │Verifier │        │
│  │  r  │  │      │  │         │        │
│  └──┬──┘  └───┬──┘  └────┬────┘        │
│     └─────────┴──────────┘             │
│              │                          │
│        ┌─────┴─────┐                   │
│        │  MongoDB  │                   │
│        └───────────┘                   │
└─────────────────────────────────────────┘
```

## Files Created

### Application Entry Points
- **`dcp-holder/src/main/java/it/eng/dcp/holder/HolderApplication.java`**
  - Main class for Holder service
  - Enables standalone execution

- **`dcp-verifier/src/main/java/it/eng/dcp/verifier/VerifierApplication.java`**
  - Main class for Verifier service
  - Enables standalone execution

### Docker Configuration
- **`dcp-holder/Dockerfile`**
  - Docker image for Holder service
  - Port 8085

- **`dcp-verifier/Dockerfile`**
  - Docker image for Verifier service
  - Port 8086

### Application Configuration
- **`dcp-holder/src/main/resources/application.properties`**
- **`dcp-verifier/src/main/resources/application.properties`**

### Documentation
- **`dcp-e2e-tests/REFACTORING_SUMMARY.md`** - This file
- **`dcp-e2e-tests/doc/E2E_TESTCONTAINERS_REFACTORING.md`** - Detailed technical documentation

## Files Modified

### Test Infrastructure
- **`BaseDcpE2ETest.java`** - Completely rewritten to use Testcontainers
- **`DcpCompleteFlowE2ETest.java`** - Updated to verify containers instead of contexts

### Build Configuration
- **`dcp-holder/pom.xml`** - Added Spring Boot plugin
- **`dcp-verifier/pom.xml`** - Added Spring Boot plugin

## Key Features

### ✅ Automatic Docker Image Building
```java
ImageFromDockerfile issuerImage = new ImageFromDockerfile("dcp-issuer-e2e-test", false)
    .withFileFromPath(".", issuerPath)
    .withDockerfile(issuerPath.resolve("Dockerfile"));

issuerContainer = new GenericContainer<>(issuerImage)
    .withNetwork(network)
    .withExposedPorts(8084)
    .waitingFor(Wait.forLogMessage(".*Started IssuerApplication.*", 1))
    .withReuse(false);
```

### ✅ Automatic Cleanup
```java
@AfterAll
static void stopContainersAndCleanup() {
    // Stop containers
    verifierContainer.stop();
    holderContainer.stop();
    issuerContainer.stop();
    mongoDBContainer.stop();
    
    // Remove Docker images
    for (String imageName : imagesToCleanup) {
        removeImageCmd(imageName).withForce(true).exec();
    }
    
    // Close network
    network.close();
}
```

### ✅ Dynamic Port Mapping
```java
@BeforeEach
void setupClients() {
    int issuerPort = issuerContainer.getMappedPort(8084);
    int holderPort = holderContainer.getMappedPort(8085);
    int verifierPort = verifierContainer.getMappedPort(8086);
    
    issuerClient = new RestTemplateBuilder()
        .rootUri("http://localhost:" + issuerPort)
        .build();
    // ... same for holder and verifier
}
```

## Testing

### Run All Tests
```bash
mvn test
```

### Run Specific Tests

#### Smoke Test (Container Startup + DID Documents)
```bash
mvn test -Dtest=DcpCompleteFlowE2ETest
```

This test verifies:
- ✅ All containers start successfully
- ✅ REST clients are initialized
- ✅ DID documents are accessible and valid
- ✅ DID structure matches expected format
- ✅ All DIDs are unique

#### Credential Flow Tests
```bash
mvn test -Dtest=DcpCredentialFlowE2ETest
```

This test suite includes:
- **DID Discovery Flow** - Tests complete DID document discovery from all parties
- **Issuer Metadata Discovery** - Tests issuer metadata endpoint and credential offerings
- **Verification Methods Validation** - Validates all verification methods in DID documents

### Expected Output
```
═══════════════════════════════════════════════
Starting DCP E2E Test Environment...
═══════════════════════════════════════════════
✓ Created Docker network
✓ MongoDB started on port: 55001
✓ Issuer started on port: 55002
✓ Holder started on port: 55003
✓ Verifier started on port: 55004
═══════════════════════════════════════════════
✓ All containers started successfully
═══════════════════════════════════════════════
...
✓✓✓ SMOKE TEST PASSED - E2E Infrastructure OK
═══════════════════════════════════════════════
...
Stopping containers and cleaning up...
✓ Verifier container stopped
✓ Holder container stopped
✓ Issuer container stopped
✓ MongoDB container stopped
✓ Network closed
✓ Removed Docker image: dcp-issuer-e2e-test
✓ Removed Docker image: dcp-holder-e2e-test
✓ Removed Docker image: dcp-verifier-e2e-test
═══════════════════════════════════════════════
✓ Cleanup complete
═══════════════════════════════════════════════
```

## Benefits

| Aspect | Before | After |
|--------|--------|-------|
| **Isolation** | Same JVM process | Separate Docker containers |
| **Network** | In-memory | Docker network |
| **Cleanup** | Manual context close | Automatic image removal |
| **Realism** | Test environment | Production-like environment |
| **Debugging** | Limited | Can inspect containers |
| **CI/CD** | JVM-based | Docker-based (portable) |

## Troubleshooting

### Problem: Tests fail with "Cannot connect to Docker"
**Solution:** Ensure Docker Desktop is running

### Problem: JARs not found during Docker build
**Solution:**
```bash
# Build from dcp directory
cd C:\Users\davjovanov\Documents\work\dsp-true-connector\dcp
mvn clean install -DskipTests
```

### Problem: Containers timeout during startup
**Solution:** Check Docker resources (memory, CPU) and application logs

### Problem: Images not cleaned up
**Solution:**
```bash
# Manually remove test images
docker rmi dcp-issuer-e2e-test dcp-holder-e2e-test dcp-verifier-e2e-test -f
```

## Migration Guide for Existing Tests

**Good news:** Existing tests work without changes! 

The refactoring maintains API compatibility:
- ✅ Same REST clients: `issuerClient`, `holderClient`, `verifierClient`
- ✅ Same helper methods: `getIssuerDid()`, `getHolderDid()`, `getVerifierDid()`
- ✅ Same base URLs: `getIssuerBaseUrl()`, `getHolderBaseUrl()`, `getVerifierBaseUrl()`

## For More Information

- **Detailed Documentation:** `doc/E2E_TESTCONTAINERS_REFACTORING.md`
- **Testcontainers:** https://www.testcontainers.org/
- **Spring Boot Docker:** https://spring.io/guides/gs/spring-boot-docker/

## Summary of Changes

✅ **Created:** 7 new files (applications, Dockerfiles, config)  
✅ **Modified:** 4 files (test classes, pom.xml files)  
✅ **Documented:** 2 comprehensive documentation files  
✅ **Tested:** Smoke test verifies all containers start successfully  
✅ **Automated:** Full image lifecycle management  

The refactoring is complete and ready for use! 🎉

