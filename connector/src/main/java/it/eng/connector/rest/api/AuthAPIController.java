package it.eng.connector.rest.api;

import it.eng.connector.configuration.ApiUserPrincipal;
import it.eng.connector.dto.*;
import it.eng.connector.service.AuthService;
import it.eng.connector.util.ControllerUtils;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE,
        path = ApiEndpoints.AUTH_V1)
@Slf4j
public class AuthAPIController {

    private final AuthService authService;

    public AuthAPIController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * User login endpoint.
     *
     * @param loginRequest the login request containing email and password
     * @return GenericApiResponse containing login response with tokens and user info
     */
    @PostMapping("/login")
    public ResponseEntity<GenericApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        LoginResponse loginResponse = authService.login(loginRequest);
        return ControllerUtils.success(loginResponse, "Login successful");
    }

    /**
     * User registration endpoint.
     *
     * @param registerRequest the registration request containing user details
     * @return GenericApiResponse containing login response with tokens and user info
     */
    @PostMapping("/register")
    public ResponseEntity<GenericApiResponse<LoginResponse>> register(@Valid @RequestBody RegisterRequest registerRequest) {
        LoginResponse loginResponse = authService.register(registerRequest);
        return ControllerUtils.success(loginResponse, "Registration successful - you are now logged in");
    }

    /**
     * Refresh token endpoint.
     *
     * @param refreshRequest the refresh token request
     * @return GenericApiResponse containing new access and refresh tokens
     */
    @PostMapping("/refresh")
    public ResponseEntity<GenericApiResponse<LoginResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshRequest) {
        LoginResponse loginResponse = authService.refreshToken(refreshRequest);
        return ControllerUtils.success(loginResponse, "Token refreshed successfully");
    }

    /**
     * User logout endpoint.
     *
     * @param authorizationHeader the Authorization header containing the Bearer token
     * @param authentication      the authentication object containing user principal
     * @return GenericApiResponse indicating logout success
     */
    @PostMapping("/logout")
    public ResponseEntity<GenericApiResponse<String>> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = true) String authorizationHeader,
            Authentication authentication) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ") || authentication == null) {
            return ResponseEntity.status(401)
                    .body(GenericApiResponse.error("Unauthorized", "Authorization: Bearer <token> header is required"));
        }
        authService.logout(authentication, authorizationHeader.substring(7));
        return ControllerUtils.success("Logged out successfully");
    }

    /**
     * Get current user information.
     *
     * @param authentication the authentication object containing user principal
     * @return GenericApiResponse containing current user profile
     */
    @GetMapping("/me")
    public ResponseEntity<GenericApiResponse<UserProfileResponse>> getCurrentUser(Authentication authentication) {
        ApiUserPrincipal userPrincipal = (ApiUserPrincipal) authentication.getPrincipal();
        UserProfileResponse userProfile = authService.getCurrentUser(userPrincipal.getUserId());
        return ControllerUtils.success(userProfile, "Current user information");
    }

}
