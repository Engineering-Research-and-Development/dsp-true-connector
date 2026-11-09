package it.eng.tools.auth.keycloak.issuer;

import com.nimbusds.jose.util.Resource;
import com.nimbusds.jose.util.ResourceRetriever;
import it.eng.tools.auth.keycloak.BoundedHttpExchange;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Loads JWK sets over HTTP with the Keycloak JDK client. Unlike the Nimbus default retriever it never follows
 * redirects and only accepts {@code http} and {@code https} URLs, so the only URL fetched is the
 * {@code jwks_uri} of the issuer's verified discovery document. The whole request, body included, must
 * complete within the connect plus the read timeout, and the body must not exceed the size limit.
 */
final class JwkSetRetriever implements ResourceRetriever {

    private final HttpClient httpClient;
    private final Duration readTimeout;
    private final Duration exchangeTimeout;
    private final int sizeLimit;

    /**
     * Creates a retriever.
     *
     * @param httpClient the Keycloak HTTP client, which must not follow redirects
     * @param connectTimeout the connect timeout of {@code httpClient}
     * @param readTimeout the timeout for the response headers; the whole exchange is bounded by the connect
     *        plus the read timeout
     * @param sizeLimit the maximum response size, in bytes
     */
    JwkSetRetriever(HttpClient httpClient, Duration connectTimeout, Duration readTimeout, int sizeLimit) {
        this.httpClient = httpClient;
        this.readTimeout = readTimeout;
        this.exchangeTimeout = connectTimeout.plus(readTimeout);
        this.sizeLimit = sizeLimit;
    }

    /**
     * Fetches the resource.
     *
     * @param url the JWK set URL
     * @return the response body and content type
     * @throws IOException when the URL is not http(s), the request fails or times out, the status is not
     *         2xx or the response exceeds the size limit
     */
    @Override
    public Resource retrieveResource(URL url) throws IOException {
        String protocol = url.getProtocol();
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            throw new IOException("Unsupported JWK set URL scheme");
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(url.toURI())
                    .timeout(readTimeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new IOException("Invalid JWK set URL", e);
        }
        try {
            BoundedHttpExchange.Result response = BoundedHttpExchange.send(httpClient, request, exchangeTimeout,
                    sizeLimit);
            if (!response.isSuccessful()) {
                throw new IOException("JWK set request returned HTTP " + response.statusCode());
            }
            String contentType = response.headers().firstValue("Content-Type").orElse(null);
            return new Resource(new String(response.body(), StandardCharsets.UTF_8), contentType);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("JWK set request was interrupted", e);
        }
    }
}
