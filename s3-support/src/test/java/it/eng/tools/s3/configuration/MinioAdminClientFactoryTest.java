package it.eng.tools.s3.configuration;

import io.minio.admin.MinioAdminClient;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MinioAdminClientFactory}.
 * Verifies caching semantics: same endpoint+accessKey returns the same instance;
 * different credentials produce distinct clients.
 */
class MinioAdminClientFactoryTest {

    private MinioAdminClientFactory factory;

    @BeforeEach
    void setUp() {
        factory = new MinioAdminClientFactory(new OkHttpClient());
    }

    @Test
    @DisplayName("get - same endpoint and accessKey returns cached instance")
    void get_sameParams_returnsCachedInstance() {
        MinioAdminClient first = factory.get("http://minio:9000", "admin", "secret");
        MinioAdminClient second = factory.get("http://minio:9000", "admin", "secret");

        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("get - different accessKey returns distinct instance")
    void get_differentAccessKey_returnsDistinctInstance() {
        MinioAdminClient first = factory.get("http://minio:9000", "admin1", "secret1");
        MinioAdminClient second = factory.get("http://minio:9000", "admin2", "secret2");

        assertThat(first).isNotSameAs(second);
    }

    @Test
    @DisplayName("get - different endpoint returns distinct instance")
    void get_differentEndpoint_returnsDistinctInstance() {
        MinioAdminClient first = factory.get("http://minio-a:9000", "admin", "secret");
        MinioAdminClient second = factory.get("http://minio-b:9000", "admin", "secret");

        assertThat(first).isNotSameAs(second);
    }

    @Test
    @DisplayName("get - returns a non-null MinioAdminClient")
    void get_returnsNonNull() {
        MinioAdminClient client = factory.get("http://minio:9000", "admin", "secret");

        assertThat(client).isNotNull();
    }
}