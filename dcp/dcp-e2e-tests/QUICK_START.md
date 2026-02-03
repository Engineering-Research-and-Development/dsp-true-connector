# E2E Test Quick Reference

## 🚀 Quick Start

### Run the E2E Test
```bash
# From project root
mvn test -pl dcp/dcp-e2e-tests -Dtest=HolderVerifierE2ETest

# From module directory
cd dcp/dcp-e2e-tests
mvn test -Dtest=HolderVerifierE2ETest
```

### What Gets Tested ✅

1. **MongoDB 7.0.12 Container**
   - Container starts successfully
   - Correct version verification
   - Port mapping works
   - Connection established

2. **HolderVerifierTestApplication**
   - Spring Boot app starts on random port
   - MongoDB connection successful
   - All beans loaded correctly

3. **DID Document Endpoints**
   - ✅ `GET http://localhost:{port}/holder/did.json` → 200 OK + Valid JSON
   - ✅ `GET http://localhost:{port}/verifier/did.json` → 200 OK + Valid JSON

## 📊 Test Results

```
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 ✅
BUILD SUCCESS ✅
```

## 🗂️ Files Created

| File | Description |
|------|-------------|
| `HolderVerifierE2ETest.java` | Main E2E test class |
| `application-test.properties` | Test configuration |
| `logback-test.xml` | Test logging config |
| `eckey.p12` | Keystore (copied) |
| `E2E_TEST_DOCUMENTATION.md` | Full documentation |
| `E2E_TEST_IMPLEMENTATION_SUMMARY.md` | Implementation details |

## 🔧 Prerequisites

- ✅ Docker running (Desktop or Engine)
- ✅ Java 17+
- ✅ Maven 3.6+

## 🏗️ Architecture

```
┌─────────────────────────────────────┐
│   HolderVerifierE2ETest (JUnit 5)  │
└─────────────────┬───────────────────┘
                  │
        ┌─────────┴─────────┐
        │                   │
┌───────▼──────┐   ┌────────▼────────┐
│ MongoDB 7.0.12│   │ Spring Boot App │
│ (Testcontainer)   │ (Random Port)   │
└───────────────┘   └─────────────────┘
        │                   │
        └────── Connected ──┘
```

## 📝 Test Methods

| Test | Purpose |
|------|---------|
| `testMongoDBContainerIsRunning()` | Verify container startup |
| `testApplicationStartup()` | Verify app starts |
| `testBothServicesAreRunning()` | Verify integration |
| `testHolderDidDocumentEndpoint()` | Test holder DID endpoint |
| `testVerifierDidDocumentEndpoint()` | Test verifier DID endpoint |
| `testMongoDBConnection()` | Verify DB connectivity |

## 🐛 Troubleshooting

### Docker not running?
```bash
# Start Docker Desktop
# Then retry test
```

### Port conflicts?
No worries! Tests use random ports automatically.

### Want more logging?
Edit `src/test/resources/logback-test.xml`:
```xml
<logger name="it.eng.dcp" level="DEBUG"/>
```

## 📖 Full Documentation

See `E2E_TEST_DOCUMENTATION.md` for complete details.

## 🎯 Next Steps

Ready to expand? Add:
- Credential issuance flows
- Presentation verification tests
- Error scenario testing
- Performance benchmarks
- Multi-service interactions

---

**✨ All tests passing! Ready for production use.**
