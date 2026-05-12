package it.eng.dataplane.s3.configuration;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient;
import software.amazon.awssdk.utils.AttributeMap;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * Factory for creating HTTP clients for S3 operations with conditional SSL/TLS configuration.
 * Creates secure clients when server.ssl.enabled=true, or insecure clients otherwise.
 */
@Configuration
@Slf4j
public class S3HttpClientFactory {

    private static final String SSL_BUNDLE_NAME = "connector";

    @Getter
    private final boolean sslEnabled;
    private final SslBundles sslBundles;

    /**
     * Constructs the factory with SSL configuration.
     *
     * @param sslEnabled  whether SSL is enabled
     * @param sslBundles  the SSL bundles provider
     */
    public S3HttpClientFactory(
            @Value("${server.ssl.enabled:false}") boolean sslEnabled,
            SslBundles sslBundles) {
        this.sslEnabled = sslEnabled;
        this.sslBundles = sslBundles;

        if (sslEnabled) {
            log.info("S3HttpClientFactory initialized with SSL enabled - will create secure S3 clients");
        } else {
            log.warn("S3HttpClientFactory initialized with SSL disabled - will create INSECURE S3 clients");
        }
    }

    /**
     * Creates an Apache HTTP client bean for synchronous S3 operations.
     *
     * @return SdkHttpClient configured based on SSL settings
     */
    @Bean
    public SdkHttpClient sdkHttpClient() {
        if (sslEnabled) {
            log.info("Creating secure Apache HTTP client for S3 with SSL bundle: {}", SSL_BUNDLE_NAME);
            try {
                var bundle = sslBundles.getBundle(SSL_BUNDLE_NAME);
                var keyManagers = bundle.getManagers().getKeyManagerFactory().getKeyManagers();
                var trustManagers = bundle.getManagers().getTrustManagerFactory().getTrustManagers();
                return ApacheHttpClient.builder()
                        .tlsKeyManagersProvider(() -> keyManagers)
                        .tlsTrustManagersProvider(() -> trustManagers)
                        .expectContinueEnabled(false)
                        .build();
            } catch (Exception e) {
                log.error("Failed to create secure Apache HTTP client", e);
                throw e;
            }
        } else {
            log.warn("Creating INSECURE Apache HTTP client for S3 - SSL is disabled");
            return createInsecureApacheHttpClient();
        }
    }

    /**
     * Creates an AWS CRT HTTP client bean for asynchronous S3 operations.
     *
     * @return SdkAsyncHttpClient configured based on SSL settings
     */
    @Bean
    public SdkAsyncHttpClient sdkAsyncHttpClient() {
        if (sslEnabled) {
            log.info("Creating secure AWS CRT HTTP client for S3AsyncClient with SSL bundle: {}", SSL_BUNDLE_NAME);
            try {
                var bundle = sslBundles.getBundle(SSL_BUNDLE_NAME);
                var keyManagers = bundle.getManagers().getKeyManagerFactory().getKeyManagers();
                var trustManagers = bundle.getManagers().getTrustManagerFactory().getTrustManagers();
                return AwsCrtAsyncHttpClient.builder()
                        .buildWithDefaults(AttributeMap.builder()
                                .put(SdkHttpConfigurationOption.TLS_KEY_MANAGERS_PROVIDER, () -> keyManagers)
                                .put(SdkHttpConfigurationOption.TLS_TRUST_MANAGERS_PROVIDER, () -> trustManagers)
                                .build());
            } catch (Exception e) {
                log.error("Failed to create secure AWS CRT HTTP client", e);
                throw e;
            }
        } else {
            log.warn("Creating INSECURE AWS CRT HTTP client for S3AsyncClient - SSL is disabled");
            return createInsecureCrtHttpClient();
        }
    }

    private SdkHttpClient createInsecureApacheHttpClient() {
        try {
            return ApacheHttpClient.builder()
                    .expectContinueEnabled(false)
                    .tlsTrustManagersProvider(this::getTrustAllManagers)
                    .build();
        } catch (Exception e) {
            log.error("Failed to create insecure Apache HTTP client", e);
            throw new RuntimeException("Failed to create insecure Apache HTTP client", e);
        }
    }

    private SdkAsyncHttpClient createInsecureCrtHttpClient() {
        try {
            return AwsCrtAsyncHttpClient.builder()
                    .buildWithDefaults(AttributeMap.builder()
                            .put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, Boolean.TRUE)
                            .build());
        } catch (Exception e) {
            log.error("Failed to create insecure AWS CRT HTTP client", e);
            throw new RuntimeException("Failed to create insecure AWS CRT HTTP client", e);
        }
    }

    private TrustManager[] getTrustAllManagers() {
        return new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[]{};
                }
            }
        };
    }
}
