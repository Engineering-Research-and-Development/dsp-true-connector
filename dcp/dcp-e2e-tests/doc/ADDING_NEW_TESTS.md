# Quick Start: Adding a New Runtime-Agnostic Test

This guide shows you how to add a new E2E test that runs in both Spring and Docker environments.

## Step 1: Add Test to AbstractDcpE2ETest

Open `src/test/java/it/eng/dcp/e2e/tests/AbstractDcpE2ETest.java` and add your test method:

```java
/**
 * Test that verifies something important.
 */
@Test
public void testMyNewFeature() {
    DcpTestEnvironment env = getEnvironment();
    
    System.out.println("═══════════════════════════════════════════════");
    System.out.println("TEST: My New Feature Test");
    System.out.println("Environment: " + env.getEnvironmentName());
    System.out.println("═══════════════════════════════════════════════");
    
    // Get the appropriate REST client
    RestTemplate issuerClient = env.getIssuerClient();
    
    // Make your API call
    ResponseEntity<String> response = issuerClient.getForEntity(
        "/api/my-endpoint",
        String.class
    );
    
    // Add assertions
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    
    System.out.println("✓ Test passed!");
    System.out.println("═══════════════════════════════════════════════\n");
}
```

**Important:** The method MUST be `public` (not package-private) so it can be called from DcpDockerE2ETest.

## Step 2: Add Delegation in DcpDockerE2ETest

Open `src/test/java/it/eng/dcp/e2e/docker/DcpDockerE2ETest.java` and add a delegation method:

```java
@Test
void testMyNewFeature() {
    testDelegate.testMyNewFeature();
}
```

## Step 3: Done! No Changes Needed for Spring Tests

The `DcpSpringE2ETest` class extends `AbstractDcpE2ETest`, so it automatically inherits your new test. No code changes needed!

## Step 4: Run Your Test

### Run in Spring Boot environment:
```bash
mvn verify -DtestGroup=spring
```

### Run in Docker environment:
```bash
mvn verify -DtestGroup=docker
```

### Run in both environments:
```bash
mvn verify -DtestGroup=all
```

## Available Methods from DcpTestEnvironment

```java
DcpTestEnvironment env = getEnvironment();

// Get REST clients (pre-configured with base URLs)
RestTemplate issuerClient = env.getIssuerClient();
RestTemplate holderClient = env.getHolderClient();
RestTemplate verifierClient = env.getVerifierClient();

// Get base URLs
String issuerUrl = env.getIssuerBaseUrl();
String holderUrl = env.getHolderBaseUrl();
String verifierUrl = env.getVerifierBaseUrl();

// Get DIDs
String issuerDid = env.getIssuerDid();
String holderDid = env.getHolderDid();
String verifierDid = env.getVerifierDid();

// Get environment name
String envName = env.getEnvironmentName();
```

## Checklist for New Tests

- [ ] Test method is in `AbstractDcpE2ETest`
- [ ] Test method is marked `public`
- [ ] Test method has `@Test` annotation
- [ ] Test uses `getEnvironment()` to access apps
- [ ] Delegation method added to `DcpDockerE2ETest`
- [ ] Test runs successfully with `-DtestGroup=spring`
- [ ] Test runs successfully with `-DtestGroup=docker`
