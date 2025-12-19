package it.eng.dcp.issuer.client;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

/**
 * Minimal implementation of HTTP client functionality for DID resolution.
 * This class provides just the executeCall method needed by HttpDidResolverService,
 * matching the OkHttpRestClient interface from tools without requiring the full dependency.
 */
@Slf4j
public class SimpleOkHttpRestClient {

    private final OkHttpClient okHttpClient;

    /**
     * Constructor.
     *
     * @param okHttpClient The OkHttpClient to use
     */
    public SimpleOkHttpRestClient(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    /**
     * Executes an HTTP request.
     * This matches the signature of OkHttpRestClient.executeCall() used by HttpDidResolverService.
     *
     * @param request The request to execute
     * @return The response, or null if an error occurs
     */
    public Response executeCall(Request request) {
        try {
            return okHttpClient.newCall(request).execute();
        } catch (IOException e) {
            log.error("Error while executing HTTP call to {}: {}", request.url(), e.getMessage(), e);
            return null;
        }
    }
}

