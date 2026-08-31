package it.eng.connector.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/auth/logout}.
 *
 * @param refreshToken the opaque refresh token id to revoke, mapped from the incoming
 *                      {@code refresh_token} JSON field
 */
public record LogoutRequest(@NotBlank @JsonProperty("refresh_token") String refreshToken) {
}
