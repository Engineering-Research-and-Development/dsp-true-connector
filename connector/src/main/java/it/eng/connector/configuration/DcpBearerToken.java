package it.eng.connector.configuration;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

/**
 * Authentication token for DCP Bearer token authentication.
 *
 * <p>This is a minimal token that holds the raw self-issued ID token (JWT).
 * The actual validation is performed by DcpVerifierAuthenticationProvider.
 *
 * <p>Before validation: isAuthenticated() returns false
 * <p>After validation: Provider returns a UsernamePasswordAuthenticationToken with authorities
 */
public class DcpBearerToken extends AbstractAuthenticationToken {

    private final String token;

    /**
     * Creates an unauthenticated token (used by filter before validation).
     *
     * @param token The raw self-issued ID token (JWT) from Authorization header
     */
    public DcpBearerToken(String token) {
        super(Collections.emptyList());
        this.token = token;
        setAuthenticated(false);
    }

    /**
     * Gets the raw token string.
     *
     * @return The self-issued ID token
     */
    public String getToken() {
        return token;
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return token;
    }
}
