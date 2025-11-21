package it.eng.tools.s3.configuration;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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

    public S3HttpClientFactory(
            @Value("${server.ssl.enabled:false}") boolean sslEnabled,
            SslBundles sslBundles) {
        this.sslEnabled = true;
        this.sslBundles = sslBundles;

        if (sslEnabled) {
            log.info("S3HttpClientFactory initialized with SSL enabled - will create secure S3 clients");
        } else {
            log.warn("S3HttpClientFactory initialized with SSL disabled - will create INSECURE S3 clients");
        }
    }

    /**
     * Creates an Apache HTTP client bean for synchronous S3 operations.
     * Returns a secure client if SSL is enabled, or an insecure client otherwise.
     *
     * @return SdkHttpClient configured based on SSL settings
     */
    @Bean
    public SdkHttpClient sdkHttpClient() {
        if (sslEnabled) {
            log.debug("Creating secure Apache HTTP client for S3");
            return ApacheHttpClient.builder()
                    .tlsKeyManagersProvider(() -> sslBundles.getBundle(SSL_BUNDLE_NAME)
                            .getManagers()
                            .getKeyManagerFactory()
                            .getKeyManagers())
                    .tlsTrustManagersProvider(() -> sslBundles.getBundle(SSL_BUNDLE_NAME)
                            .getManagers()
                            .getTrustManagerFactory()
                            .getTrustManagers())
                    .build();
        } else {
            log.debug("Creating insecure Apache HTTP client for S3");
            return createInsecureApacheHttpClient();
        }
    }

    /**
     * Creates a Netty HTTP client bean for asynchronous S3 operations.
     * Returns a secure client if SSL is enabled, or an insecure client otherwise.
     *
     * @return SdkAsyncHttpClient configured based on SSL settings
     */
    @Bean
    public SdkAsyncHttpClient sdkAsyncHttpClient() {
        if (sslEnabled) {
            log.debug("Creating secure Netty HTTP client for S3AsyncClient");
            return NettyNioAsyncHttpClient.builder()
                    .tlsKeyManagersProvider(() -> sslBundles.getBundle(SSL_BUNDLE_NAME)
                            .getManagers()
                            .getKeyManagerFactory()
                            .getKeyManagers())
                    .tlsTrustManagersProvider(() -> sslBundles.getBundle(SSL_BUNDLE_NAME)
                            .getManagers()
                            .getTrustManagerFactory()
                            .getTrustManagers())
                    .build();
        } else {
            log.debug("Creating insecure Netty HTTP client for S3AsyncClient");
            return createInsecureNettyHttpClient();
        }
    }

    /**
     * Creates an insecure Apache HTTP client that accepts all certificates.
     * Should only be used when SSL is disabled (development/testing).
     * @return Insecure SdkHttpClient
     */
    private SdkHttpClient createInsecureApacheHttpClient() {
        try {
            return ApacheHttpClient.builder()
                    .tlsTrustManagersProvider(this::getTrustAllManagers)
                    .build();
        } catch (Exception e) {
            log.error("Failed to create insecure Apache HTTP client", e);
            throw new RuntimeException("Failed to create insecure Apache HTTP client", e);
        }
    }

    /**
     * Creates an insecure Netty HTTP client that accepts all certificates.
     * Should only be used when SSL is disabled (development/testing).
     * @return Insecure SdkAsyncHttpClient
     */
    private SdkAsyncHttpClient createInsecureNettyHttpClient() {
        try {
            return NettyNioAsyncHttpClient.builder()
                    .tlsTrustManagersProvider(this::getTrustAllManagers)
                    .build();
        } catch (Exception e) {
            log.error("Failed to create insecure Netty HTTP client", e);
            throw new RuntimeException("Failed to create insecure Netty HTTP client", e);
        }
    }


    /**
     * Returns trust managers that accept all certificates.
     * @return TrustManager array that trusts all certificates
     */
    private TrustManager[] getTrustAllManagers() {
        return new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
            }
        };
    }

}

