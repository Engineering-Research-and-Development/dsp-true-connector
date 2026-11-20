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
import java.util.Optional;
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
    private record CachedDoc(DidDocument didDocument, Instant fetchedAt, Instant expiresAt) { }

    private final ConcurrentMap<String, HttpDidResolverService.CachedDoc> cache = new ConcurrentHashMap<>();

    /**
     * Cache TTL in seconds. Default: 300 seconds (5 minutes).
     * Can be modified for testing or specific requirements.
     */
    @Setter
    private long cacheTtlSeconds = 300;

    /**
     * Minimum age in seconds of a cached DID document before an unknown {@code kid} triggers a refetch.
     * Default: 30 seconds.
     */
    @Setter
    private long minRefreshIntervalSeconds = 30;

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
     * <p>Key selection follows DCP §4.3.3: with a {@code kid}, the verification method whose ID
     * (absolute DID URL, {@code #fragment} or bare fragment) or JWK {@code kid} matches it; without a
     * {@code kid}, the single verification method of the document, and no key when the document has
     * several. When {@code verificationRelationship} is given, the selected method must also be referenced
     * by that relationship. Every case without a suitable key returns {@code null}, so callers reject the
     * token.
     *
     * <p>When a {@code kid} matches no key of a cached document, the document is fetched again once, so a
     * participant that rotated its key is accepted without waiting for the cache to expire. A document is
     * refetched at most once per {@link #minRefreshIntervalSeconds}, so unknown {@code kid}s cannot force
     * constant fetches.
     */
    @Override
    public JWK resolvePublicKey(String did, String kid, String verificationRelationship) throws DidResolutionException {
        return resolveVerificationMethod(did, kid, verificationRelationship)
                .map(ResolvedVerificationMethod::publicKey)
                .orElse(null);
    }

    /**
     * Resolves the verification method a token references, with its absolute ID and public key.
     *
     * <p>Selects the key exactly as {@link #resolvePublicKey(String, String, String)} does, and also returns
     * the absolute DID URL of the selected verification method. Callers use its DID part to check who
     * controls the key, for example that a Verifiable Credential was signed with a key of its issuer
     * (DCP §5.4.3).
     *
     * @param did the DID to resolve (for example {@code did:web:example.com})
     * @param kid the {@code kid} header of the token, or null if the token has none
     * @param verificationRelationship the relationship the key must be referenced by, or null for any
     * @return the selected verification method, or empty if no key satisfies the selection rules
     * @throws DidResolutionException if the DID document cannot be fetched or the selected JWK cannot be parsed
     */
    public Optional<ResolvedVerificationMethod> resolveVerificationMethod(String did, String kid,
            String verificationRelationship) throws DidResolutionException {
        if (did == null) {
            return Optional.empty();
        }

        if (!did.toLowerCase(Locale.ROOT).startsWith("did:web:")) {
            return Optional.empty(); // unsupported DID method
        }

        try {
            Optional<ResolvedVerificationMethod> method =
                    selectKey(fetchDidDocumentCached(did), kid, verificationRelationship);
            if (method.isEmpty() && kid != null && !kid.isBlank() && evictIfRefreshAllowed(did)) {
                log.debug("kid {} not found in cached DID document of {}; fetching it again", kid, did);
                method = selectKey(fetchDidDocumentCached(did), kid, verificationRelationship);
            }
            return method;
        } catch (IOException e) {
            throw new DidResolutionException("Failed to fetch or parse DID document", e);
        }
    }

    /**
     * Selects the key for a token from a DID document per DCP §4.3.3.
     *
     * @param didDocument the resolved DID document, may be null
     * @param kid the {@code kid} header of the token, or null
     * @param verificationRelationship the relationship the key must be referenced by, or null for any
     * @return the selected verification method with its absolute ID, or empty if none qualifies
     * @throws DidResolutionException if the selected JWK cannot be parsed
     */
    private Optional<ResolvedVerificationMethod> selectKey(DidDocument didDocument, String kid, String verificationRelationship)
            throws DidResolutionException {
        if (didDocument == null || didDocument.getVerificationMethods().isEmpty()) {
            return Optional.empty();
        }
        String docId = didDocument.getId();
        List<VerificationMethod> methods = didDocument.getVerificationMethods().stream()
                .filter(HttpDidResolverService::hasJwk)
                .toList();
        boolean relationshipRequired = verificationRelationship != null && !verificationRelationship.isBlank();

        List<VerificationMethod> candidates;
        if (kid == null || kid.isBlank()) {
            if (didDocument.getVerificationMethods().size() != 1) {
                log.debug("Token without kid, but DID document {} has {} verification methods",
                        docId, didDocument.getVerificationMethods().size());
                return Optional.empty();
            }
            candidates = methods;
        } else {
            String absoluteKid = absolutize(kid, docId);
            candidates = methods.stream()
                    .filter(vm -> absolutize(vm.getId(), docId).equals(absoluteKid))
                    .toList();
            if (candidates.isEmpty()) {
                candidates = methods.stream()
                        .filter(vm -> parseQuietly(vm).map(jwk -> kid.equals(jwk.getKeyID())).orElse(false))
                        .toList();
            }
        }

        Optional<VerificationMethod> selected = candidates.stream()
                .filter(vm -> !relationshipRequired
                        || isReferencedByRelationship(didDocument, vm.getId(), verificationRelationship))
                .findFirst();
        if (selected.isEmpty()) {
            log.debug("No verification method of {} matches kid {} with relationship {}",
                    docId, kid, verificationRelationship);
            return Optional.empty();
        }
        try {
            return Optional.of(new ResolvedVerificationMethod(
                    absolutize(selected.get().getId(), docId), JWK.parse(selected.get().getPublicKeyJwk())));
        } catch (ParseException pe) {
            throw new DidResolutionException("Failed to parse JWK", pe);
        }
    }

    private static boolean hasJwk(VerificationMethod vm) {
        return vm.getId() != null && vm.getPublicKeyJwk() != null && !vm.getPublicKeyJwk().isEmpty();
    }

    private static Optional<JWK> parseQuietly(VerificationMethod vm) {
        try {
            return Optional.of(JWK.parse(vm.getPublicKeyJwk()));
        } catch (ParseException pe) {
            log.debug("Skipping verification method {} with an unparsable JWK", vm.getId());
            return Optional.empty();
        }
    }

    /**
     * Turns a verification method ID, {@code kid} or relationship reference into an absolute DID URL.
     *
     * <p>{@code #key-1} and the bare fragment {@code key-1} become {@code <docId>#key-1}; absolute DID URLs
     * are returned unchanged.
     *
     * @param ref the ID or reference
     * @param docId the ID of the DID document the reference belongs to
     * @return the absolute DID URL
     */
    private static String absolutize(String ref, String docId) {
        if (ref.startsWith("#")) {
            return docId + ref;
        }
        if (!ref.startsWith("did:")) {
            return docId + "#" + ref;
        }
        return ref;
    }

    /**
     * Drops the cached document of a DID if it was fetched long enough ago to be refetched.
     *
     * @param did the DID whose document did not contain the requested key
     * @return true if the cache entry was dropped and the document should be fetched again
     */
    private boolean evictIfRefreshAllowed(String did) {
        String key = didDocumentUrl(did);
        CachedDoc cd = cache.get(key);
        if (cd == null || cd.fetchedAt().plusSeconds(minRefreshIntervalSeconds).isAfter(Instant.now())) {
            return false;
        }
        return cache.remove(key, cd);
    }

    /**
     * Fetches DID document with caching support.
     * @param did The DID document URL
     * @return The parsed JSON root node
     * @throws IOException on IO errors
     */
    public DidDocument fetchDidDocumentCached(String did) throws IOException {
        String didDocumentUrl = didDocumentUrl(did);

        CachedDoc cd = cache.get(didDocumentUrl);
        Instant now = Instant.now();

        if (cd != null && cd.expiresAt.isAfter(now)) {
            return cd.didDocument;
        }

        DidDocument fetchedDoc = fetchDidDocumentWithRetries(didDocumentUrl);

        // Cache the fetched document
        if (fetchedDoc != null) {
            Instant expiresAt = now.plusSeconds(cacheTtlSeconds);
            cache.put(didDocumentUrl, new CachedDoc(fetchedDoc, now, expiresAt));
        }

        return fetchedDoc;
    }

    /**
     * Builds the URL a DID document is fetched from, which is also its cache key.
     *
     * @param did the DID to resolve
     * @return the DID document URL
     */
    private String didDocumentUrl(String did) {
        String url = DidUrlConverter.convertDidToUrl(did, sslEnabled);
        return DidUrlConverter.extractBaseUrl(url) + "/.well-known/did.json";
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
        String docId = didDocument.getId();
        String absoluteVmId = absolutize(vmId, docId);
        return relationshipRefs.stream().anyMatch(ref -> absolutize(ref, docId).equals(absoluteVmId));
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
