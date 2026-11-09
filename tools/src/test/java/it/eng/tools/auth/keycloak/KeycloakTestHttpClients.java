package it.eng.tools.auth.keycloak;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * Builds Keycloak HTTP clients for tests that talk to a local plain-HTTP server.
 */
public final class KeycloakTestHttpClients {

    private KeycloakTestHttpClients() {
    }

    /**
     * Builds a Keycloak HTTP client over the JVM default truststore.
     *
     * @param properties supplies the connect timeout
     * @return the HTTP client
     */
    public static HttpClient forProperties(KeycloakTrustProperties properties) {
        return withConnectTimeout(properties.getDiscoveryConnectTimeout());
    }

    /**
     * Builds a Keycloak HTTP client over the JVM default truststore.
     *
     * @param connectTimeout the connect timeout
     * @return the HTTP client
     */
    public static HttpClient withConnectTimeout(Duration connectTimeout) {
        try {
            return KeycloakHttpClientConfiguration.newHttpClient(connectTimeout, SSLContext.getDefault());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Builds a discovery client over the JVM default truststore.
     *
     * @param properties the Keycloak trust configuration
     * @return the discovery client
     */
    public static KeycloakDiscoveryClient discoveryClient(KeycloakTrustProperties properties) {
        return new KeycloakDiscoveryClient(properties, forProperties(properties));
    }
}
