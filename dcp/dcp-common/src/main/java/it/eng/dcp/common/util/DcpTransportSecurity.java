package it.eng.dcp.common.util;

import it.eng.dcp.common.exception.InsecureEndpointException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Single policy point for the transport security of DCP endpoints (DCP v1.0.1, Transport Security and
 * Base URL): endpoints are HTTPS unless the development property {@code dcp.allow-insecure-http} is
 * {@code true}.
 */
public final class DcpTransportSecurity {

    private DcpTransportSecurity() {
    }

    /**
     * Returns the scheme used for URLs the connector derives itself (DID to URL, advertised endpoints).
     *
     * <p>It is {@code https}, except when insecure HTTP is allowed and TLS is not enabled on this server,
     * which keeps plain HTTP development and CI stacks working.
     *
     * @param allowInsecureHttp value of {@code dcp.allow-insecure-http}
     * @param sslEnabled value of {@code server.ssl.enabled}
     * @return {@code http} or {@code https}
     */
    public static String scheme(boolean allowInsecureHttp, boolean sslEnabled) {
        return allowInsecureHttp && !sslEnabled ? "http" : "https";
    }

    /**
     * Checks that a discovered or configured endpoint uses HTTPS, or HTTP when insecure HTTP is allowed.
     *
     * @param endpoint the endpoint URL
     * @param description what the endpoint is, used in the error message
     * @param allowInsecureHttp value of {@code dcp.allow-insecure-http}
     * @throws InsecureEndpointException if the endpoint is blank, not an absolute URL, or not allowed
     */
    public static void requireSecure(String endpoint, String description, boolean allowInsecureHttp) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new InsecureEndpointException("Missing " + description);
        }
        String scheme;
        try {
            scheme = new URI(endpoint.trim()).getScheme();
        } catch (URISyntaxException e) {
            scheme = null;
        }
        if (scheme == null) {
            throw new InsecureEndpointException("Invalid " + description + ": " + endpoint);
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if ("https".equals(scheme) || (allowInsecureHttp && "http".equals(scheme))) {
            return;
        }
        throw new InsecureEndpointException("Insecure " + description + ": " + endpoint
                + " (HTTPS is required; set dcp.allow-insecure-http=true for development only)");
    }
}
