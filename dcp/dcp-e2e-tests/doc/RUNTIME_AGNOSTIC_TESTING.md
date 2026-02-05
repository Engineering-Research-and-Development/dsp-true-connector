# Runtime-Agnostic E2E Testing Guide

## Overview

This guide explains how to write and run E2E tests that work across different runtime environments (Spring Boot vs Docker containers) without duplicating test logic.

## Architecture

The solution uses a **Test Environment Abstraction Pattern** with the following components:

### 1. Core Abstraction Layer

```
└── it.eng.dcp.e2e.common/
    ├── DcpTestEnvironment (interface)        - Runtime-agnostic contract
    ├── SpringTestEnvironment                 - Spring Boot implementation
    └── DockerTestEnvironment                 - Docker/Testcontainers implementation
```

### 2. Test Logic Layer

```
└── it.eng.dcp.e2e.tests/
    └── AbstractDcpE2ETest                    - Shared test logic (runtime-agnostic)
```

### 3. Runtime-Specific Test Classes

```
└── it.eng.dcp.e2e.spring/
    └── DcpSpringE2ETest                      - Runs tests in Spring Boot environment

└── it.eng.dcp.e2e.docker/
    └── DcpDockerE2ETest                      - Runs tests in Docker environment
```

## How It Works

### Step 1: Define Runtime-Agnostic Interface

`DcpTestEnvironment` provides a contract for accessing application endpoints:

```java
public interface DcpTestEnvironment {
    RestTemplate getIssuerClient();
    RestTemplate getHolderClient();
    RestTemplate getVerifierClient();
    String getIssuerDid();
    String getHolderDid();
    String getVerifierDid();
    // ... other methods
}
```

### Step 2: Write Tests Once

All test logic lives in `AbstractDcpE2ETest`:

```java
public abstract class AbstractDcpE2ETest {
    protected abstract DcpTestEnvironment getEnvironment();
    
    @Test
    public void testIssuerDidDocumentIsAccessible() {
        DcpTestEnvironment env = getEnvironment();
        RestTemplate issuerClient = env.getIssuerClient();
        
        ResponseEntity<DidDocument> response = 
            issuerClient.getForEntity("/.well-known/did.json", DidDocument.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        // ... assertions
    }
}
```

### Step 3: Run Tests in Different Environments

**Spring Boot Environment:**
```java
@Testcontainers
public class DcpSpringE2ETest extends AbstractDcpE2ETest {
    private static SpringTestEnvironment environment;
    
    @BeforeAll
    static void startApplications() {
        // Start Spring Boot apps on ports 8081, 8082
        environment = new SpringTestEnvironment(...);
    }
    
    @Override
    protected DcpTestEnvironment getEnvironment() {
        return environment;
    }
}
```

**Docker Environment:**
```java
public class DcpDockerE2ETest extends BaseDcpE2ETest {
    private final TestDelegate testDelegate = new TestDelegate();
    
    @BeforeEach
    void setupTestEnvironment() {
        DockerTestEnvironment environment = 
            new DockerTestEnvironment(issuerContainer, holderVerifierContainer);
        testDelegate.setEnvironment(environment);
    }
    
    // Delegate test methods
    @Test
    void testIssuerDidDocumentIsAccessible() {
        testDelegate.testIssuerDidDocumentIsAccessible();
    }
}
```

## Running Tests

### Using Maven Profiles

The project uses Maven Failsafe plugin with profiles to select test groups:

#### Run Spring Boot Tests Only
```bash
mvn verify -DtestGroup=spring
```

#### Run Docker Tests Only
```bash
mvn verify -DtestGroup=docker
```

#### Run All Tests
```bash
mvn verify -DtestGroup=all
```

### Profile Configuration (pom.xml)

```xml
<profiles>
    <profile>
        <id>spring-tests</id>
        <activation>
            <property>
                <name>testGroup</name>
                <value>spring</value>
            </property>
        </activation>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-failsafe-plugin</artifactId>
                    <configuration>
                        <skipITs>false</skipITs>
                        <includes>
                            <include>**/spring/**/*TestE2E.java</include>
                        </includes>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
    
    <profile>
        <id>docker-tests</id>
        <!-- Similar configuration for docker tests -->
    </profile>
</profiles>
```

## Adding New Tests

### Step 1: Add Test Method to AbstractDcpE2ETest

```java
@Test
public void testNewFeature() {
    DcpTestEnvironment env = getEnvironment();
    RestTemplate client = env.getIssuerClient();
    
    // Your test logic here - works for both Spring and Docker!
    String response = client.getForObject("/api/new-feature", String.class);
    assertNotNull(response);
}
```

### Step 2: Add Delegation in DcpDockerE2ETest

```java
@Test
void testNewFeature() {
    testDelegate.testNewFeature();
}
```

### Step 3: That's it!

- `DcpSpringE2ETest` automatically inherits the test (extends `AbstractDcpE2ETest`)
- `DcpDockerE2ETest` runs it via delegation
- Both use the same test logic, just different infrastructure

## Test Execution Flow

### Spring Boot Flow
```
1. DcpSpringE2ETest.startApplications()
   ├── Start MongoDB container
   ├── Start Holder+Verifier Spring app (port 8081)
   └── Start Issuer Spring app (port 8082)

2. Create SpringTestEnvironment
   └── Configure REST clients with localhost:8081, localhost:8082

3. Run inherited tests from AbstractDcpE2ETest
   └── Tests access apps via SpringTestEnvironment

4. DcpSpringE2ETest.stopApplications()
   └── Close Spring contexts and stop MongoDB
```

### Docker Flow
```
1. BaseDcpE2ETest.startContainers()
   ├── Build Docker images from JAR files
   ├── Start MongoDB container
   ├── Start Holder+Verifier container (dynamic port)
   └── Start Issuer container (dynamic port)

2. DcpDockerE2ETest.setupTestEnvironment()
   └── Create DockerTestEnvironment with container ports

3. Run tests via TestDelegate
   └── Tests access containers via DockerTestEnvironment

4. BaseDcpE2ETest.stopContainersAndCleanup()
   └── Stop containers and remove Docker images
```

## Benefits

### ✅ No Code Duplication
- Write test logic once in `AbstractDcpE2ETest`
- Reuse across Spring and Docker environments

### ✅ Easy to Add Tests
- Add method to `AbstractDcpE2ETest`
- Add delegation in `DcpDockerE2ETest`
- Done!

### ✅ Flexible Test Execution
- Choose runtime via Maven profile: `-DtestGroup=spring` or `-DtestGroup=docker`
- Different performance characteristics (Spring is faster, Docker is more realistic)

### ✅ Clean Separation of Concerns
- Test logic: `AbstractDcpE2ETest`
- Infrastructure: `DcpSpringE2ETest` / `BaseDcpE2ETest`
- Abstraction: `DcpTestEnvironment` implementations

## Example: Current Tests

All these tests work in **both** Spring and Docker environments:

1. ✅ `testIssuerDidDocumentIsAccessible()` - Verify Issuer serves DID document
2. ✅ `testHolderDidDocumentIsAccessible()` - Verify Holder serves DID document
3. ✅ `testVerifierDidDocumentIsAccessible()` - Verify Verifier serves DID document
4. ✅ `testAllDidsAreUnique()` - Verify all DIDs are unique

## Future Additions

You can easily add more tests:

```java
@Test
public void testCredentialIssuance() {
    DcpTestEnvironment env = getEnvironment();
    // Test credential flow
}

@Test
public void testPresentationVerification() {
    DcpTestEnvironment env = getEnvironment();
    // Test presentation flow
}
```

Just remember to add delegation methods in `DcpDockerE2ETest`!

## Troubleshooting

### Issue: "Cannot find method testXxx()"
**Solution:** Make sure the test method in `AbstractDcpE2ETest` is marked as `public`, not package-private.

### Issue: Tests not running with `-DtestGroup=spring`
**Solution:** Check that your test class ends with `*TestE2E.java` and is in the correct package (`spring` or `docker`).

### Issue: Docker tests fail to start
**Solution:** Ensure Docker Desktop is running and `BaseDcpE2ETest.startContainers()` completes successfully.

## Summary

This architecture provides a **clean, maintainable way** to write E2E tests that work across different deployment scenarios. Tests are written once and can be executed against:
- ✅ Spring Boot applications (fast, in-memory)
- ✅ Docker containers (production-like)
- ✅ Future: Kubernetes, cloud deployments, etc.

The abstraction layer (`DcpTestEnvironment`) makes it easy to add new runtime environments without touching existing test logic.
