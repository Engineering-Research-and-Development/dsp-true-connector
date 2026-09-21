package it.eng.connector.integration.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.connector.repository.UserRepository;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.tools.auth.jwt.JwtService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.IConstants;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test verifying the unified INTERNAL authentication mode flow.
 * Covers full login -> protected call -> refresh token rotation -> logout -> revoked token rejection,
 * login failure scenarios, validation (400) flows, and internal machine-to-machine Basic Auth regression check.
 */
@Slf4j
public class InternalAuthFlowIT extends BaseIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ContractNegotiationRepository contractNegotiationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private JwtService jwtService;

    @BeforeEach
    @AfterEach
    void cleanUpTestUsers() {
        deleteUserIfExists("lifecycle@test.com");
        deleteUserIfExists("failures@test.com");
        deleteUserIfExists("disabled@test.com");
        deleteUserIfExists("locked@test.com");
        deleteUserIfExists("connector@test.com");
        deleteUserIfExists("admin@test.com");
        contractNegotiationRepository.deleteAll();
    }

    private void deleteUserIfExists(String email) {
        userRepository.findByEmail(email).ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("Full authentication lifecycle for API: login -> protected call -> refresh -> logout -> rejection of revoked token")
    void fullAuthLifecycleFlow_API() throws Exception {
        String email = "lifecycle@test.com";
        String password = "password123";
        createTestUserAPI(email, password, true, false);

        // 1. Full Login
        MvcResult loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.refresh_token", notNullValue()))
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        Map<?, ?> responseMap = objectMapper.readValue(responseBody, Map.class);
        String accessToken1 = (String) responseMap.get("access_token");
        String refreshToken1 = (String) responseMap.get("refresh_token");

        // 2. Protected Call
        mockMvc.perform(get(ApiEndpoints.CATALOG_DATA_SERVICES_V1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // 3. Refresh (Rotation)
        MvcResult refreshResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refresh_token", refreshToken1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.refresh_token", notNullValue()))
                .andReturn();

        String refreshResponseBody = refreshResult.getResponse().getContentAsString();
        Map<?, ?> refreshResponseMap = objectMapper.readValue(refreshResponseBody, Map.class);
        String accessToken2 = (String) refreshResponseMap.get("access_token");
        String refreshToken2 = (String) refreshResponseMap.get("refresh_token");

        // 4. Protected Call
        mockMvc.perform(get(ApiEndpoints.CATALOG_DATA_SERVICES_V1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken2)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // 5. Logout
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refresh_token", refreshToken2))))
                .andExpect(status().isOk());

        // 6. Refresh-with-revoked-token rejection
        // Attempting to refresh with the rotated token (refreshToken1) should be rejected
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refresh_token", refreshToken1))))
                .andExpect(status().isUnauthorized());

        // Attempting to refresh with the logged-out token (refreshToken2) should also be rejected
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refresh_token", refreshToken2))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Full authentication lifecycle for protocol endpoint: login -> protected call -> refresh -> logout -> rejection of revoked token")
    void fullAuthLifecycleFlow_protocol() throws Exception {
        String email = "lifecycle@test.com";
        String password = "password123";
        createTestUserProtocol(email, password, true, false);

        // 1. Full Login
        MvcResult loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.refresh_token", notNullValue()))
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        Map<?, ?> responseMap = objectMapper.readValue(responseBody, Map.class);
        String accessToken1 = (String) responseMap.get("access_token");
        String refreshToken1 = (String) responseMap.get("refresh_token");

        // 2. Protected Call

        ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
                .consumerPid("urn:uuid:" + UUID.randomUUID())
                .providerPid("urn:uuid:" + UUID.randomUUID())
                .callbackAddress("callback")
                .state(ContractNegotiationState.REQUESTED)
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(TENANT_ID)
                .build();
        contractNegotiationRepository.save(cn);

        mockMvc.perform(get("/" + TENANT_ID + "/negotiations/" + cn.getProviderPid())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // 3. Refresh (Rotation)
        MvcResult refreshResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refresh_token", refreshToken1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.refresh_token", notNullValue()))
                .andReturn();

        String refreshResponseBody = refreshResult.getResponse().getContentAsString();
        Map<?, ?> refreshResponseMap = objectMapper.readValue(refreshResponseBody, Map.class);
        String accessToken2 = (String) refreshResponseMap.get("access_token");
        String refreshToken2 = (String) refreshResponseMap.get("refresh_token");

        // 3. Protected Call
        mockMvc.perform(get("/" + TENANT_ID + "/negotiations/" + cn.getProviderPid())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken2)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // 5. Logout
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refresh_token", refreshToken2))))
                .andExpect(status().isOk());

        // 6. Refresh-with-revoked-token rejection
        // Attempting to refresh with the rotated token (refreshToken1) should be rejected
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refresh_token", refreshToken1))))
                .andExpect(status().isUnauthorized());

        // Attempting to refresh with the logged-out token (refreshToken2) should also be rejected
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refresh_token", refreshToken2))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protocol endpoint validation: missing Authorization header returns 401")
    void protocolMissingAuth_returns401() throws Exception {
        ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
                .consumerPid("urn:uuid:" + UUID.randomUUID())
                .providerPid("urn:uuid:" + UUID.randomUUID())
                .callbackAddress("callback")
                .state(ContractNegotiationState.REQUESTED)
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(TENANT_ID)
                .build();
        contractNegotiationRepository.save(cn);

        mockMvc.perform(get("/" + TENANT_ID + "/negotiations/" + cn.getProviderPid())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protocol endpoint validation: tampered/invalid-signature JWT returns 401")
    void protocolTamperedJwt_returns401() throws Exception {
        String email = "lifecycle@test.com";
        String password = "password123";
        createTestUserProtocol(email, password, true, false);

        MvcResult loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        Map<?, ?> responseMap = objectMapper.readValue(responseBody, Map.class);
        String accessToken = (String) responseMap.get("access_token");

        // Tamper with the signature of the retrieved JWT
        String tamperedToken = accessToken + "invalid_signature_suffix";

        ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
                .consumerPid("urn:uuid:" + UUID.randomUUID())
                .providerPid("urn:uuid:" + UUID.randomUUID())
                .callbackAddress("callback")
                .state(ContractNegotiationState.REQUESTED)
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(TENANT_ID)
                .build();
        contractNegotiationRepository.save(cn);

        mockMvc.perform(get("/" + TENANT_ID + "/negotiations/" + cn.getProviderPid())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protocol endpoint validation: expired JWT returns 401")
    void protocolExpiredJwt_returns401() throws Exception {
        ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
                .consumerPid("urn:uuid:" + UUID.randomUUID())
                .providerPid("urn:uuid:" + UUID.randomUUID())
                .callbackAddress("callback")
                .state(ContractNegotiationState.REQUESTED)
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(TENANT_ID)
                .build();
        contractNegotiationRepository.save(cn);

        // A mock, pre-expired token representation (payload expiration claim set to Jan 1, 2020)
        // This fails the expiration check or signature verification, validating filter protection
        String expiredToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJzdWIiOiJjb25uZWN0b3JAdGVzdC5jb20iLCJyb2xlcyI6WyJST0xFX0NPTk5FQ1RPUiJdLCJleHAiOjE1NzgwOTI4MDB9." +
                "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

        mockMvc.perform(get("/" + TENANT_ID + "/negotiations/" + cn.getProviderPid())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protocol endpoint validation: Basic Auth header under INTERNAL mode returns 401 (regression check)")
    void protocolBasicAuth_returns401() throws Exception {
        ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
                .consumerPid("urn:uuid:" + UUID.randomUUID())
                .providerPid("urn:uuid:" + UUID.randomUUID())
                .callbackAddress("callback")
                .state(ContractNegotiationState.REQUESTED)
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(TENANT_ID)
                .build();
        contractNegotiationRepository.save(cn);

        // Attempts to authorize with valid internal Basic Auth format must be rejected with 401 in the protocol zone
        mockMvc.perform(get("/" + TENANT_ID + "/negotiations/" + cn.getProviderPid())
                        .header(HttpHeaders.AUTHORIZATION, "Basic Y29ubmVjdG9yQHRlc3QuY29tOnBhc3N3b3JkMTIz")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cross access test: ADMIN role sends request to protocol endpoint and vice versa")
    void crossAccessTest() throws Exception {
        String email = "admin@test.com";
        String password = "password123";
        createTestUserAPI(email, password, true, false);

        // 1. Full Login ADMIN
        MvcResult loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.refresh_token", notNullValue()))
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        Map<?, ?> responseMap = objectMapper.readValue(responseBody, Map.class);
        String accessToken1 = (String) responseMap.get("access_token");

        // 2. ADMIN request to protocol
        ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
                .consumerPid("urn:uuid:" + UUID.randomUUID())
                .providerPid("urn:uuid:" + UUID.randomUUID())
                .callbackAddress("callback")
                .state(ContractNegotiationState.REQUESTED)
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(TENANT_ID)
                .build();
        contractNegotiationRepository.save(cn);

        mockMvc.perform(get("/" + TENANT_ID + "/negotiations/" + cn.getProviderPid())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        String email2 = "connector@test.com";
        String password2 = "password123";
        createTestUserProtocol(email2, password2, true, false);

        // 3. Full Login CONNECTOR
        MvcResult loginResult2 = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email2,
                                "password", password2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.refresh_token", notNullValue()))
                .andReturn();

        String responseBody2 = loginResult2.getResponse().getContentAsString();
        Map<?, ?> responseMap2 = objectMapper.readValue(responseBody2, Map.class);
        String accessToken2 = (String) responseMap2.get("access_token");

        // 4. CONNECTOR request to API
        mockMvc.perform(get(ApiEndpoints.CATALOG_DATA_SERVICES_V1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken2)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Login failure paths: bad password, disabled, and locked")
    void loginFailurePaths() throws Exception {
        String email = "failures@test.com";
        String password = "password123";

        createTestUserAPI(email, password, true, false);

        // A. Bad Password
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "wrongpassword"))))
                .andExpect(status().isUnauthorized());

        // B. Disabled User
        String disabledEmail = "disabled@test.com";
        createTestUserAPI(disabledEmail, password, false, false);

        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", disabledEmail,
                                "password", password))))
                .andExpect(status().isUnauthorized());

        // C. Locked User
        String lockedEmail = "locked@test.com";
        createTestUserAPI(lockedEmail, password, true, true);

        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", lockedEmail,
                                "password", password))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Validation failure paths: empty email, empty password, or empty refresh token")
    void validationFailurePaths() throws Exception {
        // Empty Email
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "",
                                "password", "password123"))))
                .andExpect(status().isBadRequest());

        // Empty Password
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "test@test.com",
                                "password", ""))))
                .andExpect(status().isBadRequest());

        // Empty Refresh Token
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refresh_token", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Failing authorization: Using refresh token instead of access token")
    void failAuth_usingRefreshToken() throws Exception {
        String email = "lifecycle@test.com";
        String password = "password123";
        createTestUserAPI(email, password, true, false);

        // 1. Full Login
        MvcResult loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.refresh_token", notNullValue()))
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        Map<?, ?> responseMap = objectMapper.readValue(responseBody, Map.class);
        String refreshToken1 = (String) responseMap.get("refresh_token");

        // 2. Protected Call
        mockMvc.perform(get(ApiEndpoints.CATALOG_DATA_SERVICES_V1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

    }

    private void createTestUserAPI(String email, String password, boolean enabled, boolean locked) {
        User user = new User("urn:uuid:" + UUID.randomUUID(), "John", "Doe",
                email, passwordEncoder.encode(password),
                enabled, false, locked, Role.ADMIN, TENANT_ID);
        userRepository.save(user);
    }

    private void createTestUserProtocol(String email, String password, boolean enabled, boolean locked) {
        User user = new User("urn:uuid:" + UUID.randomUUID(), "John", "Doe",
                email, passwordEncoder.encode(password),
                enabled, false, locked, Role.CONNECTOR, TENANT_ID);
        userRepository.save(user);
    }
}