package it.eng.tools.s3.configuration;

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
     * Returns a secure client if SSL is enabled, or an insecure client otherwise.
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

                log.info("SSL configured - KeyManagers count: {}, TrustManagers count: {}",
                        keyManagers.length, trustManagers.length);

                // Log certificate details from truststore
                logTrustManagerCertificates(trustManagers);

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
     * Replaces the previous Netty-based client which rejected percent-encoded characters
     * (e.g. %3A for ':') in URI paths when Netty >= 4.2.x strict validation is active.
     * Returns a secure client if SSL is enabled, or an insecure client otherwise.
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

                log.info("SSL configured for async - KeyManagers count: {}, TrustManagers count: {}",
                        keyManagers.length, trustManagers.length);

                // Log certificate details from truststore
                logTrustManagerCertificates(trustManagers);

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

    /**
     * Logs details about certificates in the trust managers.
     *
     * @param trustManagers array of trust managers to inspect
     */
    private void logTrustManagerCertificates(javax.net.ssl.TrustManager[] trustManagers) {
        for (javax.net.ssl.TrustManager tm : trustManagers) {
            if (tm instanceof X509TrustManager) {
                X509TrustManager x509tm = (X509TrustManager) tm;
                X509Certificate[] certs = x509tm.getAcceptedIssuers();
                log.info("TrustManager contains {} certificates", certs.length);
                for (X509Certificate cert : certs) {
                    log.info("  - Certificate Subject: {}", cert.getSubjectX500Principal().getName());
                    log.info("    Issuer: {}", cert.getIssuerX500Principal().getName());

                    // Log Subject Alternative Names
                    try {
                        var sans = cert.getSubjectAlternativeNames();
                        if (sans != null) {
                            log.info("    Subject Alternative Names:");
                            sans.forEach(san -> {
                                // SAN is a list where first element is type (2 = DNS name)
                                log.info("      Type {}: {}", san.get(0), san.get(1));
                            });
                        }
                    } catch (Exception e) {
                        log.debug("    No Subject Alternative Names found");
                    }
                }
            }
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
                    .expectContinueEnabled(false)
                    .tlsTrustManagersProvider(this::getTrustAllManagers)
                    .build();
        } catch (Exception e) {
            log.error("Failed to create insecure Apache HTTP client", e);
            throw new RuntimeException("Failed to create insecure Apache HTTP client", e);
        }
    }

    /**
     * Creates an insecure AWS CRT HTTP client that accepts all certificates.
     * Should only be used when SSL is disabled (development/testing).
     * @return Insecure SdkAsyncHttpClient
     */
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
