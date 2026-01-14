package it.eng.dcp.issuer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.VerificationMethod;
import it.eng.dcp.common.service.did.DidResolverService;
import it.eng.dcp.common.util.DidDocumentClient;
import it.eng.dcp.issuer.client.SimpleOkHttpRestClient;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
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
 * HTTP-backed DID resolver for did:web documents (issuer-specific implementation).
 * This is a simplified version that uses SimpleOkHttpRestClient instead of the full OkHttpRestClient from tools.
 */
@Service
@Primary
@Slf4j
public class IssuerDidResolverService implements DidResolverService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SimpleOkHttpRestClient simpleOkHttpRestClient;
    private final boolean sslEnabled;
    private final DidDocumentClient didDocumentClient;

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
     * @param sslEnabled              SSL enabled flag from application properties
     * @param didDocumentClient      The DidDocumentClient for fetching DID documents
     */
    public IssuerDidResolverService(SimpleOkHttpRestClient simpleOkHttpRestClient, @Value("${server.ssl.enabled:false}") boolean sslEnabled, DidDocumentClient didDocumentClient) {
        log.info("IssuerDidResolverService initialized with SimpleOkHttpRestClient, sslEnabled={} and DidDocumentClient", sslEnabled);
        this.simpleOkHttpRestClient = simpleOkHttpRestClient;
        this.sslEnabled = sslEnabled;
        this.didDocumentClient = didDocumentClient;
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
//            String url = convertDidToUrl(did, sslEnabled);
//            url = url + "/.well-known/did.json";
//
//            JsonNode root = fetchDidDocumentCached(url);

            DidDocument didDocument = didDocumentClient.fetchDidDocumentCached(did);

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
                // Check if this key matches the requested kid
                if (isKeyMatch(jwk, vmId, kid)) {
                    // Enforce verification relationship if specified
//                    TODO: Re-enable verification relationship check when available
//                    if (verificationRelationship != null && !verificationRelationship.isBlank()) {
//                        enforceVerificationRelationship(didDocument, vmId, kid, verificationRelationship);
//                    }
                    return jwk;
                }
            }

            return null;
        } catch (IOException e) {
            throw new DidResolutionException("Failed to fetch or parse DID document", e);
        }
    }

    // Change from private to package-private for testability
    boolean isKeyMatch(JWK jwk, String vmId, String kid) {
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

    // Change from private to package-private for testability
    void enforceVerificationRelationship(JsonNode root, String vmId, String kid,
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
