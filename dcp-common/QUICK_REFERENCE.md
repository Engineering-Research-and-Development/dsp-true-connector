# DCP Common - Quick Reference Guide

## Quick Start

### Add to Your Module
```xml
<dependency>
    <groupId>it.eng</groupId>
    <artifactId>dcp-common</artifactId>
    <version>${revision}</version>
</dependency>
```

### Import Classes
```java
import it.eng.dcp.common.model.*;
import it.eng.dcp.common.util.*;
```

---

## Common Use Cases

### 1. Create a DID Document
```java
DidDocument doc = DidDocument.Builder.newInstance()
    .id("did:web:issuer.example.com")
    .service(List.of(
        new ServiceEntry(
            "credential-service",
            "CredentialIssuerService",
            "https://issuer.example.com/credentials"
        )
    ))
    .verificationMethod(List.of(
        VerificationMethod.Builder.newInstance()
            .id("did:web:issuer.example.com#key-1")
            .type("JsonWebKey2020")
            .controller("did:web:issuer.example.com")
            .publicKeyJwk(Map.of(
                "kty", "RSA",
                "n", "...",
                "e", "AQAB"
            ))
            .build()
    ))
    .build();
```

### 2. Convert URL to DID
```java
// Simple conversion
String did = DidUrlConverter.convertUrlToDid("https://example.com/path");
// Result: "did:web:example.com"

// With port
String did = DidUrlConverter.convertUrlToDid("https://localhost:8080/api");
// Result: "did:web:localhost%3A8080"

// Extract base URL
String baseUrl = DidUrlConverter.extractBaseUrl("https://example.com:9090/path/to/resource");
// Result: "https://example.com:9090"
```

### 3. Use Credential Status
```java
CredentialStatus status = CredentialStatus.PENDING;
status = CredentialStatus.RECEIVED;
status = CredentialStatus.ISSUED;
status = CredentialStatus.REJECTED;
```

### 4. Work with Profile IDs
```java
// Get profile
ProfileId profile = ProfileId.VC11_SL2021_JWT;
String value = profile.getValue(); // "vc11_sl2021_jwt"

// Parse from string
ProfileId parsed = ProfileId.fromValue("vc11_sl2021_jsonld");
// Result: ProfileId.VC11_SL2021_JSONLD
```

### 5. Extend BaseDcpMessage
```java
@JsonDeserialize(builder = MyMessage.Builder.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MyMessage extends BaseDcpMessage {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    @Override
    @JsonProperty(value = DSpaceConstants.TYPE, access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return MyMessage.class.getSimpleName();
    }
    
    @NotNull
    private String myField;
    
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final MyMessage msg;
        
        private Builder() {
            msg = new MyMessage();
            msg.getContext().add(DSpaceConstants.DCP_CONTEXT);
        }
        
        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }
        
        public Builder myField(String myField) {
            msg.myField = myField;
            return this;
        }
        
        public MyMessage build() {
            try {
                msg.validateBase();
                // Add custom validation here
                return msg;
            } catch (Exception e) {
                throw new ValidationException("MyMessage - " + e.getMessage());
            }
        }
    }
}
```

---

## API Reference

### DidDocument
**Package:** `it.eng.dcp.common.model`

**Fields:**
- `String id` - DID identifier (required)
- `List<ServiceEntry> services` - Service endpoints
- `List<VerificationMethod> verificationMethods` - Verification methods

**Builder Methods:**
- `id(String)` - Set DID identifier
- `service(List<ServiceEntry>)` - Add services
- `verificationMethod(List<VerificationMethod>)` - Add verification methods
- `build()` - Build and validate

---

### VerificationMethod
**Package:** `it.eng.dcp.common.model`

**Fields:**
- `String id` - Method identifier
- `String type` - Method type (e.g., "JsonWebKey2020")
- `String controller` - Controller DID
- `String publicKeyMultibase` - Multibase encoded public key
- `Map<String, Object> publicKeyJwk` - JWK format public key

**Builder Methods:**
- `id(String)` - Set identifier
- `type(String)` - Set type
- `controller(String)` - Set controller
- `publicKeyMultibase(String)` - Set multibase key
- `publicKeyJwk(Map<String, Object>)` - Set JWK
- `build()` - Build instance

---

### ServiceEntry
**Package:** `it.eng.dcp.common.model`

**Constructor:**
```java
new ServiceEntry(String id, String type, String serviceEndpoint)
```

**Fields:**
- `String id()` - Service identifier
- `String type()` - Service type
- `String serviceEndpoint()` - Endpoint URL

---

### DidUrlConverter
**Package:** `it.eng.dcp.common.util`

**Static Methods:**
```java
String convertUrlToDid(String targetUrl)
String extractBaseUrl(String targetUrl)
```

---

### CredentialStatus
**Package:** `it.eng.dcp.common.model`

**Values:**
- `PENDING` - Request pending
- `RECEIVED` - Request received
- `ISSUED` - Credential issued
- `REJECTED` - Request rejected

---

### ProfileId
**Package:** `it.eng.dcp.common.model`

**Values:**
- `VC11_SL2021_JWT` - "vc11_sl2021_jwt"
- `VC11_SL2021_JSONLD` - "vc11_sl2021_jsonld"

**Methods:**
- `String getValue()` - Get string value
- `static ProfileId fromValue(String)` - Parse from string
- `String toString()` - Get string representation

---

### BaseDcpMessage
**Package:** `it.eng.dcp.common.model`

**Abstract Methods:**
- `String getType()` - Return message type

**Protected Methods:**
- `void validateBase()` - Validate base fields
- `List<String> getContext()` - Get mutable context list

**Usage:** Extend this class for custom DCP messages

---

## Examples

### Complete DID Document with Multiple Services
```java
DidDocument doc = DidDocument.Builder.newInstance()
    .id("did:web:connector.example.com")
    .service(List.of(
        new ServiceEntry(
            "credential-issuer",
            "CredentialIssuerService",
            "https://connector.example.com/issuer"
        ),
        new ServiceEntry(
            "credential-verifier",
            "CredentialVerifierService",
            "https://connector.example.com/verifier"
        )
    ))
    .verificationMethod(List.of(
        VerificationMethod.Builder.newInstance()
            .id("did:web:connector.example.com#key-1")
            .type("JsonWebKey2020")
            .controller("did:web:connector.example.com")
            .publicKeyJwk(myJwkMap)
            .build()
    ))
    .build();
```

### Error Handling
```java
try {
    String did = DidUrlConverter.convertUrlToDid(url);
} catch (IllegalArgumentException e) {
    log.error("Invalid URL: {}", e.getMessage());
}

try {
    DidDocument doc = DidDocument.Builder.newInstance()
        .id("did:web:example.com")
        .build();
} catch (ValidationException e) {
    log.error("Validation failed: {}", e.getMessage());
}
```

---

## Package Summary

| Package | Purpose | Classes |
|---------|---------|---------|
| `it.eng.dcp.common.model` | Shared data models | 6 classes/enums |
| `it.eng.dcp.common.util` | Utility functions | 1 class |

---

## Dependencies You Get

When you add dcp-common, you automatically get:
- ✓ Jackson (JSON processing)
- ✓ Lombok (code generation)
- ✓ SLF4J (logging)
- ✓ Nimbus JOSE+JWT
- ✓ Jakarta Validation
- ✓ it.eng:tools (DSpaceConstants)

---

## Tips & Best Practices

1. **Always validate** - Use the builder pattern and let validation happen
2. **Immutable where possible** - Use records like ServiceEntry
3. **Extend BaseDcpMessage** - For all DCP protocol messages
4. **Use ProfileId enum** - Don't hardcode profile strings
5. **Handle exceptions** - DidUrlConverter throws IllegalArgumentException

---

## Need Help?

- See `README.md` for module overview
- See `CREATION_SUMMARY.md` for component details
- See `COMPLETION_REPORT.md` for full technical documentation
- Check existing code in `dcp/` module for usage examples

---

**Version:** 0.6.4-SNAPSHOT  
**Last Updated:** December 18, 2025

