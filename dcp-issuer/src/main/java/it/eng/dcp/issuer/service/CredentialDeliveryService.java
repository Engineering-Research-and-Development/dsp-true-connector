package it.eng.dcp.issuer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.model.CredentialStatus;
import it.eng.dcp.common.service.did.DidResolverService;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.issuer.client.SimpleOkHttpRestClient;
import it.eng.dcp.model.CredentialMessage;
import it.eng.dcp.model.CredentialRequest;
import it.eng.dcp.issuer.repository.CredentialRequestRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.http.client.methods.RequestBuilder;
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
    private final SimpleOkHttpRestClient httpClient;

    @Autowired
    public CredentialDeliveryService(CredentialRequestRepository requestRepository,
                                     DidResolverService didResolverService,
                                     SelfIssuedIdTokenService tokenService,
                                     ObjectMapper mapper,
                                     SimpleOkHttpRestClient httpClient) {
        this.requestRepository = requestRepository;
        this.didResolverService = didResolverService;
        this.tokenService = tokenService;
        this.mapper = mapper;
        this.httpClient = httpClient;
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
            LOG.error("issuerPid is required");
            return false;
        }

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
            String credentialServiceUrl = resolveCredentialServiceEndpoint(holderPid);
            if (credentialServiceUrl == null || credentialServiceUrl.isBlank()) {
                LOG.error("Could not resolve Credential Service endpoint for holder: {}", holderPid);
                return false;
            }

            String targetUrl = credentialServiceUrl.endsWith("/credentials")
                    ? credentialServiceUrl
                    : (credentialServiceUrl.endsWith("/") ? credentialServiceUrl + "credentials" : credentialServiceUrl + "/credentials");

            LOG.info("Delivering {} credentials to holder {} at {}", credentials.size(), holderPid, targetUrl);

            CredentialMessage message = CredentialMessage.Builder.newInstance()
                    .issuerPid(issuerPid)
                    .holderPid(holderPid)
                    .requestId(issuerPid)
                    .status("ISSUED")
                    .credentials(credentials)
                    .build();

            String token = tokenService.createAndSignToken(holderPid, null);
            String messageJson = mapper.writeValueAsString(message);

            String response = sendPostRequest(targetUrl, messageJson, "Bearer " + token);

            if (response != null) {
                LOG.info("Successfully delivered credentials to holder {}.", holderPid);

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
                LOG.error("Failed to deliver credentials to holder {}.", holderPid);
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
            String credentialServiceUrl = resolveCredentialServiceEndpoint(holderPid);
            if (credentialServiceUrl == null || credentialServiceUrl.isBlank()) {
                LOG.error("Could not resolve Credential Service endpoint for holder: {}", holderPid);
                return false;
            }

            String targetUrl = credentialServiceUrl.endsWith("/credentials")
                    ? credentialServiceUrl
                    : (credentialServiceUrl.endsWith("/") ? credentialServiceUrl + "credentials" : credentialServiceUrl + "/credentials");

            LOG.info("Sending rejection notification to holder {} at {}", holderPid, targetUrl);

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

            String response = sendPostRequest(targetUrl, messageJson, "Bearer " + token);

            if (response != null) {
                LOG.info("Successfully sent rejection notification to holder {}.", holderPid);

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
                LOG.error("Failed to send rejection notification to holder {}", holderPid);
                return false;
            }

        } catch (Exception e) {
            LOG.error("Failed to send rejection notification: {}", e.getMessage(), e);
            return false;
        }
    }

    private String resolveCredentialServiceEndpoint(String holderDid) {
        try {
            LOG.warn("Using simplified DID resolution. In production, implement full DID document resolution.");

            if (holderDid.startsWith("did:web:")) {
                String webIdentifier = holderDid.substring("did:web:".length());
                String domainPart;

                if (webIdentifier.contains("%3A")) {
                    int pathSeparatorIndex = webIdentifier.indexOf(':', webIdentifier.indexOf("%3A") + 3);
                    if (pathSeparatorIndex > 0) {
                        domainPart = webIdentifier.substring(0, pathSeparatorIndex);
                    } else {
                        domainPart = webIdentifier;
                    }
                    domainPart = domainPart.replace("%3A", ":");
                } else {
                    String[] parts = webIdentifier.split(":");
                    if (parts.length == 1) {
                        domainPart = parts[0];
                    } else if (parts.length == 2) {
                        try {
                            Integer.parseInt(parts[1]);
                            domainPart = parts[0] + ":" + parts[1];
                        } catch (NumberFormatException e) {
                            domainPart = parts[0];
                        }
                    } else {
                        domainPart = parts[0] + ":" + parts[1];
                    }
                }

                String protocol = "https";
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

    private String sendPostRequest(String url, String jsonPayload, String authorization) {
        try {
            JsonNode jsonNode = mapper.readTree(jsonPayload);
            Request.Builder requestBuilder = new Request.Builder().url(url);
            RequestBody body;
            if (jsonNode != null) {
                body = RequestBody.create(jsonNode.toPrettyString(), MediaType.parse("application/json"));
            } else {
                body = RequestBody.create("", MediaType.parse("application/json"));
            }
            requestBuilder.post(body);

            if (authorization != null && !authorization.isBlank()) {
                requestBuilder.addHeader("Authorization", authorization);
            }

            Request request = requestBuilder.build();
            try (Response response = httpClient.executeCall(request)) {
                int code = response.code();
                LOG.info("Status {}", code);
                String resp = null;
                if (response.body() != null) {
                    resp = response.body().string();
                }
                LOG.info("Response received: {}", resp);
                if (response.isSuccessful()) { // code in 200..299
                    return response.body().string();
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to send POST request to {}: {}", url, e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return null;
    }
}

