package it.eng.connector.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.eng.connector.dto.*;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.connector.repository.UserRepository;
import it.eng.connector.service.JwtTokenService;
import it.eng.connector.util.TestUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.IConstants;
import it.eng.tools.serializer.InstantDeserializer;
import it.eng.tools.serializer.InstantSerializer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.spring.EnableWireMock;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@SpringBootTest(
        webEnvironment = WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=8090"
        })
@AutoConfigureMockMvc
@EnableWireMock
@Testcontainers
public class BaseIntegrationTest {

    // starts a mongodb and s3 simulated cloud storage container; the containers are shared among all tests; docker must be running
    protected static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0.12");
    protected static final MinIOContainer minIOContainer = new MinIOContainer("minio/minio");

    @Autowired
    protected MockMvc mockMvc;
    
    @Autowired
    protected JwtTokenService jwtTokenService;
    
    @Autowired
    protected UserRepository userRepository;
    
    @Autowired
    protected PasswordEncoder passwordEncoder;
    
    protected JsonMapper jsonMapper;
    
    // Cache for JWT tokens to avoid repeated generation
    private final Map<String, String> tokenCache = new HashMap<>();

    protected String createNewId() {
        return "urn:uuid:" + UUID.randomUUID().toString();
    }
    
    // ===== INTEGRATION TEST DATA SETUP =====
    
    /**
     * Common test data constants for integration tests
     */
    protected static final String TEST_EMAIL = "integration@example.com";
    protected static final String TEST_PASSWORD = "Test123!";
    protected static final String TEST_FIRST_NAME = "Integration";
    protected static final String TEST_LAST_NAME = "Test";
    
    /**
     * Create and save a test user with the specified role
     * 
     * @param role the role for the user
     * @return the created and saved User
     */
    protected User createAndSaveTestUser(Role role) {
        return createAndSaveTestUser(role, TEST_EMAIL, TEST_PASSWORD);
    }
    
    /**
     * Create and save a test user with the specified role and credentials
     * 
     * @param role the role for the user
     * @param email the email for the user
     * @param password the password for the user
     * @return the created and saved User
     */
    protected User createAndSaveTestUser(Role role, String email, String password) {
        User user = TestUtil.createUser(role, email);
        user.setPassword(passwordEncoder.encode(password));
        return userRepository.save(user);
    }
    
    /**
     * Create and save an admin test user
     * 
     * @return the created and saved admin User
     */
    protected User createAndSaveAdminUser() {
        return createAndSaveTestUser(Role.ROLE_ADMIN);
    }
    
    /**
     * Create and save a regular test user
     * 
     * @return the created and saved regular User
     */
    protected User createAndSaveRegularUser() {
        return createAndSaveTestUser(Role.ROLE_USER);
    }
    
    /**
     * Create and save a connector test user
     * 
     * @return the created and saved connector User
     */
    protected User createAndSaveConnectorUser() {
        return createAndSaveTestUser(Role.ROLE_CONNECTOR);
    }
    
    /**
     * Clean up test users by email
     * 
     * @param email the email of the user to delete
     */
    protected void cleanupTestUser(String email) {
        userRepository.findByEmail(email).ifPresent(userRepository::delete);
    }
    
    /**
     * Clean up all test users created during integration tests
     */
    protected void cleanupAllTestUsers() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(TestUtil.TEST_EMAIL).ifPresent(userRepository::delete);
        // Add any other test emails that might be used
    }
    
    // ===== JWT Authentication Utilities =====
    
    /**
     * Generate JWT token for the given user email.
     * Uses caching to avoid repeated token generation for the same user.
     * 
     * @param userEmail the email of the user to generate token for
     * @return JWT token string
     * @throws RuntimeException if user not found
     */
    protected String getJwtToken(String userEmail) {
        return tokenCache.computeIfAbsent(userEmail, email -> {
            // Handle case where multiple users might exist with same email
            // by finding the first active user
            List<User> users = userRepository.findAll().stream()
                    .filter(u -> email.equals(u.getEmail()) && u.isEnabled())
                    .collect(Collectors.toList());
            
            if (users.isEmpty()) {
                throw new RuntimeException("Test user not found: " + email);
            }
            
            // Use the first enabled user found
            User user = users.get(0);
            return jwtTokenService.generateAccessToken(user);
        });
    }
    
    /**
     * Create HTTP headers with JWT authentication for the given user email.
     * 
     * @param userEmail the email of the user to authenticate as
     * @return HttpHeaders with Authorization header set
     */
    protected HttpHeaders createJwtHeaders(String userEmail) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + getJwtToken(userEmail));
        return headers;
    }
    
    /**
     * Convenience method to get JWT headers for admin user.
     * 
     * @return HttpHeaders for admin authentication
     */
    protected HttpHeaders adminHeaders() {
        return createJwtHeaders(TestUtil.ADMIN_USER);
    }
    
    /**
     * Convenience method to get JWT headers for regular user.
     * 
     * @return HttpHeaders for user authentication
     */
    protected HttpHeaders userHeaders() {
        return createJwtHeaders("user@mail.com");
    }
    
    /**
     * Convenience method to get JWT headers for connector user.
     * 
     * @return HttpHeaders for connector authentication
     */
    protected HttpHeaders connectorHeaders() {
        return createJwtHeaders(TestUtil.CONNECTOR_USER);
    }
    
    // ===== Authenticated Request Builders =====
    
    /**
     * Create an authenticated GET request with JWT headers.
     * 
     * @param uri the request URI
     * @param userEmail the email of the user to authenticate as
     * @return MockHttpServletRequestBuilder configured with JWT authentication
     */
    protected MockHttpServletRequestBuilder authenticatedGet(String uri, String userEmail) {
        return get(uri).headers(createJwtHeaders(userEmail));
    }
    
    /**
     * Create an authenticated POST request with JWT headers.
     * 
     * @param uri the request URI
     * @param userEmail the email of the user to authenticate as
     * @return MockHttpServletRequestBuilder configured with JWT authentication
     */
    protected MockHttpServletRequestBuilder authenticatedPost(String uri, String userEmail) {
        return post(uri).headers(createJwtHeaders(userEmail));
    }
    
    /**
     * Create an authenticated PUT request with JWT headers.
     * 
     * @param uri the request URI
     * @param userEmail the email of the user to authenticate as
     * @return MockHttpServletRequestBuilder configured with JWT authentication
     */
    protected MockHttpServletRequestBuilder authenticatedPut(String uri, String userEmail) {
        return put(uri).headers(createJwtHeaders(userEmail));
    }
    
    /**
     * Create an authenticated DELETE request with JWT headers.
     * 
     * @param uri the request URI
     * @param userEmail the email of the user to authenticate as
     * @return MockHttpServletRequestBuilder configured with JWT authentication
     */
    protected MockHttpServletRequestBuilder authenticatedDelete(String uri, String userEmail) {
        return delete(uri).headers(createJwtHeaders(userEmail));
    }
    
    /**
     * Create an authenticated PATCH request with JWT headers.
     * 
     * @param uri the request URI
     * @param userEmail the email of the user to authenticate as
     * @return MockHttpServletRequestBuilder configured with JWT authentication
     */
    protected MockHttpServletRequestBuilder authenticatedPatch(String uri, String userEmail) {
        return patch(uri).headers(createJwtHeaders(userEmail));
    }

    static {
        mongoDBContainer.start();
        // used for checking S3 storage during test debugging; will be exposed on random localhost port which can be checked with `docker ps`or some docker GUI
        minIOContainer.addExposedPort(9001);
        minIOContainer.start();
    }

    @DynamicPropertySource
    static void containersProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getHost);
        registry.add("spring.data.mongodb.port", mongoDBContainer::getFirstMappedPort);
        registry.add("s3.endpoint", minIOContainer::getS3URL);
        registry.add("s3.externalPresignedEndpoint", minIOContainer::getS3URL);

    }

    @BeforeEach
    public void setup() {
        SimpleModule instantConverterModule = new SimpleModule();
        instantConverterModule.addSerializer(Instant.class, new InstantSerializer());
        instantConverterModule.addDeserializer(Instant.class, new InstantDeserializer());
        jsonMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .configure(MapperFeature.USE_ANNOTATIONS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .addModule(instantConverterModule)
                .build();
    }

    protected JsonNode getContractNegotiationOverAPI() throws Exception {
        final ResultActions result =
                mockMvc.perform(
                        get(ApiEndpoints.NEGOTIATION_V1)
                                .param("role", IConstants.ROLE_CONSUMER)
                                .with(user(TestUtil.CONNECTOR_USER).password("password").roles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        JsonNode jsonNode = jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return jsonNode.path("response").path("data").path("content").get(0);
    }

    protected JsonNode getContractNegotiationOverAPI(String contractNegotiationId)
            throws Exception, JsonProcessingException, JsonMappingException, UnsupportedEncodingException {
        final ResultActions result =
                mockMvc.perform(
                        get(ApiEndpoints.NEGOTIATION_V1 + "/" + contractNegotiationId)
                                .with(user(TestUtil.CONNECTOR_USER).password("password").roles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected ContractNegotiation getContractNegotiationOverAPI(String consumerPid, String providerPid) throws Exception {
        final ResultActions result =
                mockMvc.perform(
                        get(ApiEndpoints.NEGOTIATION_V1)
                                .with(user(TestUtil.CONNECTOR_USER).password("password").roles("ADMIN"))
                                .param("consumerPid", consumerPid)
                                .param("providerPid", providerPid)
                                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        String json = result.andReturn().getResponse().getContentAsString();
        JsonNode jsonNode = jsonMapper.readTree(json);
        JsonNode toDeserialize = jsonNode.path("response").path("data").path("content").get(0);
        return NegotiationSerializer.deserializePlain(toDeserialize.toPrettyString(), ContractNegotiation.class);
    }

    protected void offerCheck(ContractNegotiation contractNegotiation, String offerId) {
        assertEquals(offerId, contractNegotiation.getOffer().getOriginalId());
    }

    protected void agreementCheck(ContractNegotiation contractNegotiation) {
        assertNotNull(contractNegotiation.getAgreement());
    }
    
    /**
     * Clear the JWT token cache to ensure test isolation.
     * This method should be called between tests to prevent token reuse.
     */
    protected void clearTokenCache() {
        tokenCache.clear();
    }
    
    // ===== INTEGRATION TEST DTO CREATION =====
    
    /**
     * Create a LoginRequest for integration tests
     * 
     * @return LoginRequest with default test credentials
     */
    protected LoginRequest createLoginRequest() {
        return TestUtil.createLoginRequest(TEST_EMAIL, TEST_PASSWORD);
    }
    
    /**
     * Create a LoginRequest with custom credentials
     * 
     * @param email the email for login
     * @param password the password for login
     * @return LoginRequest with custom credentials
     */
    protected LoginRequest createLoginRequest(String email, String password) {
        return TestUtil.createLoginRequest(email, password);
    }
    
    /**
     * Create a RegisterRequest for integration tests
     * 
     * @return RegisterRequest with default test data
     */
    protected RegisterRequest createRegisterRequest() {
        return TestUtil.createRegisterRequest(TEST_FIRST_NAME, TEST_LAST_NAME, TEST_EMAIL, TEST_PASSWORD);
    }
    
    /**
     * Create a RegisterRequest with custom data
     * 
     * @param firstName the first name
     * @param lastName the last name
     * @param email the email
     * @param password the password
     * @return RegisterRequest with custom data
     */
    protected RegisterRequest createRegisterRequest(String firstName, String lastName, String email, String password) {
        return TestUtil.createRegisterRequest(firstName, lastName, email, password);
    }
    
    /**
     * Create a RefreshTokenRequest for integration tests
     * 
     * @param refreshToken the refresh token
     * @return RefreshTokenRequest
     */
    protected RefreshTokenRequest createRefreshTokenRequest(String refreshToken) {
        return TestUtil.createRefreshTokenRequest(refreshToken);
    }
    
    /**
     * Create a LogoutRequest for integration tests
     * 
     * @param accessToken the access token
     * @param refreshToken the refresh token
     * @return LogoutRequest
     */
    protected LogoutRequest createLogoutRequest(String accessToken, String refreshToken) {
        return TestUtil.createLogoutRequest(accessToken, refreshToken);
    }
    
    // ===== ENHANCED AUTHENTICATED REQUEST BUILDERS =====
    
    /**
     * Create an authenticated GET request with JSON content type
     * 
     * @param uri the request URI
     * @param userEmail the email of the user to authenticate as
     * @return MockHttpServletRequestBuilder configured with JWT authentication and JSON content type
     */
    protected MockHttpServletRequestBuilder authenticatedGetJson(String uri, String userEmail) {
        return authenticatedGet(uri, userEmail).contentType(MediaType.APPLICATION_JSON);
    }
    
    /**
     * Create an authenticated POST request with JSON content type
     * 
     * @param uri the request URI
     * @param userEmail the email of the user to authenticate as
     * @return MockHttpServletRequestBuilder configured with JWT authentication and JSON content type
     */
    protected MockHttpServletRequestBuilder authenticatedPostJson(String uri, String userEmail) {
        return authenticatedPost(uri, userEmail).contentType(MediaType.APPLICATION_JSON);
    }
    
    /**
     * Create an authenticated PUT request with JSON content type
     * 
     * @param uri the request URI
     * @param userEmail the email of the user to authenticate as
     * @return MockHttpServletRequestBuilder configured with JWT authentication and JSON content type
     */
    protected MockHttpServletRequestBuilder authenticatedPutJson(String uri, String userEmail) {
        return authenticatedPut(uri, userEmail).contentType(MediaType.APPLICATION_JSON);
    }
    
    /**
     * Create an authenticated DELETE request with JSON content type
     * 
     * @param uri the request URI
     * @param userEmail the email of the user to authenticate as
     * @return MockHttpServletRequestBuilder configured with JWT authentication and JSON content type
     */
    protected MockHttpServletRequestBuilder authenticatedDeleteJson(String uri, String userEmail) {
        return authenticatedDelete(uri, userEmail).contentType(MediaType.APPLICATION_JSON);
    }
    
    // ===== CONVENIENCE METHODS FOR COMMON USER TYPES =====
    
    /**
     * Create an authenticated GET request for admin user
     * 
     * @param uri the request URI
     * @return MockHttpServletRequestBuilder configured with admin JWT authentication
     */
    protected MockHttpServletRequestBuilder adminGet(String uri) {
        return authenticatedGetJson(uri, TestUtil.ADMIN_USER);
    }
    
    /**
     * Create an authenticated POST request for admin user
     * 
     * @param uri the request URI
     * @return MockHttpServletRequestBuilder configured with admin JWT authentication
     */
    protected MockHttpServletRequestBuilder adminPost(String uri) {
        return authenticatedPostJson(uri, TestUtil.ADMIN_USER);
    }
    
    /**
     * Create an authenticated PUT request for admin user
     * 
     * @param uri the request URI
     * @return MockHttpServletRequestBuilder configured with admin JWT authentication
     */
    protected MockHttpServletRequestBuilder adminPut(String uri) {
        return authenticatedPutJson(uri, TestUtil.ADMIN_USER);
    }
    
    /**
     * Create an authenticated DELETE request for admin user
     * 
     * @param uri the request URI
     * @return MockHttpServletRequestBuilder configured with admin JWT authentication
     */
    protected MockHttpServletRequestBuilder adminDelete(String uri) {
        return authenticatedDeleteJson(uri, TestUtil.ADMIN_USER);
    }
    
    /**
     * Create an authenticated GET request for regular user
     * 
     * @param uri the request URI
     * @return MockHttpServletRequestBuilder configured with user JWT authentication
     */
    protected MockHttpServletRequestBuilder userGet(String uri) {
        return authenticatedGetJson(uri, TestUtil.REGULAR_USER);
    }
    
    /**
     * Create an authenticated POST request for regular user
     * 
     * @param uri the request URI
     * @return MockHttpServletRequestBuilder configured with user JWT authentication
     */
    protected MockHttpServletRequestBuilder userPost(String uri) {
        return authenticatedPostJson(uri, TestUtil.REGULAR_USER);
    }
    
    /**
     * Create an authenticated GET request for connector user
     * 
     * @param uri the request URI
     * @return MockHttpServletRequestBuilder configured with connector JWT authentication
     */
    protected MockHttpServletRequestBuilder connectorGet(String uri) {
        return authenticatedGetJson(uri, TestUtil.CONNECTOR_USER);
    }
    
    /**
     * Create an authenticated POST request for connector user
     * 
     * @param uri the request URI
     * @return MockHttpServletRequestBuilder configured with connector JWT authentication
     */
    protected MockHttpServletRequestBuilder connectorPost(String uri) {
        return authenticatedPostJson(uri, TestUtil.CONNECTOR_USER);
    }
    
    // ===== COMMON ASSERTION HELPERS =====
    
    /**
     * Assert that a response contains a successful JSON response
     * 
     * @param result the ResultActions to assert on
     * @throws Exception if assertion fails
     */
    protected void assertSuccessResponse(ResultActions result) throws Exception {
        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"success\": true}"));
    }
    
    /**
     * Assert that a response contains an error JSON response
     * 
     * @param result the ResultActions to assert on
     * @param expectedStatus the expected HTTP status code
     * @throws Exception if assertion fails
     */
    protected void assertErrorResponse(ResultActions result, int expectedStatus) throws Exception {
        result.andExpect(status().is(expectedStatus))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"success\": false}"));
    }
    
    /**
     * Assert that a response contains an unauthorized JSON response
     * 
     * @param result the ResultActions to assert on
     * @throws Exception if assertion fails
     */
    protected void assertUnauthorizedResponse(ResultActions result) throws Exception {
        assertErrorResponse(result, 401);
    }
    
    /**
     * Assert that a response contains a bad request JSON response
     * 
     * @param result the ResultActions to assert on
     * @throws Exception if assertion fails
     */
    protected void assertBadRequestResponse(ResultActions result) throws Exception {
        assertErrorResponse(result, 400);
    }
}
