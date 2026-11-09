package it.eng.tools.auth.keycloak;

import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import it.eng.tools.ssl.ocsp.OcspTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;

/**
 * Provides the JDK HTTP client that loads Keycloak discovery documents and JWK sets.
 * <p>
 * With {@code server.ssl.enabled=true} the client trusts the truststore of the {@code connector} SSL bundle,
 * with OCSP checks as configured by {@code application.ocsp.validation.*}. That is the same trust as the
 * OkHttp client that requests Keycloak tokens. With {@code server.ssl.enabled=false} it uses the JVM default
 * truststore. It never trusts all certificates: the JWK sets it loads are the keys that verify every token.
 */
@Slf4j
@Configuration
@Conditional(KeycloakAuthenticationModeCondition.class)
public class KeycloakHttpClientConfiguration {

    /**
     * Name of the Keycloak HTTP client bean.
     */
    public static final String KEYCLOAK_HTTP_CLIENT = "keycloakHttpClient";

    /**
     * Creates the Keycloak HTTP client.
     *
     * @param properties the Keycloak trust configuration, which supplies the connect timeout
     * @param ocspTrustManagerFactory creates the OCSP trust managers over the connector SSL bundle
     * @param sslEnabled the value of {@code server.ssl.enabled}
     * @return the HTTP client
     * @throws GeneralSecurityException when the TLS context cannot be created
     */
    @Bean(KEYCLOAK_HTTP_CLIENT)
    HttpClient keycloakHttpClient(KeycloakTrustProperties properties, OcspTrustManagerFactory ocspTrustManagerFactory,
            @Value("${server.ssl.enabled:false}") boolean sslEnabled) throws GeneralSecurityException {
        SSLContext sslContext;
        if (sslEnabled) {
            log.info("Keycloak HTTP client trusts the connector SSL bundle truststore, with OCSP validation");
            sslContext = SSLContext.getInstance("TLS");
            TrustManager[] trustManagers = ocspTrustManagerFactory.createTrustManagers();
            sslContext.init(null, trustManagers, new SecureRandom());
        } else {
            log.info("Keycloak HTTP client trusts the JVM default truststore (server.ssl.enabled=false)");
            sslContext = SSLContext.getDefault();
        }
        return newHttpClient(properties.getDiscoveryConnectTimeout(), sslContext);
    }

    /**
     * Builds a Keycloak HTTP client: it never follows redirects and verifies host names.
     *
     * @param connectTimeout the connect timeout
     * @param sslContext the TLS context
     * @return the HTTP client
     */
    public static HttpClient newHttpClient(Duration connectTimeout, SSLContext sslContext) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .sslContext(sslContext)
                .build();
    }
}
