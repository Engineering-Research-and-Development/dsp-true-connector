package it.eng.dcp.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.config.DcpProperties;
import it.eng.dcp.core.DidResolverService;
import it.eng.dcp.core.DidResolutionException;
import it.eng.dcp.core.JtiReplayCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Self-Issued ID Token service using EC keys from `KeyService` for signing.
 */
@Service
@Slf4j
public class SelfIssuedIdTokenService {

    private final DcpProperties props;
    private final DidResolverService didResolver;
    private final JtiReplayCache jtiCache;
    private final KeyService keyService;

    // test hook to override the signing key with a specific EC JWK (used in unit tests)
    private ECKey overrideSigningKey;

    @Autowired
    public SelfIssuedIdTokenService(DcpProperties props, DidResolverService didResolver, JtiReplayCache jtiCache, KeyService keyService) {
        this.props = props;
        this.didResolver = didResolver;
        this.jtiCache = jtiCache;
        this.keyService = keyService;
    }

    // Test-only setter
    void setOverrideSigningKey(ECKey key) {
        this.overrideSigningKey = key;
    }

    public String createAndSignToken(String audienceDid, String accessToken) {
        if (audienceDid == null || audienceDid.isBlank()) {
            throw new IllegalArgumentException("audienceDid required");
        }

        log.debug("Creating token for audience: {}", audienceDid);
        log.debug("DcpProperties instance: {}", props);

        String connectorDid = props.getConnectorDid();
        log.debug("Retrieved connectorDid: {}", connectorDid);

        if (connectorDid == null || connectorDid.isBlank()) {
            log.error("connectorDid is null or blank! DcpProperties: {}", props);
            throw new IllegalStateException("connectorDid is not configured. Please set dcp.connector.did property");
        }

        try {
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(300);
            String jti = UUID.randomUUID().toString();

            JWTClaimsSet.Builder cb = new JWTClaimsSet.Builder();
            cb.issuer(connectorDid).subject(connectorDid);
            cb.audience(audienceDid);
            cb.issueTime(Date.from(now));
            cb.expirationTime(Date.from(exp));
            cb.jwtID(jti);
            if (accessToken != null) cb.claim("token", accessToken);

            JWTClaimsSet claims = cb.build();

            // Obtain EC signing key: prefer override (tests), otherwise ask KeyService for signing JWK
            ECKey signingJwk;
            if (overrideSigningKey != null) {
                signingJwk = overrideSigningKey;
            } else {
                if (keyService == null) {
                    throw new IllegalStateException("KeyService is not available for signing");
                }
                signingJwk = keyService.getSigningJwk();
            }

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .keyID(signingJwk.getKeyID())
                    .build();

            SignedJWT jwt = new SignedJWT(header, claims);
            JWSSigner signer = new ECDSASigner(signingJwk.toECPrivateKey());
            jwt.sign(signer);

            return jwt.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    public JWTClaimsSet validateToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            String issuer = jwt.getJWTClaimsSet().getIssuer();
            String kid = jwt.getHeader().getKeyID();
            if (issuer == null || kid == null) throw new SecurityException("Missing issuer or kid");

            JWK jwk;
            try {
                jwk = didResolver.resolvePublicKey(issuer, kid, "capabilityInvocation");
            } catch (DidResolutionException dre) {
                throw new SecurityException("Failed to resolve issuer public key", dre);
            }
            if (jwk == null) throw new SecurityException("No key found for issuer/kid");

            // Expect EC public key
            ECKey ecPub = (ECKey) jwk;
            JWSVerifier verifier = new ECDSAVerifier(ecPub.toECPublicKey());
            if (!jwt.verify(verifier)) throw new SecurityException("Invalid signature");

            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            // basic claim checks
            Instant now = Instant.now();
            Date exp = claims.getExpirationTime();
            Date iat = claims.getIssueTime();
            if (exp == null || iat == null) throw new SecurityException("Missing iat/exp");
            if (exp.toInstant().isBefore(now)) throw new SecurityException("Token expired");

            // replay protection: use jti and exp
            String jti = claims.getJWTID();
            if (jti == null) throw new SecurityException("Missing jti");
            jtiCache.checkAndPut(jti, exp.toInstant());

            return claims;
        } catch (java.text.ParseException | JOSEException e) {
            throw new RuntimeException(e);
        }
    }
}
