package it.eng.dcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.model.CredentialRequestMessage;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.common.model.ServiceEntry;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.exception.IssuerServiceNotFoundException;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for interacting with external Issuer Services for credential issuance (Phase 3).
 */
@Service
public class CredentialIssuanceClient {

    private static final Logger LOG = LoggerFactory.getLogger(CredentialIssuanceClient.class);

    // replaced RestTemplate with OkHttpRestClient
    private final SimpleOkHttpRestClient rest;
    private final SelfIssuedIdTokenService tokenService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final BaseDidDocumentConfiguration config;

    @Autowired
    public CredentialIssuanceClient(SimpleOkHttpRestClient rest, SelfIssuedIdTokenService tokenService, BaseDidDocumentConfiguration config) {
        this.rest = rest;
        this.tokenService = tokenService;
        this.config = config;
    }

    /**
     * Discover the Issuer Service endpoint for a given issuer identifier (DID or URL) using the issuer's DID document
     * or by falling back to the issuer string when it is an HTTP URL.
     * @param issuerMetadata issuer metadata containing issuer field (DID or URL)
     * @return service endpoint URL (e.g., https://issuer.example.com)
     */
    public String discoverIssuerService(IssuerMetadata issuerMetadata) {
        if (issuerMetadata == null) throw new IllegalArgumentException("issuerMetadata required");
        String issuer = issuerMetadata.getIssuer();
        if (issuer == null || issuer.isBlank()) throw new IllegalArgumentException("issuer missing in metadata");

        // If issuer is a DID (did:web), try to resolve did.json and look for a service entry of type IssuerService
        if (issuer.toLowerCase().startsWith("did:")) {
            String url = buildDidDocumentUrl(issuer);
            try {
                DidDocument didDocument = rest.executeAndDeserialize(url, "GET", null, null, DidDocument.class);
                if (didDocument != null && didDocument.getServices() != null) {
                    for (ServiceEntry service : didDocument.getServices()) {
                        if ("IssuerService".equals(service.type())) {
                            return service.serviceEndpoint();
                        }
                    }
                }
            } catch (IOException e) {
                LOG.debug("Failed to fetch or parse DID document {}: {}", url, e.getMessage());
            }
            throw new IssuerServiceNotFoundException("IssuerService entry not found in DID document for issuer: " + issuer);
        }

        // Fallback: if issuer is an HTTP(S) URL, return it as service base
        if (issuer.startsWith("http://") || issuer.startsWith("https://")) {
            return issuer;
        }

        throw new IssuerServiceNotFoundException("Cannot discover issuer service for issuer: " + issuer);
    }

    private String buildDidDocumentUrl(String did) {
        // Map did:web:<host> or did:web:<host>:path... to https://<host>/.well-known/did.json or https://<host>/<path>/did.json
        String path = did.substring("did:web:".length());
        String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
        String mapped = decoded.replace(':', '/');
        int idx = mapped.indexOf('/');
        if (idx < 0) {
            return "https://" + mapped + "/.well-known/did.json";
        } else {
            String host = mapped.substring(0, idx);
            String rest = mapped.substring(idx + 1);
            return "https://" + host + "/" + rest + "/did.json";
        }
    }

    /**
     * Send a credential request to the issuer's /credentials endpoint. Returns Location header (status URL) on 201.
     * @param issuerMetadata Metadata of the issuer
     * @param credentialObjectId The object ID of the credential to request
     * @param holderPid The holder's persistent identifier
     * @return The status URL from Location header
     */
    public String requestCredential(IssuerMetadata issuerMetadata, String credentialObjectId, String holderPid) {
        if (issuerMetadata == null || credentialObjectId == null || holderPid == null) {
            throw new IllegalArgumentException("issuerMetadata, credentialObjectId and holderPid are required");
        }

        // Discover issuer service URL (may resolve DID to service endpoint)
        String issuerBase = discoverIssuerService(issuerMetadata);
        String url = issuerBase.endsWith("/") ? issuerBase + "credentials" : issuerBase + "/credentials";

        // Build request body: CredentialRequestMessage wrapper with requestId
        String requestId = java.util.UUID.randomUUID().toString();
        CredentialRequestMessage.CredentialReference ref = CredentialRequestMessage.CredentialReference.Builder.newInstance().id(credentialObjectId).build();
        CredentialRequestMessage req = CredentialRequestMessage.Builder.newInstance()
                .requestId(requestId)
                .holderPid(holderPid)
                .credentials(List.of(ref))
                .build();

        // Create token for issuer audience if possible
        // Use the issuer DID or base as audience if available in IssuerMetadata; tokenService may return null if not configured
        String audience = issuerMetadata.getIssuer();
        String token = null;
        try {
            token = tokenService.createAndSignToken(audience, null, config.getDidDocumentConfig());
        } catch (Exception e) {
            LOG.debug("No token could be created for issuer audience {}: {}", audience, e.getMessage());
        }

        try {
            String json = mapper.writeValueAsString(req);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            Request.Builder rb = new Request.Builder().url(url).post(body);
            if (token != null && !token.isBlank()) {
                rb.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            Request request = rb.build();
            try (Response response = rest.executeCall(request)) {
                if (response == null) {
                    throw new RuntimeException("No response from issuer at " + url);
                }
                int code = response.code();
                if ((code == HttpStatus.CREATED.value()) || (code == HttpStatus.ACCEPTED.value()) || (code >= 200 && code < 300)) {
                    String location = response.header(HttpHeaders.LOCATION);
                    if (location != null && !location.isBlank()) return location;
                    return url;
                }
                String respBody = null;
                try {
                    if (response.body() != null) respBody = response.body().string();
                } catch (Exception ex) {
                    LOG.debug("Failed to read response body: {}", ex.getMessage());
                }
                throw new RuntimeException("Unexpected response from issuer: " + code + " - " + respBody);
            }
        } catch (RuntimeException e) {
            LOG.error("Issuer returned error while requesting credential: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            LOG.error("Error while requesting credential: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Fetch issuer metadata from given URL (issuer base + /metadata or provided URL).
     * @param metadataUrl The metadata URL
     * @return The IssuerMetadata object
     */
    public IssuerMetadata getIssuerMetadata(String metadataUrl) {
        if (metadataUrl == null) throw new IllegalArgumentException("metadataUrl required");

        Map<String, String> headers = new HashMap<>();
        //TODO add SelfIssuedIdTokenService token generation here
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer geefghsdfgsd");

        try {
            return rest.executeAndDeserialize(metadataUrl, "GET", headers, null, IssuerMetadata.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch issuer metadata from " + metadataUrl + ": " + e.getMessage(), e);
        }
    }

    /**
     * Perform authenticated GET to status URL to retrieve current status (expect JSON).
     * @param statusUrl The status URL
     * @param audience The audience for the token
     * @return The ResponseEntity with status JSON body
     */
    public ResponseEntity<String> getRequestStatus(String statusUrl, String audience) {
        if (statusUrl == null) throw new IllegalArgumentException("statusUrl required");
        String token = tokenService.createAndSignToken(audience, null, config.getDidDocumentConfig());
        Request.Builder rb = new Request.Builder().url(statusUrl).get();
        if (token != null && !token.isBlank()) {
            rb.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        Request request = rb.build();
        try (Response response = rest.executeCall(request)) {
            if (response == null) {
                throw new RuntimeException("No response from issuer at " + statusUrl);
            }
            try {
                int code = response.code();
                String body = null;
                if (response.body() != null) body = response.body().string();
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                for (String name : response.headers().names()) {
                    headers.put(name, response.headers(name));
                }
                HttpStatus status = HttpStatus.resolve(code);
                if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
                return new ResponseEntity<>(body, headers, status);
            } catch (Exception e) {
                throw new RuntimeException("Failed to read status response: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Poll a status URL until a terminal response or timeout. Returns final response body.
     * @param statusUrl The status URL to poll
     * @param audience The audience for the token
     * @param timeout Maximum duration to wait
     * @param backoff Initial backoff duration between attempts
     * @return The final status response body
     * @throws InterruptedException if interrupted while waiting
     */
    public String awaitStatus(String statusUrl, String audience, Duration timeout, Duration backoff) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        long delay = backoff.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                ResponseEntity<String> resp = getRequestStatus(statusUrl, audience);
                if (resp.getStatusCode().is2xxSuccessful()) {
                    // assume body contains status JSON; caller will parse
                    return resp.getBody();
                } else if (resp.getStatusCode().is4xxClientError()) {
                    throw new RuntimeException("Client error fetching status: " + resp.getStatusCode());
                }
            } catch (Exception e) {
                LOG.warn("Status fetch attempt failed: {}", e.getMessage());
            }
            Thread.sleep(delay);
            delay = Math.min(delay * 2, 5000);
        }
        throw new RuntimeException("Status polling timed out");
    }
}
