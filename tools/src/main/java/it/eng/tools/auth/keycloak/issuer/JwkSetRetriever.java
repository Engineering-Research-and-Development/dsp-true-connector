package it.eng.tools.auth.keycloak.issuer;

import com.nimbusds.jose.util.Resource;
import com.nimbusds.jose.util.ResourceRetriever;
import it.eng.tools.auth.keycloak.BoundedOkHttpCall;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Loads JWK sets over HTTP with the Keycloak HTTP client. Unlike the Nimbus default retriever it never follows
 * redirects and only accepts {@code http} and {@code https} URLs, so the only URL fetched is the
 * {@code jwks_uri} of the issuer's verified discovery document. The whole request, body included, must
 * complete within the call timeout of the client, and the body must not exceed the size limit.
 */
final class JwkSetRetriever implements ResourceRetriever {

    private final OkHttpClient httpClient;
    private final int sizeLimit;

    /**
     * Creates a retriever.
     *
     * @param httpClient the Keycloak HTTP client, which must not follow redirects and whose call timeout
     *        bounds the whole request
     * @param sizeLimit the maximum response size, in bytes
     */
    JwkSetRetriever(OkHttpClient httpClient, int sizeLimit) {
        this.httpClient = httpClient;
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
        HttpUrl httpUrl = HttpUrl.get(url);
        if (httpUrl == null) {
            throw new IOException("Invalid JWK set URL");
        }
        Request request = new Request.Builder()
                .url(httpUrl)
                .header("Accept", "application/json")
                .get()
                .build();
        BoundedOkHttpCall.Result response = BoundedOkHttpCall.execute(httpClient, request, sizeLimit);
        if (!response.isSuccessful()) {
            throw new IOException("JWK set request returned HTTP " + response.statusCode());
        }
        return new Resource(new String(response.body(), StandardCharsets.UTF_8), response.contentType().orElse(null));
    }
}
