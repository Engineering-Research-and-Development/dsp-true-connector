package it.eng.dcp.common.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Map;

/**
 * Minimal implementation of HTTP client functionality for DID resolution.
 * This class provides just the executeCall method needed by HttpDidResolverService,
 * matching the OkHttpRestClient interface from tools without requiring the full dependency.
 */
@Slf4j
public class SimpleOkHttpRestClient {

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    /**
     * Constructor.
     *
     * @param okHttpClient The OkHttpClient to use
     */
    public SimpleOkHttpRestClient(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Constructor with OkHttpClient and custom ObjectMapper.
     *
     * @param okHttpClient The OkHttpClient to use
     * @param objectMapper The ObjectMapper to use for JSON serialization/deserialization
     */
    public SimpleOkHttpRestClient(OkHttpClient okHttpClient, ObjectMapper objectMapper) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
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

    /**
     * Executes an HTTP request with custom headers and deserializes the response body to the specified type.
     * This method builds a request with the provided URL, HTTP method, headers, and optional request body,
     * then executes it and deserializes the successful response (2xx status codes) to the target class.
     *
     * @param <T>          The type of the expected response object
     * @param url          The target URL for the request
     * @param method       The HTTP method (GET, POST, etc.)
     * @param headers      A map of HTTP headers to include in the request (can be null or empty)
     * @param requestBody  The request body (can be null for GET requests)
     * @param responseType The class type to deserialize the response into
     * @return The deserialized response object, or null if the request fails or response cannot be deserialized
     * @throws IOException if an I/O error occurs during request execution or response deserialization
     */
    public <T> T executeAndDeserialize(String url, String method, Map<String, String> headers,
                                       okhttp3.RequestBody requestBody, Class<T> responseType) throws IOException {
        // Build the request
        Request.Builder requestBuilder = new Request.Builder().url(url);

        // Add headers if provided
        if (headers != null && !headers.isEmpty()) {
            headers.forEach(requestBuilder::addHeader);
        }

        // Set the HTTP method
        requestBuilder.method(method, requestBody);

        Request request = requestBuilder.build();
        log.debug("Executing {} request to {}", method, url);

        // Execute the request
        try (Response response = okHttpClient.newCall(request).execute()) {
            int statusCode = response.code();
            log.debug("Response status code: {}", statusCode);

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                log.error("Request to {} failed with status {}: {}", url, statusCode, errorBody);
                throw new IOException("HTTP request failed with status " + statusCode + ": " + errorBody);
            }

            // Deserialize response body
            if (response.body() != null) {
                String responseBody = response.body().string();
                log.debug("Response body received from {}: {}", url, responseBody);
                return objectMapper.readValue(responseBody, responseType);
            } else {
                log.warn("Response body is null for request to {}", url);
                return null;
            }
        } catch (IOException e) {
            log.error("Error while executing HTTP call to {}: {}", url, e.getMessage(), e);
            throw e;
        }
    }
}

