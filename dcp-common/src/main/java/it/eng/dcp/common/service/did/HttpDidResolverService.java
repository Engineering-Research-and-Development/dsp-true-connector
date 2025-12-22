package it.eng.dcp.common.service.did;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static it.eng.dcp.common.util.DidUrlConverter.convertDidToUrl;
import static it.eng.dcp.common.util.DidUrlConverter.extractBaseUrl;

/**
 * HTTP-backed DID resolver for did:web documents.
 *
 * <p>This implementation fetches DID documents over HTTPS and extracts JWKs matching
 * the requested key ID. It supports:
 * <ul>
 *   <li>DID Web method (did:web) with proper URL encoding/decoding</li>
 *   <li>Verification relationship enforcement (authentication, assertionMethod, etc.)</li>
 *   <li>In-memory caching with configurable TTL</li>
 *   <li>Automatic retry logic with exponential backoff</li>
 *   <li>SSL/TLS support via OkHttpRestClient</li>
 * </ul>
 *
 * <p>The resolver follows the W3C DID specification for did:web method:
 * <ul>
 *   <li>did:web:example.com → https://example.com/.well-known/did.json</li>
 *   <li>did:web:example.com:user:alice → https://example.com/user/alice/did.json</li>
 *   <li>did:web:localhost:8080 → https://localhost:8080/.well-known/did.json</li>
 * </ul>
 *
 * <p>Marked as @Primary to be the default implementation in production.
 */
@Service
@Primary
@Slf4j
public class HttpDidResolverService implements DidResolverService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpRestClient httpClient;

    /**
     * Cached DID document entry with expiry time.
     */
    private static class CachedDoc {
        final JsonNode root;
        final Instant expiresAt;

        CachedDoc(JsonNode root, Instant expiresAt) {
            this.root = root;
            this.expiresAt = expiresAt;
        }
    }

    private final ConcurrentMap<String, CachedDoc> cache = new ConcurrentHashMap<>();

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
     * Constructor with OkHttpRestClient injection.
     *
     * @param httpClient The OkHttpRestClient to use for HTTP requests
     */
    public HttpDidResolverService(OkHttpRestClient httpClient) {
        log.info("HttpDidResolverService initialized with OkHttpRestClient");
        this.httpClient = httpClient;
    }

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @Override
    public JWK resolvePublicKey(String did, String kid, String verificationRelationship) throws DidResolutionException {
        if (did == null || kid == null) {
            return null;
        }

        if (!did.toLowerCase(Locale.ROOT).startsWith("did:web:")) {
            return null; // unsupported DID method
        }

        try {
            String url = convertDidToUrl(did, sslEnabled);
            String baseUrl = extractBaseUrl(url);
            // TODO: consider moving this to service or handle in uniform way across resolvers
            JsonNode root = fetchDidDocumentCached(baseUrl + "/.well-known/did.json");

            JsonNode vmArray = root.get("verificationMethod");
            if (vmArray == null || !vmArray.isArray()) {
                return null;
            }

            // Iterate through verification methods
            Iterator<JsonNode> it = vmArray.elements();
            while (it.hasNext()) {
                JsonNode vm = it.next();
                JsonNode idNode = vm.get("id");
                JsonNode jwkNode = vm.get("publicKeyJwk");
                String vmId = idNode != null ? idNode.asText() : null;

                if (jwkNode == null || vmId == null) {
                    continue;
                }

                // Parse JWK
                JWK jwk;
                try {
                    jwk = JWK.parse(jwkNode.toString());
                } catch (ParseException pe) {
                    throw new DidResolutionException("Failed to parse JWK", pe);
                }

                // Check if this key matches the requested kid
                if (isKeyMatch(jwk, vmId, kid)) {
                    // Enforce verification relationship if specified
                    if (verificationRelationship != null && !verificationRelationship.isBlank()) {
                        enforceVerificationRelationship(root, vmId, kid, verificationRelationship);
                    }
                    return jwk;
                }
            }

            return null;
        } catch (IOException e) {
            throw new DidResolutionException("Failed to fetch or parse DID document", e);
        }
    }

    /**
     * Fetches DID document with caching support.
     * @param url The DID document URL
     * @return The parsed JSON root node
     * @throws IOException on IO errors
     */
    private JsonNode fetchDidDocumentCached(String url) throws IOException {
        CachedDoc cd = cache.get(url);
        Instant now = Instant.now();

        if (cd != null && cd.expiresAt.isAfter(now)) {
            return cd.root;
        }

        String doc = fetchDidDocument(url);
        if (doc == null) {
            return null;
        }

        JsonNode root = mapper.readTree(doc);
        cache.put(url, new CachedDoc(root, now.plusSeconds(cacheTtlSeconds)));
        return root;
    }

    /**
     * Checks if a JWK matches the requested key ID.
     * @param jwk The JWK to check
     * @param vmId The verification method ID
     * @param kid The requested key ID
     * @return true if matches, false otherwise
     */
    private boolean isKeyMatch(JWK jwk, String vmId, String kid) {
        String jwkKid = jwk.getKeyID();

        // Direct match with JWK kid
        if (kid.equals(jwkKid)) {
            return true;
        }

        // Match with verification method ID
        if (vmId.endsWith('#' + kid) || vmId.equals(kid)) {
            return true;
        }

        return false;
    }

    /**
     * Enforces that the key is referenced in the specified verification relationship.
     * @param root The DID document root node
     * @param vmId The verification method ID
     * @param kid The requested key ID
     * @param verificationRelationship The verification relationship to check
     * @throws DidResolutionException if the key is not referenced properly
     */
    private void enforceVerificationRelationship(JsonNode root, String vmId, String kid,
                                                   String verificationRelationship) throws DidResolutionException {
        JsonNode relArray = root.get(verificationRelationship);
        if (relArray == null) {
            throw new DidResolutionException(
                "Verification relationship '" + verificationRelationship + "' not present in DID document");
        }

        boolean found = false;
        for (JsonNode rel : relArray) {
            if (rel.isTextual()) {
                String relText = rel.asText();
                if (relText.equals(vmId) || relText.endsWith('#' + kid) || relText.equals(kid)) {
                    found = true;
                    break;
                }
            } else if (rel.isObject()) {
                JsonNode rid = rel.get("id");
                if (rid != null) {
                    String ridText = rid.asText();
                    if (ridText.equals(vmId) || ridText.endsWith('#' + kid) || ridText.equals(kid)) {
                        found = true;
                        break;
                    }
                }
            }
        }

        if (!found) {
            throw new DidResolutionException(
                "Key found but not referenced in verification relationship '" + verificationRelationship + "'");
        }
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
        GenericApiResponse<String> response = httpClient.sendGETRequest(url, null);

        if (response == null) {
            throw new IOException("No response received from " + url);
        }

        if (response.isSuccess()) {
            return response.getData();
        }

        String errorMsg = response.getMessage() != null ? response.getMessage() : "Unknown error";
        throw new IOException("Failed to fetch DID document from " + url + ": " + errorMsg);
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
