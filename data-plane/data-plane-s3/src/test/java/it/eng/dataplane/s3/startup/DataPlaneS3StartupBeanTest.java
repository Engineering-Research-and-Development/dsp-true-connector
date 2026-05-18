package it.eng.dataplane.s3.startup;

import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3BucketProvisionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataPlaneS3StartupBean}.
 */
@ExtendWith(MockitoExtension.class)
class DataPlaneS3StartupBeanTest {

    @Mock
    private S3BucketProvisionService s3BucketProvisionService;

    @Mock
    private S3Properties s3Properties;

    @InjectMocks
    private DataPlaneS3StartupBean startupBean;

    @Test
    @DisplayName("ensureBucketCredentials provisions bucket when bucket name is configured")
    void ensureBucketCredentials_provisionsBucket_whenConfigured() {
        when(s3Properties.getBucketName()).thenReturn("dp-bucket");

        startupBean.ensureBucketCredentials();

        verify(s3BucketProvisionService).ensureBucketCredentials("dp-bucket");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("ensureBucketCredentials skips provisioning when bucket name is blank")
    void ensureBucketCredentials_skips_whenBucketNameBlank(String bucketName) {
        when(s3Properties.getBucketName()).thenReturn(bucketName);

        startupBean.ensureBucketCredentials();

        verifyNoInteractions(s3BucketProvisionService);
    }

    @Test
    @DisplayName("ensureBucketCredentials logs error but does not rethrow on exception")
    void ensureBucketCredentials_doesNotRethrow_onException() {
        when(s3Properties.getBucketName()).thenReturn("dp-bucket");
        doThrow(new RuntimeException("S3 unavailable"))
                .when(s3BucketProvisionService).ensureBucketCredentials("dp-bucket");

        assertDoesNotThrow(() -> startupBean.ensureBucketCredentials());

        verify(s3BucketProvisionService).ensureBucketCredentials("dp-bucket");
    }
}
