package it.eng.connector.configuration;

import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3BucketProvisionService;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import it.eng.tools.service.AuditEventPublisher;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialDataLoaderTest {

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private Environment environment;
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
    @InjectMocks
    private InitialDataLoader loader;

    @Test
    @DisplayName("Startup provisions every enabled tenant without depending on mock upload")
    void startupProvisionsEveryEnabledTenant() throws Exception {
        when(tenantRepository.findByEnabled(true)).thenReturn(List.of(
                tenant("first", "first-bucket"), tenant("second", "second-bucket")));

        runStartup();

        verify(s3BucketProvisionService).ensureBucketCredentials("first-bucket");
        verify(s3BucketProvisionService).ensureBucketCredentials("second-bucket");
        verify(tenantRepository, never()).findAll();
        verifyNoInteractions(s3ClientService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Startup derives and persists a distinct bucket for each tenant without a bucket")
    void startupPersistsTenantSpecificFallbackBuckets(String bucketName) throws Exception {
        when(tenantRepository.findByEnabled(true)).thenReturn(List.of(
                tenant("first", bucketName), tenant("second", bucketName)));
        when(s3Properties.getBucketName()).thenReturn("default-bucket");

        runStartup();

        InOrder order = inOrder(s3BucketProvisionService, mongoTemplate);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        order.verify(s3BucketProvisionService).ensureBucketCredentials("default-bucket-first");
        order.verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq(Tenant.class));
        order.verify(s3BucketProvisionService).ensureBucketCredentials("default-bucket-second");
        order.verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq(Tenant.class));
        assertEquals(List.of("first", "second"), query.getAllValues().stream()
                .map(value -> value.getQueryObject().getString("_id")).toList());
        assertEquals(List.of("default-bucket-first", "default-bucket-second"), update.getAllValues().stream()
                .map(value -> value.getUpdateObject().get("$set", Document.class)
                        .getString("bucketName")).toList());
    }

    @Test
    @DisplayName("Startup preserves explicit bucket names and does not rewrite tenants")
    void startupPreservesExplicitBuckets() throws Exception {
        when(tenantRepository.findByEnabled(true)).thenReturn(List.of(tenant("first", "custom-bucket")));

        runStartup();

        verify(s3BucketProvisionService).ensureBucketCredentials("custom-bucket");
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Tenant.class));
        verifyNoInteractions(s3Properties);
    }

    @Test
    @DisplayName("Startup fails when a later tenant cannot be provisioned")
    void startupFailsWhenProvisioningFails() {
        when(tenantRepository.findByEnabled(true)).thenReturn(List.of(
                tenant("first", "first-bucket"), tenant("second", "second-bucket")));
        when(s3BucketProvisionService.ensureBucketCredentials("first-bucket")).thenReturn(null);
        doThrow(new IllegalArgumentException("Invalid bucket")).when(s3BucketProvisionService)
                .ensureBucketCredentials("second-bucket");

        IllegalStateException failure = assertThrows(IllegalStateException.class, this::runStartup);

        assertTrue(failure.getMessage().contains("second"), failure::toString);
        assertTrue(failure.getCause() instanceof IllegalArgumentException);
        verify(s3BucketProvisionService).ensureBucketCredentials("first-bucket");
    }

    @Test
    @DisplayName("Startup does not persist a fallback bucket when provisioning fails")
    void startupDoesNotPersistFailedProvisioning() {
        when(tenantRepository.findByEnabled(true)).thenReturn(List.of(tenant("first", null)));
        when(s3Properties.getBucketName()).thenReturn("default-bucket");
        doThrow(new IllegalStateException("S3 unavailable")).when(s3BucketProvisionService)
                .ensureBucketCredentials("default-bucket-first");

        assertThrows(IllegalStateException.class, this::runStartup);

        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Tenant.class));
    }

    @Test
    @DisplayName("Startup fails if the derived bucket name cannot be persisted")
    void startupFailsWhenBucketPersistenceFails() {
        when(tenantRepository.findByEnabled(true)).thenReturn(List.of(tenant("first", null)));
        when(s3Properties.getBucketName()).thenReturn("default-bucket");
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Tenant.class)))
                .thenThrow(new IllegalStateException("MongoDB unavailable"));

        IllegalStateException failure = assertThrows(IllegalStateException.class, this::runStartup);

        assertTrue(failure.getMessage().contains("first"));
        verify(s3BucketProvisionService).ensureBucketCredentials("default-bucket-first");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Startup rejects missing global bucket configuration for a tenant without a bucket")
    void startupRejectsMissingFallbackPrefix(String prefix) {
        when(tenantRepository.findByEnabled(true)).thenReturn(List.of(tenant("first", null)));
        when(s3Properties.getBucketName()).thenReturn(prefix);

        assertThrows(IllegalStateException.class, this::runStartup);

        verifyNoInteractions(s3BucketProvisionService);
    }

    @Test
    @DisplayName("Startup refuses to run without enabled tenants")
    void startupRejectsNoEnabledTenants() {
        when(tenantRepository.findByEnabled(true)).thenReturn(List.of());

        assertThrows(IllegalStateException.class, this::runStartup);

        verifyNoInteractions(s3BucketProvisionService);
    }

    @Test
    @DisplayName("Every enabled tenant is provisioned even when the optional mock file is absent")
    void missingMockFileStillProvisionsAllBuckets() throws Exception {
        when(tenantRepository.findByEnabled(true)).thenReturn(List.of(
                tenant("first", "first-bucket"), tenant("second", "second-bucket")));

        runStartup();
        try (MockedConstruction<ClassPathResource> resources = mockConstruction(ClassPathResource.class)) {
            loader.loadMockData();
        }

        verify(s3BucketProvisionService).ensureBucketCredentials("first-bucket");
        verify(s3BucketProvisionService).ensureBucketCredentials("second-bucket");
        verifyNoInteractions(s3ClientService);
    }

    @Test
    @DisplayName("All enabled buckets are provisioned before mock uploads, each using a fresh stream")
    void mockUploadUsesEveryProvisionedBucket() throws Exception {
        when(tenantRepository.findByEnabled(true)).thenReturn(List.of(
                tenant("first", "default-bucket-first"), tenant("second", "second-bucket")));
        runStartup();
        when(s3Properties.getEndpoint()).thenReturn("");
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getAccessKey()).thenReturn("test-key");
        when(s3Properties.getSecretKey()).thenReturn("test-secret");
        when(s3ClientService.uploadFile(any(InputStream.class), any(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("etag"));
        InputStream firstStream = mock(InputStream.class);
        InputStream secondStream = mock(InputStream.class);
        try (MockedConstruction<ClassPathResource> resources = mockConstruction(ClassPathResource.class,
                (resource, context) -> {
                    when(resource.exists()).thenReturn(true);
                    when(resource.getFilename()).thenReturn("ENG-employee.json");
                    when(resource.getInputStream()).thenReturn(firstStream, secondStream);
                })) {
            loader.loadMockData();
        }

        InOrder order = inOrder(s3BucketProvisionService, s3ClientService);
        order.verify(s3BucketProvisionService).ensureBucketCredentials("default-bucket-first");
        order.verify(s3BucketProvisionService).ensureBucketCredentials("second-bucket");
        order.verify(s3ClientService).uploadFile(eq(firstStream),
                argThat(properties -> "default-bucket-first".equals(properties.get(S3Utils.BUCKET_NAME))),
                eq("application/json"), anyString());
        order.verify(s3ClientService).uploadFile(eq(secondStream),
                argThat(properties -> "second-bucket".equals(properties.get(S3Utils.BUCKET_NAME))),
                eq("application/json"), anyString());
        verify(firstStream).close();
        verify(secondStream).close();
    }

    @Test
    @DisplayName("An optional mock-file read failure is logged without failing startup provisioning")
    void mockFileFailureIsOptional() {
        when(tenantRepository.findByEnabled(true)).thenReturn(List.of(tenant("first", "first-bucket")));
        when(s3Properties.getEndpoint()).thenReturn("");
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getAccessKey()).thenReturn("test-key");
        when(s3Properties.getSecretKey()).thenReturn("test-secret");
        try (MockedConstruction<ClassPathResource> resources = mockConstruction(ClassPathResource.class,
                (resource, context) -> {
                    when(resource.exists()).thenReturn(true);
                    when(resource.getFilename()).thenReturn("ENG-employee.json");
                    when(resource.getInputStream()).thenThrow(new IOException("Mock file unavailable"));
                })) {
            assertDoesNotThrow(loader::loadMockData);
        }

        verifyNoInteractions(s3BucketProvisionService, s3ClientService);
    }

    @Test
    @DisplayName("A failed optional upload does not skip later tenants")
    void mockUploadFailureDoesNotSkipLaterTenants() throws Exception {
        when(tenantRepository.findByEnabled(true)).thenReturn(List.of(
                tenant("first", "first-bucket"), tenant("second", "second-bucket")));
        when(s3Properties.getEndpoint()).thenReturn("");
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getAccessKey()).thenReturn("test-key");
        when(s3Properties.getSecretKey()).thenReturn("test-secret");
        when(s3ClientService.uploadFile(any(InputStream.class), any(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IOException("Upload failed")),
                        CompletableFuture.completedFuture("etag"));
        try (MockedConstruction<ClassPathResource> resources = mockConstruction(ClassPathResource.class,
                (resource, context) -> {
                    when(resource.exists()).thenReturn(true);
                    when(resource.getFilename()).thenReturn("ENG-employee.json");
                    when(resource.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(new byte[0]));
                })) {
            assertDoesNotThrow(loader::loadMockData);
        }

        verify(s3ClientService).uploadFile(any(InputStream.class),
                argThat(properties -> "first-bucket".equals(properties.get(S3Utils.BUCKET_NAME))),
                eq("application/json"), anyString());
        verify(s3ClientService).uploadFile(any(InputStream.class),
                argThat(properties -> "second-bucket".equals(properties.get(S3Utils.BUCKET_NAME))),
                eq("application/json"), anyString());
        verifyNoInteractions(s3BucketProvisionService);
    }

    private void runStartup() throws Exception {
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        try (MockedConstruction<ClassPathResource> resources = mockConstruction(ClassPathResource.class,
                (resource, context) -> when(resource.getInputStream()).thenReturn(
                        new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8))))) {
            loader.loadInitialData().run();
        }
    }

    private Tenant tenant(String id, String bucketName) {
        return Tenant.Builder.newInstance()
                .id(id)
                .name(id)
                .participantId("urn:connector:" + id)
                .enabled(true)
                .bucketName(bucketName)
                .build();
    }
}
