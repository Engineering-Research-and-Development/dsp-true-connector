package it.eng.tools.auth;

/**
 * Interface for connector authentication providers.
 * Implementations provide JWT tokens for connector-to-connector communication.
 */
public interface AuthProvider {

    /**
     * Fetches an authentication token.
     * @param role the role for which to fetch the token
     *
     * @return the authentication token, or null if unavailable
     */
    String fetchToken(String role);

    /**
     * Validates an authentication token.
     *
     * @param token the token to validate
     * @return true if the token is valid, false otherwise
     */
    boolean validateToken(String token);
}


