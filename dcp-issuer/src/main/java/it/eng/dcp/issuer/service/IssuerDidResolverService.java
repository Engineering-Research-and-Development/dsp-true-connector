package it.eng.dcp.issuer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.service.did.DidResolverService;
import it.eng.dcp.issuer.client.SimpleOkHttpRestClient;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
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

/**
 * HTTP-backed DID resolver for did:web documents (issuer-specific implementation).
 * This is a simplified version that uses SimpleOkHttpRestClient instead of the full OkHttpRestClient from tools.
 */
@Service
@Primary
@Slf4j
public class IssuerDidResolverService implements DidResolverService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SimpleOkHttpRestClient simpleOkHttpRestClient;

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

    @Setter
    private long cacheTtlSeconds = 300;

    @Setter
    private int maxRetries = 2;

    /**
     * Constructor.
     *
     * @param simpleOkHttpRestClient The SimpleOkHttpRestClient to use
     */
    public IssuerDidResolverService(SimpleOkHttpRestClient simpleOkHttpRestClient) {
        log.info("IssuerDidResolverService initialized with SimpleOkHttpRestClient");
        this.simpleOkHttpRestClient = simpleOkHttpRestClient;
    }

    @Override
    public JWK resolvePublicKey(String did, String kid, String verificationRelationship) throws DidResolutionException {
        if (did == null || kid == null) {
            return null;
        }

        if (!did.toLowerCase(Locale.ROOT).startsWith("did:web:")) {
            return null; // unsupported DID method
        }

        try {
            String url = convertDidToUrl(did);
            JsonNode root = fetchDidDocumentCached(url);

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
        IssuerDidResolverService.CachedDoc cd = cache.get(url);
        Instant now = Instant.now();

        if (cd != null && cd.expiresAt.isAfter(now)) {
            return cd.root;
        }

        String doc = fetchDidDocument(url);
        if (doc == null) {
            return null;
        }

        JsonNode root = mapper.readTree(doc);
        cache.put(url, new IssuerDidResolverService.CachedDoc(root, now.plusSeconds(cacheTtlSeconds)));
        return root;
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

        try (Response response = simpleOkHttpRestClient.executeCall(requestBuilder.build())) {
            int code = response.code();
            log.info("Status {}", code);
            String resp = null;
            if (response.body() != null) {
                resp = response.body().string();
            }
            log.info("Response received: {}", resp);
            if(response.isSuccessful()) { // code in 200..299
                return response.body().string();
            }
        } catch (IOException e) {
            log.error(e.getLocalizedMessage());
            return e.getMessage() != null ? e.getMessage() : "Unknown error";
        }
        throw new IOException("Failed to fetch DID document from " + url);
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

}

