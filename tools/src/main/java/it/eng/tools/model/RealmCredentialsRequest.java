package it.eng.tools.model;

/**
 * Request-only payload that carries the client secret of a tenant realm's user-management client.
 * The secret is never part of {@link #toString()}.
 *
 * @param clientSecret the client secret, never echoed back
 */
public record RealmCredentialsRequest(String clientSecret) {

    /**
     * Describes the request without the secret.
     *
     * @return a fixed description
     */
    @Override
    public String toString() {
        return "RealmCredentialsRequest{clientSecret=***}";
    }
}
