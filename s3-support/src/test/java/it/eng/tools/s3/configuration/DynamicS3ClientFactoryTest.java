package it.eng.tools.s3.configuration;

import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.S3ClientRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class DynamicS3ClientFactoryTest {

    @Mock
    private SdkHttpClient sdkHttpClient;

    @Mock
    private SdkAsyncHttpClient sdkAsyncHttpClient;

    private DynamicS3ClientFactory factory;

    @BeforeEach
    void setUp() {
        factory = new DynamicS3ClientFactory(sdkHttpClient, sdkAsyncHttpClient);
    }

    @Test
    @DisplayName("getClient with explicit credentials returns a non-null S3Client")
    void getClient_withExplicitCredentials_returnsClient() {
        S3ClientRequest request = S3ClientRequest.from("us-east-1", "http://minio:9000",
                bucket("my-bucket", "access-key", "secret-key"));

        S3Client client = factory.getClient(request);

        assertThat(client).isNotNull();
    }

    @Test
    @DisplayName("getClient called twice with same credentials returns the same cached instance")
    void getClient_calledTwice_returnsCachedInstance() {
        S3ClientRequest request = S3ClientRequest.from("us-east-1", "http://minio:9000",
                bucket("my-bucket", "access-key", "secret-key"));

        S3Client first = factory.getClient(request);
        S3Client second = factory.getClient(request);

        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("getClient with null credentials throws IllegalStateException")
    void getClient_withNullCredentials_throwsIllegalState() {
        S3ClientRequest request = S3ClientRequest.from("us-east-1", "http://minio:9000");

        assertThatThrownBy(() -> factory.getClient(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("explicit BucketCredentialsEntity");
    }

    @Test
    @DisplayName("getAsyncClient with explicit credentials returns a non-null S3AsyncClient")
    void getAsyncClient_withExplicitCredentials_returnsAsyncClient() {
        S3ClientRequest request = S3ClientRequest.from("us-east-1", "http://minio:9000",
                bucket("async-bucket", "async-key", "async-secret"));

        S3AsyncClient client = factory.getAsyncClient(request);

        assertThat(client).isNotNull();
    }

    @Test
    @DisplayName("getAsyncClient called twice with same credentials returns the same cached instance")
    void getAsyncClient_calledTwice_returnsCachedInstance() {
        S3ClientRequest request = S3ClientRequest.from("us-east-1", "http://minio:9000",
                bucket("async-bucket", "async-key", "async-secret"));

        S3AsyncClient first = factory.getAsyncClient(request);
        S3AsyncClient second = factory.getAsyncClient(request);

        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("getAsyncClient with null credentials throws IllegalStateException")
    void getAsyncClient_withNullCredentials_throwsIllegalState() {
        S3ClientRequest request = S3ClientRequest.from("us-east-1", "http://minio:9000");

        assertThatThrownBy(() -> factory.getAsyncClient(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("explicit BucketCredentialsEntity");
    }

    @Test
    @DisplayName("clearBucketCache evicts clients so next call creates a fresh instance")
    void clearBucketCache_evictsClientsForBucket() {
        S3ClientRequest request = S3ClientRequest.from("us-east-1", "http://minio:9000",
                bucket("cache-bucket", "ck", "cs"));

        S3Client before = factory.getClient(request);
        factory.clearBucketCache("cache-bucket");
        S3Client after = factory.getClient(request);

        assertThat(before).isNotSameAs(after);
    }

    @Test
    @DisplayName("clearBucketCache does not evict clients belonging to a different bucket")
    void clearBucketCache_doesNotEvictOtherBuckets() {
        S3ClientRequest requestA = S3ClientRequest.from("us-east-1", "http://minio:9000",
                bucket("bucket-a", "ka", "sa"));
        S3ClientRequest requestB = S3ClientRequest.from("us-east-1", "http://minio:9000",
                bucket("bucket-b", "kb", "sb"));

        S3Client clientA = factory.getClient(requestA);
        factory.getClient(requestB);

        factory.clearBucketCache("bucket-b");

        assertThat(factory.getClient(requestA)).isSameAs(clientA);
    }

    @Test
    @DisplayName("adminClient throws UnsupportedOperationException in data-plane mode")
    void adminClient_throwsUnsupportedOperation() {
        assertThatThrownBy(() -> factory.adminClient())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("data-plane mode");
    }

    private BucketCredentialsEntity bucket(String name, String accessKey, String secretKey) {
        return BucketCredentialsEntity.Builder.newInstance()
                .bucketName(name)
                .accessKey(accessKey)
                .secretKey(secretKey)
                .build();
    }
}
