package it.eng.connector.rest.api;

import it.eng.connector.exception.AuthValidationException;
import it.eng.connector.model.LoginRequest;
import it.eng.connector.model.LoginResponse;
import it.eng.connector.model.LogoutRequest;
import it.eng.connector.model.RefreshRequest;
import it.eng.connector.service.AuthService;
import it.eng.tools.auth.condition.InternalOrKeycloakAuthenticationModeCondition;
import it.eng.tools.controller.ApiEndpoints;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

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
 *
 * <p>Request DTOs are validated manually (via an injected {@link Validator}) rather than with
 * {@code @Valid}, so that validation failures raise {@link AuthValidationException} instead of
 * {@link org.springframework.web.bind.MethodArgumentNotValidException}. The shared
 * {@code ExceptionAPIAdvice} (in {@code tools}) is ordered at
 * {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE} and applies to this controller's
 * package, so it would otherwise always intercept {@code MethodArgumentNotValidException} before
 * the dedicated {@code AuthExceptionAdvice} is ever consulted.
 */
@RestController
@RequestMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        path = ApiEndpoints.AUTH_V1)
@Slf4j
@Conditional(InternalOrKeycloakAuthenticationModeCondition.class)
public class AuthController {

    private final AuthService authService;
    private final Validator validator;

    /**
     * Creates the controller with its required service dependencies.
     *
     * @param authService the unified login/refresh/logout service
     * @param validator    the bean validator used to manually validate request DTOs
     */
    public AuthController(AuthService authService, Validator validator) {
        this.authService = authService;
        this.validator = validator;
    }

    /**
     * Authenticates the given credentials and issues a new access/refresh token pair.
     *
     * @param request the login credentials
     * @return 200 OK with the issued {@link LoginResponse}
     */
    @PostMapping(path = "/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        validate(request);
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
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        validate(request);
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
    public ResponseEntity<Void> logout(@RequestBody LogoutRequest request) {
        validate(request);
        authService.logout(request.refreshToken());
        return ResponseEntity.ok().build();
    }

    /**
     * Validates the given request DTO, raising {@link AuthValidationException} on failure.
     *
     * @param request the request DTO to validate
     * @param <T>     the request DTO type
     * @throws AuthValidationException if bean validation reports one or more violations
     */
    private <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
            throw new AuthValidationException(message);
        }
    }
}
