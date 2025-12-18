package it.eng.dcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.model.CredentialStatus;
import it.eng.dcp.common.service.did.DidResolverService;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.model.CredentialMessage;
import it.eng.dcp.model.CredentialRequest;
import it.eng.dcp.repository.CredentialRequestRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service responsible for processing approved credential requests and delivering
 * credentials to the Holder's Credential Service via the DCP Storage API.
 */
@Service
public class CredentialDeliveryService {

    private static final Logger LOG = LoggerFactory.getLogger(CredentialDeliveryService.class);

    private final CredentialRequestRepository requestRepository;
    private final DidResolverService didResolverService;
    private final SelfIssuedIdTokenService tokenService;
    private final ObjectMapper mapper;
    private final OkHttpRestClient httpClient;

    @Autowired
    public CredentialDeliveryService(CredentialRequestRepository requestRepository,
                                     DidResolverService didResolverService,
                                     SelfIssuedIdTokenService tokenService,
                                     ObjectMapper mapper,
                                     OkHttpRestClient httpClient) {
        this.requestRepository = requestRepository;
        this.didResolverService = didResolverService;
        this.tokenService = tokenService;
        this.mapper = mapper;
        this.httpClient = httpClient;
    }

    /**
     * Process and deliver credentials for an approved request.
     * This method:
     * 1. Retrieves the credential request
     * 2. Generates/retrieves the actual credentials
     * 3. Resolves the holder's DID to find their Credential Service endpoint
     * 4. Sends a CredentialMessage to the holder's /credentials endpoint
     * 5. Updates the request status to ISSUED
     *
     * @param issuerPid The issuer PID of the credential request
     * @param credentials List of credential containers to deliver
     * @return true if delivery was successful, false otherwise
     */
    public boolean deliverCredentials(String issuerPid, List<CredentialMessage.CredentialContainer> credentials) {
        if (issuerPid == null || issuerPid.isBlank()) {
            LOG.error("issuerPid is required");
            return false;
        }

        // Retrieve the credential request
        CredentialRequest request = requestRepository.findByIssuerPid(issuerPid)
                .orElseThrow(() -> new IllegalArgumentException("Credential request not found: " + issuerPid));

        if (request.getStatus() == CredentialStatus.ISSUED) {
            LOG.warn("Credential request {} already issued", issuerPid);
            return false;
        }

        if (request.getStatus() == CredentialStatus.REJECTED) {
            LOG.warn("Credential request {} was rejected", issuerPid);
            return false;
        }

        String holderPid = request.getHolderPid();
        if (holderPid == null || !holderPid.startsWith("did:")) {
            LOG.error("Invalid holder PID (must be a DID): {}", holderPid);
            return false;
        }

        try {
            // Resolve holder's DID document to find Credential Service endpoint
            String credentialServiceUrl = resolveCredentialServiceEndpoint(holderPid);
            if (credentialServiceUrl == null || credentialServiceUrl.isBlank()) {
                LOG.error("Could not resolve Credential Service endpoint for holder: {}", holderPid);
                return false;
            }

            // Ensure URL ends with /credentials for Storage API
            String targetUrl = credentialServiceUrl.endsWith("/credentials")
                    ? credentialServiceUrl
                    : (credentialServiceUrl.endsWith("/") ? credentialServiceUrl + "credentials" : credentialServiceUrl + "/credentials");

            LOG.info("Delivering {} credentials to holder {} at {}", credentials.size(), holderPid, targetUrl);

            // Build CredentialMessage
            CredentialMessage message = CredentialMessage.Builder.newInstance()
                    .issuerPid(issuerPid)
                    .holderPid(holderPid)
                    .requestId(issuerPid)
                    .status("ISSUED")
                    .credentials(credentials)
                    .build();

            // Generate Self-Issued ID Token for authentication
            String token = tokenService.createAndSignToken(holderPid, null);

            // Send CredentialMessage to holder's Credential Service
            String messageJson = mapper.writeValueAsString(message);

            GenericApiResponse<String> response = sendPostRequest(targetUrl, messageJson, "Bearer " + token);

            if (response != null && response.isSuccess()) {
                LOG.info("Successfully delivered credentials to holder {}. Response: {}", holderPid, response.getMessage());

                // Update request status to ISSUED
                request = CredentialRequest.Builder.newInstance()
                        .issuerPid(request.getIssuerPid())
                        .holderPid(request.getHolderPid())
                        .credentialIds(request.getCredentialIds())
                        .status(CredentialStatus.ISSUED)
                        .createdAt(request.getCreatedAt())
                        .build();
                requestRepository.save(request);

                return true;
            } else {
                String errorMsg = response != null ? response.getMessage() : "No response";
                LOG.error("Failed to deliver credentials to holder {}. Error: {}", holderPid, errorMsg);
                return false;
            }

        } catch (Exception e) {
            LOG.error("Failed to deliver credentials to holder {}: {}", holderPid, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Reject a credential request.
     *
     * @param issuerPid The issuer PID of the credential request
     * @param rejectionReason The reason for rejection
     * @return true if rejection was successful, false otherwise
     */
    public boolean rejectCredentialRequest(String issuerPid, String rejectionReason) {
        if (issuerPid == null || issuerPid.isBlank()) {
            LOG.error("issuerPid is required");
            return false;
        }

        CredentialRequest request = requestRepository.findByIssuerPid(issuerPid)
                .orElseThrow(() -> new IllegalArgumentException("Credential request not found: " + issuerPid));

        if (request.getStatus() == CredentialStatus.ISSUED) {
            LOG.warn("Cannot reject credential request {} - already issued", issuerPid);
            return false;
        }

        String holderPid = request.getHolderPid();
        if (holderPid == null || !holderPid.startsWith("did:")) {
            LOG.error("Invalid holder PID (must be a DID): {}", holderPid);
            return false;
        }

        try {
            // Resolve holder's DID document to find Credential Service endpoint
            String credentialServiceUrl = resolveCredentialServiceEndpoint(holderPid);
            if (credentialServiceUrl == null || credentialServiceUrl.isBlank()) {
                LOG.error("Could not resolve Credential Service endpoint for holder: {}", holderPid);
                return false;
            }

            String targetUrl = credentialServiceUrl.endsWith("/credentials")
                    ? credentialServiceUrl
                    : (credentialServiceUrl.endsWith("/") ? credentialServiceUrl + "credentials" : credentialServiceUrl + "/credentials");

            LOG.info("Sending rejection notification to holder {} at {}", holderPid, targetUrl);

            // Build rejection CredentialMessage
            CredentialMessage message = CredentialMessage.Builder.newInstance()
                    .issuerPid(issuerPid)
                    .holderPid(holderPid)
                    .requestId(issuerPid)
                    .status("REJECTED")
                    .rejectionReason(rejectionReason)
                    .credentials(List.of())
                    .build();

            String token = tokenService.createAndSignToken(holderPid, null);
            String messageJson = mapper.writeValueAsString(message);

            GenericApiResponse<String> response = sendPostRequest(targetUrl, messageJson, "Bearer " + token);

            if (response != null && response.isSuccess()) {
                LOG.info("Successfully sent rejection notification to holder {}. Response: {}", holderPid, response.getMessage());

                // Update request status to REJECTED
                request = CredentialRequest.Builder.newInstance()
                        .issuerPid(request.getIssuerPid())
                        .holderPid(request.getHolderPid())
                        .credentialIds(request.getCredentialIds())
                        .status(CredentialStatus.REJECTED)
                        .rejectionReason(rejectionReason)
                        .createdAt(request.getCreatedAt())
                        .build();
                requestRepository.save(request);

                return true;
            } else {
                String errorMsg = response != null ? response.getMessage() : "No response";
                LOG.error("Failed to send rejection notification to holder {}. Error: {}", holderPid, errorMsg);
                return false;
            }

        } catch (Exception e) {
            LOG.error("Failed to send rejection notification: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Helper method to create a JWT credential container.
     *
     * @param credentialType The type of credential (e.g., "MembershipCredential")
     * @param jwtPayload The JWT token string
     * @return A CredentialContainer with JWT format
     */
    public CredentialMessage.CredentialContainer createJwtCredential(String credentialType, String jwtPayload) {
        return CredentialMessage.CredentialContainer.Builder.newInstance()
                .credentialType(credentialType)
                .payload(jwtPayload)
                .format("jwt")
                .build();
    }

    /**
     * Helper method to create a JSON-LD credential container.
     *
     * @param credentialType The type of credential (e.g., "OrganizationCredential")
     * @param credentialObject The JSON-LD credential object
     * @return A CredentialContainer with json-ld format
     */
    public CredentialMessage.CredentialContainer createJsonLdCredential(String credentialType, Map<String, Object> credentialObject) {
        return CredentialMessage.CredentialContainer.Builder.newInstance()
                .credentialType(credentialType)
                .payload(credentialObject)
                .format("json-ld")
                .build();
    }

    /**
     * Resolve the Credential Service endpoint from a holder's DID.
     *
     * @param holderDid The DID of the holder
     * @return The Credential Service endpoint URL, or null if not found
     */
    private String resolveCredentialServiceEndpoint(String holderDid) {
        try {
            // For now, we'll construct a simple endpoint based on the DID
            // In a full implementation, we would call didResolverService.resolvePublicKey()
            // and then fetch the full DID document to extract the service endpoint
            // Since DidResolverService only resolves public keys, not full DID documents,
            // we'll use a simplified approach for this implementation

            // TODO: Implement proper DID document resolution when available
            LOG.warn("Using simplified DID resolution. In production, implement full DID document resolution.");

            // Parse did:web format to construct endpoint
            // DID Web format can be:
            // 1. did:web:localhost%3A8080 -> http://localhost:8080
            // 2. did:web:localhost:8080 -> http://localhost:8080 (non-standard but common)
            // 3. did:web:example.com -> https://example.com
            // 4. did:web:localhost%3A8080:holder -> http://localhost:8080 (ignore path after domain:port)
            // 5. did:web:localhost:8080:holder -> http://localhost:8080 (ignore path after domain:port)
            if (holderDid.startsWith("did:web:")) {
                String webIdentifier = holderDid.substring("did:web:".length());

                String domainPart;

                // Check if port is encoded with %3A
                if (webIdentifier.contains("%3A")) {
                    // Format: localhost%3A8080 or localhost%3A8080:holder
                    // Find the first unencoded colon (which would be a path separator after %3Aport)
                    int pathSeparatorIndex = webIdentifier.indexOf(':', webIdentifier.indexOf("%3A") + 3);
                    if (pathSeparatorIndex > 0) {
                        // Has path component, extract just domain:port
                        domainPart = webIdentifier.substring(0, pathSeparatorIndex);
                    } else {
                        // No path component
                        domainPart = webIdentifier;
                    }
                    // Decode %3A to :
                    domainPart = domainPart.replace("%3A", ":");
                } else {
                    // Format: localhost:8080 or localhost:8080:holder or example.com or example.com:holder
                    // Need to distinguish between port and path separators
                    // Strategy: count colons
                    // - 0 colons: just domain (example.com)
                    // - 1 colon: domain:port (localhost:8080) OR domain:path (example.com:holder)
                    // - 2+ colons: domain:port:path (localhost:8080:holder)

                    String[] parts = webIdentifier.split(":");
                    if (parts.length == 1) {
                        // Just domain
                        domainPart = parts[0];
                    } else if (parts.length == 2) {
                        // Could be domain:port OR domain:path
                        // Try to parse second part as number to determine if it's a port
                        try {
                            Integer.parseInt(parts[1]);
                            // It's a number, so it's a port
                            domainPart = parts[0] + ":" + parts[1];
                        } catch (NumberFormatException e) {
                            // Not a number, so it's a path - just use domain
                            domainPart = parts[0];
                        }
                    } else {
                        // 3+ parts: domain:port:path... - take first two parts
                        domainPart = parts[0] + ":" + parts[1];
                    }
                }

                // Construct base URL - assume http for localhost, https for others
                String protocol = "https"; //domainPart.startsWith("localhost") ? "http" : "https";
                String baseUrl = protocol + "://" + domainPart;
                return baseUrl + "/dcp/credentials";
            }

            LOG.error("Unable to resolve Credential Service endpoint for DID: {}", holderDid);
            return null;
        } catch (Exception e) {
            LOG.error("Failed to resolve DID {}: {}", holderDid, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Helper method to send POST requests with JSON payload.
     *
     * @param url The target URL
     * @param jsonPayload The JSON payload as string
     * @param authorization The authorization header value (e.g., "Bearer token")
     * @return GenericApiResponse with the result
     */
    private GenericApiResponse<String> sendPostRequest(String url, String jsonPayload, String authorization) {
        try {
            // Convert JSON string to JsonNode for OkHttpRestClient
            com.fasterxml.jackson.databind.JsonNode jsonNode = mapper.readTree(jsonPayload);
            return httpClient.sendRequestProtocol(url, jsonNode, authorization);
        } catch (Exception e) {
            LOG.error("Failed to send POST request to {}: {}", url, e.getMessage(), e);
            return GenericApiResponse.error(e.getMessage());
        }
    }
}
