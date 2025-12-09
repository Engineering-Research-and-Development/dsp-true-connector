package it.eng.dcp.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * HTTP-backed DID resolver for did:web documents. It fetches the DID document and returns the JWK matching the kid.
 * It enforces that the matching verificationMethod is referenced in the requested verification relationship array
 * (e.g., "capabilityInvocation").
 *
 * Enhancements: simple in-memory caching with TTL and retry logic.
 * Uses OkHttpRestClient for HTTP requests with SSL/TLS support.
 */
@Service
@Primary
@Slf4j
public class HttpDidResolverService implements DidResolverService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpRestClient httpClient;

    // Simple cache: map URL -> cached JSON and expiry
    private static class CachedDoc {
        final JsonNode root;
        final Instant expiresAt;

        CachedDoc(JsonNode root, Instant expiresAt) {
            this.root = root;
            this.expiresAt = expiresAt;
        }
    }

    private final ConcurrentMap<String, CachedDoc> cache = new ConcurrentHashMap<>();

    // Expose setters for tests or runtime wiring
    // Configurable parameters (defaults)
    @Setter
    private long cacheTtlSeconds = 300; // 5 minutes
    @Setter
    private int maxRetries = 2;

    /**
     * Constructor with OkHttpRestClient injection.
     * @param httpClient The OkHttpRestClient to use for HTTP requests
     */
    public HttpDidResolverService(OkHttpRestClient httpClient) {
        log.info("HttpDidResolverService initialized with OkHttpRestClient");
        this.httpClient = httpClient;
    }


    @Override
    public JWK resolvePublicKey(String did, String kid, String verificationRelationship) throws DidResolutionException {
        if (did == null || kid == null) return null;
        if (!did.toLowerCase(Locale.ROOT).startsWith("did:web:")) {
            return null; // unsupported in this implementation
        }

        try {
            String path = did.substring("did:web:".length());
            // percent-decode first
            String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);

            // DID Web format: did:web:domain[:port][:path][:segments]
            // After decoding, we need to:
            // 1. Extract domain and optional port (first segment before any path)
            // 2. Convert remaining colons to path separators

            String[] segments = decoded.split(":", -1);
            if (segments.length == 0) {
                return null;
            }

            String hostPort;
            String pathSegments = null;

            if (segments.length == 1) {
                // Just domain: did:web:example.com
                hostPort = segments[0];
            } else if (segments.length == 2) {
                // Could be domain:port OR domain:path
                // Check if second segment is numeric (port) or not (path)
                String secondSegment = segments[1];
                if (secondSegment.matches("\\d+")) {
                    // It's a port: did:web:localhost:8080
                    hostPort = segments[0] + ":" + segments[1];
                } else {
                    // It's a path: did:web:example.com:users
                    hostPort = segments[0];
                    pathSegments = secondSegment;
                }
            } else {
                // 3+ segments: did:web:example.com:8080:path:to:resource
                // OR: did:web:example.com:path:to:resource
                // Check if second segment is numeric (port)
                if (segments[1].matches("\\d+")) {
                    // Has port: domain:port:path:segments
                    hostPort = segments[0] + ":" + segments[1];
                    // Join remaining segments with /
                    pathSegments = String.join("/", java.util.Arrays.copyOfRange(segments, 2, segments.length));
                } else {
                    // No port: domain:path:segments
                    hostPort = segments[0];
                    // Join remaining segments with /
                    pathSegments = String.join("/", java.util.Arrays.copyOfRange(segments, 1, segments.length));
                }
            }

            // Build the URL
            String url;
            if (pathSegments == null || pathSegments.isEmpty()) {
                // No path segments: https://host[:port]/.well-known/did.json
                url = "https://" + hostPort + "/.well-known/did.json";
            } else {
                // Has path segments: https://host[:port]/path/segments/did.json
                url = "https://" + hostPort + "/" + pathSegments + "/did.json";
            }

            JsonNode root = null;
            // check cache
            CachedDoc cd = cache.get(url);
            Instant now = Instant.now();
            if (cd != null && cd.expiresAt.isAfter(now)) {
                root = cd.root;
            } else {
                String doc = fetchDidDocument(url);
                if (doc == null) return null;
                root = mapper.readTree(doc);
                cache.put(url, new CachedDoc(root, now.plusSeconds(cacheTtlSeconds)));
            }

            JsonNode vmArray = root.get("verificationMethod");
            if (vmArray == null || !vmArray.isArray()) return null;

            // iterate verificationMethod entries
            Iterator<JsonNode> it = vmArray.elements();
            while (it.hasNext()) {
                JsonNode vm = it.next();
                JsonNode idNode = vm.get("id");
                JsonNode jwkNode = vm.get("publicKeyJwk");
                String vmId = idNode != null ? idNode.asText() : null;
                if (jwkNode == null || vmId == null) continue;

                // try parse jwk
                JWK jwk;
                try {
                    jwk = JWK.parse(jwkNode.toString());
                } catch (ParseException pe) {
                    throw new DidResolutionException("Failed to parse JWK", pe);
                }
                // determine if kid matches: JWK.kid or vmId contains kid fragment
                String jwkKid = jwk.getKeyID();
                boolean match = false;
                if (kid.equals(jwkKid)) match = true;
                if (!match) {
                    // compare full vm id ending
                    if (vmId.endsWith('#' + kid) || vmId.equals(kid)) match = true;
                }

                if (match) {
                    // enforce verificationRelationship if provided
                    if (verificationRelationship == null || verificationRelationship.isBlank()) {
                        return jwk;
                    }

                    JsonNode relArray = root.get(verificationRelationship);
                    if (relArray == null) {
                        throw new DidResolutionException("Verification relationship '" + verificationRelationship + "' not present in DID document");
                    }

                    boolean found = false;
                    for (JsonNode rel : relArray) {
                        if (rel.isTextual()) {
                            if (rel.asText().equals(vmId) || rel.asText().endsWith('#' + kid) || rel.asText().equals(kid)) {
                                found = true;
                                break;
                            }
                        } else if (rel.isObject()) {
                            JsonNode rid = rel.get("id");
                            if (rid != null && (rid.asText().equals(vmId) || rid.asText().endsWith('#' + kid) || rid.asText().equals(kid))) {
                                found = true;
                                break;
                            }
                        }
                    }

                    if (!found) {
                        throw new DidResolutionException("Key found but not referenced in verification relationship '" + verificationRelationship + "'");
                    }

                    return jwk;
                }
            }

            return null;
        } catch (IOException e) {
            throw new DidResolutionException("Failed to fetch or parse DID document", e);
        }
    }

    private String fetchDidDocumentWithRetries(String url) throws IOException {
        int attempts = 0;
        IOException lastIoEx = null;
        while (attempts <= maxRetries) {
            attempts++;
            try {
                return fetchDidDocumentWithTimeout(url);
            } catch (IOException e) {
                lastIoEx = e;
                // simple backoff
                try {
                    Thread.sleep(100L * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException(ie);
                }
            }
        }
        if (lastIoEx != null) throw lastIoEx;
        return null;
    }

    private String fetchDidDocumentWithTimeout(String url) throws IOException {
        GenericApiResponse<String> response = httpClient.sendGETRequest(url, null);

        if (response == null) {
            throw new IOException("No response received from " + url);
        }

        if (response.isSuccess()) {
            return response.getData();
        }

        // Extract error message
        String errorMsg = response.getMessage() != null ? response.getMessage() : "Unknown error";
        throw new IOException("Failed to fetch DID document from " + url + ": " + errorMsg);
    }

    /**
     * Fetch the DID document over HTTP. Overridable for tests.
     * @param url The DID document URL
     * @return The document content, or null if not found
     * @throws IOException on IO errors
     */
    protected String fetchDidDocument(String url) throws IOException {
        // kept for backward compatibility; delegates to fetch with timeout and retries
        return fetchDidDocumentWithRetries(url);
    }
}
