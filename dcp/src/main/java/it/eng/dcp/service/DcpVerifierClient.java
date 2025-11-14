package it.eng.dcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.model.PresentationQueryMessage;
import it.eng.dcp.model.PresentationResponseMessage;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@Service
@Slf4j
public class DcpVerifierClient {

    private final OkHttpRestClient httpClient;
    private final ObjectMapper mapper;

    @Autowired
    public DcpVerifierClient(OkHttpRestClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    /**
     * Fetch presentations from a remote holder.
     * @param holderBase base url for holder (e.g. https://holder.example)
     * @param requiredTypes list of credential types to request (may be empty)
     * @param accessToken optional access token (raw token string, not including "Bearer ")
     * @return PresentationResponseMessage deserialized
     */
    public PresentationResponseMessage fetchPresentations(String holderBase, List<String> requiredTypes, String accessToken) {
        if (holderBase == null || holderBase.isBlank()) throw new IllegalArgumentException("holderBase is required");
        // enforce https
        try {
            URI u = new URI(holderBase);
            if (!"https".equalsIgnoreCase(u.getScheme())) {
                throw new InsecureEndpointException("Insecure holder endpoint: " + holderBase);
            }
        } catch (URISyntaxException e) {
            // allow non-absolute, but still require https prefix
            if (!holderBase.toLowerCase().startsWith("https://")) {
                throw new InsecureEndpointException("Insecure or invalid holder endpoint: " + holderBase);
            }
        }

        String target = holderBase.endsWith("/") ? holderBase + "dcp/presentations/query" : holderBase + "/dcp/presentations/query";

        PresentationQueryMessage.Builder b = PresentationQueryMessage.Builder.newInstance();
        if (requiredTypes != null && !requiredTypes.isEmpty()) b.scope(requiredTypes);
        PresentationQueryMessage query = b.build();

        JsonNode body = mapper.valueToTree(query);

        String authHeader = null;
        if (StringUtils.isNotBlank(accessToken)) authHeader = "Bearer " + accessToken;

        int maxRetries = 3;
        int attempt = 0;
        long backoffMs = 200L;
        while (true) {
            attempt++;
            try {
                GenericApiResponse<String> resp = httpClient.sendRequestProtocol(target, body, authHeader);
                if (resp == null) throw new RuntimeException("No response from holder");
                if (resp.isSuccess()) {
                    String data = resp.getData();
                    if (data == null) data = "{}";
                    PresentationResponseMessage prm = mapper.readValue(data, PresentationResponseMessage.class);
                    return prm;
                } else {
                    // inspect message for http error codes - OkHttpRestClient returns error with body message
                    // We don't have direct status code here, so attempt simple heuristics
                    String msg = resp.getMessage() != null ? resp.getMessage() : resp.getData();
                    if (msg != null && msg.contains("401")) {
                        throw new RemoteHolderAuthException("Unauthorized when calling holder: " + msg);
                    }
                    if (msg != null && (msg.contains("403") || msg.toLowerCase().contains("forbid"))) {
                        throw new RemoteHolderAuthException("Forbidden when calling holder: " + msg);
                    }
                    // treat as client error if 4xx
                    if (msg != null && (msg.contains("400") || msg.contains("404") || msg.contains("422"))) {
                        throw new RemoteHolderClientException("Client error from holder: " + msg);
                    }
                    // otherwise consider retryable
                    if (attempt >= maxRetries) {
                        throw new RuntimeException("Failed to fetch presentations after retries: " + msg);
                    }
                    log.warn("Attempt {} failed, retrying in {}ms: {}", attempt, backoffMs, msg);
                    Thread.sleep(backoffMs);
                    backoffMs *= 2;
                }
            } catch (RemoteHolderAuthException | RemoteHolderClientException e) {
                throw e;
            } catch (Exception e) {
                if (attempt >= maxRetries) {
                    throw new RuntimeException("Failed to fetch presentations from holder: " + e.getMessage(), e);
                }
                log.warn("Exception while calling holder (attempt {}): {}", attempt, e.getMessage());
                try { Thread.sleep(backoffMs); } catch (InterruptedException ignored) {}
                backoffMs *= 2;
            }
        }
    }
}
