package it.eng.connector.service;

import it.eng.connector.configuration.ApiUserPrincipal;
import it.eng.connector.dto.*;
import it.eng.connector.model.RefreshToken;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.connector.repository.UserRepository;
import it.eng.connector.util.PasswordCheckValidator;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordCheckValidator passwordCheckValidator;
    private final JwtTokenService jwtTokenService;
    private final boolean registrationAuto;
    private final String defaultRole;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       PasswordCheckValidator passwordCheckValidator,
                       JwtTokenService jwtTokenService,
                       @Value("${app.registration.auto:true}") boolean registrationAuto,
                       @Value("${app.registration.default-role:ROLE_USER}") String defaultRole) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordCheckValidator = passwordCheckValidator;
        this.jwtTokenService = jwtTokenService;
        this.registrationAuto = registrationAuto;
        this.defaultRole = defaultRole;
    }

    /**
     * Authenticate user and generate tokens.
     *
     * @param loginRequest the login credentials
     * @return LoginResponse with tokens and user info
     */
    public LoginResponse login(LoginRequest loginRequest) {
        log.info("Login attempt for user: {}", loginRequest.getEmail());

        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is disabled");
        }

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // Update last login on successful login
        user.setLastLoginDate(LocalDateTime.now());
        userRepository.save(user);

        // Generate tokens
        String accessToken = jwtTokenService.generateAccessToken(user);
        String refreshToken = jwtTokenService.generateRefreshToken(user);

        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenService.getAccessTokenExpirationSeconds())
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();

        log.info("User {} logged in successfully", user.getEmail());
        return loginResponse;
    }

    /**
     * Register a new user and automatically log them in.
     *
     * @param registerRequest the registration details
     * @return LoginResponse with tokens and user info
     */
    @Transactional
    public LoginResponse register(RegisterRequest registerRequest) {
        log.info("Registration attempt for user: {}", registerRequest.getEmail());

        // Check if user already exists
        if (userRepository.findByEmail(registerRequest.getEmail()).isPresent()) {
            throw new BadRequestException("User with this email already exists");
        }

        // Validate password
        passwordCheckValidator.isValid(registerRequest.getPassword());

        // Create new user with conditional enabled status
        User newUser = User.builder()
                .id("urn:uuid:" + UUID.randomUUID())
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .enabled(registrationAuto)  // Enabled based on auto setting
                .expired(false)
                .locked(false)
                .createdDate(LocalDateTime.now())
                .role(Role.valueOf(defaultRole))
                .build();

        try {
            User savedUser = userRepository.save(newUser);

            if (registrationAuto) {
                // Auto-registration: generate tokens and return login response
                String accessToken = jwtTokenService.generateAccessToken(savedUser);
                String refreshToken = jwtTokenService.generateRefreshToken(savedUser);

                LoginResponse loginResponse = LoginResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .tokenType("Bearer")
                        .expiresIn(jwtTokenService.getAccessTokenExpirationSeconds())
                        .userId(savedUser.getId())
                        .firstName(savedUser.getFirstName())
                        .lastName(savedUser.getLastName())
                        .email(savedUser.getEmail())
                        .role(savedUser.getRole())
                        .build();

                log.info("User {} registered and logged in successfully", savedUser.getEmail());
                return loginResponse;
            } else {
                // Manual approval: return success message without tokens
                LoginResponse loginResponse = LoginResponse.builder()
                        .userId(savedUser.getId())
                        .firstName(savedUser.getFirstName())
                        .lastName(savedUser.getLastName())
                        .email(savedUser.getEmail())
                        .role(savedUser.getRole())
                        .message("Registration successful. Your account is pending admin approval.")
                        .build();

                log.info("User {} registered successfully, pending admin approval", savedUser.getEmail());
                return loginResponse;
            }
        } catch (DuplicateKeyException e) {
            log.warn("Registration failed: User with email {} already exists", registerRequest.getEmail());
            throw new BadRequestException("User with this email already exists");
        }
    }

    /**
     * Refresh access token using refresh token.
     *
     * @param refreshRequest the refresh token request
     * @return LoginResponse with new tokens
     */
    public LoginResponse refreshToken(RefreshTokenRequest refreshRequest) {
        log.debug("Token refresh attempt");

        Optional<RefreshToken> refreshTokenOpt = jwtTokenService.validateRefreshToken(refreshRequest.getRefreshToken());
        if (refreshTokenOpt.isEmpty()) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        RefreshToken refreshToken = refreshTokenOpt.get();
        User user = refreshToken.getUser();

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is disabled");
        }

        // Generate new tokens
        String newAccessToken = jwtTokenService.generateAccessToken(user);
        String newRefreshToken = jwtTokenService.generateRefreshToken(user);

        // Revoke old refresh token
        jwtTokenService.revokeRefreshToken(refreshRequest.getRefreshToken());

        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenService.getAccessTokenExpirationSeconds())
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();

        log.debug("Token refreshed successfully for user: {}", user.getEmail());
        return loginResponse;
    }

    /**
     * Logout user by revoking both refresh and access tokens.
     *
     * @param logoutRequest the logout request containing both tokens
     */
    public void logout(LogoutRequest logoutRequest) {
        log.debug("Logout attempt");

        // Revoke refresh token
        jwtTokenService.revokeRefreshToken(logoutRequest.getRefreshToken());

        // Revoke access token
        jwtTokenService.revokeAccessToken(logoutRequest.getAccessToken(), "logout");

        log.debug("User logged out successfully - both tokens revoked");
    }

    /**
     * Logout using Authorization header. Revokes the presented access token and revokes
     * all refresh tokens for the currently authenticated user.
     *
     * @param authentication current authentication (must contain ApiUserPrincipal)
     * @param accessToken    bearer token from Authorization header (without prefix)
     */
    public void logout(Authentication authentication, String accessToken) {
        log.debug("Header-based logout attempt");
        if (authentication == null || !(authentication.getPrincipal() instanceof ApiUserPrincipal)) {
            throw new BadCredentialsException("Unauthorized");
        }
        ApiUserPrincipal principal = (ApiUserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new BadCredentialsException("Unauthorized"));

        // Revoke the presented access token
        jwtTokenService.revokeAccessToken(accessToken, "logout");
        // Revoke all refresh tokens so they cannot be used to mint new access tokens
        jwtTokenService.revokeAllUserRefreshTokens(user);
        log.debug("User {} logged out: access token revoked and all refresh tokens revoked", user.getEmail());
    }

    /**
     * Get current user information from user ID.
     *
     * @param userId the user ID
     * @return UserProfileResponse with user info
     */
    public UserProfileResponse getCurrentUser(String userId) {
        log.debug("Getting current user info for: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return toUserProfileResponse(user);
    }


    /**
     * Convert User entity to UserProfileResponse DTO.
     *
     * @param user the user entity
     * @return the user profile response
     */
    /**
     * Converts a User entity to a UserProfileResponse DTO.
     *
     * @param user the user entity
     * @return UserProfileResponse DTO
     */
    public static UserProfileResponse toUserProfileResponse(User user) {
        return UserProfileResponse.builder()
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .build();
    }
}
