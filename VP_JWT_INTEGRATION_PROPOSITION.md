# VP JWT Integration Proposition for Contract Negotiation

## Executive Summary

This document proposes an **optional, configurable** integration of DCP Verifiable Presentation (VP) JWT credentials into the contract negotiation flow, specifically for the `sendContractRequestMessage` method in `ContractNegotiationAPIService`.

The solution leverages the existing `CredentialUtils.getConnectorCredentials()` method as the integration point, allowing the system to optionally fetch and use VP JWTs from the DCP module instead of basic authentication.

---

## Current Architecture Analysis

### Flow Overview

```
ContractNegotiationAPIService.sendContractRequestMessage()
    ↓
    Creates ContractRequestMessage
    ↓
    Calls: okHttpRestClient.sendRequestProtocol(url, body, credentials)
    ↓
    credentials = credentialUtils.getConnectorCredentials()
    ↓
    Currently returns: Basic Auth "connector@mail.com:password"
    ↓
    Sends HTTP POST with Authorization header
    ↓
    Verifier receives request (provider connector)
```

### Current Implementation

#### 1. **ContractNegotiationAPIService.sendContractRequestMessage()**

```java
// Line 116-118
GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
    ContractNegotiationCallback.getInitialNegotiationRequestURL(forwardTo),
    NegotiationSerializer.serializeProtocolJsonNode(contractRequestMessage), 
    credentialUtils.getConnectorCredentials());  // ← Integration point
```

**Key observations:**
- Uses `credentialUtils.getConnectorCredentials()` for authentication
- `forwardTo` URL identifies the target verifier (provider)
- Contract request contains offer details

#### 2. **CredentialUtils.getConnectorCredentials()**

```java
@Component
public class CredentialUtils {
    public String getConnectorCredentials() {
        // TODO replace with Daps JWT
        return okhttp3.Credentials.basic("connector@mail.com", "password");
    }
}
```

**Key observations:**
- Marked with TODO to replace with DAPS JWT
- Perfect integration point for VP JWT
- Already used across all protocol calls
- Returns full authorization header value (e.g., "Basic ..." or "Bearer ...")

#### 3. **OkHttpRestClient.sendRequestProtocol()**

```java
// Line 58-75
public GenericApiResponse<String> sendRequestProtocol(String targetAddress, 
                                                       JsonNode jsonNode, 
                                                       String authorization) {
    Request.Builder requestBuilder = new Request.Builder().url(targetAddress);
    // ... build request body ...
    if(StringUtils.isNotBlank(authorization)) {
        requestBuilder.addHeader(HttpHeaders.AUTHORIZATION, authorization);
    }
    Request request = requestBuilder.build();
    // ... execute request ...
}
```

**Key observations:**
- Accepts authorization string directly
- No format restrictions (can be Basic, Bearer, etc.)
- Authorization is optional (null check)

---

## Proposed Solution

### Option 1: Enhanced CredentialUtils with DCP Integration (RECOMMENDED)

Extend `CredentialUtils` to optionally fetch VP JWTs from the DCP module when enabled.

#### Architecture

```
ContractNegotiationAPIService.sendContractRequestMessage()
    ↓
    credentialUtils.getConnectorCredentials()
    ↓
    Check: dcp.vp.enabled = true?
    ↓
    Yes → Fetch VP JWT from DCP module
    ↓
    dcpCredentialService.getVerifiablePresentationJwt(targetDid, scope)
    ↓
    presentationService.createPresentation(query)
    ↓
    BasicVerifiablePresentationSigner.sign(vp, "jwt")
    ↓
    Returns: "Bearer eyJhbGc...VP_JWT..."
    ↓
    No → Return basic auth (current behavior)
```

#### Implementation

##### 1. **Create DcpCredentialService** (New)

```java
package it.eng.tools.dcp;

import it.eng.dcp.model.PresentationQueryMessage;
import it.eng.dcp.model.PresentationResponseMessage;
import it.eng.dcp.service.PresentationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for fetching Verifiable Presentation JWTs from DCP module.
 * Used to obtain credentials for authenticating protocol requests to other connectors.
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "dcp.vp", name = "enabled", havingValue = "true")
public class DcpCredentialService {

    private final PresentationService presentationService;

    @Autowired
    public DcpCredentialService(PresentationService presentationService) {
        this.presentationService = presentationService;
    }

    /**
     * Get a Verifiable Presentation JWT for authenticating requests to a target connector.
     * 
     * @param targetDid Optional DID of the target verifier (can be null for self-issued)
     * @param scope Optional list of credential types to include (can be null for all)
     * @return Bearer token string with VP JWT (e.g., "Bearer eyJhbGc...")
     */
    public String getVerifiablePresentationJwt(String targetDid, List<String> scope) {
        log.debug("Creating VP JWT for target: {}, scope: {}", targetDid, scope);
        
        try {
            // Build presentation query
            PresentationQueryMessage.Builder queryBuilder = PresentationQueryMessage.Builder.newInstance();
            if (scope != null && !scope.isEmpty()) {
                queryBuilder.scope(scope);
            }
            PresentationQueryMessage query = queryBuilder.build();
            
            // Create presentation with embedded credentials
            PresentationResponseMessage response = presentationService.createPresentation(query);
            
            // Extract first presentation (should be JWT string)
            if (response.getPresentation() == null || response.getPresentation().isEmpty()) {
                log.warn("No presentations created, falling back to basic auth");
                return null;
            }
            
            Object presentation = response.getPresentation().get(0);
            if (presentation instanceof String) {
                String vpJwt = (String) presentation;
                log.debug("VP JWT created successfully, length: {}", vpJwt.length());
                return "Bearer " + vpJwt;
            } else {
                log.warn("Presentation is not a JWT string, falling back to basic auth");
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to create VP JWT: {}", e.getMessage(), e);
            // Fail gracefully - return null to fall back to basic auth
            return null;
        }
    }

    /**
     * Get a Verifiable Presentation JWT with default scope (all credentials).
     * 
     * @return Bearer token string with VP JWT
     */
    public String getVerifiablePresentationJwt() {
        return getVerifiablePresentationJwt(null, null);
    }
}
```

##### 2. **Update CredentialUtils**

```java
package it.eng.tools.util;

import it.eng.tools.dcp.DcpCredentialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class CredentialUtils {

    @Value("${dcp.vp.enabled:false}")
    private boolean dcpVpEnabled;

    @Value("${dcp.vp.scope:#{null}}")
    private List<String> dcpVpScope;

    @Autowired(required = false)
    private DcpCredentialService dcpCredentialService;

    /**
     * Get connector credentials for authenticating protocol requests.
     * 
     * When DCP VP is enabled, returns a Bearer token with Verifiable Presentation JWT.
     * Otherwise, returns basic authentication credentials.
     * 
     * @return Full authorization header value (e.g., "Basic ..." or "Bearer ...")
     */
    public String getConnectorCredentials() {
        // Option 1: Use DCP Verifiable Presentation JWT
        if (dcpVpEnabled && dcpCredentialService != null) {
            try {
                log.debug("DCP VP enabled, fetching VP JWT");
                String vpJwt = dcpCredentialService.getVerifiablePresentationJwt(null, dcpVpScope);
                if (vpJwt != null) {
                    log.info("Using VP JWT for connector authentication");
                    return vpJwt;
                } else {
                    log.warn("VP JWT creation failed, falling back to basic auth");
                }
            } catch (Exception e) {
                log.error("Error fetching VP JWT: {}", e.getMessage(), e);
                log.warn("Falling back to basic authentication");
            }
        }
        
        // Option 2: Fall back to basic auth (current behavior)
        log.debug("Using basic authentication for connector credentials");
        return okhttp3.Credentials.basic("connector@mail.com", "password");
    }

    /**
     * Get connector credentials with specific credential types.
     * 
     * @param requiredCredentialTypes List of credential types to include in VP
     * @return Full authorization header value
     */
    public String getConnectorCredentials(List<String> requiredCredentialTypes) {
        if (dcpVpEnabled && dcpCredentialService != null) {
            try {
                log.debug("DCP VP enabled, fetching VP JWT with scope: {}", requiredCredentialTypes);
                String vpJwt = dcpCredentialService.getVerifiablePresentationJwt(null, requiredCredentialTypes);
                if (vpJwt != null) {
                    log.info("Using VP JWT with specific scope for connector authentication");
                    return vpJwt;
                }
            } catch (Exception e) {
                log.error("Error fetching VP JWT with scope: {}", e.getMessage(), e);
            }
        }
        
        return getConnectorCredentials(); // Fall back to default
    }
    
    public String getAPICredentials() {
        // get from users or from property file instead hardcoded
        return okhttp3.Credentials.basic("admin@mail.com", "password");
    }
}
```

##### 3. **Configuration Properties**

Add to `application.properties` (or `application-consumer.properties`):

```properties
# DCP Verifiable Presentation Configuration
# Enable VP JWT for connector-to-connector authentication
dcp.vp.enabled=false

# Optional: Specify credential types to include in VP
# If not specified, all available credentials will be included
# dcp.vp.scope=MembershipCredential,DataUsageCredential

# Optional: Specify target DID for VP (if known)
# dcp.vp.target-did=did:web:provider.example
```

##### 4. **Optional: ContractNegotiationAPIService Enhancement**

For more granular control, allow specifying credential types per request:

```java
public ContractNegotiation sendContractRequestMessage(JsonNode contractRequestMessageRequest) {
    String forwardTo = contractRequestMessageRequest.get("Forward-To").asText();
    JsonNode offerNode = contractRequestMessageRequest.get(DSpaceConstants.OFFER);

    Offer offerWithoutOriginalId = NegotiationSerializer.deserializePlain(offerNode.toPrettyString(), Offer.class);

    log.info("Sending ContractRequestMessage to {} to start a new Contract Negotiation", forwardTo);
    ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
            .callbackAddress(properties.consumerCallbackAddress())
            .consumerPid(ToolsUtil.generateUniqueId())
            .offer(offerWithoutOriginalId)
            .build();

    // OPTIONAL: Extract required credential types from offer/policy
    List<String> requiredCredentials = extractRequiredCredentials(offerWithoutOriginalId);
    String credentials = requiredCredentials != null && !requiredCredentials.isEmpty()
            ? credentialUtils.getConnectorCredentials(requiredCredentials)
            : credentialUtils.getConnectorCredentials();

    GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
            ContractNegotiationCallback.getInitialNegotiationRequestURL(forwardTo),
            NegotiationSerializer.serializeProtocolJsonNode(contractRequestMessage), 
            credentials);
    
    // ... rest of method ...
}

/**
 * Extract required credential types from offer policy (optional enhancement).
 */
private List<String> extractRequiredCredentials(Offer offer) {
    // TODO: Implement policy parsing to extract credential requirements
    // For now, return null to use all credentials
    return null;
}
```

---

## Implementation Phases

### Phase 1: Basic Integration (Minimal Changes)

1. Create `DcpCredentialService` in tools module
2. Update `CredentialUtils` with conditional VP JWT logic
3. Add configuration properties
4. Test with `dcp.vp.enabled=true`

**Effort**: 4-6 hours  
**Risk**: Low (graceful fallback to basic auth)

### Phase 2: Enhanced Control (Optional)

1. Add scope-based credential selection
2. Implement policy-based credential extraction
3. Add caching for VP JWTs (to avoid regenerating on every request)
4. Add metrics/monitoring

**Effort**: 8-12 hours  
**Risk**: Medium (requires policy parsing logic)

### Phase 3: Full DCP Integration (Future)

1. Implement verifier-side VP validation
2. Add mutual VP exchange (both consumer and provider present VPs)
3. Integrate with trust frameworks
4. Add credential refresh logic

**Effort**: 20-30 hours  
**Risk**: Medium-High (requires both sides to support DCP)

---

## Benefits

### 1. **Zero Breaking Changes**
- Existing code continues to work with basic auth
- VP JWT is optional (feature flag)
- Graceful fallback if VP creation fails

### 2. **Minimal Code Changes**
- Only `CredentialUtils` and new `DcpCredentialService`
- No changes to `ContractNegotiationAPIService` required
- Reuses existing `PresentationService` and `BasicVerifiablePresentationSigner`

### 3. **DCP Spec Compliant**
- Uses VP JWT with embedded credentials
- Follows W3C VC Data Model
- Compatible with Dataspace Protocol

### 4. **Flexible Configuration**
- Enable/disable per environment
- Configure credential scope globally or per request
- Easy to test and validate

### 5. **Production Ready**
- Error handling with fallback
- Logging for troubleshooting
- Conditional bean creation (doesn't break if DCP module absent)

---

## Security Considerations

### 1. **VP JWT Lifecycle**

**Current Approach**: Generate fresh VP JWT per request

**Pros**:
- Always up-to-date credentials
- No caching/expiry concerns

**Cons**:
- Performance overhead
- Multiple VPs for same negotiation

**Future Enhancement**: Cache VP JWTs with TTL

```java
@Service
public class DcpCredentialService {
    private final Map<String, CachedVpJwt> vpCache = new ConcurrentHashMap<>();
    
    public String getVerifiablePresentationJwt(String targetDid, List<String> scope) {
        String cacheKey = targetDid + ":" + scope;
        CachedVpJwt cached = vpCache.get(cacheKey);
        
        if (cached != null && !cached.isExpired()) {
            return cached.getJwt();
        }
        
        String vpJwt = createFreshVpJwt(targetDid, scope);
        vpCache.put(cacheKey, new CachedVpJwt(vpJwt, Instant.now().plusSeconds(300)));
        return vpJwt;
    }
}
```

### 2. **Credential Selection**

**Challenge**: Which credentials to include in VP?

**Options**:
- **All credentials**: Simple but may expose unnecessary data
- **Scoped by type**: Use `dcp.vp.scope` config
- **Policy-based**: Parse offer policy to extract requirements (Phase 2)

**Recommendation**: Start with configurable scope, add policy parsing later

### 3. **Verifier Validation**

**Important**: This proposal focuses on **consumer (holder) side** generating VP JWTs.

The **provider (verifier) side** must also be updated to:
1. Accept Bearer tokens with VP JWTs
2. Parse and validate VP signature (holder's key)
3. Extract and validate embedded VC signatures (issuer's keys)
4. Check credential validity (dates, revocation, trust)

**This is a separate effort** and should be tracked as Phase 3.

---

## Testing Strategy

### Unit Tests

```java
@Test
public void testCredentialUtils_dcpDisabled_returnsBasicAuth() {
    CredentialUtils utils = new CredentialUtils();
    utils.dcpVpEnabled = false;
    
    String creds = utils.getConnectorCredentials();
    
    assertTrue(creds.startsWith("Basic "));
}

@Test
public void testCredentialUtils_dcpEnabled_returnsVpJwt() {
    CredentialUtils utils = new CredentialUtils();
    utils.dcpVpEnabled = true;
    utils.dcpCredentialService = mock(DcpCredentialService.class);
    when(utils.dcpCredentialService.getVerifiablePresentationJwt(null, null))
        .thenReturn("Bearer eyJhbGc...");
    
    String creds = utils.getConnectorCredentials();
    
    assertTrue(creds.startsWith("Bearer "));
    assertTrue(creds.contains("eyJhbGc"));
}

@Test
public void testCredentialUtils_dcpEnabled_fallsBackOnError() {
    CredentialUtils utils = new CredentialUtils();
    utils.dcpVpEnabled = true;
    utils.dcpCredentialService = mock(DcpCredentialService.class);
    when(utils.dcpCredentialService.getVerifiablePresentationJwt(null, null))
        .thenThrow(new RuntimeException("DCP error"));
    
    String creds = utils.getConnectorCredentials();
    
    assertTrue(creds.startsWith("Basic "));  // Fallback
}
```

### Integration Tests

```java
@SpringBootTest
@TestPropertySource(properties = {"dcp.vp.enabled=true", "dcp.vp.scope=MembershipCredential"})
public class ContractNegotiationVpIntegrationTest {
    
    @Autowired
    private ContractNegotiationAPIService apiService;
    
    @Autowired
    private CredentialUtils credentialUtils;
    
    @Test
    public void testSendContractRequest_withVpJwt() {
        // Setup: Store a credential in holder repository
        storeTestCredential();
        
        // Execute: Send contract request
        JsonNode request = createContractRequest();
        ContractNegotiation result = apiService.sendContractRequestMessage(request);
        
        // Verify: VP JWT was used (check logs or mock)
        // Note: Verifier must be configured to accept VP JWTs
        assertNotNull(result);
    }
}
```

---

## Migration Path

### Step 1: Development Environment
1. Enable DCP module in consumer connector
2. Set `dcp.vp.enabled=true`
3. Configure `dcp.vp.scope` if needed
4. Test contract negotiation flow
5. Verify VP JWT in request logs

### Step 2: Staging/Testing
1. Deploy both consumer and provider with DCP support
2. Test with real credentials (not test data)
3. Verify verifier can validate VP JWTs
4. Performance testing (VP generation overhead)

### Step 3: Production Rollout
1. Deploy consumer with `dcp.vp.enabled=false` (safe mode)
2. Gradually enable for specific partners/connectors
3. Monitor error rates and fallback usage
4. Full rollout once stable

---

## Alternative Approaches (Considered but Not Recommended)

### Alternative 1: Direct Integration in ContractNegotiationAPIService

**Approach**: Add DCP logic directly in `sendContractRequestMessage()`

**Pros**:
- More control per request
- Can customize VP per negotiation

**Cons**:
- ❌ Violates single responsibility
- ❌ Tight coupling between negotiation and DCP
- ❌ Hard to reuse for other protocol calls
- ❌ More complex testing

### Alternative 2: AOP/Interceptor for Authorization

**Approach**: Use Spring AOP to intercept `sendRequestProtocol()` calls

**Pros**:
- No changes to existing code
- Centralized credential logic

**Cons**:
- ❌ Hard to debug
- ❌ Complex configuration
- ❌ Difficult to test
- ❌ Magic behavior (not explicit)

### Alternative 3: Custom OkHttp Interceptor

**Approach**: Add interceptor to OkHttpClient that adds VP JWT header

**Pros**:
- Transparent to application code

**Cons**:
- ❌ Too low-level
- ❌ Hard to configure per request
- ❌ Difficult to disable/fallback
- ❌ Mixes transport and business logic

**Conclusion**: Option 1 (Enhanced CredentialUtils) provides the best balance of simplicity, flexibility, and maintainability.

---

## Conclusion

The proposed solution leverages the existing `CredentialUtils.getConnectorCredentials()` method as a clean integration point for DCP Verifiable Presentation JWTs. This approach:

✅ **Minimal changes** - Only 2 classes modified/created  
✅ **Optional** - Feature flag controlled  
✅ **Safe** - Graceful fallback to basic auth  
✅ **Reusable** - Works for all protocol calls  
✅ **Testable** - Clear unit/integration test paths  
✅ **DCP compliant** - Uses VP JWT with embedded credentials  
✅ **Production ready** - Error handling, logging, monitoring  

### Recommended Next Steps

1. ✅ Review this proposition document
2. Create `DcpCredentialService` class
3. Update `CredentialUtils` with conditional logic
4. Add configuration properties
5. Write unit tests
6. Test with `dcp.vp.enabled=true` in development
7. Document verifier-side validation requirements (separate task)

---

## Appendix: Example VP JWT Structure

When `dcp.vp.enabled=true`, the system will generate and send VP JWTs like this:

```
Authorization: Bearer eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImhvbGRlci1rZXktMSJ9.eyJpc3MiOiJkaWQ6d2ViOmNvbnN1bWVyLmV4YW1wbGUiLCJzdWIiOiJkaWQ6d2ViOmNvbnN1bWVyLmV4YW1wbGUiLCJ2cCI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVQcmVzZW50YXRpb24iXSwidmVyaWZpYWJsZUNyZWRlbnRpYWwiOlsiZXlKaGJHY2lPaUpGVXpJMU5pSXNJblI1Y0NJNklrcFhWQ0lzSW10cFpDSTZJbWx6YzNWbGNpMXJaWGt0TVNKOS5leUpwYzNNaU9pSmthV1E2ZDJWaU9tbHpjM1ZsY2k1bGVHRnRjR3hsSWl3aWMzVmlJam9pWkdsa09uZGxZanBqYjI1emRXMWxjaTVsZUdGdGNHeGxJaXdpZG1NaU9uc2lRR052Ym5SbGVIUWlPbHNpYUhSMGNITTZMeTkzZDNjdWR6TXViM0puTHpJd01UZ3ZZM0psWkdWdWRHbGhiSE12ZGpFaVhTd2lkSGx3WlNJNld5SldaWEpwWm1saFlteGxRM0psWkdWdWRHbGhiQ0lzSWsxbGJXSmxjbk5vYVhCRGNtVmtaVzUwYVdGc0lsMHNJbU55WldSbGJuUnBZV3hUZFdKcVpXTjBJanA3SW1sa0lqb2laR2xrT25kbFlqcGpiMjV6ZFcxbGNpNWxlR0Z0Y0d4bElpd2liV1Z0WW1WeVRHVjJaV3dpT2lKSGIyeGtJbjE5TENKcFlYUWlPakUzTURJek1UQTBNREFzSW1WNGNDSTZNVGN6TXpnME5qUXdNQ3dpYW5ScElqb2lkWEp1T25WMWFXUTZaakppTlRKbVltUXROelZBTUxUUXpabUl0WVdZME1DMHhNelUyTW1Fd1pUQTBNalFpZlEuc2lnbmF0dXJlLWJ5LWlzc3VlciJdLCJwcm9maWxlSWQiOiJWQzExX1NMMjAyMV9KV1QifSwiaWF0IjoxNzY1NDUxNjAyLCJqdGkiOiJ1cm46dXVpZDo0NzBhOTJmZi0xNDk1LTQxYWItYjJlZi1jOTk5NDkyYzVhNGQifQ.signature-by-holder
```

Decoded:

```json
{
  "iss": "did:web:consumer.example",
  "sub": "did:web:consumer.example",
  "vp": {
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiablePresentation"],
    "verifiableCredential": [
      "eyJhbGc...FULL_VC_JWT..."  // Embedded credential
    ],
    "profileId": "VC11_SL2021_JWT"
  },
  "iat": 1765451602,
  "jti": "urn:uuid:470a92ff-1495-41ab-b2ef-c999492c5a4d"
}
```

The verifier (provider) can then validate both the VP signature (proving the consumer created it) and the embedded VC signature (proving the issuer created the credential).

