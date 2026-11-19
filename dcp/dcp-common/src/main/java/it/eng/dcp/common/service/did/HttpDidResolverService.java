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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * HTTP-backed DID resolver for did:web documents.
 *
 * <p>This implementation fetches DID documents over HTTPS and extracts JWKs matching
 * the requested key ID. It supports:
 * <ul>
 *   <li>DID Web method (did:web) with proper URL encoding/decoding</li>
 *   <li>Key selection per DCP §4.3.3 and verification relationship enforcement
 *       (authentication, assertionMethod, capabilityInvocation)</li>
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

    /**
     * {@inheritDoc}
     *
     * <p>Key selection follows DCP §4.3.3: with a {@code kid}, the verification method whose ID or JWK
     * {@code kid} matches it; without a {@code kid}, the single verification method of the document, and
     * no key when the document has several. When {@code verificationRelationship} is given, the selected
     * method must also be referenced by that relationship. Every case without a suitable key returns
     * {@code null}, so callers reject the token.
     */
    @Override
    public JWK resolvePublicKey(String did, String kid, String verificationRelationship) throws DidResolutionException {
        if (did == null) {
            return null;
        }

        if (!did.toLowerCase(Locale.ROOT).startsWith("did:web:")) {
            return null; // unsupported DID method
        }

        try {
            DidDocument didDocument = fetchDidDocumentCached(did);
            if (didDocument == null) {
                return null;
            }

            List<VerificationMethod> verificationMethods = didDocument.getVerificationMethods();
            if (verificationMethods == null || verificationMethods.isEmpty()) {
                log.debug("DID document {} has no verification methods", did);
                return null;
            }

            VerificationMethod selected = selectVerificationMethod(verificationMethods, kid, did);
            if (selected == null) {
                return null;
            }

            if (verificationRelationship != null && !verificationRelationship.isBlank()
                    && !isReferencedByRelationship(didDocument, selected.getId(), verificationRelationship)) {
                log.debug("Key {} of {} is not referenced by verification relationship {}",
                        selected.getId(), did, verificationRelationship);
                return null;
            }

            try {
                return JWK.parse(selected.getPublicKeyJwk());
            } catch (ParseException pe) {
                throw new DidResolutionException("Failed to parse JWK", pe);
            }
        } catch (IOException e) {
            throw new DidResolutionException("Failed to fetch or parse DID document", e);
        }
    }

    /**
     * Selects the verification method for a token per DCP §4.3.3.
     *
     * @param verificationMethods the verification methods of the DID document, not empty
     * @param kid the {@code kid} header of the token, or null
     * @param did the DID being resolved, for logging
     * @return the selected verification method with a JWK, or null if none qualifies
     * @throws DidResolutionException if a candidate JWK cannot be parsed
     */
    private VerificationMethod selectVerificationMethod(List<VerificationMethod> verificationMethods, String kid,
                                                        String did) throws DidResolutionException {
        if (kid == null || kid.isBlank()) {
            if (verificationMethods.size() != 1) {
                log.debug("Token without kid, but DID document {} has {} verification methods",
                        did, verificationMethods.size());
                return null;
            }
            VerificationMethod only = verificationMethods.get(0);
            return hasJwk(only) ? only : null;
        }

        for (VerificationMethod vm : verificationMethods) {
            if (!hasJwk(vm)) {
                continue;
            }
            JWK jwk;
            try {
                jwk = JWK.parse(vm.getPublicKeyJwk());
            } catch (ParseException pe) {
                throw new DidResolutionException("Failed to parse JWK", pe);
            }
            if (isKeyMatch(jwk, vm.getId(), kid)) {
                return vm;
            }
        }
        log.debug("No verification method in DID document {} matches kid {}", did, kid);
        return null;
    }

    private static boolean hasJwk(VerificationMethod vm) {
        return vm.getId() != null && vm.getPublicKeyJwk() != null && !vm.getPublicKeyJwk().isEmpty();
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
        // Direct match with JWK kid
        if (kid.equals(jwk.getKeyID())) {
            return true;
        }
        // Match with the verification method ID (absolute DID URL or fragment)
        return vmId.equals(kid) || vmId.endsWith('#' + kid) || (kid.startsWith("#") && vmId.endsWith(kid));
    }

    /**
     * Checks that a verification method is referenced by the given verification relationship.
     *
     * <p>References may be absolute DID URLs ({@code did:web:example.com#key-1}) or relative to the
     * document ({@code #key-1}).
     *
     * @param didDocument The DID document containing verification relationships
     * @param vmId The ID of the selected verification method
     * @param verificationRelationship The verification relationship name (for example capabilityInvocation)
     * @return true if the relationship references the verification method
     */
    private boolean isReferencedByRelationship(DidDocument didDocument, String vmId, String verificationRelationship) {
        List<String> relationshipRefs = getVerificationRelationshipRefs(didDocument, verificationRelationship);
        if (relationshipRefs == null || relationshipRefs.isEmpty()) {
            return false;
        }
        int hash = vmId.indexOf('#');
        String fragment = hash >= 0 ? vmId.substring(hash) : null;
        for (String ref : relationshipRefs) {
            if (ref.equals(vmId) || (fragment != null && ref.equals(fragment))
                    || (ref.startsWith("#") && didDocument.getId() != null
                        && vmId.equals(didDocument.getId() + ref))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the verification relationship reference list from the DID document.
     *
     * @param didDocument The DID document
     * @param relationshipName The verification relationship name
     * @return The list of references, or null if the relationship is not supported
     */
    private List<String> getVerificationRelationshipRefs(DidDocument didDocument, String relationshipName) {
        return switch (relationshipName.toLowerCase(Locale.ROOT)) {
            case "capabilityinvocation" -> didDocument.getCapabilityInvocation();
            case "authentication" -> didDocument.getAuthentication();
            case "assertionmethod" -> didDocument.getAssertionMethod();
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
