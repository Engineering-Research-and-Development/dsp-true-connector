package it.eng.dcp.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.config.DcpProperties;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.service.KeyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * DCP-compliant Self-Issued ID Token service using the "token" claim approach
 * as specified in DCP Protocol v1.0 Section 4.3.1 (Verifiable Presentation Access Token).
 *
 * <p>This service creates tokens with a "token" claim containing either:
 * <ul>
 *   <li>An access token (opaque string for authorization)</li>
 *   <li>A presentationId (reference to a specific VP)</li>
 *   <li>Both (for maximum flexibility)</li>
 * </ul>
 *
 * <p>The verifier will use this token to fetch the VP from the holder's Credential Service
 * via the /presentations/query endpoint, as defined in DCP Protocol Section 5.4.
 *
 * @see <a href="https://w3id.org/dspace-dcp/v1.0">DCP Protocol Specification v1.0</a>
 */
@Service
@Slf4j
public class DcpCompliantTokenService {

    private final DcpProperties props;
    private final KeyService keyService;
    private final PresentationAccessTokenGenerator tokenGenerator;
    private final DidDocumentConfig config;

    @Autowired
    public DcpCompliantTokenService(DcpProperties props,
                                    KeyService keyService,
                                    PresentationAccessTokenGenerator tokenGenerator,
                                    @Qualifier("holderDidDocumentConfig") DidDocumentConfig config) {
        this.props = props;
        this.keyService = keyService;
        this.tokenGenerator = tokenGenerator;
        this.config = config;
    }

    /**
     * Creates a DCP-compliant Self-Issued ID Token with a "token" claim.
     * The token claim contains an access token that the verifier can use to fetch
     * the VP from the holder's Credential Service.
     *
     * @param audienceDid The DID of the verifier (audience)
     * @param scopes The scopes/credential types the verifier is authorized to access
     * @return A signed JWT string
     */
    public String createTokenWithAccessToken(String audienceDid, String... scopes) {
        if (audienceDid == null || audienceDid.isBlank()) {
            throw new IllegalArgumentException("audienceDid is required");
        }

        String connectorDid = getConnectorDid();

        // Generate an access token for the verifier to use when querying presentations
        String accessToken = tokenGenerator.generateAccessToken(audienceDid, scopes);

        log.debug("Creating DCP-compliant token with access token for audience: {}", audienceDid);

        return buildAndSignToken(connectorDid, audienceDid, accessToken, null);
    }

    /**
     * Creates a DCP-compliant Self-Issued ID Token with a "token" claim containing
     * a presentationId reference.
     *
     * @param audienceDid The DID of the verifier (audience)
     * @param presentationId The ID of the presentation the verifier can fetch
     * @return A signed JWT string
     */
    public String createTokenWithPresentationId(String audienceDid, String presentationId) {
        if (audienceDid == null || audienceDid.isBlank()) {
            throw new IllegalArgumentException("audienceDid is required");
        }
        if (presentationId == null || presentationId.isBlank()) {
            throw new IllegalArgumentException("presentationId is required");
        }

        String connectorDid = getConnectorDid();

        log.debug("Creating DCP-compliant token with presentationId: {} for audience: {}",
                  presentationId, audienceDid);

        return buildAndSignToken(connectorDid, audienceDid, presentationId, null);
    }

    /**
     * Creates a DCP-compliant Self-Issued ID Token with both access token and presentationId
     * in the "token" claim. This provides maximum flexibility for the verifier.
     *
     * @param audienceDid The DID of the verifier (audience)
     * @param presentationId The ID of the presentation
     * @param scopes The scopes/credential types authorized
     * @return A signed JWT string
     */
    public String createTokenWithBoth(String audienceDid, String presentationId, String... scopes) {
        if (audienceDid == null || audienceDid.isBlank()) {
            throw new IllegalArgumentException("audienceDid is required");
        }

        String connectorDid = getConnectorDid();

        // Generate access token
        String accessToken = tokenGenerator.generateAccessToken(audienceDid, scopes);

        log.debug("Creating DCP-compliant token with both access token and presentationId: {} for audience: {}",
                  presentationId, audienceDid);

        return buildAndSignToken(connectorDid, audienceDid, accessToken, presentationId);
    }

    /**
     * Builds and signs the Self-Issued ID Token with the "token" claim.
     *
     * @param issuerDid The holder's DID (issuer and subject)
     * @param audienceDid The verifier's DID (audience)
     * @param accessToken The access token (can be null if only presentationId is used)
     * @param presentationId The presentation ID (can be null if only access token is used)
     * @return Signed JWT string
     */
    private String buildAndSignToken(String issuerDid, String audienceDid,
                                     String accessToken, String presentationId) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(300); // 5 minutes validity
            String jti = UUID.randomUUID().toString();

            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issuer(issuerDid)
                .subject(issuerDid)
                .audience(audienceDid)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .jwtID(jti);

            // Add the "token" claim according to DCP spec
            // Format: "token" can be opaque string, structured token, or JSON object
            if (presentationId != null && accessToken != null) {
                // Both: create a structured token claim
                String tokenClaim = String.format("{\"access_token\":\"%s\",\"presentation_id\":\"%s\"}",
                                                  accessToken, presentationId);
                claimsBuilder.claim("token", tokenClaim);
                log.debug("Added token claim with both access_token and presentation_id");
            } else if (presentationId != null) {
                // Only presentationId
                claimsBuilder.claim("token", presentationId);
                log.debug("Added token claim with presentation_id only");
            } else if (accessToken != null) {
                // Only access token (most common DCP pattern)
                claimsBuilder.claim("token", accessToken);
                log.debug("Added token claim with access_token only");
            } else {
                throw new IllegalArgumentException("Either accessToken or presentationId must be provided");
            }

            JWTClaimsSet claims = claimsBuilder.build();

            // Get signing key
            ECKey signingJwk = keyService.getSigningJwk(config);

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .keyID(signingJwk.getKeyID())
                    .build();

            SignedJWT jwt = new SignedJWT(header, claims);
            JWSSigner signer = new ECDSASigner(signingJwk.toECPrivateKey());
            jwt.sign(signer);

            String signedToken = jwt.serialize();
            log.debug("Successfully created and signed DCP-compliant token");

            return signedToken;

        } catch (JOSEException e) {
            log.error("Failed to create and sign DCP-compliant token", e);
            throw new RuntimeException("Failed to create DCP-compliant token", e);
        }
    }

    /**
     * Gets the connector DID from configuration.
     *
     * @return The connector DID
     * @throws IllegalStateException if connector DID is not configured
     */
    private String getConnectorDid() {
        String connectorDid = props.getConnectorDid();

        if (connectorDid == null || connectorDid.isBlank()) {
            log.error("Connector DID is not configured. Please set dcp.connector.did property");
            throw new IllegalStateException("connectorDid is not configured. Please set dcp.connector.did property");
        }

        log.debug("Using connector DID: {}", connectorDid);
        return connectorDid;
    }
}

