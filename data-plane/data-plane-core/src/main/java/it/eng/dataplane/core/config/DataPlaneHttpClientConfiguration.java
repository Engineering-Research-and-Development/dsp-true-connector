package it.eng.dataplane.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * Configures the shared {@link HttpClient} used by data-plane protocols for artifact downloads
 * (presigned GET URL → S3 upload). The client mirrors the SSL posture of the connector's
 * {@code OkHttpClientConfiguration} (in {@code tools}):
 * <ul>
 *   <li><b>Insecure mode</b> ({@code server.ssl.enabled=false}): trust-all {@link SSLContext},
 *       accepting all certificates without validation — development / testing only.</li>
 *   <li><b>Secure mode</b> ({@code server.ssl.enabled=true}): {@link SSLContext} built from the
 *       {@code connector} SSL bundle, which must be configured in the DP's application properties
 *       via {@code spring.ssl.bundle.jks.connector.*} (custom keystore + truststore).</li>
 * </ul>
 *
 * <p>{@code java.net.http.HttpClient} does not use {@link javax.net.ssl.HttpsURLConnection}
 * JVM-level defaults set by {@code GlobalSSLConfiguration}, so the {@link SSLContext} must be
 * supplied explicitly via {@link HttpClient.Builder#sslContext(SSLContext)}.
 */
@Configuration
@Slf4j
public class DataPlaneHttpClientConfiguration {

    private static final String SSL_BUNDLE_NAME = "connector";
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    private final SslBundles sslBundles;
    private final boolean sslEnabled;

    /**
     * Constructs the configuration.
     *
     * @param sslBundles Spring SSL bundles registry
     * @param sslEnabled {@code true} when {@code server.ssl.enabled=true}
     */
    public DataPlaneHttpClientConfiguration(SslBundles sslBundles,
                                            @Value("${server.ssl.enabled:false}") boolean sslEnabled) {
        this.sslBundles = sslBundles;
        this.sslEnabled = sslEnabled;
    }

    /**
     * Creates the shared {@link HttpClient} for artifact downloads.
     * <p>Prefers HTTP/2 via ALPN on TLS connections; falls back to HTTP/1.1 for plain HTTP
     * (e.g. development MinIO without TLS). The instance is thread-safe and intended to be
     * reused across all concurrent transfers.
     *
     * @return configured {@link HttpClient}
     * @throws NoSuchAlgorithmException if TLS algorithm is unavailable
     * @throws KeyManagementException   if SSLContext initialisation fails
     */
    @Bean
    public HttpClient dataPlaneHttpClient() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = sslEnabled ? secureContext() : insecureContext();
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS))
                .sslContext(sslContext)
                .build();
        log.info("DataPlane HttpClient created (ssl.enabled={}, HTTP/2 preferred)", sslEnabled);
        return client;
    }

    /**
     * Builds an {@link SSLContext} from the {@code connector} SSL bundle.
     * Requires {@code spring.ssl.bundle.jks.connector.*} to be configured in the DP's
     * application properties (custom keystore + truststore, not the JVM default).
     *
     * @return {@link SSLContext} backed by the configured truststore
     */
    private SSLContext secureContext() {
        try {
            SSLContext ctx = sslBundles.getBundle(SSL_BUNDLE_NAME).createSslContext();
            log.info("DataPlane HttpClient: using SSLContext from '{}' bundle", SSL_BUNDLE_NAME);
            return ctx;
        } catch (NoSuchSslBundleException e) {
            log.error("SSL bundle '{}' not found — check spring.ssl.bundle.jks.connector.* properties; "
                    + "falling back to JVM default SSLContext", SSL_BUNDLE_NAME, e);
            return defaultContext();
        }
    }

    /**
     * Builds a trust-all {@link SSLContext} that accepts every certificate without validation.
     * Used in insecure (development / testing) mode only.
     *
     * @return trust-all {@link SSLContext}
     * @throws NoSuchAlgorithmException if TLS algorithm is unavailable
     * @throws KeyManagementException   if SSLContext initialisation fails
     */
    private SSLContext insecureContext() throws NoSuchAlgorithmException, KeyManagementException {
        log.warn("DataPlane HttpClient: creating trust-all SSLContext (server.ssl.enabled=false) — development only!");
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) { }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) { }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[]{};
                }
            }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAllCerts, new SecureRandom());
        return ctx;
    }

    /**
     * Returns the JVM default {@link SSLContext} as a last-resort fallback when the SSL bundle
     * is misconfigured.
     *
     * @return JVM default {@link SSLContext}
     */
    private static SSLContext defaultContext() {
        try {
            return SSLContext.getDefault();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot obtain JVM default SSLContext", e);
        }
    }
}
