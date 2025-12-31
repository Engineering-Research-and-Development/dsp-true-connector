package it.eng.dcp.issuer.integration;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.service.KeyService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class BaseIssuerIntegrationTest {

    private static final String HOLDER_KEY_ID = "holder-key-1";
    private static final String ISSUER_KEY_ID = "issuer-key-1";

    protected static final MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0.12"))
                    .withReuse(false);
    protected static final MinIOContainer minIOContainer =
            new MinIOContainer(DockerImageName.parse("minio/minio"))
                    .withReuse(false);

    static {
        mongoDBContainer.start();
        // used for checking S3 storage during test debugging; will be exposed on random localhost port which can be checked with `docker ps`or some docker GUI
        minIOContainer.addExposedPort(9001);
        minIOContainer.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @MockBean
    protected KeyService keyService;

    protected ECKey holderKeyPair;
    protected ECKey issuerKeyPair;

    @DynamicPropertySource
    static void containersProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getHost);
        registry.add("spring.data.mongodb.port", mongoDBContainer::getFirstMappedPort);
        registry.add("s3.endpoint", minIOContainer::getS3URL);
        registry.add("s3.externalPresignedEndpoint", minIOContainer::getS3URL);
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
