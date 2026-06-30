package it.eng.connector.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.keycloak.representations.idm.RealmRepresentation;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Base integration test for Keycloak-secured scenarios.
 */
@TestPropertySource(properties = "application.auth.provider=KEYCLOAK")
public abstract class BaseKeycloakIntegrationTest extends BaseIntegrationTest {

    private static final String KEYCLOAK_REALM = "dsp-connector";
    private static final String KEYCLOAK_UI_CLIENT_ID = "dsp-connector-ui";
    private static final String KEYCLOAK_BACKEND_CLIENT_ID = "dsp-connector-consumer-backend";
    private static final String KEYCLOAK_BACKEND_CLIENT_SECRET = "dsp-connector-consumer-secret";
    private static final String KEYCLOAK_ADMIN_USERNAME = "admin@test.com";
    private static final String KEYCLOAK_ADMIN_PASSWORD = "admin123";
    private static final String KEYCLOAK_CONNECTOR_USERNAME = "connector@test.com";
    private static final String KEYCLOAK_CONNECTOR_PASSWORD = "connector123";
    private static final String KEYCLOAK_SUPER_ADMIN_USERNAME = "superadmin@test.com";
    private static final String KEYCLOAK_SUPER_ADMIN_PASSWORD = "password";
    private static final Duration KEYCLOAK_STARTUP_TIMEOUT = Duration.ofMinutes(3);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static final KeycloakContainer KEYCLOAK_CONTAINER = new KeycloakContainer()
            .withStartupTimeout(KEYCLOAK_STARTUP_TIMEOUT);

    static {
        KEYCLOAK_CONTAINER.start();
        importRealm();
    }

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("application.auth.provider", () -> "KEYCLOAK");
        registry.add("server.port", () -> "0");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", BaseKeycloakIntegrationTest::realmUrl);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> realmUrl() + "/protocol/openid-connect/certs");
        registry.add("application.keycloak.backend.client-id", () -> KEYCLOAK_BACKEND_CLIENT_ID);
        registry.add("application.keycloak.backend.client-secret", () -> KEYCLOAK_BACKEND_CLIENT_SECRET);
        registry.add("application.keycloak.backend.token-url",
                () -> realmUrl() + "/protocol/openid-connect/token");
        registry.add("application.callback.address", () -> "http://localhost:8080/");
        registry.add("application.automatic.negotiation", () -> "false");
        registry.add("application.encryption.key", () -> "5xplehys9mtcatb");
        registry.add("s3.accessKey", () -> "minioadmin");
        registry.add("s3.secretKey", () -> "minioadmin");
        registry.add("s3.region", () -> "us-east-1");
        registry.add("s3.bucketName", () -> "dsp-true-connector");
        registry.add("server.ssl.enabled", () -> "false");
        registry.add("application.usagecontrol.enabled", () -> "true");
        registry.add("application.usagecontrol.constraint.location", () -> "EU");
        registry.add("application.usagecontrol.constraint.purpose", () -> "demo");
    }

    /**
     * Returns a bearer token for the Keycloak admin test user.
     *
     * @return a valid bearer token with the admin realm role
     * @throws Exception when the token cannot be obtained
     */
    protected String adminAccessToken() throws Exception {
        return accessToken(KEYCLOAK_ADMIN_USERNAME, KEYCLOAK_ADMIN_PASSWORD);
    }

    /**
     * Returns a bearer token for the Keycloak connector test user.
     *
     * @return a valid bearer token with the connector realm role
     * @throws Exception when the token cannot be obtained
     */
    protected String connectorAccessToken() throws Exception {
        return accessToken(KEYCLOAK_CONNECTOR_USERNAME, KEYCLOAK_CONNECTOR_PASSWORD);
    }

    /**
     * Returns a bearer token for the Keycloak super-admin test user.
     *
     * @return a valid bearer token with the super_admin realm role
     * @throws Exception when the token cannot be obtained
     */
    protected String superAdminAccessToken() throws Exception {
        return accessToken(KEYCLOAK_SUPER_ADMIN_USERNAME, KEYCLOAK_SUPER_ADMIN_PASSWORD);
    }

    /**
     * Formats the given JWT for an Authorization header.
     *
     * @param accessToken the raw JWT access token
     * @return the Bearer header value
     */
    protected String bearerToken(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String accessToken(String username, String password) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(realmUrl() + "/protocol/openid-connect/token"))
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(formBody(username, password)))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Unable to obtain Keycloak access token for user '%s'. Status: %d, body: %s"
                    .formatted(username, response.statusCode(), response.body()));
        }

        JsonNode responseJson = jsonMapper.readTree(response.body());
        JsonNode accessToken = responseJson.get("access_token");
        if (accessToken == null || accessToken.asText().isBlank()) {
            throw new IllegalStateException("Keycloak token response does not contain a usable access_token field.");
        }
        return accessToken.asText();
    }

    private static Path resolveRealmImportFile() {
        Path currentDirectory = Path.of("").toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                currentDirectory.resolve("ci/docker/keycloak_resources/realm-dsp-connector.json"),
                currentDirectory.resolve("../ci/docker/keycloak_resources/realm-dsp-connector.json").normalize()
        );

        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to find ci/docker/keycloak_resources/realm-dsp-connector.json for Keycloak integration tests."
                ));
    }

    private static String realmUrl() {
        return KEYCLOAK_CONTAINER.getAuthServerUrl() + "/realms/" + KEYCLOAK_REALM;
    }

    private static void importRealm() {
        try {
            RealmRepresentation realmRepresentation = new ObjectMapper()
                    .readValue(Files.readString(resolveRealmImportFile()), RealmRepresentation.class);

            KEYCLOAK_CONTAINER.getKeycloakAdminClient().realms().create(realmRepresentation);
            boolean realmPresent = KEYCLOAK_CONTAINER.getKeycloakAdminClient().realms().findAll().stream()
                    .anyMatch(realm -> KEYCLOAK_REALM.equals(realm.getRealm()));
            if (!realmPresent) {
                throw new IllegalStateException("Keycloak test realm was not created successfully.");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize Keycloak test realm.", exception);
        }
    }

    private static String formBody(String username, String password) {
        return "grant_type=password"
                + "&client_id=" + encode(KEYCLOAK_UI_CLIENT_ID)
                + "&username=" + encode(username)
                + "&password=" + encode(password);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
