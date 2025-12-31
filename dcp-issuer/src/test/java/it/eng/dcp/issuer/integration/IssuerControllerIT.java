package it.eng.dcp.issuer.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.CredentialRequestMessage;
import it.eng.dcp.common.model.CredentialRequestMessage.CredentialReference;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.common.service.did.DidResolverService;
import it.eng.dcp.issuer.service.CredentialDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for IssuerController using mocked DID resolution.
 *
 * <p>This test suite validates the credential request flow with:
 * <ul>
 *   <li>Real JWT token creation and signing</li>
 *   <li>Mocked DID resolution for controlled test scenarios</li>
 *   <li>Full Spring context with MockMvc</li>
 * </ul>
 */

public class IssuerControllerIT extends BaseIssuerIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DidResolverService didResolverService;

    @MockBean
    private CredentialDeliveryService credentialDeliveryService;

    private static final String ISSUER_DID = "did:test:issuer";
    private static final String HOLDER_DID = "did:test:holder";
    private static final String HOLDER_KEY_ID = "holder-key-1";
    private static final String ISSUER_KEY_ID = "issuer-key-1";

    /**
     * Setup method to initialize keypairs and reset mocks before each test.
     * Generates fresh EC key pairs for holder and issuer.
     * Configures credential delivery service mock to succeed by default.
     * Individual tests can override this behavior as needed.
     */
    @Override
    @BeforeEach
    void beforeEach() throws JOSEException {
        super.beforeEach();

        // Reset and configure credential delivery service mock to succeed by default
        // This ensures consistent behavior across all tests and allows individual tests
        // to override when testing failure scenarios
        reset(credentialDeliveryService);
        when(credentialDeliveryService.deliverCredentials(anyString(), anyList())).thenReturn(true);
        when(credentialDeliveryService.rejectCredentialRequest(anyString(), anyString())).thenReturn(true);
    }

    /**
     * Creates a self-issued ID token signed with the given key.
     *
     * @param signingKey The private EC key to sign the token
     * @param holderDid The holder DID (iss and sub)
     * @param audienceDid The audience DID (aud)
     * @return Signed JWT token string
     * @throws JOSEException if token creation fails
     */
    private String createToken(ECKey signingKey, String holderDid, String audienceDid) throws JOSEException {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(300); // 5 minutes

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(holderDid)
            .subject(holderDid)
            .audience(audienceDid)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(exp))
            .notBeforeTime(Date.from(now))
            .jwtID(UUID.randomUUID().toString())
            .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
            .keyID(signingKey.getKeyID())
            .build();

        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new ECDSASigner(signingKey.toECPrivateKey()));

        return jwt.serialize();
    }

    /**
     * Tests successful credential request with valid token and matching public key.
     * Mock returns the correct public key, allowing signature verification to succeed.
     */
    @Test
    void createCredentialRequest_success_withValidToken() throws Exception {
        // Mock: Return MATCHING public key for holder
        when(didResolverService.resolvePublicKey(
            eq(HOLDER_DID),
            eq(HOLDER_KEY_ID),
            eq("capabilityInvocation")))
            .thenReturn(holderKeyPair.toPublicJWK());

        // Create valid token signed with holder's private key
        String token = createToken(holderKeyPair, HOLDER_DID, ISSUER_DID);

        CredentialRequestMessage requestMessage = CredentialRequestMessage.Builder
            .newInstance()
            .holderPid(HOLDER_DID)
            .credentials(List.of(
                CredentialReference.Builder.newInstance().id("TestCredential").build()
            ))
            .build();

        String json = objectMapper.writeValueAsString(requestMessage);

        ResultActions result = mockMvc.perform(
            post("/issuer/credentials")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        );

        result.andExpect(status().isCreated())
              .andExpect(header().exists("Location"));
    }

    /**
     * Tests credential request failure when DID resolver throws exception.
     * Simulates DID not found scenario.
     */
    @Test
    void createCredentialRequest_failure_didNotFound() throws Exception {
        // Mock: Throw exception (DID not found)
        when(didResolverService.resolvePublicKey(
            eq(HOLDER_DID),
            eq(HOLDER_KEY_ID),
            eq("capabilityInvocation")))
            .thenThrow(new DidResolutionException("DID not found"));

        String token = createToken(holderKeyPair, HOLDER_DID, ISSUER_DID);

        CredentialRequestMessage requestMessage = CredentialRequestMessage.Builder
            .newInstance()
            .holderPid(HOLDER_DID)
            .credentials(List.of(
                CredentialReference.Builder.newInstance().id("cred1").build()
            ))
            .build();

        String json = objectMapper.writeValueAsString(requestMessage);

        ResultActions result = mockMvc.perform(
            post("/issuer/credentials")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        );

        result.andExpect(status().isUnauthorized());
    }

    /**
     * Tests credential request failure when key is not found.
     * Mock returns null, simulating missing key in DID document.
     */
    @Test
    void createCredentialRequest_failure_keyNotFound() throws Exception {
        // Mock: Return null (key not found)
        when(didResolverService.resolvePublicKey(
            eq(HOLDER_DID),
            eq(HOLDER_KEY_ID),
            eq("capabilityInvocation")))
            .thenReturn(null);

        String token = createToken(holderKeyPair, HOLDER_DID, ISSUER_DID);

        CredentialRequestMessage requestMessage = CredentialRequestMessage.Builder
            .newInstance()
            .holderPid(HOLDER_DID)
            .credentials(List.of(
                CredentialReference.Builder.newInstance().id("cred1").build()
            ))
            .build();

        String json = objectMapper.writeValueAsString(requestMessage);

        ResultActions result = mockMvc.perform(
            post("/issuer/credentials")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        );

        result.andExpect(status().isUnauthorized());
    }

    /**
     * Tests credential request without Authorization header.
     * Should return 401 Unauthorized.
     */
    @Test
    public void createCredentialRequest_unauthorized_noAuthHeader() throws Exception {
        CredentialRequestMessage requestMessage = CredentialRequestMessage.Builder
            .newInstance()
            .holderPid(HOLDER_DID)
            .credentials(List.of(
                CredentialReference.Builder.newInstance().id("cred1").build()
            ))
            .build();

        String json = objectMapper.writeValueAsString(requestMessage);

        ResultActions result = mockMvc.perform(
            post("/issuer/credentials")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        );

        result.andExpect(status().isUnauthorized());
    }

    /**
     * Tests credential request with missing holderPid in request body.
     * Should return 400 Bad Request.
     */
    @Test
    public void createCredentialRequest_badRequest_noHolderPid() throws Exception {
        // Mock: Return matching public key
        when(didResolverService.resolvePublicKey(
            eq(HOLDER_DID),
            eq(HOLDER_KEY_ID),
            eq("capabilityInvocation")))
            .thenReturn(holderKeyPair.toPublicJWK());

        String token = createToken(holderKeyPair, HOLDER_DID, ISSUER_DID);

        CredentialRequestMessage requestMessage = CredentialRequestMessage.Builder
            .newInstance()
            .holderPid(HOLDER_DID)
            .credentials(List.of(
                CredentialReference.Builder.newInstance().id("cred1").build()
            ))
            .build();

        Map<String, Object> invalidRequestMap = objectMapper.convertValue(requestMessage, Map.class);
        invalidRequestMap.remove("holderPid");

        String json = objectMapper.writeValueAsString(invalidRequestMap);

        ResultActions result = mockMvc.perform(
            post("/issuer/credentials")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        );

        result.andExpect(status().isBadRequest());
    }

    // ==================== Parameterized Tests for Authorization Failures ====================

    /**
     * Provides test cases for endpoints that require authorization.
     * Each case includes: endpoint URL, HTTP method, request body (if needed)
     */
    static Stream<Arguments> protectedEndpoints() {
        Map<String, Object> credRequestBody = Map.of(
            "holderPid", HOLDER_DID,
            "credentials", List.of(Map.of("id", "TestCredential")),
            "@context", List.of("https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"),
            "@type", "CredentialRequestMessage"
        );

        return Stream.of(
            Arguments.of("GET", "/issuer/metadata", null),
            Arguments.of("POST", "/issuer/credentials", credRequestBody)
        );
    }

    /**
     * Parameterized test: All protected endpoints should return 401 when token is invalid (DID not found).
     */
    @ParameterizedTest(name = "{0} {1} - should return 401 when DID not found")
    @MethodSource("protectedEndpoints")
    void protectedEndpoint_unauthorized_didNotFound(String method, String endpoint, Map<String, Object> body) throws Exception {
        // Mock: Throw exception (DID not found)
        when(didResolverService.resolvePublicKey(eq(HOLDER_DID), eq(HOLDER_KEY_ID), eq("capabilityInvocation")))
            .thenThrow(new DidResolutionException("DID not found"));

        String token = createToken(holderKeyPair, HOLDER_DID, ISSUER_DID);

        MockHttpServletRequestBuilder request = createRequest(method, endpoint)
            .header("Authorization", "Bearer " + token);

        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                   .content(new ObjectMapper().writeValueAsString(body));
        }

        mockMvc.perform(request)
            .andExpect(status().isUnauthorized());
    }

    /**
     * Parameterized test: All protected endpoints should return 401 when key not found.
     */
    @ParameterizedTest(name = "{0} {1} - should return 401 when key not found")
    @MethodSource("protectedEndpoints")
    void protectedEndpoint_unauthorized_keyNotFound(String method, String endpoint, Map<String, Object> body) throws Exception {
        // Mock: Return null (key not found)
        when(didResolverService.resolvePublicKey(eq(HOLDER_DID), eq(HOLDER_KEY_ID), eq("capabilityInvocation")))
            .thenReturn(null);

        String token = createToken(holderKeyPair, HOLDER_DID, ISSUER_DID);

        MockHttpServletRequestBuilder request = createRequest(method, endpoint)
            .header("Authorization", "Bearer " + token);

        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                   .content(new ObjectMapper().writeValueAsString(body));
        }

        mockMvc.perform(request)
            .andExpect(status().isUnauthorized());
    }

    /**
     * Parameterized test: All protected endpoints should return 401 when Authorization header is missing.
     */
    @ParameterizedTest(name = "{0} {1} - should return 401 when no auth header")
    @MethodSource("protectedEndpoints")
    void protectedEndpoint_unauthorized_noAuthHeader(String method, String endpoint, Map<String, Object> body) throws Exception {
        MockHttpServletRequestBuilder request = createRequest(method, endpoint);

        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                   .content(new ObjectMapper().writeValueAsString(body));
        }

        mockMvc.perform(request)
            .andExpect(status().isUnauthorized());
    }

    /**
     * Parameterized test: All protected endpoints should return 401 with expired token.
     */
    @ParameterizedTest(name = "{0} {1} - should return 401 with expired token")
    @MethodSource("protectedEndpoints")
    void protectedEndpoint_unauthorized_expiredToken(String method, String endpoint, Map<String, Object> body) throws Exception {
        // Mock: Return matching public key
        when(didResolverService.resolvePublicKey(eq(HOLDER_DID), eq(HOLDER_KEY_ID), eq("capabilityInvocation")))
            .thenReturn(holderKeyPair.toPublicJWK());

        // Create token that expired 10 minutes ago
        String expiredToken = createExpiredToken(holderKeyPair, HOLDER_DID, ISSUER_DID);

        MockHttpServletRequestBuilder request = createRequest(method, endpoint)
            .header("Authorization", "Bearer " + expiredToken);

        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                   .content(new ObjectMapper().writeValueAsString(body));
        }

        mockMvc.perform(request)
            .andExpect(status().isUnauthorized());
    }

    // ==================== GET /issuer/metadata Tests ====================

    /**
     * Tests successful metadata retrieval with valid token.
     */
    @Test
    void getMetadata_success_withValidToken() throws Exception {
        // Mock: Return matching public key
        when(didResolverService.resolvePublicKey(eq(HOLDER_DID), eq(HOLDER_KEY_ID), eq("capabilityInvocation")))
            .thenReturn(holderKeyPair.toPublicJWK());

        String token = createToken(holderKeyPair, HOLDER_DID, ISSUER_DID);

        mockMvc.perform(get("/issuer/metadata")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.credentialsSupported").isArray())
            .andExpect(jsonPath("$.credentialsSupported[0].id").value("TestCredential"));
    }

    // ==================== POST /issuer/requests/{requestId}/approve Tests ====================

    /**
     * Tests successful credential approval without custom claims.
     */
    @Test
    void approveRequest_success_withoutCustomClaims() throws Exception {
        // First create a credential request
        when(didResolverService.resolvePublicKey(eq(HOLDER_DID), eq(HOLDER_KEY_ID), eq("capabilityInvocation")))
            .thenReturn(holderKeyPair.toPublicJWK());

        String token = createToken(holderKeyPair, HOLDER_DID, ISSUER_DID);

        CredentialRequestMessage requestMessage = CredentialRequestMessage.Builder
            .newInstance()
            .holderPid(HOLDER_DID)
            .credentials(List.of(
                CredentialReference.Builder.newInstance().id("TestCredential").build()
            ))
            .build();

        String location = mockMvc.perform(post("/issuer/credentials")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestMessage)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");

        // Extract request ID from location
        String requestId = location.substring(location.lastIndexOf('/') + 1);

        // Approve the request
        mockMvc.perform(post("/issuer/requests/{requestId}/approve", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.credentialsCount").exists());
    }

    /**
     * Tests credential approval with custom claims.
     */
    @Test
    void approveRequest_success_withCustomClaims() throws Exception {
        // First create a credential request
        when(didResolverService.resolvePublicKey(eq(HOLDER_DID), eq(HOLDER_KEY_ID), eq("capabilityInvocation")))
            .thenReturn(holderKeyPair.toPublicJWK());

        String token = createToken(holderKeyPair, HOLDER_DID, ISSUER_DID);

        CredentialRequestMessage requestMessage = CredentialRequestMessage.Builder
            .newInstance()
            .holderPid(HOLDER_DID)
            .credentials(List.of(
                CredentialReference.Builder.newInstance().id("TestCredential").build()
            ))
            .build();

        String location = mockMvc.perform(post("/issuer/credentials")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestMessage)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");

        String requestId = location.substring(location.lastIndexOf('/') + 1);

        // Approve with custom claims
        Map<String, Object> approvalBody = new HashMap<>();
        Map<String, Object> customClaims = new HashMap<>();
        customClaims.put("customField", "customValue");
        approvalBody.put("customClaims", customClaims);

        mockMvc.perform(post("/issuer/requests/{requestId}/approve", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(approvalBody)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Tests approval failure when credential delivery fails (returns false).
     */
    @Test
    void approveRequest_failure_deliveryFails() throws Exception {
        // Mock delivery service to return false (delivery failed)
        when(credentialDeliveryService.deliverCredentials(anyString(), anyList())).thenReturn(false);

        // First create a credential request
        when(didResolverService.resolvePublicKey(eq(HOLDER_DID), eq(HOLDER_KEY_ID), eq("capabilityInvocation")))
            .thenReturn(holderKeyPair.toPublicJWK());

        String token = createToken(holderKeyPair, HOLDER_DID, ISSUER_DID);

        CredentialRequestMessage requestMessage = CredentialRequestMessage.Builder
            .newInstance()
            .holderPid(HOLDER_DID)
            .credentials(List.of(
                CredentialReference.Builder.newInstance().id("TestCredential").build()
            ))
            .build();

        String location = mockMvc.perform(post("/issuer/credentials")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestMessage)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");

        String requestId = location.substring(location.lastIndexOf('/') + 1);

        // Approve the request - should fail due to delivery failure
        mockMvc.perform(post("/issuer/requests/{requestId}/approve", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(containsString("Failed to deliver credentials")));
    }

    /**
     * Tests approval failure when credential delivery throws an exception.
     */
    @Test
    void approveRequest_failure_deliveryThrowsException() throws Exception {
        // Mock delivery service to throw exception
        when(credentialDeliveryService.deliverCredentials(anyString(), anyList()))
            .thenThrow(new RuntimeException("Network error"));

        // First create a credential request
        when(didResolverService.resolvePublicKey(eq(HOLDER_DID), eq(HOLDER_KEY_ID), eq("capabilityInvocation")))
            .thenReturn(holderKeyPair.toPublicJWK());

        String token = createToken(holderKeyPair, HOLDER_DID, ISSUER_DID);

        CredentialRequestMessage requestMessage = CredentialRequestMessage.Builder
            .newInstance()
            .holderPid(HOLDER_DID)
            .credentials(List.of(
                CredentialReference.Builder.newInstance().id("TestCredential").build()
            ))
            .build();

        String location = mockMvc.perform(post("/issuer/credentials")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestMessage)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");

        String requestId = location.substring(location.lastIndexOf('/') + 1);

        // Approve the request - should fail due to exception
        mockMvc.perform(post("/issuer/requests/{requestId}/approve", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(containsString("Internal error")));
    }

    /**
     * Tests approval failure with invalid request ID.
     */
    @Test
    void approveRequest_failure_invalidRequestId() throws Exception {
        mockMvc.perform(post("/issuer/requests/{requestId}/approve", "invalid-request-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    // ==================== POST /issuer/requests/{requestId}/reject Tests ====================

    /**
     * Tests successful credential request rejection.
     */
    @Test
    void rejectRequest_success() throws Exception {
        // First create a credential request
        when(didResolverService.resolvePublicKey(eq(HOLDER_DID), eq(HOLDER_KEY_ID), eq("capabilityInvocation")))
            .thenReturn(holderKeyPair.toPublicJWK());

        String token = createToken(holderKeyPair, HOLDER_DID, ISSUER_DID);

        CredentialRequestMessage requestMessage = CredentialRequestMessage.Builder
            .newInstance()
            .holderPid(HOLDER_DID)
            .credentials(List.of(
                CredentialReference.Builder.newInstance().id("TestCredential").build()
            ))
            .build();

        String location = mockMvc.perform(post("/issuer/credentials")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestMessage)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");

        String requestId = location.substring(location.lastIndexOf('/') + 1);

        // Reject the request
        Map<String, String> rejectionBody = new HashMap<>();
        rejectionBody.put("reason", "Holder not verified");

        mockMvc.perform(post("/issuer/requests/{requestId}/reject", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rejectionBody)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Credential request rejected successfully"));
    }

    /**
     * Tests rejection failure when notification delivery fails (returns false).
     */
    @Test
    void rejectRequest_failure_deliveryFails() throws Exception {
        // Mock rejection delivery to return false
        when(credentialDeliveryService.rejectCredentialRequest(anyString(), anyString())).thenReturn(false);

        // First create a credential request
        when(didResolverService.resolvePublicKey(eq(HOLDER_DID), eq(HOLDER_KEY_ID), eq("capabilityInvocation")))
            .thenReturn(holderKeyPair.toPublicJWK());

        String token = createToken(holderKeyPair, HOLDER_DID, ISSUER_DID);

        CredentialRequestMessage requestMessage = CredentialRequestMessage.Builder
            .newInstance()
            .holderPid(HOLDER_DID)
            .credentials(List.of(
                CredentialReference.Builder.newInstance().id("TestCredential").build()
            ))
            .build();

        String location = mockMvc.perform(post("/issuer/credentials")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestMessage)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");

        String requestId = location.substring(location.lastIndexOf('/') + 1);

        // Reject the request - should fail due to delivery failure
        Map<String, String> rejectionBody = new HashMap<>();
        rejectionBody.put("reason", "Holder not verified");

        mockMvc.perform(post("/issuer/requests/{requestId}/reject", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rejectionBody)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Failed to reject request"));
    }

    /**
     * Tests rejection failure when notification delivery throws an exception.
     */
    @Test
    void rejectRequest_failure_deliveryThrowsException() throws Exception {
        // Mock rejection delivery to throw exception
        when(credentialDeliveryService.rejectCredentialRequest(anyString(), anyString()))
            .thenThrow(new RuntimeException("Connection timeout"));

        // First create a credential request
        when(didResolverService.resolvePublicKey(eq(HOLDER_DID), eq(HOLDER_KEY_ID), eq("capabilityInvocation")))
            .thenReturn(holderKeyPair.toPublicJWK());

        String token = createToken(holderKeyPair, HOLDER_DID, ISSUER_DID);

        CredentialRequestMessage requestMessage = CredentialRequestMessage.Builder
            .newInstance()
            .holderPid(HOLDER_DID)
            .credentials(List.of(
                CredentialReference.Builder.newInstance().id("TestCredential").build()
            ))
            .build();

        String location = mockMvc.perform(post("/issuer/credentials")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestMessage)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");

        String requestId = location.substring(location.lastIndexOf('/') + 1);

        // Reject the request - should fail due to exception
        Map<String, String> rejectionBody = new HashMap<>();
        rejectionBody.put("reason", "Holder not verified");

        mockMvc.perform(post("/issuer/requests/{requestId}/reject", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rejectionBody)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(containsString("Internal error")));
    }

    /**
     * Tests rejection failure with invalid request ID.
     */
    @Test
    void rejectRequest_failure_invalidRequestId() throws Exception {
        Map<String, String> rejectionBody = new HashMap<>();
        rejectionBody.put("reason", "Invalid holder");

        mockMvc.perform(post("/issuer/requests/invalid-id/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rejectionBody)))
            .andExpect(status().isBadRequest());
    }

    // ==================== GET /issuer/requests/{requestId} Tests ====================

    /**
     * Tests successful retrieval of credential request status.
     */
    @Test
    void getRequest_success() throws Exception {
        // First create a credential request
        when(didResolverService.resolvePublicKey(eq(HOLDER_DID), eq(HOLDER_KEY_ID), eq("capabilityInvocation")))
            .thenReturn(holderKeyPair.toPublicJWK());

        String token = createToken(holderKeyPair, HOLDER_DID, ISSUER_DID);

        CredentialRequestMessage requestMessage = CredentialRequestMessage.Builder
            .newInstance()
            .holderPid(HOLDER_DID)
            .credentials(List.of(
                CredentialReference.Builder.newInstance().id("TestCredential").build()
            ))
            .build();

        String location = mockMvc.perform(post("/issuer/credentials")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestMessage)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");

        String requestId = location.substring(location.lastIndexOf('/') + 1);

        // Get the request status
        mockMvc.perform(get("/issuer/requests/{requestId}", requestId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("CredentialStatus"))
            .andExpect(jsonPath("$.issuerPid").value(requestId))
            .andExpect(jsonPath("$.holderPid").value(HOLDER_DID))
            .andExpect(jsonPath("$.status").exists());
    }

    /**
     * Tests request status retrieval with invalid ID.
     */
    @Test
    void getRequest_notFound() throws Exception {
        mockMvc.perform(get("/issuer/requests/{requestId}", "non-existent-id"))
            .andExpect(status().isNotFound());
    }

    // ==================== Helper Methods ====================

    /**
     * Creates an expired token (expired 10 minutes ago).
     */
    private String createExpiredToken(ECKey signingKey, String holderDid, String audienceDid) throws JOSEException {
        Instant past = Instant.now().minusSeconds(600); // 10 minutes ago
        Instant exp = past.plusSeconds(300); // Expired 5 minutes ago

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(holderDid)
            .subject(holderDid)
            .audience(audienceDid)
            .issueTime(Date.from(past))
            .expirationTime(Date.from(exp))
            .notBeforeTime(Date.from(past))
            .jwtID(UUID.randomUUID().toString())
            .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
            .keyID(signingKey.getKeyID())
            .build();

        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new ECDSASigner(signingKey.toECPrivateKey()));

        return jwt.serialize();
    }

    /**
     * Creates a MockHttpServletRequestBuilder for the given method and endpoint.
     */
    private MockHttpServletRequestBuilder createRequest(String method, String endpoint) {
        return switch (method.toUpperCase()) {
            case "GET" -> get(endpoint);
            case "POST" -> post(endpoint);
            case "PUT" -> put(endpoint);
            case "DELETE" -> delete(endpoint);
            case "PATCH" -> patch(endpoint);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };
    }
}

