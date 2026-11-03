package it.eng.tools.auth.keycloak.issuer;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import it.eng.tools.auth.keycloak.KeycloakDiscoveryClient;
import it.eng.tools.auth.keycloak.KeycloakTrustProperties;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TrustedIssuerRegistryTest {

    private static final String PLATFORM = "platform";
    private static final String ADMIN = "connector-admin";
    private static final String TENANT_REALM = "tenant-Engineering";
    private static final String TENANT_ID = "engineering";
    private static final String DISCOVERY_PATH = "/.well-known/openid-configuration";
    private static final String CERTS_PATH = "/protocol/openid-connect/certs";
    private static final Set<IssuerType> ALL_TYPES = Set.of(IssuerType.PLATFORM, IssuerType.ADMIN, IssuerType.TENANT);

    private static RSAKey platformKey;
    private static RSAKey adminKey;
    private static RSAKey tenantKey;
    private static RSAKey rotatedKey1;
    private static RSAKey rotatedKey2;
    private static RSAKey attackerKey;

    private HttpServer server;
    private final Map<String, StubResponse> routes = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final AtomicLong nanos = new AtomicLong();
    private KeycloakTrustProperties properties;
    private TenantRepository tenantRepository;
    private TrustedIssuerRegistry registry;

    private record StubResponse(int status, String body, String location) {
    }

    @BeforeAll
    static void generateKeys() throws JOSEException {
        platformKey = rsaKey("platform-1");
        adminKey = rsaKey("admin-1");
        tenantKey = rsaKey("tenant-1");
        rotatedKey1 = rsaKey("rotated-1");
        rotatedKey2 = rsaKey("rotated-2");
        attackerKey = rsaKey("attacker-1");
    }

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.add(path);
            StubResponse response = routes.getOrDefault(path, new StubResponse(404, "", null));
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if (response.location() != null) {
                exchange.getResponseHeaders().add("Location", response.location());
            }
            exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
        properties = new KeycloakTrustProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setDiscoveryReadTimeout(Duration.ofSeconds(2));
        properties.init();
        tenantRepository = mock(TenantRepository.class);
        registry = newRegistry(IssuerRegistrySettings.defaults());
    }

    @AfterEach
    void tearDown() {
        registry.close();
        server.stop(0);
    }

    // --- resolve -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("Static issuers resolve by exact match without a tenant lookup")
    void resolvesStaticIssuers() {
        TrustedIssuer platform = registry.resolve(issuer(PLATFORM)).orElseThrow();
        assertEquals(new TrustedIssuer(issuer(PLATFORM), PLATFORM, IssuerType.PLATFORM, null), platform);
        TrustedIssuer admin = registry.resolve(issuer(ADMIN)).orElseThrow();
        assertEquals(new TrustedIssuer(issuer(ADMIN), ADMIN, IssuerType.ADMIN, null), admin);
        verify(tenantRepository, never()).findFirstByRealmAndEnabledTrue(anyString());
        assertTrue(requests.isEmpty());
    }

    @Test
    @DisplayName("An enabled tenant realm resolves to a tenant issuer with its tenant id")
    void resolvesEnabledTenant() {
        when(tenantRepository.findFirstByRealmAndEnabledTrue(TENANT_REALM))
                .thenReturn(Optional.of(tenant(TENANT_ID, TENANT_REALM, true)));
        TrustedIssuer tenant = registry.resolve(issuer(TENANT_REALM)).orElseThrow();
        assertEquals(new TrustedIssuer(issuer(TENANT_REALM), TENANT_REALM, IssuerType.TENANT, TENANT_ID), tenant);
        assertTrue(requests.isEmpty());
    }

    @Test
    @DisplayName("A well-formed realm without an enabled tenant does not resolve")
    void unknownTenantRealmDoesNotResolve() {
        assertTrue(registry.resolve(issuer("unknown")).isEmpty());
        verify(tenantRepository).findFirstByRealmAndEnabledTrue("unknown");
        assertTrue(requests.isEmpty());
    }

    @ParameterizedTest
    @DisplayName("Foreign, malformed, traversal and reserved issuers are rejected without lookup or request")
    @ValueSource(strings = {
        "https://evil.example/realms/x",
        "https://evil.example/realms/platform",
        "BASE/realms/",
        "BASE/realms",
        "BASE/realm/tenant",
        "BASE0/realms/tenant",
        "BASE/realms/a/../platform",
        "BASE/realms/../realms/platform",
        "BASE/realms/..",
        "BASE/realms/.",
        "BASE/realms/platform/",
        "BASE/realms/tenant?x=1",
        "BASE/realms/tenant#x",
        "BASE/realms/ten ant",
        "BASE/realms/tenant%2F..",
        "BASE/realms/tenant\n",
        "BASE/realms/master",
        "BASE/realms/MASTER",
        "BASE/realms/Platform",
        "BASE/realms/CONNECTOR-ADMIN",
        ""
    })
    void rejectsWithoutLookup(String value) {
        String iss = value.replace("BASE", properties.getBaseUrl());
        assertTrue(registry.resolve(iss).isEmpty());
        verify(tenantRepository, never()).findFirstByRealmAndEnabledTrue(anyString());
        assertTrue(requests.isEmpty());
    }

    @Test
    @DisplayName("A null issuer does not resolve")
    void nullIssuerDoesNotResolve() {
        assertTrue(registry.resolve(null).isEmpty());
    }

    @Test
    @DisplayName("A repository result that is disabled or bound to another realm is ignored")
    void ignoresInconsistentRepositoryResults() {
        when(tenantRepository.findFirstByRealmAndEnabledTrue(TENANT_REALM))
                .thenReturn(Optional.of(tenant(TENANT_ID, TENANT_REALM, false)))
                .thenReturn(Optional.of(tenant(TENANT_ID, "other-realm", true)));
        assertTrue(registry.resolve(issuer(TENANT_REALM)).isEmpty());
        assertTrue(registry.resolve(issuer(TENANT_REALM)).isEmpty());
    }

    // --- issuerSelectingDecoder: rejection without network ---------------------------------------------

    @Test
    @DisplayName("Unknown, traversal, malformed, issuer-less and alg none tokens make zero requests")
    void rejectedTokensMakeNoRequests() throws JOSEException {
        serveRealm(PLATFORM, platformKey);
        JwtDecoder decoder = registry.issuerSelectingDecoder(ALL_TYPES);
        List<String> tokens = new ArrayList<>();
        tokens.add(token(attackerKey, "https://evil.example/realms/x"));
        tokens.add(token(attackerKey, issuer("unknown")));
        tokens.add(token(attackerKey, properties.getBaseUrl() + "/realms/a/../" + PLATFORM));
        tokens.add(token(attackerKey, properties.getBaseUrl() + "/realms/master"));
        tokens.add(token(attackerKey, null));
        tokens.add(new PlainJWT(claims(issuer(PLATFORM))).serialize());
        tokens.add("not-a-jwt");
        tokens.add("a.b.c");
        tokens.add(" ");
        for (String token : tokens) {
            assertThrows(BadJwtException.class, () -> decoder.decode(token));
        }
        assertThrows(BadJwtException.class, () -> decoder.decode(null));
        assertTrue(requests.isEmpty(), "unexpected requests: " + requests);
    }

    @Test
    @DisplayName("An issuer type outside the allowed set is rejected without a request")
    void disallowedTypeRejectedWithoutRequest() throws JOSEException {
        serveRealm(PLATFORM, platformKey);
        JwtDecoder tenantOnly = registry.issuerSelectingDecoder(Set.of(IssuerType.TENANT));
        String token = token(platformKey, issuer(PLATFORM));
        assertThrows(BadJwtException.class, () -> tenantOnly.decode(token));
        JwtDecoder none = registry.issuerSelectingDecoder(Set.of());
        assertThrows(BadJwtException.class, () -> none.decode(token));
        assertTrue(requests.isEmpty());
    }

    @Test
    @DisplayName("A repository failure leaves the request unauthenticated without a request to Keycloak")
    void repositoryFailureIsJwtException() throws JOSEException {
        when(tenantRepository.findFirstByRealmAndEnabledTrue(TENANT_REALM))
                .thenThrow(new DataAccessResourceFailureException("down"));
        JwtDecoder decoder = registry.issuerSelectingDecoder(ALL_TYPES);
        String token = token(tenantKey, issuer(TENANT_REALM));
        JwtException e = assertThrows(JwtException.class, () -> decoder.decode(token));
        assertFalse(e instanceof BadJwtException);
        assertTrue(requests.isEmpty());
    }

    // --- per-issuer keys ----------------------------------------------------------------------------------

    @Test
    @DisplayName("Each trusted issuer verifies tokens signed with its own key")
    void eachIssuerVerifiesItsOwnKey() throws JOSEException {
        serveRealm(PLATFORM, platformKey);
        serveRealm(ADMIN, adminKey);
        serveRealm(TENANT_REALM, tenantKey);
        when(tenantRepository.findFirstByRealmAndEnabledTrue(TENANT_REALM))
                .thenReturn(Optional.of(tenant(TENANT_ID, TENANT_REALM, true)));
        JwtDecoder decoder = registry.issuerSelectingDecoder(ALL_TYPES);

        assertEquals(issuer(PLATFORM), decoder.decode(token(platformKey, issuer(PLATFORM))).getIssuer().toString());
        assertEquals(issuer(ADMIN), decoder.decode(token(adminKey, issuer(ADMIN))).getIssuer().toString());
        assertEquals(issuer(TENANT_REALM), decoder.decode(token(tenantKey, issuer(TENANT_REALM))).getIssuer().toString());
    }

    @Test
    @DisplayName("A token signed with another issuer's or a foreign key is rejected")
    void crossKeyRejected() throws JOSEException {
        serveRealm(PLATFORM, platformKey);
        serveRealm(TENANT_REALM, tenantKey);
        when(tenantRepository.findFirstByRealmAndEnabledTrue(TENANT_REALM))
                .thenReturn(Optional.of(tenant(TENANT_ID, TENANT_REALM, true)));
        JwtDecoder decoder = registry.issuerSelectingDecoder(ALL_TYPES);

        // Tenant key (and kid) with the platform issuer: the platform JWKS has no such key.
        assertThrows(JwtException.class, () -> decoder.decode(token(tenantKey, issuer(PLATFORM))));
        // Platform key with the tenant issuer.
        assertThrows(JwtException.class, () -> decoder.decode(token(platformKey, issuer(TENANT_REALM))));
        // Foreign key that claims the platform kid: the signature does not verify.
        assertThrows(BadJwtException.class,
                () -> decoder.decode(token(attackerKey, platformKey.getKeyID(), issuer(PLATFORM))));
        // The genuine tokens still verify.
        decoder.decode(token(platformKey, issuer(PLATFORM)));
        decoder.decode(token(tenantKey, issuer(TENANT_REALM)));
    }

    @Test
    @DisplayName("An expired token is rejected by the default validators")
    void expiredTokenRejected() throws JOSEException {
        serveRealm(PLATFORM, platformKey);
        JWTClaimsSet expired = new JWTClaimsSet.Builder()
                .issuer(issuer(PLATFORM))
                .subject("client")
                .expirationTime(Date.from(Instant.now().minusSeconds(600)))
                .build();
        String token = sign(platformKey, platformKey.getKeyID(), expired);
        JwtDecoder decoder = registry.issuerSelectingDecoder(ALL_TYPES);
        assertThrows(JwtValidationException.class, () -> decoder.decode(token));
    }

    // --- key rotation and JWK set cache ----------------------------------------------------------------

    @Test
    @DisplayName("An unknown kid triggers a JWKS refetch, the rotated key verifies and the removed key fails")
    void rotation() throws JOSEException {
        serveRealm(PLATFORM, rotatedKey1);
        JwtDecoder decoder = registry.issuerSelectingDecoder(ALL_TYPES);
        String token1 = token(rotatedKey1, issuer(PLATFORM));
        decoder.decode(token1);
        decoder.decode(token1);
        assertEquals(1, count(jwksPath(PLATFORM)));

        serveJwks(PLATFORM, rotatedKey2);
        decoder.decode(token(rotatedKey2, issuer(PLATFORM)));
        assertEquals(2, count(jwksPath(PLATFORM)));

        assertThrows(JwtException.class, () -> decoder.decode(token1));
        assertEquals(1, count(discoveryPath(PLATFORM)));
    }

    @Test
    @DisplayName("Refetches triggered by unknown kids are rate limited")
    void unknownKidRefetchIsRateLimited() throws JOSEException {
        serveRealm(PLATFORM, platformKey);
        JwtDecoder decoder = registry.issuerSelectingDecoder(ALL_TYPES);
        decoder.decode(token(platformKey, issuer(PLATFORM)));
        for (int i = 0; i < 10; i++) {
            String token = token(attackerKey, "unknown-" + i, issuer(PLATFORM));
            assertThrows(JwtException.class, () -> decoder.decode(token));
        }
        // The initial load plus at most one refetch within the rate limit interval.
        assertEquals(2, count(jwksPath(PLATFORM)));
        // Known keys keep working while the refetch is rate limited.
        decoder.decode(token(platformKey, issuer(PLATFORM)));
    }

    @Test
    @DisplayName("The JWK set is loaded again once its cache time-to-live has passed")
    void jwksReloadedAfterTimeToLive() throws Exception {
        registry.close();
        registry = newRegistry(new IssuerRegistrySettings(10, Duration.ofHours(1), Duration.ofSeconds(5),
                Duration.ofMillis(300), Duration.ofMillis(50), Duration.ofMillis(50), Duration.ofMillis(100), 64 * 1024));
        serveRealm(PLATFORM, platformKey);
        JwtDecoder decoder = registry.issuerSelectingDecoder(ALL_TYPES);
        decoder.decode(token(platformKey, issuer(PLATFORM)));
        assertEquals(1, count(jwksPath(PLATFORM)));
        TimeUnit.MILLISECONDS.sleep(400);
        decoder.decode(token(platformKey, issuer(PLATFORM)));
        assertEquals(2, count(jwksPath(PLATFORM)));
        assertEquals(1, count(discoveryPath(PLATFORM)));
    }

    // --- dynamic tenants ------------------------------------------------------------------------------

    @Test
    @DisplayName("A tenant enabled later is trusted, a disabled tenant is not, and re-enabling reloads keys")
    void dynamicTenant() throws JOSEException {
        serveRealm(TENANT_REALM, tenantKey);
        JwtDecoder decoder = registry.issuerSelectingDecoder(Set.of(IssuerType.TENANT));
        String token = token(tenantKey, issuer(TENANT_REALM));

        assertThrows(BadJwtException.class, () -> decoder.decode(token));
        assertTrue(requests.isEmpty());

        when(tenantRepository.findFirstByRealmAndEnabledTrue(TENANT_REALM))
                .thenReturn(Optional.of(tenant(TENANT_ID, TENANT_REALM, true)));
        decoder.decode(token);
        assertEquals(1, count(discoveryPath(TENANT_REALM)));

        when(tenantRepository.findFirstByRealmAndEnabledTrue(TENANT_REALM)).thenReturn(Optional.empty());
        assertThrows(BadJwtException.class, () -> decoder.decode(token));

        // The decoder was evicted when the tenant stopped resolving, so it is loaded again.
        when(tenantRepository.findFirstByRealmAndEnabledTrue(TENANT_REALM))
                .thenReturn(Optional.of(tenant(TENANT_ID, TENANT_REALM, true)));
        decoder.decode(token);
        assertEquals(2, count(discoveryPath(TENANT_REALM)));
    }

    @Test
    @DisplayName("A tenant moved to another realm is trusted only under the new realm")
    void reRealmedTenant() throws JOSEException {
        String newRealm = "tenant-EngineeringB";
        serveRealm(TENANT_REALM, tenantKey);
        serveRealm(newRealm, rotatedKey1);
        when(tenantRepository.findFirstByRealmAndEnabledTrue(TENANT_REALM))
                .thenReturn(Optional.of(tenant(TENANT_ID, TENANT_REALM, true)));
        JwtDecoder decoder = registry.issuerSelectingDecoder(Set.of(IssuerType.TENANT));
        String oldToken = token(tenantKey, issuer(TENANT_REALM));
        String newToken = token(rotatedKey1, issuer(newRealm));
        decoder.decode(oldToken);
        assertThrows(BadJwtException.class, () -> decoder.decode(newToken));

        when(tenantRepository.findFirstByRealmAndEnabledTrue(TENANT_REALM)).thenReturn(Optional.empty());
        when(tenantRepository.findFirstByRealmAndEnabledTrue(newRealm))
                .thenReturn(Optional.of(tenant(TENANT_ID, newRealm, true)));
        assertThrows(BadJwtException.class, () -> decoder.decode(oldToken));
        Jwt jwt = decoder.decode(newToken);
        assertEquals(issuer(newRealm), jwt.getIssuer().toString());
        assertEquals(TENANT_ID, registry.resolve(issuer(newRealm)).orElseThrow().tenantId());
    }

    // --- loading failures -----------------------------------------------------------------------------

    @Test
    @DisplayName("A discovery failure is a JwtException, throttled, and retried after the retry interval")
    void discoveryFailureIsNotCachedPermanently() throws JOSEException {
        routes.put(discoveryPath(PLATFORM), new StubResponse(500, "", null));
        JwtDecoder decoder = registry.issuerSelectingDecoder(ALL_TYPES);
        String token = token(platformKey, issuer(PLATFORM));

        JwtException first = assertThrows(JwtException.class, () -> decoder.decode(token));
        assertFalse(first instanceof BadJwtException);
        assertEquals(1, count(discoveryPath(PLATFORM)));

        serveRealm(PLATFORM, platformKey);
        assertThrows(JwtException.class, () -> decoder.decode(token));
        assertEquals(1, count(discoveryPath(PLATFORM)), "retry must be throttled");

        nanos.addAndGet(Duration.ofSeconds(6).toNanos());
        decoder.decode(token);
        assertEquals(2, count(discoveryPath(PLATFORM)));
    }

    @Test
    @DisplayName("A JWKS failure fails the load, which is retried after the retry interval")
    void jwksFailureIsNotCachedPermanently() throws JOSEException {
        serveRealm(PLATFORM, platformKey);
        routes.put(jwksPath(PLATFORM), new StubResponse(503, "", null));
        JwtDecoder decoder = registry.issuerSelectingDecoder(ALL_TYPES);
        String token = token(platformKey, issuer(PLATFORM));

        assertThrows(JwtException.class, () -> decoder.decode(token));
        serveJwks(PLATFORM, platformKey);
        assertThrows(JwtException.class, () -> decoder.decode(token));
        assertEquals(1, count(jwksPath(PLATFORM)));

        nanos.addAndGet(Duration.ofSeconds(6).toNanos());
        decoder.decode(token);
        assertEquals(2, count(discoveryPath(PLATFORM)));
    }

    @Test
    @DisplayName("A JWKS redirect is not followed")
    void jwksRedirectIsNotFollowed() throws JOSEException {
        serveRealm(PLATFORM, platformKey);
        String target = "/elsewhere/certs";
        routes.put(target, new StubResponse(200, new JWKSet(platformKey.toPublicJWK()).toString(), null));
        routes.put(jwksPath(PLATFORM), new StubResponse(302, "", properties.getBaseUrl() + target));
        JwtDecoder decoder = registry.issuerSelectingDecoder(ALL_TYPES);
        assertThrows(JwtException.class, () -> decoder.decode(token(platformKey, issuer(PLATFORM))));
        assertEquals(0, count(target));
    }

    @Test
    @DisplayName("A discovery document with an unusable jwks_uri fails the load")
    void invalidJwksUriFailsLoad() throws JOSEException {
        routes.put(discoveryPath(PLATFORM), new StubResponse(200,
                "{\"issuer\":\"" + issuer(PLATFORM) + "\",\"jwks_uri\":\"file:///etc/passwd\"}", null));
        JwtDecoder decoder = registry.issuerSelectingDecoder(ALL_TYPES);
        assertThrows(JwtException.class, () -> decoder.decode(token(platformKey, issuer(PLATFORM))));
    }

    @Test
    @DisplayName("A load that outlasts the load timeout fails, is throttled, and is retried after the retry interval")
    void slowLoadTimesOut() throws Exception {
        serveRealm(PLATFORM, platformKey);
        properties.setDiscoveryConnectTimeout(Duration.ofMillis(100));
        properties.setDiscoveryReadTimeout(Duration.ofMillis(100));
        KeycloakDiscoveryClient realClient = new KeycloakDiscoveryClient(properties);
        KeycloakDiscoveryClient slowClient = mock(KeycloakDiscoveryClient.class);
        CountDownLatch release = new CountDownLatch(1);
        when(slowClient.fetch(anyString()))
                .thenAnswer(invocation -> {
                    release.await();
                    throw new IllegalStateException("released");
                })
                .thenAnswer(invocation -> realClient.fetch(invocation.getArgument(0)));
        TrustedIssuerRegistry slowRegistry = new TrustedIssuerRegistry(properties, slowClient, tenantRepository,
                IssuerRegistrySettings.defaults(), nanos::get);
        try {
            TrustedIssuer platform = slowRegistry.resolve(issuer(PLATFORM)).orElseThrow();

            // Load timeout: 2 x (100 ms connect + 100 ms read) + 500 ms margin.
            JwtException timedOut = assertTimeoutPreemptively(Duration.ofSeconds(3),
                    () -> assertThrows(JwtException.class, () -> slowRegistry.decoderFor(platform)));
            assertTrue(timedOut.getMessage().contains("in time"));
            assertThrows(JwtException.class, () -> slowRegistry.decoderFor(platform));
            verify(slowClient, times(1)).fetch(anyString());

            nanos.addAndGet(Duration.ofSeconds(6).toNanos());
            JwtDecoder decoder = slowRegistry.decoderFor(platform);
            decoder.decode(token(platformKey, issuer(PLATFORM)));
            verify(slowClient, times(2)).fetch(anyString());
        } finally {
            release.countDown();
            slowRegistry.close();
        }
    }

    // --- decoder cache and validator hook --------------------------------------------------------------

    @Test
    @DisplayName("The decoder of an issuer is loaded once and reused")
    void decoderIsCachedPerIssuer() {
        serveRealm(PLATFORM, platformKey);
        TrustedIssuer platform = registry.resolve(issuer(PLATFORM)).orElseThrow();
        JwtDecoder first = registry.decoderFor(platform);
        assertSame(first, registry.decoderFor(platform));
        assertEquals(1, count(discoveryPath(PLATFORM)));
        assertEquals(1, count(jwksPath(PLATFORM)));
    }

    @Test
    @DisplayName("A trusted issuer whose URL does not match its realm is rejected without a request")
    void decoderForRejectsForgedIssuer() {
        TrustedIssuer forged = new TrustedIssuer("https://evil.example/realms/platform", PLATFORM,
                IssuerType.PLATFORM, null);
        assertThrows(IllegalArgumentException.class, () -> registry.decoderFor(forged));
        TrustedIssuer traversal = new TrustedIssuer(issuer(PLATFORM), "a/../platform", IssuerType.PLATFORM, null);
        assertThrows(IllegalArgumentException.class, () -> registry.decoderFor(traversal));
        assertTrue(requests.isEmpty());
    }

    @Test
    @DisplayName("Additional per-issuer validators run after the shared cached decoder")
    void additionalValidatorHook() throws JOSEException {
        serveRealm(TENANT_REALM, tenantKey);
        when(tenantRepository.findFirstByRealmAndEnabledTrue(TENANT_REALM))
                .thenReturn(Optional.of(tenant(TENANT_ID, TENANT_REALM, true)));
        AtomicReference<TrustedIssuer> seen = new AtomicReference<>();
        JwtDecoder strict = registry.issuerSelectingDecoder(Set.of(IssuerType.TENANT), issuer -> {
            seen.set(issuer);
            return jwt -> jwt.getAudience() != null && jwt.getAudience().contains("dsp-true-connector")
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "audience", null));
        });
        JwtDecoder lenient = registry.issuerSelectingDecoder(Set.of(IssuerType.TENANT));

        String withoutAudience = token(tenantKey, issuer(TENANT_REALM));
        JwtValidationException e = assertThrows(JwtValidationException.class, () -> strict.decode(withoutAudience));
        assertEquals("audience", e.getErrors().iterator().next().getDescription());
        assertEquals(TENANT_ID, seen.get().tenantId());
        lenient.decode(withoutAudience);

        JWTClaimsSet withAudience = new JWTClaimsSet.Builder(claims(issuer(TENANT_REALM)))
                .audience("dsp-true-connector")
                .build();
        strict.decode(sign(tenantKey, tenantKey.getKeyID(), withAudience));
        assertEquals(1, count(discoveryPath(TENANT_REALM)));
    }

    @Test
    @DisplayName("A failing load surfaces the cause as a JwtException from decoderFor")
    void decoderForFailureIsJwtException() {
        TrustedIssuer platform = registry.resolve(issuer(PLATFORM)).orElseThrow();
        JwtException e = assertThrows(JwtException.class, () -> registry.decoderFor(platform));
        assertTrue(e.getMessage().contains(issuer(PLATFORM)));
    }

    // --- helpers ----------------------------------------------------------------------------------------

    private TrustedIssuerRegistry newRegistry(IssuerRegistrySettings settings) {
        return new TrustedIssuerRegistry(properties, new KeycloakDiscoveryClient(properties), tenantRepository,
                settings, nanos::get);
    }

    private String issuer(String realm) {
        return properties.issuerFor(realm);
    }

    private String discoveryPath(String realm) {
        return "/realms/" + realm + DISCOVERY_PATH;
    }

    private String jwksPath(String realm) {
        return "/realms/" + realm + CERTS_PATH;
    }

    private void serveRealm(String realm, JWK... keys) {
        String iss = issuer(realm);
        routes.put(discoveryPath(realm), new StubResponse(200,
                "{\"issuer\":\"" + iss + "\",\"jwks_uri\":\"" + iss + CERTS_PATH + "\"}", null));
        serveJwks(realm, keys);
    }

    private void serveJwks(String realm, JWK... keys) {
        List<JWK> publicKeys = new ArrayList<>();
        for (JWK key : keys) {
            publicKeys.add(key.toPublicJWK());
        }
        routes.put(jwksPath(realm), new StubResponse(200, new JWKSet(publicKeys).toString(), null));
    }

    private long count(String path) {
        return requests.stream().filter(path::equals).count();
    }

    private static Tenant tenant(String id, String realm, boolean enabled) {
        return Tenant.Builder.newInstance()
                .id(id)
                .name(id)
                .participantId("urn:" + id)
                .realm(realm)
                .enabled(enabled)
                .build();
    }

    private static RSAKey rsaKey(String kid) throws JOSEException {
        return new RSAKeyGenerator(2048).keyID(kid).generate();
    }

    private static JWTClaimsSet claims(String iss) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(iss)
                .subject("user")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
    }

    private static String token(RSAKey key, String iss) throws JOSEException {
        return token(key, key.getKeyID(), iss);
    }

    private static String token(RSAKey key, String kid, String iss) throws JOSEException {
        return sign(key, kid, claims(iss));
    }

    private static String sign(RSAKey key, String kid, JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(kid)
                .type(JOSEObjectType.JWT)
                .build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
