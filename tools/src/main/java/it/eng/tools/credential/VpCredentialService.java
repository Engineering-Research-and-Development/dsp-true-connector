package it.eng.tools.credential;

/**
 * Interface for credential services that provide VP JWT authentication.
 * This interface allows the tools module to interact with DCP credential services
 * without a compile-time dependency on the dcp module.
 */
public interface VpCredentialService {

    /**
     * Check if VP JWT authentication is enabled.
     *
     * @return true if VP JWT is enabled, false otherwise
     */
    boolean isVpJwtEnabled();

    /**
     * Generate a Bearer token with VP JWT.
     *
     * @return "Bearer {JWT}" or null if generation fails
     */
    String getBearerToken();

    /**
     * Generate a Bearer token with VP JWT for a specific target URL.
     * The verifier DID is extracted from the target URL.
     *
     * @param targetUrl The target URL (can be null)
     * @return "Bearer {JWT}" or null if generation fails
     */
    String getBearerToken(String targetUrl);
}

