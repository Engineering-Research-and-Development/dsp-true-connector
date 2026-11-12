package it.eng.tools.auth.keycloak;

import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.internal.tls.OkHostnameVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Provides the HTTP client that loads Keycloak discovery documents and JWK sets.
 * <p>
 * The client is derived from the shared {@code okHttpClient} bean, so it has the same TLS trust, host name
 * verification and connection specs, and shares its connection pool and dispatcher. With
 * {@code server.ssl.enabled=true} it trusts the truststore of the {@code connector} SSL bundle, with OCSP checks
 * as configured by {@code application.ocsp.validation.*}, verifies host names and refuses plain {@code http}.
 * With {@code server.ssl.enabled=false} it trusts all certificates and does not verify host names. That mode is
 * not secure: anyone between the connector and Keycloak can serve their own signing keys and then forge tokens
 * the connector accepts.
 * <p>
 * On top of the shared settings, the client never follows redirects, uses the discovery connect and read
 * timeouts, and has a deadline for the whole call, body included, of the connect plus the read timeout.
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
     * Creates the Keycloak HTTP client from the shared OkHttp client.
     *
     * @param okHttpClient the shared OkHttp client, which supplies TLS trust and host name verification
     * @param properties the Keycloak trust configuration, which supplies the timeouts
     * @return the Keycloak HTTP client
     */
    @Bean(KEYCLOAK_HTTP_CLIENT)
    OkHttpClient keycloakHttpClient(OkHttpClient okHttpClient, KeycloakTrustProperties properties) {
        if (!(okHttpClient.hostnameVerifier() instanceof OkHostnameVerifier)) {
            log.warn("Keycloak HTTP client trusts ALL certificates and does not verify host names "
                    + "(server.ssl.enabled=false). Anyone between the connector and Keycloak can serve their own "
                    + "signing keys and forge tokens. Use only for local development and first tests!");
        }
        Duration connectTimeout = properties.getDiscoveryConnectTimeout();
        Duration readTimeout = properties.getDiscoveryReadTimeout();
        return okHttpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .callTimeout(connectTimeout.plus(readTimeout))
                .build();
    }
}
