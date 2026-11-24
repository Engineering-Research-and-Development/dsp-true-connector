package it.eng.dcp.issuer.integration;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantContextProvider;
import it.eng.dcp.common.service.KeyService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "dcp.admin.users[0].username=" + BaseIssuerIntegrationTest.ADMIN_USERNAME,
        "dcp.admin.users[0].password=" + BaseIssuerIntegrationTest.ADMIN_PASSWORD_HASH,
        "dcp.admin.users[0].authorities[0]=DCP_ISSUER_ADMIN",
        "dcp.admin.users[1].username=" + BaseIssuerIntegrationTest.NON_ADMIN_USERNAME,
        "dcp.admin.users[1].password={noop}" + BaseIssuerIntegrationTest.NON_ADMIN_PASSWORD,
        "dcp.admin.users[1].authorities[0]=DCP_ISSUER_AUDITOR"
})
public class BaseIssuerIntegrationTest {

    /** Administration user with the {@code DCP_ISSUER_ADMIN} authority. */
    protected static final String ADMIN_USERNAME = "issuer-admin";
    /** Plain password of {@link #ADMIN_USERNAME}; test value only. */
    protected static final String ADMIN_PASSWORD = "issuer-admin-test-password";
    /** BCrypt hash of {@link #ADMIN_PASSWORD} in DelegatingPasswordEncoder format. */
    protected static final String ADMIN_PASSWORD_HASH =
            "{bcrypt}$2a$10$aZXjLqJPPXsBq7zSJRnID.6fAO9oPNoB.fNQf8.XMH8k43qBEOnB6";
    /** Authenticated user without the {@code DCP_ISSUER_ADMIN} authority. */
    protected static final String NON_ADMIN_USERNAME = "issuer-auditor";
    /** Plain password of {@link #NON_ADMIN_USERNAME}; test value only. */
    protected static final String NON_ADMIN_PASSWORD = "issuer-auditor-test-password";

    private static final String HOLDER_KEY_ID = "holder-key-1";
    private static final String ISSUER_KEY_ID = "issuer-key-1";

    protected static final MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0.12"))
                    .withReuse(false);

    static {
        mongoDBContainer.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected KeyService keyService;

    @Autowired
    protected ParticipantContextProvider participantContextProvider;

    protected ECKey holderKeyPair;
    protected ECKey issuerKeyPair;

    /**
     * Returns the default participant served by this connector.
     *
     * @return the default participant context
     */
    protected ParticipantContext defaultParticipant() {
        return participantContextProvider.find(ParticipantContext.DEFAULT_ID).orElseThrow();
    }

    @DynamicPropertySource
    static void containersProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getHost);
        registry.add("spring.data.mongodb.port", mongoDBContainer::getFirstMappedPort);
    }

    @BeforeEach
    void beforeEach() throws JOSEException {
        // Generate holder keypair for signing test tokens
        holderKeyPair = new ECKeyGenerator(Curve.P_256)
                .keyID(HOLDER_KEY_ID)
                .generate();

        // Generate issuer keypair
        issuerKeyPair = new ECKeyGenerator(Curve.P_256)
                .keyID(ISSUER_KEY_ID)
                .generate();

        // Mock KeyService to return a generated ECKey for ITs (workaround for missing p12)
        ECKey generatedKey = new ECKeyGenerator(Curve.P_256).keyID("test-key-id").generate();
        when(keyService.getSigningJwk(any(DidDocumentConfig.class))).thenReturn(generatedKey);
    }
}
