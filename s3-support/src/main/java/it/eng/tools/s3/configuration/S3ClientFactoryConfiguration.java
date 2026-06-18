package it.eng.tools.s3.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

/**
 * Registers the fallback {@link S3ClientFactory} bean for data-plane mode.
 *
 * <p>When {@code s3.access-key} is configured, {@link S3ClientProvider} registers itself as
 * the {@link S3ClientFactory} (connector mode). When it is absent, this configuration
 * registers {@link DynamicS3ClientFactory} as the fallback. Placing
 * {@link ConditionalOnMissingBean} on a {@link Bean} method (rather than directly on
 * a {@link org.springframework.stereotype.Service}) guarantees reliable evaluation order
 * during the Spring Boot context refresh.</p>
 */
@Configuration
public class S3ClientFactoryConfiguration {

    /**
     * Registers {@link DynamicS3ClientFactory} when no other {@link S3ClientFactory} bean
     * is present (i.e., {@link S3ClientProvider} is inactive because {@code s3.access-key}
     * is not configured).
     *
     * @param sdkHttpClient      synchronous HTTP client
     * @param sdkAsyncHttpClient asynchronous HTTP client
     * @return the data-plane S3 client factory
     */
    @Bean
    @ConditionalOnMissingBean(S3ClientFactory.class)
    public DynamicS3ClientFactory dynamicS3ClientFactory(SdkHttpClient sdkHttpClient,
                                                         SdkAsyncHttpClient sdkAsyncHttpClient) {
        return new DynamicS3ClientFactory(sdkHttpClient, sdkAsyncHttpClient);
    }
}
