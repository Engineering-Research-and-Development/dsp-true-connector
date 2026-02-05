# Verification: Docker Environment Uses BaseDcpE2ETest Infrastructure

## ✅ CONFIRMED: DcpDockerE2ETest Uses Same Docker Build Logic

After reviewing the code, I can confirm that **`DcpDockerE2ETest` properly relies on the same Docker image building and container management logic** from `BaseDcpE2ETest`.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    BaseDcpE2ETest                               │
│  (Abstract base class - Docker infrastructure management)       │
│                                                                  │
│  @BeforeAll static void startContainers()                       │
│  ├── Build Maven modules (if needed)                            │
│  ├── Create Docker network                                      │
│  ├── Start MongoDB container                                    │
│  ├── createAdnRunHolderVerifierDockerEnvironment(dcpRoot)      │
│  │   ├── Find JAR: dcp-e2e-tests-exec.jar                      │
│  │   ├── Build Docker image from JAR                            │
│  │   ├── Create GenericContainer with MongoDB connection        │
│  │   └── Start container (port 8087)                            │
│  └── createAndRunIssuerDockerEnvironment(dcpRoot)              │
│      ├── Find JAR: dcp-issuer-exec.jar                          │
│      ├── Build Docker image from JAR                            │
│      ├── Create GenericContainer with MongoDB connection        │
│      └── Start container (port 8084)                            │
│                                                                  │
│  @AfterAll static void stopContainersAndCleanup()              │
│  └── Stop containers and cleanup Docker images                  │
│                                                                  │
│  Protected fields available to subclasses:                      │
│  - protected static GenericContainer<?> issuerContainer         │
│  - protected static GenericContainer<?> holderVerifierContainer│
│  - protected static MongoDBContainer mongoDBContainer          │
│  - protected static Network network                             │
└─────────────────────────────────────────────────────────────────┘
                              ▲
                              │ extends
                              │
┌─────────────────────────────┴───────────────────────────────────┐
│                    DcpDockerE2ETest                             │
│  (Concrete test class - uses containers from parent)            │
│                                                                  │
│  @BeforeEach void setupTestEnvironment()                        │
│  └── Create DockerTestEnvironment(                             │
│         issuerContainer,          ← from BaseDcpE2ETest         │
│         holderVerifierContainer)  ← from BaseDcpE2ETest         │
│                                                                  │
│  Delegates tests to AbstractDcpE2ETest via TestDelegate         │
└─────────────────────────────────────────────────────────────────┘
```

## How It Works

### 1. Test Execution Starts

When you run `mvn test -Dtest=DcpDockerE2ETest`:

```java
@BeforeAll static void startContainers() {
    // This runs ONCE before ANY tests in DcpDockerE2ETest
    // Called from BaseDcpE2ETest (parent class)
}
```

### 2. Docker Infrastructure Setup (BaseDcpE2ETest)

#### Step 1: Check/Build JAR Files
```java
Path issuerPath = dcpRoot.resolve("dcp-issuer");
if (!jarExists(issuerTargetPath, "dcp-issuer-exec.jar")) {
    buildModule(issuerPath, "Issuer");  // mvn clean package -DskipTests
}
```

#### Step 2: Build Holder+Verifier Docker Image
```java
private static void createAdnRunHolderVerifierDockerEnvironment(Path dcpRoot) {
    Path holderVerifierJar = findJarFile(
        holderVerifierPath.resolve("target"), 
        "dcp-e2e-tests-exec.jar"
    );
    
    ImageFromDockerfile holderVerifierImage = 
        new ImageFromDockerfile("dcp-holder-verifier-test-e2e", false)
            .withDockerfileFromBuilder(builder -> builder
                .from("eclipse-temurin:17-jre-alpine")
                .workDir("/app")
                .copy(jarRelativePath, "/app/app.jar")
                .copy("src/main/resources/eckey.p12", "/app/eckey.p12")
                .expose(8087)
                .entryPoint("sh", "-c", "java $JAVA_OPTS -jar /app/app.jar")
                .build())
            .withFileFromPath(jarRelativePath, holderVerifierJar)
            .withFileFromPath("src/main/resources/eckey.p12", ...);
    
    holderVerifierContainer = new GenericContainer<>(holderVerifierImage)
        .withNetwork(network)
        .withNetworkAliases("holder-verifier")
        .withExposedPorts(8087)
        .withEnv("SPRING_DATA_MONGODB_HOST", "mongodb")
        .withEnv("SERVER_PORT", "8087")
        .waitingFor(Wait.forLogMessage(".*Started.*Application.*", 1))
        .withReuse(false);
    
    holderVerifierContainer.start();
}
```

#### Step 3: Build Issuer Docker Image
```java
private static void createAndRunIssuerDockerEnvironment(Path dcpRoot) {
    Path issuerJar = findJarFile(
        issuerPath.resolve("target"), 
        "dcp-issuer-exec.jar"
    );
    
    ImageFromDockerfile issuerImage = 
        new ImageFromDockerfile("dcp-issuer-e2e-test", false)
            .withDockerfileFromBuilder(builder -> builder
                .from("eclipse-temurin:17-jre-alpine")
                .workDir("/app")
                .copy(jarRelativePath, "/app/dcp-issuer.jar")
                .copy("src/main/resources/eckey-issuer.p12", "/app/eckey-issuer.p12")
                .expose(8084)
                .entryPoint("sh", "-c", "java $JAVA_OPTS -jar /app/dcp-issuer.jar")
                .build())
            .withFileFromPath(jarRelativePath, issuerJar)
            .withFileFromPath("src/main/resources/eckey-issuer.p12", ...);
    
    issuerContainer = new GenericContainer<>(issuerImage)
        .withNetwork(network)
        .withNetworkAliases("issuer")
        .withExposedPorts(8084)
        .withEnv("SPRING_DATA_MONGODB_HOST", "mongodb")
        .withEnv("SERVER_PORT", "8084")
        .waitingFor(Wait.forLogMessage(".*Started IssuerApplication.*", 1))
        .withReuse(false);
    
    issuerContainer.start();
}
```

### 3. Test Setup (DcpDockerE2ETest)

Before each test method runs:

```java
@BeforeEach
void setupTestEnvironment() {
    // Wrap the containers from BaseDcpE2ETest
    DockerTestEnvironment environment = new DockerTestEnvironment(
        issuerContainer,           // ← Protected field from BaseDcpE2ETest
        holderVerifierContainer    // ← Protected field from BaseDcpE2ETest
    );
    testDelegate.setEnvironment(environment);
}
```

### 4. Test Execution

```java
@Test
void testIssuerDidDocumentIsAccessible() {
    // Delegate to AbstractDcpE2ETest which uses the environment
    testDelegate.testIssuerDidDocumentIsAccessible();
}
```

Inside the test:
```java
DcpTestEnvironment env = getEnvironment();  // Returns DockerTestEnvironment
RestTemplate client = env.getIssuerClient(); 

// DockerTestEnvironment gets the client like this:
// http://localhost:{issuerContainer.getMappedPort(8084)}
```

## Key Points

### ✅ Same Docker Build Logic
- `DcpDockerE2ETest` **extends** `BaseDcpE2ETest`
- Inherits `@BeforeAll startContainers()` which builds images
- Uses **exact same** methods:
  - `createAdnRunHolderVerifierDockerEnvironment()`
  - `createAndRunIssuerDockerEnvironment()`

### ✅ Same Container References
- `issuerContainer` and `holderVerifierContainer` are **protected static** fields
- Both `DcpDockerE2ETest` and other test classes extending `BaseDcpE2ETest` use the **same container instances**

### ✅ Single Docker Setup
- `@BeforeAll` runs **once per test class**
- All tests in `DcpDockerE2ETest` share the same Docker infrastructure
- Containers are reused across test methods (efficient)

### ✅ Proper Cleanup
- `@AfterAll stopContainersAndCleanup()` is inherited
- Stops containers and removes Docker images

## Docker Image Details

### Holder+Verifier Image
- **Name**: `dcp-holder-verifier-test-e2e`
- **Base**: `eclipse-temurin:17-jre-alpine`
- **JAR**: `dcp-e2e-tests/target/dcp-e2e-tests-exec.jar`
- **Keystore**: `dcp-e2e-tests/src/main/resources/eckey.p12`
- **Port**: 8087 (mapped to dynamic host port)
- **MongoDB**: Connected via network alias `mongodb`

### Issuer Image
- **Name**: `dcp-issuer-e2e-test`
- **Base**: `eclipse-temurin:17-jre-alpine`
- **JAR**: `dcp-issuer/target/dcp-issuer-exec.jar`
- **Keystore**: `dcp-issuer/src/main/resources/eckey-issuer.p12`
- **Port**: 8084 (mapped to dynamic host port)
- **MongoDB**: Connected via network alias `mongodb`

## Comparison with Other Docker Tests

### DcpCompleteFlowTestE2E
```java
class DcpCompleteFlowTestE2E extends BaseDcpE2ETest {
    // Uses same containers from BaseDcpE2ETest
    // Accesses via protected fields: issuerContainer, holderVerifierContainer
}
```

### DcpCredentialFlowTestE2E
```java
class DcpCredentialFlowTestE2E extends BaseDcpE2ETest {
    // Uses same containers from BaseDcpE2ETest
    // Accesses via protected fields: issuerContainer, holderVerifierContainer
}
```

### DcpDockerE2ETest (NEW)
```java
class DcpDockerE2ETest extends BaseDcpE2ETest {
    // Uses same containers from BaseDcpE2ETest
    // Wraps them in DockerTestEnvironment for abstraction layer
    // Delegates to AbstractDcpE2ETest for test logic
}
```

**All three use the EXACT SAME Docker infrastructure!**

## Container Lifecycle

```
Test Class Load
    ↓
@BeforeAll startContainers()
    ↓
┌─────────────────────────┐
│ Build Docker Images     │
│ - Holder+Verifier       │
│ - Issuer                │
└─────────────────────────┘
    ↓
┌─────────────────────────┐
│ Start Containers        │
│ - MongoDB               │
│ - Holder+Verifier:8087  │
│ - Issuer:8084           │
└─────────────────────────┘
    ↓
┌─────────────────────────┐
│ Test Method 1           │
│ @BeforeEach setup       │
│ → Run test              │
└─────────────────────────┘
    ↓
┌─────────────────────────┐
│ Test Method 2           │
│ @BeforeEach setup       │
│ → Run test              │
└─────────────────────────┘
    ↓
┌─────────────────────────┐
│ Test Method N           │
│ @BeforeEach setup       │
│ → Run test              │
└─────────────────────────┘
    ↓
@AfterAll stopContainersAndCleanup()
    ↓
┌─────────────────────────┐
│ Stop Containers         │
│ Remove Docker Images    │
└─────────────────────────┘
```

## Verification Checklist

- ✅ `DcpDockerE2ETest` extends `BaseDcpE2ETest`
- ✅ Uses inherited `@BeforeAll startContainers()`
- ✅ Uses inherited `@AfterAll stopContainersAndCleanup()`
- ✅ Accesses containers via protected fields (`issuerContainer`, `holderVerifierContainer`)
- ✅ Same Docker image build logic (no duplication)
- ✅ Same Testcontainers setup
- ✅ Same JAR file lookup logic
- ✅ Same network configuration
- ✅ Same MongoDB integration

## Conclusion

✅ **VERIFIED**: `DcpDockerE2ETest` properly relies on and uses the **exact same** Docker image building and container management logic from `BaseDcpE2ETest`.

**No code duplication** - The Docker infrastructure is centralized in `BaseDcpE2ETest` and **shared** by:
1. `DcpDockerE2ETest` (new runtime-agnostic tests)
2. `DcpCompleteFlowTestE2E` (existing smoke tests)
3. `DcpCredentialFlowTestE2E` (existing credential flow tests)

The abstraction layer (`DcpTestEnvironment`) is **purely for test portability** - it doesn't change how Docker containers are built or managed.
