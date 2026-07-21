package it.eng.connector.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Response body for {@code POST /api/v1/auth/login} and {@code POST /api/v1/auth/refresh}.
 *
 * <p>Serialized with snake_case field names ({@code access_token}, {@code refresh_token},
 * {@code token_type}, {@code expires_in}) so the shape mirrors a typical identity-provider token
 * response. This is intentionally a flat, token-only body: no {@code user} object and no
 * {@code sub}/{@code email}/{@code roles}/{@code tenantId} fields, which live exclusively in the
 * JWT claims.
 *
 * @param accessToken  the signed JWT access token
 * @param refreshToken the opaque refresh token id
 * @param tokenType    always {@code "Bearer"}
 * @param expiresIn    the access token's remaining time-to-live, in seconds
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LoginResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

    /**
     * Builds a {@link LoginResponse} from a service-layer token pair, defaulting
     * {@code tokenType} to {@code "Bearer"}.
     *
     * @param accessToken      the signed JWT access token
     * @param refreshToken     the opaque refresh token id
     * @param expiresInSeconds the access token's remaining time-to-live, in seconds
     * @return the assembled {@link LoginResponse}
     */
    public static LoginResponse bearer(String accessToken, String refreshToken, long expiresInSeconds) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}
