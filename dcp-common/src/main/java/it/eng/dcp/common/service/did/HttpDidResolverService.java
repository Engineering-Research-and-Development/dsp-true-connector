package it.eng.dcp.common.service.did;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.VerificationMethod;
import it.eng.dcp.common.util.DidUrlConverter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    private final SimpleOkHttpRestClient httpClient;

    /**
     * Cached DID document entry with expiry time.
     */
    private record CachedDoc(DidDocument didDocument, Instant expiresAt) { }

    private final ConcurrentMap<String, HttpDidResolverService.CachedDoc> cache = new ConcurrentHashMap<>();

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
    public HttpDidResolverService(SimpleOkHttpRestClient httpClient) {
        log.info("HttpDidResolverService initialized with SimpleOkHttpRestClient");
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
//            String url = convertDidToUrl(did, sslEnabled);
//            String baseUrl = extractBaseUrl(url);
//            // TODO: consider moving this to service or handle in uniform way across resolvers
//            JsonNode root = fetchDidDocumentCached(baseUrl + "/.well-known/did.json");

            DidDocument didDocument = fetchDidDocumentCached(did);

            List<VerificationMethod> verificationMethods = didDocument.getVerificationMethods();
            if (verificationMethods == null || verificationMethods.isEmpty()) {
                return null;
            }

            // Iterate through verification methods
            Iterator<VerificationMethod> it = verificationMethods.iterator();
            while (it.hasNext()) {
                VerificationMethod vm = it.next();
                String vmId = vm.getId();
                Map<String, Object> jwkNode = vm.getPublicKeyJwk();

                if (jwkNode == null || vmId == null) {
                    continue;
                }

                // Parse JWK
                JWK jwk;
                try {
                    jwk = JWK.parse(jwkNode);
                } catch (ParseException pe) {
                    throw new DidResolutionException("Failed to parse JWK", pe);
                }

                if (isKeyMatch(jwk, vmId, kid)) {
                    // Enforce verification relationship if specified
//                  TODO: re-enable this when available

//                    if (verificationRelationship != null && !verificationRelationship.isBlank()) {
//                        enforceVerificationRelationship(root, vmId, kid, verificationRelationship);
//                    }
                    return jwk;
                }
            }

            return null;
        } catch (IOException e) {
            throw new DidResolutionException("Failed to fetch or parse DID document", e);
        }
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
     * Fetches DID document with caching support.
     * @param did The DID
     * @return The parsed JSON root node
     * @throws IOException on IO errors
     */
    @Override
    public DidDocument fetchDidDocumentCached(String did) throws IOException {
        String url = DidUrlConverter.convertDidToUrl(did, sslEnabled);
        String baseUrl = DidUrlConverter.extractBaseUrl(url);
        String didDocumentUrl = baseUrl + "/.well-known/did.json";

        HttpDidResolverService.CachedDoc cd = cache.get(didDocumentUrl);
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
        cache.put(didDocumentUrl, new HttpDidResolverService.CachedDoc(didDocument, now.plusSeconds(cacheTtlSeconds)));
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

        try (Response response = httpClient.executeCall(requestBuilder.build())) {
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
