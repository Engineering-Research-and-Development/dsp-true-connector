package it.eng.tools.auth.keycloak.issuer;

import it.eng.tools.auth.keycloak.KeycloakTestHttpClients;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import it.eng.tools.auth.keycloak.KeycloakDiscoveryClient;
import it.eng.tools.auth.keycloak.KeycloakTrustProperties;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Token-level cases that a real Keycloak cannot produce, run through {@link TrustedIssuerRegistry} and
 * {@link IssuerTokenValidators} together: a bad signature, an expired {@code exp} and a future {@code nbf}.
 * A well-formed token with the same claims passes, so each failure is caused by the one tampered property.
 * The same cases with real tokens (wrong {@code aud}, {@code azp}, {@code tenant_id}) are in
 * {@code KeycloakMultiIssuerApiIT}.
 */
class TenantTokenMatrixTest {

    private static final String TENANT_REALM = "tenant-EngineeringA";
    private static final String TENANT_ID = "engineering-a";
    private static final String AUDIENCE = "dsp-true-connector";
    private static final String UI_CLIENT = "keycloak-api";
    private static final String PLATFORM_CLIENT = "dsp-connector-backend";
    private static final long FAR_SECONDS = 600;

    private static RSAKey tenantKey;
    private static RSAKey attackerKey;

    private HttpServer server;
    private KeycloakTrustProperties properties;
    private TrustedIssuerRegistry registry;
    private JwtDecoder decoder;

    @BeforeAll
    static void generateKeys() throws JOSEException {
        tenantKey = new RSAKeyGenerator(2048).keyID("tenant-1").generate();
        attackerKey = new RSAKeyGenerator(2048).keyID("tenant-1").generate();
    }

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String issuer = properties.issuerFor(TENANT_REALM);
            String path = exchange.getRequestURI().getPath();
            String body = path.endsWith("/.well-known/openid-configuration")
                    ? "{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/protocol/openid-connect/certs\"}"
                    : new JWKSet(tenantKey.toPublicJWK()).toString();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        properties = new KeycloakTrustProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setDiscoveryReadTimeout(Duration.ofSeconds(2));
        properties.init();
        TenantRepository tenantRepository = mock(TenantRepository.class);
        when(tenantRepository.findFirstByRealmAndEnabledTrue(TENANT_REALM)).thenReturn(Optional.of(
                Tenant.Builder.newInstance().id(TENANT_ID).name(TENANT_ID).participantId("urn:" + TENANT_ID)
                        .realm(TENANT_REALM).enabled(true).build()));
        registry = new TrustedIssuerRegistry(properties, KeycloakTestHttpClients.discoveryClient(properties), tenantRepository,
                KeycloakTestHttpClients.forProperties(properties));
        IssuerTokenValidators validators = new IssuerTokenValidators(AUDIENCE, PLATFORM_CLIENT, UI_CLIENT);
        decoder = registry.issuerSelectingDecoder(Set.of(IssuerType.TENANT), validators::forIssuer);
    }

    @AfterEach
    void tearDown() {
        registry.close();
        server.stop(0);
    }

    @Test
    @DisplayName("A correctly signed, current tenant token passes the registry and the tenant rules")
    void validTokenPasses() throws JOSEException {
        assertEquals(TENANT_ID, decoder.decode(sign(tenantKey, claims())).getClaimAsString("tenant_id"));
    }

    @Test
    @DisplayName("A token signed with a foreign key that claims the tenant realm's kid is rejected")
    void badSignatureIsRejected() throws JOSEException {
        String forged = sign(attackerKey, claims());

        assertThrows(BadJwtException.class, () -> decoder.decode(forged));
    }

    @Test
    @DisplayName("A genuine token whose payload was changed after signing is rejected")
    void tamperedPayloadIsRejected() throws JOSEException {
        String[] parts = sign(tenantKey, claims()).split("\\.");
        JWTClaimsSet changed = claims(builder -> builder.claim("tenant_id", "engineering-b"));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(changed.toString().getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + payload + "." + parts[2];

        assertThrows(BadJwtException.class, () -> decoder.decode(tampered));
    }

    @Test
    @DisplayName("An expired token is rejected")
    void expiredTokenIsRejected() throws JOSEException {
        Instant past = Instant.now().minusSeconds(FAR_SECONDS);
        String expired = sign(tenantKey, claims(builder -> builder
                .issueTime(Date.from(past.minusSeconds(FAR_SECONDS)))
                .expirationTime(Date.from(past))));

        assertThrows(JwtValidationException.class, () -> decoder.decode(expired));
    }

    @Test
    @DisplayName("A token that is not valid yet (future nbf) is rejected")
    void futureNotBeforeIsRejected() throws JOSEException {
        String notYetValid = sign(tenantKey, claims(builder -> builder
                .notBeforeTime(Date.from(Instant.now().plusSeconds(FAR_SECONDS)))));

        assertThrows(JwtValidationException.class, () -> decoder.decode(notYetValid));
    }

    private JWTClaimsSet claims() {
        return claims(builder -> builder);
    }

    private JWTClaimsSet claims(UnaryOperator<JWTClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(properties.issuerFor(TENANT_REALM))
                .subject("user")
                .audience(AUDIENCE)
                .claim("azp", UI_CLIENT)
                .claim("tenant_id", TENANT_ID)
                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(FAR_SECONDS)));
        return customizer.apply(builder).build();
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID())
                .type(JOSEObjectType.JWT)
                .build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
