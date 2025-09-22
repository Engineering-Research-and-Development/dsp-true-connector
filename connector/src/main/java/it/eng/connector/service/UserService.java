package it.eng.connector.service;

import it.eng.connector.configuration.ApiUserPrincipal;
import it.eng.connector.dto.*;
import it.eng.connector.model.PasswordValidationResult;
import it.eng.connector.model.User;
import it.eng.connector.repository.UserRepository;
import it.eng.connector.util.PasswordCheckValidator;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Comprehensive user service that handles all user-related operations.
 * Consolidates user management, profile management, and authentication operations.
 */
@Service
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final PasswordCheckValidator passwordCheckValidator;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       PasswordCheckValidator passwordCheckValidator) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.passwordCheckValidator = passwordCheckValidator;
    }

    // ==================== USER MANAGEMENT (Admin Operations) ====================

    /**
     * Find users based on generic filter criteria.
     *
     * @param filters  the filter criteria
     * @param pageable the pagination information
     * @return Page of users matching the criteria
     */
    public Page<User> findUsers(Map<String, Object> filters, Pageable pageable) {
        return userRepository.findWithDynamicFilters(filters, User.class, pageable);
    }

    /**
     * Get pending users (disabled users awaiting approval).
     *
     * @param pageable the pagination information
     * @return Page of pending users
     */
    public Page<User> getPendingUsers(Pageable pageable) {
        return userRepository.findByEnabledFalse(pageable);
    }

    /**
     * Get user by ID.
     *
     * @param id the user ID
     * @return the user entity
     */
    public User getUserById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    /**
     * Create new user (admin only).
     *
     * @param createRequest the user creation request
     * @return the created user entity
     */
    public User createUser(AdminUserCreateRequest createRequest) {
        // Check if user already exists
        if (userRepository.findByEmail(createRequest.getEmail()).isPresent()) {
            throw new BadRequestException("User with this email already exists");
        }

        // Validate password
        passwordCheckValidator.isValid(createRequest.getPassword());

        // Create new user
        User newUser = User.builder()
                .firstName(createRequest.getFirstName())
                .lastName(createRequest.getLastName())
                .email(createRequest.getEmail())
                .password(passwordEncoder.encode(createRequest.getPassword()))
                .enabled(createRequest.getEnabled())
                .expired(false)
                .locked(false)
                .role(createRequest.getRole())
                .build();

        return userRepository.save(newUser);
    }

    /**
     * Update user (admin only).
     *
     * @param id            the user ID
     * @param updateRequest the user update request
     * @return the updated user entity
     */
    public User updateUser(String id, AdminUserUpdateRequest updateRequest) {
        User user = getUserById(id);

        // Check if email is being changed and if it's already taken
        if (updateRequest.getEmail() != null && !updateRequest.getEmail().equals(user.getEmail())) {
            if (userRepository.findByEmail(updateRequest.getEmail()).isPresent()) {
                throw new BadRequestException("Email is already taken by another user");
            }
            user.setEmail(updateRequest.getEmail());
        }

        // Update fields
        if (updateRequest.getFirstName() != null) {
            user.setFirstName(updateRequest.getFirstName());
        }
        if (updateRequest.getLastName() != null) {
            user.setLastName(updateRequest.getLastName());
        }
        if (updateRequest.getRole() != null) {
            user.setRole(updateRequest.getRole());
        }
        if (updateRequest.getEnabled() != null) {
            user.setEnabled(updateRequest.getEnabled());

            // If disabling user, revoke all their refresh tokens
            if (!updateRequest.getEnabled()) {
                jwtTokenService.revokeAllUserRefreshTokens(user);
            }
        }

        return userRepository.save(user);
    }

    /**
     * Update user status (enable/disable).
     *
     * @param id      the user ID
     * @param enabled the new enabled status
     * @return the updated user entity
     */
    public User updateUserStatus(String id, boolean enabled) {
        User user = getUserById(id);
        user.setEnabled(enabled);

        // If disabling user, revoke all their refresh tokens
        if (!enabled) {
            jwtTokenService.revokeAllUserRefreshTokens(user);
        }

        return userRepository.save(user);
    }

    /**
     * Delete user (soft delete by disabling).
     *
     * @param id          the user ID
     * @param adminUserId the admin user ID performing the deletion
     * @return the deleted user entity
     */
    public User deleteUser(String id, String adminUserId) {
        User user = getUserById(id);

        // Prevent admin from deleting themselves
        if (user.getId().equals(adminUserId)) {
            throw new BadRequestException("Cannot delete your own account");
        }

        // Soft delete by disabling the account
        user.setEnabled(false);
        userRepository.save(user);

        // Revoke and remove all tokens for the user
        jwtTokenService.revokeAllUserRefreshTokens(user);
        jwtTokenService.deleteAllUserTokens(user);

        return user;
    }

    // ==================== USER PROFILE (Self Operations) ====================

    /**
     * Get current user profile.
     *
     * @param userPrincipal the authenticated user principal
     * @return the user profile response
     */
    public UserProfileResponse getCurrentUserProfile(ApiUserPrincipal userPrincipal) {
        log.info("Getting profile for user: {}", userPrincipal.getEmail());

        User user = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return toUserProfileResponse(user);
    }

    /**
     * Update current user profile.
     *
     * @param updateRequest the profile update request
     * @param userPrincipal the authenticated user principal
     * @return the updated user profile response
     */
    public UserProfileResponse updateCurrentUserProfile(UpdateProfileRequest updateRequest,
                                                        ApiUserPrincipal userPrincipal) {
        log.info("Updating profile for user: {}", userPrincipal.getEmail());

        User user = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check if email is being changed and if it's already taken
        if (updateRequest.getEmail() != null && !updateRequest.getEmail().equals(user.getEmail())) {
            if (userRepository.findByEmail(updateRequest.getEmail()).isPresent()) {
                throw new BadRequestException("Email is already taken by another user");
            }
            user.setEmail(updateRequest.getEmail());
        }

        // Update other fields
        if (updateRequest.getFirstName() != null) {
            user.setFirstName(updateRequest.getFirstName());
        }
        if (updateRequest.getLastName() != null) {
            user.setLastName(updateRequest.getLastName());
        }

        User updatedUser = userRepository.save(user);
        log.info("Profile updated successfully for user: {}", userPrincipal.getEmail());

        return toUserProfileResponse(updatedUser);
    }

    /**
     * Delete current user account (soft delete).
     *
     * @param userPrincipal the authenticated user principal
     */
    public void deleteCurrentUser(ApiUserPrincipal userPrincipal) {
        log.info("Deleting account for user: {}", userPrincipal.getEmail());

        User user = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Soft delete by disabling the account
        user.setEnabled(false);
        userRepository.save(user);

        // Revoke all refresh tokens
        jwtTokenService.revokeAllUserRefreshTokens(user);

        log.info("Account deleted successfully for user: {}", userPrincipal.getEmail());
    }

    // ==================== PASSWORD OPERATIONS ====================

    /**
     * Change user password with authentication.
     *
     * @param id             the user ID
     * @param changeRequest  the password change request
     * @param authentication the authentication object
     * @return the updated user entity
     */
    public User changeUserPassword(String id, ChangePasswordRequest changeRequest, Authentication authentication) {
        User user = getUserById(id);

        // Check permissions
        validatePasswordChangePermissions(user, authentication, true);

        // Verify current password
        if (!passwordEncoder.matches(changeRequest.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        // Validate and update password
        PasswordValidationResult validationResult = passwordCheckValidator.isValid(changeRequest.getNewPassword());
        if (!validationResult.isValid()) {
            throw new BadRequestException("Password does not meet strength requirements: " + String.join(", ", validationResult.getViolations()));
        }
        user.setPassword(passwordEncoder.encode(changeRequest.getNewPassword()));
        userRepository.save(user);

        // Revoke all existing refresh tokens for security
        jwtTokenService.revokeAllUserRefreshTokens(user);

        log.info("Password changed successfully for user: {}", user.getEmail());
        return user;
    }

    /**
     * Change password for current user.
     *
     * @param changePasswordRequest the password change request
     * @param userPrincipal         the authenticated user principal
     */
    public void changeCurrentUserPassword(ChangePasswordRequest changePasswordRequest,
                                          ApiUserPrincipal userPrincipal) {
        log.info("Changing password for user: {}", userPrincipal.getEmail());

        User user = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Verify current password
        if (!passwordEncoder.matches(changePasswordRequest.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        // Validate and update password
        PasswordValidationResult validationResult = passwordCheckValidator.isValid(changePasswordRequest.getNewPassword());
        if (!validationResult.isValid()) {
            throw new BadRequestException("Password does not meet strength requirements: " + String.join(", ", validationResult.getViolations()));
        }
        user.setPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
        userRepository.save(user);

        // Revoke all existing refresh tokens for security
        jwtTokenService.revokeAllUserRefreshTokens(user);

        log.info("Password changed successfully for user: {}", userPrincipal.getEmail());
    }

    /**
     * Reset user password (admin only).
     *
     * @param id          the user ID
     * @param newPassword the new password
     * @return the updated user entity
     */
    public User resetUserPassword(String id, String newPassword) {
        User user = getUserById(id);

        PasswordValidationResult validationResult = passwordCheckValidator.isValid(newPassword);
        if (!validationResult.isValid()) {
            throw new BadRequestException("Password does not meet strength requirements: " + String.join(", ", validationResult.getViolations()));
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        jwtTokenService.revokeAllUserRefreshTokens(user);

        log.info("Password reset successfully for user: {}", user.getEmail());
        return user;
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

    /**
     * Validate password change permissions.
     *
     * @param user                   the user entity
     * @param authentication         the authentication object
     * @param requireCurrentPassword whether current password is required
     */
    private void validatePasswordChangePermissions(User user, Authentication authentication, boolean requireCurrentPassword) {
        if (authentication == null) {
            throw new BadRequestException("Authentication required");
        }

        String currentUserEmail = authentication.getName();
        boolean isOwnPassword = user.getEmail().equals(currentUserEmail);
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

        if (!isOwnPassword && !isAdmin) {
            throw new BadRequestException("You can only change your own password");
        }
    }
}
