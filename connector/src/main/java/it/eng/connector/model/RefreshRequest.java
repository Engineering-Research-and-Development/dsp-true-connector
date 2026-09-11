package it.eng.connector.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/auth/refresh}.
 *
 * @param refreshToken the opaque refresh token id presented for rotation, mapped from the
 *                      incoming {@code refresh_token} JSON field
 */
public record RefreshRequest(@NotBlank @JsonProperty("refresh_token") String refreshToken) {
}
