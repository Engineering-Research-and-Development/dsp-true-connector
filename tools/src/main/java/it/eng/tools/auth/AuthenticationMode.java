package it.eng.tools.auth;

/**
 * Supported authentication modes for the application.
 */
public enum AuthenticationMode {

    /** OAuth2/OIDC authentication via Keycloak. */
    KEYCLOAK,

    /** Username and password authentication using locally stored credentials. */
    BASIC,

    /** All endpoints are unprotected. For development and testing only. */
    DISABLED
}
