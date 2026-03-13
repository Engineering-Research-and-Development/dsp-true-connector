package it.eng.dcp.holder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.audit.DcpAuditEventType;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.*;
import it.eng.dcp.common.service.audit.DcpAuditEventPublisher;
import it.eng.dcp.common.service.did.DidResolverService;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.holder.exception.IssuerServiceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client for interacting with external Issuer Services for credential issuance (Phase 3).
 */
@Service
@Slf4j
public class CredentialIssuanceClient {

    private static final String HOLDER_SOURCE = "holder";

    // replaced RestTemplate with OkHttpRestClient
    private final SimpleOkHttpRestClient rest;
    private final SelfIssuedIdTokenService tokenService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final DidDocumentConfig config;
    private final DidResolverService didResolverService;
    private final DcpAuditEventPublisher auditPublisher;

    @Value("${dcp.issuer.location:}")
    private String issuerDid;

    @Autowired
    public CredentialIssuanceClient(SimpleOkHttpRestClient rest,
                                    @Qualifier("selfIssuedIdTokenService") SelfIssuedIdTokenService tokenService,
                                    DidDocumentConfig config,
                                    DidResolverService didResolverService,
                                    DcpAuditEventPublisher auditPublisher) {
        this.rest = rest;
        this.tokenService = tokenService;
        this.config = config;
        this.didResolverService = didResolverService;
        this.auditPublisher = auditPublisher;
    }

    /**
     * Discovers the Issuer Service endpoint from the issuer's DID document.
     *
     * @return service endpoint URL (e.g., {@code https://issuer.example.com})
     * @throws IssuerServiceNotFoundException if IssuerService entry not found in DID document
     * @throws RuntimeException if failed to fetch DID document
     */
    public String discoverIssuerService() {
        DidDocument didDocument;
        try {
            didDocument = didResolverService.fetchDidDocumentCached(issuerDid);
        } catch (IOException e) {
            log.error("Failed to fetch issuer DID document from {}: {}", issuerDid, e.getMessage());
            throw new RuntimeException("Failed to fetch issuer DID document: " + e.getMessage(), e);
        }

        if (didDocument != null && didDocument.getServices() != null) {
            return didDocument.getServices().stream()
                    .filter(service -> "IssuerService".equals(service.type()))
                    .findFirst()
                    .map(ServiceEntry::serviceEndpoint)
                    .orElseThrow(() -> new IssuerServiceNotFoundException("IssuerService entry not found in DID document for issuer: " + issuerDid));
        }

        throw new IssuerServiceNotFoundException("IssuerService entry not found in DID document for issuer: " + issuerDid);
    }

    /**
     * Fetches issuer metadata from the issuer service base URL + /metadata.
     *
     * @return the IssuerMetadata object
     * @throws RuntimeException if failed to fetch metadata
     */
    public IssuerMetadata getPersonalIssuerMetadata() {
        // Discover issuer service URL from DID document
        String issuerBase = discoverIssuerService();
        String metadataUrl = issuerBase.endsWith("/") ? issuerBase + DCPConstants.ISSUER_METADATA_PATH.substring(1) : issuerBase + DCPConstants.ISSUER_METADATA_PATH;
        IssuerMetadata metadata = getIssuerMetadata(metadataUrl);
        auditPublisher.publishEvent(
                DcpAuditEventType.ISSUER_METADATA_FETCHED,
                "Issuer metadata fetched from: " + metadataUrl,
                HOLDER_SOURCE,
                null, issuerDid, null, null,
                Map.of("issuerDid", issuerDid, "metadataUrl", metadataUrl));
        return metadata;
    }

    /**
     * Fetches issuer metadata from the given URL (issuer base + /metadata or provided URL).
     *
     * @param metadataUrl the metadata URL
     * @return the IssuerMetadata object
     * @throws IllegalArgumentException if metadataUrl is null
     * @throws RuntimeException if failed to fetch metadata
     */
    public IssuerMetadata getIssuerMetadata(String metadataUrl) {
        if (metadataUrl == null) {
            throw new IllegalArgumentException("metadataUrl required");
        }

        // Create token for issuer audience if possible
        // Use the issuer DID as audience; tokenService may return null if not configured
        String audience = issuerDid;
        String token = null;
        try {
            token = tokenService.createAndSignToken(audience, null, config);
        } catch (Exception e) {
            log.debug("No token could be created for issuer audience {}: {}", audience, e.getMessage());
        }

        // Build headers with authorization if token is available
        Map<String, String> headers = Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        try {
            return rest.executeAndDeserialize(metadataUrl, "GET", headers, null, IssuerMetadata.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch issuer metadata from " + metadataUrl + ": " + e.getMessage(), e);
        }
    }

    /**
     * Sends a credential request to the issuer's /credentials endpoint.
     *
     * <p>Returns Location header (status URL) on 201/202 response.
     *
     * @param credentialObjectIds the list of credential object IDs to request
     * @return the status URL from Location header
     * @throws IllegalArgumentException if credentialObjectIds is null or empty
     * @throws RuntimeException if the request fails
     */
    public String requestCredential(List<String> credentialObjectIds) {
        if (credentialObjectIds == null || credentialObjectIds.isEmpty()) {
            throw new IllegalArgumentException("credentialObjectIds (non-empty list) is required");
        }

        // Discover issuer service URL from DID document
        String issuerBase = discoverIssuerService();
        String url = issuerBase.endsWith("/") ? issuerBase + DCPConstants.ISSUER_CREDENTIALS_PATH.substring(1) : issuerBase + DCPConstants.ISSUER_CREDENTIALS_PATH;

        // Build request body: CredentialRequestMessage wrapper with requestId
        String requestId = UUID.randomUUID().toString();
        String holderPid = UUID.randomUUID().toString();
        List<CredentialRequestMessage.CredentialReference> credentialRefs = credentialObjectIds.stream()
            .map(id -> CredentialRequestMessage.CredentialReference.Builder.newInstance().id(id).build())
            .toList();
        CredentialRequestMessage req = CredentialRequestMessage.Builder.newInstance()
                .requestId(requestId)
                .holderPid(holderPid)
                .credentials(credentialRefs)
                .build();

        // Create token for issuer audience if possible
        // Use the issuer DID as audience; tokenService may return null if not configured
        String audience = issuerDid;
        String token = null;
        try {
            token = tokenService.createAndSignToken(audience, null, config);
        } catch (Exception e) {
            log.debug("No token could be created for issuer audience {}: {}", audience, e.getMessage());
        }

        try {
            String json = mapper.writeValueAsString(req);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));

            // Build headers
            Map<String, String> headers = new HashMap<>();
            headers.put(HttpHeaders.CONTENT_TYPE, "application/json");
            if (token != null && !token.isBlank()) {
                headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }

            // Build request
            Request.Builder rb = new Request.Builder().url(url);
            headers.forEach(rb::addHeader);
            rb.post(body);
            Request request = rb.build();

            try (Response response = rest.executeCall(request)) {
                if (response == null) {
                    throw new RuntimeException("No response from issuer at " + url);
                }
                int code = response.code();
                if ((code == HttpStatus.CREATED.value()) || (code == HttpStatus.ACCEPTED.value()) || (code >= 200 && code < 300)) {
                    String location = response.header(HttpHeaders.LOCATION);
                    String statusUrl = (location != null && !location.isBlank()) ? location : url;
                    auditPublisher.publishEvent(
                            DcpAuditEventType.CREDENTIAL_REQUESTED,
                            "Credential request submitted for " + credentialObjectIds.size() + " credential(s) to issuer: " + issuerDid,
                            HOLDER_SOURCE,
                            null, issuerDid,
                            credentialObjectIds,
                            requestId,
                            Map.of("credentialIds", credentialObjectIds, "statusUrl", statusUrl, "requestId", requestId));
                    return statusUrl;
                }
                String respBody = null;
                try {
                    if (response.body() != null) {
                        respBody = response.body().string();
                    }
                } catch (Exception ex) {
                    log.debug("Failed to read response body: {}", ex.getMessage());
                }
                auditPublisher.publishEvent(
                        DcpAuditEventType.CREDENTIAL_REQUEST_FAILED,
                        "Credential request failed for issuer: " + issuerDid + ", HTTP " + code,
                        HOLDER_SOURCE,
                        null, issuerDid,
                        credentialObjectIds,
                        requestId,
                        Map.of("credentialIds", credentialObjectIds, "requestId", requestId,
                               "httpStatus", code, "reason", respBody != null ? respBody : ""));
                throw new RuntimeException("Unexpected response from issuer: " + code + " - " + respBody);
            }
        } catch (IOException e) {
            log.error("Error while requesting credential: {}", e.getMessage());
            auditPublisher.publishEvent(
                    DcpAuditEventType.CREDENTIAL_REQUEST_FAILED,
                    "Credential request failed with exception for issuer: " + issuerDid,
                    HOLDER_SOURCE,
                    null, issuerDid,
                    credentialObjectIds,
                    requestId,
                    Map.of("credentialIds", credentialObjectIds, "requestId", requestId, "reason", e.getMessage()));
            throw new RuntimeException(e);
        }
    }

    /**
     * Performs authenticated GET to status URL to retrieve current status (expect JSON).
     *
     * @param statusUrl the status URL
     * @param audience the audience for the token
     * @return the ResponseEntity with status JSON body
     * @throws IllegalArgumentException if statusUrl is null
     * @throws RuntimeException if the request fails
     */
    public ResponseEntity<String> getRequestStatus(String statusUrl, String audience) {
        if (statusUrl == null) {
            throw new IllegalArgumentException("statusUrl required");
        }

        String token = tokenService.createAndSignToken(audience, null, config);

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
     * Polls a status URL until a terminal response or timeout. Returns final response body.
     *
     * @param statusUrl the status URL to poll
     * @param audience the audience for the token
     * @param timeout maximum duration to wait
     * @param backoff initial backoff duration between attempts
     * @return the final status response body
     * @throws InterruptedException if interrupted while waiting
     * @throws RuntimeException if status polling times out
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
                log.warn("Status fetch attempt failed: {}", e.getMessage());
            }
            Thread.sleep(delay);
            delay = Math.min(delay * 2, 5000);
        }
        throw new RuntimeException("Status polling timed out");
    }
}
