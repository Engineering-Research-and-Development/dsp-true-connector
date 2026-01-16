package it.eng.dcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.exception.InsecureEndpointException;
import it.eng.dcp.exception.RemoteHolderAuthException;
import it.eng.dcp.exception.RemoteHolderClientException;
import it.eng.dcp.model.PresentationQueryMessage;
import it.eng.dcp.model.PresentationResponseMessage;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for communicating with DCP Holder services to fetch Verifiable Presentations.
 */
@Service
@Slf4j
public class DcpVerifierClient {

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 200L;
    private static final String DCP_PRESENTATIONS_QUERY_PATH = "/dcp/presentations/query";

    private final SimpleOkHttpRestClient httpClient;
    private final ObjectMapper mapper;

    @Autowired
    public DcpVerifierClient(SimpleOkHttpRestClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper;
    }


    /**
     * Fetch presentations from a remote holder.
     *
     * @param holderBase    base URL for holder (e.g., {@code https://holder.example})
     * @param requiredTypes list of credential types to request (may be empty or null)
     * @param accessToken   optional access token (raw token string, not including "Bearer ")
     * @return PresentationResponseMessage deserialized from the response
     * @throws IllegalArgumentException    if holderBase is null or blank
     * @throws InsecureEndpointException   if the holder endpoint is not HTTPS
     * @throws RemoteHolderAuthException   if authentication fails (401/403)
     * @throws RemoteHolderClientException if a client error occurs (4xx)
     * @throws RuntimeException            if the request fails after all retries
     */
    public PresentationResponseMessage fetchPresentations(String holderBase, List<String> requiredTypes, String accessToken) {
        if (holderBase == null || holderBase.isBlank()) {
            throw new IllegalArgumentException("holderBase is required");
        }

        // Enforce HTTPS
        validateSecureEndpoint(holderBase);

        // Construct target URL
        String target = buildTargetUrl(holderBase);

        // Build query message
        PresentationQueryMessage query = buildQueryMessage(requiredTypes);

        // Execute request with retry mechanism
        return executeWithRetries(target, query, accessToken);
    }

    /**
     * Validates that the endpoint uses HTTPS protocol.
     *
     * @param holderBase the base URL to validate
     * @throws InsecureEndpointException if the endpoint is not HTTPS
     */
    private void validateSecureEndpoint(String holderBase) {
        try {
            URI uri = new URI(holderBase);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new InsecureEndpointException("Insecure holder endpoint: " + holderBase);
            }
        } catch (URISyntaxException e) {
            // Allow non-absolute URIs, but still require https prefix
            if (!holderBase.toLowerCase().startsWith("https://")) {
                throw new InsecureEndpointException("Insecure or invalid holder endpoint: " + holderBase);
            }
        }
    }

    /**
     * Builds the target URL for the presentations query endpoint.
     *
     * @param holderBase the base URL of the holder
     * @return the complete URL for the presentations query endpoint
     */
    private String buildTargetUrl(String holderBase) {
        return holderBase.endsWith("/")
                ? holderBase + "dcp/presentations/query"
                : holderBase + DCP_PRESENTATIONS_QUERY_PATH;
    }

    /**
     * Builds a PresentationQueryMessage with the required credential types.
     *
     * @param requiredTypes list of credential types to request
     * @return the constructed PresentationQueryMessage
     */
    private PresentationQueryMessage buildQueryMessage(List<String> requiredTypes) {
        PresentationQueryMessage.Builder builder = PresentationQueryMessage.Builder.newInstance();
        if (requiredTypes != null && !requiredTypes.isEmpty()) {
            builder.scope(requiredTypes);
        }
        return builder.build();
    }

    /**
     * Executes the HTTP request with retry logic and exponential backoff.
     *
     * @param target      the target URL
     * @param query       the query message
     * @param accessToken optional access token
     * @return the deserialized PresentationResponseMessage
     * @throws RemoteHolderAuthException   if authentication fails
     * @throws RemoteHolderClientException if a client error occurs
     * @throws RuntimeException            if all retry attempts fail
     */
    private PresentationResponseMessage executeWithRetries(String target, PresentationQueryMessage query, String accessToken) {
        int attempt = 0;
        long backoffMs = INITIAL_BACKOFF_MS;

        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                return executeRequest(target, query, accessToken);
            } catch (IOException e) {
                log.warn("Attempt {}/{} failed for {}: {}", attempt, MAX_RETRIES, target, e.getMessage());

                // Check if the error is an auth or client error (non-retryable)
                String errorMessage = e.getMessage();
                if (errorMessage != null) {
                    if (errorMessage.contains("401") || errorMessage.toLowerCase().contains("unauthorized")) {
                        throw new RemoteHolderAuthException("Unauthorized when calling holder: " + errorMessage);
                    }
                    if (errorMessage.contains("403") || errorMessage.toLowerCase().contains("forbidden")) {
                        throw new RemoteHolderAuthException("Forbidden when calling holder: " + errorMessage);
                    }
                    if (errorMessage.contains("400") || errorMessage.contains("404") || errorMessage.contains("422")) {
                        throw new RemoteHolderClientException("Client error from holder: " + errorMessage);
                    }
                }

                // If this was the last attempt, throw the exception
                if (attempt >= MAX_RETRIES) {
                    throw new RuntimeException("Failed to fetch presentations from holder after " + MAX_RETRIES + " attempts: " + e.getMessage(), e);
                }

                // Otherwise, wait and retry with exponential backoff
                log.info("Retrying in {}ms...", backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting to retry", ie);
                }
                backoffMs *= 2;
            }
        }

        throw new RuntimeException("Failed to fetch presentations from holder after " + MAX_RETRIES + " attempts");
    }

    /**
     * Executes a single HTTP request to fetch presentations.
     *
     * @param target      the target URL
     * @param query       the query message
     * @param accessToken optional access token
     * @return the deserialized PresentationResponseMessage
     * @throws IOException if the request fails or response cannot be deserialized
     */
    private PresentationResponseMessage executeRequest(String target, PresentationQueryMessage query, String accessToken) throws IOException {
        // Serialize query to JSON
        JsonNode bodyNode = mapper.valueToTree(query);
        String jsonBody = bodyNode.toPrettyString();

        // Create request body
        RequestBody requestBody = RequestBody.create(jsonBody, MediaType.parse("application/json"));

        // Build headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        // Add authorization if provided
        if (StringUtils.isNotBlank(accessToken)) {
            headers.put("Authorization", "Bearer " + accessToken);
        }

        log.debug("Sending presentation query to {}", target);

        // Execute request and deserialize response
        return httpClient.executeAndDeserialize(
                target,
                "POST",
                headers,
                requestBody,
                PresentationResponseMessage.class
        );
    }
}
