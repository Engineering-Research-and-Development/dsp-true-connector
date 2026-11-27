package it.eng.tools.auth.keycloak;

import okhttp3.OkHttpClient;

import java.time.Duration;

/**
 * Builds Keycloak HTTP clients for tests that talk to a local plain-HTTP server. They are configured like the
 * {@code keycloakHttpClient} bean: no redirects, and a call timeout of connect plus read timeout.
 */
public final class KeycloakTestHttpClients {

    private KeycloakTestHttpClients() {
    }

    /**
     * Builds a Keycloak HTTP client with the discovery timeouts of the trust configuration.
     *
     * @param properties supplies the connect and read timeouts
     * @return the HTTP client
     */
    public static OkHttpClient forProperties(KeycloakTrustProperties properties) {
        return withTimeouts(properties.getDiscoveryConnectTimeout(), properties.getDiscoveryReadTimeout());
    }

    /**
     * Builds a Keycloak HTTP client with the given timeouts.
     *
     * @param connectTimeout the connect timeout
     * @param readTimeout the read timeout
     * @return the HTTP client
     */
    public static OkHttpClient withTimeouts(Duration connectTimeout, Duration readTimeout) {
        return new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .callTimeout(connectTimeout.plus(readTimeout))
                .build();
    }

    /**
     * Builds a discovery client with the discovery timeouts of the trust configuration.
     *
     * @param properties the Keycloak trust configuration
     * @return the discovery client
     */
    public static KeycloakDiscoveryClient discoveryClient(KeycloakTrustProperties properties) {
        return new KeycloakDiscoveryClient(properties, forProperties(properties));
    }
}
