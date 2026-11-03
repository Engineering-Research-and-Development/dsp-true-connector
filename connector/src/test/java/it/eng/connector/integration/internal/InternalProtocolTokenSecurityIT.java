package it.eng.connector.integration.internal;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.tools.auth.jwt.JwtService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.s3.service.S3BucketProvisionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The locally minted {@code INTERNAL} protocol token (see {@link JwtService#issueProtocolToken()}) against the
 * three security chains of an {@code INTERNAL}-mode connector: accepted on the protocol routes, 403 on
 * {@code /api/**} and 401 on {@code /internal/**}. The protocol chain decides by the {@code CONNECTOR} role
 * alone, so the extra claims and the missing role below are handled as for any other {@code INTERNAL} JWT.
 */
class InternalProtocolTokenSecurityIT extends BaseIntegrationTest {

    private static final String CATALOG_REQUEST_PATH = "/" + TENANT_ID + "/catalog/request";
    private static final String INTERNAL_PATH = ApiEndpoints.INTERNAL_DATASETS_V1 + "/some-dataset/artifact";

    private static final int BUCKET_WAIT_SECONDS = 30;

    @Autowired
    private JwtService jwtService;
    @Autowired
    private CatalogRepository catalogRepository;
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DataServiceRepository dataServiceRepository;
    @Autowired
    private DistributionRepository distributionRepository;
    @Autowired
    private ArtifactRepository artifactRepository;
    @Autowired
    private S3BucketProvisionService s3BucketProvisionService;
    @Value("${application.security.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void storeCatalog() throws InterruptedException {
        // The tenant bucket is created asynchronously on startup and a catalog request lists it.
        long deadline = System.currentTimeMillis() + BUCKET_WAIT_SECONDS * 1000L;
        while (!s3BucketProvisionService.bucketExists(s3Properties.getBucketName())
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(250);
        }
        Catalog catalog = CatalogMockObjectUtil.createNewCatalog();
        catalog.injectTenantId(TENANT_ID);
        // EXTERNAL artifacts are kept in the catalog response without a file in S3.
        catalog.getDataset().forEach(dataset -> {
            dataset.injectTenantId(TENANT_ID);
            ReflectionTestUtils.setField(dataset.getArtifact(), "artifactType", ArtifactType.EXTERNAL);
        });
        catalogRepository.save(catalog);
        datasetRepository.saveAll(catalog.getDataset());
        dataServiceRepository.saveAll(catalog.getService());
        distributionRepository.saveAll(catalog.getDistribution());
        catalog.getDataset().forEach(dataset -> artifactRepository.save(dataset.getArtifact()));
    }

    @AfterEach
    void removeCatalog() {
        datasetRepository.deleteAll();
        catalogRepository.deleteAll();
        dataServiceRepository.deleteAll();
        distributionRepository.deleteAll();
        artifactRepository.deleteAll();
    }

    @Test
    @DisplayName("Protocol route: a minted protocol token is accepted")
    void protocolRouteAcceptsMintedToken() throws Exception {
        catalogRequest(jwtService.issueProtocolToken()).andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("/api: a minted protocol token returns 403")
    void apiRouteRejectsMintedTokenWithForbidden() throws Exception {
        mockMvc.perform(get(ApiEndpoints.CATALOG_DATASETS_V1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtService.issueProtocolToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/internal: a minted protocol token returns 401")
    void internalRouteRejectsMintedTokenWithUnauthorized() throws Exception {
        mockMvc.perform(get(INTERNAL_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtService.issueProtocolToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protocol route: an expired minted token returns 401")
    void expiredTokenReturnsUnauthorized() throws Exception {
        catalogRequest(token(Instant.now().minusSeconds(3600), Instant.now().minusSeconds(60), Map.of(),
                List.of(JwtService.CONNECTOR_ROLE))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protocol route: a protocol token signed with another secret returns 401")
    void tokenSignedWithOtherSecretReturnsUnauthorized() throws Exception {
        String foreign = JWT.create().withSubject(JwtService.PROTOCOL_TOKEN_SUBJECT)
                .withClaim(JwtService.REALM_ACCESS_CLAIM, Map.of(JwtService.ROLES_CLAIM, List.of(JwtService.CONNECTOR_ROLE)))
                .withExpiresAt(Date.from(Instant.now().plusSeconds(600)))
                .sign(Algorithm.HMAC256("another-signing-secret-of-at-least-32-bytes"));
        catalogRequest(foreign).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protocol route: an extra tenant_id claim is ignored, the CONNECTOR role decides (2xx)")
    void extraTenantClaimIsAccepted() throws Exception {
        catalogRequest(token(Instant.now(), Instant.now().plusSeconds(600), Map.of(JwtService.TENANT_ID_CLAIM, TENANT_ID),
                List.of(JwtService.CONNECTOR_ROLE))).andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("Protocol route: a valid token without the CONNECTOR role returns 403")
    void tokenWithoutConnectorRoleReturnsForbidden() throws Exception {
        catalogRequest(token(Instant.now(), Instant.now().plusSeconds(600), Map.of(), List.of("ADMIN")))
                .andExpect(status().isForbidden());
    }

    private ResultActions catalogRequest(String token) throws Exception {
        return mockMvc.perform(post(CATALOG_REQUEST_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE)));
    }

    // A token signed with the connector's own secret, shaped like the protocol token but with the given claims.
    private String token(Instant issuedAt, Instant expiresAt, Map<String, String> extraClaims, List<String> roles) {
        var builder = JWT.create()
                .withSubject(JwtService.PROTOCOL_TOKEN_SUBJECT)
                .withClaim(JwtService.REALM_ACCESS_CLAIM, Map.of(JwtService.ROLES_CLAIM, roles))
                .withIssuedAt(Date.from(issuedAt))
                .withExpiresAt(Date.from(expiresAt))
                .withJWTId(UUID.randomUUID().toString());
        extraClaims.forEach(builder::withClaim);
        return builder.sign(Algorithm.HMAC256(jwtSecret));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
