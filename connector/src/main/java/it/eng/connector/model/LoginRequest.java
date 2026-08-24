package it.eng.connector.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/auth/login}.
 *
 * @param email    the user's email address, used as the username
 * @param password the user's plaintext password
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {
}
