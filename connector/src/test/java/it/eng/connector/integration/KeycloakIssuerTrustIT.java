package it.eng.connector.integration;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import it.eng.tools.auth.keycloak.KeycloakDiscoveryClient;
import it.eng.tools.auth.keycloak.issuer.IssuerType;
import it.eng.tools.auth.keycloak.issuer.TrustedIssuer;
import it.eng.tools.auth.keycloak.issuer.TrustedIssuerRegistry;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.exception.DuplicateTenantRealmException;
import it.eng.tools.exception.InvalidTenantRealmException;
import it.eng.tools.model.Tenant;
import it.eng.tools.s3.repository.BucketCredentialsRepository;
import it.eng.tools.service.TenantService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.crypto.KeyUse;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.representations.idm.KeysMetadataRepresentation.KeyMetadataRepresentation;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultMatcher;

import java.text.ParseException;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks of the trusted-issuer registry in {@code KEYCLOAK} mode against the seed realms.
 *
 * <p>Covers the runtime tenant lifecycle (driven through {@link TenantService}, because super-admin tokens are
 * not accepted on {@code /api/**} yet), forged and unknown issuers that must fail without any outbound request,
 * the per-realm decoders of the registry, and a real signing-key rotation of {@code tenant-EngineeringA}.
 *
 * <p>The discovery client is a spy: a decoder is only ever created after a discovery fetch, so "no fetch"
 * means no new decoder and no request to a discovery or JWKS URL.
 */
class KeycloakIssuerTrustIT extends BaseKeycloakIntegrationTest {

    private static final String PROTECTED_ROUTE = ApiEndpoints.NEGOTIATION_V1;
    private static final String PLATFORM_REALM = "platform";
    private static final String ADMIN_REALM = "connector-admin";
    private static final String TENANT_A_REALM = "tenant-EngineeringA";
    private static final String TENANT_B_ID = "engineering-b";
    private static final String TENANT_B_REALM = "tenant-EngineeringB";
    private static final String TENANT_B_MOVED_REALM = "tenant-EngineeringB-moved";
    private static final String TENANT_B_BUCKET = "dsp-engineering-b";
    private static final String OTHER_TENANT_ID = "issuer-trust-other";
    private static final String KEY_PROVIDER_TYPE = "org.keycloak.keys.KeyProvider";
    private static final String RSA_GENERATED_PROVIDER = "rsa-generated";
    private static final String ENABLED_CONFIG = "enabled";
    private static final int ROTATED_KEY_PRIORITY_INCREMENT = 100;

    @MockitoSpyBean
    private KeycloakDiscoveryClient discoveryClient;

    @Autowired
    private TrustedIssuerRegistry registry;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private BucketCredentialsRepository bucketCredentialsRepository;

    @BeforeEach
    void removeTestTenants() {
        deleteTestTenants();
        Mockito.clearInvocations(discoveryClient);
    }

    @AfterEach
    void cleanupTestTenants() {
        deleteTestTenants();
        bucketCredentialsRepository.deleteById(TENANT_B_BUCKET);
    }

    @Test
    @DisplayName("Tenant lifecycle through TenantService: create, disable, enable and realm change apply without restart")
    void tenantLifecycleChangesTrustAtRuntime() throws Exception {
        String tenantBToken = tenantBAdminAccessToken();
        performProtected(tenantBToken, status().isUnauthorized());

        tenantService.saveTenant(tenantB(TENANT_B_REALM));
        performProtected(tenantBToken, status().isOk());

        tenantService.disableTenant(TENANT_B_ID);
        performProtected(tenantBToken, status().isUnauthorized());

        tenantService.enableTenant(TENANT_B_ID);
        performProtected(tenantBToken, status().isOk());

        tenantService.updateTenant(TENANT_B_ID, tenantB(TENANT_B_MOVED_REALM));
        performProtected(tenantBToken, status().isUnauthorized());

        tenantService.updateTenant(TENANT_B_ID, tenantB(TENANT_B_REALM));
        performProtected(tenantBToken, status().isOk());
    }

    @Test
    @DisplayName("Tenant created on a placeholder realm becomes trusted for its new realm after a realm change")
    void realmChangeTrustsNewRealm() throws Exception {
        String tenantBToken = tenantBAdminAccessToken();

        tenantService.saveTenant(tenantB(TENANT_B_MOVED_REALM));
        performProtected(tenantBToken, status().isUnauthorized());

        tenantService.updateTenant(TENANT_B_ID, tenantB(TENANT_B_REALM));
        performProtected(tenantBToken, status().isOk());
    }

    @Test
    @DisplayName("Reserved, malformed and duplicate realms are never stored, so they never become trusted")
    void invalidRealmsAreNeverStored() {
        tenantService.saveTenant(tenantB(TENANT_B_REALM));

        assertThrows(DuplicateTenantRealmException.class,
                () -> tenantService.saveTenant(otherTenant(TENANT_B_REALM)));
        assertThrows(InvalidTenantRealmException.class,
                () -> tenantService.saveTenant(otherTenant(PLATFORM_REALM)));
        assertThrows(InvalidTenantRealmException.class,
                () -> tenantService.saveTenant(otherTenant("Master")));
        assertThrows(InvalidTenantRealmException.class,
                () -> tenantService.saveTenant(otherTenant("a/../" + PLATFORM_REALM)));
        assertThrows(InvalidTenantRealmException.class,
                () -> tenantService.updateTenant(TENANT_B_ID, tenantB(ADMIN_REALM)));

        assertTrue(tenantRepository.findById(OTHER_TENANT_ID).isEmpty());
        assertEquals(TENANT_B_REALM, tenantRepository.findById(TENANT_B_ID).orElseThrow().getRealm());
    }

    @Test
    @DisplayName("First request of a newly trusted issuer fetches its discovery document once, then uses the cache")
    void newlyTrustedIssuerIsFetchedOnce() throws Exception {
        String tenantBToken = tenantBAdminAccessToken();
        // Not bound: the request is rejected and any cached tenant B decoder is evicted.
        performProtected(tenantBToken, status().isUnauthorized());
        verify(discoveryClient, never()).fetch(anyString());

        tenantService.saveTenant(tenantB(TENANT_B_REALM));
        performProtected(tenantBToken, status().isOk());
        performProtected(tenantBToken, status().isOk());

        verify(discoveryClient, times(1)).fetch(realmUrl(TENANT_B_REALM));
        verify(discoveryClient, times(1)).fetch(anyString());
    }

    @ParameterizedTest(name = "[{index}] iss={0}")
    @MethodSource("forgedIssuers")
    @DisplayName("Forged or unknown issuer is rejected with no outbound request")
    void forgedIssuerIsRejectedWithoutFetch(String forgedIssuer) throws Exception {
        String original = adminAccessToken();
        String forged = resign(original, claims(original).issuer(forgedIssuer).build());

        performProtected(forged, status().isUnauthorized());

        verify(discoveryClient, never()).fetch(anyString());
        assertTrue(registry.resolve(forgedIssuer).isEmpty());
    }

    @Test
    @DisplayName("Missing iss, alg none, and an unbound tenant realm are rejected with no outbound request")
    void untrustedTokensAreRejectedWithoutFetch() throws Exception {
        String original = adminAccessToken();
        String missingIssuer = resign(original, claims(original).issuer(null).build());
        String unsigned = new PlainJWT(claims(original).build()).serialize();

        performProtected(missingIssuer, status().isUnauthorized());
        performProtected(unsigned, status().isUnauthorized());
        // A real token of a real realm that is not bound to a tenant. (Platform and connector-admin tokens are
        // trusted on /api, see KeycloakProtocolTrustIT and KeycloakSecurityIT.)
        performProtected(tenantBAdminAccessToken(), status().isUnauthorized());

        verify(discoveryClient, never()).fetch(anyString());
    }

    @Test
    @DisplayName("Registry resolves each seed realm to its own issuer and each decoder verifies only its own realm")
    void registryDecodersAreRealmSpecific() throws Exception {
        TrustedIssuer platform = registry.resolve(realmUrl(PLATFORM_REALM)).orElseThrow();
        TrustedIssuer admin = registry.resolve(realmUrl(ADMIN_REALM)).orElseThrow();
        TrustedIssuer tenantA = registry.resolve(realmUrl(TENANT_A_REALM)).orElseThrow();

        assertIssuer(platform, PLATFORM_REALM, IssuerType.PLATFORM, null);
        assertIssuer(admin, ADMIN_REALM, IssuerType.ADMIN, null);
        assertIssuer(tenantA, TENANT_A_REALM, IssuerType.TENANT, TENANT_ID);

        Map<TrustedIssuer, String> tokens = Map.of(
                platform, connectorAccessToken(),
                admin, superAdminAccessToken(),
                tenantA, adminAccessToken());
        for (TrustedIssuer decoderIssuer : tokens.keySet()) {
            JwtDecoder decoder = registry.decoderFor(decoderIssuer);
            assertSame(decoder, registry.decoderFor(decoderIssuer), "decoder is cached per issuer");
            for (Map.Entry<TrustedIssuer, String> token : tokens.entrySet()) {
                if (token.getKey().equals(decoderIssuer)) {
                    Jwt jwt = decoder.decode(token.getValue());
                    assertEquals(decoderIssuer.issuer(), jwt.getIssuer().toString());
                } else {
                    assertThrows(JwtException.class, () -> decoder.decode(token.getValue()),
                            () -> decoderIssuer.realm() + " decoder accepted a " + token.getKey().realm() + " token");
                }
            }
        }
    }

    /**
     * Rotates the signing key of {@code tenant-EngineeringA}: a new RSA key provider with a higher priority is
     * added and the old provider is disabled before the first new-key token is presented. The unknown {@code kid}
     * makes the cached decoder refetch the JWK set, which then no longer holds the old key, so an old-key token
     * fails right away (no wait for the 5 minute JWK set expiry). The decoder is evicted before the test, so that
     * the initial load and the refetch are the only two JWK set requests in the rate-limit window, and after it,
     * once the old key is restored.
     */
    @Test
    @DisplayName("Signing-key rotation of tenant-EngineeringA: new-key tokens verify, old-key tokens fail")
    void tenantRealmKeyRotation() throws Exception {
        RealmResource realm = keycloakAdminClient().realm(TENANT_A_REALM);
        String oldToken = adminAccessToken();
        String oldKid = SignedJWT.parse(oldToken).getHeader().getKeyID();
        KeyMetadataRepresentation oldKey = signingKey(realm, oldKid);

        evictTenantADecoder();
        Mockito.clearInvocations(discoveryClient);
        String newComponentId = null;
        try {
            performProtected(oldToken, status().isOk());
            verify(discoveryClient, times(1)).fetch(realmUrl(TENANT_A_REALM));

            newComponentId = addRsaKeyProvider(realm, oldKey.getProviderPriority() + ROTATED_KEY_PRIORITY_INCREMENT);
            setProviderEnabled(realm, oldKey.getProviderId(), false);

            String newToken = adminAccessToken();
            String newKid = SignedJWT.parse(newToken).getHeader().getKeyID();
            assertNotEquals(oldKid, newKid);
            assertEquals(newComponentId, signingKey(realm, newKid).getProviderId());

            performProtected(newToken, status().isOk());
            performProtected(oldToken, status().isUnauthorized());
            // The refetch is a JWK set request of the cached decoder, not a new discovery.
            verify(discoveryClient, times(1)).fetch(anyString());
        } finally {
            setProviderEnabled(realm, oldKey.getProviderId(), true);
            if (newComponentId != null) {
                realm.components().component(newComponentId).remove();
            }
            evictTenantADecoder();
        }
        performProtected(adminAccessToken(), status().isOk());
    }

    private static Stream<String> forgedIssuers() {
        String realms = keycloakBaseUrl() + "/realms/";
        return Stream.of(
                "https://issuer.invalid/realms/" + TENANT_A_REALM,
                keycloakBaseUrl().toUpperCase(Locale.ROOT) + "/realms/" + TENANT_A_REALM,
                realms + "tenant-Unknown",
                realms + TENANT_A_REALM + "/",
                realms + "a/../" + PLATFORM_REALM,
                realms + "a/../" + TENANT_A_REALM,
                realms + "a%2F..%2F" + PLATFORM_REALM,
                realms + "%2e%2e%2F" + PLATFORM_REALM,
                realms + "%2e%2e",
                realms + "..",
                realms + "master",
                realms + "Platform",
                realms + TENANT_A_REALM + "#fragment",
                realms + TENANT_A_REALM + "?realm=x",
                realms);
    }

    private void performProtected(String accessToken, ResultMatcher expectedStatus) throws Exception {
        mockMvc.perform(get(PROTECTED_ROUTE)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(accessToken)))
                .andExpect(expectedStatus);
    }

    /**
     * Drops the cached decoder of {@code tenant-EngineeringA}: a request while its tenant is disabled no longer
     * resolves the issuer, which evicts the decoder.
     */
    private void evictTenantADecoder() throws Exception {
        String token = adminAccessToken();
        tenantService.disableTenant(TENANT_ID);
        try {
            performProtected(token, status().isUnauthorized());
        } finally {
            tenantService.enableTenant(TENANT_ID);
        }
    }

    private static KeyMetadataRepresentation signingKey(RealmResource realm, String kid) {
        return realm.keys().getKeyMetadata().getKeys().stream()
                .filter(key -> kid.equals(key.getKid()) && KeyUse.SIG == key.getUse())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No signing key with the token's kid in the realm"));
    }

    private static String addRsaKeyProvider(RealmResource realm, long priority) {
        ComponentRepresentation component = new ComponentRepresentation();
        component.setName("rotated-rsa-" + priority);
        component.setProviderId(RSA_GENERATED_PROVIDER);
        component.setProviderType(KEY_PROVIDER_TYPE);
        component.setParentId(realm.toRepresentation().getId());
        MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        config.putSingle("priority", Long.toString(priority));
        config.putSingle(ENABLED_CONFIG, "true");
        config.putSingle("active", "true");
        config.putSingle("algorithm", "RS256");
        component.setConfig(config);
        try (Response response = realm.components().add(component)) {
            return CreatedResponseUtil.getCreatedId(response);
        }
    }

    private static void setProviderEnabled(RealmResource realm, String componentId, boolean enabled) {
        ComponentRepresentation component = realm.components().component(componentId).toRepresentation();
        component.getConfig().putSingle(ENABLED_CONFIG, Boolean.toString(enabled));
        realm.components().component(componentId).update(component);
    }

    private static void assertIssuer(TrustedIssuer issuer, String realm, IssuerType type, String tenantId) {
        assertEquals(realmUrl(realm), issuer.issuer());
        assertEquals(realm, issuer.realm());
        assertEquals(type, issuer.type());
        if (tenantId == null) {
            assertNull(issuer.tenantId());
        } else {
            assertEquals(tenantId, issuer.tenantId());
        }
    }

    private void deleteTestTenants() {
        tenantRepository.deleteById(TENANT_B_ID);
        tenantRepository.deleteById(OTHER_TENANT_ID);
    }

    private static Tenant tenantB(String realm) {
        return Tenant.Builder.newInstance()
                .id(TENANT_B_ID)
                .name("EngineeringB")
                .participantId("urn:connector:engineering-b")
                .realm(realm)
                .enabled(true)
                .build();
    }

    private static Tenant otherTenant(String realm) {
        return Tenant.Builder.newInstance()
                .id(OTHER_TENANT_ID)
                .name("IssuerTrustOther")
                .participantId("urn:connector:issuer-trust-other")
                .realm(realm)
                .enabled(true)
                .build();
    }

    private static JWTClaimsSet.Builder claims(String token) throws ParseException {
        return new JWTClaimsSet.Builder(SignedJWT.parse(token).getJWTClaimsSet());
    }

    /**
     * Signs the claims with a freshly generated key, keeping the original header's key id.
     */
    private static String resign(String original, JWTClaimsSet claims) throws ParseException, JOSEException {
        JWSHeader originalHeader = SignedJWT.parse(original).getHeader();
        RSAKey foreignKey = new RSAKeyGenerator(2048).keyID(originalHeader.getKeyID()).generate();
        SignedJWT forged = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(originalHeader.getType())
                .keyID(originalHeader.getKeyID())
                .build(), claims);
        forged.sign(new RSASSASigner(foreignKey));
        return forged.serialize();
    }
}
