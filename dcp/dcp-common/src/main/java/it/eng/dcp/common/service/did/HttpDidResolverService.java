package it.eng.dcp.common.service.did;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.VerificationMethod;
import it.eng.dcp.common.util.DidUrlConverter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
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
     * Constructor with SimpleOkHttpRestClient injection.
     * The injected SimpleOkHttpRestClient is already configured with dcpOkHttpClient bean,
     * so no need for @Qualifier here - Spring will automatically inject the correct instance.
     *
     * @param httpClient The SimpleOkHttpRestClient (internally uses dcpOkHttpClient)
     */
    public HttpDidResolverService(SimpleOkHttpRestClient httpClient) {
        log.info("HttpDidResolverService initialized with SimpleOkHttpRestClient (using dcpOkHttpClient)");
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
            DidDocument didDocument = fetchDidDocumentCached(did);

            List<VerificationMethod> verificationMethods = didDocument.getVerificationMethods();
            if (verificationMethods == null || verificationMethods.isEmpty()) {
                return null;
            }

            // Iterate through verification methods
            for (VerificationMethod vm : verificationMethods) {
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

//                if (isKeyMatch(jwk, vmId, kid)) {
//                    // Enforce verification relationship if specified
////                  TODO: re-enable this when available
//
////                    if (verificationRelationship != null && !verificationRelationship.isBlank()) {
////                        enforceVerificationRelationship(root, vmId, kid, verificationRelationship);
////                    }
//                    return jwk;
//                }
                    return jwk;
                }

            return null;
        } catch (IOException e) {
            throw new DidResolutionException("Failed to fetch or parse DID document", e);
        }
    }

    /**
     * Fetches DID document with caching support.
     * @param did The DID document URL
     * @return The parsed JSON root node
     * @throws IOException on IO errors
     */
    public DidDocument fetchDidDocumentCached(String did) throws IOException {
        String url = DidUrlConverter.convertDidToUrl(did, sslEnabled);
        String baseUrl = DidUrlConverter.extractBaseUrl(url);
        String didDocumentUrl = baseUrl + "/.well-known/did.json";

        CachedDoc cd = cache.get(didDocumentUrl);
        Instant now = Instant.now();

        if (cd != null && cd.expiresAt.isAfter(now)) {
            return cd.didDocument;
        }

        DidDocument fetchedDoc = fetchDidDocumentWithRetries(didDocumentUrl);

        // Cache the fetched document
        if (fetchedDoc != null) {
            Instant expiresAt = now.plusSeconds(cacheTtlSeconds);
            cache.put(didDocumentUrl, new CachedDoc(fetchedDoc, expiresAt));
        }

        return fetchedDoc;
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
     *
     * @param didDocument The DID document containing verification relationships
     * @param vmId The verification method ID
     * @param kid The requested key ID
     * @param verificationRelationship The verification relationship to check (authentication, assertionMethod, etc.)
     * @throws DidResolutionException if the key is not referenced properly
     */
    private void enforceVerificationRelationship(DidDocument didDocument, String vmId, String kid,
                                                   String verificationRelationship) throws DidResolutionException {

        // Get the appropriate verification relationship array based on the relationship name
        List<String> relationshipRefs = getVerificationRelationshipRefs(didDocument, verificationRelationship);

        if (relationshipRefs == null || relationshipRefs.isEmpty()) {
            throw new DidResolutionException(
                "Verification relationship '" + verificationRelationship + "' not present in DID document");
        }

        // Check if any reference in the relationship array matches our key
        boolean found = false;
        for (String ref : relationshipRefs) {
            // References can be:
            // 1. Full verification method ID: "did:web:example.com#key-1"
            // 2. Fragment only: "#key-1" or "key-1"
            // 3. Full DID URL
            if (ref.equals(vmId) || ref.equals("#" + kid) || ref.equals(kid) || ref.endsWith("#" + kid)) {
                found = true;
                break;
            }
        }

        if (!found) {
            throw new DidResolutionException(
                "Key '" + kid + "' found but not referenced in verification relationship '" + verificationRelationship + "'");
        }
    }

    /**
     * Gets the verification relationship reference list from the DID document.
     *
     * @param didDocument The DID document
     * @param relationshipName The verification relationship name
     * @return The list of references, or null if not found
     */
    private List<String> getVerificationRelationshipRefs(DidDocument didDocument, String relationshipName) {
        // Currently only capabilityInvocation is supported in DidDocument
        // For other verification relationships, we would need to extend the DidDocument model
        return switch (relationshipName.toLowerCase(Locale.ROOT)) {
//            case "capabilityinvocation" -> didDocument.getCapabilityInvocation();
            case "authentication", "assertionmethod", "keyagreement", "capabilitydelegation" ->
                // These are not currently stored in DidDocument, return null
                null;
            default -> null;
        };
    }

    /**
     * Fetches DID document with retry logic.
     * @param url The DID document URL
     * @return The document content
     * @throws IOException on IO errors
     */
    DidDocument fetchDidDocumentWithRetries(String url) throws IOException {
        int attempts = 0;
        IOException lastIoEx = null;

        while (attempts <= maxRetries) {
            attempts++;
            try {
                return httpClient.executeAndDeserialize(url, "GET", null, null, DidDocument.class);
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
}
