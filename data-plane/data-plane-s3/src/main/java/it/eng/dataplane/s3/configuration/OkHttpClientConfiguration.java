package it.eng.dataplane.s3.configuration;

import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Configuration that provides an {@link OkHttpClient} bean for use by MinIO admin client
 * and control-plane communication clients.
 * When {@code server.ssl.enabled=true}, a standard TLS client is created.
 * When {@code server.ssl.enabled=false}, an insecure client is created for development.
 */
@Configuration
@Slf4j
public class OkHttpClientConfiguration {

    private final boolean sslEnabled;

    /**
     * Constructs the configuration.
     *
     * @param sslEnabled whether SSL is enabled ({@code server.ssl.enabled})
     */
    public OkHttpClientConfiguration(@Value("${server.ssl.enabled:false}") boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    /**
     * Creates an {@link OkHttpClient} bean appropriate for the current SSL configuration.
     *
     * @return a configured OkHttpClient instance
     * @throws NoSuchAlgorithmException if the TLS algorithm is unavailable
     * @throws KeyManagementException   if key management initialization fails
     */
    @Bean
    public OkHttpClient okHttpClient() throws NoSuchAlgorithmException, KeyManagementException {
        if (!sslEnabled) {
            log.warn("Creating insecure OkHttpClient (server.ssl.enabled=false) - for development only");
            return createInsecureClient();
        }
        log.info("Creating standard OkHttpClient (server.ssl.enabled=true)");
        return new OkHttpClient.Builder()
                .connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS))
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    private OkHttpClient createInsecureClient() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCerts = new TrustManager[]{
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
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return new OkHttpClient.Builder()
                .connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true)
                .build();
    }
}
