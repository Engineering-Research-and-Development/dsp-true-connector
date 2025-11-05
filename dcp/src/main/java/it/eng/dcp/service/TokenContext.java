package it.eng.dcp.service;

import com.nimbusds.jwt.JWTClaimsSet;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

/**
 * Lightweight context extracted from validated tokens used during presentation validation.
 */
@Getter
public class TokenContext {

    private final String issuer;
    private final String subject;
    private final Set<String> scopes = new HashSet<>();
    private final JWTClaimsSet claims;

    public TokenContext(JWTClaimsSet claims) {
        this.claims = claims;
        this.issuer = claims.getIssuer();
        this.subject = claims.getSubject();
    }

}

