package it.eng.connector.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.eng.tools.repository.TenantRepository;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3BucketProvisionService;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import it.eng.tools.service.AuditEventPublisher;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(MockitoExtension.class)
class InitialDataLoaderTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private S3ClientService s3ClientService;

    @Mock
    private S3BucketProvisionService s3BucketProvisionService;

    @Mock
    private S3Properties s3Properties;

    @Mock
    private AuditEventPublisher publisher;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private Resource missingResource;

    @Captor
    private ArgumentCaptor<Map<String, String>> destinationCaptor;

    @Captor
    private ArgumentCaptor<String> contentTypeCaptor;

    @Captor
    private ArgumentCaptor<String> contentDispositionCaptor;

    private Environment environment;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        when(tenantRepository.findAll()).thenReturn(List.of());
        when(s3Properties.getBucketName()).thenReturn("test-bucket");
    }

    @Test
    void loadMockDataUsesConfiguredExternalResource() {
        MockEnvironment mockEnvironment = (MockEnvironment) environment;
        String mockResourceLocation = "file:/home/nobody/ftp/test-data/large-transfer.txt";
        mockEnvironment.setProperty("application.mock.data.resource", mockResourceLocation);

        Resource largeTransferResource = new ByteArrayResource("fixture".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "large-transfer.txt";
            }
        };
        when(resourceLoader.getResource(mockResourceLocation)).thenReturn(largeTransferResource);
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getAccessKey()).thenReturn("access-key");
        when(s3Properties.getSecretKey()).thenReturn("secret-key");
        when(s3ClientService.uploadFile(any(InputStream.class), anyMap(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("etag"));

        InitialDataLoader initialDataLoader = new InitialDataLoader(mongoTemplate, environment, s3ClientService,
                s3BucketProvisionService, s3Properties, publisher, tenantRepository, resourceLoader);

        initialDataLoader.loadMockData();

        verify(s3BucketProvisionService).ensureBucketCredentials("test-bucket");
        verify(s3ClientService).uploadFile(any(InputStream.class), destinationCaptor.capture(),
                contentTypeCaptor.capture(), contentDispositionCaptor.capture());
        assertEquals("test-bucket", destinationCaptor.getValue().get(S3Utils.BUCKET_NAME));
        assertEquals("urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
                destinationCaptor.getValue().get(S3Utils.OBJECT_KEY));
        assertEquals(MediaType.TEXT_PLAIN_VALUE, contentTypeCaptor.getValue());
        assertTrue(contentDispositionCaptor.getValue().contains("large-transfer.txt"));
    }

    @Test
    void loadMockDataFallsBackToClasspathResourceWhenNoOverrideIsSet() {
        when(resourceLoader.getResource("classpath:ENG-employee.json")).thenReturn(missingResource);
        when(missingResource.exists()).thenReturn(false);

        InitialDataLoader initialDataLoader = new InitialDataLoader(mongoTemplate, environment, s3ClientService,
                s3BucketProvisionService, s3Properties, publisher, tenantRepository, resourceLoader);

        initialDataLoader.loadMockData();

        verify(resourceLoader).getResource("classpath:ENG-employee.json");
        verify(s3ClientService, never()).uploadFile(any(InputStream.class), anyMap(), anyString(), anyString());
    }
}
