package it.eng.dcp.holder.service;

import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Generates access tokens that will be included in the "token" claim of Self-Issued ID Tokens.
 * These access tokens are used by verifiers to fetch Verifiable Presentations from the
 * holder's Credential Service.
 *
 * <p>According to DCP v1.0.1 §4.3.1, the format of the token is implementation-specific and is treated
 * as an opaque string by the Verifier. This connector uses one format, decided in ADR D-TEC-014 and
 * produced by {@link SelfIssuedIdTokenService#createAccessTokenWithScopes(String, DidDocumentConfig, String...)}:
 * a JWT signed with the holder's DID key, with {@code aud} = Verifier DID, {@code sub} = holder DID and
 * {@code scope}. The Credential Service validates it with
 * {@link SelfIssuedIdTokenService#validateAccessToken(String, String)}.
 */
@Service
@Slf4j
public class PresentationAccessTokenGenerator {

    private final SelfIssuedIdTokenService tokenService;
    private final DidDocumentConfig config;

    /**
     * Creates the generator.
     *
     * @param tokenService the holder's token service, which owns the access token format
     * @param config the holder's DID configuration
     */
    @Autowired
    public PresentationAccessTokenGenerator(@Qualifier("selfIssuedIdTokenService") SelfIssuedIdTokenService tokenService,
                                            DidDocumentConfig config) {
        this.tokenService = tokenService;
        this.config = config;
    }

    /**
     * Generates an access token that grants the verifier permission to fetch
     * Verifiable Presentations with the specified scopes.
     *
     * @param verifierDid The DID of the verifier who will use this token
     * @param scopes The scopes the verifier may query; without scopes the token authorizes no credentials
     * @return A signed JWT access token (opaque string for the verifier)
     * @throws IllegalArgumentException if verifierDid is null or blank
     */
    public String generateAccessToken(String verifierDid, String... scopes) {
        if (verifierDid == null || verifierDid.isBlank()) {
            throw new IllegalArgumentException("verifierDid is required");
        }
        String accessToken = tokenService.createAccessTokenWithScopes(verifierDid, config, scopes);
        log.debug("Generated access token for verifier: {} with {} scopes",
                verifierDid, scopes != null ? scopes.length : 0);
        return accessToken;
    }
}
