package it.eng.dcp.issuer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.audit.DcpAuditEventType;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.CredentialMessage;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.model.CredentialStatus;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.repository.CredentialRequestRepository;
import it.eng.dcp.common.service.audit.DcpAuditEventPublisher;
import it.eng.dcp.common.service.did.DidResolverService;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service responsible for processing approved credential requests and delivering
 * credentials to the Holder's Credential Service via the DCP Storage API.
 */
@Service
@Slf4j
public class CredentialDeliveryService {

    private static final String ISSUER_SOURCE = "issuer";

    private final CredentialRequestRepository requestRepository;
    private final SelfIssuedIdTokenService tokenService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SimpleOkHttpRestClient httpClient;
    private final DidDocumentConfig config;
    private final DidResolverService didResolverService;
    private final DcpAuditEventPublisher auditPublisher;

    @Autowired
    public CredentialDeliveryService(CredentialRequestRepository requestRepository,
                                     @Qualifier("selfIssuedIdTokenService") SelfIssuedIdTokenService tokenService,
                                     SimpleOkHttpRestClient httpClient,
                                     DidDocumentConfig config,
                                     DidResolverService didResolverService,
                                     DcpAuditEventPublisher auditPublisher) {
        this.requestRepository = requestRepository;
        this.tokenService = tokenService;
        this.httpClient = httpClient;
        this.config = config;
        this.didResolverService = didResolverService;
        this.auditPublisher = auditPublisher;
    }

    /**
     * Process and deliver credentials for an approved request.
     *
     * @param issuerPid The issuer PID of the credential request
     * @param credentials List of credentials to deliver
     * @return true if delivery was successful, false otherwise
     */
    public boolean deliverCredentials(String issuerPid, List<CredentialMessage.CredentialContainer> credentials) {
        if (issuerPid == null || issuerPid.isBlank()) {
            log.error("issuerPid is required");
            return false;
        }

        CredentialRequest request = requestRepository.findByIssuerPid(issuerPid)
                .orElseThrow(() -> new IllegalArgumentException("Credential request not found: " + issuerPid));

        if (request.getStatus() == CredentialStatus.ISSUED) {
            log.warn("Credential request {} already issued", issuerPid);
            return false;
        }

        if (request.getStatus() == CredentialStatus.REJECTED) {
            log.warn("Credential request {} was rejected", issuerPid);
            return false;
        }

        String holderDid = request.getHolderDid();
        if (holderDid == null || !holderDid.startsWith("did:")) {
            log.error("Invalid holder DID: {}", holderDid);
            return false;
        }

        try {
            String credentialServiceUrl = resolveCredentialServiceEndpoint(holderDid);
            if (credentialServiceUrl == null || credentialServiceUrl.isBlank()) {
                log.error("Could not resolve Credential Service endpoint for holder: {}", holderDid);
                return false;
            }

            log.info("Delivering {} credentials to holder {} at {}", credentials.size(), holderDid, credentialServiceUrl);

            CredentialMessage message = CredentialMessage.Builder.newInstance()
                    .issuerPid(issuerPid)
                    .holderPid(request.getHolderPid())
                    .requestId(issuerPid)
                    .status(CredentialStatus.ISSUED.toString())
                    .credentials(credentials)
                    .build();

            String token = tokenService.createAndSignToken(holderDid, null, config);
            String messageJson = mapper.writeValueAsString(message);

            Request httpRequest = new Request.Builder()
                    .url(credentialServiceUrl)
                    .addHeader("Authorization", "Bearer " + token)
                    .post(RequestBody.create(messageJson, MediaType.parse("application/json")))
                    .build();

            try (Response httpResponse = httpClient.executeCall(httpRequest)) {
                boolean delivered = httpResponse != null && httpResponse.isSuccessful();

                if (delivered) {
                    log.info("Successfully delivered credentials to holder {}.", holderDid);

                // Update the existing request by preserving its id
                request = CredentialRequest.Builder.newInstance()
                        .id(request.getId())
                        .issuerPid(request.getIssuerPid())
                        .holderPid(request.getHolderPid())
                        .holderDid(request.getHolderDid())
                        .credentialIds(request.getCredentialIds())
                        .status(CredentialStatus.ISSUED)
                        .createdAt(request.getCreatedAt())
                        .build();
                requestRepository.save(request);

                    List<String> credentialTypes = credentials.stream()
                            .map(CredentialMessage.CredentialContainer::getCredentialType)
                            .toList();
                    auditPublisher.publishEvent(
                            DcpAuditEventType.CREDENTIAL_DELIVERED,
                            "Credentials delivered to holder: " + holderDid,
                            ISSUER_SOURCE,
                            holderDid, null,
                            credentialTypes,
                            issuerPid,
                            Map.of("issuerPid", issuerPid, "holderDid", holderDid,
                                   "credentialsCount", credentials.size(), "credentialTypes", credentialTypes));
                    return true;
                } else {
                    log.error("Failed to deliver credentials to holder {}.", holderDid);
                    auditPublisher.publishEvent(
                            DcpAuditEventType.CREDENTIAL_DELIVERY_FAILED,
                            "Credential delivery failed for holder: " + holderDid,
                            ISSUER_SOURCE,
                            holderDid, null, null,
                            issuerPid,
                            Map.of("issuerPid", issuerPid, "holderDid", holderDid, "reason", "No response from holder"));
                    return false;
                }
            }

        } catch (Exception e) {
            log.error("Failed to deliver credentials to holder {}: {}", holderDid, e.getMessage(), e);
            auditPublisher.publishEvent(
                    DcpAuditEventType.CREDENTIAL_DELIVERY_FAILED,
                    "Credential delivery failed with exception for holder: " + holderDid,
                    ISSUER_SOURCE,
                    holderDid, null, null,
                    issuerPid,
                    Map.of("issuerPid", issuerPid, "holderDid", holderDid, "reason", e.getMessage()));
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
            log.error("issuerPid is required");
            return false;
        }

        CredentialRequest request = requestRepository.findByIssuerPid(issuerPid)
                .orElseThrow(() -> new IllegalArgumentException("Credential request not found: " + issuerPid));

        if (request.getStatus() == CredentialStatus.ISSUED) {
            log.warn("Cannot reject credential request {} - already issued", issuerPid);
            return false;
        }

        String holderDid = request.getHolderDid();
        if (holderDid == null || !holderDid.startsWith("did:")) {
            log.error("Invalid holder DID: {}", holderDid);
            return false;
        }

        try {
            String credentialServiceUrl = resolveCredentialServiceEndpoint(holderDid);
            if (credentialServiceUrl == null || credentialServiceUrl.isBlank()) {
                return false;
            }

            log.info("Sending rejection notification to holder {} at {}", holderDid, credentialServiceUrl);

            CredentialMessage message = CredentialMessage.Builder.newInstance()
                    .issuerPid(issuerPid)
                    .holderPid(holderDid)
                    .requestId(issuerPid)
                    .status("REJECTED")
                    .rejectionReason(rejectionReason)
                    .credentials(List.of(CredentialMessage.CredentialContainer.Builder.newInstance().credentialType("No type").format("JWT").build()))
                    .build();

            String token = tokenService.createAndSignToken(holderDid, null, config);
            String messageJson = mapper.writeValueAsString(message);

            Request httpRequest = new Request.Builder()
                    .url(credentialServiceUrl)
                    .addHeader("Authorization", "Bearer " + token)
                    .post(RequestBody.create(messageJson, MediaType.parse("application/json")))
                    .build();

            try (Response httpResponse = httpClient.executeCall(httpRequest)) {
                boolean sent = httpResponse != null && httpResponse.isSuccessful();

                if (sent) {
                    log.info("Successfully sent rejection notification to holder {}.", holderDid);

                    // Update the existing request by preserving its MongoDB id
                    request = CredentialRequest.Builder.newInstance()
                            .id(request.getId())
                            .issuerPid(request.getIssuerPid())
                            .holderPid(request.getHolderPid())
                            .holderDid(request.getHolderDid())
                            .credentialIds(request.getCredentialIds())
                            .status(CredentialStatus.REJECTED)
                            .rejectionReason(rejectionReason)
                            .createdAt(request.getCreatedAt())
                            .build();
                    requestRepository.save(request);

                    auditPublisher.publishEvent(
                            DcpAuditEventType.CREDENTIAL_DENIED,
                            "Credential request rejected, notification sent to holder: " + holderDid,
                            ISSUER_SOURCE,
                            holderDid, null, null,
                            issuerPid,
                            Map.of("issuerPid", issuerPid, "holderDid", holderDid, "rejectionReason", rejectionReason));
                    return true;
                } else {
                    log.error("Failed to send rejection notification to holder {}", holderDid);
                    return false;
                }
            }

        } catch (Exception e) {
            log.error("Failed to send rejection notification: {}", e.getMessage(), e);
            return false;
        }
    }

    private String resolveCredentialServiceEndpoint(String holderDid) {
        try {
            DidDocument document = didResolverService.fetchDidDocumentCached(holderDid);
            String credentialServiceUrl = document.getServices().stream()
                    .filter(s -> s.type().equals("CredentialService"))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("No CredentialService found in DID Document for " + holderDid))
                    .serviceEndpoint();

            String targetUrl = credentialServiceUrl.endsWith("/credentials")
                    ? credentialServiceUrl
                    : (credentialServiceUrl.endsWith("/") ? credentialServiceUrl + "credentials" : credentialServiceUrl + "/credentials");

            return targetUrl;
        } catch (Exception e) {
            log.error("Failed to resolve DID {}: {}", holderDid, e.getMessage(), e);
            return null;
        }
    }
}
