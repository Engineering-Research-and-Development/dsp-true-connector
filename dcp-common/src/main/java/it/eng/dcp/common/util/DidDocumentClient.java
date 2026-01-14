package it.eng.dcp.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.model.DidDocument;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static it.eng.dcp.common.util.DidUrlConverter.convertDidToUrl;
import static it.eng.dcp.common.util.DidUrlConverter.extractBaseUrl;

/**
 * Utility class for fetching the DID Document.
 */

@Service
@Slf4j
public class DidDocumentClient {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient httpClient;
    private final boolean sslEnabled;
    
    public DidDocumentClient(OkHttpClient httpClient, @Value("${server.ssl.enabled:false}") boolean sslEnabled) {
        this.httpClient = httpClient;
        this.sslEnabled = sslEnabled;
    }

    /**
     * Cached DID document entry with expiry time.
     */
    private record CachedDoc(DidDocument didDocument, Instant expiresAt) { }

    private final ConcurrentMap<String, DidDocumentClient.CachedDoc> cache = new ConcurrentHashMap<>();

    /**
     * Cache TTL in seconds. Default: 300 seconds (5 minutes).
     * Can be modified for testing or specific requirements.
     */
    @Setter
    private long cacheTtlSeconds = 300;

    /**
     * Maximum number of retry attempts for failed HTTP requests.
     * Default: 2 retries.
     */
    @Setter
    private int maxRetries = 2;

    /**
     * Fetches DID document with caching support.
     * @param did The DID
     * @return The parsed JSON root node
     * @throws IOException on IO errors
     */
    public DidDocument fetchDidDocumentCached(String did) throws IOException {
        String url = convertDidToUrl(did, sslEnabled);
        String baseUrl = extractBaseUrl(url);
        String didDocumentUrl = baseUrl + "/.well-known/did.json";

        DidDocumentClient.CachedDoc cd = cache.get(didDocumentUrl);
        Instant now = Instant.now();

        if (cd != null && cd.expiresAt.isAfter(now)) {
            return cd.didDocument;
        }

        String doc = fetchDidDocument(didDocumentUrl);
        if (doc == null) {
            return null;
        }

        JsonNode jsonDid = mapper.readTree(doc);
        DidDocument didDocument = mapper.treeToValue(jsonDid, DidDocument.class);
        cache.put(url, new DidDocumentClient.CachedDoc(didDocument, now.plusSeconds(cacheTtlSeconds)));
        return didDocument;
    }

    /**
     * Fetches DID document with retry logic.
     * @param url The DID document URL
     * @return The document content
     * @throws IOException on IO errors
     */
    private String fetchDidDocumentWithRetries(String url) throws IOException {
        int attempts = 0;
        IOException lastIoEx = null;

        while (attempts <= maxRetries) {
            attempts++;
            try {
                return fetchDidDocumentWithTimeout(url);
            } catch (IOException e) {
                lastIoEx = e;
                // Simple exponential backoff
                try {
                    Thread.sleep(100L * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException(ie);
                }
            }
        }

        if (lastIoEx != null) {
            throw lastIoEx;
        }
        return null;
    }

    /**
     * Fetches DID document using OkHttpRestClient.
     * @param url The DID document URL
     * @return The document content
     * @throws IOException on IO errors
     */
    private String fetchDidDocumentWithTimeout(String url) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(url);

        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            int code = response.code();
            log.info("Status {}", code);
            if (response.body() != null) {
                return response.body().string();
            }
        } catch (IOException e) {
            log.error(e.getLocalizedMessage());
            throw new IOException("Failed to fetch DID document from " + url + ": " + e.getLocalizedMessage());
        }
        throw new IOException("Failed to fetch DID document from " + url);
    }

    /**
     * Fetch the DID document over HTTP.
     * Protected method for testing and retry logic.
     *
     * @param url The DID document URL
     * @return The document content, or null if not found
     * @throws IOException on IO errors
     */
    protected String fetchDidDocument(String url) throws IOException {
        return fetchDidDocumentWithRetries(url);
    }
}
