package it.eng.connector.rest.api;

import it.eng.connector.model.LoginRequest;
import it.eng.connector.model.LoginResponse;
import it.eng.connector.model.LogoutRequest;
import it.eng.connector.model.RefreshRequest;
import it.eng.connector.service.AuthService;
import it.eng.tools.auth.condition.InternalAuthenticationModeCondition;
import it.eng.tools.controller.ApiEndpoints;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the unified admin-zone authentication contract:
 * {@code /api/v1/auth/login}, {@code /api/v1/auth/refresh}, and {@code /api/v1/auth/logout}.
 *
 * <p>The response shape is a flat, snake_case, token-only JSON body (see {@link LoginResponse})
 * that mirrors a typical identity-provider token response, so that clients built against a real
 * Keycloak token response do not need provider-specific parsing.
 *
 * <p>Active only when {@code application.auth.provider=INTERNAL}, mirroring the sole
 * {@link AuthService} implementation ({@code InternalAuthServiceImpl}) currently registered under
 * that same condition; a future Keycloak-backed {@code AuthService} (AUTH3) is expected to widen
 * this condition when it is implemented.
 */
@RestController
@RequestMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        path = ApiEndpoints.AUTH_V1)
@Slf4j
@Conditional(InternalAuthenticationModeCondition.class)
public class AuthController {

    private final AuthService authService;

    /**
     * Creates the controller with its required service dependency.
     *
     * @param authService the unified login/refresh/logout service
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates the given credentials and issues a new access/refresh token pair.
     *
     * @param request the login credentials
     * @return 200 OK with the issued {@link LoginResponse}
     */
    @PostMapping(path = "/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.debug("Login attempt for email {}", request.email());
        AuthService.AuthTokens tokens = authService.login(request.email(), request.password());
        return ResponseEntity.ok(
                LoginResponse.bearer(tokens.accessToken(), tokens.refreshToken(), tokens.expiresInSeconds()));
    }

    /**
     * Rotates a valid refresh token id and mints a fresh access token for its owning subject.
     *
     * @param request the refresh token to rotate
     * @return 200 OK with a newly issued {@link LoginResponse}
     */
    @PostMapping(path = "/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthService.AuthTokens tokens = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(
                LoginResponse.bearer(tokens.accessToken(), tokens.refreshToken(), tokens.expiresInSeconds()));
    }

    /**
     * Revokes a refresh token id. Always returns 200, including for an already-revoked or unknown
     * token, matching {@link AuthService#logout(String)}'s idempotent contract.
     *
     * @param request the refresh token to revoke
     * @return 200 OK with an empty body
     */
    @PostMapping(path = "/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok().build();
    }
}
